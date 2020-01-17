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
import org.apache.commons.io.FileUtils;
import org.hamcrest.Matchers;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;

@Test
public class TorrentArtifactCacheListenerSecondTest extends BaseTestCase {

  private static final String BUILD_EXTERNAL_ID = "Tst_project";
  private static final long BUILD_ID = 15;
  private static final String BUILD_ID_WITH_SUFFIX = BUILD_ID + ".tcbuildid";

  private TorrentArtifactCacheListener myCacheListener;
  private BuildAgentConfiguration myAgentConfiguration;
  private Mockery myM;
  private ArtifactsWatcherEx myArtifactsWatcher;
  private CurrentBuildTracker myBuildTracker;
  private ArtifactCacheProvider myCacheProvider;
  private File myAgentDirectory;
  private AgentTorrentsManager myManager;

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myAgentDirectory = createTempDir();

    myM = new Mockery();
    final AgentRunningBuild build = myM.mock(AgentRunningBuild.class);
    final BuildProgressLogger logger = new FakeBuildProgressLogger();
    myBuildTracker = myM.mock(CurrentBuildTracker.class);
    myCacheProvider = myM.mock(ArtifactCacheProvider.class);
    myAgentConfiguration = myM.mock(BuildAgentConfiguration.class);
    final LeechSettings leechSettings = myM.mock(LeechSettings.class);
    final SeedSettings seedingSettings = myM.mock(SeedSettings.class);
    myM.checking(new Expectations() {{
      allowing(myBuildTracker).getCurrentBuild();
      will(returnValue(build));
      allowing(build).getBuildLogger();
      will(returnValue(logger));
      allowing(build).getAgentConfiguration();
      will(returnValue(myAgentConfiguration));
      allowing(build).getBuildTempDirectory();
      will(returnValue(new File(myAgentDirectory, "temp")));
      allowing(myAgentConfiguration).getSystemDirectory();
      will(returnValue(new File(myAgentDirectory, "system")));
      allowing(myAgentConfiguration).getCacheDirectory(with(Constants.TORRENTS_DIRNAME));
      will(returnValue(new File(myAgentDirectory, "torrents")));
      allowing(myAgentConfiguration).getServerUrl(); will(returnValue("http://localhost:6969/bs"));
      allowing(build).getBuildTypeExternalId();
      will(returnValue(BUILD_EXTERNAL_ID));
      allowing(build).getBuildId();
      will(returnValue(BUILD_ID));
      allowing(myCacheProvider).addListener(with(any(ArtifactsCacheListener.class)));
      allowing(leechSettings).isDownloadEnabled(); will(returnValue(true));
      allowing(seedingSettings).isSeedingEnabled(); will(returnValue(true));
    }});

    final TorrentConfiguration configuration = new FakeTorrentConfiguration();
    myArtifactsWatcher = myM.mock(ArtifactsWatcherEx.class);

    AgentTorrentsSeeder seeder = new AgentTorrentsSeeder(myAgentConfiguration, configuration);
    TorrentFilesFactoryImpl torrentsFactory = new TorrentFilesFactoryImpl(myAgentConfiguration, configuration, new FakeAgentIdleTasks(), seeder);

    final EventDispatcher<AgentLifeCycleListener> eventDispatcher = EventDispatcher.create(AgentLifeCycleListener.class);
    myManager = new AgentTorrentsManager(eventDispatcher,
            myCacheProvider,
            myBuildTracker,
            configuration,
            seeder,
            torrentsFactory,
            myArtifactsWatcher,
            new TorrentsDownloadStatistic(),
            leechSettings,
            myAgentConfiguration, seedingSettings);

    myCacheListener = new TorrentArtifactCacheListener(seeder.getTorrentsSeeder(), myBuildTracker, configuration, myManager, torrentsFactory, myAgentConfiguration);

    myCacheListener.onCacheInitialized(new DirectoryCacheProviderImpl(new File(myAgentDirectory, "cache"), new SimpleDigestCalculator()));
    myManager.checkReady();
  }

  public void creatingTorrentCopiesAndSendAsArtifactsTest() throws Exception {

    myM.checking(new Expectations() {{
      one(myArtifactsWatcher).addInternalArtifactsPath(with(Matchers.endsWith("test.txt.torrent=>.teamcity/torrents/.")));
    }});
    final File cacheDir = new File(myAgentDirectory, "cache");
    File buildStaticDir = new File(cacheDir, "localhost_6969" + File.separator + "bs" + File.separator + Constants.CACHE_STATIC_DIRS);
    File buildProjectDir = new File(buildStaticDir, BUILD_EXTERNAL_ID);
    File buildDir = new File(buildProjectDir, BUILD_ID_WITH_SUFFIX);
    assertTrue(buildDir.mkdirs());
    final File artifact = new File(buildDir, "test.txt");
    FileUtils.writeByteArrayToFile(artifact, new byte[15000000]);
    myCacheListener.onAfterAddOrUpdate(artifact);
    File[] tempFiles = myBuildTracker.getCurrentBuild().getBuildTempDirectory().listFiles();
    myManager.afterAtrifactsPublished(myBuildTracker.getCurrentBuild(), BuildFinishedStatus.FINISHED_SUCCESS);
    assertNotNull(tempFiles);
    File[] torrentCopies = tempFiles[0].listFiles();
    assertNotNull(torrentCopies);
    assertEquals(1, torrentCopies.length);
    assertEquals("test.txt.torrent", torrentCopies[0].getName());
    myM.assertIsSatisfied();
  }
}
