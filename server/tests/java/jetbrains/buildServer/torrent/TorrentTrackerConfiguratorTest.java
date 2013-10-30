package jetbrains.buildServer.torrent;

import com.turn.ttorrent.common.protocol.TrackerMessage;
import com.turn.ttorrent.common.protocol.http.HTTPAnnounceResponseMessage;
import com.turn.ttorrent.common.protocol.http.HTTPTrackerMessage;
import com.turn.ttorrent.tracker.TrackedTorrent;
import com.turn.ttorrent.tracker.TrackerRequestProcessor;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.XmlRpcHandlerManager;
import jetbrains.buildServer.torrent.torrent.TorrentUtil;
import jetbrains.buildServer.serverSide.BuildServerListener;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.serverSide.impl.ServerSettings;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.WaitFor;
import org.jetbrains.annotations.NotNull;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Sergey.Pak
 *         Date: 10/16/13
 *         Time: 12:43 PM
 */
@Test
public class TorrentTrackerConfiguratorTest extends BaseTestCase {

  private TorrentConfigurator myConfigurator;
  private EventDispatcher<BuildServerListener> myDispatcher;
  private ServerTorrentsDirectorySeeder myDirectorySeeder;
  private TorrentTrackerManager myTrackerManager;

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final Mockery m = new Mockery();

    final ServerPaths serverPaths = new ServerPaths(createTempDir().getAbsolutePath());
    final ServerSettings settings = m.mock(ServerSettings.class);
    m.checking(new Expectations(){{
      allowing(settings).getRootUrl(); will(returnValue("http://localhost:8111"));
    }});

    myConfigurator = new TorrentConfigurator(serverPaths, settings, new XmlRpcHandlerManager() {
      public void addHandler(String handlerName, Object handler) {}
      public void addSessionHandler(String handlerName, Object handler) {}
    });
    myDispatcher = EventDispatcher.create(BuildServerListener.class);

    ExecutorServices services = new ExecutorServices() {
      @NotNull
      public ScheduledExecutorService getNormalExecutorService() {
        return null;
      }

      @NotNull
      public ExecutorService getLowPriorityExecutorService() {
        return Executors.newSingleThreadExecutor();
      }
    };

    myDirectorySeeder = new ServerTorrentsDirectorySeeder(serverPaths, myConfigurator, services, myDispatcher);
    myTrackerManager = new TorrentTrackerManager(myConfigurator, myDispatcher);

    myDispatcher.getMulticaster().serverStartup();

    new WaitFor(5 * 1000) {
      @Override
      protected boolean condition() {
        return !myDirectorySeeder.getTorrentsDirectorySeeder().isStopped();
      }
    };
    assertFalse(myDirectorySeeder.getTorrentsDirectorySeeder().isStopped());
  }

  public void test_enable_disable_tracker(){
    assertTrue(myTrackerManager.isTrackerRunning());
    System.setProperty(TorrentConfigurator.TRACKER_ENABLED, "false");
    myConfigurator.getConfigurationWatcher().checkForModifications();
    assertFalse(myTrackerManager.isTrackerRunning());

    System.setProperty(TorrentConfigurator.TRACKER_ENABLED, "true");
    myConfigurator.getConfigurationWatcher().checkForModifications();
    assertTrue(myTrackerManager.isTrackerRunning());

    assertEquals("http://localhost:8111/trackerAnnounce.html", myTrackerManager.getAnnounceUri().toString());
  }

  public void test_tracker_dedicated_port(){
    assertTrue(myTrackerManager.isTrackerRunning());
    System.setProperty(TorrentConfigurator.TRACKER_DEDICATED_PORT, "true");
    myConfigurator.getConfigurationWatcher().checkForModifications();
    assertTrue(myTrackerManager.isTrackerRunning());
    assertTrue(myTrackerManager.getAnnounceUri().toString().endsWith(":6969/announce"));

    System.setProperty(TorrentConfigurator.TRACKER_DEDICATED_PORT, "false");
    myConfigurator.getConfigurationWatcher().checkForModifications();
    assertTrue(myTrackerManager.isTrackerRunning());
    assertEquals("http://localhost:8111/trackerAnnounce.html", myTrackerManager.getAnnounceUri().toString());
  }

  public void test_enable_disable_seeder(){
    System.setProperty(TorrentConfigurator.SEEDER_ENABLED, "false");
    myConfigurator.getConfigurationWatcher().checkForModifications();
    assertTrue(myDirectorySeeder.getTorrentsDirectorySeeder().isStopped());

    System.setProperty(TorrentConfigurator.SEEDER_ENABLED, "true");
    myConfigurator.getConfigurationWatcher().checkForModifications();
    new WaitFor(5 * 1000) {
      @Override
      protected boolean condition() {
        return !myDirectorySeeder.getTorrentsDirectorySeeder().isStopped();
      }
    };
    assertFalse(myDirectorySeeder.getTorrentsDirectorySeeder().isStopped());
  }

  public void test_file_size_threshold(){
    System.setProperty(TorrentConfigurator.FILE_SIZE_THRESHOLD, "200");
    myConfigurator.getConfigurationWatcher().checkForModifications();
    assertTrue(TorrentUtil.shouldCreateTorrentFor(200 * 1024 * 1024, myConfigurator));
    assertFalse(TorrentUtil.shouldCreateTorrentFor(200 * 1024 * 1024 - 1, myConfigurator));
  }

  public void test_transport_enabled(){
    System.setProperty(TorrentConfigurator.TRANSPORT_ENABLED, "false");
    myConfigurator.getConfigurationWatcher().checkForModifications();
    assertFalse(myConfigurator.isTransportEnabled());

    System.setProperty(TorrentConfigurator.TRANSPORT_ENABLED, "true");
    myConfigurator.getConfigurationWatcher().checkForModifications();
    assertTrue(myConfigurator.isTransportEnabled());
  }

  public void test_announce_interval() throws IOException, TrackerMessage.MessageValidationException {
    System.setProperty(TorrentConfigurator.ANNOUNCE_INTERVAL, "10");
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
    System.setProperty(TorrentConfigurator.TRACKER_TORRENT_EXPIRE_TIMEOUT, "5");
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

  public void test_max_number_of_seeded_torrents(){
    System.setProperty(TorrentConfigurator.MAX_NUMBER_OF_SEEDED_TORRENTS, "3");
    myConfigurator.getConfigurationWatcher().checkForModifications();
    assertEquals(3, myConfigurator.getMaxNumberOfSeededTorrents());

    assertEquals(3, myDirectorySeeder.getTorrentsDirectorySeeder().getMaxTorrentsToSeed());
  }

  @AfterMethod
  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    System.getProperties().remove(TorrentConfigurator.TRACKER_ENABLED);
    System.getProperties().remove(TorrentConfigurator.TRACKER_DEDICATED_PORT);
    System.getProperties().remove(TorrentConfigurator.OWN_ADDRESS);
    System.getProperties().remove(TorrentConfigurator.SEEDER_ENABLED);
    System.getProperties().remove(TorrentConfigurator.FILE_SIZE_THRESHOLD);
    System.getProperties().remove(TorrentConfigurator.TRANSPORT_ENABLED);
    System.getProperties().remove(TorrentConfigurator.ANNOUNCE_INTERVAL);
    System.getProperties().remove(TorrentConfigurator.TRACKER_TORRENT_EXPIRE_TIMEOUT);
    System.getProperties().remove(TorrentConfigurator.MAX_NUMBER_OF_SEEDED_TORRENTS);
    myDispatcher.getMulticaster().serverShutdown();
  }
}
