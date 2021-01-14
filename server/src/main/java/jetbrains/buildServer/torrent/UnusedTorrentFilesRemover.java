/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

import jetbrains.buildServer.serverSide.artifacts.BuildArtifact;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;

public interface UnusedTorrentFilesRemover {

  /**
   * method must remove all torrent files, that don't contains artifact pair.
   *
   * @param artifacts   list of artifacts. Only for this artifacts must exist torrent files
   * @param torrentsDir root path of torrent files
   */
  void removeUnusedTorrents(@NotNull List<BuildArtifact> artifacts, @NotNull Path torrentsDir);

}
