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

package jetbrains.buildServer.torrent.seeder;

import com.turn.ttorrent.common.Torrent;
import com.turn.ttorrent.tracker.Tracker;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.util.FileUtil;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.security.NoSuchAlgorithmException;

@Test
public class TorrentsSeederTest extends BaseTestCase {
  private TorrentsSeeder myDirectorySeeder;
  private Tracker myTracker;

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myTracker = new Tracker(6969);
    myTracker.start(false);

    myDirectorySeeder = new TorrentsSeeder(createTempDir(), 100, null);
    myDirectorySeeder.start(new InetAddress[]{InetAddress.getLocalHost()}, myTracker.getAnnounceURI(), 3);
  }

  public void start_seeding_file() throws IOException, NoSuchAlgorithmException, InterruptedException {
    final File srcFile = createTempFile(65535);
    final File torrentFile = createTorrentFromFile(srcFile, srcFile.getParentFile());
    myDirectorySeeder.registerSrcAndTorrentFile(srcFile, torrentFile, true);

    assertTrue(myDirectorySeeder.isSeeding(torrentFile));
  }

  public void stop_seeding() throws IOException, NoSuchAlgorithmException, InterruptedException {
    final File srcFile = createTempFile(65535);
    final File torrentFile = createTorrentFromFile(srcFile, srcFile.getParentFile());
    myDirectorySeeder.registerSrcAndTorrentFile(srcFile, torrentFile, true);

    assertTrue(myDirectorySeeder.isSeeding(torrentFile));

    myDirectorySeeder.unregisterSrcFile(srcFile);

    assertFalse(myDirectorySeeder.isSeeding(torrentFile));
  }

  public void stop_seeding_broken_file() throws IOException, NoSuchAlgorithmException, InterruptedException {
    final File srcFile = createTempFile(65535);
    final File torrentFile = createTorrentFromFile(srcFile, srcFile.getParentFile());
    myDirectorySeeder.registerSrcAndTorrentFile(srcFile, torrentFile, true);

    assertTrue(myDirectorySeeder.isSeeding(torrentFile));

    FileUtil.delete(srcFile);

    myDirectorySeeder.checkForBrokenFiles();
    assertFalse(myDirectorySeeder.isSeeding(torrentFile));
  }

  private File createTorrentFromFile(File srcFile, File torrentDir) throws InterruptedException, NoSuchAlgorithmException, IOException {
    File torrentFile = new File(torrentDir, srcFile.getName() + ".torrent");
    final Torrent torrent = Torrent.create(srcFile, myTracker.getAnnounceURI(), "Test");
    torrent.save(torrentFile);
    return torrentFile;
  }

  @AfterMethod
  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    myDirectorySeeder.dispose();
    myTracker.stop();
  }
}
