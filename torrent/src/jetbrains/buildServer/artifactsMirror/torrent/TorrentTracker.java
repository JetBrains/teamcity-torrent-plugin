package jetbrains.buildServer.artifactsMirror.torrent;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.turn.ttorrent.common.Torrent;
import com.turn.ttorrent.tracker.TrackedTorrent;
import com.turn.ttorrent.tracker.Tracker;
import jetbrains.buildServer.NetworkUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TorrentTracker {
  private final static Logger LOG = Logger.getInstance(TorrentTracker.class.getName());
  public static final String TORRENT_FILE_SUFFIX = ".torrent";

  private Tracker myTracker;
  private final TorrentSeeder mySeeder;
  private final List<AnnouncedTorrent> myAnnouncedTorrents = new ArrayList<AnnouncedTorrent>();

  public TorrentTracker(@NotNull TorrentSeeder seeder) {
    mySeeder = seeder;
  }

  public void start(@NotNull String rootUrl) {
    int freePort = NetworkUtil.getFreePort(6969);

    try {
      if (rootUrl.endsWith("/")) rootUrl = rootUrl.substring(0, rootUrl.length()-1);
      URI serverUrl = new URI(rootUrl);
      InetAddress serverAddress = InetAddress.getByName(serverUrl.getHost());
      myTracker = new Tracker(new InetSocketAddress(serverAddress, freePort));
      myTracker.start();
      LOG.info("Torrent tracker started on url: " + myTracker.getAnnounceUrl().toString());
    } catch (Exception e) {
      LOG.error("Failed to start torrent tracker, server URL is invalid: " + e.toString());
    }
  }

  public void stop() {
    if (myTracker != null) {
      LOG.info("Stopping torrent tracker");
      myTracker.stop();
    }
  }

  /**
   * Creates the torrent file for the specified <code>srcFile</code>.
   *
   * @param srcFile file to distribute
   * @param torrentsStore the directory (store) where to create the file
   * @return true if successful
   */
  public boolean createTorrent(@NotNull File srcFile, @NotNull File torrentsStore) {
    if (myTracker == null) {
      return false;
    }

    try {
      File torrentFile = new File(torrentsStore, srcFile.getName() + TORRENT_FILE_SUFFIX);
      if (torrentFile.isFile()) {
        FileUtil.delete(torrentFile);
      }

      Torrent t = Torrent.create(srcFile, myTracker.getAnnounceUrl().toURI(), "TeamCity");
      t.save(torrentFile);
      LOG.info("Torrent file created: " + torrentFile);
      return true;
    } catch (Exception e) {
      LOG.warn("Failed to create torrent file: " + e.toString());
      LOG.debug(e.getMessage(), e);
      return false;
    }
  }

  /**
   * Announces a torrent file in the tracker and starts a seeder thread.
   *
   * @param srcFile file to distribute
   * @param torrentFile the torrent file corresponding to the <code>srcFile</code>.
   * @return true if successful
   */
  public boolean announceAndSeedTorrent(@NotNull File srcFile, @NotNull File torrentFile) {
    if (myTracker == null) {
      return false;
    }

    try {
      assert torrentFile.isFile();
      Torrent t = Torrent.load(torrentFile, null);
      TrackedTorrent trackedTorrent = new TrackedTorrent(t);
      myTracker.announce(trackedTorrent);

      synchronized (myAnnouncedTorrents) {
        myAnnouncedTorrents.add(new AnnouncedTorrent(srcFile, torrentFile, trackedTorrent));
      }

      mySeeder.seedTorrent(t, srcFile);
      LOG.info("Torrent announced in tracker: " + srcFile.getAbsolutePath());

      return true;
    } catch (Exception e) {
      LOG.warn("Failed to announce file in torrent tracker: " + e.toString());
      return false;
    }
  }

  @NotNull
  public List<AnnouncedTorrent> getAnnouncedTorrents() {
    synchronized (myAnnouncedTorrents) {
      return Collections.unmodifiableList(new ArrayList<AnnouncedTorrent>(myAnnouncedTorrents));
    }
  }

  public void removeAnnouncedTorrent(@NotNull AnnouncedTorrent torrent) {
    myTracker.remove(torrent.getTorrent());
    synchronized (myAnnouncedTorrents) {
      myAnnouncedTorrents.remove(torrent);
    }
  }
}
