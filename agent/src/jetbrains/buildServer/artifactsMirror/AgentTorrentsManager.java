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
public class AgentTorrentsManager extends AgentLifeCycleAdapter implements ArtifactsPublisher {
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

  public AgentTorrentsManager(@NotNull BuildAgentConfiguration agentConfiguration,
                              @NotNull EventDispatcher<AgentLifeCycleListener> eventDispatcher,
                              @Nullable final ArtifactCacheProvider artifactsCacheProvider,
                              @NotNull TorrentTrackerConfiguration trackerManager) throws Exception {
    eventDispatcher.addListener(this);
    myArtifactCacheProvider = artifactsCacheProvider;
    File torrentsStorage = agentConfiguration.getCacheDirectory(TORRENT_FOLDER_NAME);
    myTrackerManager = trackerManager;
    myTorrentsDirectorySeeder = new TorrentsDirectorySeeder(torrentsStorage, -1, 0);
  }

  private boolean settingsInited() {
    return myTorrentClientStarted && myTrackerAnnounceUrl != null && myFileSizeThresholdMb != null;
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

  public int publishFiles(@NotNull Map<File, String> fileStringMap) throws ArtifactPublishingFailedException {
    return announceBuildArtifacts(fileStringMap.keySet());
  }

  private boolean announceNewFile(@NotNull File srcFile) {
    if (!settingsInited()) return true;

    try {
      if (myArtifactCacheProvider == null || !FileUtil.isAncestor(myArtifactCacheProvider.getCacheDir(), srcFile, true))
        return true;

      myTorrentsDirectorySeeder.getTorrentSeeder().stopSeedingByPath(srcFile);


      if (myTorrentsDirectorySeeder.shouldCreateTorrentFileFor(srcFile)) {
        Torrent torrent = Torrent.create(srcFile, myTrackerAnnounceUrl, "teamcity");
        myTorrentsDirectorySeeder.getTorrentSeeder().seedTorrent(torrent, srcFile);
        log2Build(String.format("Seeding torrent for %s. Hash: %s", srcFile.getAbsolutePath(), torrent.getHexInfoHash()));
      }
    } catch (Exception e) {
      log2Build("Can't start seeding: " + e.getMessage());
      return false;
    }

    return true;
  }

  private int announceBuildArtifacts(@NotNull Collection<File> artifacts) {
    int num = 0;
    for (File artifact : artifacts) {
      if (announceNewFile(artifact)) ++num;
    }
    return num;
  }

  private void log2Build(final String msg) {
    final BuildMessage1 textMessage = DefaultMessagesInfo.createTextMessage(msg);
    myBuild.getBuildLogger().logMessage(DefaultMessagesInfo.internalize(textMessage));
  }

}
