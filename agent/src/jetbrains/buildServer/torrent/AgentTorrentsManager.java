package jetbrains.buildServer.torrent;

import com.intellij.openapi.diagnostic.Logger;
import com.turn.ttorrent.TorrentDefaults;
import jetbrains.buildServer.NetworkUtil;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.artifacts.ArtifactCacheProvider;
import jetbrains.buildServer.torrent.seeder.TorrentsSeeder;
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;

/**
 * User: Victory.Bedrosova
 * Date: 10/9/12
 * Time: 5:12 PM
 */
public class AgentTorrentsManager extends AgentLifeCycleAdapter {
  private final static Logger LOG = Logger.getInstance(AgentTorrentsManager.class.getName());

  @NotNull
  private final TorrentConfiguration myTrackerManager;
  private volatile URI myTrackerAnnounceUrl;
  private volatile Integer myAnnounceIntervalSec = TorrentDefaults.ANNOUNCE_INTERVAL_SEC;
  private boolean myTorrentEnabled = false;
  private TorrentsSeeder myTorrentsSeeder;

  public AgentTorrentsManager(@NotNull final EventDispatcher<AgentLifeCycleListener> eventDispatcher,
                              @NotNull final ArtifactCacheProvider artifactsCacheProvider,
                              @NotNull final CurrentBuildTracker currentBuildTracker,
                              @NotNull final TorrentConfiguration trackerManager,
                              @NotNull final AgentTorrentsSeeder torrentsSeeder,
                              @NotNull final TorrentFilesFactory torrentFilesFactory) throws Exception {
    eventDispatcher.addListener(this);
    myTrackerManager = trackerManager;
    myTorrentsSeeder = torrentsSeeder;
    artifactsCacheProvider.addListener(new TorrentArtifactCacheListener(torrentsSeeder, currentBuildTracker, trackerManager, this, torrentFilesFactory));
  }

  private boolean updateSettings() {
    try {
      String announceUrl = myTrackerManager.getAnnounceUrl();
      if (announceUrl == null) return false;
      myTrackerAnnounceUrl = new URI(announceUrl);
      myAnnounceIntervalSec = myTrackerManager.getAnnounceIntervalSec();
      myTorrentsSeeder.setAnnounceInterval(myAnnounceIntervalSec);
      boolean enabled = myTrackerManager.isTorrentEnabled();
      myTorrentEnabled = enabled;
      if (enabled){
        startSeeder();
      } else {
        stopSeeder();
      }
    } catch (Exception e) {
      LOG.warn("Error updating torrent settings", e);
      return false;
    }
    return true;
  }

  @Override
  public void agentStarted(@NotNull BuildAgent agent) {
    checkReady();
  }

  public void checkReady(){
    updateSettings();
  }

  @Override
  public void buildStarted(@NotNull AgentRunningBuild runningBuild) {
    checkReady();
  }

  private void startSeeder() throws IOException {
    myTorrentsSeeder.start(NetworkUtil.getSelfAddresses(null), myTrackerAnnounceUrl, myAnnounceIntervalSec);
  }

  private void stopSeeder(){
    myTorrentsSeeder.stop();
  }

  @Override
  public void agentShutdown() {
    myTorrentsSeeder.dispose();
  }

  @NotNull
  public TorrentsSeeder getTorrentsSeeder() {
    return myTorrentsSeeder;
  }

  public boolean isTorrentEnabled() {
    return myTorrentEnabled;
  }
}
