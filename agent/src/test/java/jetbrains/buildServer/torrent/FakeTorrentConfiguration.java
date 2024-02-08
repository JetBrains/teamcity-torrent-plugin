

package jetbrains.buildServer.torrent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FakeTorrentConfiguration implements TorrentConfiguration {
  @Nullable
  public String getAnnounceUrl() {
    return "http://localhost:6969/announce";
  }

  public long getFileSizeThresholdBytes() {
    return 1024*1024;
  }

  public int getAnnounceIntervalSec() {
    return 3;
  }

  public boolean isTransportEnabled() {
    return true;
  }

  public boolean isTorrentEnabled() {
    return true;
  }

  @Override public int getSocketTimeout() {
    return 60;
  }

  @Override public int getCleanupTimeout() {
    return 60;
  }

  @NotNull
  @Override
  public String getOwnTorrentAddress() {
    return "";
  }

  @NotNull
  @Override
  public String getAgentAddressPrefix() {
    return "";
  }

  @Override
  public int getWorkerPoolSize() {
    return 10;
  }

  @Override
  public int getPieceHashingPoolSize() {
    return 4;
  }

  @Override public int getMaxConnectionsCount() {
    return 10;
  }

}