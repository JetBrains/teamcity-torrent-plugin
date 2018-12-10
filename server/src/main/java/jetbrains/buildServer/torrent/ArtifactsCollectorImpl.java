/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
