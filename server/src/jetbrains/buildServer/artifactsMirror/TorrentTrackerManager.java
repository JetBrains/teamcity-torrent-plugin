package jetbrains.buildServer.artifactsMirror;

import jetbrains.buildServer.RootUrlHolder;
import jetbrains.buildServer.artifactsMirror.torrent.TorrentTracker;
import jetbrains.buildServer.artifactsMirror.torrent.TorrentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URI;

public class TorrentTrackerManager {
  private final TorrentTracker myTorrentTracker;
  private final  RootUrlHolder myRootUrlHolder;

  public TorrentTrackerManager(@NotNull final RootUrlHolder rootUrlHolder) {
    myTorrentTracker = new TorrentTracker();
    myRootUrlHolder = rootUrlHolder;
  }

  public void startTracker() {
    stopTracker();
    myTorrentTracker.start(myRootUrlHolder.getRootUrl());
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