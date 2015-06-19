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

package jetbrains.buildServer.torrent;

import com.intellij.util.WaitFor;
import com.turn.ttorrent.client.SharedTorrent;
import com.turn.ttorrent.common.Torrent;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.RootUrlHolder;
import jetbrains.buildServer.XmlRpcHandlerManager;
import jetbrains.buildServer.torrent.seeder.FileLink;
import jetbrains.buildServer.serverSide.BuildServerListener;
import jetbrains.buildServer.serverSide.BuildServerListenerEventDispatcher;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.serverSide.impl.*;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.auth.SecurityContextImpl;
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jmock.Expectations;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * @author Sergey.Pak
 *         Date: 9/5/13
 *         Time: 8:38 PM
 */
@Test
public class ServerTorrentsDirectorySeederTest extends BaseTestCase {

  private ServerTorrentsDirectorySeeder myDirectorySeeder;
  private TorrentConfigurator myConfigurator;
  private EventDispatcher<BuildServerListener> myDispatcher;


  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final Mockery m = new Mockery();

    final ServerPaths serverPaths = new ServerPaths(createTempDir().getAbsolutePath());
    final RootUrlHolder rootUrlHolder = m.mock(RootUrlHolder.class);
    m.checking(new Expectations(){{
      allowing(rootUrlHolder).getRootUrl(); will(returnValue("http://localhost:8111/"));
    }});

    myConfigurator = new TorrentConfigurator(serverPaths, rootUrlHolder, new XmlRpcHandlerManager() {
      public void addHandler(String handlerName, Object handler) {}
      public void addSessionHandler(String handlerName, Object handler) {}
    });
    myConfigurator.setTorrentEnabled(true);


    ExecutorServices services = new ExecutorServices() {
      private final ExecutorService executorService = Executors.newSingleThreadExecutor();
      @NotNull
      public ScheduledExecutorService getNormalExecutorService() {
        return new ScheduledThreadPoolExecutor(1);
      }

      @NotNull
      public ExecutorService getLowPriorityExecutorService() {
        return executorService;
      }
    };

    myDispatcher = new BuildServerListenerEventDispatcher(new SecurityContextImpl());


    myDirectorySeeder = new ServerTorrentsDirectorySeeder(serverPaths, myConfigurator, services, myDispatcher, 3);
  }

  public void max_number_of_seeded_torrents_on_startup() throws IOException, NoSuchAlgorithmException, InterruptedException {
    final String announceURIStr = "http://localhost:6969/announce";
    final URI announceURI = URI.create(announceURIStr);

    System.setProperty(TorrentConfiguration.MAX_NUMBER_OF_SEEDED_TORRENTS, "3");
    System.setProperty(TorrentConfiguration.ANNOUNCE_URL, announceURIStr);

    myConfigurator.getConfigurationWatcher().checkForModifications();

    final File artifactsDir = createTempDir();
    final File torrentsDir = createTempDir();
    final File storageDir = myDirectorySeeder.getTorrentsDirectorySeeder().getStorageDirectory();
    final List<File> torrentFilesList = new ArrayList<File>();
    final List<File> linkFilesList = new ArrayList<File>();
    final int fileSize = 11 * 1024 * 1024;
    for (int i=0; i<5; i++) {
      File tempFile = createTempFile(fileSize);
      // move to artifacts dir;
      final File srcFile = new File(artifactsDir, tempFile.getName());
      tempFile.renameTo(srcFile);
      tempFile = null;

      final Torrent torrent = Torrent.create(srcFile, announceURI, "Teamcity torrent plugin test");
      final File torrentFile = new File(torrentsDir, srcFile.getName() + ".torrent");
      torrent.save(torrentFile);
      torrentFilesList.add(torrentFile);
      linkFilesList.add(FileLink.createLink(srcFile, torrentFile, storageDir));

    }

    System.setProperty(TorrentConfiguration.TRACKER_ENABLED, "true");
    System.setProperty(TorrentConfiguration.SEEDER_ENABLED, "true");
    myConfigurator.getConfigurationWatcher().checkForModifications();
    myDispatcher.getMulticaster().serverStartup();
    new WaitFor(15*1000){

      @Override
      protected boolean condition() {
        return myDirectorySeeder.getNumberOfSeededTorrents() == 3;
      }
    };
    assertEquals(3, myDirectorySeeder.getNumberOfSeededTorrents());
    int linksCount = 0;
    for (File file : linkFilesList) {
      if (file.exists()){
        linksCount++;
      }
    }
    assertEquals(3, linksCount);

    int torrentsCount = 0;
    for (File file : torrentFilesList) {
      if (file.exists()){
        torrentsCount++;
      }
    }
    assertEquals(3, torrentsCount);

  }


  public void new_file_seedeed_old_removed() throws IOException, InterruptedException {
    System.setProperty(TorrentConfiguration.MAX_NUMBER_OF_SEEDED_TORRENTS, "3");
    System.setProperty(TorrentConfiguration.ANNOUNCE_URL, "http://localhost:6969/announce");
    System.setProperty(TorrentConfiguration.FILE_SIZE_THRESHOLD, "1");
    System.setProperty(TorrentConfiguration.TRACKER_ENABLED, "true");
    System.setProperty(TorrentConfiguration.SEEDER_ENABLED, "true");
    myConfigurator.getConfigurationWatcher().checkForModifications();
    myDispatcher.getMulticaster().serverStartup();

    final File artifactsDir = createTempDir();
    final File torrentsDir = createTempDir();
    final File storageDirectory = myDirectorySeeder.getTorrentsDirectorySeeder().getStorageDirectory();

    final int fileSize = 1 * 1024 * 1024;
    final Queue<String> filesQueue = new ArrayDeque<String>();
    final List<File> allArtifacts = new ArrayList<File>();
    final List<File> allLinks = new ArrayList<File>();
    final List<File> allTorrents = new ArrayList<File>();
    for (int i=0; i<5; i++) {
      File tempFile = createTempFile(fileSize);
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
      new WaitFor(5*1000){

        @Override
        protected boolean condition() {
          final Collection<SharedTorrent> sharedTorrents = myDirectorySeeder.getSharedTorrents();
          if (sharedTorrents.size() <= 3){
            for (SharedTorrent torrent : sharedTorrents) {
              if (torrent.getName().equals(srcFile.getName())) {
                return true;
              }
            }
          }
          return false;
        }
      };
      assertTrue(myDirectorySeeder.getSharedTorrents().size() <= 3);
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
  }

  private File createTorrentFromFile(File srcFile, File torrentDir) throws InterruptedException, NoSuchAlgorithmException, IOException {
    File torrentFile = new File(torrentDir, srcFile.getName() + ".torrent");
    final Torrent torrent = Torrent.create(srcFile, URI.create("http://localhost:6969"), "Test");
    torrent.save(torrentFile);
    return torrentFile;
  }

}
