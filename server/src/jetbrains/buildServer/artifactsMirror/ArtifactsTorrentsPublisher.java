/*
 * Copyright (c) 2000-2012 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package jetbrains.buildServer.artifactsMirror;

import jetbrains.buildServer.artifactsMirror.torrent.TorrentTracker;
import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.SRunningBuild;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.artifacts.ArtifactsGuard;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifact;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifacts;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactsViewMode;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author Maxim Podkolzine (maxim.podkolzine@jetbrains.com)
 * @since 8.0
 */
public class ArtifactsTorrentsPublisher extends BuildServerAdapter {
  private static final int MIN_ARTIFACT_SIZE = TeamCityProperties.getInteger("teamcity.artifacts.size.for.torrent",
                                                                             50 * (1 << 20));

  private final ArtifactsGuard myGuard;
  private final TorrentTracker myTorrentTracker;

  public ArtifactsTorrentsPublisher(@NotNull SBuildServer buildServer,
                                    @NotNull ArtifactsGuard guard,
                                    @NotNull TorrentTracker torrentTracker) {
    myGuard = guard;
    myTorrentTracker = torrentTracker;
    buildServer.addListener(this);
  }

  @Override
  public void buildFinished(SRunningBuild build) {
    final File artifactsDirectory = build.getArtifactsDirectory();

    BuildArtifacts artifacts = build.getArtifacts(BuildArtifactsViewMode.VIEW_DEFAULT);
    artifacts.iterateArtifacts(new BuildArtifacts.BuildArtifactsProcessor() {
      @NotNull
      public Continuation processBuildArtifact(@NotNull BuildArtifact artifact) {
        if (shouldCreateTorrentFor(artifact)) {
          File artifactFile = new File(artifactsDirectory, artifact.getRelativePath());
          myGuard.lockReading(artifactFile);
          try {
            myTorrentTracker.announceTorrent(artifactFile);
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
    return size > MIN_ARTIFACT_SIZE;
  }
}
