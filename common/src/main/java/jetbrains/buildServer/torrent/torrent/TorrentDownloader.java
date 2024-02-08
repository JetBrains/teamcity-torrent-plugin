

package jetbrains.buildServer.torrent.torrent;

import com.intellij.openapi.diagnostic.Logger;
import com.turn.ttorrent.client.PeerInformation;
import com.turn.ttorrent.client.PieceInformation;
import com.turn.ttorrent.client.TorrentListenerWrapper;
import com.turn.ttorrent.common.TorrentMetadata;
import jetbrains.buildServer.artifacts.FileProgress;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class TorrentDownloader extends TorrentListenerWrapper {

  private final static Logger LOG = Logger.getInstance(TorrentDownloader.class.getName());

  /**
   * {@link TorrentMetadata} instance related with torrent
   */
  @NotNull
  private final TorrentMetadata myTorrentMetadata;

  /**
   * minimum count of peers. If this peers were not found in specified timeout download will be failed
   */
  private final int myMinPeersCount;

  /**
   * Timeout in millis for finding peers, see {@link TorrentDownloader#myMinPeersCount}
   */
  private final int myTimeoutForFindingPeers;

  /**
   * Timeout in millis for downloading one valid piece (idle timeout). If no pieces were downloaded
   * in specified timeout download will be failed
   */
  private final int myIdleTimeout;

  /**
   * {@link FileProgress} instance which will be invoked when each piece will be downloaded
   */
  @NotNull
  private final FileProgress myFileDownloadProgress;

  @NotNull
  private final AtomicInteger myDownloadedPiecesCount;

  @NotNull
  private final AtomicInteger myReceivedPiecesCount;

  @NotNull
  private final AtomicInteger myConnectedPeersCount;

  @NotNull
  private final Semaphore mySemaphore;

  @NotNull
  private final AtomicReference<Throwable> myFailedExceptionHolder;

  public TorrentDownloader(@NotNull final TorrentMetadata metadata,
                           @NotNull final FileProgress fileDownloadProgress,
                           int minPeersCount,
                           int timeoutForFindingPeers,
                           int idleTimeout) {
    myTorrentMetadata = metadata;
    myFileDownloadProgress = fileDownloadProgress;
    myMinPeersCount = minPeersCount;
    myTimeoutForFindingPeers = timeoutForFindingPeers;
    myIdleTimeout = idleTimeout;
    myDownloadedPiecesCount = new AtomicInteger();
    myConnectedPeersCount = new AtomicInteger();
    mySemaphore = new Semaphore(0);
    myFailedExceptionHolder = new AtomicReference<Throwable>();
    myReceivedPiecesCount = new AtomicInteger();
  }

  public void awaitDownload() throws InterruptedException, DownloadException {

    //wait setup connection with peers
    if (mySemaphore.tryAcquire(myTimeoutForFindingPeers, TimeUnit.MILLISECONDS)) {
      //download was finished in this timeout
      return;
    }

    int downloadedPieces = myDownloadedPiecesCount.get();
    while (true) {
      int connectedPeers = myConnectedPeersCount.get();
      boolean allPiecesReceived = myReceivedPiecesCount.get() == myTorrentMetadata.getPiecesCount();
      if (connectedPeers < myMinPeersCount && !allPiecesReceived) {
        throw new DownloadException("Need " + myMinPeersCount +
                " peers but right now only " + connectedPeers + " are connected");
      }
      if (mySemaphore.tryAcquire(myIdleTimeout, TimeUnit.MILLISECONDS)) {
        return;
      }
      int newDownloadedPieces = myDownloadedPiecesCount.get();
      if (newDownloadedPieces <= downloadedPieces) {
        //no pieces were downloaded
        throw new DownloadException(String.format(
                "No pieces were downloaded in %dms. Downloaded pieces %d/%d, connected peers %d",
                myIdleTimeout,
                downloadedPieces,
                myTorrentMetadata.getPiecesCount(),
                connectedPeers));
      }
      Throwable failedException = myFailedExceptionHolder.get();
      if (failedException != null) {
        throw new DownloadException("Downloading was failed, problem: " + failedException.getMessage(),
                failedException);
      }
      downloadedPieces = newDownloadedPieces;
      //continue waiting
    }
  }

  @Override
  public void pieceReceived(PieceInformation pieceInformation, PeerInformation peerInformation) {
    myReceivedPiecesCount.incrementAndGet();
  }

  @Override
  public void peerConnected(PeerInformation peerInformation) {
    myConnectedPeersCount.incrementAndGet();
    LOG.debug("Connected new peer " + peerInformation);
  }

  @Override
  public void peerDisconnected(PeerInformation peerInformation) {
    myConnectedPeersCount.decrementAndGet();
    LOG.debug("Peer " + peerInformation + " is disconnected");
  }

  @Override
  public void pieceDownloaded(PieceInformation pieceInformation, PeerInformation peerInformation) {
    myDownloadedPiecesCount.incrementAndGet();
    myFileDownloadProgress.transferred(pieceInformation.getSize());
  }

  @Override
  public void downloadComplete() {
    mySemaphore.release();
  }

  @Override
  public void downloadFailed(Throwable cause) {
    myFailedExceptionHolder.set(cause);
  }
}