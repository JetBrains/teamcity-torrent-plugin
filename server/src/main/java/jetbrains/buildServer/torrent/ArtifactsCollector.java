

package jetbrains.buildServer.torrent;

import jetbrains.buildServer.serverSide.artifacts.BuildArtifact;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifacts;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface ArtifactsCollector {

  /**
   * Method for create collection with any conditions from buildArtifacts object
   *
   * @param buildArtifacts artifacts for creating collection
   * @return list of selected artifacts
   */
  @NotNull List<BuildArtifact> collectArtifacts(@NotNull BuildArtifacts buildArtifacts);

}