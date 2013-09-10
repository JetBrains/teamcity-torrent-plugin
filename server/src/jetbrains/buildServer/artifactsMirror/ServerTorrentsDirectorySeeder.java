/*
 * Copyright (c) 2000-2012 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package jetbrains.buildServer.artifactsMirror;

import jetbrains.buildServer.NetworkUtil;
import jetbrains.buildServer.artifactsMirror.seeder.FileLink;
import jetbrains.buildServer.artifactsMirror.seeder.TorrentsDirectorySeeder;
import jetbrains.buildServer.artifactsMirror.torrent.TorrentUtil;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifact;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifacts;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactsViewMode;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Maxim Podkolzine (maxim.podkolzine@jetbrains.com)
 * @since 8.0
 */
public class ServerTorrentsDirectorySeeder {
  private final TorrentsDirectorySeeder myTorrentsDirectorySeeder;
  private final TorrentConfigurator myConfigurator;
  private volatile int myFileSizeThreshold;
  private URI myAnnounceURI;
  private int myMaxTorrentsToSeed;

  public ServerTorrentsDirectorySeeder(@NotNull final ServerPaths serverPaths,
                                       @NotNull final TorrentConfigurator configurator,
                                       @NotNull final ExecutorServices executorServices,
                                       @NotNull final EventDispatcher<BuildServerListener> eventDispatcher) {
    File torrentsStorage = new File(serverPaths.getPluginDataDirectory(), "torrents");
    torrentsStorage.mkdirs();
    myTorrentsDirectorySeeder = new TorrentsDirectorySeeder(torrentsStorage,
            configurator.getMaxNumberOfSeededTorrents(),
            configurator.getFileSizeThresholdMb());
    setMaxNumberOfSeededTorrents(configurator.getMaxNumberOfSeededTorrents());
    setFileSizeThreshold(configurator.getFileSizeThresholdMb());
    myConfigurator = configurator;
    eventDispatcher.addListener(new BuildServerAdapter() {
      public void serverShutdown() {
        stopSeeder();
      }


      @Override
      public void serverStartup() {
        if (myConfigurator.isSeederEnabled()) {
          executorServices.getLowPriorityExecutorService().submit(new Runnable() {
            public void run() {
              startSeeder();
            }
          });
        }
      }

      @Override
      public void buildFinished(SRunningBuild build) {
        if (myConfigurator.isTrackerEnabled()) {
          announceBuildArtifacts(build);
        }
      }
    });

    configurator.addPropertyChangeListener(new PropertyChangeListener() {
      public void propertyChange(PropertyChangeEvent evt) {
        String propertyName = evt.getPropertyName();
        if (TorrentConfigurator.FILE_SIZE_THRESHOLD.equals(propertyName)){
          setFileSizeThreshold((Integer) evt.getNewValue());
        } else if (TorrentConfigurator.MAX_NUMBER_OF_SEEDED_TORRENTS.equals(propertyName)){
          setMaxNumberOfSeededTorrents((Integer) evt.getNewValue());
          myTorrentsDirectorySeeder.setMaxTorrentsToSeed(myMaxTorrentsToSeed);
        } else if (TorrentConfigurator.ANNOUNCE_INTERVAL.equals(propertyName)){
          myTorrentsDirectorySeeder.setAnnounceInterval((Integer)evt.getNewValue());
        } else if (TorrentConfigurator.ANNOUNCE_URL.equals(propertyName)){
          setAnnounceURI(URI.create(String.valueOf(evt.getNewValue())));
        } else if (TorrentConfigurator.SEEDER_ENABLED.equals(propertyName)){
          boolean enabled = (Boolean) evt.getNewValue();
          if (enabled){
            startSeeder();
          } else {
            stopSeeder();
          }
        }
      }
    });
  }


  public void stopSeeder() {
    if (!myTorrentsDirectorySeeder.isStopped()) {
      myTorrentsDirectorySeeder.stop();
    }
  }

  public void startSeeder() {
    try {
      InetAddress[] myAddresses = NetworkUtil.getSelfAddresses();

      myTorrentsDirectorySeeder.start(myAddresses,
              myAnnounceURI,
              myConfigurator.getAnnounceIntervalSec());
    } catch (Exception e) {
      Loggers.SERVER.warn("Failed to start torrent seeder, error: " + e.toString());
    }
  }

  public void setFileSizeThreshold(int fileSizeThreshold) {
    myFileSizeThreshold = fileSizeThreshold;
  }

  @NotNull
  public File getTorrentFilesBaseDir(@NotNull SBuild build) {
    final File artifactsDirectory = build.getArtifactsDirectory();
    return new File(artifactsDirectory, TorrentsDirectorySeeder.TORRENTS_DIT_PATH);
  }

  @NotNull
  public Collection<File> getTorrentFiles(@NotNull SBuild build) {
    File baseDir = getTorrentFilesBaseDir(build);
    try {
      return FileUtil.findFiles(new FileFilter() {
        public boolean accept(File file) {
          return file.getName().endsWith(TorrentUtil.TORRENT_FILE_SUFFIX);
        }
      }, baseDir);
    } catch (Exception e) {
      return Collections.emptyList();
    }
  }

  @NotNull
  public File getTorrentFile(@NotNull SBuild build, @NotNull String torrentPath) {
    return new File(getTorrentFilesBaseDir(build), torrentPath);
  }

  public int getNumberOfSeededTorrents() {
    if (myTorrentsDirectorySeeder.isStopped()) {
      return 0;
    }
    return myTorrentsDirectorySeeder.getNumberOfSeededTorrents();
  }

  private void announceBuildArtifacts(@NotNull final SBuild build) {
    final File torrentsDir = getTorrentFilesBaseDir(build);
    BuildArtifacts artifacts = build.getArtifacts(BuildArtifactsViewMode.VIEW_DEFAULT);
    final File artifactsDirectory = build.getArtifactsDirectory();
    final File linkDir = getLinkDir(build);
    torrentsDir.mkdirs();
    artifacts.iterateArtifacts(new BuildArtifacts.BuildArtifactsProcessor() {
      @NotNull
      public Continuation processBuildArtifact(@NotNull BuildArtifact artifact) {
        processArtifactInternal(artifact, artifactsDirectory, linkDir, torrentsDir);
        return BuildArtifacts.BuildArtifactsProcessor.Continuation.CONTINUE;
      }

    });
  }

  protected void processArtifactInternal(@NotNull final BuildArtifact artifact,
                                       @NotNull final File artifactsDirectory,
                                       @NotNull final File linkDir,
                                       @NotNull final File torrentsDir) {
    if (artifact.isDirectory()){
      for (BuildArtifact childArtifacts : artifact.getChildren()) {
        processArtifactInternal(childArtifacts, artifactsDirectory, linkDir, torrentsDir);
      }
      return;
    }

    if (shouldCreateTorrentFor(artifact)) {
      File artifactFile = new File(artifactsDirectory, artifact.getRelativePath());

      linkDir.mkdirs();

      try {
        if (myConfigurator.isSeederEnabled()) {
          File torrentFile = createTorrent(artifactFile, artifact.getRelativePath(), torrentsDir);
          myTorrentsDirectorySeeder.getTorrentSeeder().seedTorrent(torrentFile, artifactFile);
          FileLink.createLink(artifactFile, torrentFile, linkDir);
        }
      } catch (IOException e) {
        e.printStackTrace();
      } catch (NoSuchAlgorithmException e) {
        e.printStackTrace();
      }
    }
  }


  private File createTorrent(@NotNull final File artifactFile,
                             @NotNull final String artifactPath,
                             @NotNull final File torrentsDir){
    File destPath = new File(torrentsDir, artifactPath);
    final File parentDir = destPath.getParentFile();
    parentDir.mkdirs();

    return TorrentUtil.getOrCreateTorrent(artifactFile, artifactPath, torrentsDir, myAnnounceURI);
  }

  private boolean shouldCreateTorrentFor(@NotNull BuildArtifact artifact) {
    long size = artifact.getSize();
    if (size < myFileSizeThreshold * 1024 * 1024)
      return false;

    if (myMaxTorrentsToSeed > 0 && myTorrentsDirectorySeeder.getNumberOfSeededTorrents() >= myMaxTorrentsToSeed){
      Loggers.SERVER.warn("Reached max number of seeded torrents. Torrent for "+artifact.getName()+" will not be seeded");
      return false;
    }

    return true;
  }

  public void setMaxNumberOfSeededTorrents(int maxNumberOfSeededTorrents) {
    myMaxTorrentsToSeed = maxNumberOfSeededTorrents;
  }

  public void setAnnounceURI(URI announceURI){
    myAnnounceURI = announceURI;
  }

  private File getLinkDir(@NotNull SBuild build) {
    return new File(myTorrentsDirectorySeeder.getStorageDirectory(),
            build.getBuildTypeId() + File.separator + build.getBuildId());
  }

}
