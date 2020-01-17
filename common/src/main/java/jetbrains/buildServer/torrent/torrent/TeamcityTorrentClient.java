/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.turn.ttorrent.client.*;
import com.turn.ttorrent.client.announce.TrackerClientFactory;
import com.turn.ttorrent.client.peer.SharingPeer;
import com.turn.ttorrent.client.storage.EmptyPieceStorageFactory;
import com.turn.ttorrent.client.storage.FileCollectionStorage;
import com.turn.ttorrent.client.storage.FullyPieceStorageFactory;
import com.turn.ttorrent.client.storage.PieceStorage;
import com.turn.ttorrent.common.TorrentHash;
import com.turn.ttorrent.common.TorrentMetadata;
import com.turn.ttorrent.common.TorrentParser;
import com.turn.ttorrent.network.SelectorFactory;
import jetbrains.buildServer.artifacts.FileProgress;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static jetbrains.buildServer.torrent.torrent.TorrentUtil.isConnectionManagerInitialized;

public class TeamcityTorrentClient {
  private final static Logger LOG = Logger.getInstance(TeamcityTorrentClient.class.getName());

  @NotNull
  private final CommunicationManager myCommunicationManager;

  public TeamcityTorrentClient(ExecutorService es, ExecutorService validatorES, TrackerClientFactory trackerClientFactory) {
    myCommunicationManager = new CommunicationManager(es, validatorES, trackerClientFactory);
  }

  public void start(@NotNull InetAddress[] inetAddresses,
                    @Nullable final URI defaultTrackerURI,
                    final int announceInterval,
                    @NotNull final SelectorFactory selectorFactory) throws IOException {
    myCommunicationManager.start(inetAddresses, announceInterval, defaultTrackerURI, selectorFactory);
  }

  public void stop() {
    myCommunicationManager.stop();
  }

  public boolean seedTorrent(@NotNull File torrentFile, @NotNull File srcFile) throws IOException, NoSuchAlgorithmException {
    try {
      myCommunicationManager.addTorrent(torrentFile.getAbsolutePath(), srcFile.getParent(), FullyPieceStorageFactory.INSTANCE);
      return true;
    } catch (FileNotFoundException e) {
      LOG.debug("File " + srcFile.getName() + " is not found, ", e);
      return false;
    } catch (IllegalArgumentException e) {
      //valid case since the library throws this exception when file was removed with parent directory
      LOG.debug("File " + srcFile.getName() + " is not found, ", e);
      return false;
    } catch (Exception e) {
      LOG.warn("Failed to seed file: " + srcFile.getName(), e);
      return false;
    }
  }

  public Set<SharingPeer> getPeers() {
    return myCommunicationManager.getPeers();
  }

  public LoadedTorrent getAnnounceableFileTorrent(String hash) {
    return myCommunicationManager.getTorrentsStorage().getLoadedTorrent(hash);
  }

  public void setReceiveBufferSize(int size) {
    myCommunicationManager.setReceiveBufferSize(size);
  }

  public void setSendBufferSize(int size) {
    myCommunicationManager.setSendBufferSize(size);
  }

  public void stopSeeding(@NotNull File torrentFile) {
    TorrentMetadata t = null;
    try {
      t = loadTorrent(torrentFile);
    } catch (FileNotFoundException e) {
      //torrent file can be deleted, ignore this exception
    } catch(IOException e) {
      LOG.warn(e.toString());
    }
    if (t != null) {
      myCommunicationManager.removeTorrent(t.getHexInfoHash());
    }
  }
  public void stopSeeding(@NotNull TorrentHash torrentHash) {
    myCommunicationManager.removeTorrent(torrentHash.getHexInfoHash());
  }

  public List<LoadedTorrent> getLoadedTorrents() {
    return myCommunicationManager.getTorrentsStorage().getLoadedTorrents();
  }

  private TorrentMetadata loadTorrent(File torrentFile) throws IOException {
    return new TorrentParser().parseFromFile(torrentFile);
  }

  public boolean isSeeding(@NotNull File torrentFile) {
    try {
      return isSeeding(loadTorrent(torrentFile));
    } catch (IOException e) {
    }
    return false;
  }

  public boolean isSeeding(@NotNull TorrentHash torrent) {
    return myCommunicationManager.getTorrentsStorage().getLoadedTorrent(torrent.getHexInfoHash()) != null;
  }

  public void setAnnounceInterval(final int announceInterval){
    myCommunicationManager.setAnnounceInterval(announceInterval);
  }

  public void setSocketTimeout(final int socketTimeoutSec) {
    if (!isConnectionManagerInitialized(myCommunicationManager)) return;
    myCommunicationManager.setSocketConnectionTimeout(socketTimeoutSec, TimeUnit.SECONDS);
  }

  public void setCleanupTimeout(final int cleanupTimeoutSec) {
    if (!isConnectionManagerInitialized(myCommunicationManager)) return;
    myCommunicationManager.setCleanupTimeout(cleanupTimeoutSec, TimeUnit.SECONDS);
  }

  public void setMaxIncomingConnectionsCount(int maxIncomingConnectionsCount) {
    if (!isConnectionManagerInitialized(myCommunicationManager)) return;
    myCommunicationManager.setMaxInConnectionsCount(maxIncomingConnectionsCount);
  }

  public void setMaxOutgoingConnectionsCount(int maxOutgoingConnectionsCount) {
    if (!isConnectionManagerInitialized(myCommunicationManager)) return;
    myCommunicationManager.setMaxOutConnectionsCount(maxOutgoingConnectionsCount);
  }

  public int getNumberOfSeededTorrents() {
    return myCommunicationManager.getTorrentsStorage().announceableTorrents().size();
  }

  public Thread downloadAndShareOrFailAsync(@NotNull final File torrentFile,
                                            @NotNull final List<String> fileNames,
                                            @NotNull final String hexInfoHash,
                                            @NotNull final File destFile,
                                            @NotNull final File destDir,
                                            @NotNull final FileProgress fileDownloadProgress,
                                            final int downloadTimeoutMs,
                                            final int minSeedersCount,
                                            final int maxTimeoutForConnect,
                                            final AtomicReference<Exception> occuredException) {
    final Thread thread = new Thread(new Runnable() {
      public void run() {
        try {
          downloadAndShareOrFail(torrentFile,
                  fileNames,
                  hexInfoHash,
                  destFile,
                  destDir,
                  fileDownloadProgress,
                  downloadTimeoutMs,
                  minSeedersCount,
                  maxTimeoutForConnect);
        } catch (Exception e) {
          occuredException.set(e);
        }
      }
    });
    thread.start();
    return thread;
  }

  private void checkThatTorrentContainsFile(@NotNull final List<String> fileNames,
                                            @NotNull final File destFile) throws IOException {
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
  }

  public void downloadAndShareOrFail(@NotNull final File torrentFile,
                                     @NotNull final List<String> fileNames,
                                     @NotNull final String hexInfoHash,
                                     @NotNull final File destFile,
                                     @NotNull final File destDir,
                                     @NotNull final FileProgress fileDownloadProgress,
                                     final int downloadTimeoutMs,
                                     final int minSeedersCount,
                                     final int maxTimeoutForConnect) throws Exception {
    checkThatTorrentContainsFile(fileNames, destFile);

    destDir.mkdirs();
    if (myCommunicationManager.containsTorrentWithHash(hexInfoHash)){
      LOG.info("Already seeding torrent with hash " + hexInfoHash + ". Stop seeding and try download again");
      stopSeeding(torrentFile);
    }
    LOG.info(String.format("Will attempt to download uninterruptibly %s into %s. Timeout:%d",
            destFile.getAbsolutePath(), destDir.getAbsolutePath(), downloadTimeoutMs));

    TorrentMetadataProvider metadataProvider = new FileMetadataProvider(torrentFile.getAbsolutePath());
    TorrentMetadata metadata = metadataProvider.getTorrentMetadata();
    FileCollectionStorage fileCollectionStorage = FileCollectionStorage.create(metadata, destDir);
    PieceStorage pieceStorage = EmptyPieceStorageFactory.INSTANCE.createStorage(metadata, fileCollectionStorage);

    TorrentDownloader torrentDownloader = new TorrentDownloader(
            metadata,
            fileDownloadProgress,
            minSeedersCount,
            maxTimeoutForConnect,
            downloadTimeoutMs
    );

    myCommunicationManager.addTorrent(
            metadataProvider,
            pieceStorage,
            Collections.<TorrentListener>singletonList(torrentDownloader)
    );
    Exception exception = null;
    try {
      torrentDownloader.awaitDownload();
    } catch (Exception e) {
      exception = e;
    }
    try {
      myCommunicationManager.removeTorrent(metadata.getHexInfoHash());
      pieceStorage.close();
    } finally {
      boolean downloadFailed = exception != null;
      if (downloadFailed) {
        fileCollectionStorage.delete();
        throw exception;
      }
    }
  }

  public Collection<SharedTorrent> getSharedTorrents(){
    return myCommunicationManager.getTorrents();
  }
}
