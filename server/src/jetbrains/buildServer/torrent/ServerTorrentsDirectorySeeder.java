/*
 * Copyright (c) 2000-2012 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package jetbrains.buildServer.torrent;

import com.intellij.openapi.diagnostic.Logger;
import com.turn.ttorrent.client.SharedTorrent;
import com.turn.ttorrent.client.peer.SharingPeer;
import jetbrains.buildServer.NetworkUtil;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifact;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifacts;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactsViewMode;
import jetbrains.buildServer.torrent.seeder.ParentDirConverter;
import jetbrains.buildServer.torrent.seeder.TorrentsSeeder;
import jetbrains.buildServer.torrent.settings.SeedSettings;
import jetbrains.buildServer.torrent.torrent.TorrentUtil;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileFilter;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Maxim Podkolzine (maxim.podkolzine@jetbrains.com)
 * @since 8.0
 */
public class ServerTorrentsDirectorySeeder {
  private final static Logger LOG = Logger.getInstance(ServerTorrentsDirectorySeeder.class.getName());

  private TorrentsSeeder myTorrentsSeeder;
  private final TorrentConfigurator myConfigurator;
  private URI myAnnounceURI;
  private int myMaxTorrentsToSeed;

  public ServerTorrentsDirectorySeeder(@NotNull final ServerPaths serverPaths,
                                       @NotNull final ServerSettings serverSettings,
                                       @NotNull final TorrentConfigurator configurator,
                                       @NotNull final EventDispatcher<BuildServerListener> eventDispatcher) {
    setMaxNumberOfSeededTorrents(configurator.getMaxNumberOfSeededTorrents());
    myConfigurator = configurator;
    eventDispatcher.addListener(new BuildServerAdapter() {
      @Override
      public void serverStartup() {
        final File torrentsStorage = new File(serverPaths.getPluginDataDirectory(), "torrents");
        torrentsStorage.mkdirs();
        myTorrentsSeeder = new TorrentsSeeder(torrentsStorage, configurator.getMaxNumberOfSeededTorrents(), new ParentDirConverter() {
          @NotNull
          @Override
          public File getParentDir() {
            return serverSettings.getArtifactDirectories().get(0);
          }
        });

        // if torrent file expires, it will be removed from disk as well
        // this is needed to prevent agents from downloading this torrent file (because most likely no one is going to seed this torrent in the future)
        // and to stop showing torrent icons for users
        myTorrentsSeeder.setRemoveExpiredTorrentFiles(true);

        startSeeder();
      }

      @Override
      public void buildFinished(@NotNull SRunningBuild build) {
        File artifactsDirectory = build.getArtifactsDirectory();
        final File torrentsDir = getTorrentFilesBaseDir(artifactsDirectory);
        torrentsDir.mkdirs();
        Path torrentsPath = torrentsDir.toPath();
        announceBuildArtifacts(torrentsPath,
                build.getArtifacts(BuildArtifactsViewMode.VIEW_INTERNAL_ONLY),
                new ArtifactsCollectorImpl(),
                new ArtifactProcessorImpl(torrentsPath, artifactsDirectory.toPath(), myTorrentsSeeder, myConfigurator),
                new UnusedTorrentFilesRemoverImpl(Files::delete, Files::walkFileTree));
      }

      public void serverShutdown() {
        if (myTorrentsSeeder != null) {
          myTorrentsSeeder.dispose();
        }
      }

    });

    configurator.addPropertyChangeListener(new PropertyChangeListener() {
      public void propertyChange(PropertyChangeEvent evt) {
        String propertyName = evt.getPropertyName();
        switch (propertyName) {
          case SeedSettings.MAX_NUMBER_OF_SEEDED_TORRENTS:
            setMaxNumberOfSeededTorrents((Integer) evt.getNewValue());
            myTorrentsSeeder.setMaxTorrentsToSeed(myMaxTorrentsToSeed);
            break;
          case TorrentConfiguration.ANNOUNCE_INTERVAL:
            myTorrentsSeeder.setAnnounceInterval((Integer) evt.getNewValue());
            break;
          case TorrentConfiguration.MAX_INCOMING_CONNECTIONS:
            myTorrentsSeeder.setMaxIncomingConnectionsCount((Integer) evt.getNewValue());
            myTorrentsSeeder.setMaxOutgoingConnectionsCount((Integer) evt.getNewValue());
            break;
          case TorrentConfiguration.SOCKET_CONNECTION_TIMEOUT:
            myTorrentsSeeder.setSocketTimeout((Integer) evt.getNewValue());
            break;
          case TorrentConfiguration.CLEANUP_TIMEOUT:
            myTorrentsSeeder.setCleanupTimeout((Integer) evt.getNewValue());
            break;
          case TorrentConfiguration.ANNOUNCE_URL:
            setAnnounceURI(URI.create(String.valueOf(evt.getNewValue())));
            break;
          case TorrentConfiguration.DOWNLOAD_ENABLED:
          case SeedSettings.SEEDING_ENABLED:
            boolean enabled = (Boolean) evt.getNewValue();
            if (enabled) {
              startSeeder();
            } else {
              stopSeeder();
            }
            break;
        }
      }
    });
  }


  public void stopSeeder() {
    myTorrentsSeeder.stop();
  }

  private void startSeeder() {
    try {

      final InetAddress[] addresses;
      if (myConfigurator.getOwnAddress() != null) {
        addresses = new InetAddress[]{InetAddress.getByName(myConfigurator.getOwnAddress())};
      } else {
        addresses = NetworkUtil.getSelfAddresses(null);
      }

      myTorrentsSeeder.start(addresses, myAnnounceURI, TorrentConfiguration.DEFAULT_ANNOUNCE_INTERVAL);
    } catch (Exception e) {
      Loggers.SERVER.warn("Failed to start torrent seeder", e);
    }
  }

  @NotNull
  public File getTorrentFilesBaseDir(@NotNull File artifactsDirectory) {
    return new File(artifactsDirectory, TorrentsSeeder.TORRENTS_DIT_PATH);
  }

  @NotNull
  public Collection<File> getTorrentFiles(@NotNull SBuild build) {
    File baseDir = getTorrentFilesBaseDir(build.getArtifactsDirectory());
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
    return new File(getTorrentFilesBaseDir(build.getArtifactsDirectory()), torrentPath);
  }

  public int getNumberOfSeededTorrents() {
    if (myTorrentsSeeder.isStopped()) {
      return 0;
    }
    return myTorrentsSeeder.getNumberOfSeededTorrents();
  }

  void announceBuildArtifacts(@NotNull final Path torrentsDir,
                              @NotNull final BuildArtifacts buildArtifacts,
                              @NotNull final ArtifactsCollector artifactsCollector,
                              @NotNull final ArtifactProcessor artifactProcessor,
                              @NotNull final UnusedTorrentFilesRemover torrentFilesRemover) {
    List<BuildArtifact> artifactList = artifactsCollector.collectArtifacts(buildArtifacts);
    artifactProcessor.processArtifacts(artifactList);
    torrentFilesRemover.removeUnusedTorrents(artifactList, torrentsDir);
  }

  public void setMaxNumberOfSeededTorrents(int maxNumberOfSeededTorrents) {
    myMaxTorrentsToSeed = maxNumberOfSeededTorrents;
  }

  public void setAnnounceURI(URI announceURI) {
    myAnnounceURI = announceURI;
  }

  public Collection<SharedTorrent> getSharedTorrents() {
    return myTorrentsSeeder.getSharedTorrents();
  }

  public Set<SharingPeer> getPeers() {
    return myTorrentsSeeder.getClient().getPeers();
  }

  //for tests
  TorrentsSeeder getTorrentsSeeder() {
    return myTorrentsSeeder;
  }
}