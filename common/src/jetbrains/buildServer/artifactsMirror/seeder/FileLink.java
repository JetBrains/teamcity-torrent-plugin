package jetbrains.buildServer.artifactsMirror.seeder;

import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

/**
 * User: Victory.Bedrosova
 * Date: 10/15/12
 * Time: 4:12 PM
 */
public class FileLink {
  public static final String LINK_FILE_SUFFIX = ".link";
  private static final String LINK_FILE_ENCODING = "UTF-8";

  @NotNull
  private final File myFile;
  @NotNull
  private final File mySrcFile;

  private FileLink(@NotNull File root, @NotNull File srcFile) {
    mySrcFile = srcFile;
    myFile = new File(root, srcFile.getName() + LINK_FILE_SUFFIX);
  }

  @NotNull
  private File save() throws IOException {
    FileUtil.createParentDirs(myFile);
    FileUtil.writeFile(myFile, mySrcFile.getAbsolutePath(), LINK_FILE_ENCODING);
    return myFile;
  }

  public static boolean isLink(@NotNull File file) {
    return file.getName().endsWith(LINK_FILE_SUFFIX);
  }

  @NotNull
  public static File getTargetFile(@NotNull File linkFile) throws IOException {
    return new File(FileUtil.readText(linkFile, LINK_FILE_ENCODING));
  }

  @NotNull
  public static File createLink(@NotNull File srcFile, @NotNull File storageDir) throws IOException {
    return new FileLink(storageDir, srcFile).save();
  }
}
