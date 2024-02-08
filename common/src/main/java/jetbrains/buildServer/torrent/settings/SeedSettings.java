

package jetbrains.buildServer.torrent.settings;

public interface SeedSettings {

  String SEEDING_ENABLED = "teamcity.torrent.peer.seeding.enabled";
  boolean DEFAULT_SEEDING_ENABLED = false;

  String MAX_NUMBER_OF_SEEDED_TORRENTS = "teamcity.torrent.seeder.maxSeedingFiles";
  int DEFAULT_MAX_NUMBER_OF_SEEDED_TORRENTS = 2000;

  /**
   * Indicates if peers must seed artifacts
   */
  boolean isSeedingEnabled();

  /**
   * @return count of max seeded torrents
   */
  int getMaxNumberOfSeededTorrents();

}