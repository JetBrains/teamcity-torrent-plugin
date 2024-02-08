

package jetbrains.buildServer.torrent.settings;

public interface LeechSettings {

  String MIN_SEEDERS_FOR_DOWNLOAD = "teamcity.torrent.peer.download.minSeedersToStart";
  String MAX_PIECE_DOWNLOAD_TIME = "teamcity.torrent.peer.download.pieceTotalTimeout.seconds";
  String DOWNLOAD_ENABLED = "teamcity.torrent.peer.download.enabled";
  int DEFAULT_MIN_SEEDERS_FOR_DOWNLOAD = 2;
  int DEFAULT_MAX_PIECE_DOWNLOAD_TIME = 7;
  boolean DEFAULT_DOWNLOAD_ENABLED = false;

  /**
   * Returns maximum time for download one piece
   *
   * @return see above
   */
  int getMaxPieceDownloadTime();

  /**
   * Indicates whether torrent transport is enabled and agent must try to download artifact via bittorrent
   */
  boolean isDownloadEnabled();

  /**
   * Returns min seeders count for download.
   *
   * @return see above
   */
  int getMinSeedersForDownload();

}