package jetbrains.buildServer.torrent;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import jetbrains.buildServer.NetworkUtil;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.agent.impl.artifacts.ArtifactsWatcherEx;
import jetbrains.buildServer.artifacts.ArtifactCacheProvider;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.messages.serviceMessages.BuildStatisticValue;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.torrent.seeder.TorrentsSeeder;
import jetbrains.buildServer.torrent.settings.LeechSettings;
import jetbrains.buildServer.torrent.settings.SeedSettings;
import jetbrains.buildServer.torrent.torrent.TeamcityTorrentClient;
import jetbrains.buildServer.torrent.util.TorrentsDownloadStatistic;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.filters.Filter;
import org.apache.log4j.LogManager;
import org.apache.log4j.spi.LoggerFactory;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;

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
  private boolean myTransportEnabled = false;
  @NotNull
  private final AgentTorrentsSeeder myTorrentsSeeder;
  private final LeechSettings myLeechSettings;
  private final SeedSettings mySeedingSettings;
  @NotNull
  private final ArtifactsWatcherEx myArtifactsWatcher;
  @NotNull
  private final Collection<String> torrensForPublishing;

  public AgentTorrentsManager(@NotNull final EventDispatcher<AgentLifeCycleListener> eventDispatcher,
                              @NotNull final ArtifactCacheProvider artifactsCacheProvider,
                              @NotNull final CurrentBuildTracker currentBuildTracker,
                              @NotNull final TorrentConfiguration trackerManager,
                              @NotNull final AgentTorrentsSeeder torrentsSeeder,
                              @NotNull final TorrentFilesFactoryImpl torrentFilesFactory,
                              @NotNull final ArtifactsWatcherEx artifactsWatcher,
                              @NotNull final TorrentsDownloadStatistic torrentsDownloadStatistic,
                              @NotNull final LeechSettings leechSettings,
                              @NotNull final BuildAgentConfiguration agentConfiguration,
                              @NotNull final SeedSettings seedingSettings) throws Exception {
    myLeechSettings = leechSettings;
    mySeedingSettings = seedingSettings;
    eventDispatcher.addListener(this);
    myTrackerManager = trackerManager;
    myTorrentsSeeder = torrentsSeeder;
    torrensForPublishing = new ArrayList<String>();
    myArtifactsWatcher = artifactsWatcher;
    myTorrentsDownloadStatistic = torrentsDownloadStatistic;
    artifactsCacheProvider.addListener(new TorrentArtifactCacheListener(
            torrentsSeeder.getTorrentsSeeder(),
            currentBuildTracker,
            trackerManager,
            this,
            torrentFilesFactory,
            agentConfiguration));
  }

  private boolean updateSettings() {
    try {

      final int defaultSize = SystemInfo.isWindows ? TorrentConfiguration.DEFAULT_BUFFER_SIZE_WINDOWS : -1;
      final TeamcityTorrentClient client = myTorrentsSeeder.getClient();
      client.setSendBufferSize(TeamCityProperties.getInteger(TorrentConfiguration.SEND_BUFFER_SIZE, defaultSize));
      client.setReceiveBufferSize(TeamCityProperties.getInteger(TorrentConfiguration.RECEIVE_BUFFER_SIZE, defaultSize));

      String announceUrl = myTrackerManager.getAnnounceUrl();
      if (announceUrl == null) return false;
      myTrackerAnnounceUrl = new URI(announceUrl);
      myTorrentsSeeder.setSocketTimeout(myTrackerManager.getSocketTimeout());
      myTorrentsSeeder.setCleanupTimeout(myTrackerManager.getCleanupTimeout());
      myTorrentsSeeder.setAnnounceInterval(myAnnounceIntervalSec);
      final int maxConnectionsCount = myTrackerManager.getMaxConnectionsCount();
      myTorrentsSeeder.setMaxIncomingConnectionsCount(maxConnectionsCount);
      myTorrentsSeeder.setMaxOutgoingConnectionsCount(maxConnectionsCount);
      myTransportEnabled = myLeechSettings.isDownloadEnabled();

      boolean needEnableTorrent = (mySeedingSettings.isSeedingEnabled() || myTransportEnabled);
      if (needEnableTorrent) {
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
    try {
      startSeeder();
    } catch (IOException e) {
      LOG.warnAndDebugDetails("error start seeder on agent started", e);
    }

    LogManager.getLogger("jetbrains.torrent", new LoggerFactory() {
      @Override
      public org.apache.log4j.Logger makeNewLoggerInstance(String s) {
        return org.apache.log4j.Logger.getLogger(Loggers.AGENT_CATEGORY);
      }
    });
  }

  public void checkReady() {
    updateSettings();
  }

  @Override
  public void afterAtrifactsPublished(@NotNull AgentRunningBuild runningBuild, @NotNull BuildFinishedStatus status) {
    final String torrentPaths;
    synchronized (torrensForPublishing) {
      torrentPaths = StringUtil.join(",", torrensForPublishing);
      torrensForPublishing.clear();
    }
    myArtifactsWatcher.addInternalArtifactsPath(torrentPaths);
  }

  @Override
  public void buildStarted(@NotNull AgentRunningBuild runningBuild) {
    checkReady();
    synchronized (torrensForPublishing) {
      //collection must be empty on start build
      if (!torrensForPublishing.isEmpty()) {
        LOG.info("collection of torrents for publishing is not empty: " + torrensForPublishing + ". Collection is clearing");
        torrensForPublishing.clear();
      }
    }
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
    final URI trackerAnnounceUrlLocal = myTrackerAnnounceUrl;
    if (trackerAnnounceUrlLocal == null) {
      Loggers.AGENT.error("Announce url is null. Seeding is disabled");
      return;
    }
    String ownAddress = myTrackerManager.getOwnTorrentAddress();
    final String agentAddressPrefix = myTrackerManager.getAgentAddressPrefix();
    final InetAddress[] selfAddresses;
    if (!ownAddress.isEmpty()) {
      selfAddresses = new InetAddress[]{InetAddress.getByName(ownAddress)};
    } else if (!agentAddressPrefix.isEmpty()) {
      selfAddresses = NetworkUtil.getSelfAddresses(new Filter<InetAddress>() {
        @Override
        public boolean accept(@NotNull InetAddress data) {
          return data.getHostAddress().startsWith(agentAddressPrefix);
        }
      });
    } else {
      selfAddresses = NetworkUtil.getSelfAddresses(null);
    }
    myTorrentsSeeder.start(selfAddresses, trackerAnnounceUrlLocal, myAnnounceIntervalSec);
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
    return myTorrentsSeeder.getTorrentsSeeder();
  }

  @Override public void beforeBuildFinish(@NotNull AgentRunningBuild build, @NotNull BuildFinishedStatus buildStatus) {
    final BuildProgressLogger logger = build.getBuildLogger();

    logger.message(new BuildStatisticValue(TorrentsDownloadStatistic.FAIL_DOWNLOAD_KEY,
            myTorrentsDownloadStatistic.getFailedDownloadCount()).asString());
    logger.message(new BuildStatisticValue(TorrentsDownloadStatistic.SUCCESS_DOWNLOAD_KEY,
            myTorrentsDownloadStatistic.getSuccessfulDownloadCount()).asString());
  }

  @NotNull
  public TorrentsDownloadStatistic getTorrentsDownloadStatistic() {
    return myTorrentsDownloadStatistic;
  }

  public boolean isTransportEnabled() {
    return myTransportEnabled;
  }

  void saveTorrentForPublish(@NotNull final String torrentFilePath) {
    synchronized (torrensForPublishing) {
      torrensForPublishing.add(torrentFilePath);
    }
  }
}
