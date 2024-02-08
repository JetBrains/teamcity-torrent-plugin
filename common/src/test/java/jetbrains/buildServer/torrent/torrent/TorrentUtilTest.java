

package jetbrains.buildServer.torrent.torrent;

import com.turn.ttorrent.client.CommunicationManager;
import org.testng.annotations.Test;

import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

@Test
public class TorrentUtilTest {

  public void isConnectionManagerInitializedTest() throws Exception {
    ExecutorService es = Executors.newFixedThreadPool(2);
    ExecutorService validatorES = Executors.newFixedThreadPool(2);
    CommunicationManager communicationManager = new CommunicationManager(es, validatorES);
    assertFalse(TorrentUtil.isConnectionManagerInitialized(communicationManager));
    communicationManager.start(InetAddress.getLocalHost());
    assertTrue(TorrentUtil.isConnectionManagerInitialized(communicationManager));
    communicationManager.stop();
    assertTrue(TorrentUtil.isConnectionManagerInitialized(communicationManager));
    es.shutdown();
    validatorES.shutdown();
  }

}