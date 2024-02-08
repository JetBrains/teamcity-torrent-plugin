

package jetbrains.buildServer.torrent;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

public interface FileRemover {

  /**
   * Deletes a specify file.
   *
   * @param path for removing
   * @throws IOException for any I/O errors
   */
  void remove(@NotNull Path path) throws IOException;

}