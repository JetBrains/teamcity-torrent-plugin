package jetbrains.buildServer.artifactsMirror.torrent;

import com.intellij.openapi.diagnostic.Logger;
import com.turn.ttorrent.client.Client;
import com.turn.ttorrent.client.SharedTorrent;
import com.turn.ttorrent.common.Torrent;
import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.SBuildServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class TorrentSeeder {
  private final static Logger LOG = Logger.getInstance(TorrentSeeder.class.getName());

  private final static int MAX_CLIENTS = 10;

  private final SBuildServer myServer;
  private volatile boolean myStopped = false;
  private final List<Client> myClients = new ArrayList<Client>();
  private final LinkedBlockingQueue<SharedTorrent> myTorrentsQueue;
  private final Thread myThread;

  public TorrentSeeder(@NotNull SBuildServer server) {
    myServer = server;
    myTorrentsQueue = new LinkedBlockingQueue<SharedTorrent>();
    myThread = new Thread(createRunnable(), "Torrent seeders processing thread");
    myThread.setDaemon(true);
    myServer.addListener(new BuildServerAdapter() {
      @Override
      public void serverShutdown() {
        super.serverShutdown();
        stop();
      }
    });
  }

  public void start() {
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

  public boolean seedTorrent(@NotNull Torrent torrent, @NotNull File srcFile) {
    if (myStopped) return false;

    try {
      SharedTorrent sharedTorrent = new SharedTorrent(torrent, srcFile, true);
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
  public Runnable createRunnable() {
    return new Runnable() {
      public void run() {
        while (!myStopped) {
          Iterator<Client> clIt = myClients.iterator();
          while (clIt.hasNext()) {
            Client c = clIt.next();
            if (c.getState() == Client.ClientState.DONE) {
              c.stop();
              clIt.remove();
            }
          }

          SharedTorrent st = myTorrentsQueue.poll();
          if (st != null) {

            boolean alreadySeeded = false;
            for (Client client: myClients) {
              if (st.getHexInfoHash().equals(client.getTorrent().getHexInfoHash())) {
                // torrent is already seeded
                alreadySeeded = true;
                break;
              }
            }

            if (!alreadySeeded) {
              String clientHost = getClientHost();
              Client client = null;
              if (clientHost != null && myClients.size() < MAX_CLIENTS) {
                try {
                  client = new Client(InetAddress.getByName(clientHost), st);
                } catch (IOException e) {
                  LOG.warn("Failed to create client for torrent: " + e.toString());
                }
              }

              if (client == null) {
                myTorrentsQueue.offer(st); // failed to create client, return torrent in the queue
              } else {
                myClients.add(client);
                client.share(0);
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

        for (Client c: myClients) {
          c.stop(true);
        }
      }
    };
  }

  @Nullable
  private String getClientHost() {
    try {
      return new URI(myServer.getRootUrl()).getHost();
    } catch (URISyntaxException e) {
      return null;
    }
  }

}
