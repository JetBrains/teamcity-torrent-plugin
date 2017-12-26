package jetbrains.buildServer.torrent;

import org.jetbrains.annotations.Nullable;

/**
 * User: Victory.Bedrosova
 * Date: 10/12/12
 * Time: 4:02 PM
 */
public interface TorrentConfiguration {
  String TRACKER_ENABLED = "torrent.tracker.enabled";
  boolean DEFAULT_TRACKER_ENABLED = true;
  String OWN_ADDRESS = "torrent.ownAddress";
  String FILE_SIZE_THRESHOLD = "teamcity.torrent.seeder.minFileSize";
  String DEFAULT_FILE_SIZE_THRESHOLD = "10mb";
  String MIN_SEEDERS_FOR_DOWNLOAD = "teamcity.torrent.peer.download.minSeedersToStart";
  int DEFAULT_MIN_SEEDERS_FOR_DOWNLOAD = 2;
  String TRANSPORT_ENABLED = "torrent.transport.enabled";
  boolean DEFAULT_TRANSPORT_ENABLED = false;
  String DOWNLOAD_ENABLED = "torrent.download.enabled";
  boolean DEFAULT_DOWNLOAD_ENABLED = false;
  String ANNOUNCE_INTERVAL = "torrent.announce.interval.sec";
  int DEFAULT_ANNOUNCE_INTERVAL = 60;
  String TRACKER_TORRENT_EXPIRE_TIMEOUT = "torrent.tracker.expire.timeout.sec";
  int DEFAULT_TRACKER_TORRENT_EXPIRE_TIMEOUT = 180;
  String MAX_NUMBER_OF_SEEDED_TORRENTS = "teamcity.torrent.seeder.maxSeedingFiles";
  int DEFAULT_MAX_NUMBER_OF_SEEDED_TORRENTS = 2000;
  String TRACKER_DEDICATED_PORT ="torrent.tracker.dedicated.port";
  boolean DEFAULT_TRACKER_DEDICATED_PORT = false;
  boolean DEFAULT_TORRENT_ENABLED = false;
  String SOCKET_CONNECTION_TIMEOUT ="teamcity.torrent.peer.connection.operationTimeout.seconds";
  String MAX_INCOMING_CONNECTIONS ="teamcity.torrent.peer.download.maxConnections";
  int DEFAULT_MAX_CONNECTIONS = 50;
  String CLEANUP_TIMEOUT ="teamcity.torrent.peer.connection.operationTimeoutCheckInterval.seconds";
  String MAX_PIECE_DOWNLOAD_TIME ="teamcity.torrent.peer.download.pieceTotalTimeout.seconds";
  int DEFAULT_MAX_PIECE_DOWNLOAD_TIME = 15;
  // this is fake option to multicast announce url changes;
  String ANNOUNCE_URL = "announce.url";

  /**
   * Returns announce URL of the tracker or null if tracker isn't started
   * @return see above
   */
  @Nullable String getAnnounceUrl();

  /**
   * Returns minimum supported file size to avoid seeding very small files
   * @return see above
   */
  long getFileSizeThresholdBytes();

  /**
   * Returns maximum time for download one piece
   * @return see above
   */
  int getMaxPieceDownloadTime();

  /**
  * Returns the announce interval to avoid too frequent or too rare tracker updates
  * @return update interval in seconds
  */
  int getAnnounceIntervalSec();

  /**
   * Indicates whether torrent transport is enabled
   */
  boolean isTransportEnabled();

  /**
   * Indicates whether torrent plugin features are enabled (server and agents seeds, server's tracker,
   * creation of torrent files)
   */
  boolean isTorrentEnabled();

  /**
   * Returns min seeders count for download.
   * @return see above
   */
  int getMinSeedersForDownload();

  /**
   * Returns server URL
   * @return see above
   */
  String getServerURL();

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
