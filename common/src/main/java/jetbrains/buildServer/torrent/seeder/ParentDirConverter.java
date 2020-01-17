/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

package jetbrains.buildServer.torrent.seeder;

import org.jetbrains.annotations.NotNull;

import java.io.File;

public abstract class ParentDirConverter implements PathConverter {
  @NotNull
  public abstract File getParentDir();

  @NotNull
  public File convertToFile(@NotNull String path) {
    final File file = new File(path);
    if (file.isAbsolute()) {
      return file;
    }

    return new File(getParentDir(), path);
  }

  @NotNull
  public String convertToPath(@NotNull File file) {
    String filePath = file.getAbsolutePath();
    String parentPath = getParentDir().getAbsolutePath() + File.separatorChar;
    if (filePath.startsWith(parentPath)) {
      return filePath.substring(parentPath.length());
    }
    return filePath;
  }
}
