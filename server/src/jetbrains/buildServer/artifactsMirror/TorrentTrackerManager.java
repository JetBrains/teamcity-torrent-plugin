package jetbrains.buildServer.artifactsMirror;

import com.intellij.openapi.diagnostic.Logger;
import com.turn.ttorrent.tracker.PeerCollectorThread;
import com.turn.ttorrent.tracker.TrackedTorrent;
import com.turn.ttorrent.tracker.Tracker;
import com.turn.ttorrent.tracker.TrackerRequestProcessor;
import jetbrains.buildServer.NetworkUtil;
import jetbrains.buildServer.artifactsMirror.web.TrackerController;
import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.BuildServerListener;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
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
  private boolean myTrackerRunning;
  private PeerCollectorThread myCollectorThread;
  private final TorrentConfigurator myConfigurator;

  public TorrentTrackerManager(@NotNull final TorrentConfigurator configurator,
                               @NotNull final EventDispatcher<BuildServerListener> dispatcher) {
    myConfigurator = configurator;
    myTrackerService = new TrackerRequestProcessor();
    myTrackerService.setAcceptForeignTorrents(true);
    myTorrents = new ConcurrentHashMap<String, TrackedTorrent>();
    dispatcher.addListener(new BuildServerAdapter(){
      @Override
      public void serverShutdown() {
        stopTracker();
      }

      @Override
      public void serverStartup() {
        if (configurator.isTrackerEnabled()) {
          startTracker();
        }
      }
    });
    myConfigurator.addPropertyChangeListener(new PropertyChangeListener() {
      public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getNewValue() instanceof Integer){
          integerPropertyChanged(evt.getPropertyName(), (Integer) evt.getNewValue());
        } else if (evt.getNewValue() instanceof Boolean){
          booleanPropertyChanged(evt.getPropertyName(), (Boolean) evt.getNewValue());
        }
      }
    });
  }

  public void booleanPropertyChanged(@NotNull final String propertyName, boolean newValue){
    if (TorrentConfigurator.TRACKER_USES_DEDICATED_PORT.equals(propertyName)){
      condRestartTracker();
    } else if (TorrentConfigurator.TRACKER_ENABLED.equals(propertyName)){
      if (newValue){
        startTracker();
      } else {
        stopTracker();
      }
    }
  }

  public void integerPropertyChanged(@NotNull final String propertyName, int newValue){
    if (TorrentConfigurator.ANNOUNCE_INTERVAL.equals(propertyName)){
      setAnnounceInterval(newValue);
    } else if (TorrentConfigurator.TRACKER_TORRENT_EXPIRE_TIMEOUT.equals(propertyName)){
      if (myCollectorThread != null){
        myCollectorThread.setTorrentExpireTimeoutSec(newValue);
      }
    }
  }

  /**
   * restarts tracker if it's running and does nothing otherwise
   *
   */
  private void condRestartTracker(){
    boolean wasRunning = isTrackerRunning();
    if (wasRunning){
      stopTracker();
      startTracker();
    }
  }

  public void startTracker(){
    myTorrents.clear();

    // if we don't use individual port, we need nothing. Tracker's controller is already initialized.
    if (myConfigurator.getTrackerUsesDedicatedPort()){
      startIndividualPort(myConfigurator.getOwnAddress());
    }

    //setting peer collection interval to the same as announce interval
    myCollectorThread = new PeerCollectorThread(myTorrents);
    myCollectorThread.setTorrentExpireTimeoutSec(myConfigurator.getTrackerTorrentExpireTimeoutSec());
    myCollectorThread.setName("peer-collector");
    myCollectorThread.start();

    myTrackerRunning = true;

    myConfigurator.setAnnounceUrl(getAnnounceUri().toString());
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
      LOG.error("Failed to start torrent tracker, server URL is invalid: ", e);
      throw new RuntimeException(e);
    }
  }

  public void stopTracker() {
    myCollectorThread.interrupt();
    try {
      myCollectorThread.join();
    } catch (InterruptedException e) {
    }
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
    return myConfigurator.getTrackerUsesDedicatedPort();
  }

  public ConcurrentMap<String, TrackedTorrent> getTorrents() {
    return myTorrents;
  }

  public TrackerRequestProcessor getTrackerService() {
    return myTrackerService;
  }

  public void setAnnounceInterval(final int announceInterval){
    myTrackerService.setAnnounceInterval(announceInterval);
  }

  public int getConnectedClientsNum() {
    if (!myTrackerRunning){
      return 0;
    }
    Set<String> uniquePeers = new HashSet<String>();
    for (TrackedTorrent tt : myTorrents.values()) {
      uniquePeers.addAll(tt.getPeers().keySet());
    }
    return uniquePeers.size();
  }

  public int getAnnouncedTorrentsNum() {
    if (!myTrackerRunning){
      return 0;
    }
    return myTorrents.size();
  }

  public URI getAnnounceUri() {
    if (myConfigurator.getTrackerUsesDedicatedPort()){
      return myTracker.getAnnounceURI();
    } else {
      String serverUrl = myConfigurator.getServerAddress();
      if (serverUrl.endsWith("/")){
        serverUrl = serverUrl.substring(0, serverUrl.length()-1);
      }
      return URI.create(serverUrl + TrackerController.PATH);
    }
  }
}