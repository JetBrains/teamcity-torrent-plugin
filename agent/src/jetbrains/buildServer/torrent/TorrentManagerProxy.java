package jetbrains.buildServer.torrent;

import jetbrains.buildServer.agent.BuildAgent;
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
  @NotNull
  private final XmlRpcTarget myXmlRpcTarget;

  public TorrentManagerProxy(@NotNull BuildAgent buildAgent) {
    myXmlRpcTarget = XmlRpcFactory.getInstance().create(buildAgent.getConfiguration().getServerUrl(), "TeamCity Agent", 30000, false);
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

  @NotNull
  private <T> T call(@NotNull String methodName, @NotNull final T defaultValue) {
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
