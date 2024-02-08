

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