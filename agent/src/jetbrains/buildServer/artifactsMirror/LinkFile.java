package jetbrains.buildServer.artifactsMirror;

import jetbrains.buildServer.artifactsMirror.torrent.TorrentUtil;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * User: Victory.Bedrosova
 * Date: 10/15/12
 * Time: 4:12 PM
 */
public class LinkFile {
  private static final String LINK_FILE_SUFFIX = ".link";

  @NotNull
  private final File myFile;
  @NotNull
  private final File mySrcFile;

  public LinkFile(@NotNull File root, @NotNull File srcFile) {
    mySrcFile = srcFile;
    myFile = new File(root, srcFile.getName() + LINK_FILE_SUFFIX);
  }

  public void save() throws IOException {
    FileUtil.createParentDirs(myFile);
    FileUtil.writeFile(myFile, mySrcFile.getAbsolutePath(), "UTF-8");
  }

  public static boolean isLinkFile(@NotNull File file) {
    return file.getName().endsWith(LINK_FILE_SUFFIX);
  }

  public static Collection<File> getLinkFiles(@NotNull File root) {
    return FileUtil.findFiles(new FileFilter() {
      public boolean accept(File pathname) {
        return LinkFile.isLinkFile(pathname);
      }
    }, root);
  }

  public static Collection<File> getTargetFiles(@NotNull File root) throws IOException {
    final List<File> result = new ArrayList<File>();
    for (File linkFile : getLinkFiles(root)) {
      result.add(readFile(linkFile));
    }
    return result;
  }

  @NotNull
  public static File readFile(@NotNull File linkFile) throws IOException {
    return new File(FileUtil.readText(linkFile));
  }

  @Nullable
  public static File getLinkFile(@NotNull final File targetFile, @NotNull File root) {
    final Collection<File> result = FileUtil.findFiles(new FileFilter() {
      public boolean accept(File pathname) {
        try {
          return LinkFile.isLinkFile(pathname) && targetFile.equals(readFile(pathname));
        } catch (IOException e) {
          return false;
        }
      }
    }, root);
    return result.isEmpty() ? null : result.iterator().next();
  }

  @NotNull
  public static File getTorrentFile(@NotNull File linkFile) {
    return new File(linkFile.getAbsolutePath().replace(LINK_FILE_SUFFIX, TorrentUtil.TORRENT_FILE_SUFFIX));
  }
}
