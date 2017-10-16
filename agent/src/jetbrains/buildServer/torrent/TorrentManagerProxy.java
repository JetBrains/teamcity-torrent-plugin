package jetbrains.buildServer.torrent;

import jetbrains.buildServer.agent.AgentLifeCycleAdapter;
import jetbrains.buildServer.agent.AgentLifeCycleListener;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.BuildAgent;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.xmlrpc.RemoteCallException;
import jetbrains.buildServer.xmlrpc.XmlRpcFactory;
import jetbrains.buildServer.xmlrpc.XmlRpcTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Victory.Bedrosova
 * Date: 10/12/12
 * Time: 4:06 PM
 */
public class TorrentManagerProxy implements TorrentConfiguration {
  private XmlRpcTarget myXmlRpcTarget;

  public TorrentManagerProxy(@NotNull final EventDispatcher<AgentLifeCycleListener> dispatcher) {
    dispatcher.addListener(new AgentLifeCycleAdapter(){
      @Override
      public void afterAgentConfigurationLoaded(@NotNull BuildAgent agent) {
        if (StringUtil.isNotEmpty(agent.getConfiguration().getServerUrl())){
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

  public int getAnnounceIntervalSec() {
    return call("getAnnounceIntervalSec", TorrentConfiguration.DEFAULT_ANNOUNCE_INTERVAL);
  }

  public boolean isTransportEnabled() {
    return call("isTransportEnabled", TorrentConfiguration.DEFAULT_TRANSPORT_ENABLED);
  }

  public boolean isTorrentEnabled() {
    return call("isTorrentEnabled", TorrentConfiguration.DEFAULT_TORRENT_ENABLED);
  }

  @Override
  public String getServerURL() {
    return "";// TODO: 10/16/17 inject BuildAgentConfiguration and use method from it
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
