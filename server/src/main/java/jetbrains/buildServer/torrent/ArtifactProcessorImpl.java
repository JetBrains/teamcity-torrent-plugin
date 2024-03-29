

package jetbrains.buildServer.torrent;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifact;
import jetbrains.buildServer.torrent.seeder.TorrentsSeeder;
import jetbrains.buildServer.torrent.torrent.TorrentUtil;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Artifact processor, that for each artifact try find torrent file and start seeding, if torrent file exist
 */
public class ArtifactProcessorImpl implements ArtifactProcessor {

  private final static Logger LOG = Logger.getInstance(ArtifactProcessorImpl.class.getName());

  @NotNull private final Path myTorrentsDir;
  @NotNull private final Path myArtifactsDirectory;
  @NotNull private final TorrentsSeeder myTorrentsSeeder;
  @NotNull private final TorrentConfigurator myConfigurator;

  ArtifactProcessorImpl(@NotNull Path torrentsDir,
                        @NotNull Path artifactsDirectory,
                        @NotNull TorrentsSeeder torrentsSeeder,
                        @NotNull TorrentConfigurator configurator) {
    myTorrentsDir = torrentsDir;
    myTorrentsSeeder = torrentsSeeder;
    myConfigurator = configurator;
    myArtifactsDirectory = artifactsDirectory;
  }

  @Override
  public void processArtifacts(@NotNull List<BuildArtifact> artifacts) {
    artifacts.forEach(artifact -> {
      if (artifact.isDirectory()) {
        return;
      }

      if (!myConfigurator.isSeedingEnabled()) {
        return;
      }

      String artifactRelativePath = artifact.getRelativePath();
      Path fullPath = myArtifactsDirectory.resolve(artifactRelativePath);
      if (!Files.exists(fullPath)) {
        LOG.debug(String.format("File '%s' doesn't exist. Won't create a torrent for it", fullPath.toString()));
        return;
      }
      Path torrent = findTorrent(fullPath, artifactRelativePath);
      if (!Files.exists(torrent)) {
        LOG.debug(String.format("torrent file for artifact %s doesn't exist", artifactRelativePath));
        return;
      }
      myTorrentsSeeder.registerSrcAndTorrentFile(fullPath.toFile(), torrent.toFile(), true);
    });
  }

  @NotNull
  private Path findTorrent(@NotNull final Path artifactFullPath,
                           @NotNull final String artifactRelativePath) {
    Path destPath = myTorrentsDir.resolve(artifactRelativePath);
    final Path parentDir = destPath.getParent();
    return parentDir.resolve(artifactFullPath.getFileName().toString() + TorrentUtil.TORRENT_FILE_SUFFIX);
  }
}