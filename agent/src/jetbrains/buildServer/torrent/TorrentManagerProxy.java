package jetbrains.buildServer.torrent;

import com.turn.ttorrent.TorrentDefaults;
import jetbrains.buildServer.agent.AgentLifeCycleAdapter;
import jetbrains.buildServer.agent.AgentLifeCycleListener;
import jetbrains.buildServer.agent.BuildAgent;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.StringUtil;
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
public class TorrentManagerProxy implements TorrentConfiguration {
  private XmlRpcTarget myXmlRpcTarget;
  @NotNull
  final private BuildAgentConfiguration myBuildAgentConfiguration;

  public TorrentManagerProxy(@NotNull final EventDispatcher<AgentLifeCycleListener> dispatcher,
                             @NotNull final BuildAgentConfiguration buildAgentConfiguration) {
    this.myBuildAgentConfiguration = buildAgentConfiguration;
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

  public int getFileSizeThresholdMb() {
    return call("getFileSizeThresholdMb", TorrentConfiguration.DEFAULT_FILE_SIZE_THRESHOLD);
  }

  @Override
  public int getMinSeedersForDownload() {
    return call("getMinSeedersForDownload", TorrentConfiguration.DEFAULT_MIN_SEEDERS_FOR_DOWNLOAD);
  }

  public int getAnnounceIntervalSec() {
    return call("getAnnounceIntervalSec", TorrentConfiguration.DEFAULT_ANNOUNCE_INTERVAL);
  }

  public boolean isTransportEnabled() {
    return call("isTransportEnabled", TorrentConfiguration.DEFAULT_TRANSPORT_ENABLED);
  }

  @Override public int getSocketTimeout() {
    int defaultTimeout = (int) TimeUnit.MILLISECONDS.toSeconds(TorrentDefaults.SOCKET_CONNECTION_TIMEOUT_MILLIS);
    return call("getSocketTimeout", defaultTimeout);
  }

  @Override public int getCleanupTimeout() {
    int defaultTimeout = (int) TimeUnit.MILLISECONDS.toSeconds(TorrentDefaults.CLEANUP_RUN_TIMEOUT);
    return call("getCleanupTimeout", defaultTimeout);
  }

  @Override public int getMaxIncomingConnectionsCount() {
    return call("getMaxIncomingConnectionsCount", TorrentConfiguration.DEFAULT_MAX_INCOMING_CONNECTIONS);
  }

  @Override public int getMaxOutgoingConnectionsCount() {
    return call("getMaxOutgoingConnectionsCount", TorrentConfiguration.DEFAULT_MAX_OUTGOING_CONNECTIONS);
  }

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
