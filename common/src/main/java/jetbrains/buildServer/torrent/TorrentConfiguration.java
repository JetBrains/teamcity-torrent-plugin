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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Victory.Bedrosova
 * Date: 10/12/12
 * Time: 4:02 PM
 */
public interface TorrentConfiguration {
  String TRACKER_ENABLED = "torrent.tracker.enabled";
  boolean DEFAULT_TRACKER_ENABLED = true;
  String OWN_ADDRESS = "teamcity.torrent.network.ownAddress";
  String PREFIX_ADDRESS = "teamcity.torrent.network.prefixAddress";
  String FILE_SIZE_THRESHOLD = "teamcity.torrent.seeder.minFileSize";
  String DEFAULT_FILE_SIZE_THRESHOLD = "10mb";
  String USER_DOWNLOAD_ENABLED = "teamcity.torrent.download.enabled";
  boolean DEFAULT_DOWNLOAD_ENABLED = false;
  String ANNOUNCE_INTERVAL = "torrent.announce.interval.sec";
  int DEFAULT_ANNOUNCE_INTERVAL = 60;
  String TRACKER_TORRENT_EXPIRE_TIMEOUT = "torrent.tracker.expire.timeout.sec";
  int DEFAULT_TRACKER_TORRENT_EXPIRE_TIMEOUT = 600;
  String TRACKER_DEDICATED_PORT ="torrent.tracker.dedicated.port";
  boolean DEFAULT_TRACKER_DEDICATED_PORT = false;
  boolean DEFAULT_TORRENT_ENABLED = false;
  String SOCKET_CONNECTION_TIMEOUT ="teamcity.torrent.peer.connection.operationTimeout.seconds";
  String MAX_INCOMING_CONNECTIONS ="teamcity.torrent.peer.download.maxConnections";
  int DEFAULT_MAX_CONNECTIONS = 50;
  String CLEANUP_TIMEOUT ="teamcity.torrent.peer.connection.operationTimeoutCheckInterval.seconds";
  String SEND_BUFFER_SIZE = "teamcity.torrent.network.sendBufferSize";
  String RECEIVE_BUFFER_SIZE = "teamcity.torrent.network.receiveBufferSize";
  String WORKER_POOL_SIZE = "teamcity.torrent.network.poolSize";
  int DEFAULT_WORKER_POOL_SIZE = 10;
  String VALIDATOR_POOL_SIZE = "teamcity.torrent.validation.poolSize";
  int DEFAULT_VALIDATOR_POOL_SIZE = 4;
  int DEFAULT_BUFFER_SIZE_WINDOWS = 1024*1024;
  // this is fake option to multicast announce url changes;
  String ANNOUNCE_URL = "announce.url";

  /**
   * Returns announce URL of the tracker or null if tracker isn't started
   * @return see above
   */
  @Nullable String getAnnounceUrl();

  /**
   * host address, which must be used by agent/server in tracker announce messages
   * @return see above
   */
  @NotNull String getOwnTorrentAddress();

  /**
   * prefix of agent inet address. Can also be used for detect really agent address when it have many net interfaces
   * @return see above
   */
  @NotNull String getAgentAddressPrefix();

  /**
   * Returns minimum supported file size to avoid seeding very small files
   * @return see above
   */
  long getFileSizeThresholdBytes();

  /**
   * Returns max pool size for library worker executor
   * @return see above
   */
  int getWorkerPoolSize();

  /**
   * Returns max pool size for pieces hashing executor
   * @return see above
   */
  int getPieceHashingPoolSize();

  /**
   * Returns socket connection timeout in seconds.
   * If connection is inactive more, that this timeout, then connection will be closed on first cleanup
   * @return see above
   */
  int getSocketTimeout();

  /**
   * Returns cleanup timeout in seconds
   * @return see above
   */
  int getCleanupTimeout();

  /**
   * Return max connections count.
   * @return see above
   */
  int getMaxConnectionsCount();

}
