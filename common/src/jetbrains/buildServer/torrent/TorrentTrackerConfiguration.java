package jetbrains.buildServer.torrent;

import org.jetbrains.annotations.Nullable;

/**
 * User: Victory.Bedrosova
 * Date: 10/12/12
 * Time: 4:02 PM
 */
public interface TorrentTrackerConfiguration {
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

 }
