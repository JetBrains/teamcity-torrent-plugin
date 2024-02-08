

package jetbrains.buildServer.torrent.util;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TorrentsDownloadStatistic {

  public final static String SUCCESS_DOWNLOAD_KEY = "torrent.statistic.successCount";
  public final static String FAIL_DOWNLOAD_KEY = "torrent.statistic.failCount";


  private final AtomicInteger mySuccessfulDownloadCount;
  private final AtomicInteger myFailedDownloadCount;


  public TorrentsDownloadStatistic() {
    this.mySuccessfulDownloadCount = new AtomicInteger(0);
    this.myFailedDownloadCount = new AtomicInteger(0);
  }


  public void reset() {
    mySuccessfulDownloadCount.set(0);
    myFailedDownloadCount.set(0);
  }

  public void fileDownloaded() {
    mySuccessfulDownloadCount.incrementAndGet();
  }

  public void fileDownloadFailed() {
    myFailedDownloadCount.incrementAndGet();
  }

  public int getSuccessfulDownloadCount() {
    return mySuccessfulDownloadCount.get();
  }

  public int getFailedDownloadCount() {
    return myFailedDownloadCount.get();
  }

}