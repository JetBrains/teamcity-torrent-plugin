package jetbrains.buildServer.torrent;

import org.jetbrains.annotations.Nullable;

/**
 * User: Victory.Bedrosova
 * Date: 10/12/12
 * Time: 4:02 PM
 */
public interface TorrentConfiguration {
  String TRACKER_ENABLED = "torrent.tracker.enabled";
  String OWN_ADDRESS = "torrent.ownAddress";
  String SEEDER_ENABLED = "torrent.seeder.enabled";
  String FILE_SIZE_THRESHOLD = "torrent.file.size.threshold.mb";
  String TRANSPORT_ENABLED = "torrent.transport.enabled";
  String DOWNLOAD_ENABLED = "torrent.download.enabled";
  String ANNOUNCE_INTERVAL = "torrent.announce.interval.sec";
  String TRACKER_TORRENT_EXPIRE_TIMEOUT = "torrent.tracker.expire.timeout.sec";
  String MAX_NUMBER_OF_SEEDED_TORRENTS = "torrent.max.seeded.number";
  String TRACKER_DEDICATED_PORT ="torrent.tracker.dedicated.port";
  String TORRENT_ENABLED ="torrent.enabled";
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
