package jetbrains.buildServer.artifactsMirror;

import com.intellij.openapi.diagnostic.Logger;
import com.turn.ttorrent.common.Torrent;
import jetbrains.buildServer.NetworkUtil;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.artifactsMirror.seeder.FileLink;
import jetbrains.buildServer.artifactsMirror.seeder.TorrentsDirectorySeeder;
import jetbrains.buildServer.artifactsMirror.torrent.TorrentUtil;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.security.NoSuchAlgorithmException;
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
  private volatile Integer myFileSizeThresholdMb;
  private TorrentsDirectorySeeder myTorrentsDirectorySeeder;
  private AgentRunningBuild myBuild;

  public AgentTorrentsManager(@NotNull BuildAgentConfiguration agentConfiguration,
                              @NotNull EventDispatcher<AgentLifeCycleListener> eventDispatcher,
                              @NotNull TorrentTrackerConfiguration trackerManager) throws Exception {
    eventDispatcher.addListener(this);

    File torrentsStorage = agentConfiguration.getCacheDirectory(TORRENT_FOLDER_NAME);
    myTrackerManager = trackerManager;
    myTorrentsDirectorySeeder = new TorrentsDirectorySeeder(torrentsStorage, -1, 0);
  }

  private boolean settingsInited() {
    return myTrackerAnnounceUrl != null && myFileSizeThresholdMb != null;
  }

  private boolean initSettings() {
    try {
      String announceUrl = myTrackerManager.getAnnounceUrl();
      if (announceUrl == null) return false;
      myTrackerAnnounceUrl = new URI(announceUrl);
    } catch (URISyntaxException e) {
      LOG.warn(e.toString(), e);
      return false;
    }
    myFileSizeThresholdMb = myTrackerManager.getFileSizeThresholdMb();
    myTorrentsDirectorySeeder.setFileSizeThresholdMb(myFileSizeThresholdMb);
    return true;
  }

  @Override
  public void agentStarted(@NotNull BuildAgent agent) {
    try {
      initSettings();
      final String url = myTrackerManager.getAnnounceUrl();
      final URI defaultTrackerURI = url == null ? null : URI.create(url);
      myTorrentsDirectorySeeder.start(NetworkUtil.getSelfAddresses(), defaultTrackerURI, myTrackerManager.getAnnounceIntervalSec());
    } catch (SocketException e) {
      Loggers.AGENT.error("Failed to start torrent seeder, error: " + e.toString());
    }
  }

  @Override
  public void buildStarted(@NotNull AgentRunningBuild runningBuild) {
    initSettings();
    myBuild = runningBuild;
  }

  @Override
  public void agentShutdown() {
    if (!myTorrentsDirectorySeeder.isStopped()) {
      myTorrentsDirectorySeeder.stop();
    }
  }

  private boolean announceNewFile(@NotNull File srcFile) {
    if (!settingsInited()) return false;

    myTorrentsDirectorySeeder.getTorrentSeeder().stopSeedingByPath(srcFile);

    if (myTorrentsDirectorySeeder.shouldCreateTorrentFileFor(srcFile)) {
      LOG.info("Will create torrent for " + srcFile.getAbsolutePath());
      File linkDir;
      if (srcFile.getAbsolutePath().startsWith(myBuild.getCheckoutDirectory().getAbsolutePath())) {
        String relPath = FileUtil.getRelativePath(myBuild.getCheckoutDirectory(), srcFile);
        linkDir = new File(myTorrentsDirectorySeeder.getStorageDirectory(), myBuild.getBuildTypeId() + File.separator + relPath).getParentFile();
      } else {
        linkDir = new File(myTorrentsDirectorySeeder.getStorageDirectory(), myBuild.getBuildTypeId());
      }
      linkDir.mkdirs();
      if (!linkDir.isDirectory()) return false;
      try {
        Torrent torrent = Torrent.create(srcFile, myTrackerAnnounceUrl, "teamcity");
        LOG.info("created torrent with hash " + torrent.getHexInfoHash());
        myTorrentsDirectorySeeder.getTorrentSeeder().seedTorrent(torrent, srcFile);
        LOG.info("Seeding " + srcFile.getAbsolutePath());
      } catch (Exception e) {
        LOG.warn("Can't start seeding", e);
        return false;
      }
    }

    return true;
  }


  public int publishFiles(@NotNull Map<File, String> fileStringMap) throws ArtifactPublishingFailedException {
    // update filesize threshold
    myTorrentsDirectorySeeder.setFileSizeThresholdMb(myTrackerManager.getFileSizeThresholdMb());
    myTorrentsDirectorySeeder.setAnnounceInterval(myTrackerManager.getAnnounceIntervalSec());
    final String announceUrl = myTrackerManager.getAnnounceUrl();
    if (announceUrl != null) {
      myTrackerAnnounceUrl = URI.create(announceUrl);
    }
    return announceBuildArtifacts(fileStringMap.keySet());
  }

  private int announceBuildArtifacts(@NotNull Collection<File> artifacts) {
    int num = 0;
    for (File artifact : artifacts) {
      if (announceNewFile(artifact)) ++num;
    }
    return num;
  }

  public TorrentsDirectorySeeder getTorrentsDirectorySeeder() {
    return myTorrentsDirectorySeeder;
  }
}
