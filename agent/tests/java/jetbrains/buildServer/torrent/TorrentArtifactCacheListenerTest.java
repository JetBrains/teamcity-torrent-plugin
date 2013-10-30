package jetbrains.buildServer.torrent;

import com.turn.ttorrent.client.SharedTorrent;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.BaseServerLoggerFacade;
import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.agent.CurrentBuildTracker;
import jetbrains.buildServer.artifacts.impl.DirectoryCacheProviderImpl;
import jetbrains.buildServer.artifacts.impl.SimpleDigestCalculator;
import jetbrains.buildServer.torrent.seeder.TorrentsDirectorySeeder;
import jetbrains.buildServer.messages.BuildMessage1;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nullable;
import org.jmock.Expectations;
import org.jmock.Mockery;
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
  private File myLinksDir;


  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myCacheDir = createTempDir();
    myLinksDir = createTempDir();
    mySeeder = new TorrentsDirectorySeeder(myLinksDir, 10, 1);

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
    m.checking(new Expectations(){{
      allowing(buildTracker).getCurrentBuild(); will(returnValue(build));
      allowing(build).getBuildLogger(); will(returnValue(logger));
    }});

    myCacheListener = new TorrentArtifactCacheListener(mySeeder,buildTracker, new TorrentTrackerConfiguration() {
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
    });

    myCacheListener.onCacheInitialized(new DirectoryCacheProviderImpl(myCacheDir, new SimpleDigestCalculator()));
  }


  public void test_seed_when_file_appear() throws IOException {
    File file = createTempFile(1024*1025);

    File newLocation = new File(myCacheDir, CONTEXT_PATH + file.getName());
    FileUtils.moveFile(file, newLocation);
    myCacheListener.onAfterAddOrUpdate(newLocation);
    assertEquals(1, mySeeder.getNumberOfSeededTorrents());
    final SharedTorrent torrent = mySeeder.getSharedTorrents().iterator().next();
    assertEquals(newLocation.getAbsolutePath(), torrent.getParentFile().getAbsolutePath() + File.separatorChar + torrent.getFilenames().get(0));
  }

  public void test_stop_seed_when_delete() throws IOException {
    File file = createTempFile(1024*1025);

    File newLocation = new File(myCacheDir, CONTEXT_PATH + file.getName());
    FileUtils.moveFile(file, newLocation);
    myCacheListener.onAfterAddOrUpdate(newLocation);
    assertEquals(1, mySeeder.getNumberOfSeededTorrents());
    final SharedTorrent torrent = mySeeder.getSharedTorrents().iterator().next();
    assertEquals(newLocation.getAbsolutePath(), torrent.getParentFile().getAbsolutePath() + File.separatorChar + torrent.getFilenames().get(0));
    myCacheListener.onBeforeDelete(newLocation);
    assertEquals(0, mySeeder.getNumberOfSeededTorrents());
  }
}
