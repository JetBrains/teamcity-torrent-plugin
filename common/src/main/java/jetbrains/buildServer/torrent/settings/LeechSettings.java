/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

package jetbrains.buildServer.torrent.settings;

public interface LeechSettings {

  String MIN_SEEDERS_FOR_DOWNLOAD = "teamcity.torrent.peer.download.minSeedersToStart";
  String MAX_PIECE_DOWNLOAD_TIME = "teamcity.torrent.peer.download.pieceTotalTimeout.seconds";
  String DOWNLOAD_ENABLED = "teamcity.torrent.peer.download.enabled";
  int DEFAULT_MIN_SEEDERS_FOR_DOWNLOAD = 2;
  int DEFAULT_MAX_PIECE_DOWNLOAD_TIME = 7;
  boolean DEFAULT_DOWNLOAD_ENABLED = false;

  /**
   * Returns maximum time for download one piece
   *
   * @return see above
   */
  int getMaxPieceDownloadTime();

  /**
   * Indicates whether torrent transport is enabled and agent must try to download artifact via bittorrent
   */
  boolean isDownloadEnabled();

  /**
   * Returns min seeders count for download.
   *
   * @return see above
   */
  int getMinSeedersForDownload();

}
