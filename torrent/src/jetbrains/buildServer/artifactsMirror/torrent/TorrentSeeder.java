package jetbrains.buildServer.artifactsMirror.torrent;

import com.intellij.openapi.diagnostic.Logger;
import com.turn.ttorrent.client.Client;
import com.turn.ttorrent.client.SharedTorrent;
import com.turn.ttorrent.client.peer.SharingPeer;
import com.turn.ttorrent.common.Torrent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;

public class TorrentSeeder {
  private final static Logger LOG = Logger.getInstance(TorrentSeeder.class.getName());

  private Client myClient;

  public TorrentSeeder() {
  }

  public void start(@NotNull InetAddress inetAddress) {
    try {
      myClient = new Client(inetAddress);
      myClient.share();
    } catch (IOException e) {
      LOG.warn("Failed to start torrent client: " + e.toString());
    }
  }

  public void start(@NotNull String rootUrl) {
    try {
      start(InetAddress.getByName(getClientHost(rootUrl)));
    } catch (UnknownHostException e) {
      LOG.warn("Failed to start torrent client: " + e.toString());
    }
  }

  @Nullable
  private String getClientHost(@NotNull String rootUrl) {
    try {
      return new URI(rootUrl).getHost();
    } catch (URISyntaxException e) {
      return null;
    }
  }

  public void stop() {
    myClient.stop(true);
  }

  public int getConnectedClientsNum() {
    int num = 0;
    for (SharingPeer peer: myClient.getPeers()) {
      if (peer.isDownloading() || peer.isConnected()) num++;
    }

    return num;
  }

  public boolean seedTorrent(@NotNull File torrentFile, @NotNull File srcFile) {
    if (myClient == null) return false;
    try {
      Torrent t = loadTorrent(torrentFile);
      myClient.addTorrent(new SharedTorrent(t, srcFile.getParentFile(), false, true));
    } catch (IOException e) {
      return false;
    } catch (NoSuchAlgorithmException e) {
      return false;
    } catch (InterruptedException e) {
      return false;
    }

    return true;
  }

  public void stopSeeding(@NotNull File torrentFile) {
    if (myClient == null) return;
    try {
      Torrent t = loadTorrent(torrentFile);
      myClient.removeTorrent(t);
    } catch (IOException e) {
      LOG.warn(e.toString());
    } catch (NoSuchAlgorithmException e) {
      LOG.warn(e.toString());
    }
  }

  private Torrent loadTorrent(File torrentFile) throws IOException, NoSuchAlgorithmException {
    return Torrent.load(torrentFile, null);
  }

  public boolean isSeeding(@NotNull File torrentFile) throws IOException, NoSuchAlgorithmException {
    Torrent t = loadTorrent(torrentFile);
    for (SharedTorrent st: myClient.getTorrents()) {
      if (st.getHexInfoHash().equals(t.getHexInfoHash())) return true;
    }
    return false;
  }
}
