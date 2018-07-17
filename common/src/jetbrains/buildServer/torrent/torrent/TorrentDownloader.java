/*
 * Copyright 2000-2018 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.torrent.torrent;

import com.turn.ttorrent.client.PeerInformation;
import com.turn.ttorrent.client.PieceInformation;
import com.turn.ttorrent.client.TorrentListenerWrapper;
import com.turn.ttorrent.client.TorrentManager;
import jetbrains.buildServer.artifacts.FileProgress;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TorrentDownloader {

  /**
   * {@link TorrentManager} instance related with torrent, see {@link com.turn.ttorrent.client.CommunicationManager#addTorrent}
   */
  @NotNull
  private final TorrentManager myTorrentManager;
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

  public TorrentDownloader(@NotNull final TorrentManager torrentManager,
                           @NotNull final FileProgress fileDownloadProgress,
                           int minPeersCount,
                           int timeoutForFindingPeers,
                           int idleTimeout) {
    myTorrentManager = torrentManager;
    myFileDownloadProgress = fileDownloadProgress;
    myMinPeersCount = minPeersCount;
    myTimeoutForFindingPeers = timeoutForFindingPeers;
    myIdleTimeout = idleTimeout;
  }

  public void awaitDownload() throws InterruptedException, DownloadException {
    final Semaphore semaphore = new Semaphore(0);
    final AtomicInteger connectedPeersCount = new AtomicInteger();
    final AtomicInteger downloadedPiecesCount = new AtomicInteger();
    TorrentListenerWrapper listener = new TorrentListenerWrapper() {
      @Override
      public void peerConnected(PeerInformation peerInformation) {
        connectedPeersCount.incrementAndGet();
      }

      @Override
      public void peerDisconnected(PeerInformation peerInformation) {
        connectedPeersCount.decrementAndGet();
      }

      @Override
      public void pieceDownloaded(PieceInformation pieceInformation, PeerInformation peerInformation) {
        downloadedPiecesCount.incrementAndGet();
        myFileDownloadProgress.transferred(pieceInformation.getSize());
      }

      @Override
      public void downloadComplete() {
        semaphore.release();
      }
    };
    myTorrentManager.addListener(listener);

    try {

      //wait setup connection with peers
      if (semaphore.tryAcquire(myTimeoutForFindingPeers, TimeUnit.MILLISECONDS)) {
        //download was finished in this timeout
        return;
      }

      while (true) {
        int connectedPeers = connectedPeersCount.get();
        if (connectedPeers < myMinPeersCount) {
          throw new DownloadException("Need " + myMinPeersCount +
                  " peers but right now only " + connectedPeers + " are connected");
        }
        int downloadedPieces = downloadedPiecesCount.get();
        if (semaphore.tryAcquire(myIdleTimeout, TimeUnit.MILLISECONDS)) {
          return;
        }
        int newDownloadedPieces = downloadedPiecesCount.get();
        if (newDownloadedPieces <= downloadedPieces) {
          //no pieces were downloaded
          throw new DownloadException("No pieces were downloaded in " + myIdleTimeout + "ms");
        }
        //continue waiting
      }
    } finally {
      myTorrentManager.removeListener(listener);
    }
  }
}
