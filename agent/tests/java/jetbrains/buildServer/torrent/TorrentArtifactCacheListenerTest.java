package jetbrains.buildServer.torrent;

import com.turn.ttorrent.client.SharedTorrent;
import com.turn.ttorrent.tracker.Tracker;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.agent.artifacts.ArtifactsWatcher;
import jetbrains.buildServer.artifacts.ArtifactCacheProvider;
import jetbrains.buildServer.artifacts.ArtifactsCacheListener;
import jetbrains.buildServer.artifacts.impl.DirectoryCacheProviderImpl;
import jetbrains.buildServer.artifacts.impl.SimpleDigestCalculator;
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

    m.checking(new Expectations(){{
      allowing(buildTracker).getCurrentBuild(); will(returnValue(build));
      allowing(build).getBuildLogger(); will(returnValue(logger));
      allowing(build).getAgentConfiguration(); will(returnValue(myAgentConfiguration));
      allowing(cacheProvider).addListener(with(any(ArtifactsCacheListener.class)));
      allowing(build).getBuildTypeExternalId(); will(returnValue("1"));
      allowing(build).getBuildId(); will(returnValue(1L));
    }});

    final TorrentConfiguration configuration = new FakeTorrentConfiguration();
    final ArtifactsWatcher artifactsWatcher = m.mock(ArtifactsWatcher.class);

    mySeeder = new AgentTorrentsSeeder(myAgentConfiguration);
    TorrentFilesFactory torrentsFactory = new TorrentFilesFactory(myAgentConfiguration, configuration, new FakeAgentIdleTasks(), mySeeder);

    final EventDispatcher<AgentLifeCycleListener> eventDispatcher = EventDispatcher.create(AgentLifeCycleListener.class);
    AgentTorrentsManager manager = new AgentTorrentsManager(eventDispatcher, cacheProvider, buildTracker, configuration, mySeeder, torrentsFactory, artifactsWatcher);

    myCacheListener = new TorrentArtifactCacheListener(mySeeder, buildTracker, configuration, manager, torrentsFactory, artifactsWatcher);

    myCacheListener.onCacheInitialized(new DirectoryCacheProviderImpl(getTorrentsDirectory(), new SimpleDigestCalculator()));
    manager.checkReady();
  }

  public void test_seed_when_file_appear() throws IOException {
    File file = createTempFile(1024*1025);

    myCacheListener.onAfterAddOrUpdate(file);

    waitForSeededTorrents(1);

    assertEquals(1, mySeeder.getNumberOfSeededTorrents());
    final SharedTorrent torrent = mySeeder.getSharedTorrents().iterator().next();
    assertEquals(file.getAbsolutePath(), torrent.getParentFile().getAbsolutePath() + File.separatorChar + torrent.getFilenames().get(0));
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

  public void test_stop_seed_when_delete() throws IOException {
    File file = createTempFile(1024*1025);

    myCacheListener.onAfterAddOrUpdate(file);

    waitForSeededTorrents(1);

    final SharedTorrent torrent = mySeeder.getSharedTorrents().iterator().next();
    assertEquals(file.getAbsolutePath(), torrent.getParentFile().getAbsolutePath() + File.separatorChar + torrent.getFilenames().get(0));
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
