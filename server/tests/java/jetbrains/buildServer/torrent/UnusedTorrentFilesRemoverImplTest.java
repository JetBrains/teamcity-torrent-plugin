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

import jetbrains.buildServer.serverSide.artifacts.BuildArtifact;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.testng.Assert.assertEquals;

@Test
public class UnusedTorrentFilesRemoverImplTest {


  public void removeUnusedTorrentsTest() throws IOException {
    Set<String> actual = new HashSet<>();
    UnusedTorrentFilesRemoverImpl unusedTorrentFilesRemover = new UnusedTorrentFilesRemoverImpl(
            (path) -> {
              actual.add(path.toString());
            },
            (path, visitor) -> {
              visitor.visitFile(Paths.get(".teamcity", "torrents", "exist.torrent"), null);
              visitor.visitFile(Paths.get(".teamcity", "torrents", "notExist.torrent"), null);
              visitor.visitFile(Paths.get(".teamcity", "torrents", "dir", "exist.torrent"), null);
              visitor.visitFile(Paths.get(".teamcity", "torrents", "dir", "notExist.torrent"), null);
            }
    );
    Path torrentsDir = Paths.get(".teamcity", "torrents");
    List<BuildArtifact> buildArtifacts = Arrays.asList(
            createBuildArtifact("exist", "exist"),
            createBuildArtifact("exist", "dir/exist")
    );
    unusedTorrentFilesRemover.removeUnusedTorrents(buildArtifacts, torrentsDir);
    Set<String> expected = new HashSet<>();
    expected.add(Paths.get(".teamcity", "torrents", "notExist.torrent").toString());
    expected.add(Paths.get(".teamcity", "torrents", "dir", "notExist.torrent").toString());
    assertEquals(actual, expected);
  }

  private BuildArtifact createBuildArtifact(final String name, final String relativePath) {
    return new DummyBuildArtifactAdapter() {
      @NotNull
      @Override
      public String getRelativePath() {
        return FileUtil.toSystemIndependentName(relativePath);
      }

      @NotNull
      @Override
      public String getName() {
        return name;
      }
    };
  }
}
