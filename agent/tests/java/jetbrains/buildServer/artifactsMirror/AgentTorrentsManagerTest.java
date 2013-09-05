/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package jetbrains.buildServer.artifactsMirror;

import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.agent.AgentLifeCycleListener;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.BuildAgent;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.artifactsMirror.seeder.FileLink;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jmock.Mock;
import org.testng.SkipException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Test
public class AgentTorrentsManagerTest extends BaseTestCase {
  private AgentTorrentsManager myTorrentsManager;
  private Mock myTorrentTrackerConfigurationMock;
  private Mock myConfigurationMock;
  private EventDispatcher<AgentLifeCycleListener> myDispatcher;
  private File myTorrentCacheDir;

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myTorrentCacheDir = createTempDir();
    myDispatcher = EventDispatcher.create(AgentLifeCycleListener.class);

    myConfigurationMock = mock(BuildAgentConfiguration.class);
    myConfigurationMock.stubs().method("getCacheDirectory").will(returnValue(myTorrentCacheDir));

    myTorrentTrackerConfigurationMock = mock(TorrentTrackerConfiguration.class);
    myTorrentTrackerConfigurationMock.stubs().method("getAnnounceUrl").will(returnValue("http://localhost:6969/announce"));
    myTorrentTrackerConfigurationMock.stubs().method("getFileSizeThresholdMb").will(returnValue(0));

    myTorrentsManager = new AgentTorrentsManager((BuildAgentConfiguration)myConfigurationMock.proxy(),
            myDispatcher, (TorrentTrackerConfiguration)myTorrentTrackerConfigurationMock.proxy());
    myTorrentsManager.getTorrentsDirectorySeeder().start(InetAddress.getLocalHost(), null, 60);
  }

  public void test_links_created_when_artifact_is_published() throws Exception {
    throw new SkipException("Temporary skipped");
/*
    buildStarted();
    myTorrentsManager.getTorrentsDirectorySeeder().setFileSizeThresholdMb(0);
    Map<File, String> files = new HashMap<File, String>();
    for (int i=0; i<3; i++) {
      files.put(createTempFile(), "");
    }

    myTorrentsManager.publishFiles(files);

    Collection<File> links = FileUtil.findFiles(new FileFilter() {
      public boolean accept(File file) {
        return FileLink.isLink(file);
      }
    }, new File(myTorrentCacheDir, "bt1"));

    assertEquals(3, links.size());
    for (File l: links) {
      assertTrue(files.containsKey(FileLink.getTargetFile(l)));
    }
*/
  }

  public void test_links_created_when_artifact_is_published_take_checkout_dir_into_account() throws Exception {
    throw new SkipException("Temporary skipped");
/*
    Mock buildMock = buildStarted();

    myTorrentsManager.getTorrentsDirectorySeeder().setFileSizeThresholdMb(0);

    AgentRunningBuild build = (AgentRunningBuild) buildMock.proxy();
    Map<File, String> files = new HashMap<File, String>();
    files.put(new File(build.getCheckoutDirectory(), "a/b/f.jar"), "");
    files.put(new File(build.getCheckoutDirectory(), "a/f.jar"), "");
    files.put(new File(build.getCheckoutDirectory(), "f.jar"), "");

    for (File f: files.keySet()) {
      f.getParentFile().mkdirs();
      assertTrue(f.createNewFile());
    }

    myTorrentsManager.publishFiles(files);

    Collection<File> links = FileUtil.findFiles(new FileFilter() {
      public boolean accept(File file) {
        return FileLink.isLink(file);
      }
    }, new File(myTorrentCacheDir, "bt1"));

    assertEquals(3, links.size());
    for (File l: links) {
      assertTrue(files.keySet().contains(FileLink.getTargetFile(l)));
    }

    assertTrue(new File(myTorrentCacheDir, "bt1/a/b/f.jar.link").isFile());
    assertTrue(new File(myTorrentCacheDir, "bt1/a/f.jar.link").isFile());
    assertTrue(new File(myTorrentCacheDir, "bt1/f.jar.link").isFile());
*/
  }

  public void agent_started_event() {
    throw new SkipException("Skipped");
/*
    Mock buildAgentMock = mock(BuildAgent.class);
    buildAgentMock.stubs().method("getConfiguration").will(returnValue(myConfigurationMock.proxy()));
    myConfigurationMock.stubs().method("getOwnAddress").will(returnValue("127.0.0.1"));

    try {
      myTorrentsManager.agentStarted((BuildAgent) buildAgentMock.proxy());
    } finally {
      myTorrentsManager.agentShutdown();
    }
*/
  }

  @NotNull
  private Mock buildStarted() throws IOException {
    Mock buildMock = mock(AgentRunningBuild.class);
    buildMock.stubs().method("getBuildTypeId").will(returnValue("bt1"));
    buildMock.stubs().method("getCheckoutDirectory").will(returnValue(createTempDir()));
    AgentRunningBuild build = (AgentRunningBuild) buildMock.proxy();
    myDispatcher.getMulticaster().buildStarted(build);
    return buildMock;
  }
}
