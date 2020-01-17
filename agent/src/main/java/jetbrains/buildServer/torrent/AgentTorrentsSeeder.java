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

package jetbrains.buildServer.torrent;

import com.turn.ttorrent.client.SelectorFactoryImpl;
import com.turn.ttorrent.client.SharedTorrent;
import com.turn.ttorrent.client.announce.TrackerClientFactoryImpl;
import com.turn.ttorrent.common.TorrentLoggerFactory;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.torrent.seeder.ParentDirConverter;
import jetbrains.buildServer.torrent.seeder.TorrentsSeeder;
import jetbrains.buildServer.torrent.torrent.TeamcityTorrentClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class AgentTorrentsSeeder {

  static {
    TorrentLoggerFactory.setStaticLoggersName("jetbrains.torrent.Library");
  }

  private final ScheduledExecutorService myExecutorService;
  private final TorrentsSeeder myTorrentsSeeder;

  public AgentTorrentsSeeder(@NotNull final BuildAgentConfiguration agentConfiguration,
                             @NotNull final TorrentConfiguration torrentConfiguration) {
    myExecutorService = Executors.newScheduledThreadPool(1);
    myTorrentsSeeder = new TorrentsSeeder(agentConfiguration.getCacheDirectory(Constants.TORRENTS_DIRNAME), TeamCityProperties.getInteger("teamcity.torrents.agent.maxSeededTorrents", 5000), new ParentDirConverter() {
      @NotNull
      @Override
      public File getParentDir() {
        return agentConfiguration.getSystemDirectory();
      }
    }, myExecutorService, torrentConfiguration, new TrackerClientFactoryImpl());
  }

  public void setRemoveExpiredTorrentFiles(boolean removeExpiredTorrentFiles) {
    myTorrentsSeeder.setRemoveExpiredTorrentFiles(removeExpiredTorrentFiles);
  }

  public TorrentsSeeder getTorrentsSeeder() {
    return myTorrentsSeeder;
  }

  public boolean isSeeding(@NotNull File torrentFile) {
    return myTorrentsSeeder.isSeeding(torrentFile);
  }

  public void registerSrcAndTorrentFile(@NotNull File srcFile, @NotNull File torrentFile, boolean startSeeding) {
    myTorrentsSeeder.registerSrcAndTorrentFile(srcFile, torrentFile, startSeeding);
  }

  public void unregisterSrcFile(@NotNull File srcFile) {
    myTorrentsSeeder.unregisterSrcFile(srcFile);
  }

  public void start(@NotNull InetAddress[] address, @Nullable URI defaultTrackerURI, int announceInterval) throws IOException {
    myTorrentsSeeder.start(address, defaultTrackerURI, announceInterval, new SelectorFactoryImpl());
  }

  public void stop() {
    myTorrentsSeeder.stop();
  }

  public void dispose() {
    myTorrentsSeeder.dispose();
    myExecutorService.shutdownNow();
  }

  public boolean isStopped() {
    return myTorrentsSeeder.isStopped();
  }

  public int getNumberOfSeededTorrents() {
    return myTorrentsSeeder.getNumberOfSeededTorrents();
  }

  public void setAnnounceInterval(int announceInterval) {
    myTorrentsSeeder.setAnnounceInterval(announceInterval);
  }

  public void setSocketTimeout(int socketTimeoutSec) {
    myTorrentsSeeder.setSocketTimeout(socketTimeoutSec);
  }

  public void setCleanupTimeout(int cleanupTimeoutSec) {
    myTorrentsSeeder.setCleanupTimeout(cleanupTimeoutSec);
  }

  public void setMaxIncomingConnectionsCount(int maxIncomingConnectionsCount) {
    myTorrentsSeeder.setMaxIncomingConnectionsCount(maxIncomingConnectionsCount);
  }

  public void setMaxOutgoingConnectionsCount(int maxOutgoingConnectionsCount) {
    myTorrentsSeeder.setMaxOutgoingConnectionsCount(maxOutgoingConnectionsCount);
  }

  @NotNull
  public TeamcityTorrentClient getClient() {
    return myTorrentsSeeder.getClient();
  }

  public void setMaxTorrentsToSeed(int maxTorrentsToSeed) {
    myTorrentsSeeder.setMaxTorrentsToSeed(maxTorrentsToSeed);
  }

  public int getMaxTorrentsToSeed() {
    return myTorrentsSeeder.getMaxTorrentsToSeed();
  }

  @NotNull
  public Collection<SharedTorrent> getSharedTorrents() {
    return myTorrentsSeeder.getSharedTorrents();
  }

  @NotNull
  public Set<File> getRegisteredTorrentFiles() {
    return myTorrentsSeeder.getRegisteredTorrentFiles();
  }
}
