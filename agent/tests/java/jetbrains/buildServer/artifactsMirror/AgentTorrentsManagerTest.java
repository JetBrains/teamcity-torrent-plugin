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

import com.turn.ttorrent.client.SharedTorrent;
import com.turn.ttorrent.common.Torrent;
import com.turn.ttorrent.tracker.Tracker;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.agent.AgentLifeCycleListener;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.BuildAgent;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.artifactsMirror.seeder.FileLink;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.WaitFor;
import org.jetbrains.annotations.NotNull;
import org.jmock.Expectations;
import org.jmock.Mock;
import org.jmock.Mockery;
import org.testng.SkipException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Test
public class AgentTorrentsManagerTest extends BaseTestCase {
  private AgentTorrentsManager myTorrentsManager;
  private Mock myConfigurationMock;
  private EventDispatcher<AgentLifeCycleListener> myDispatcher;
  private File myLinkDir;

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myLinkDir = createTempDir();
    myDispatcher = EventDispatcher.create(AgentLifeCycleListener.class);

    myConfigurationMock = mock(BuildAgentConfiguration.class);
    myConfigurationMock.stubs().method("getCacheDirectory").will(returnValue(myLinkDir));

    Mockery m = new Mockery();
    final TorrentTrackerConfiguration trackerConfiguration = m.mock(TorrentTrackerConfiguration.class);
    m.checking(new Expectations() {{
      allowing(trackerConfiguration).getFileSizeThresholdMb();
      will(returnValue(0));
      allowing(trackerConfiguration).getAnnounceUrl();
      will(returnValue("http://localhost:6969/announce"));
      allowing(trackerConfiguration).getAnnounceIntervalSec();
      will(returnValue(60));
    }});

    myTorrentsManager = new AgentTorrentsManager((BuildAgentConfiguration)myConfigurationMock.proxy(),
            myDispatcher, trackerConfiguration);
    myTorrentsManager.getTorrentsDirectorySeeder().start(new InetAddress[]{InetAddress.getLocalHost()}, null, 60);

  }

  public void testAnnounceAllOnAgentStarted() throws IOException, URISyntaxException, InterruptedException, NoSuchAlgorithmException {
    Tracker tracker = new Tracker(6969);
    try {
      Mock buildAgentMock = mock(BuildAgent.class);

      final List<String> torrentHashes = new ArrayList<String>();
      final List<File> createdFiles = new ArrayList<File>();
      final int torrentsCount = 10;
      tracker.start(true);
      for (int i = 0; i < torrentsCount; i++) {
        final File artifactFile = createTempFile(65535);
        createdFiles.add(artifactFile);
        File torrentDir = createTempDir();
        final Torrent torrent = Torrent.create(artifactFile, tracker.getAnnounceURI(), "tc-plugin-test");
        final File torrentFile = new File(torrentDir, artifactFile.getName() + ".torrent");
        torrentHashes.add(torrent.getHexInfoHash());
        torrent.save(torrentFile);
        FileLink.createLink(artifactFile, torrentFile, myLinkDir);
      }

      myTorrentsManager.agentStarted((BuildAgent) buildAgentMock.proxy());
      new WaitFor(3*1000){

        @Override
        protected boolean condition() {
          return myTorrentsManager.getTorrentsDirectorySeeder().getNumberOfSeededTorrents() == torrentsCount;
        }
      };
      List<String> seededHashes = new ArrayList<String>();
      List<File> seededFiles = new ArrayList<File>();
      for (SharedTorrent st : myTorrentsManager.getTorrentsDirectorySeeder().getSharedTorrents()) {
        seededHashes.add(st.getHexInfoHash());
        seededFiles.add(new File(st.getParentFile(), st.getName()));
      }
      assertSameElements(torrentHashes, seededHashes);
      assertSameElements(createdFiles, seededFiles);

    } finally {
      myTorrentsManager.agentShutdown();
      tracker.stop();
    }
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
    }, new File(myLinkDir, "bt1"));

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
    }, new File(myLinkDir, "bt1"));

    assertEquals(3, links.size());
    for (File l: links) {
      assertTrue(files.keySet().contains(FileLink.getTargetFile(l)));
    }

    assertTrue(new File(myLinkDir, "bt1/a/b/f.jar.link").isFile());
    assertTrue(new File(myLinkDir, "bt1/a/f.jar.link").isFile());
    assertTrue(new File(myLinkDir, "bt1/f.jar.link").isFile());
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
