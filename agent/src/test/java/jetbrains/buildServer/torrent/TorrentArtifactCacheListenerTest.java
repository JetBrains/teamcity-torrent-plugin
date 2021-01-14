/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

import com.turn.ttorrent.client.LoadedTorrent;
import com.turn.ttorrent.common.TorrentMetadata;
import com.turn.ttorrent.tracker.Tracker;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.agent.impl.artifacts.ArtifactsWatcherEx;
import jetbrains.buildServer.artifacts.ArtifactCacheProvider;
import jetbrains.buildServer.artifacts.ArtifactsCacheListener;
import jetbrains.buildServer.artifacts.impl.DirectoryCacheProviderImpl;
import jetbrains.buildServer.artifacts.impl.SimpleDigestCalculator;
import jetbrains.buildServer.torrent.settings.LeechSettings;
import jetbrains.buildServer.torrent.settings.SeedSettings;
import jetbrains.buildServer.torrent.util.TorrentsDownloadStatistic;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.WaitFor;
import org.jetbrains.annotations.NotNull;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

/**
 * @author Sergey.Pak
 *         Date: 10/4/13
 *         Time: 6:35 PM
 */
@Test
public class TorrentArtifactCacheListenerTest extends BaseTestCase {

  private static final String CONTEXT_PATH = "httpAuth/repository/download/TC_Gaya80x_BuildDist/2063228.tcbuildid/";

  private TorrentArtifactCacheListener myCacheListener;
  private AgentTorrentsSeeder mySeeder;
  private Tracker myTracker;
  private BuildAgentConfigurationFixture myAgentConfigurationFixture = new BuildAgentConfigurationFixture();
  private BuildAgentConfiguration myAgentConfiguration;


  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myTracker = new Tracker(6969);
    myTracker.start(false);

    myAgentConfiguration = myAgentConfigurationFixture.setUp();

    Mockery m = new Mockery();
    final AgentRunningBuild build = m.mock(AgentRunningBuild.class);
    final BuildProgressLogger logger = new FakeBuildProgressLogger();
    final CurrentBuildTracker buildTracker = m.mock(CurrentBuildTracker.class);
    final ArtifactCacheProvider cacheProvider = m.mock(ArtifactCacheProvider.class);
    final LeechSettings leechSettings = m.mock(LeechSettings.class);
    final SeedSettings seedingSettings = m.mock(SeedSettings.class);

    m.checking(new Expectations(){{
      allowing(buildTracker).getCurrentBuild(); will(returnValue(build));
      allowing(build).getBuildLogger(); will(returnValue(logger));
      allowing(build).getAgentConfiguration(); will(returnValue(myAgentConfiguration));
      allowing(cacheProvider).addListener(with(any(ArtifactsCacheListener.class)));
      allowing(build).getBuildTypeExternalId(); will(returnValue("1"));
      allowing(build).getBuildId(); will(returnValue(1L));
      allowing(leechSettings).isDownloadEnabled(); will(returnValue(true));
      allowing(seedingSettings).isSeedingEnabled(); will(returnValue(true));
    }});

    final TorrentConfiguration configuration = new FakeTorrentConfiguration();
    final ArtifactsWatcherEx artifactsWatcher = m.mock(ArtifactsWatcherEx.class);

    mySeeder = new AgentTorrentsSeeder(myAgentConfiguration, configuration);
    TorrentFilesFactoryImpl torrentsFactory = new TorrentFilesFactoryImpl(myAgentConfiguration, configuration, new FakeAgentIdleTasks(), mySeeder);

    final EventDispatcher<AgentLifeCycleListener> eventDispatcher = EventDispatcher.create(AgentLifeCycleListener.class);
    AgentTorrentsManager manager = new AgentTorrentsManager(eventDispatcher,
            cacheProvider,
            buildTracker,
            configuration,
            mySeeder,
            torrentsFactory,
            artifactsWatcher,
            new TorrentsDownloadStatistic(),
            leechSettings,
            myAgentConfiguration, seedingSettings);

    myCacheListener = new TorrentArtifactCacheListener(mySeeder.getTorrentsSeeder(), buildTracker, configuration, manager, torrentsFactory, myAgentConfiguration);

    myCacheListener.onCacheInitialized(new DirectoryCacheProviderImpl(getTorrentsDirectory(), new SimpleDigestCalculator()));
    manager.checkReady();
  }

  public void test_seed_when_file_appear() throws IOException, NoSuchAlgorithmException {
    File file = createTempFile(1024*1025);

    myCacheListener.onAfterAddOrUpdate(file);

    waitForSeededTorrents(1);

    assertEquals(1, mySeeder.getNumberOfSeededTorrents());
    final LoadedTorrent loadedTorrent = mySeeder.getTorrentsSeeder().getClient().getLoadedTorrents().iterator().next();
    TorrentMetadata metadata = loadedTorrent.getMetadata();
    assertEquals(file.getAbsolutePath(), file.getParent() + File.separatorChar + metadata.getFiles().get(0).getRelativePathAsString());
  }

  @NotNull
  private File getTorrentsDirectory() {
    return myAgentConfiguration.getCacheDirectory(Constants.TORRENTS_DIRNAME);
  }

  private void waitForSeededTorrents(final int numSeededTorrents) {
    new WaitFor() {
      @Override
      protected boolean condition() {
        return mySeeder.getNumberOfSeededTorrents() == numSeededTorrents;
      }
    };
  }

  public void test_stop_seed_when_delete() throws IOException, NoSuchAlgorithmException {
    File file = createTempFile(1024*1025);

    myCacheListener.onAfterAddOrUpdate(file);

    waitForSeededTorrents(1);

    final LoadedTorrent loadedTorrentTorrent = mySeeder.getTorrentsSeeder().getClient().getLoadedTorrents().iterator().next();
    TorrentMetadata metadata = loadedTorrentTorrent.getMetadata();
    assertEquals(file.getAbsolutePath(), file.getParent() + File.separatorChar + metadata.getFiles().get(0).getRelativePathAsString());
    myCacheListener.onBeforeDelete(file);
    assertEquals(0, mySeeder.getNumberOfSeededTorrents());
  }

  public void test_torrent_like_file() throws IOException {
    File file = createTempFile(Integer.MAX_VALUE+":sourcesUpdated\n" +
            Integer.MAX_VALUE+":runnerFinished:Fetch artifacts (Ant)\n" +
            Integer.MAX_VALUE+":runnerFinished:IntelliJ IDEA Project");

    final long totalBefore = Runtime.getRuntime().totalMemory();
    final int ONEGB = 1024 * 1024 * 1024;
    if (totalBefore >= ONEGB) {
      throw new RuntimeException("Memory total 1GB before the test!");
    }
    myCacheListener.onAfterAddOrUpdate(file);
    final long totalAfter = Runtime.getRuntime().totalMemory();
    assertTrue(totalAfter-totalBefore < ONEGB/10);
    assertEquals(0, mySeeder.getNumberOfSeededTorrents());
  }

  @AfterMethod
  @Override
  protected void tearDown() throws Exception {
    mySeeder.dispose();
    myTracker.stop();
    myAgentConfigurationFixture.tearDown();
    super.tearDown();
  }
}
