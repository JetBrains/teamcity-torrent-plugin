package jetbrains.buildServer.torrent;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.NetworkUtil;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.agent.artifacts.ArtifactsWatcher;
import jetbrains.buildServer.artifacts.ArtifactCacheProvider;
import jetbrains.buildServer.messages.serviceMessages.BuildStatisticValue;
import jetbrains.buildServer.torrent.seeder.TorrentsSeeder;
import jetbrains.buildServer.torrent.util.TorrentsDownloadStatistic;
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import java.io.File;
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
  @NotNull
  private final TorrentsDownloadStatistic myTorrentsDownloadStatistic;
  private volatile URI myTrackerAnnounceUrl;
  private volatile Integer myAnnounceIntervalSec = com.turn.ttorrent.Constants.DEFAULT_ANNOUNCE_INTERVAL_SEC;
  private boolean myTorrentEnabled = false;
  private TorrentsSeeder myTorrentsSeeder;

  public AgentTorrentsManager(@NotNull final EventDispatcher<AgentLifeCycleListener> eventDispatcher,
                              @NotNull final ArtifactCacheProvider artifactsCacheProvider,
                              @NotNull final CurrentBuildTracker currentBuildTracker,
                              @NotNull final TorrentConfiguration trackerManager,
                              @NotNull final AgentTorrentsSeeder torrentsSeeder,
                              @NotNull final TorrentFilesFactory torrentFilesFactory,
                              @NotNull final ArtifactsWatcher artifactsWatcher,
                              @NotNull final TorrentsDownloadStatistic torrentsDownloadStatistic) throws Exception {
    eventDispatcher.addListener(this);
    myTrackerManager = trackerManager;
    myTorrentsSeeder = torrentsSeeder;
    myTorrentsDownloadStatistic = torrentsDownloadStatistic;
    artifactsCacheProvider.addListener(new TorrentArtifactCacheListener(torrentsSeeder, currentBuildTracker, trackerManager, this, torrentFilesFactory, artifactsWatcher));
  }

  private boolean updateSettings() {
    try {
      String announceUrl = myTrackerManager.getAnnounceUrl();
      if (announceUrl == null) return false;
      myTrackerAnnounceUrl = new URI(announceUrl);
      myAnnounceIntervalSec = myTrackerManager.getAnnounceIntervalSec();
      myTorrentsSeeder.setSocketTimeout(myTrackerManager.getSocketTimeout());
      myTorrentsSeeder.setCleanupTimeout(myTrackerManager.getCleanupTimeout());
      myTorrentsSeeder.setAnnounceInterval(myAnnounceIntervalSec);
      final int maxConnectionsCount = myTrackerManager.getMaxConnectionsCount();
      myTorrentsSeeder.setMaxIncomingConnectionsCount(maxConnectionsCount);
      myTorrentsSeeder.setMaxOutgoingConnectionsCount(maxConnectionsCount);
      boolean enabled = myTrackerManager.isTorrentEnabled();
      myTorrentEnabled = enabled;
      if (enabled) {
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

  public void checkReady() {
    updateSettings();
  }

  @Override
  public void buildStarted(@NotNull AgentRunningBuild runningBuild) {
    checkReady();
    myTorrentsDownloadStatistic.reset();
    checkThatTempTorrentDirectoryNotExist(runningBuild.getBuildTempDirectory());
  }

  private void checkThatTempTorrentDirectoryNotExist(File buildTempDirectory) {
    File torrentsTempDirectory = new File(buildTempDirectory, Constants.TORRENT_FILE_COPIES_DIR);
    if (torrentsTempDirectory.exists()) {
      LOG.info("on start build exist temp torrent directory for torrent files, but it should have been removed at finish previous build");
    }
  }

  private void startSeeder() throws IOException {
    myTorrentsSeeder.start(NetworkUtil.getSelfAddresses(null), myTrackerAnnounceUrl, myAnnounceIntervalSec);
  }

  private void stopSeeder() {
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

  @Override public void beforeBuildFinish(@NotNull AgentRunningBuild build, @NotNull BuildFinishedStatus buildStatus) {
    final BuildProgressLogger logger = build.getBuildLogger();

    logger.message(new BuildStatisticValue(TorrentsDownloadStatistic.FAIL_DOWNLOAD_KEY,
            myTorrentsDownloadStatistic.getFailedDownloadCount()).asString());
    logger.message(new BuildStatisticValue(TorrentsDownloadStatistic.SUCCESS_DOWNLOAD_KEY,
            myTorrentsDownloadStatistic.getSuccessfulDownloadCount()).asString());
    logger.message(new BuildStatisticValue(TorrentsDownloadStatistic.AVERAGE_SPEED_KEY,
            myTorrentsDownloadStatistic.getAverageSpeedKbS()).asString());
  }

  @NotNull
  public TorrentsDownloadStatistic getTorrentsDownloadStatistic() {
    return myTorrentsDownloadStatistic;
  }

  public boolean isTorrentEnabled() {
    return myTorrentEnabled;
  }
}
