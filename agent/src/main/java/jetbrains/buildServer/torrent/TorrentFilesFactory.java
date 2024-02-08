

package jetbrains.buildServer.torrent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public interface TorrentFilesFactory {

  @Nullable
  File createTorrentFile(@NotNull File srcFile);

  @NotNull
  File getTorrentFile() throws IOException;

}