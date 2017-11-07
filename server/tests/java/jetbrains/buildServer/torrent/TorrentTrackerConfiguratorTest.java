package jetbrains.buildServer.torrent;

import com.turn.ttorrent.common.protocol.TrackerMessage;
import com.turn.ttorrent.common.protocol.http.HTTPAnnounceResponseMessage;
import com.turn.ttorrent.common.protocol.http.HTTPTrackerMessage;
import com.turn.ttorrent.tracker.TrackedTorrent;
import com.turn.ttorrent.tracker.TrackerRequestProcessor;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.torrent.torrent.TorrentUtil;
import jetbrains.buildServer.util.WaitFor;
import jetbrains.buildServer.util.executors.ExecutorsFactory;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Test
public class TorrentTrackerConfiguratorTest extends ServerTorrentsSeederTestCase {
  private TorrentTrackerManager myTrackerManager;

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myTrackerManager = new TorrentTrackerManager(myConfigurator, new ExecutorServices() {
      @NotNull
      public ScheduledExecutorService getNormalExecutorService() {
        return ExecutorsFactory.newFixedScheduledExecutor("aaa", 1);
      }

      @NotNull
      public ExecutorService getLowPriorityExecutorService() {
        return null;
      }
    }, myDispatcher);

    myDispatcher.getMulticaster().serverStartup();

    new WaitFor(5 * 1000) {
      @Override
      protected boolean condition() {
        return !myTorrentsSeeder.getTorrentsSeeder().isStopped();
      }
    };
    assertFalse(myTorrentsSeeder.getTorrentsSeeder().isStopped());
  }

  public void test_enable_disable_tracker(){
    assertTrue(myTrackerManager.isTrackerRunning());
    System.setProperty(TorrentConfiguration.TRACKER_ENABLED, "false");
    myConfigurator.getConfigurationWatcher().checkForModifications();
    assertFalse(myTrackerManager.isTrackerRunning());

    System.setProperty(TorrentConfiguration.TRACKER_ENABLED, "true");
    myConfigurator.getConfigurationWatcher().checkForModifications();
    assertTrue(myTrackerManager.isTrackerRunning());

    assertEquals("http://localhost:8111/trackerAnnounce.html", myTrackerManager.getAnnounceUri().toString());
  }

  public void test_tracker_dedicated_port(){
    assertTrue(myTrackerManager.isTrackerRunning());
    System.setProperty(TorrentConfiguration.TRACKER_DEDICATED_PORT, "true");
    myConfigurator.getConfigurationWatcher().checkForModifications();
    assertTrue(myTrackerManager.isTrackerRunning());
    assertTrue(myTrackerManager.getAnnounceUri().toString().endsWith(":6969/announce"));

    System.setProperty(TorrentConfiguration.TRACKER_DEDICATED_PORT, "false");
    myConfigurator.getConfigurationWatcher().checkForModifications();
    assertTrue(myTrackerManager.isTrackerRunning());
    assertEquals("http://localhost:8111/trackerAnnounce.html", myTrackerManager.getAnnounceUri().toString());
  }

  public void test_enable_disable_seeder(){
    myConfigurator.setDownloadEnabled(false);
    myConfigurator.setTransportEnabled(false);
    assertTrue(myTorrentsSeeder.getTorrentsSeeder().isStopped());

    myConfigurator.setDownloadEnabled(true);
    assertFalse(myTorrentsSeeder.getTorrentsSeeder().isStopped());
  }

  public void test_file_size_threshold(){
    System.setProperty(TorrentConfiguration.FILE_SIZE_THRESHOLD, "200");
    myConfigurator.getConfigurationWatcher().checkForModifications();
    assertTrue(TorrentUtil.shouldCreateTorrentFor(200 * 1024 * 1024, myConfigurator));
    assertFalse(TorrentUtil.shouldCreateTorrentFor(200 * 1024 * 1024 - 1, myConfigurator));
  }

  public void testMinSeedersCount() {
    assertEquals(TorrentConfiguration.DEFAULT_MIN_SEEDERS_FOR_DOWNLOAD, myConfigurator.getMinSeedersForDownload());
    System.setProperty(TorrentConfiguration.MIN_SEEDERS_FOR_DOWNLOAD, "5");
    assertEquals(5, myConfigurator.getMinSeedersForDownload());
  }

  public void test_announce_interval() throws IOException, TrackerMessage.MessageValidationException {
    System.setProperty(TorrentConfiguration.ANNOUNCE_INTERVAL, "10");
    myConfigurator.getConfigurationWatcher().checkForModifications();
    assertEquals(10, myConfigurator.getAnnounceIntervalSec());
    assertEquals(10, myTrackerManager.getTrackerService().getAnnounceInterval());
    final String uri = "http://localhost:8111/trackerAnnounce.html" +
            "?info_hash=12345678901234567890" +
            "&peer_id=ABCDEFGHIJKLMNOPQRST" +
            "&ip=127.0.0.0" +
            "&port=6881" +
            "&downloaded=1234" +
            "&left=98765" +
            "&event=stopped";
    final AtomicReference<String> response = new AtomicReference<String>();
    myTrackerManager.getTrackerService().process(uri, "http://localhost:8111/", new TrackerRequestProcessor.RequestHandler() {
      public void serveResponse(int code, String description, ByteBuffer responseData) {
        response.set(new String(responseData.array()));
      }

      public ConcurrentMap<String, TrackedTorrent> getTorrentsMap() {
        return new ConcurrentHashMap<String, TrackedTorrent>();
      }
    });
    final HTTPAnnounceResponseMessage parse = (HTTPAnnounceResponseMessage) HTTPTrackerMessage.parse(ByteBuffer.wrap(response.get().getBytes()));
    assertEquals(10, parse.getInterval());
  }

  public void test_torrent_expire_timeout() throws IOException, TrackerMessage.MessageValidationException, InterruptedException {
    System.setProperty(TorrentConfiguration.TRACKER_TORRENT_EXPIRE_TIMEOUT, "5");
    myConfigurator.getConfigurationWatcher().checkForModifications();
    assertEquals(5, myConfigurator.getTrackerTorrentExpireTimeoutSec());
    final String uriCompleted = "http://localhost:8111/trackerAnnounce.html" +
            "?info_hash=12345678901234567890" +
            "&peer_id=ABCDEFGHIJKLMNOPQRST" +
            "&ip=172.20.240.249" +
            "&port=6884" +
            "&downloaded=1234" +
            "&left=0" +
            "&event=" + "completed";
    final String uriCompleted2 = "http://localhost:8111/trackerAnnounce.html" +
            "?info_hash=12345678901234567890" +
            "&peer_id=BBCDEFGHIJKLMNOPQRST" +
            "&ip=172.20.240.249" +
            "&port=6881" +
            "&downloaded=1234" +
            "&left=0" +
            "&event=" + "completed";
//    assertEquals(uriCompleted, uriCompleted2);
    final AtomicReference<byte[]> response = new AtomicReference<byte[]>();
    final TrackerRequestProcessor.RequestHandler requestHandler = new TrackerRequestProcessor.RequestHandler() {
      public void serveResponse(int code, String description, ByteBuffer responseData) {
        response.set(responseData.array());
      }

      public ConcurrentMap<String, TrackedTorrent> getTorrentsMap() {
        return myTrackerManager.getTorrents();
      }
    };

    myTrackerManager.getTrackerService().process(uriCompleted, "http://localhost:8111/", requestHandler);
    assertEquals(1, myTrackerManager.getTorrents().size());

    final AtomicInteger complete = new AtomicInteger(100);
    final AtomicInteger peersSize = new AtomicInteger(100);
    final String torrentHash = "3132333435363738393031323334353637383930";
    final ConcurrentHashMap<String, TrackedTorrent> torrents = (ConcurrentHashMap<String, TrackedTorrent>) myTrackerManager.getTorrents();
    new WaitFor(15*1000){
      @Override
      protected boolean condition() {
        try {
          myTrackerManager.getTrackerService().process(uriCompleted2, "http://localhost:8111/", requestHandler);
          final HTTPAnnounceResponseMessage parse = (HTTPAnnounceResponseMessage) HTTPTrackerMessage.parse(ByteBuffer.wrap(response.get()));
          complete.set(parse.getComplete());
          peersSize.set(parse.getPeers().size());

          final TrackedTorrent trackedTorrent = torrents.get(torrentHash);
          return parse.getComplete() == 1 && trackedTorrent.getPeers().size()==1;
        } catch (IOException e) {
          e.printStackTrace();
        } catch (TrackerMessage.MessageValidationException e) {
          e.printStackTrace();
        }
        return false;
      }
    };
    final TrackedTorrent trackedTorrent = torrents.get(torrentHash);
    assertNotContains(trackedTorrent.getPeers().keySet(), "4142434445464748494A4B4C4D4E4F5051525354");
    new WaitFor(15*1000){
      @Override
      protected boolean condition() {
        return !torrents.containsKey(torrentHash);
      }
    };
    assertNotContains(torrents.keySet(), torrentHash);
  }

  public void test_max_number_of_seeded_torrents() {
    String oldProperty = System.getProperty(TorrentConfiguration.MAX_NUMBER_OF_SEEDED_TORRENTS);
    try {
      System.setProperty(TorrentConfiguration.MAX_NUMBER_OF_SEEDED_TORRENTS, "3");
      myConfigurator.getConfigurationWatcher().checkForModifications();
      assertEquals(3, myConfigurator.getMaxNumberOfSeededTorrents());
      assertEquals(3, myTorrentsSeeder.getTorrentsSeeder().getMaxTorrentsToSeed());
    } finally {
      if (Objects.isNull(oldProperty)) {
        System.clearProperty(TorrentConfiguration.MAX_NUMBER_OF_SEEDED_TORRENTS);
      } else {
        System.setProperty(TorrentConfiguration.MAX_NUMBER_OF_SEEDED_TORRENTS, oldProperty);
      }
    }
  }
}
