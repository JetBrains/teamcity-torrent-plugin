package jetbrains.buildServer.artifactsMirror;

import org.jetbrains.annotations.Nullable;

/**
 * User: Victory.Bedrosova
 * Date: 10/12/12
 * Time: 4:02 PM
 */
public interface TrackerManager {
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
}
