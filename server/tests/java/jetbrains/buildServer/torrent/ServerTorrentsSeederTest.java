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
import jetbrains.buildServer.agentServer.Server;
import jetbrains.buildServer.serverSide.BuildServerListener;
import jetbrains.buildServer.serverSide.BuildServerListenerEventDispatcher;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.ServerSettings;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
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
public class ServerTorrentsSeederTest extends ServerTorrentsSeederTestCase {

  public void new_file_seedeed_old_removed() throws IOException, InterruptedException {
    System.setProperty(TorrentConfiguration.MAX_NUMBER_OF_SEEDED_TORRENTS, "3");
    System.setProperty(TorrentConfiguration.ANNOUNCE_URL, "http://localhost:6969/announce");
    System.setProperty(TorrentConfiguration.FILE_SIZE_THRESHOLD, "1");
    System.setProperty(TorrentConfiguration.TRACKER_ENABLED, "true");
    System.setProperty(TorrentConfiguration.DOWNLOAD_ENABLED, "true");
    myConfigurator.getConfigurationWatcher().checkForModifications();
    myDispatcher.getMulticaster().serverStartup();

    final File artifactsDir = createTempDir();
    final File torrentsDir = createTempDir();

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

      myTorrentsSeeder.processArtifactInternal(new DummyBuildArtifactAdapter() {
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
      }, artifactsDir, torrentsDir);

      File torrentFile = new File(torrentsDir, srcFile.getName() + ".torrent");
      assertTrue(torrentFile.exists());
      allTorrents.add(torrentFile);

      filesQueue.add(srcFile.getName());
      if (filesQueue.size() > 3){
        filesQueue.poll();
      }
      new WaitFor(5*1000){

        @Override
        protected boolean condition() {
          final Collection<SharedTorrent> sharedTorrents = myTorrentsSeeder.getSharedTorrents();
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
      assertTrue(myTorrentsSeeder.getSharedTorrents().size() <= 3);
      Collection<String> filesFromTorrents = new ArrayList<String>();
      for (SharedTorrent torrent : myTorrentsSeeder.getSharedTorrents()) {
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

    assertEquals(3, myTorrentsSeeder.getNumberOfSeededTorrents());
  }

  private File createTorrentFromFile(File srcFile, File torrentDir) throws InterruptedException, NoSuchAlgorithmException, IOException {
    File torrentFile = new File(torrentDir, srcFile.getName() + ".torrent");
    final Torrent torrent = Torrent.create(srcFile, URI.create("http://localhost:6969"), "Test");
    torrent.save(torrentFile);
    return torrentFile;
  }

}
