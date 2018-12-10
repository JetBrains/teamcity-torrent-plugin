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
