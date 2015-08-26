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
import com.turn.ttorrent.client.SharedTorrent;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.torrent.torrent.TeamcityTorrentClient;
import jetbrains.buildServer.util.ThreadUtil;
import jetbrains.buildServer.util.executors.ExecutorsFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TorrentsDirectorySeeder {
  private final static Logger LOG = Logger.getInstance(TorrentsDirectorySeeder.class.getName());

  public static final String TORRENTS_DIT_PATH = ".teamcity/torrents";

  public static final int CHECK_TORRENTS_INTERVAL = TeamCityProperties.getInteger("teamcity.torrents.checkTorrentsIntervalSec", 5*60);

  public static final String EXECUTOR_NAME = "Torrent files checker";

  @NotNull
  private final TeamcityTorrentClient myTorrentSeeder = new TeamcityTorrentClient();
  private final TorrentFilesDB myTorrentFilesDB;
  private final ScheduledExecutorService myExecutor;
  private volatile boolean myStopped = true;
  private volatile int myMaxTorrentsToSeed; // no limit by default

  public TorrentsDirectorySeeder(@NotNull File torrentStorage, int maxTorrentsToSeed, @Nullable PathConverter pathConverter) {
    myMaxTorrentsToSeed = maxTorrentsToSeed;
    myTorrentFilesDB = new TorrentFilesDB(new File(torrentStorage, "torrents.db"), maxTorrentsToSeed, pathConverter);
    myExecutor = ExecutorsFactory.newFixedScheduledDaemonExecutor(EXECUTOR_NAME, 1);
  }

  public boolean isSeedingByPath(@NotNull File srcFile){
    return myTorrentSeeder.isSeedingByPath(srcFile);
  }

  public boolean isSeeding(@NotNull File torrentFile){
    return myTorrentSeeder.isSeeding(torrentFile);
  }

  public void registerSrcAndTorrentFile(@NotNull File srcFile, @NotNull File torrentFile, boolean startSeeding) {
    myTorrentFilesDB.addFileAndTorrent(srcFile, torrentFile);
    if (startSeeding) {
      seedTorrent(srcFile, torrentFile);
    }
  }

  public void stopSeedingSrcFile(@NotNull File srcFile, boolean removeSrcFile) {
    myTorrentSeeder.stopSeedingByPath(srcFile);
    if (removeSrcFile) {
      myTorrentFilesDB.removeSrcFile(srcFile);
    }
  }

  public void start(@NotNull InetAddress[] address,
                    @Nullable final URI defaultTrackerURI,
                    final int announceInterval) throws IOException {
    if (!myStopped) return; // already started

    myTorrentSeeder.start(address, defaultTrackerURI, announceInterval);

    myStopped = false;

    myExecutor.submit(new Runnable() {
      public void run() {
        checkForBrokenFiles();
        for (Map.Entry<File, File> entry: myTorrentFilesDB.getFileAndTorrentMap().entrySet()) {
          seedTorrent(entry.getKey(), entry.getValue());
        }
      }
    });

    myExecutor.scheduleWithFixedDelay(new Runnable() {
      public void run() {
        checkForBrokenFiles();
      }
    }, CHECK_TORRENTS_INTERVAL, CHECK_TORRENTS_INTERVAL, TimeUnit.SECONDS);
  }

  void checkForBrokenFiles() {
    for (File brokenTorrent: myTorrentFilesDB.cleanupBrokenFiles()) {
      myTorrentSeeder.stopSeeding(brokenTorrent);
    }
    try {
      myTorrentFilesDB.flush();
    } catch (IOException e) {
      LOG.warnAndDebugDetails("Failed to flush torrents database on disk", e);
    }
  }

  private void seedTorrent(@NotNull File srcFile, @NotNull File torrentFile) {
    try {
      if (isSeeding(torrentFile)) {
        myTorrentSeeder.stopSeeding(torrentFile);
      }
      myTorrentSeeder.seedTorrent(torrentFile, srcFile);
    } catch (NoSuchAlgorithmException e) {
      LOG.warnAndDebugDetails("Failed to start seeding torrent: " + torrentFile.getAbsolutePath(), e);
    } catch (IOException e) {
      LOG.warnAndDebugDetails("Failed to start seeding torrent: " + torrentFile.getAbsolutePath(), e);
    }
  }

  public void stop() {
    if (myStopped) return;
    myStopped = true;
    myTorrentSeeder.stop();
    try {
      myTorrentFilesDB.flush();
    } catch (IOException e) {
      LOG.warnAndDebugDetails("Failed to save torrents database on disk", e);
    }
  }

  public void dispose() {
    stop();
    ThreadUtil.shutdownGracefully(myExecutor, EXECUTOR_NAME);
  }

  public boolean isStopped() {
    return myStopped;
  }

  public int getNumberOfSeededTorrents() {
    return myTorrentSeeder.getNumberOfSeededTorrents();
  }

  public void setAnnounceInterval(final int announceInterval){
    myTorrentSeeder.setAnnounceInterval(announceInterval);
  }

  @NotNull
  public TeamcityTorrentClient getTorrentSeeder() {
    return myTorrentSeeder;
  }

  public void setMaxTorrentsToSeed(int maxTorrentsToSeed) {
    myMaxTorrentsToSeed = maxTorrentsToSeed;
    myTorrentFilesDB.setMaxTorrents(maxTorrentsToSeed);
  }

  public int getMaxTorrentsToSeed() {
    return myMaxTorrentsToSeed;
  }

  public Collection<SharedTorrent> getSharedTorrents(){
    return myTorrentSeeder.getSharedTorrents();
  }
}

