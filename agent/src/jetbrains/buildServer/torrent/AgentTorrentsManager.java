package jetbrains.buildServer.torrent;

import com.intellij.openapi.diagnostic.Logger;
import com.turn.ttorrent.TorrentDefaults;
import jetbrains.buildServer.NetworkUtil;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.artifacts.ArtifactCacheProvider;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.torrent.seeder.ParentDirConverter;
import jetbrains.buildServer.torrent.seeder.TorrentsDirectorySeeder;
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.*;

/**
 * User: Victory.Bedrosova
 * Date: 10/9/12
 * Time: 5:12 PM
 */
public class AgentTorrentsManager extends AgentLifeCycleAdapter {
  private final static Logger LOG = Logger.getInstance(AgentTorrentsManager.class.getName());

  public static final String TORRENT_FOLDER_NAME = "torrents";

  @NotNull
  private final TorrentConfiguration myTrackerManager;
  private volatile URI myTrackerAnnounceUrl;
  private volatile Integer myAnnounceIntervalSec = TorrentDefaults.ANNOUNCE_INTERVAL_SEC;
  private boolean myTorrentEnabled = false;
  private TorrentsDirectorySeeder myTorrentsDirectorySeeder;

  public AgentTorrentsManager(@NotNull final BuildAgentConfiguration agentConfiguration,
                              @NotNull final EventDispatcher<AgentLifeCycleListener> eventDispatcher,
                              @Nullable final ArtifactCacheProvider artifactsCacheProvider,
                              @NotNull final CurrentBuildTracker currentBuildTracker,
                              @NotNull final TorrentConfiguration trackerManager) throws Exception {
    eventDispatcher.addListener(this);
    File torrentsStorage = agentConfiguration.getCacheDirectory(TORRENT_FOLDER_NAME);
    myTrackerManager = trackerManager;
    myTorrentsDirectorySeeder = new TorrentsDirectorySeeder(torrentsStorage, TeamCityProperties.getInteger("teamcity.torrents.agent.maxSeededTorrents", 5000), new ParentDirConverter() {
      @NotNull
      @Override
      public File getParentDir() {
        return agentConfiguration.getSystemDirectory();
      }
    });
    if (artifactsCacheProvider != null){
      artifactsCacheProvider.addListener(new TorrentArtifactCacheListener(myTorrentsDirectorySeeder, currentBuildTracker, trackerManager, this, agentConfiguration.getTempDirectory()));
    }
  }

  private boolean updateSettings() {
    try {
      String announceUrl = myTrackerManager.getAnnounceUrl();
      if (announceUrl == null) return false;
      myTrackerAnnounceUrl = new URI(announceUrl);
      myAnnounceIntervalSec = myTrackerManager.getAnnounceIntervalSec();
      myTorrentsDirectorySeeder.setAnnounceInterval(myAnnounceIntervalSec);
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
    myTorrentsDirectorySeeder.start(NetworkUtil.getSelfAddresses(), myTrackerAnnounceUrl, myAnnounceIntervalSec);
  }

  private void stopSeeder(){
    myTorrentsDirectorySeeder.stop();
  }

  @Override
  public void agentShutdown() {
    myTorrentsDirectorySeeder.dispose();
  }

  public TorrentsDirectorySeeder getTorrentsDirectorySeeder() {
    return myTorrentsDirectorySeeder;
  }

  public boolean isTorrentEnabled() {
    return myTorrentEnabled;
  }
}
