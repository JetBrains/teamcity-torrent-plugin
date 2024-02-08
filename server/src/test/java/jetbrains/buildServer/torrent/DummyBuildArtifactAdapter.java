

package jetbrains.buildServer.torrent;

import jetbrains.buildServer.serverSide.artifacts.BuildArtifact;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

/**
 * @author Sergey.Pak
 *         Date: 9/5/13
 *         Time: 9:09 PM
 */
public class DummyBuildArtifactAdapter implements BuildArtifact {

  public boolean isDirectory() {
    return false;
  }

  public boolean isArchive() {
    return false;
  }

  public boolean isFile() {
    return false;
  }

  public boolean isContainer() {
    return false;
  }

  public long getSize() {
    return 0;
  }

  public long getTimestamp() {
    return 0;
  }

  @NotNull
  public InputStream getInputStream() throws IOException {
    return null;
  }

  @NotNull
  public Collection<BuildArtifact> getChildren() {
    return null;
  }

  @NotNull
  public String getRelativePath() {
    return null;
  }

  @NotNull
  public String getName() {
    return null;
  }
}