/*
 * Copyright 2000-2020 JetBrains s.r.o.
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
