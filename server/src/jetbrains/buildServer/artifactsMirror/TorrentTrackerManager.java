package jetbrains.buildServer.artifactsMirror;

import com.intellij.openapi.diagnostic.Logger;
import com.turn.ttorrent.tracker.PeerCollectorThread;
import com.turn.ttorrent.tracker.TrackedTorrent;
import com.turn.ttorrent.tracker.Tracker;
import com.turn.ttorrent.tracker.TrackerRequestProcessor;
import jetbrains.buildServer.NetworkUtil;
import jetbrains.buildServer.artifactsMirror.web.TrackerController;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class TorrentTrackerManager {

  private final static Logger LOG = Logger.getInstance(TorrentTrackerManager.class.getName());

  private final TrackerRequestProcessor myTrackerService;
  private final ConcurrentMap<String, TrackedTorrent> myTorrents;
  private Tracker myTracker;
  private boolean myTrackerUsesDedicatedPort;
  private boolean myTrackerRunning;
  private String myOwnAddress;
  private PeerCollectorThread myCollectorThread;
//  private TorrentConfigurator myConfigurator;

  public TorrentTrackerManager() {
    myTrackerService = new TrackerRequestProcessor();
    myTrackerService.setAcceptForeignTorrents(true);
    myTorrents = new ConcurrentHashMap<String, TrackedTorrent>();
  }


  public void startTracker(final boolean useDedicatePort, final String ownAddress, final int torrentExpireTimeout){
    stopTracker();
    myTorrents.clear();
    myTrackerUsesDedicatedPort = useDedicatePort;
    myOwnAddress = ownAddress;
    if (myTrackerUsesDedicatedPort){
      startIndividualPort(myOwnAddress);
    }

    //setting peer collection interval to the same as announce interval
    myCollectorThread = new PeerCollectorThread(myTorrents);
    myCollectorThread.setTorrentExpireTimeoutSec(torrentExpireTimeout);
    myCollectorThread.setName("peer-collector");
    myCollectorThread.start();

    myTrackerRunning = true;
  }

  private void startIndividualPort(@NotNull String trackerAddress) {
    int freePort = NetworkUtil.getFreePort(6969);

    try {
      String announceAddress = String.format("http://%s:%d/announce", trackerAddress, freePort);
      myTracker = new Tracker(freePort, announceAddress, myTrackerService, myTorrents);
      myTracker.setAcceptForeignTorrents(true);
      myTracker.start(false);
      LOG.info("Torrent tracker started on url: " + myTracker.getAnnounceUrl());
    } catch (Exception e) {
      LOG.error("Failed to start torrent tracker, server URL is invalid: " + e.toString());
    }
  }

  public void stopTracker() {
    myTrackerRunning = false;
    if (myTracker != null) {
      LOG.info("Stopping torrent tracker");
      myTracker.stop();
    }
  }

  public boolean isTrackerRunning(){
    return myTrackerRunning;
  }

  public boolean isTrackerUsesDedicatedPort() {
    return myTrackerUsesDedicatedPort;
  }

  public ConcurrentMap<String, TrackedTorrent> getTorrents() {
    return myTorrents;
  }

  public TrackerRequestProcessor getTrackerService() {
    return myTrackerService;
  }

  public void setTorrentExpireTimeout(final int torrentExpireTimeout){
    myCollectorThread.setTorrentExpireTimeoutSec(torrentExpireTimeout);
  }

  public void setAnnounceInterval(final int announceInterval){
    myTrackerService.setAnnounceInterval(announceInterval);
  }

  public int getConnectedClientsNum() {
    Set<String> uniquePeers = new HashSet<String>();
    for (TrackedTorrent tt : myTorrents.values()) {
      uniquePeers.addAll(tt.getPeers().keySet());
    }
    return uniquePeers.size();
  }

  public int getAnnouncedTorrentsNum() {
    return myTorrents.size();
  }

  public URI getAnnounceUri() {
    if (myTrackerUsesDedicatedPort){
      return myTracker.getAnnounceURI();
    } else {
      String serverUrl = myOwnAddress;
      if (serverUrl.endsWith("/")){
        serverUrl = serverUrl.substring(0, serverUrl.length()-1);
      }
      return URI.create(serverUrl + TrackerController.PATH);
    }
  }
}