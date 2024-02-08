

package jetbrains.buildServer.torrent;

import jetbrains.buildServer.serverSide.artifacts.BuildArtifact;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface ArtifactProcessor {

  /**
   * Method for any processing artifact list. The list must not contains null
   *
   * @param artifacts list for processing
   */
  void processArtifacts(@NotNull List<BuildArtifact> artifacts);

}