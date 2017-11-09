/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.torrent;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifact;
import jetbrains.buildServer.torrent.seeder.TorrentsSeeder;
import jetbrains.buildServer.torrent.torrent.TorrentUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * Artifact processor, that for each artifact try find torrent file and start seeding, if torrent file exist
 */
public class ArtifactProcessorImpl implements ArtifactProcessor {

  private final static Logger LOG = Logger.getInstance(ArtifactProcessorImpl.class.getName());

  @NotNull private final Path myTorrentsDir;
  @NotNull private final File myArtifactsDirectory;
  @NotNull private final TorrentsSeeder myTorrentsSeeder;
  @NotNull private final TorrentConfigurator myConfigurator;

  public ArtifactProcessorImpl(@NotNull Path torrentsDir,
                               @NotNull File artifactsDirectory,
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

      if (TorrentUtil.shouldCreateTorrentFor(artifact.getSize(), myConfigurator)) {
        String artifactRelativePath = artifact.getRelativePath();
        File artifactFile = new File(myArtifactsDirectory, artifactRelativePath);
        if (!artifactFile.exists()) {
          LOG.debug(String.format("File '%s' doesn't exist. Won't create a torrent for it", artifactFile.getAbsolutePath()));
          return;
        }
        File torrentFile = findTorrent(artifactFile, artifactRelativePath);
        if (!torrentFile.exists()) {
          LOG.info(String.format("torrent file for artifact %s doesn't exist", artifactRelativePath));
          return;
        }
        myTorrentsSeeder.registerSrcAndTorrentFile(artifactFile, torrentFile, myConfigurator.isTorrentEnabled());
      }
    });
  }

  @NotNull
  private File findTorrent(@NotNull final File artifactFile,
                           @NotNull final String artifactPath) {
    File destPath = new File(myTorrentsDir.toFile(), artifactPath);
    final File parentDir = destPath.getParentFile();
    return new File(parentDir, artifactFile.getName() + TorrentUtil.TORRENT_FILE_SUFFIX);
  }
}
