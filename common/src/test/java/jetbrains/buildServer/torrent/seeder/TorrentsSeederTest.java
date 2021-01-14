/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

import com.turn.ttorrent.client.LoadedTorrent;
import com.turn.ttorrent.client.SelectorFactoryImpl;
import com.turn.ttorrent.client.announce.TrackerClientFactoryImpl;
import com.turn.ttorrent.common.TorrentCreator;
import com.turn.ttorrent.common.TorrentMetadata;
import com.turn.ttorrent.tracker.Tracker;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.torrent.TorrentConfiguration;
import jetbrains.buildServer.torrent.torrent.TorrentUtil;
import jetbrains.buildServer.util.FileUtil;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Test
public class TorrentsSeederTest extends BaseTestCase {
  private TorrentsSeeder myDirectorySeeder;
  private ScheduledExecutorService myExecutorService;
  private Tracker myTracker;

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myTracker = new Tracker(6969);
    myTracker.start(false);
    Mockery m = new Mockery();
    myExecutorService = m.mock(ScheduledExecutorService.class);
    final TorrentConfiguration torrentConfiguration = m.mock(TorrentConfiguration.class);
    m.checking(new Expectations(){{
      allowing(torrentConfiguration).getWorkerPoolSize(); will(returnValue(10));
      allowing(torrentConfiguration).getPieceHashingPoolSize(); will(returnValue(4));
      allowing(myExecutorService).submit(with(any(Runnable.class)));
      allowing(myExecutorService).scheduleWithFixedDelay(with(any(Runnable.class)), with(any(Long.class)), with(any(Long.class)), with(any(TimeUnit.class)));
      allowing(myExecutorService).shutdownNow();
    }});

    myDirectorySeeder = new TorrentsSeeder(
            createTempDir(), 100, null, myExecutorService, torrentConfiguration, new TrackerClientFactoryImpl());
    myDirectorySeeder.start(new InetAddress[]{InetAddress.getLocalHost()}, myTracker.getAnnounceURI(), 3, new SelectorFactoryImpl());
  }

  public void start_seeding_file() throws IOException, InterruptedException {
    final File srcFile = createTempFile(65535);
    final File torrentFile = createTorrentFromFile(srcFile, srcFile.getParentFile());
    myDirectorySeeder.registerSrcAndTorrentFile(srcFile, torrentFile, true);

    assertTrue(myDirectorySeeder.isSeeding(torrentFile));
  }

  public void stop_seeding() throws IOException, InterruptedException {
    final File srcFile = createTempFile(65535);
    final File torrentFile = createTorrentFromFile(srcFile, srcFile.getParentFile());
    myDirectorySeeder.registerSrcAndTorrentFile(srcFile, torrentFile, true);

    assertTrue(myDirectorySeeder.isSeeding(torrentFile));

    myDirectorySeeder.unregisterSrcFile(srcFile);

    assertFalse(myDirectorySeeder.isSeeding(torrentFile));
  }

  public void stop_seeding_broken_file() throws IOException, InterruptedException {
    final File srcFile = createTempFile(65535);
    final File torrentFile = createTorrentFromFile(srcFile, srcFile.getParentFile());
    myDirectorySeeder.registerSrcAndTorrentFile(srcFile, torrentFile, true);

    assertTrue(myDirectorySeeder.isSeeding(torrentFile));

    LoadedTorrent next = myDirectorySeeder.getClient().getLoadedTorrents().iterator().next();
    FileUtil.delete(srcFile);

    myDirectorySeeder.checkForBrokenFiles();
    assertFalse(myDirectorySeeder.isSeeding(torrentFile));
  }

  private File createTorrentFromFile(File srcFile, File torrentDir) throws InterruptedException, IOException {
    File torrentFile = new File(torrentDir, srcFile.getName() + ".torrent");
    final TorrentMetadata torrent = TorrentCreator.create(srcFile, myTracker.getAnnounceURI(), "Test");
    TorrentUtil.saveTorrentToFile(torrent, torrentFile);
    return torrentFile;
  }

  @AfterMethod
  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    myDirectorySeeder.dispose();
    myExecutorService.shutdownNow();
    myTracker.stop();
  }
}
