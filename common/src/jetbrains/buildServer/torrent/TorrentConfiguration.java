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
  String FILE_SIZE_THRESHOLD = "torrent.file.size.threshold.mb";
  int DEFAULT_FILE_SIZE_THRESHOLD = 10;
  String MIN_SEEDERS_FOR_DOWNLOAD = "torrent.download.seeders.count";
  int DEFAULT_MIN_SEEDERS_FOR_DOWNLOAD = 2;
  String TRANSPORT_ENABLED = "torrent.transport.enabled";
  boolean DEFAULT_TRANSPORT_ENABLED = false;
  String DOWNLOAD_ENABLED = "torrent.download.enabled";
  boolean DEFAULT_DOWNLOAD_ENABLED = false;
  String ANNOUNCE_INTERVAL = "torrent.announce.interval.sec";
  int DEFAULT_ANNOUNCE_INTERVAL = 60;
  String TRACKER_TORRENT_EXPIRE_TIMEOUT = "torrent.tracker.expire.timeout.sec";
  int DEFAULT_TRACKER_TORRENT_EXPIRE_TIMEOUT = 180;
  String MAX_NUMBER_OF_SEEDED_TORRENTS = "torrent.max.seeded.number";
  int DEFAULT_MAX_NUMBER_OF_SEEDED_TORRENTS = 2000;
  String TRACKER_DEDICATED_PORT ="torrent.tracker.dedicated.port";
  boolean DEFAULT_TRACKER_DEDICATED_PORT = false;
  boolean DEFAULT_TORRENT_ENABLED = false;
  String SOCKET_CONNECTION_TIMEOUT ="torrent.network.connection.timeout";
  String MAX_INCOMING_CONNECTIONS ="torrent.network.connection.incoming";
  String MAX_OUTGOING_CONNECTIONS ="torrent.network.connection.outgoing";
  int DEFAULT_MAX_OUTGOING_CONNECTIONS = 20;
  int DEFAULT_MAX_INCOMING_CONNECTIONS = 15;
  String CLEANUP_TIMEOUT ="torrent.network.cleanup.timeout";
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
  int getFileSizeThresholdMb();

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
   * Return max incoming connections count. If client already have more open connections, that this value,
   * then all incoming will be ignored
   * @return see above
   */
  int getMaxIncomingConnectionsCount();

  /**
   * Return max outgoing connections count. If client already have more open connections, that this value,
   * then all outgoing will be ignored
   * @return see above
   */
  int getMaxOutgoingConnectionsCount();

}
