/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package jetbrains.buildServer.artifactsMirror.seeder;

import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.artifactsMirror.torrent.TorrentUtil;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.WaitFor;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;

@Test
public class TorrentsDirectorySeederTest extends BaseTestCase {
  private TorrentsDirectorySeeder myDirectorySeeder;
  private File myStorageDir;

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myStorageDir = createTempDir();
    myDirectorySeeder = new TorrentsDirectorySeeder(myStorageDir, new TorrentFileFactory() {
      @NotNull
      public File createTorrentFile(@NotNull File sourceFile, @NotNull File parentDir) throws IOException {
        File torrentFile = new File(parentDir, sourceFile.getName() + TorrentUtil.TORRENT_FILE_SUFFIX);
        try {
          TorrentUtil.createTorrent(sourceFile, torrentFile, new URI("http://localhost:6969/announce"));
        } catch (URISyntaxException e) {
          throw new IOException("Invalid announce URI: " + e.toString());
        }
        return torrentFile;
      }
    });
    myDirectorySeeder.start(InetAddress.getLocalHost(), 3);
  }

  public void new_link() throws IOException, NoSuchAlgorithmException {
    File srcFile = createTempFile();
    final File linkFile = FileLink.createLink(srcFile, myStorageDir);
    final File torrentFile = waitForTorrentFile(linkFile);

    assertTrue(torrentFile.isFile());
    assertTrue(myDirectorySeeder.isSeeding(torrentFile));
  }

  public void link_removed() throws IOException, NoSuchAlgorithmException {
    File srcFile = createTempFile();
    final File linkFile = FileLink.createLink(srcFile, myStorageDir);
    final File torrentFile = waitForTorrentFile(linkFile);

    assertTrue(torrentFile.isFile());
    assertTrue(myDirectorySeeder.isSeeding(torrentFile));

    FileUtil.delete(linkFile);
    new WaitFor() {
      @Override
      protected boolean condition() {
        return !torrentFile.isFile();
      }
    };

    assertFalse(torrentFile.isFile());
  }

  public void target_file_removed() throws IOException, NoSuchAlgorithmException {
    final File srcFile = createTempFile();
    final File linkFile = FileLink.createLink(srcFile, myStorageDir);
    final File torrentFile = waitForTorrentFile(linkFile);

    assertTrue(torrentFile.isFile());
    assertTrue(myDirectorySeeder.isSeeding(torrentFile));

    FileUtil.delete(srcFile);
    assertFalse(srcFile.exists());
    new WaitFor() {
      @Override
      protected boolean condition() {
        return !torrentFile.isFile() && !linkFile.isFile();
      }
    };

    assertFalse(torrentFile.isFile());
    assertFalse(linkFile.isFile());
  }

  public void empty_dirs_removed() throws IOException, NoSuchAlgorithmException {
    File srcFile = createTempFile();
    final File linkDir = new File(myStorageDir, "subdir");
    final File linkFile = FileLink.createLink(srcFile, linkDir);
    final File torrentFile = waitForTorrentFile(linkFile);

    assertTrue(torrentFile.isFile());
    assertTrue(myDirectorySeeder.isSeeding(torrentFile));

    FileUtil.delete(linkFile);
    new WaitFor() {
      @Override
      protected boolean condition() {
        return !linkDir.isDirectory();
      }
    };

    assertFalse(linkDir.isDirectory());
  }

  public void max_number_of_seeded_torrents() throws IOException {
    myDirectorySeeder.setMaxTorrentsToSeed(3);

    for (int i=0; i<5; i++) {
      File srcFile = createTempFile();
      File linkFile = FileLink.createLink(srcFile, myStorageDir);
      waitForTorrentFile(linkFile);
    }

    new WaitFor() {
      @Override
      protected boolean condition() {
        return myStorageDir.list().length == 2 * myDirectorySeeder.getMaxTorrentsToSeed();
      }
    };

    assertEquals(myDirectorySeeder.getMaxTorrentsToSeed(), myStorageDir.listFiles(new FileFilter() {
      public boolean accept(File pathname) {
        return FileLink.isLink(pathname);
      }
    }).length);

    assertEquals(myDirectorySeeder.getMaxTorrentsToSeed(), myStorageDir.listFiles(new FileFilter() {
      public boolean accept(File pathname) {
        return pathname.getName().endsWith(TorrentUtil.TORRENT_FILE_SUFFIX);
      }
    }).length);

    assertEquals(myDirectorySeeder.getMaxTorrentsToSeed(), myDirectorySeeder.getNumberOfSeededTorrents());
  }

  private File waitForTorrentFile(File linkFile) {
    final File torrentFile = TorrentsDirectorySeeder.getTorrentFileByLinkFile(linkFile);
    new WaitFor() {
      @Override
      protected boolean condition() {
        try {
          return torrentFile.isFile() && myDirectorySeeder.isSeeding(torrentFile);
        } catch (Throwable e) {
          return false;
        }
      }
    };
    return torrentFile;
  }

  @AfterMethod
  @Override
  protected void tearDown() throws Exception {
    myDirectorySeeder.stop();
    super.tearDown();
  }
}
