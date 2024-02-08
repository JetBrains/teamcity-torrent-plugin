

package jetbrains.buildServer.torrent.torrent;

public class DownloadException extends Exception {

  public DownloadException(String message) {
    super(message);
  }

  public DownloadException(String message, Throwable e) {
    super(message, e);
  }
}