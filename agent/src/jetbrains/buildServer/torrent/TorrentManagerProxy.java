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
    return (String) call("getAnnounceUrl");
  }

  public int getFileSizeThresholdMb() {
    return (Integer) call("getFileSizeThresholdMb");
  }

  public int getAnnounceIntervalSec() {
    return (Integer) call("getAnnounceIntervalSec");
  }

  public boolean isTransportEnabled() {
    return (Boolean) call("isTransportEnabled");
  }

  public boolean isTorrentEnabled() {
    return (Boolean) call("isTorrentEnabled");
  }

  private Object call(@NotNull String methodName) {
    try {
      return myXmlRpcTarget.call(XmlRpcConstants.TORRENT_CONFIGURATION + "." + methodName, new Object[0]);
    } catch (RemoteCallException e) {
      return null;
    }
  }
}
