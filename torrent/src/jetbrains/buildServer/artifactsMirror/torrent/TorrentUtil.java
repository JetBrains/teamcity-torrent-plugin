package jetbrains.buildServer.artifactsMirror.torrent;

import com.intellij.openapi.diagnostic.Logger;
import com.turn.ttorrent.common.Torrent;
import jetbrains.buildServer.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public class TorrentUtil {
  private final static Logger LOG = Logger.getInstance(TorrentUtil.class.getName());

  public static final String TORRENT_FILE_SUFFIX = ".torrent";

  @NotNull
  public static Torrent loadTorrent(@NotNull File torrentFile) throws IOException {
    try {
      return Torrent.load(torrentFile, null);
    } catch (NoSuchAlgorithmException e) {
      ExceptionUtil.rethrowAsRuntimeException(e);
    }

    return null;
  }

  /**
   * Creates the torrent file for the specified <code>srcFile</code> and announce URI.
   * If such torrent already exists, loads and returns it.
   *
   * @param srcFile file to distribute
   * @param torrentsStore the directory (store) where to create the file
   * @return true if successful
   */
  @Nullable
  public static Torrent getOrCreateTorrent(@NotNull File srcFile, @NotNull File torrentsStore, @NotNull URI announceURI) {
    Torrent.setHashingThreadsCount(2); // limit number of threads generating hashes for a file

    File torrentFile = new File(torrentsStore, srcFile.getName() + TORRENT_FILE_SUFFIX);
    if (torrentFile.isFile()) {
      try {
        Torrent t =  loadTorrent(torrentFile);
        for (List<URI> uris: t.getAnnounceList()) {
          if (uris.contains(announceURI)) return t;
        }
      } catch (IOException e) {
        LOG.warn("Failed to load existing torrent file: " + torrentFile.getAbsolutePath() + ", error: " + e.toString() + ". Will create new torrent file instead.");
      }
    }

    try {
      Torrent t = Torrent.create(srcFile, announceURI, "TeamCity");
      t.save(torrentFile);
      return t;
    } catch (Exception e) {
      ExceptionUtil.rethrowAsRuntimeException(e);
    }

    return null;
  }
}
