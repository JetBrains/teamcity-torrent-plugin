/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package jetbrains.buildServer.artifactsMirror;

import com.turn.ttorrent.common.Torrent;
import com.turn.ttorrent.tracker.Tracker;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.XmlRpcHandlerManager;
import jetbrains.buildServer.artifactsMirror.seeder.FileLink;
import jetbrains.buildServer.serverSide.BuildServerListener;
import jetbrains.buildServer.serverSide.BuildServerListenerEventDispatcher;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifact;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.serverSide.impl.ServerSettings;
import jetbrains.buildServer.serverSide.impl.artifacts.BuildArtifactImpl;
import jetbrains.buildServer.serverSide.impl.auth.SecurityContextImpl;
import jetbrains.buildServer.serverSide.impl.executors.SimpleExecutorServices;
import jetbrains.buildServer.util.EventDispatcher;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.security.NoSuchAlgorithmException;

/**
 * @author Sergey.Pak
 *         Date: 9/5/13
 *         Time: 8:38 PM
 */
@Test
public class ServerTorrentsDirectorySeederTest extends BaseTestCase {

  private ServerTorrentsDirectorySeeder myDirectorySeeder;
  private TempFiles myTempFiles;
  private TorrentConfigurator myConfigurator;
  private Tracker myTracker;


  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final Mockery m = new Mockery();
    myTempFiles = new TempFiles();
    final File baseDir = myTempFiles.createTempDir();

    final ServerPaths serverPaths = new ServerPaths(myTempFiles.createTempDir().getAbsolutePath());
    final ServerSettings settings = m.mock(ServerSettings.class);

    myConfigurator = new TorrentConfigurator(serverPaths, settings, new XmlRpcHandlerManager() {
      public void addHandler(String handlerName, Object handler) {}
      public void addSessionHandler(String handlerName, Object handler) {}
    });


    ExecutorServices services = new SimpleExecutorServices();

    EventDispatcher<BuildServerListener> dispatcher = new BuildServerListenerEventDispatcher(new SecurityContextImpl());

    myDirectorySeeder = new ServerTorrentsDirectorySeeder(serverPaths, myConfigurator, services, dispatcher);

    myTracker = new Tracker(6969);
    myTracker.start(true);
  }

  public void max_number_of_seeded_torrents() throws IOException, NoSuchAlgorithmException, InterruptedException {
    myDirectorySeeder.startSeeder();
    myDirectorySeeder.setMaxNumberOfSeededTorrents(3);
    myDirectorySeeder.setAnnounceURI(URI.create("http://localhost:6969/announce"));
    myConfigurator.setSeederEnabled(true);
    myConfigurator.setTrackerEnabled(true);

    final File artifactsDir = myTempFiles.createTempDir();
    final File linkDir = myTempFiles.createTempDir();
    final File torrentsDir = myTempFiles.createTempDir();

    final int fileSize = 11 * 1024 * 1024;
    for (int i=0; i<5; i++) {
      final File tempFile = myTempFiles.createTempFile(fileSize);
      // move to artifacts dir;
      final File srcFile = new File(artifactsDir, tempFile.getName());
      final boolean b = tempFile.renameTo(srcFile);
      myDirectorySeeder.processArtifactInternal(new DummyBuildArtifactAdapter(){
        @Override
        public boolean isFile() {
          return true;
        }

        @Override
        public long getSize() {
          return fileSize;
        }

        @NotNull
        @Override
        public String getName() {
          return tempFile.getName();
        }

        @NotNull
        @Override
        public String getRelativePath() {
          return tempFile.getName();
        }
      }, artifactsDir, linkDir, torrentsDir);
    }

    assertEquals(3, myDirectorySeeder.getNumberOfSeededTorrents());
  }


  @AfterMethod
  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    myDirectorySeeder.stopSeeder();
    myTracker.stop();
  }

  private File createTorrentFromFile(File srcFile, File torrentDir) throws InterruptedException, NoSuchAlgorithmException, IOException {
    File torrentFile = new File(torrentDir, srcFile.getName() + ".torrent");
    final Torrent torrent = Torrent.create(srcFile, URI.create("http://localhost:6969"), "Test");
    torrent.save(torrentFile);
    return torrentFile;
  }

}
