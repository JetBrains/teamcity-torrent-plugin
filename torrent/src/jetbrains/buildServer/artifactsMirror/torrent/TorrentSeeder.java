package jetbrains.buildServer.artifactsMirror.torrent;

import com.intellij.openapi.diagnostic.Logger;
import com.turn.ttorrent.client.Client;
import com.turn.ttorrent.client.SharedTorrent;
import com.turn.ttorrent.client.peer.SharingPeer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;

public class TorrentSeeder {
  private final static Logger LOG = Logger.getInstance(TorrentSeeder.class.getName());

  private Client myClient;

  public TorrentSeeder() {
  }

  public void start(@NotNull String rootUrl) {
    try {
      myClient = new Client(InetAddress.getByName(getClientHost(rootUrl)));
      myClient.share();
    } catch (IOException e) {
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

  public int getDownloadingClientsNum() {
    int num = 0;
    for (SharingPeer peer: myClient.getPeers()) {
      if (peer.isDownloading()) num++;
    }

    return num;
  }

  public boolean seedTorrent(@NotNull File torrentFile, @NotNull File srcFile) {
    try {
      myClient.addTorrent(SharedTorrent.fromFile(torrentFile, srcFile.getParentFile(), true));
    } catch (IOException e) {
      return false;
    } catch (NoSuchAlgorithmException e) {
      return false;
    } catch (InterruptedException e) {
      return false;
    }

    return true;
  }
}
