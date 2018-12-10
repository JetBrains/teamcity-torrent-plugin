package jetbrains.buildServer.torrent.torrent;

import com.intellij.openapi.diagnostic.Logger;
import com.turn.ttorrent.client.CommunicationManager;
import com.turn.ttorrent.common.TorrentCreator;
import com.turn.ttorrent.common.TorrentMetadata;
import com.turn.ttorrent.common.TorrentParser;
import com.turn.ttorrent.common.TorrentSerializer;
import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.messages.BuildMessage1;
import jetbrains.buildServer.messages.DefaultMessagesInfo;
import jetbrains.buildServer.torrent.TorrentConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public class TorrentUtil {
  private final static Logger LOG = Logger.getInstance(TorrentUtil.class.getName());

  public static final String TORRENT_FILE_SUFFIX = ".torrent";

  /**
   * Loads torrent from torrent file
   */
  @NotNull
  public static TorrentMetadata loadTorrent(@NotNull File torrentFile) throws IOException {
    return new TorrentParser().parseFromFile(torrentFile);
  }

  public static boolean isConnectionManagerInitialized(@NotNull CommunicationManager communicationManager) {
    try {
      communicationManager.getConnectionManager();
      return true;
    } catch (IllegalStateException e) {
      return false;
    }
  }


  /**
   * Creates the torrent file for the specified <code>srcFile</code> and announce URI.
   * If such torrent already exists, loads and returns it.
   */
  @Nullable
  public static File getOrCreateTorrent(@NotNull final File srcFile,
                                        @NotNull final String relativePath,
                                        @NotNull final File torrentsStore,
                                        @NotNull final URI announceURI) {

    File torrentFile = new File(torrentsStore, relativePath + TORRENT_FILE_SUFFIX);
    if (torrentFile.isFile()) {
      try {
        TorrentMetadata t =  loadTorrent(torrentFile);

        List<List<String>> announceList = t.getAnnounceList() == null ?
                Collections.singletonList(Collections.singletonList(t.getAnnounce())) :
                t.getAnnounceList();
        for (List<String> uris: announceList) {
          if (uris.contains(announceURI.toString())) return torrentFile;
        }
      } catch (IOException e) {
        LOG.warn("Failed to load existing torrent file: " + torrentFile.getAbsolutePath() + ", error: " + e.toString() + ". Will create new torrent file instead.");
      }
    }

    final TorrentMetadata torrent = createTorrent(srcFile, torrentFile, announceURI);
    return torrent != null ? torrentFile : null;
  }

  /**
   * serialize torrent metadata and save it to file
   * @param metadata specified metadata
   * @param torrentFile file for writing
   * @throws IOException if any io error occurs
   */
  public static void saveTorrentToFile(@NotNull TorrentMetadata metadata, @NotNull File torrentFile) throws IOException{
    FileOutputStream fos = null;
    try {
      fos = new FileOutputStream(torrentFile);
      fos.write(new TorrentSerializer().serialize(metadata));
    } finally {
      if (fos != null) fos.close();
    }
  }

  /**
   * Creates the torrent file for the specified <code>srcFile</code> and announce URI.
   */
  @Nullable
  public static TorrentMetadata createTorrent(@NotNull File srcFile, @NotNull File torrentFile, @NotNull URI announceURI) {

    try {
      TorrentMetadata t = TorrentCreator.create(srcFile, announceURI, "TeamCity");
      saveTorrentToFile(t, torrentFile);
      return t;
    } catch (Exception e) {
      LOG.warnAndDebugDetails(String.format("Unable to create torrent file from %s: %s", srcFile.getPath(), e.toString()), e);
    }

    return null;
  }

  public static boolean shouldCreateTorrentFor(final long fileSize, @NotNull final TorrentConfiguration configuration){
    return (fileSize >= configuration.getFileSizeThresholdBytes()) && configuration.getAnnounceUrl() != null;
  }

  public static void log2Build(final String msg, final BuildProgressLogger buildLogger) {
    final BuildMessage1 textMessage = DefaultMessagesInfo.createTextMessage(msg);
    buildLogger.logMessage(DefaultMessagesInfo.internalize(textMessage));
  }


  public static boolean getBooleanValue(@NotNull final Properties properties, final String propertyName, final boolean defaultValue){
    final String value = properties.getProperty(propertyName);
    if (Boolean.TRUE.toString().equalsIgnoreCase(value)){
      return true;
    } else if (Boolean.FALSE.toString().equalsIgnoreCase(value)){
      return false;
    } else {
      return defaultValue;
    }
  }

  public static int getIntegerValue(@NotNull final Properties properties, final String propertyName, final int defaultValue){
    final String value = properties.getProperty(propertyName);
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException ex) {
      return defaultValue;
    }
  }
}
