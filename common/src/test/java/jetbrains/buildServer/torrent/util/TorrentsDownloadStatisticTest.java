

package jetbrains.buildServer.torrent.util;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

@Test
public class TorrentsDownloadStatisticTest {

  private TorrentsDownloadStatistic myTorrentsDownloadStatistic;

  @BeforeMethod
  public void setUp() {
    myTorrentsDownloadStatistic = new TorrentsDownloadStatistic();
  }

  public void testStatistic() {

    assertEquals(myTorrentsDownloadStatistic.getFailedDownloadCount(), 0);
    assertEquals(myTorrentsDownloadStatistic.getSuccessfulDownloadCount(), 0);

    myTorrentsDownloadStatistic.fileDownloadFailed();
    myTorrentsDownloadStatistic.fileDownloadFailed();
    myTorrentsDownloadStatistic.fileDownloadFailed();

    myTorrentsDownloadStatistic.fileDownloaded();
    myTorrentsDownloadStatistic.fileDownloaded();

    assertEquals(myTorrentsDownloadStatistic.getFailedDownloadCount(), 3);
    assertEquals(myTorrentsDownloadStatistic.getSuccessfulDownloadCount(), 2);

  }

  @AfterMethod
  public void tearDown() {
    myTorrentsDownloadStatistic.reset();
  }
}