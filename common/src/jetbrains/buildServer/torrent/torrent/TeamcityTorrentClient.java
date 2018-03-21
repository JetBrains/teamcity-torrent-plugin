package jetbrains.buildServer.torrent.torrent;

import com.intellij.openapi.diagnostic.Logger;
import com.turn.ttorrent.client.Client;
import com.turn.ttorrent.client.DownloadProgressListener;
import com.turn.ttorrent.client.SharedTorrent;
import com.turn.ttorrent.client.peer.SharingPeer;
import com.turn.ttorrent.common.AnnounceableFileTorrent;
import com.turn.ttorrent.common.AnnounceableTorrent;
import com.turn.ttorrent.common.Torrent;
import com.turn.ttorrent.common.TorrentHash;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static jetbrains.buildServer.torrent.torrent.TorrentUtil.isConnectionManagerInitialized;

public class TeamcityTorrentClient {
  private final static Logger LOG = Logger.getInstance(TeamcityTorrentClient.class.getName());

  @NotNull
  private final Client myClient;

  public TeamcityTorrentClient(ExecutorService es, ExecutorService validatorES) {
    myClient = new Client(es, validatorES);
  }

  public void start(@NotNull InetAddress[] inetAddresses, @Nullable final URI defaultTrackerURI, final int announceInterval) throws IOException {
    myClient.start(inetAddresses, announceInterval, defaultTrackerURI);
  }

  public void stop() {
    myClient.stop();
  }

  public boolean seedTorrent(@NotNull File torrentFile, @NotNull File srcFile) throws IOException, NoSuchAlgorithmException {
    try {
      myClient.addTorrent(torrentFile.getAbsolutePath(), srcFile.getParent(), true, false);
      return true;
    } catch (Exception e) {
      LOG.warn("Failed to seed file: " + srcFile.getName(), e);
      return false;
    }
  }

  public Set<SharingPeer> getPeers() {
    return myClient.getPeers();
  }

  public AnnounceableFileTorrent getAnnounceableFileTorrent(String hash) {
    return myClient.getTorrentsStorage().getAnnounceableTorrent(hash);
  }

  public void setReceiveBufferSize(int size) {
    myClient.setReceiveBufferSize(size);
  }

  public void setSendBufferSize(int size) {
    myClient.setSendBufferSize(size);
  }

  public void stopSeeding(@NotNull File torrentFile) {
    try {
      Torrent t = loadTorrent(torrentFile);
      myClient.removeTorrent(t);
    } catch (IOException e) {
      LOG.warn(e.toString());
    } catch (NoSuchAlgorithmException e) {
      LOG.warn(e.toString());
    }
  }
  public void stopSeeding(@NotNull TorrentHash torrentHash) {
    myClient.removeTorrent(torrentHash);
  }

  public List<AnnounceableTorrent> getAnnounceableTorrents() {
    return myClient.getTorrentsStorage().announceableTorrents();
  }

  public void stopSeedingByPath(File file){
    final SharedTorrent torrentByName = myClient.getTorrentByFilePath(file);
    if (torrentByName != null) {
      LOG.info("Stopped seeding torrent by file: " + file.getAbsolutePath());
      myClient.removeTorrent(torrentByName);
    }
 }

  public boolean isSeedingByPath(File file){
    final SharedTorrent torrentByName = myClient.getTorrentByFilePath(file);
    return torrentByName != null;
  }

  private Torrent loadTorrent(File torrentFile) throws IOException, NoSuchAlgorithmException {
    return Torrent.load(torrentFile);
  }

  public boolean isSeeding(@NotNull File torrentFile) {
    try {
      return isSeeding(loadTorrent(torrentFile));
    } catch (IOException e) {
    } catch (NoSuchAlgorithmException e) {
    }
    return false;
  }

  public boolean isSeeding(@NotNull TorrentHash torrent) {
    return myClient.getTorrentsStorage().getAnnounceableTorrent(torrent.getHexInfoHash()) != null;
  }

  public File findSeedingTorrentFolder(@NotNull TorrentHash torrent){
    for (SharedTorrent st : myClient.getTorrents()) {
      if (st.getHexInfoHash().equals(torrent.getHexInfoHash())){
        return st.getParentFile();
      }
    }
    return null;
  }

  public void setAnnounceInterval(final int announceInterval){
    myClient.setAnnounceInterval(announceInterval);
  }

  public void setSocketTimeout(final int socketTimeoutSec) {
    if (!isConnectionManagerInitialized(myClient)) return;
    myClient.setSocketConnectionTimeout(socketTimeoutSec, TimeUnit.SECONDS);
  }

  public void setCleanupTimeout(final int cleanupTimeoutSec) {
    if (!isConnectionManagerInitialized(myClient)) return;
    myClient.setCleanupTimeout(cleanupTimeoutSec, TimeUnit.SECONDS);
  }

  public void setMaxIncomingConnectionsCount(int maxIncomingConnectionsCount) {
    if (!isConnectionManagerInitialized(myClient)) return;
    myClient.setMaxInConnectionsCount(maxIncomingConnectionsCount);
  }

  public void setMaxOutgoingConnectionsCount(int maxOutgoingConnectionsCount) {
    if (!isConnectionManagerInitialized(myClient)) return;
    myClient.setMaxOutConnectionsCount(maxOutgoingConnectionsCount);
  }

  public int getNumberOfSeededTorrents() {
    return myClient.getTorrentsStorage().announceableTorrents().size();
  }

  public Thread downloadAndShareOrFailAsync(@NotNull final File torrentFile,
                                            @NotNull final List<String> fileNames,
                                            @NotNull final String hexInfoHash,
                                            @NotNull final File destFile,
                                            @NotNull final File destDir,
                                            final long downloadTimeoutSec,
                                            final int minSeedersCount,
                                            final AtomicBoolean isInterrupted,
                                            final long maxTimeoutForConnect,
                                            final AtomicReference<Exception> occuredException,
                                            final DownloadProgressListener listener) throws IOException, NoSuchAlgorithmException, InterruptedException {
    final Thread thread = new Thread(new Runnable() {
      public void run() {
        try {
          downloadAndShareOrFail(torrentFile, fileNames, hexInfoHash, destFile, destDir, downloadTimeoutSec, minSeedersCount, maxTimeoutForConnect, isInterrupted, listener);
        } catch (Exception e) {
          occuredException.set(e);
        }
      }
    });
    thread.start();
    return thread;
  }

  public void downloadAndShareOrFail(@NotNull final File torrentFile,
                                     @NotNull final List<String> fileNames,
                                     @NotNull final String hexInfoHash,
                                     @NotNull final File destFile,
                                     @NotNull final File destDir,
                                     final long downloadTimeoutSec,
                                     final int minSeedersCount,
                                     final long maxTimeoutForConnect,
                                     final AtomicBoolean isInterrupted,
                                     DownloadProgressListener listener) throws IOException, NoSuchAlgorithmException, InterruptedException {
    boolean torrentContainsFile = false;
    for (String filePath : fileNames) {
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
    if (myClient.containsTorrentWithHash(hexInfoHash)){
      LOG.info("Already seeding torrent with hash " + hexInfoHash + ". Stop seeding and try download again");
      stopSeeding(torrentFile);
    }
    LOG.info(String.format("Will attempt to download uninterruptibly %s into %s. Timeout:%d",
            destFile.getAbsolutePath(), destDir.getAbsolutePath(), downloadTimeoutSec));
    myClient.downloadUninterruptibly(torrentFile.getAbsolutePath(), destDir.getAbsolutePath(),
            downloadTimeoutSec, minSeedersCount, isInterrupted, maxTimeoutForConnect, listener);
  }

  public Collection<SharedTorrent> getSharedTorrents(){
    return myClient.getTorrents();
  }
}
