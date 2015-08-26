package jetbrains.buildServer.torrent;

import com.turn.ttorrent.client.SharedTorrent;
import com.turn.ttorrent.tracker.Tracker;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.artifacts.ArtifactCacheProvider;
import jetbrains.buildServer.artifacts.ArtifactsCacheListener;
import jetbrains.buildServer.artifacts.impl.DirectoryCacheProviderImpl;
import jetbrains.buildServer.artifacts.impl.SimpleDigestCalculator;
import jetbrains.buildServer.messages.BuildMessage1;
import jetbrains.buildServer.torrent.seeder.TorrentsDirectorySeeder;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.Nullable;
import org.jmock.Expectations;
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
  private TorrentsDirectorySeeder mySeeder;
  private File myCacheDir;
  private File myTorrentsDbDir;
  private Tracker myTracker;
  private File mySystemDir;


  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    mySystemDir = createTempDir();
    myCacheDir = new File(mySystemDir, "caches");
    myTorrentsDbDir = new File(mySystemDir, "torrents");
    myTracker = new Tracker(6969);
    myTracker.start(false);

    Mockery m = new Mockery();
    final AgentRunningBuild build = m.mock(AgentRunningBuild.class);
    final BuildProgressLogger logger = new BaseServerLoggerFacade() {
      @Override
      public void flush() {
      }

      @Override
      protected void log(final BuildMessage1 message) {

      }
    };
    final CurrentBuildTracker buildTracker = m.mock(CurrentBuildTracker.class);
    final BuildAgentConfiguration buildAgentConf = m.mock(BuildAgentConfiguration.class);
    final ArtifactCacheProvider cacheProvider = m.mock(ArtifactCacheProvider.class);

    m.checking(new Expectations(){{
      allowing(buildTracker).getCurrentBuild(); will(returnValue(build));
      allowing(build).getBuildLogger(); will(returnValue(logger));
      allowing(build).getAgentConfiguration(); will(returnValue(buildAgentConf));
      allowing(buildAgentConf).getCacheDirectory(AgentTorrentsManager.TORRENT_FOLDER_NAME); will(returnValue(myTorrentsDbDir));
      allowing(buildAgentConf).getSystemDirectory(); will(returnValue(mySystemDir));
      allowing(cacheProvider).addListener(with(any(ArtifactsCacheListener.class)));
    }});

    final TorrentConfiguration configuration = new TorrentConfiguration() {
      @Nullable
      public String getAnnounceUrl() {
        return "http://localhost:6969/announce";
      }

      public int getFileSizeThresholdMb() {
        return 1;
      }

      public int getAnnounceIntervalSec() {
        return 3;
      }

      public boolean isTransportEnabled() {
        return false;
      }

      public boolean isTorrentEnabled() {
        return true;
      }
    };

    AgentTorrentsManager manager = new AgentTorrentsManager(
            build.getAgentConfiguration(),
            EventDispatcher.create(AgentLifeCycleListener.class),
            cacheProvider, buildTracker, configuration);

    mySeeder = manager.getTorrentsDirectorySeeder();
    myCacheListener = new TorrentArtifactCacheListener(mySeeder, buildTracker, configuration, manager);

    myCacheListener.onCacheInitialized(new DirectoryCacheProviderImpl(myCacheDir, new SimpleDigestCalculator()));
    manager.checkReady();
  }


  public void test_seed_when_file_appear() throws IOException {
    File file = createTempFile(1024*1025);

    File newLocation = new File(myCacheDir, CONTEXT_PATH + file.getName());
    FileUtil.rename(file, newLocation);
    myCacheListener.onAfterAddOrUpdate(newLocation);
    assertEquals(1, mySeeder.getNumberOfSeededTorrents());
    final SharedTorrent torrent = mySeeder.getSharedTorrents().iterator().next();
    assertEquals(newLocation.getAbsolutePath(), torrent.getParentFile().getAbsolutePath() + File.separatorChar + torrent.getFilenames().get(0));
  }

  public void test_stop_seed_when_delete() throws IOException {
    File file = createTempFile(1024*1025);

    File newLocation = new File(myCacheDir, CONTEXT_PATH + file.getName());
    FileUtil.rename(file, newLocation);
    myCacheListener.onAfterAddOrUpdate(newLocation);
    assertEquals(1, mySeeder.getNumberOfSeededTorrents());
    final SharedTorrent torrent = mySeeder.getSharedTorrents().iterator().next();
    assertEquals(newLocation.getAbsolutePath(), torrent.getParentFile().getAbsolutePath() + File.separatorChar + torrent.getFilenames().get(0));
    myCacheListener.onBeforeDelete(newLocation);
    assertEquals(0, mySeeder.getNumberOfSeededTorrents());
  }

  public void test_torrent_like_file() throws IOException {
    File file = createTempFile(Integer.MAX_VALUE+":sourcesUpdated\n" +
            Integer.MAX_VALUE+":runnerFinished:Fetch artifacts (Ant)\n" +
            Integer.MAX_VALUE+":runnerFinished:IntelliJ IDEA Project");

    File newLocation = new File(myCacheDir, CONTEXT_PATH + file.getName());
    FileUtil.rename(file, newLocation);
    final long totalBefore = Runtime.getRuntime().totalMemory();
    final int ONEGB = 1024 * 1024 * 1024;
    if (totalBefore >= ONEGB) {
      throw new RuntimeException("Memory total 1GB before the test!");
    }
    myCacheListener.onAfterAddOrUpdate(newLocation);
    final long totalAfter = Runtime.getRuntime().totalMemory();
    assertTrue(totalAfter-totalBefore < ONEGB/10);
    assertEquals(0, mySeeder.getNumberOfSeededTorrents());
  }

  @AfterMethod
  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    myTracker.stop();
    mySeeder.dispose();
  }
}
