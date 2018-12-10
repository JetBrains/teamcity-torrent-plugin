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

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.FileVisitor;
import java.nio.file.Path;

public interface FileWalker {

  /**
   * walk file tree from root path.
   *
   * @param root root for start processing
   * @param visitor callbacks process files
   * @throws IOException for any I/O errors
   */
  void walkFileTree(@NotNull Path root, @NotNull FileVisitor<? super Path> visitor) throws IOException;

}
