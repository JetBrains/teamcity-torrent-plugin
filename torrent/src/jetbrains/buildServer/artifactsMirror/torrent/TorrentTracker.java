package jetbrains.buildServer.artifactsMirror.torrent;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.turn.ttorrent.common.Torrent;
import com.turn.ttorrent.tracker.TrackedTorrent;
import com.turn.ttorrent.tracker.Tracker;
import jetbrains.buildServer.NetworkUtil;
import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.util.Dates;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class TorrentTracker {
  private final static Logger LOG = Logger.getInstance(TorrentTracker.class.getName());

  private SBuildServer myServer;
  private Tracker myTracker;
  private final TorrentSeeder mySeeder;
  private final List<TorrentInfo> myAnnouncedTorrents = new ArrayList<TorrentInfo>();

  public TorrentTracker(@NotNull SBuildServer server, @NotNull TorrentSeeder seeder) {
    myServer = server;
    myServer.addListener(new BuildServerAdapter() {
      @Override
      public void serverStartup() {
        super.serverStartup();
        start();
      }

      @Override
      public void serverShutdown() {
        super.serverShutdown();
        stop();
      }

      @Override
      public void cleanupFinished() {
        super.cleanupFinished();
        List<Torrent> removedTorrents = new ArrayList<Torrent>();
        synchronized (myAnnouncedTorrents) {
          for (TorrentInfo ti: myAnnouncedTorrents) {
            if (ti.getSrcFile().isFile()) continue;

            FileUtil.delete(ti.getTorrentFile());
            removedTorrents.add(ti.getTorrent());
          }
        }

        for (Torrent removed: removedTorrents) {
          myTracker.remove(removed);
        }
      }
    });

    mySeeder = seeder;
  }

  public void start() {
    int freePort = NetworkUtil.getFreePort(6969);

    try {
      String rootUrl = myServer.getRootUrl();
      if (rootUrl.endsWith("/")) rootUrl = rootUrl.substring(0, rootUrl.length()-1);
      URI serverUrl = new URI(rootUrl);
      InetAddress serverAddress = InetAddress.getByName(serverUrl.getHost());
      myTracker = new Tracker(new InetSocketAddress(serverAddress, freePort));
      myTracker.start();
      LOG.info("Torrent tracker started on url: " + rootUrl + ":" + freePort);
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
      File torrentFile = new File(torrentsStore, srcFile.getName() + ".torrent");
      if (torrentFile.isFile()) {
        LOG.info("Torrent file already exists: " + torrentFile);
        return true;
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
        myAnnouncedTorrents.add(new TorrentInfo(srcFile, torrentFile, trackedTorrent));
      }

      mySeeder.seedTorrent(t, srcFile);
      LOG.info("Torrent announced in tracker: " + srcFile.getAbsolutePath());

      return true;
    } catch (Exception e) {
      LOG.warn("Failed to announce file in torrent tracker: " + e.toString());
      return false;
    }
  }

  private static class TorrentInfo implements Comparable<TorrentInfo> {
    private final TrackedTorrent myTorrent;
    private final File mySrcFile;
    private final Date myAnnounceDate;
    private final File myTorrentFile;

    private TorrentInfo(@NotNull File srcFile, @NotNull File torrentFile, @NotNull TrackedTorrent torrent) {
      mySrcFile = srcFile;
      myTorrentFile = torrentFile;
      myTorrent = torrent;
      myAnnounceDate = Dates.now();
    }

    @NotNull
    public TrackedTorrent getTorrent() {
      return myTorrent;
    }

    @NotNull
    public File getTorrentFile() {
      return myTorrentFile;
    }

    @NotNull
    public File getSrcFile() {
      return mySrcFile;
    }

    @NotNull
    public Date getAnnounceDate() {
      return myAnnounceDate;
    }

    public int compareTo(TorrentInfo o) {
      return myAnnounceDate.compareTo(o.getAnnounceDate());
    }
  }
}
