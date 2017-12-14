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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TorrentsDownloadStatistic {

  public final static String SUCCESS_DOWNLOAD_KEY = "torrent.statistic.successCount";
  public final static String FAIL_DOWNLOAD_KEY = "torrent.statistic.failCount";
  public final static String AVERAGE_SPEED_KEY = "torrent.statistic.speed";


  private final AtomicInteger mySuccessfulDownloadCount;
  private final AtomicInteger myFailedDownloadCount;
  private final AtomicLong myTotalTimeMillis;
  private final AtomicLong myTotalSize;


  public TorrentsDownloadStatistic() {
    this.mySuccessfulDownloadCount = new AtomicInteger(0);
    this.myFailedDownloadCount = new AtomicInteger(0);
    this.myTotalSize = new AtomicLong(0);
    this.myTotalTimeMillis = new AtomicLong(0);
  }


  public void reset() {
    mySuccessfulDownloadCount.set(0);
    myFailedDownloadCount.set(0);
    myTotalSize.set(0);
    myTotalTimeMillis.set(0);
  }

  public void fileDownloaded(long time, long size) {
    mySuccessfulDownloadCount.incrementAndGet();
    myTotalSize.addAndGet(size);
    myTotalTimeMillis.addAndGet(time);
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

  /**
   * @return average speed of downloaded torrents (Kilobytes/second). Return zero, if time is 0
   */
  public float getAverageSpeedKbS() {
    if (myTotalTimeMillis.get() == 0) return 0;
    float size = myTotalSize.get();
    float time = myTotalTimeMillis.get();
    float averageSpeed = size / time;//bytes/millisecond
    return averageSpeed * 1000 / 1024;
  }
}
