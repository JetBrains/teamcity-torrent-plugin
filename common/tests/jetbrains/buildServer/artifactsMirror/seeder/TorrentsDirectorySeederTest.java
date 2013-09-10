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

import com.turn.ttorrent.common.Torrent;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.util.FileUtil;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.security.NoSuchAlgorithmException;

@Test
public class TorrentsDirectorySeederTest extends BaseTestCase {
  private TorrentsDirectorySeeder myDirectorySeeder;
  private File myStorageDir;
  private URI announceURI;

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myStorageDir = createTempDir();
    announceURI = new URI("http://localhost:6969/announce");
    myDirectorySeeder = new TorrentsDirectorySeeder(myStorageDir, -1, 1);
    myDirectorySeeder.start(new InetAddress[]{InetAddress.getLocalHost()}, null, 3);
  }

  public void link_removed() throws IOException, NoSuchAlgorithmException, InterruptedException {
    File srcFile = createTempFile();
    final File torrentFile = createTorrentFromFile(srcFile, srcFile.getParentFile());
    final File linkFile = FileLink.createLink(srcFile, torrentFile, myStorageDir);
    myDirectorySeeder.getTorrentSeeder().seedTorrent(Torrent.load(torrentFile), srcFile);

    assertTrue(torrentFile.isFile());
    assertTrue(myDirectorySeeder.isSeeding(torrentFile));
    myDirectorySeeder.getNewLinksWatcher().checkForModifications();

    FileUtil.delete(linkFile);
    myDirectorySeeder.getNewLinksWatcher().checkForModifications();

    assertFalse(linkFile.isFile());
  }

  public void target_file_removed() throws IOException, NoSuchAlgorithmException, InterruptedException {
    final File srcFile = createTempFile();
    final File torrentFile = createTorrentFromFile(srcFile, srcFile.getParentFile());
    final File linkFile = FileLink.createLink(srcFile, torrentFile, myStorageDir);
    myDirectorySeeder.getTorrentSeeder().seedTorrent(Torrent.load(torrentFile), srcFile);

    assertTrue(torrentFile.isFile());
    assertTrue(myDirectorySeeder.isSeeding(torrentFile));

    myDirectorySeeder.getNewLinksWatcher().checkForModifications();

    FileUtil.delete(srcFile);
    assertFalse(srcFile.exists());
    myDirectorySeeder.getNewLinksWatcher().checkForModifications();

    assertTrue(!torrentFile.isFile() && !linkFile.isFile());

    assertFalse(torrentFile.isFile());
    assertFalse(linkFile.isFile());
  }

  public void empty_dirs_removed() throws IOException, NoSuchAlgorithmException, InterruptedException {
    throw new SkipException("Skipped"); // until we figure that out.
/*
    File srcFile = createTempFile();
    final File linkDir = new File(myStorageDir, "subdir");
    final File torrentFile = createTorrentFromFile(srcFile, srcFile.getParentFile());
    final File linkFile = FileLink.createLink(srcFile, torrentFile, linkDir);

    assertTrue(torrentFile.isFile());
    assertTrue(myDirectorySeeder.isSeeding(torrentFile));
    myDirectorySeeder.getNewLinksWatcher().checkForModifications();

    FileUtil.delete(linkFile);
    myDirectorySeeder.getNewLinksWatcher().checkForModifications();

    assertFalse(linkDir.isDirectory());
*/
  }

  private File createTorrentFromFile(File srcFile, File torrentDir) throws InterruptedException, NoSuchAlgorithmException, IOException {
    File torrentFile = new File(torrentDir, srcFile.getName() + ".torrent");
    final Torrent torrent = Torrent.create(srcFile, announceURI, "Test");
    torrent.save(torrentFile);
    return torrentFile;
  }

  @AfterMethod
  @Override
  protected void tearDown() throws Exception {
    myDirectorySeeder.stop();
    super.tearDown();
  }
}
