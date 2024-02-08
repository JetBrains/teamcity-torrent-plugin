

package jetbrains.buildServer.torrent.seeder;

import org.jetbrains.annotations.NotNull;

import java.io.File;

public interface PathConverter {
  @NotNull
  File convertToFile(@NotNull String path);

  @NotNull
  String convertToPath(@NotNull File file);
}