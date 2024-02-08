

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