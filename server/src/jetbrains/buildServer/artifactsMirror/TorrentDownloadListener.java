/*
 * Copyright (c) 2000-2012 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package jetbrains.buildServer.artifactsMirror;

import jetbrains.buildServer.ArtifactsConstants;
import jetbrains.buildServer.artifactsMirror.torrent.TorrentTracker;
import jetbrains.buildServer.controllers.artifacts.RepositoryDownloadController;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.artifacts.ArtifactsGuard;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifact;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifacts;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactsViewMode;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Listens for all ".torrent" file downloads and for each download announces the corresponding
 * artifact to the tracker and starts the seeder.
 *
 * @author Maxim Podkolzine (maxim.podkolzine@jetbrains.com)
 * @since 8.0
 */
public class TorrentDownloadListener implements RepositoryDownloadController.RepositoryListener {
  private final ArtifactsGuard myGuard;
  private final TorrentTracker myTorrentTracker;

  public TorrentDownloadListener(@NotNull RepositoryDownloadController controller,
                                 @NotNull ArtifactsGuard guard,
                                 @NotNull TorrentTracker torrentTracker) {
    myGuard = guard;
    myTorrentTracker = torrentTracker;
    controller.addListener(this);
  }

  public void artifactDownloaded(@NotNull SBuild build, @NotNull BuildArtifact torrentArtifact) {
    final String torrentRelativePath = torrentArtifact.getRelativePath();
    if (!torrentRelativePath.endsWith(TorrentTracker.TORRENT_FILE_SUFFIX) ||
        !torrentRelativePath.startsWith(ArtifactsConstants.TEAMCITY_ARTIFACTS_DIR)) {
      return;
    }

    final File artifactsDirectory = build.getArtifactsDirectory();
    final String torrentName = new File(torrentRelativePath).getName();
    final String srcName = torrentName.substring(0, torrentName.length() - TorrentTracker.TORRENT_FILE_SUFFIX.length());

    BuildArtifacts artifacts = build.getArtifacts(BuildArtifactsViewMode.VIEW_DEFAULT);
    artifacts.iterateArtifacts(new BuildArtifacts.BuildArtifactsProcessor() {
      @NotNull
      public Continuation processBuildArtifact(@NotNull BuildArtifact artifact) {
        String currentArtifact = new File(artifact.getRelativePath()).getName();
        if (!artifact.isDirectory() && srcName.equals(currentArtifact)) {
          myGuard.lockReading(artifactsDirectory);
          try {
            File currentFile = new File(artifactsDirectory, artifact.getRelativePath());
            File torrentFile = new File(artifactsDirectory, torrentRelativePath);
            myTorrentTracker.announceAndSeedTorrent(currentFile, torrentFile);
            return Continuation.BREAK;
          } finally {
            myGuard.unlockReading(artifactsDirectory);
          }
        }
        return Continuation.CONTINUE;
      }
    });
  }
}
