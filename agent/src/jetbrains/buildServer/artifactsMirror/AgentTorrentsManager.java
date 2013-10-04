package jetbrains.buildServer.artifactsMirror;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.turn.ttorrent.TorrentDefaults;
import com.turn.ttorrent.common.Torrent;
import jetbrains.buildServer.NetworkUtil;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.agent.impl.cache.AgentArtifactCacheProviderImpl;
import jetbrains.buildServer.artifacts.ArtifactCacheProvider;
import jetbrains.buildServer.artifactsMirror.seeder.TorrentsDirectorySeeder;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.messages.BuildMessage1;
import jetbrains.buildServer.messages.DefaultMessagesInfo;
import jetbrains.buildServer.util.EventDispatcher;
import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.util.*;

/**
 * User: Victory.Bedrosova
 * Date: 10/9/12
 * Time: 5:12 PM
 */
public class AgentTorrentsManager extends AgentLifeCycleAdapter {
  private final static Logger LOG = Logger.getInstance(AgentTorrentsManager.class.getName());

  private static final String TORRENT_FOLDER_NAME = "torrents";

  @NotNull
  private final TorrentTrackerConfiguration myTrackerManager;
  private volatile URI myTrackerAnnounceUrl;
  private volatile Integer myFileSizeThresholdMb = TorrentDefaults.FILESIZE_THRESHOLD_MB;
  private volatile Integer myAnnounceIntervalSec = TorrentDefaults.ANNOUNCE_INTERVAL_SEC;
  private TorrentsDirectorySeeder myTorrentsDirectorySeeder;
  private AgentRunningBuild myBuild;
  private boolean myTorrentClientStarted = false;
  @Nullable
  private final ArtifactCacheProvider myArtifactCacheProvider;

  public AgentTorrentsManager(@NotNull final BuildAgentConfiguration agentConfiguration,
                              @NotNull final EventDispatcher<AgentLifeCycleListener> eventDispatcher,
                              @Nullable final ArtifactCacheProvider artifactsCacheProvider,
                              @NotNull final CurrentBuildTracker currentBuildTracker,
                              @NotNull final TorrentTrackerConfiguration trackerManager) throws Exception {
    eventDispatcher.addListener(this);
    File torrentsStorage = agentConfiguration.getCacheDirectory(TORRENT_FOLDER_NAME);
    myTrackerManager = trackerManager;
    myTorrentsDirectorySeeder = new TorrentsDirectorySeeder(torrentsStorage, -1, 0);
    myArtifactCacheProvider = artifactsCacheProvider;
    if (artifactsCacheProvider != null){
      artifactsCacheProvider.addListener(new TorrentArtifactCacheListener(myTorrentsDirectorySeeder, currentBuildTracker, trackerManager));
    }
  }

  private boolean updateSettings() {
    try {
      String announceUrl = myTrackerManager.getAnnounceUrl();
      if (announceUrl == null) return false;
      myTrackerAnnounceUrl = new URI(announceUrl);
      myFileSizeThresholdMb = myTrackerManager.getFileSizeThresholdMb();
      myTorrentsDirectorySeeder.setFileSizeThresholdMb(myFileSizeThresholdMb);
      myAnnounceIntervalSec = myTrackerManager.getAnnounceIntervalSec();
      myTorrentsDirectorySeeder.setAnnounceInterval(myAnnounceIntervalSec);
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
    if (myTorrentClientStarted){
      updateSettings();
      return;
    }
    try {
      if (updateSettings()) {
        myTorrentsDirectorySeeder.start(NetworkUtil.getSelfAddresses(), myTrackerAnnounceUrl, myAnnounceIntervalSec);
        myTorrentClientStarted = true;
      }
    } catch (Exception e) {
      Loggers.AGENT.warn("Failed to start torrent seeder", e);
    }
  }

  @Override
  public void buildStarted(@NotNull AgentRunningBuild runningBuild) {
    checkReady();
    myBuild = runningBuild;
  }

  @Override
  public void agentShutdown() {
    if (!myTorrentsDirectorySeeder.isStopped()) {
      myTorrentsDirectorySeeder.stop();
    }
  }

  public boolean isTorrentClientStarted() {
    return myTorrentClientStarted;
  }

  public TorrentsDirectorySeeder getTorrentsDirectorySeeder() {
    return myTorrentsDirectorySeeder;
  }
}
