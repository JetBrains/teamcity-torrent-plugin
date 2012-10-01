/*
 * Copyright (c) 2000-2012 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package jetbrains.buildServer.artifactsMirror;

import jetbrains.buildServer.ArtifactsConstants;
import jetbrains.buildServer.artifactsMirror.torrent.TorrentTracker;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.artifacts.ArtifactsGuard;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifact;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifacts;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactsViewMode;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author Maxim Podkolzine (maxim.podkolzine@jetbrains.com)
 * @since 8.0
 */
public class ArtifactsTorrentsPublisher extends BuildServerAdapter {

  private final ArtifactsGuard myGuard;
  private final TorrentTracker myTorrentTracker;
  private final ExecutorServices myExecutors;
  private final SBuildServer myServer;

  public ArtifactsTorrentsPublisher(@NotNull SBuildServer buildServer,
                                    @NotNull ArtifactsGuard guard,
                                    @NotNull TorrentTracker torrentTracker,
                                    @NotNull ExecutorServices executorServices) {
    myGuard = guard;
    myTorrentTracker = torrentTracker;
    myExecutors = executorServices;
    myServer = buildServer;
    buildServer.addListener(this);
  }

  @Override
  public void serverStartup() {
    super.serverStartup();
    myExecutors.getLowPriorityExecutorService().submit(new Runnable() {
      public void run() {
        ProjectManager projectManager = myServer.getProjectManager();
        for (SBuildType buildType : projectManager.getActiveBuildTypes()) {
          SFinishedBuild build = buildType.getLastChangesFinished();
          if (build != null) {
            announceBuildArtifacts(build);
          }
        }
      }
    });
  }

  @Override
  public void buildFinished(SRunningBuild build) {
    announceBuildArtifacts(build);
  }

  private void announceBuildArtifacts(@NotNull SBuild build) {
    final File artifactsDirectory = build.getArtifactsDirectory();
    final File torrentsStore = new File(artifactsDirectory, ArtifactsConstants.TEAMCITY_ARTIFACTS_DIR);

    BuildArtifacts artifacts = build.getArtifacts(BuildArtifactsViewMode.VIEW_DEFAULT);
    artifacts.iterateArtifacts(new BuildArtifacts.BuildArtifactsProcessor() {
      @NotNull
      public Continuation processBuildArtifact(@NotNull BuildArtifact artifact) {
        if (shouldCreateTorrentFor(artifact)) {
          File artifactFile = new File(artifactsDirectory, artifact.getRelativePath());
          myGuard.lockReading(artifactFile);
          try {
            myTorrentTracker.createTorrent(artifactFile, torrentsStore);
          } finally {
            myGuard.unlockReading(artifactFile);
          }
        }
        return Continuation.CONTINUE;
      }
    });
  }

  private static boolean shouldCreateTorrentFor(@NotNull BuildArtifact artifact) {
    long size = artifact.getSize();
    return !artifact.isDirectory() &&
            size >= TeamCityProperties.getInteger("teamcity.artifactsTorrent.sizeThresholdMb", 50) * 1024 * 1024;
  }
}
