package jetbrains.buildServer.artifactsMirror.torrent;

import com.intellij.openapi.diagnostic.Logger;
import com.turn.ttorrent.tracker.TrackedTorrent;
import com.turn.ttorrent.tracker.Tracker;
import jetbrains.buildServer.NetworkUtil;
import jetbrains.buildServer.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.*;
import java.util.Collection;

public class TorrentTracker {
  private final static Logger LOG = Logger.getInstance(TorrentTracker.class.getName());

  private Tracker myTracker;

  public TorrentTracker() {
  }

  public void start(@NotNull String trackerAddress) {
    int freePort = NetworkUtil.getFreePort(6969);

    try {
      String announceAddress = String.format("http://%s:%d/announce", trackerAddress, freePort);
      myTracker = new Tracker(freePort, announceAddress);
      myTracker.setAcceptForeignTorrents(true);
      myTracker.start();
      LOG.info("Torrent tracker started on url: " + myTracker.getAnnounceUrl());
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

  @Nullable
  public URI getAnnounceURI() {
    return myTracker == null ? null : myTracker.getAnnounceURI();
  }

  @NotNull
  public Collection<TrackedTorrent> getTrackedTorrents() {
    return myTracker.getTrackedTorrents();
  }

  public void removeTrackedTorrent(@NotNull TrackedTorrent torrent) {
    myTracker.remove(torrent.getInfoHash());
  }
}
