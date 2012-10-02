package jetbrains.buildServer.artifactsMirror.torrent;

import com.intellij.openapi.diagnostic.Logger;
import com.turn.ttorrent.client.Client;
import com.turn.ttorrent.client.SharedTorrent;
import com.turn.ttorrent.client.peer.SharingPeer;
import com.turn.ttorrent.common.Torrent;
import jetbrains.buildServer.util.Dates;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class TorrentSeeder {
  private final static Logger LOG = Logger.getInstance(TorrentSeeder.class.getName());

  private final static int MAX_CLIENTS = 8;

  private volatile boolean myStopped = false;
  private final List<TorrentClient> myClients = new ArrayList<TorrentClient>();
  private final LinkedBlockingQueue<SharedTorrent> myTorrentsQueue;
  private final Thread myThread;
  private String myRootUrl;

  public TorrentSeeder() {
    myTorrentsQueue = new LinkedBlockingQueue<SharedTorrent>();
    myThread = new Thread(createRunnable(), "Torrent seeders processing thread");
    myThread.setDaemon(true);
  }

  public void start(@NotNull String rootUrl) {
    myRootUrl = rootUrl;
    myThread.start();
  }

  public void stop() {
    myStopped = true;
    try {
      myThread.interrupt();
      myThread.join();
    } catch (InterruptedException e) {
      //
    }
  }

  public int getDownloadingClientsNum() {
    int num = 0;
    for (TorrentClient cl: myClients) {
      for (SharingPeer peer: cl.myClient.getPeers()) {
        if (peer.isDownloading()) num++;
      }
    }

    return num;
  }

  @NotNull
  public List<TorrentClient> getTorrentClients() {
    return new ArrayList<TorrentClient>(myClients);
  }

  public boolean seedTorrent(@NotNull Torrent torrent, @NotNull File srcFile) {
    if (myStopped) return false;

    try {
      SharedTorrent sharedTorrent = new SharedTorrent(torrent, srcFile.getParentFile(), true);
      myTorrentsQueue.add(sharedTorrent);

      synchronized (myTorrentsQueue) {
        myTorrentsQueue.notifyAll();
      }
    } catch (IOException e) {
      return false;
    } catch (NoSuchAlgorithmException e) {
      return false;
    }

    return true;
  }

  @NotNull
  private Runnable createRunnable() {
    return new Runnable() {
      public void run() {
        while (!myStopped) {
          SharedTorrent st = myTorrentsQueue.poll();
          if (st != null) {
            if (!isSeedAlreadyExists(st)) {
              if (myClients.size() == MAX_CLIENTS) {
                stopExpiredClients();
              }

              TorrentClient client = null;
              if (myClients.size() < MAX_CLIENTS) {
                try {
                  client = new TorrentClient(st);
                } catch (IOException e) {
                  LOG.warn("Failed to create client for torrent: " + e.toString());
                }
              }

              if (client == null) {
                myTorrentsQueue.offer(st); // failed to create client, return torrent in the queue
              } else {
                myClients.add(client);
                client.startSharing();
              }
            }
          }

          try {
            synchronized (myTorrentsQueue) {
              myTorrentsQueue.wait(100);
            }
          } catch (InterruptedException e) {
            // ignore for now
          }
        }

        for (TorrentClient c: myClients) {
          c.stopIfIdle();
        }
      }

      private boolean isSeedAlreadyExists(SharedTorrent st) {
        boolean alreadySeeded = false;
        for (TorrentClient client: myClients) {
          if (st.getHexInfoHash().equals(client.getTorrent().getHexInfoHash())) {
            // torrent is already seeded
            alreadySeeded = true;
            break;
          }
        }
        return alreadySeeded;
      }

      private void stopExpiredClients() {
        Iterator<TorrentClient> clIt = myClients.iterator();
        while (clIt.hasNext()) {
          TorrentClient tc = clIt.next();
          if (tc.stopIfIdle()) {
            clIt.remove();
          }
        }
      }
    };
  }

  public class TorrentClient {
    private Client myClient;
    private SharedTorrent myTorrent;
    private final Date myCreationDate;
    private final static long TORRENT_CLIENT_EXPIRATION_TIME = 1000 * 60; // 1 minute

    private TorrentClient(@NotNull SharedTorrent torrent) throws IOException {
      myTorrent = torrent;

      String clientHost = getClientHost();
      myClient = new Client(InetAddress.getByName(clientHost), torrent);
      myCreationDate = Dates.now();
    }

    @NotNull
    public SharedTorrent getTorrent() {
      return myTorrent;
    }

    @NotNull
    public Client getClient() {
      return myClient;
    }

    public void startSharing() {
      myClient.share();
    }

    public boolean stopIfIdle() {
      if (canBeStopped()) {
        myClient.stop(true);
        return true;
      }
      return false;
    }

    private boolean canBeStopped() {
      Client.ClientState state = myClient.getState();
      if (state == Client.ClientState.DONE) return true;
      for (SharingPeer p: myClient.getPeers()) {
        if (p.isDownloading()) return false;
      }
      return state != Client.ClientState.WAITING || Dates.now().getTime() - myCreationDate.getTime() > TORRENT_CLIENT_EXPIRATION_TIME;
    }

    @Nullable
    private String getClientHost() {
      try {
        return new URI(myRootUrl).getHost();
      } catch (URISyntaxException e) {
        return null;
      }
    }
  }

}
