

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