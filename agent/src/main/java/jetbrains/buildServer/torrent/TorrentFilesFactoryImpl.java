/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.turn.ttorrent.common.TorrentCreator;
import com.turn.ttorrent.common.TorrentMetadata;
import jetbrains.buildServer.agent.AgentIdleTasks;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.agent.InterruptState;
import jetbrains.buildServer.torrent.torrent.TorrentUtil;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Random;
import java.util.Set;

/**
 * The purpose of this class is to create torrent files on disk and maintain them.
 * This class also cleanups torrent files automatically if torrent seeder is not seeding them anymore.
 */
public class TorrentFilesFactoryImpl implements TorrentFilesFactory {
  private final static Logger LOG = Logger.getInstance(TorrentFilesFactoryImpl.class.getName());

  private final File myTorrentFilesDir;
  private final Random myRandom;
  private final TorrentConfiguration myConfiguration;

  public TorrentFilesFactoryImpl(@NotNull BuildAgentConfiguration agentConfiguration,
                                 @NotNull TorrentConfiguration configuration,
                                 @NotNull AgentIdleTasks agentIdleTasks,
                                 @NotNull final AgentTorrentsSeeder torrentsSeeder) {
    myConfiguration = configuration;
    myTorrentFilesDir = agentConfiguration.getCacheDirectory(Constants.TORRENTS_DIRNAME);
    myRandom = new Random(System.currentTimeMillis());
    agentIdleTasks.addRecurringTask(new AgentIdleTasks.Task() {
      @NotNull
      public String getName() {
        return "Torrent files cleaner";
      }

      public void execute(@NotNull InterruptState interruptState) {
        Set<File> registeredTorrentFiles = torrentsSeeder.getRegisteredTorrentFiles();

        File[] dirs = myTorrentFilesDir.listFiles();
        if (dirs != null) {
          for (File dir: dirs) {
            if (interruptState.isInterrupted()) return;
            if (!dir.isDirectory()) continue;
            File[] files = dir.listFiles();
            if (files != null) {
              for (File f: files) {
                if (interruptState.isInterrupted()) return;
                if (registeredTorrentFiles.contains(f)) continue;
                FileUtil.delete(f);
              }
            }
          }
        }
      }
    });
  }

  @Nullable @Override
  public File createTorrentFile(@NotNull File srcFile) {
    final String announceUrl = myConfiguration.getAnnounceUrl();
    if (announceUrl == null) return null;

    try {
      File torrentFile = getTorrentFile();
      TorrentMetadata torrent = TorrentCreator.create(srcFile, URI.create(announceUrl), "TeamCity Torrent Plugin");
      TorrentUtil.saveTorrentToFile(torrent, torrentFile);
      return torrentFile;
    } catch (Exception e) {
      LOG.warnAndDebugDetails("Failed to create torrent for source file: " + srcFile.getAbsolutePath(), e);
    }

    return null;
  }

  @NotNull @Override
  public File getTorrentFile() throws IOException {
    long hash = myRandom.nextInt(10);
    long dirIdx = hash % 10;
    File baseDir = new File(myTorrentFilesDir, String.valueOf(dirIdx));
    baseDir.mkdirs();

    for (int i=0; i<100; i++) {
      String fileName = myRandom.nextLong() + ".torrent";
      final File file = new File(baseDir, fileName);
      if (!file.isFile()) {
        return file;
      }
    }

    throw new IOException("Failed to generate name for torrent file. Gave up after 100 attempts");
  }
}
