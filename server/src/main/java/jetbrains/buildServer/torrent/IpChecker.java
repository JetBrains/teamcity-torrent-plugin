

package jetbrains.buildServer.torrent;

import com.turn.ttorrent.tracker.AddressChecker;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class IpChecker implements AddressChecker {

  @Override
  public boolean isBadAddress(String ip) {
    InetAddress inetAddress;
    try {
      inetAddress = InetAddress.getByName(ip);
    } catch (UnknownHostException e) {
      return true;
    }
    return inetAddress.isLinkLocalAddress() || inetAddress.isLoopbackAddress() || inetAddress.isAnyLocalAddress();
  }
}