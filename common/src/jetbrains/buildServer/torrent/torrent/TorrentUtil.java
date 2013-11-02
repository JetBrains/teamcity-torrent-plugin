package jetbrains.buildServer.torrent.torrent;

import com.intellij.openapi.diagnostic.Logger;
import com.turn.ttorrent.common.Torrent;
import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.torrent.TorrentConfiguration;
import jetbrains.buildServer.messages.BuildMessage1;
import jetbrains.buildServer.messages.DefaultMessagesInfo;
import jetbrains.buildServer.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Properties;

public class TorrentUtil {
  private final static Logger LOG = Logger.getInstance(TorrentUtil.class.getName());

  public static final String TORRENT_FILE_SUFFIX = ".torrent";

  /**
   * Loads torrent from torrent file
   */
  @NotNull
  public static Torrent loadTorrent(@NotNull File torrentFile) throws IOException {
    setHashingThreadsCount();

    try {
      return Torrent.load(torrentFile);
    } catch (NoSuchAlgorithmException e) {
      ExceptionUtil.rethrowAsRuntimeException(e);
    }

    return null;
  }

  private static void setHashingThreadsCount() {
    Torrent.setHashingThreadsCount(2); // limit number of threads generating hashes for a file
  }

  /**
   * Creates the torrent file for the specified <code>srcFile</code> and announce URI.
   * If such torrent already exists, loads and returns it.
   */
  @NotNull
  public static File getOrCreateTorrent(@NotNull final File srcFile,
                                        @NotNull final String relativePath,
                                        @NotNull final File torrentsStore,
                                        @NotNull final URI announceURI) {
    setHashingThreadsCount();

    File torrentFile = new File(torrentsStore, relativePath + TORRENT_FILE_SUFFIX);
    if (torrentFile.isFile()) {
      try {
        Torrent t =  loadTorrent(torrentFile);
        for (List<URI> uris: t.getAnnounceList()) {
          if (uris.contains(announceURI)) return torrentFile;
        }
      } catch (IOException e) {
        LOG.warn("Failed to load existing torrent file: " + torrentFile.getAbsolutePath() + ", error: " + e.toString() + ". Will create new torrent file instead.");
      }
    }

    createTorrent(srcFile, torrentFile, announceURI);
    return torrentFile;
  }

  /**
   * Creates the torrent file for the specified <code>srcFile</code> and announce URI.
   */
  @Nullable
  public static Torrent createTorrent(@NotNull File srcFile, @NotNull File torrentFile, @NotNull URI announceURI) {
    setHashingThreadsCount();

    try {
      Torrent t = Torrent.create(srcFile, announceURI, "TeamCity");
      t.save(torrentFile);
      return t;
    } catch (Exception e) {
      ExceptionUtil.rethrowAsRuntimeException(e);
    }

    return null;
  }

  public static boolean shouldCreateTorrentFor(@NotNull final long fileSize, @NotNull final TorrentConfiguration configuration){
    return (fileSize >= configuration.getFileSizeThresholdMb()*1024*1024) && configuration.getAnnounceUrl() != null;
  }

  public static void log2Build(final String msg, final BuildProgressLogger buildLogger) {
    final BuildMessage1 textMessage = DefaultMessagesInfo.createTextMessage(msg);
    buildLogger.logMessage(DefaultMessagesInfo.internalize(textMessage));
  }


  public static boolean getBooleanValue(final String systemPropertyName, final boolean defaultValue){
    return getBooleanValue(System.getProperties(), systemPropertyName, defaultValue);
  }

  public static boolean getBooleanValue(final Properties properties, final String propertyName, final boolean defaultValue){
    final String value = properties.getProperty(propertyName);
    if (Boolean.TRUE.toString().equalsIgnoreCase(value)){
      return true;
    } else if (Boolean.FALSE.toString().equalsIgnoreCase(value)){
      return false;
    } else {
      return defaultValue;
    }
  }

  public static int getIntegerValue(final String systemPropertyName, final int defaultValue){
    return getIntegerValue(System.getProperties(), systemPropertyName, defaultValue);
  }

  public static int getIntegerValue(final Properties properties, final String systemPropertyName, final int defaultValue){
    final String value = properties.getProperty(systemPropertyName);
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException ex) {
      return defaultValue;
    }
  }
}
