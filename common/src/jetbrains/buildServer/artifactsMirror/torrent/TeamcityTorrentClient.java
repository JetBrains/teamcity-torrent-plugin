package jetbrains.buildServer.artifactsMirror.torrent;

import com.intellij.openapi.diagnostic.Logger;
import com.turn.ttorrent.client.Client;
import com.turn.ttorrent.client.SharedTorrent;
import com.turn.ttorrent.common.Peer;
import com.turn.ttorrent.common.Torrent;
import com.turn.ttorrent.tracker.TrackerHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;

public class TeamcityTorrentClient {
  private final static Logger LOG = Logger.getInstance(TeamcityTorrentClient.class.getName());

  private Client myClient;

  public TeamcityTorrentClient() {
    myClient = new Client();
  }

  public void start(@NotNull InetAddress[] inetAddresses, @Nullable final URI defaultTrackerURI, final int announceInterval) throws IOException {
    myClient.start(inetAddresses, announceInterval, defaultTrackerURI);
  }

  public void stop() {
    myClient.stop(true);
  }

  public boolean seedTorrent(@NotNull File torrentFile, @NotNull File srcFile) throws IOException, NoSuchAlgorithmException {
    Torrent torrent = loadTorrent(torrentFile);
    if (!TrackerHelper.tryTracker(torrent)){
      torrent = torrent.createWithNewTracker(myClient.getDefaultTrackerURI());
      torrent.save(torrentFile);
    }
    return seedTorrent(torrent, srcFile);
  }

  public boolean seedTorrent(@NotNull Torrent torrent, @NotNull File srcFile) {
    if (myClient == null) return false;
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

  public void setAnnounceInterval(final int announceInterval){
    myClient.setAnnounceInterval(announceInterval);
  }

  public int getNumberOfSeededTorrents() {
    return myClient.getTorrents().size();
  }

  public void downloadAndShareOrFail(@NotNull final Torrent torrent,
                                     @NotNull final File destFile,
                                     @NotNull final File destDir,
                                     final long downloadTimeoutSec,
                                     final int minSeedersCount) throws IOException, NoSuchAlgorithmException, InterruptedException {
    boolean torrentContainsFile = false;
    for (String filePath : torrent.getFilenames()) {
      final String destFileAbsolutePath = destFile.getAbsolutePath();
      final String destFileCleaned = destFileAbsolutePath.replaceAll("\\\\", "/");
      final String filePathCleaned = filePath.replaceAll("\\\\", "/");
      if (destFileCleaned.endsWith(filePathCleaned)){
        torrentContainsFile = true;
        break;
      }
    }
    if (!torrentContainsFile){
      throw new IOException("File not found in torrent");
    }

    destDir.mkdirs();
    SharedTorrent downTorrent = new SharedTorrent(torrent, destDir, false);
    if (myClient.getTorrentsMap().containsKey(torrent.getHexInfoHash())){
      LOG.info("Already seeding torrent with hash " + torrent.getHexInfoHash() + ". Will stop seeding it");
      myClient.removeTorrent(torrent);
    }
    LOG.info(String.format("Will attempt to download uninterruptibly %s into %s. Timeout:%d",
            destFile.getAbsolutePath(), destDir.getAbsolutePath(), downloadTimeoutSec));
    myClient.downloadUninterruptibly(downTorrent, downloadTimeoutSec, minSeedersCount);
  }

  public Collection<SharedTorrent> getSharedTorrents(){
    return myClient.getTorrents();
  }
}
