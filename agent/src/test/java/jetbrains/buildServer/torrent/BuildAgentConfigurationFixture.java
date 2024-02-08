

package jetbrains.buildServer.torrent;

import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import org.jmock.Expectations;
import org.jmock.Mockery;

import java.io.File;
import java.io.IOException;

public class BuildAgentConfigurationFixture {
  private TempFiles myTmpFiles;

  public BuildAgentConfiguration setUp() throws IOException {
    myTmpFiles = new TempFiles();
    final File systemDir = myTmpFiles.createTempDir();
    final File torrentsDir = myTmpFiles.createTempDir();

    Mockery m = new Mockery();
    final BuildAgentConfiguration agentConfiguration = m.mock(BuildAgentConfiguration.class);
    m.checking(new Expectations() {{
      allowing(agentConfiguration).getServerUrl(); will(returnValue("http://localhost:8111/bs"));
      allowing(agentConfiguration).getSystemDirectory(); will(returnValue(systemDir));
      allowing(agentConfiguration).getCacheDirectory(with(Constants.TORRENTS_DIRNAME)); will(returnValue(torrentsDir));
    }});

    return agentConfiguration;
  }

  public void tearDown() {
    myTmpFiles.cleanup();
  }
}