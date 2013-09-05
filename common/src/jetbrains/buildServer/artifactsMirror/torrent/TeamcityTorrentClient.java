package jetbrains.buildServer.artifactsMirror.torrent;

import com.intellij.openapi.diagnostic.Logger;
import com.turn.ttorrent.client.Client;
import com.turn.ttorrent.client.SharedTorrent;
import com.turn.ttorrent.common.Peer;
import com.turn.ttorrent.common.Torrent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.security.NoSuchAlgorithmException;

public class TeamcityTorrentClient {
  private final static Logger LOG = Logger.getInstance(TeamcityTorrentClient.class.getName());

  private int myMaxTorrentsToSeed = -1;
  private Client myClient;

  public TeamcityTorrentClient() {
  }

  public void start(@NotNull InetAddress inetAddress, @Nullable final URI defaultTrackerURI) {
    try {
      myClient = new Client(inetAddress, defaultTrackerURI);
      myClient.share();
    } catch (IOException e) {
      LOG.warn("Failed to start torrent client: " + e.toString());
    }
  }

  public void stop() {
    myClient.stop(true);
  }

  public boolean seedTorrent(@NotNull File torrentFile, @NotNull File srcFile) throws IOException, NoSuchAlgorithmException {
    return seedTorrent(loadTorrent(torrentFile), srcFile);
  }
  public boolean seedTorrent(@NotNull Torrent torrent, @NotNull File srcFile) {
    if (myClient == null) return false;
    if (myMaxTorrentsToSeed != -1 && myClient.getTorrents().size() >= myMaxTorrentsToSeed){
      LOG.warn("Reached max number of seeded torrents. Torrent "+torrent.getName()+" will not be seeded");
      return false;
    }
    try {
      final SharedTorrent st = new SharedTorrent(torrent, srcFile.getParentFile(), false, true);
      myClient.addTorrent(st);
      return true;
    } catch (Exception e) {
      LOG.warn("Failed to seed file: " + srcFile.getName(), e);
      return false;
    }
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

  public void stopSeedingByPath(File file){
    final SharedTorrent torrentByName = myClient.getTorrentByFilePath(file);
    if (torrentByName != null) {
      LOG.info("Stopped seeding torrent by file: " + file.getAbsolutePath());
      myClient.removeTorrent(torrentByName);
    }
 }

  private Torrent loadTorrent(File torrentFile) throws IOException, NoSuchAlgorithmException {
    return Torrent.load(torrentFile);
  }

  public boolean isSeeding(@NotNull File torrentFile) throws IOException, NoSuchAlgorithmException {
    Torrent t = loadTorrent(torrentFile);
    for (SharedTorrent st: myClient.getTorrents()) {
      if (st.getHexInfoHash().equals(t.getHexInfoHash())) return true;
    }
    return false;
  }

  public int getNumberOfSeededTorrents() {
    return myClient.getTorrents().size();
  }

  public Peer getSelfPeer(){
    return myClient.getPeerSpec();
  }

  public void downloadAndShareOrFail(Torrent torrent, File destFile, File destDir, long downloadTimeoutSec) throws IOException, NoSuchAlgorithmException, InterruptedException {
    boolean torrentContainsFile = false;
    for (String filePath : torrent.getFilenames()) {
      if (destFile.getAbsolutePath().replaceAll("/", "\\").endsWith(filePath.replaceAll("/", "\\"))){
        torrentContainsFile = true;
        break;
      }
    }
    if (!torrentContainsFile){
      throw new IOException("File not found in torrent");
    }
    SharedTorrent downTorrent = new SharedTorrent(torrent, destDir, false);
    if (myClient.getTorrentsMap().containsKey(torrent.getHexInfoHash())){
      LOG.info("Already seeding torrent with hash " + torrent.getHexInfoHash() + ". Will stop seeding it");
      myClient.removeTorrent(torrent);
    }
    myClient.downloadUninterruptibly(downTorrent, downloadTimeoutSec);
  }

  public void setMaxTorrentsToSeed(int maxTorrentsToSeed) {
    myMaxTorrentsToSeed = maxTorrentsToSeed;
  }
}
