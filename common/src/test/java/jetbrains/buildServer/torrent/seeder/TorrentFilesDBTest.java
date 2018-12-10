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
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.ThreadUtil;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Test
public class TorrentFilesDBTest extends BaseTestCase {

  private TempFiles myTempFiles;

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    myTempFiles = new TempFiles();
  }

  public void new_db() throws IOException {
    File dbFile = createTempFile();

    File srcFile = createTempFile();
    File torrentFile = createTempFile();

    TorrentFilesDB db = new TorrentFilesDB(dbFile, 10, null, null);
    db.addFileAndTorrent(srcFile, torrentFile);
    db.flush();

    List<String> lines = FileUtil.readFile(dbFile);
    assertTrue(lines.get(0).contains(srcFile.getAbsolutePath()));
    assertTrue(lines.get(0).contains(torrentFile.getAbsolutePath()));
  }

  public void reopen_db() throws IOException {
    File dbFile = createTempFile();

    Map<File, File> expectedMap = new HashMap<File, File>();

    TorrentFilesDB db = new TorrentFilesDB(dbFile, 10, null, null);
    for (int i=0; i<10; i++) {
      File srcFile = createTempFile();
      File torrentFile = createTempFile();
      expectedMap.put(srcFile, torrentFile);
      db.addFileAndTorrent(srcFile, torrentFile);
    }
    db.flush();

    db = new TorrentFilesDB(dbFile, 10, null, null);
    assertEquals(expectedMap, db.getFileAndTorrentMap());
  }

  public void db_limit() throws IOException {
    File dbFile = createTempFile();

    Map<File, File> expectedMap = new HashMap<File, File>();

    TorrentFilesDB db = new TorrentFilesDB(dbFile, 10, null, null);
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

    final File dir = createTempDir();
    TorrentFilesDB db = new TorrentFilesDB(dbFile, 10, null, null);
    for (int i=0; i<10; i++) {
      ThreadUtil.sleep(100);

      File srcFile = createTmpFileWithTS(dir);
      File torrentFile = createTempFile();

      if (i >= 5) {
        expectedMap.put(srcFile, torrentFile);
      }
      db.addFileAndTorrent(srcFile, torrentFile);
    }

    db.setMaxTorrents(5);
    assertEquals(expectedMap, db.getFileAndTorrentMap());
  }

  @NotNull
  private File createTmpFileWithTS(File dir) throws IOException {
    File srcFile = new File(dir, String.format("%d-test.tmp", System.currentTimeMillis()));
    srcFile.createNewFile();
    myTempFiles.registerAsTempFile(srcFile);
    return srcFile;
  }

  public void custom_path_translator() throws IOException {
    File dbFile = createTempFile();

    final File rootDir = createTempDir();

    final PathConverter pathConverter = new ParentDirConverter() {
      @NotNull
      @Override
      public File getParentDir() {
        return rootDir;
      }
    };
    TorrentFilesDB db = new TorrentFilesDB(dbFile, 10, pathConverter, null);

    new File(rootDir, "a/b/c").mkdirs();
    new File(rootDir, "d/e/f").mkdirs();

    Map<File, File> expectedMap = new HashMap<File, File>();
    for (int i=0; i<5; i++) {
      File src = new File(rootDir, "a/b/c/src" + i + ".txt").getAbsoluteFile();
      File torrent = new File(rootDir, "d/e/f/torrent" + i + ".txt").getAbsoluteFile();

      src.createNewFile();
      torrent.createNewFile();

      ThreadUtil.sleep(100); // need this for proper sorting of files because TorrentFilesDB sorts file by last modified time

      db.addFileAndTorrent(src, torrent);
      expectedMap.put(src, torrent);
    }

    assertEquals(expectedMap, db.getFileAndTorrentMap());

    db.flush();

    db = new TorrentFilesDB(dbFile, 10, pathConverter, null);
    assertEquals(expectedMap, db.getFileAndTorrentMap());

    final List<String> lines = FileUtil.readFile(dbFile);
    assertEquals(5, lines.size());
    for (int i=0; i<lines.size(); i++) {
      String expected = ("a/b/c/src" + i + ".txt || d/e/f/torrent" + i + ".txt").replace('/', File.separatorChar);
      assertEquals(expected, lines.get(i));
    }
  }

  public void test_listener_notified() throws IOException {
    File dbFile = createTempFile();
    final int[] counter = new int[1];
    TorrentFilesDB db = new TorrentFilesDB(dbFile, 3, null, new TorrentFilesDB.CacheListener() {
      public void onRemove(@NotNull Map.Entry<File, File> removedEntry) {
        counter[0]++;
      }
    });

    db.addFileAndTorrent(createTempFile(), createTempFile());
    db.addFileAndTorrent(createTempFile(), createTempFile());
    db.addFileAndTorrent(createTempFile(), createTempFile());

    assertEquals(0, counter[0]);

    final File srcFile = createTempFile();
    db.addFileAndTorrent(srcFile, createTempFile());
    assertEquals(1, counter[0]);

    db.removeSrcFile(srcFile);
    assertEquals(2, counter[0]);

    db.setMaxTorrents(1);
    assertEquals(3, counter[0]);
  }

  @AfterMethod
  @Override
  protected void tearDown() throws Exception {
    myTempFiles.cleanup();
    super.tearDown();
  }
}
