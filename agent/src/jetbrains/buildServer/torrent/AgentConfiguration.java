package jetbrains.buildServer.torrent;

import com.turn.ttorrent.Constants;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.torrent.settings.LeechSettings;
import jetbrains.buildServer.torrent.settings.SeedSettings;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import jetbrains.buildServer.xmlrpc.XmlRpcFactory;
import jetbrains.buildServer.xmlrpc.XmlRpcTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

/**
 * User: Victory.Bedrosova
 * Date: 10/12/12
 * Time: 4:06 PM
 */
public class AgentConfiguration implements TorrentConfiguration, SeedSettings, LeechSettings {

  private final static String ANNOUNCE_URL_KEY = "teamcity.torrent.announce.url";

  @Nullable
  private volatile XmlRpcTarget myXmlRpcTarget;
  @NotNull
  private final BuildAgentConfiguration myBuildAgentConfiguration;
  @NotNull
  private final CurrentBuildTracker myCurrentBuildTracker;

  public AgentConfiguration(@NotNull final EventDispatcher<AgentLifeCycleListener> dispatcher,
                            @NotNull final BuildAgentConfiguration buildAgentConfiguration,
                            @NotNull final CurrentBuildTracker currentBuildTracker,
                            @NotNull final SSLTrustStoreProvider SSLTrustStoreProvider) {
    myCurrentBuildTracker = currentBuildTracker;
    myBuildAgentConfiguration = buildAgentConfiguration;
    dispatcher.addListener(new AgentLifeCycleAdapter() {
      @Override
      public void agentStarted(@NotNull BuildAgent agent) {
        String serverUrl = agent.getConfiguration().getServerUrl();
        if (StringUtil.isNotEmpty(serverUrl)) {
          myXmlRpcTarget = XmlRpcFactory.getInstance().create(
                  serverUrl,
                  "TeamCity Agent",
                  30000,
                  false,
                  SSLTrustStoreProvider.getTrustStore());
        } else {
          Loggers.AGENT.error("Cannot create RPC instance for torrent plugin: server url is not specified");
        }
      }
    });
  }

  @NotNull
  @Override
  public String getOwnTorrentAddress() {
    return TeamCityProperties.getProperty(OWN_ADDRESS, "");
  }

  @NotNull
  @Override
  public String getAgentAddressPrefix() {
    return TeamCityProperties.getProperty(PREFIX_ADDRESS, "172.");
  }

  @Nullable
  public String getAnnounceUrl() {

    String announceUrlLocal = myBuildAgentConfiguration.getConfigurationParameters().get(ANNOUNCE_URL_KEY);
    if (StringUtil.isNotEmpty(announceUrlLocal)) return announceUrlLocal;

    String serverUrlLocal = myBuildAgentConfiguration.getServerUrl();
    if (serverUrlLocal == null) return null;

    serverUrlLocal = StringUtil.removeTailingSlash(serverUrlLocal);

    return call("getAnnounceUrl", serverUrlLocal + "/trackerAnnounce.html");
  }

  @Override
  public long getFileSizeThresholdBytes() {
    final String fileSizeThresholdBytes = getPropertyFromBuildOrDefault(FILE_SIZE_THRESHOLD, DEFAULT_FILE_SIZE_THRESHOLD);
    try {
      return StringUtil.parseFileSize(fileSizeThresholdBytes);
    } catch (NumberFormatException e) {
      return StringUtil.parseFileSize(DEFAULT_FILE_SIZE_THRESHOLD);
    }
  }

  @Override
  public int getWorkerPoolSize() {
    return TeamCityProperties.getInteger(WORKER_POOL_SIZE, DEFAULT_WORKER_POOL_SIZE);
  }

  @Override
  public int getPieceHashingPoolSize() {
    return TeamCityProperties.getInteger(VALIDATOR_POOL_SIZE, DEFAULT_VALIDATOR_POOL_SIZE);
  }

  @Override
  public boolean isDownloadEnabled() {
    String value = getPropertyFromBuildOrDefault(LeechSettings.DOWNLOAD_ENABLED, String.valueOf(LeechSettings.DEFAULT_DOWNLOAD_ENABLED));
    return Boolean.parseBoolean(value);
  }

  @Override
  public boolean isSeedingEnabled() {
    String value = getPropertyFromBuildOrDefault(SeedSettings.SEEDING_ENABLED, String.valueOf(SeedSettings.DEFAULT_SEEDING_ENABLED));
    return Boolean.parseBoolean(value);
  }

  @Override
  public int getMaxNumberOfSeededTorrents() {
    return getFromBuildOrDefault(SeedSettings.MAX_NUMBER_OF_SEEDED_TORRENTS, 500);
  }

  @Override
  public int getMinSeedersForDownload() {
    return getFromBuildOrDefault(LeechSettings.MIN_SEEDERS_FOR_DOWNLOAD, LeechSettings.DEFAULT_MIN_SEEDERS_FOR_DOWNLOAD);
  }

  @Override
  public int getMaxPieceDownloadTime() {
    return getFromBuildOrDefault(LeechSettings.MAX_PIECE_DOWNLOAD_TIME, LeechSettings.DEFAULT_MAX_PIECE_DOWNLOAD_TIME);
  }

  @Override
  public int getSocketTimeout() {
    int defaultTimeout = (int) TimeUnit.MILLISECONDS.toSeconds(Constants.DEFAULT_SOCKET_CONNECTION_TIMEOUT_MILLIS);
    return call("getSocketTimeout", defaultTimeout);
  }

  @Override
  public int getCleanupTimeout() {
    int defaultTimeout = (int) TimeUnit.MILLISECONDS.toSeconds(Constants.DEFAULT_CLEANUP_RUN_TIMEOUT_MILLIS);
    return call("getCleanupTimeout", defaultTimeout);
  }

  @Override
  public int getMaxConnectionsCount() {
    return call("getMaxConnectionsCount", TorrentConfiguration.DEFAULT_MAX_CONNECTIONS);
  }

  private int getFromBuildOrDefault(String key, int defaultValue) {
    String value = getPropertyFromBuildOrDefault(key, String.valueOf(defaultValue));
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  @NotNull
  private String getPropertyFromBuildOrDefault(String key, String defaultValue) {
    AgentRunningBuild currentBuild;
    try {
      currentBuild = myCurrentBuildTracker.getCurrentBuild();
    } catch (NoRunningBuildException e) {
      return defaultValue;
    }
    final String value = currentBuild.getSharedConfigParameters().get(key);
    if (value == null) {
      return defaultValue;
    }
    return value;
  }

  @NotNull
  private <T> T call(@NotNull String methodName, @NotNull final T defaultValue) {
    final XmlRpcTarget xmlRpcTargetLocal = myXmlRpcTarget;
    if (xmlRpcTargetLocal == null) {
      Loggers.AGENT.warn("RPC object is not initialized");
      return defaultValue;
    }
    try {
      final Object retval = xmlRpcTargetLocal.call(XmlRpcConstants.TORRENT_CONFIGURATION + "." + methodName, new Object[0]);

      if (retval == null) {
        Loggers.AGENT.warn("method " + methodName + " cannot be invoked via RPC");
        return defaultValue;
      }

      return (T) retval;

    } catch (Exception e) {
      Loggers.AGENT.warnAndDebugDetails("method " + methodName + " cannot be invoked via RPC", e);
      return defaultValue;
    }
  }
}
