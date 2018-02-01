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
import jetbrains.buildServer.torrent.torrent.TorrentUtil;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * remove all torrent files without pair in artifacts list. Pair is search by relative path.
 * Torrent files must have suffix {@link TorrentUtil#TORRENT_FILE_SUFFIX}
 * e.g. if artifact placed in root build directory with name "art1.jar"
 * then torrent file must have name ar1.jar.torrent and place in torrents root dir
 */
public class UnusedTorrentFilesRemoverImpl implements UnusedTorrentFilesRemover {

  private final static Logger LOG = Logger.getInstance(UnusedTorrentFilesRemoverImpl.class.getName());

  @NotNull private final FileRemover myFileRemover;
  @NotNull private final FileWalker myFileWalker;

  public UnusedTorrentFilesRemoverImpl(@NotNull FileRemover fileRemover,
                                       @NotNull FileWalker fileWalker) {
    myFileRemover = fileRemover;
    myFileWalker = fileWalker;
  }

  @Override
  public void removeUnusedTorrents(@NotNull List<BuildArtifact> artifacts, @NotNull Path torrentsDir) {
    Collection<Path> torrentsForRemoving = new ArrayList<>();
    Set<String> expectedTorrentPathsForArtifacts = artifacts
            .stream()
            .map(it -> it.getRelativePath() + TorrentUtil.TORRENT_FILE_SUFFIX)
            .collect(Collectors.toSet());
    try {
      myFileWalker.walkFileTree(torrentsDir, new SimpleFileVisitor<Path>() {
        public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
          Path relativePath = torrentsDir.relativize(path);
          final String systemIndependentPath = FileUtil.toSystemIndependentName(relativePath.toString());
          if (expectedTorrentPathsForArtifacts.contains(systemIndependentPath)) {
            return FileVisitResult.CONTINUE;
          }
          torrentsForRemoving.add(path);
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException e) {
      LOG.warnAndDebugDetails("failed walk torrent files tree for removing useless torrents", e);
    }
    torrentsForRemoving.forEach(path -> {
      try {
        myFileRemover.remove(path);
      } catch (IOException e) {
        LOG.warnAndDebugDetails("unable to remove unused torrent file " + path, e);
      }
    });
  }
}
