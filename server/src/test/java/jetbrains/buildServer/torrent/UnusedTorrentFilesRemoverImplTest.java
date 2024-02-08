

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