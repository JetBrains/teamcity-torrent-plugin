package jetbrains.buildServer.torrent;

import com.turn.ttorrent.Constants;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.xmlrpc.XmlRpcFactory;
import jetbrains.buildServer.xmlrpc.XmlRpcTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * User: Victory.Bedrosova
 * Date: 10/12/12
 * Time: 4:06 PM
 */
public class TorrentManagerProxy implements TorrentConfiguration {
  private XmlRpcTarget myXmlRpcTarget;
  @NotNull
  final private BuildAgentConfiguration myBuildAgentConfiguration;
  @NotNull
  private final CurrentBuildTracker myCurrentBuildTracker;

  public TorrentManagerProxy(@NotNull final EventDispatcher<AgentLifeCycleListener> dispatcher,
                             @NotNull final BuildAgentConfiguration buildAgentConfiguration,
                             @NotNull CurrentBuildTracker currentBuildTracker) {
    this.myBuildAgentConfiguration = buildAgentConfiguration;
    myCurrentBuildTracker = currentBuildTracker;
    dispatcher.addListener(new AgentLifeCycleAdapter() {
      @Override
      public void afterAgentConfigurationLoaded(@NotNull BuildAgent agent) {
        if (StringUtil.isNotEmpty(agent.getConfiguration().getServerUrl())) {
          myXmlRpcTarget = XmlRpcFactory.getInstance().create(agent.getConfiguration().getServerUrl(), "TeamCity Agent", 30000, false);
        }
      }
    });
  }

  @Nullable
  public String getAnnounceUrl() {
    return call("getAnnounceUrl", "http://localhost:8111/trackerAnnounce.html");
  }

  public long getFileSizeThresholdBytes() {
    final String fileSizeThresholdBytes = call("getFileSizeThresholdBytes", TorrentConfiguration.DEFAULT_FILE_SIZE_THRESHOLD);
    return StringUtil.parseFileSize(fileSizeThresholdBytes);
  }

  @Override
  public int getMinSeedersForDownload() {
    return call("getMinSeedersForDownload", TorrentConfiguration.DEFAULT_MIN_SEEDERS_FOR_DOWNLOAD);
  }

  @Override
  public int getMaxPieceDownloadTime() {
    return call("getMaxPieceDownloadTime", TorrentConfiguration.DEFAULT_MAX_PIECE_DOWNLOAD_TIME);
  }

  public int getAnnounceIntervalSec() {
    return call("getAnnounceIntervalSec", TorrentConfiguration.DEFAULT_ANNOUNCE_INTERVAL);
  }

  @Override
  public boolean isTransportEnabled() {
    try {
      final Map<String, String> sharedConfigParameters = myCurrentBuildTracker.getCurrentBuild().getSharedConfigParameters();
      return Boolean.parseBoolean(sharedConfigParameters.get(TorrentConfiguration.TRANSPORT_ENABLED));
    } catch (NoRunningBuildException ignored) {
    }
    return false;
  }

  @Override public int getSocketTimeout() {
    int defaultTimeout = (int) TimeUnit.MILLISECONDS.toSeconds(Constants.DEFAULT_SOCKET_CONNECTION_TIMEOUT_MILLIS);
    return call("getSocketTimeout", defaultTimeout);
  }

  @Override public int getCleanupTimeout() {
    int defaultTimeout = (int) TimeUnit.MILLISECONDS.toSeconds(Constants.DEFAULT_CLEANUP_RUN_TIMEOUT_MILLIS);
    return call("getCleanupTimeout", defaultTimeout);
  }

  @Override public int getMaxConnectionsCount() {
    return call("getMaxConnectionsCount", TorrentConfiguration.DEFAULT_MAX_CONNECTIONS);
  }

  @Override
  public boolean isTorrentEnabled() {
    return call("isTorrentEnabled", TorrentConfiguration.DEFAULT_TORRENT_ENABLED);
  }

  @Override
  public String getServerURL() {
    return myBuildAgentConfiguration.getServerUrl();
  }

  @NotNull
  private <T> T call(@NotNull String methodName, @NotNull final T defaultValue) {
    if (myXmlRpcTarget == null) {
      return defaultValue;
    }
    try {
      final Object retval = myXmlRpcTarget.call(XmlRpcConstants.TORRENT_CONFIGURATION + "." + methodName, new Object[0]);
      if (retval != null)
        return (T) retval;
      else
        return defaultValue;
    } catch (Exception e) {
      return defaultValue;
    }
  }
}
