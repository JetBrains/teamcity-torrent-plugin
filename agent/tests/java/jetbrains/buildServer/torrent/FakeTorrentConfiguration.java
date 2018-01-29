/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package jetbrains.buildServer.torrent;

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

  @Override
  public int getWorkerPoolSize() {
    return 10;
  }

  @Override public int getMaxConnectionsCount() {
    return 10;
  }

}
