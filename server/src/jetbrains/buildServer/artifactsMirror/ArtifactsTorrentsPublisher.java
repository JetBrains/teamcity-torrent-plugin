/*
 * Copyright (c) 2000-2012 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package jetbrains.buildServer.artifactsMirror;

import jetbrains.buildServer.artifactsMirror.seeder.LinkFile;
import jetbrains.buildServer.artifactsMirror.seeder.TorrentFileFactory;
import jetbrains.buildServer.artifactsMirror.seeder.TorrentsDirectorySeeder;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifact;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifacts;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactsViewMode;
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

/**
 * @author Maxim Podkolzine (maxim.podkolzine@jetbrains.com)
 * @since 8.0
 */
public class ArtifactsTorrentsPublisher extends BuildServerAdapter {
  private final TorrentTrackerManager myTorrentTrackerManager;
  private final TorrentsDirectorySeeder myTorrentsDirectorySeeder;

  public ArtifactsTorrentsPublisher(@NotNull ServerPaths serverPaths,
                                    @NotNull TorrentTrackerManager torrentTrackerManager,
                                    @NotNull EventDispatcher<BuildServerListener> eventDispatcher) {
    myTorrentTrackerManager = torrentTrackerManager;
    File torrentsStorage = new File(serverPaths.getPluginDataDirectory(), "torrents");
    torrentsStorage.mkdirs();
    myTorrentsDirectorySeeder = new TorrentsDirectorySeeder(torrentsStorage, new TorrentFileFactory() {
      public File createTorrentFile(@NotNull File sourceFile, @NotNull File parentDir) throws IOException {
        return myTorrentTrackerManager.createTorrent(sourceFile, parentDir);
      }
    });

    eventDispatcher.addListener(this);
  }

  @Override
  public void buildFinished(SRunningBuild build) {
    announceBuildArtifacts(build);
  }

  @Override
  public void serverStartup() {
    super.serverStartup();
    myTorrentsDirectorySeeder.start();
  }

  @Override
  public void serverShutdown() {
    super.serverShutdown();
    myTorrentsDirectorySeeder.stop();
  }

  private void announceBuildArtifacts(@NotNull final SBuild build) {
    final File artifactsDirectory = build.getArtifactsDirectory();

    BuildArtifacts artifacts = build.getArtifacts(BuildArtifactsViewMode.VIEW_DEFAULT);
    artifacts.iterateArtifacts(new BuildArtifacts.BuildArtifactsProcessor() {
      @NotNull
      public Continuation processBuildArtifact(@NotNull BuildArtifact artifact) {
        if (shouldCreateTorrentFor(artifact)) {
          File artifactFile = new File(artifactsDirectory, artifact.getRelativePath());
          File linkDir = new File(myTorrentsDirectorySeeder.getStorageDirectory(), build.getBuildTypeId());
          try {
            LinkFile.createLink(artifactFile, linkDir);
          } catch (IOException e) {
            //
          }
        }
        return Continuation.CONTINUE;
      }
    });
  }

  private boolean shouldCreateTorrentFor(@NotNull BuildArtifact artifact) {
    long size = artifact.getSize();
    return !artifact.isDirectory() && size >= myTorrentTrackerManager.getFileSizeThresholdMb() * 1024 * 1024;
  }
}
