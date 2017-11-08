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
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifact;
import org.jetbrains.annotations.NotNull;
import org.jmock.core.Constraint;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.*;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * @author Sergey.Pak
 *         Date: 9/5/13
 *         Time: 8:38 PM
 */
@Test
public class ServerTorrentsSeederTest extends ServerTorrentsSeederTestCase {
  private TempFiles myTempFiles;

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myTempFiles = new TempFiles();
  }


  public void new_file_seedeed_old_removed() throws IOException, InterruptedException, NoSuchAlgorithmException {
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
    final List<File> allTorrents = new ArrayList<File>();
    for (int i = 0; i < 5; i++) {
      // move to artifacts dir;
      final File srcFile = createTmpFileWithTS(artifactsDir, fileSize);
      allArtifacts.add(srcFile);

      File torrentFile = new File(torrentsDir, srcFile.getName() + ".torrent");
      assertFalse(torrentFile.exists());
      Torrent torrentMetaInfo = Torrent.create(srcFile, URI.create(""), "");
      torrentMetaInfo.save(torrentFile);

      BuildArtifact buildArtifact = new DummyBuildArtifactAdapter() {
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
      };

      new ArtifactProcessorImpl(torrentsDir.toPath(), artifactsDir, myTorrentsSeeder.getTorrentsSeeder(), myConfigurator)
              .processArtifacts(Collections.singletonList(buildArtifact));

      allTorrents.add(torrentFile);

      filesQueue.add(srcFile.getName());
      if (filesQueue.size() > 3) {
        filesQueue.poll();
      }
      new WaitFor(5 * 1000) {

        @Override
        protected boolean condition() {
          final Collection<SharedTorrent> sharedTorrents = myTorrentsSeeder.getSharedTorrents();
          if (sharedTorrents.size() <= 3) {
            for (SharedTorrent torrent : sharedTorrents) {
              if (torrent.getName().equals(srcFile.getName())) {
                return true;
              }
            }
          }
          return false;
        }
      }.assertCompleted("should have completed in 5 sec");
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
          for (File artifact : (List<File>) o) {
            if (!artifact.exists()) {
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
          for (File link : (List<File>) o) {
            if (link.exists() != filesQueue.contains(link.getName().replace(".torrent", ""))) {
              return false;
            }
          }
          return true;
        }

        public StringBuffer describeTo(StringBuffer buffer) {
          return null;
        }
      });
    }

    assertEquals(3, myTorrentsSeeder.getNumberOfSeededTorrents());
  }

  @NotNull
  private File createTmpFileWithTS(File dir, int size) throws IOException {
    File srcFile = new File(dir, String.format("%d-test.tmp", System.currentTimeMillis()));
    srcFile.createNewFile();
    myTempFiles.registerAsTempFile(srcFile);
    int bufLen = Math.min(8 * 1024, size);
    if (bufLen == 0) return srcFile;
    final OutputStream fos = new BufferedOutputStream(new FileOutputStream(srcFile));
    try {
      byte[] buf = new byte[bufLen];
      for (int i = 0; i < buf.length; i++) {
        buf[i] = (byte) Math.round(Math.random() * 128);
      }

      int numWritten = 0;
      for (int i = 0; i < size / buf.length; i++) {
        fos.write(buf);
        numWritten += buf.length;
      }

      if (size > numWritten) {
        fos.write(buf, 0, size - numWritten);
      }
    } finally {
      fos.close();
    }

    return srcFile;
  }

  @AfterMethod
  @Override
  protected void tearDown() throws Exception {
    myTempFiles.cleanup();
    super.tearDown();
  }
}
