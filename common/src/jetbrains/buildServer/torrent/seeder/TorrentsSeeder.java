/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package jetbrains.buildServer.torrent.seeder;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.turn.ttorrent.client.SharedTorrent;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.torrent.TorrentConfiguration;
import jetbrains.buildServer.torrent.torrent.TeamcityTorrentClient;
import jetbrains.buildServer.util.NamedThreadFactory;
import jetbrains.buildServer.util.ThreadUtil;
import jetbrains.buildServer.util.executors.TeamCityThreadPoolExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

public class TorrentsSeeder {
  private final static Logger LOG = Logger.getInstance(TorrentsSeeder.class.getName());

  public static final String TORRENTS_DIT_PATH = ".teamcity/torrents";

  public static final int CHECK_TORRENTS_INTERVAL = TeamCityProperties.getInteger("teamcity.torrents.checkTorrentsIntervalSec", 5 * 60);
  private static final int FLUSH_DB_INTERVAL = TeamCityProperties.getInteger("teamcity.torrents.flushDBIntervalSec", 3 * 60);

  public static final String PLUGIN_EXECUTOR_NAME = "Torrent plugin worker";

  @NotNull
  private final TeamcityTorrentClient myClient;
  @NotNull
  private final TeamCityThreadPoolExecutor myWorkerExecutor;
  private final TorrentFilesDB myTorrentFilesDB;
  private final ScheduledExecutorService myExecutor;
  private volatile boolean myRemoveExpiredTorrentFiles;
  private volatile boolean myWorking = false;
  private volatile int myMaxTorrentsToSeed; // no limit by default
  @Nullable
  private volatile ScheduledFuture<?> myBrokenFilesCheckerFuture;
  @Nullable
  private volatile ScheduledFuture<?> myDBFlushFuture;

  public TorrentsSeeder(@NotNull File torrentStorage,
                        int maxTorrentsToSeed,
                        @Nullable PathConverter pathConverter,
                        ScheduledExecutorService executor,
                        @NotNull final TorrentConfiguration torrentConfiguration) {
    myMaxTorrentsToSeed = maxTorrentsToSeed;
    myTorrentFilesDB = new TorrentFilesDB(new File(torrentStorage, "torrents.db"), maxTorrentsToSeed, pathConverter, new TorrentFilesDB.CacheListener() {
      public void onRemove(@NotNull Map.Entry<File, File> removedEntry) {
        myClient.stopSeeding(removedEntry.getValue());
        if (myRemoveExpiredTorrentFiles) {
          FileUtil.delete(removedEntry.getValue());
        }
      }
    });
    int workerPoolSize = torrentConfiguration.getWorkerPoolSize();
    myWorkerExecutor = new TeamCityThreadPoolExecutor(workerPoolSize, workerPoolSize,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(2000),
            new NamedThreadFactory(PLUGIN_EXECUTOR_NAME));
    myWorkerExecutor.allowCoreThreadTimeOut(true);
    myClient = new TeamcityTorrentClient(myWorkerExecutor);
    myExecutor = executor;
  }

  /**
   * @param removeExpiredTorrentFiles if true, then torrent files removed from the cache will be removed from disk too
   */
  public void setRemoveExpiredTorrentFiles(boolean removeExpiredTorrentFiles) {
    myRemoveExpiredTorrentFiles = removeExpiredTorrentFiles;
  }

  public boolean isSeedingByPath(@NotNull File srcFile) {
    return myClient.isSeedingByPath(srcFile);
  }

  public boolean isSeeding(@NotNull File torrentFile) {
    return myClient.isSeeding(torrentFile);
  }

  public void registerSrcAndTorrentFile(@NotNull File srcFile, @NotNull File torrentFile, boolean startSeeding) {
    myTorrentFilesDB.addFileAndTorrent(srcFile, torrentFile);
    if (startSeeding) {
      seedTorrent(srcFile, torrentFile);
    }
  }

  public void unregisterSrcFile(@NotNull File srcFile) {
    myTorrentFilesDB.removeSrcFile(srcFile);
  }

  public synchronized void start(@NotNull InetAddress[] address,
                                 @Nullable final URI defaultTrackerURI,
                                 final int announceInterval) throws IOException {
    if (myWorking) return; // already started

    myWorking = true;

    try {
      myClient.start(address, defaultTrackerURI, announceInterval);
    } catch (RejectedExecutionException e) {
      LOG.warnAndDebugDetails("Failed to start bittorrent client", e);
    }
    try {
      myExecutor.submit(new Runnable() {
        public void run() {
          checkForBrokenFiles();
          for (Map.Entry<File, File> entry : myTorrentFilesDB.getFileAndTorrentMap().entrySet()) {
            seedTorrent(entry.getKey(), entry.getValue());
          }
        }
      });
    } catch (RejectedExecutionException e) {
      LOG.warnAndDebugDetails("Failed to execute seed stored torrents task", e);
    }
    try {
      myBrokenFilesCheckerFuture = myExecutor.scheduleWithFixedDelay(new Runnable() {
        public void run() {
          try {
            checkForBrokenFiles();
          } catch (Throwable e) {
            LOG.warnAndDebugDetails("Unhandled exception in check brokenf files task", e);
          }
        }
      }, CHECK_TORRENTS_INTERVAL, CHECK_TORRENTS_INTERVAL, TimeUnit.SECONDS);
    } catch (RejectedExecutionException e) {
      LOG.warnAndDebugDetails("Failed to schedule broken files check task", e);
    }
    try {
      myDBFlushFuture = myExecutor.scheduleWithFixedDelay(new Runnable() {
        public void run() {
          try {
            flushTorrentsDB();
          } catch (Throwable e) {
            LOG.warnAndDebugDetails("Unhandled exception in flush torrents db task", e);
          }
        }
      }, FLUSH_DB_INTERVAL, FLUSH_DB_INTERVAL, TimeUnit.SECONDS);
    } catch (RejectedExecutionException e) {
      LOG.warnAndDebugDetails("Failed to schedule db flush task", e);
    }
  }

  void checkForBrokenFiles() {
    myTorrentFilesDB.cleanupBrokenFiles();
  }

  private void flushTorrentsDB() {
    try {
      myTorrentFilesDB.flush();
    } catch (IOException e) {
      LOG.warnAndDebugDetails("Failed to flush torrents database on disk", e);
    }
  }

  private void seedTorrent(@NotNull File srcFile, @NotNull File torrentFile) {
    try {
      if (isSeeding(torrentFile)) {
        myClient.stopSeeding(torrentFile);
      }
      LOG.debug("Start seeding file: " + srcFile.getAbsolutePath());
      myClient.seedTorrent(torrentFile, srcFile);
    } catch (NoSuchAlgorithmException e) {
      LOG.warnAndDebugDetails("Failed to start seeding torrent: " + torrentFile.getAbsolutePath(), e);
    } catch (IOException e) {
      LOG.warnAndDebugDetails("Failed to start seeding torrent: " + torrentFile.getAbsolutePath(), e);
    }
  }

  public synchronized void stop() {
    if (!myWorking) return;
    myWorking = false;
    myClient.stop();
    try {
      myTorrentFilesDB.flush();
    } catch (IOException e) {
      LOG.warnAndDebugDetails("Failed to save torrents database on disk", e);
    }
  }

  public void dispose() {
    stop();
    final ScheduledFuture<?> localFuture = myBrokenFilesCheckerFuture;
    if (localFuture != null) {
      localFuture.cancel(true);
    }
    final ScheduledFuture<?> dbFlushFuture = myDBFlushFuture;
    if (dbFlushFuture != null) {
      dbFlushFuture.cancel(true);
    }
    ThreadUtil.shutdownGracefully(myWorkerExecutor, "bittorrent client worker executor");
  }

  public boolean isStopped() {
    return !myWorking;
  }

  public int getNumberOfSeededTorrents() {
    return myClient.getNumberOfSeededTorrents();
  }

  public void setAnnounceInterval(final int announceInterval) {
    myClient.setAnnounceInterval(announceInterval);
  }

  public void setSocketTimeout(final int socketTimeoutSec) {
    myClient.setSocketTimeout(socketTimeoutSec);
  }

  public void setCleanupTimeout(final int cleanupTimeoutSec) {
    myClient.setCleanupTimeout(cleanupTimeoutSec);
  }

  public void setMaxIncomingConnectionsCount(int maxIncomingConnectionsCount) {
    myClient.setMaxIncomingConnectionsCount(maxIncomingConnectionsCount);
  }

  public void setMaxOutgoingConnectionsCount(int maxOutgoingConnectionsCount) {
    myClient.setMaxOutgoingConnectionsCount(maxOutgoingConnectionsCount);
  }

  @NotNull
  public TeamcityTorrentClient getClient() {
    return myClient;
  }

  public void setMaxTorrentsToSeed(int maxTorrentsToSeed) {
    myMaxTorrentsToSeed = maxTorrentsToSeed;
    myTorrentFilesDB.setMaxTorrents(maxTorrentsToSeed);
  }

  public int getMaxTorrentsToSeed() {
    return myMaxTorrentsToSeed;
  }

  @NotNull
  public Collection<SharedTorrent> getSharedTorrents() {
    return myClient.getSharedTorrents();
  }

  /**
   * @return set of all registered .torrent files
   */
  @NotNull
  public Set<File> getRegisteredTorrentFiles() {
    return new HashSet<File>(myTorrentFilesDB.getFileAndTorrentMap().values());
  }
}

