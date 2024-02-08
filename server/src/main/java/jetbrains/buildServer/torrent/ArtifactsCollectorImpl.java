

package jetbrains.buildServer.torrent;

import jetbrains.buildServer.ArtifactsConstants;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifact;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifacts;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Artifacts collector, that collect only artifact-files and skip .teamcity system directory
 */
public class ArtifactsCollectorImpl implements ArtifactsCollector {

  @Override
  @NotNull
  public List<BuildArtifact> collectArtifacts(@NotNull BuildArtifacts buildArtifacts) {
    final List<BuildArtifact> result = new ArrayList<>();
    buildArtifacts.iterateArtifacts(artifact -> handleArtifact(result, artifact));
    return result;
  }

  @NotNull
  private BuildArtifacts.BuildArtifactsProcessor.Continuation handleArtifact(List<BuildArtifact> result, BuildArtifact artifact) {
    if (artifact.getName().equals(ArtifactsConstants.TEAMCITY_ARTIFACTS_DIR)) {
      return BuildArtifacts.BuildArtifactsProcessor.Continuation.SKIP_CHILDREN;
    }
    if (artifact.isFile()) {
      result.add(artifact);
    }
    return BuildArtifacts.BuildArtifactsProcessor.Continuation.CONTINUE;
  }
}