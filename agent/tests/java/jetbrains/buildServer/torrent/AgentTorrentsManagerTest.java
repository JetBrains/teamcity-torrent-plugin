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

package jetbrains.buildServer.torrent;

import com.turn.ttorrent.client.SharedTorrent;
import com.turn.ttorrent.common.Torrent;
import com.turn.ttorrent.tracker.Tracker;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.agent.AgentLifeCycleListener;
import jetbrains.buildServer.agent.BuildAgent;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.artifacts.*;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.WaitFor;
import org.jetbrains.annotations.NotNull;
import org.jmock.Expectations;
import org.jmock.Mock;
import org.jmock.Mockery;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

@Test
public class AgentTorrentsManagerTest extends BaseTestCase {
  private AgentTorrentsManager myTorrentsManager;
  private Mock myConfigurationMock;
  private EventDispatcher<AgentLifeCycleListener> myDispatcher;
  private File myCacheDir;
  private File mySystemDir;

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    mySystemDir = createTempDir();
    File tempDir = createTempDir();
    myCacheDir = new File(mySystemDir, "caches");
    myDispatcher = EventDispatcher.create(AgentLifeCycleListener.class);

    myConfigurationMock = mock(BuildAgentConfiguration.class);
    myConfigurationMock.stubs().method("getCacheDirectory").will(returnValue(myCacheDir));
    myConfigurationMock.stubs().method("getSystemDirectory").will(returnValue(mySystemDir));
    myConfigurationMock.stubs().method("getTempDirectory").will(returnValue(tempDir));

    Mockery m = new Mockery();
    final TorrentConfiguration trackerConfiguration = m.mock(TorrentConfiguration.class);
    m.checking(new Expectations() {{
      allowing(trackerConfiguration).getFileSizeThresholdMb();will(returnValue(0));
      allowing(trackerConfiguration).getAnnounceUrl();will(returnValue("http://localhost:6969/announce"));
      allowing(trackerConfiguration).getAnnounceIntervalSec();will(returnValue(60));
      allowing(trackerConfiguration).isTransportEnabled();will(returnValue(false));
      allowing(trackerConfiguration).isTorrentEnabled();will(returnValue(true));
    }});

    final ArtifactCacheProvider cacheProvider = new ArtifactCacheProvider() {
      @NotNull
      public FileCache getHttpCache(@NotNull URLContentRetriever urlContentRetriever) {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public LocalCache getLocalCache() {
        return null;
      }

      @NotNull
      public File getCacheDir() {
        return myCacheDir;
      }

      public void addListener(@NotNull ArtifactsCacheListener artifactsCacheListener) {

      }

      public void removeListener(@NotNull ArtifactsCacheListener artifactsCacheListener) {

      }
    };

/*
    myTorrentsManager = new AgentTorrentsManager(
            myDispatcher, cacheProvider, new CurrentBuildTrackerImpl(myDispatcher), trackerConfiguration);
*/


  }

  @AfterMethod
  public void tearDown() throws Exception {
    myTorrentsManager.getTorrentsSeeder().dispose();
    super.tearDown();
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

        myTorrentsManager.getTorrentsSeeder().registerSrcAndTorrentFile(artifactFile, torrentFile, false);
      }

      Mock buildAgentMock = mock(BuildAgent.class);
      myTorrentsManager.agentStarted((BuildAgent) buildAgentMock.proxy());
      new WaitFor(3*1000){

        @Override
        protected boolean condition() {
          return myTorrentsManager.getTorrentsSeeder().getNumberOfSeededTorrents() == torrentsCount;
        }
      };
      List<String> seededHashes = new ArrayList<String>();
      List<File> seededFiles = new ArrayList<File>();
      for (SharedTorrent st : myTorrentsManager.getTorrentsSeeder().getSharedTorrents()) {
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
}
