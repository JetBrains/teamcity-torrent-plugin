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
  String SEEDER_ENABLED = "torrent.seeder.enabled";
  boolean DEFAULT_SEEDER_ENABLED = true;
  String FILE_SIZE_THRESHOLD = "torrent.file.size.threshold.mb";
  int DEFAULT_FILE_SIZE_THRESHOLD = 10;
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
  String TORRENT_ENABLED ="torrent.enabled";
  boolean DEFAULT_TORRENT_ENABLED = DEFAULT_DOWNLOAD_ENABLED || DEFAULT_TRANSPORT_ENABLED;
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
}
