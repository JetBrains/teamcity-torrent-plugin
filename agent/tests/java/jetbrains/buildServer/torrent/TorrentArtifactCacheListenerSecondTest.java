package jetbrains.buildServer.torrent;

import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.agent.artifacts.ArtifactsWatcher;
import jetbrains.buildServer.artifacts.ArtifactCacheProvider;
import jetbrains.buildServer.artifacts.ArtifactsCacheListener;
import jetbrains.buildServer.artifacts.impl.DirectoryCacheProviderImpl;
import jetbrains.buildServer.artifacts.impl.SimpleDigestCalculator;
import jetbrains.buildServer.util.EventDispatcher;
import org.apache.commons.io.FileUtils;
import org.hamcrest.Matchers;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Test
public class TorrentArtifactCacheListenerSecondTest extends BaseTestCase {

  private static final String BUILD_EXTERNAL_ID = "Tst_project";
  private static final long BUILD_ID = 15;
  private static final String BUILD_ID_WITH_SUFFIX = BUILD_ID + ".tcbuildid";

  private TorrentArtifactCacheListener myCacheListener;
  private BuildAgentConfiguration myAgentConfiguration;
  private Mockery myM;
  private ArtifactsWatcher myArtifactsWatcher;
  private CurrentBuildTracker myBuildTracker;
  private ArtifactCacheProvider myCacheProvider;
  private File myAgentDirectory;

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
      allowing(build).getBuildTypeExternalId();
      will(returnValue(BUILD_EXTERNAL_ID));
      allowing(build).getBuildId();
      will(returnValue(BUILD_ID));
      allowing(myCacheProvider).addListener(with(any(ArtifactsCacheListener.class)));
    }});

    final TorrentConfiguration configuration = new FakeTorrentConfiguration();
    myArtifactsWatcher = myM.mock(ArtifactsWatcher.class);

    AgentTorrentsSeeder seeder = new AgentTorrentsSeeder(myAgentConfiguration);
    TorrentFilesFactory torrentsFactory = new TorrentFilesFactory(myAgentConfiguration, configuration, new FakeAgentIdleTasks(), seeder);

    final EventDispatcher<AgentLifeCycleListener> eventDispatcher = EventDispatcher.create(AgentLifeCycleListener.class);
    AgentTorrentsManager manager = new AgentTorrentsManager(eventDispatcher, myCacheProvider, myBuildTracker, configuration, seeder, torrentsFactory, myArtifactsWatcher);

    myCacheListener = new TorrentArtifactCacheListener(seeder, myBuildTracker, configuration, manager, torrentsFactory, myArtifactsWatcher);

    myCacheListener.onCacheInitialized(new DirectoryCacheProviderImpl(new File(myAgentDirectory, "cache"), new SimpleDigestCalculator()));
    manager.checkReady();
  }

  public void creatingTorrentCopiesAndSendAsArtifactsTest() throws Exception {

    myM.checking(new Expectations() {{
      one(myArtifactsWatcher).addNewArtifactsPath(with(Matchers.endsWith("test.txt.torrent=>.teamcity/torrents/.")));
    }});

    final File cacheDir = new File(myAgentDirectory, "cache");
    List<String> subDirs = new ArrayList<String>(Constants.CACHE_STATIC_DIRS);
    subDirs.add(BUILD_EXTERNAL_ID);
    subDirs.add(BUILD_ID_WITH_SUFFIX);
    File buildCacheDir = cacheDir;
    for (String subDir : subDirs) {
      buildCacheDir = new File(buildCacheDir, subDir);
    }
    assertTrue(buildCacheDir.mkdirs());
    final File artifact = new File(buildCacheDir, "test.txt");
    FileUtils.writeByteArrayToFile(artifact, new byte[15000000]);
    myCacheListener.onAfterAddOrUpdate(artifact);
    File[] tempFiles = myBuildTracker.getCurrentBuild().getBuildTempDirectory().listFiles();
    assertNotNull(tempFiles);
    File[] torrentCopies = tempFiles[0].listFiles();
    assertNotNull(torrentCopies);
    assertEquals(1, torrentCopies.length);
    assertEquals("test.txt.torrent", torrentCopies[0].getName());
    myM.assertIsSatisfied();
  }

}