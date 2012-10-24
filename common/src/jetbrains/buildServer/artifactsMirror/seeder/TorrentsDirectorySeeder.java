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

import jetbrains.buildServer.artifactsMirror.torrent.TorrentSeeder;
import jetbrains.buildServer.artifactsMirror.torrent.TorrentUtil;
import jetbrains.buildServer.configuration.ChangeListener;
import jetbrains.buildServer.configuration.FilesWatcher;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.InetAddress;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class TorrentsDirectorySeeder {
  @NotNull
  private final File myTorrentStorage;

  @NotNull
  private final TorrentSeeder myTorrentSeeder = new TorrentSeeder();
  private final FilesWatcher myNewLinksWatcher;
  private final TorrentFileFactory myTorrentFactory;
  private volatile boolean myStopped = true;
  private volatile int myMaxTorrentsToSeed = -1; // no limit by default

  public TorrentsDirectorySeeder(@NotNull File torrentStorage, @NotNull TorrentFileFactory torrentFileFactory) {
    myTorrentStorage = torrentStorage;
    myTorrentFactory = torrentFileFactory;
    myNewLinksWatcher = new FilesWatcher(new FilesWatcher.WatchedFilesProvider() {
      public File[] getWatchedFiles() throws IOException {
        Collection<File> links = findAllLinks(myMaxTorrentsToSeed);
        return links.toArray(new File[links.size()]);
      }
    });
    myNewLinksWatcher.registerListener(new ChangeListener() {
      public void changeOccured(String requestor) {
        for (File link : CollectionsUtil.join(myNewLinksWatcher.getNewFiles(), myNewLinksWatcher.getModifiedFiles())) {
          processChangedLink(link);
        }
        for (File link : myNewLinksWatcher.getRemovedFiles()) {
          processRemovedLink(link);
        }
      }
    });
  }

  @NotNull
  public File getStorageDirectory() {
    return myTorrentStorage;
  }

  @NotNull
  private Collection<File> findAllLinks(int maxLinksNum) {
    Collection<File> links = FileUtil.findFiles(new FileFilter() {
      public boolean accept(File file) {
        if (!FileLink.isLink(file)) return false;

        File targetFile;
        try {
          targetFile = FileLink.getTargetFile(file);
          return targetFile.isFile();
        } catch (IOException e) {
          return false; // cannot read content of the link
        }
      }
    }, myTorrentStorage);

    if (maxLinksNum < 0 || links.size() <= maxLinksNum) {
      return links;
    }

    List<File> sorted = new ArrayList<File>(links);
    Collections.sort(sorted, new Comparator<File>() {
      public int compare(File o1, File o2) {
        return (int)(o2.lastModified() - o1.lastModified());
      }
    });

    return sorted.subList(0, maxLinksNum);
  }

  private void processRemovedLink(@NotNull File removedLink) {
    File torrentFile = getTorrentFileByLinkFile(removedLink);
    if (!torrentFile.exists()) {
      return;
    }

    stopSeedingTorrent(torrentFile);
    FileUtil.delete(torrentFile);
    cleanupBrokenLink(removedLink);
  }

  private void cleanupBrokenLink(@NotNull File linkFile) {
    File linkDir = linkFile.getParentFile();
    FileUtil.delete(linkFile);

    if (linkDir.equals(myTorrentStorage)) return;
    FileUtil.deleteIfEmpty(linkDir);
  }

  private void processChangedLink(@NotNull File changedLink) {
    try {
      File torrentFile = getTorrentFileByLinkFile(changedLink);
      if (torrentFile.exists()) {
        stopSeedingTorrent(torrentFile);
      }

      File targetFile = FileLink.getTargetFile(changedLink);
      File linkDir = changedLink.getParentFile();
      if (targetFile.isFile()) {
        torrentFile = myTorrentFactory.createTorrentFile(targetFile, linkDir);
        if (torrentFile != null) {
          myTorrentSeeder.seedTorrent(torrentFile, targetFile);
        }
      } else {
        cleanupBrokenLink(changedLink);
      }
    } catch (IOException e) {
      Loggers.AGENT.warn("Exception during new link processing: " + e.toString(), e);
    }
  }

  private void stopSeedingTorrent(@NotNull File torrentFile) {
    myTorrentSeeder.stopSeeding(torrentFile);
  }

  public static File getTorrentFileByLinkFile(@NotNull File linkFile) {
    String linkFileName = linkFile.getName();
    String name = linkFileName.substring(0, linkFileName.length() - FileLink.LINK_FILE_SUFFIX.length());
    return new File(linkFile.getParentFile(), name + TorrentUtil.TORRENT_FILE_SUFFIX);
  }

  public boolean isSeeding(@NotNull File torrentFile) throws IOException, NoSuchAlgorithmException {
    return myTorrentSeeder.isSeeding(torrentFile);
  }

  public void start(@NotNull InetAddress address) {
    start(address, 30);
  }

  public void start(@NotNull InetAddress address, int directoryScanIntervalSeconds) {
    myTorrentSeeder.start(address);

    // initialization: scan all existing links and start seeding them
    for (File linkFile: findAllLinks(-1)) {
      processChangedLink(linkFile);
    }

    myNewLinksWatcher.setSleepingPeriod(directoryScanIntervalSeconds * 1000);
    myNewLinksWatcher.start();

    myStopped = false;
  }

  public void stop() {
    myStopped = true;
    myNewLinksWatcher.stop();
    myTorrentSeeder.stop();
  }

  public boolean isStopped() {
    return myStopped;
  }

  public int getNumberOfSeededTorrents() {
    return myTorrentSeeder.getNumberOfSeededTorrents();
  }

  public int getMaxTorrentsToSeed() {
    return myMaxTorrentsToSeed;
  }

  public void setMaxTorrentsToSeed(int maxTorrentsToSeed) {
    myMaxTorrentsToSeed = maxTorrentsToSeed;
  }
}

