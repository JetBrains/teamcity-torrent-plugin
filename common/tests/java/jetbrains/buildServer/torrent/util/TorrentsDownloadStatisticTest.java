/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.torrent.util;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

@Test
public class TorrentsDownloadStatisticTest {

  private TorrentsDownloadStatistic myTorrentsDownloadStatistic;

  @BeforeMethod
  public void setUp() throws Exception {
    myTorrentsDownloadStatistic = new TorrentsDownloadStatistic();
  }

  public void testStatistic() {

    assertEquals(myTorrentsDownloadStatistic.getFailedDownloadCount(), 0);
    assertEquals(myTorrentsDownloadStatistic.getSuccessfulDownloadCount(), 0);
    assertEquals(myTorrentsDownloadStatistic.getAverageSpeedKbS(), 0, 0.001);

    myTorrentsDownloadStatistic.fileDownloadFailed();
    myTorrentsDownloadStatistic.fileDownloadFailed();
    myTorrentsDownloadStatistic.fileDownloadFailed();

    myTorrentsDownloadStatistic.fileDownloaded(1000, 2000000);
    myTorrentsDownloadStatistic.fileDownloaded(1000, 4000000);

    assertEquals(myTorrentsDownloadStatistic.getFailedDownloadCount(), 3);
    assertEquals(myTorrentsDownloadStatistic.getSuccessfulDownloadCount(), 2);
    assertEquals(myTorrentsDownloadStatistic.getAverageSpeedKbS(), 2929.68, 0.1);

  }

  @AfterMethod
  public void tearDown() throws Exception {
    myTorrentsDownloadStatistic.reset();
  }
}
