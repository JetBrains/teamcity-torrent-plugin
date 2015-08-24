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

package jetbrains.buildServer.torrent.seeder;

import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.ThreadUtil;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Test
public class TorrentFilesDBTest extends BaseTestCase {
  public void new_db() throws IOException {
    File dbFile = createTempFile();

    File srcFile = createTempFile();
    File torrentFile = createTempFile();

    TorrentFilesDB db = new TorrentFilesDB(dbFile, 10);
    db.addFileAndTorrent(srcFile, torrentFile);
    db.flush();

    List<String> lines = FileUtil.readFile(dbFile);
    assertTrue(lines.get(0).contains(srcFile.getAbsolutePath()));
    assertTrue(lines.get(0).contains(torrentFile.getAbsolutePath()));
  }

  public void reopen_db() throws IOException {
    File dbFile = createTempFile();

    Map<File, File> expectedMap = new HashMap<File, File>();

    TorrentFilesDB db = new TorrentFilesDB(dbFile, 10);
    for (int i=0; i<10; i++) {
      File srcFile = createTempFile();
      File torrentFile = createTempFile();
      expectedMap.put(srcFile, torrentFile);
      db.addFileAndTorrent(srcFile, torrentFile);
    }
    db.flush();

    db = new TorrentFilesDB(dbFile, 10);
    assertEquals(expectedMap, db.getFileAndTorrentMap());
  }

  public void db_limit() throws IOException {
    File dbFile = createTempFile();

    Map<File, File> expectedMap = new HashMap<File, File>();

    TorrentFilesDB db = new TorrentFilesDB(dbFile, 10);
    for (int i=0; i<20; i++) {
      File srcFile = createTempFile();
      File torrentFile = createTempFile();
      if (i >= 10) {
        expectedMap.put(srcFile, torrentFile);
      }
      db.addFileAndTorrent(srcFile, torrentFile);
    }
    db.flush();

    assertEquals(expectedMap, db.getFileAndTorrentMap());
  }

  public void set_smaller_limit() throws IOException {
    File dbFile = createTempFile();

    Map<File, File> expectedMap = new HashMap<File, File>();

    TorrentFilesDB db = new TorrentFilesDB(dbFile, 10);
    for (int i=0; i<10; i++) {
      ThreadUtil.sleep(10);
      File srcFile = createTempFile();
      File torrentFile = createTempFile();

      if (i >= 5) {
        expectedMap.put(srcFile, torrentFile);
      }
      db.addFileAndTorrent(srcFile, torrentFile);
    }

    db.setMaxTorrents(5);
    assertEquals(expectedMap, db.getFileAndTorrentMap());
  }
}
