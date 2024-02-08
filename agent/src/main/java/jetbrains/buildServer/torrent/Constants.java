

package jetbrains.buildServer.torrent;

import jetbrains.buildServer.ArtifactsConstants;

import java.io.File;

public class Constants {
  public static final String TORRENTS_DIRNAME = "torrents";
  public static final String TORRENTS_DIR_ON_SERVER = ArtifactsConstants.TEAMCITY_ARTIFACTS_DIR + "/" + TORRENTS_DIRNAME + "/";
  public static final String CACHE_STATIC_DIRS = "httpAuth" + File.separator + "repository" + File.separator + "download";
  public static final String TEAMCITY_IVY = "teamcity-ivy.xml";
  public static final String TORRENT_FILE_COPIES_DIR = "tempTorrentFilesCopies";
}