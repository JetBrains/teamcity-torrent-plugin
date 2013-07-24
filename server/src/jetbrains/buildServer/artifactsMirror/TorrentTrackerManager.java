package jetbrains.buildServer.artifactsMirror;

import jetbrains.buildServer.artifactsMirror.torrent.TorrentTracker;
import jetbrains.buildServer.artifactsMirror.torrent.TorrentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URI;

public class TorrentTrackerManager {
  private final TorrentTracker myTorrentTracker;

  public TorrentTrackerManager() {
    myTorrentTracker = new TorrentTracker();
  }

  public void startTracker(@NotNull String trackerAddress) {
    stopTracker();
    myTorrentTracker.start(trackerAddress);
  }

  public void stopTracker() {
    myTorrentTracker.stop();
  }

  @NotNull
  public File createTorrent(@NotNull File srcFile, @NotNull File torrentsStore) {
    return TorrentUtil.getOrCreateTorrent(srcFile, torrentsStore, myTorrentTracker.getAnnounceURI());
  }

  public int getConnectedClientsNum() {
    return 0;
  }

  public int getAnnouncedTorrentsNum() {
    return myTorrentTracker.getTrackedTorrents().size();
  }

  @Nullable
  public String getAnnounceUrl() {
    final URI uri = myTorrentTracker.getAnnounceURI();
    return uri == null ? null : uri.toString();
  }
}