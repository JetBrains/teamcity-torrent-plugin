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

import com.intellij.util.WaitFor;
import com.turn.ttorrent.client.SharedTorrent;
import com.turn.ttorrent.common.Torrent;
import com.turn.ttorrent.tracker.Tracker;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.XmlRpcHandlerManager;
import jetbrains.buildServer.artifactsMirror.seeder.FileLink;
import jetbrains.buildServer.serverSide.BuildServerListener;
import jetbrains.buildServer.serverSide.BuildServerListenerEventDispatcher;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.serverSide.impl.ServerSettings;
import jetbrains.buildServer.serverSide.impl.auth.SecurityContextImpl;
import jetbrains.buildServer.serverSide.impl.executors.SimpleExecutorServices;
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jmock.Mockery;
import org.jmock.core.Constraint;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.*;

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

    final ServerPaths serverPaths = new ServerPaths(myTempFiles.createTempDir().getAbsolutePath());
    final ServerSettings settings = m.mock(ServerSettings.class);

    myConfigurator = new TorrentConfigurator(serverPaths, settings, new XmlRpcHandlerManager() {
      public void addHandler(String handlerName, Object handler) {}
      public void addSessionHandler(String handlerName, Object handler) {}
    });


    ExecutorServices services = new SimpleExecutorServices();

    EventDispatcher<BuildServerListener> dispatcher = new BuildServerListenerEventDispatcher(new SecurityContextImpl());

    myDirectorySeeder = new ServerTorrentsDirectorySeeder(serverPaths, myConfigurator, services, dispatcher);
    dispatcher.getMulticaster().serverStartup();

    myTracker = new Tracker(6969);
    myTracker.start(true);
  }

  public void max_number_of_seeded_torrents_on_startup() throws IOException, NoSuchAlgorithmException, InterruptedException {
    myConfigurator.setMaxNumberOfSeededTorrents(3);
    final URI announceURI = URI.create("http://localhost:6969/announce");
    myDirectorySeeder.setAnnounceURI(announceURI);

    final File artifactsDir = myTempFiles.createTempDir();
    final File torrentsDir = myTempFiles.createTempDir();
    final File storageDir = myDirectorySeeder.getTorrentsDirectorySeeder().getStorageDirectory();

    final int fileSize = 11 * 1024 * 1024;
    for (int i=0; i<5; i++) {
      File tempFile = myTempFiles.createTempFile(fileSize);
      // move to artifacts dir;
      final File srcFile = new File(artifactsDir, tempFile.getName());
      tempFile.renameTo(srcFile);
      tempFile = null;

      final Torrent torrent = Torrent.create(srcFile, announceURI, "Teamcity torrent plugin test");
      final File torrentFile = new File(torrentsDir, srcFile.getName() + ".torrent");
      torrent.save(torrentFile);
      FileLink.createLink(srcFile, torrentFile, storageDir);
    }

    myConfigurator.setSeederEnabled(true);
    myConfigurator.setTrackerEnabled(true);

    Thread.sleep(5*1000);

    assertEquals(3, myDirectorySeeder.getNumberOfSeededTorrents());
  }


  public void new_file_seedeed_old_removed() throws IOException, InterruptedException {
    myDirectorySeeder.setAnnounceURI(URI.create("http://localhost:6969/announce"));
    myConfigurator.setTrackerEnabled(true);
    myConfigurator.setSeederEnabled(true);
    myConfigurator.setMaxNumberOfSeededTorrents(3);
    myConfigurator.setFileSizeThresholdMb(1);
    myDirectorySeeder.startSeeder(3);

    final File artifactsDir = myTempFiles.createTempDir();
    final File torrentsDir = myTempFiles.createTempDir();
    final File storageDirectory = myDirectorySeeder.getTorrentsDirectorySeeder().getStorageDirectory();

    final int fileSize = 1 * 1024 * 1024;
    final Queue<String> filesQueue = new ArrayDeque<String>();
    final List<File> allArtifacts = new ArrayList<File>();
    final List<File> allLinks = new ArrayList<File>();
    final List<File> allTorrents = new ArrayList<File>();
    for (int i=0; i<5; i++) {
      File tempFile = myTempFiles.createTempFile(fileSize);
      // move to artifacts dir;
      final File srcFile = new File(artifactsDir, tempFile.getName());
      tempFile.renameTo(srcFile);
      tempFile = null;
      allArtifacts.add(srcFile);

      myDirectorySeeder.processArtifactInternal(new DummyBuildArtifactAdapter() {
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
          return srcFile.getName();
        }

        @NotNull
        @Override
        public String getRelativePath() {
          return srcFile.getName();
        }
      }, artifactsDir, storageDirectory, torrentsDir);

      File torrentFile = new File(torrentsDir, srcFile.getName() + ".torrent");
      assertTrue(torrentFile.exists());
      allTorrents.add(torrentFile);

      File linkFile = new File(storageDirectory, srcFile.getName() + ".link");
      assertTrue(linkFile.exists());
      allLinks.add(linkFile);

      filesQueue.add(srcFile.getName());
      if (filesQueue.size() > 3){
        filesQueue.poll();
      }
      new WaitFor(15*1000){

        @Override
        protected boolean condition() {
          final Collection<SharedTorrent> sharedTorrents = myDirectorySeeder.getSharedTorrents();
          if (sharedTorrents.size() <= 3){
            for (SharedTorrent torrent : sharedTorrents) {
              if (torrent.getName().equals(srcFile.getName()))
                return true;
            }
          }
          return false;
        }
      };
      Collection<String> filesFromTorrents = new ArrayList<String>();
      for (SharedTorrent torrent : myDirectorySeeder.getSharedTorrents()) {
        filesFromTorrents.add(torrent.getName());
      }
      // checking currently seeded torrents
      assertEquals(filesQueue.size(), filesFromTorrents.size());
      assertContains(filesQueue, filesFromTorrents.toArray(new String[filesFromTorrents.size()]));

      // checking removed ones;
      assertThat(allArtifacts, new Constraint() {
        public boolean eval(Object o) {
          for (File artifact : (List<File>)o) {
            if (!artifact.exists()){
              return false;
            }
          }
          return true;
        }

        public StringBuffer describeTo(StringBuffer buffer) {
          return null;
        }
      });
      assertThat(allLinks, new Constraint() {
        public boolean eval(Object o) {
          for (File link : (List<File>)o) {
            if (link.exists() != filesQueue.contains(link.getName().replace(".link", ""))){
              return false;
            }
          }
          return true;
        }

        public StringBuffer describeTo(StringBuffer buffer) {
          return null;
        }
      });
      assertThat(allTorrents, new Constraint() {
        public boolean eval(Object o) {
          for (File link : (List<File>)o) {
            if (link.exists() != filesQueue.contains(link.getName().replace(".torrent", ""))){
              return false;
            }
          }
          return true;
        }

        public StringBuffer describeTo(StringBuffer buffer) {
          return null;
        }
      });

      Thread.sleep(2*1000); // wait 2 seconds, because we need to have files timestamps different
    }

    assertEquals(3, myDirectorySeeder.getNumberOfSeededTorrents());

  }

  @AfterMethod
  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    myDirectorySeeder.stopSeeder();
    myTracker.stop();
    myTempFiles.cleanup();
  }

  private File createTorrentFromFile(File srcFile, File torrentDir) throws InterruptedException, NoSuchAlgorithmException, IOException {
    File torrentFile = new File(torrentDir, srcFile.getName() + ".torrent");
    final Torrent torrent = Torrent.create(srcFile, URI.create("http://localhost:6969"), "Test");
    torrent.save(torrentFile);
    return torrentFile;
  }

}
