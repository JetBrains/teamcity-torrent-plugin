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

import com.intellij.openapi.util.io.FileUtil;
import com.turn.ttorrent.client.SharedTorrent;
import com.turn.ttorrent.common.Torrent;
import com.turn.ttorrent.tracker.Tracker;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.agent.impl.AgentRunningBuildImpl;
import jetbrains.buildServer.agent.impl.CurrentBuildTrackerImpl;
import jetbrains.buildServer.artifacts.*;
import jetbrains.buildServer.artifactsMirror.seeder.FileLink;
import jetbrains.buildServer.messages.BuildMessage1;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.WaitFor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jmock.Expectations;
import org.jmock.Mock;
import org.jmock.Mockery;
import org.jmock.api.Expectation;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Test
public class AgentTorrentsManagerTest extends BaseTestCase {
  private AgentTorrentsManager myTorrentsManager;
  private Mock myConfigurationMock;
  private EventDispatcher<AgentLifeCycleListener> myDispatcher;
  private File myLinkDir;
  private File myCacheDir;

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myLinkDir = createTempDir();
    myCacheDir = myLinkDir.getParentFile();
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

    final ArtifactCacheProvider cacheProvider = new ArtifactCacheProvider() {
      @NotNull
      public FileCache getHttpCache(@NotNull URLContentRetriever urlContentRetriever) {
        throw new NotImplementedException();
      }

      @NotNull
      public LocalCache getLocalCache() {
        return null;
      }

      @NotNull
      public File getCacheDir() {
        return myCacheDir;
      }

      public void shutdownFlushExecutor() {
        throw new NotImplementedException();
      }

      @NotNull
      public List<CleanableCachedArtifact> getCleanableArtifacts() {
        throw new NotImplementedException();
      }

      public void addListener(@NotNull ArtifactsCacheListener artifactsCacheListener) {

      }

      public void removeListener(@NotNull ArtifactsCacheListener artifactsCacheListener) {

      }
    };

    myTorrentsManager = new AgentTorrentsManager((BuildAgentConfiguration)myConfigurationMock.proxy(),
            myDispatcher, cacheProvider, new CurrentBuildTrackerImpl(myDispatcher), trackerConfiguration);


  }

  @AfterMethod
  public void tearDown(){
    if (!myTorrentsManager.getTorrentsDirectorySeeder().isStopped()){
      myTorrentsManager.getTorrentsDirectorySeeder().stop();
    }
  }

  public void testAnnounceAllOnAgentStarted() throws IOException, URISyntaxException, InterruptedException, NoSuchAlgorithmException {
    Tracker tracker = new Tracker(6969);
    try {

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

      Mock buildAgentMock = mock(BuildAgent.class);
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

  public void test_dont_seed_from_non_cache_folder() throws IOException {
    throw new SkipException("Skipped");
/*
    myCacheDir = createTempDir();
    final File file1 = new File(myCacheDir, "art1.dat");
    createTempFile(512).renameTo(file1);
    final File otherDir = createTempDir();
    final File file2 = new File(otherDir, "art2.dat");
    createTempFile(1024).renameTo(file2);

    Mock buildAgentMock = mock(BuildAgent.class);
    myTorrentsManager.agentStarted((BuildAgent) buildAgentMock.proxy());
    myTorrentsManager.buildStarted((AgentRunningBuild) buildStarted().proxy());

    final HashMap<File, String> fileStringMap = new HashMap<File, String>();
    fileStringMap.put(file1, file1.getName());
    fileStringMap.put(file2, file2.getName());
    myTorrentsManager.publishFiles(fileStringMap);
    final Collection<SharedTorrent> sharedTorrents = myTorrentsManager.getTorrentsDirectorySeeder().getSharedTorrents();
    assertEquals(1, sharedTorrents.size());
    assertEquals("art1.dat", sharedTorrents.iterator().next().getName());
*/
  }

  @NotNull
  private Mock buildStarted() throws IOException {
    Mock buildMock = mock(AgentRunningBuild.class);
    buildMock.stubs().method("getBuildTypeId").will(returnValue("bt1"));
    buildMock.stubs().method("getCheckoutDirectory").will(returnValue(createTempDir()));
    buildMock.stubs().method("getBuildLogger").will(returnValue(new BaseServerLoggerFacade() {
      @Override
      public void flush() {

      }

      @Override
      protected void log(BuildMessage1 buildMessage1) {

      }
    }));
    AgentRunningBuild build = (AgentRunningBuild) buildMock.proxy();
    myDispatcher.getMulticaster().buildStarted(build);
    return buildMock;
  }
}
