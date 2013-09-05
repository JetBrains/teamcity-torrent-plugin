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
import jetbrains.buildServer.artifactsMirror.torrent.TeamcityTorrentClient;
import jetbrains.buildServer.configuration.ChangeListener;
import jetbrains.buildServer.configuration.FilesWatcher;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class TorrentsDirectorySeeder {

  public static final String TORRENTS_DIT_PATH = ".teamcity/torrents";

  public static final int TORRENTS_STORAGE_VERSION=2;
  public static final String TORRENTS_STORAGE_VERSION_FILE = "storage.version";

  @NotNull
  private final File myTorrentStorage;


  @NotNull
  private final TeamcityTorrentClient myTorrentSeeder = new TeamcityTorrentClient();
  private final FilesWatcher myNewLinksWatcher;
  private volatile boolean myStopped = true;
  private volatile int myMaxTorrentsToSeed = -1; // no limit by default
  private volatile int myFileSizeThresholdMb=10; //default value

  public TorrentsDirectorySeeder(@NotNull File torrentStorage) {
    myTorrentStorage = torrentStorage;
    checkTorrentsStorageVersion();

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
    try {
      File torrentFile = FileLink.getTorrentFile(removedLink);
      if (torrentFile == null || !torrentFile.exists()) {
        return;
      }
      stopSeedingTorrent(torrentFile);
      FileUtil.delete(torrentFile);
      cleanupBrokenLink(removedLink);
    } catch (IOException e) {
      Loggers.AGENT.warn("Exception during new link removing", e);
    }
 }

  private void cleanupBrokenLink(@NotNull File linkFile) {
    File linkDir = linkFile.getParentFile();
    FileUtil.delete(linkFile);

    if (linkDir.equals(myTorrentStorage)) return;
    FileUtil.deleteIfEmpty(linkDir);
  }

  private void startSeeding(File linkFile){
    try {
      File torrentFile = FileLink.getTorrentFile(linkFile);
      File targetFile = FileLink.getTargetFile(linkFile);
      if (torrentFile.exists() && targetFile.exists()){
        getTorrentSeeder().seedTorrent(torrentFile, targetFile);
      }
    } catch (IOException e) {
      e.printStackTrace();
    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
    }
  }

  public void processChangedLink(@NotNull File changedLink) {
    //do nothing
    try {
      File torrentFile = FileLink.getTorrentFile(changedLink);

      File targetFile = FileLink.getTargetFile(changedLink);
      if (!targetFile.isFile()) {
        if (torrentFile != null && torrentFile.exists()) {
          stopSeedingTorrent(torrentFile);
          FileUtil.delete(torrentFile);
        }
        cleanupBrokenLink(changedLink);
      }
    } catch (IOException e) {
      Loggers.AGENT.warn("Exception during new link processing: " + e.toString(), e);
    }
  }

  private void stopSeedingTorrent(@NotNull File torrentFile) {
    myTorrentSeeder.stopSeeding(torrentFile);
  }


  public boolean isSeeding(@NotNull File torrentFile) throws IOException, NoSuchAlgorithmException {
    return myTorrentSeeder.isSeeding(torrentFile);
  }

  public void start(@NotNull InetAddress address, @Nullable final URI defaultTrackerURI) {
    start(address, defaultTrackerURI, 30);
  }

  public void start(@NotNull InetAddress address, @Nullable final URI defaultTrackerURI, int directoryScanIntervalSeconds) {
    myTorrentSeeder.start(address, defaultTrackerURI);

    // initialization: scan all existing links and start seeding them
    for (File linkFile: findAllLinks(-1)) {
      startSeeding(linkFile);
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
    myTorrentSeeder.setMaxTorrentsToSeed(myMaxTorrentsToSeed);
  }

  public int getFileSizeThresholdMb() {
    return myFileSizeThresholdMb;
  }

  public void setFileSizeThresholdMb(int fileSizeThresholdMb) {
    myFileSizeThresholdMb = fileSizeThresholdMb;
  }

  public boolean shouldCreateTorrentFileFor(File srcFile) {
    return srcFile.length() >= myFileSizeThresholdMb * 1024 * 1024;
  }

  @NotNull
  public TeamcityTorrentClient getTorrentSeeder() {
    return myTorrentSeeder;
  }

  //for tests only
  FilesWatcher getNewLinksWatcher() {
    return myNewLinksWatcher;
  }

  private void checkTorrentsStorageVersion(){

    final File versionFile = new File(myTorrentStorage, TORRENTS_STORAGE_VERSION_FILE);
    try {
      final String s = FileUtil.readText(versionFile);
      if (Integer.parseInt(s) == TORRENTS_STORAGE_VERSION)
        return;
      else {
        Loggers.AGENT.warn("Torrent storage version " + s + " doesn't match expected " + TORRENTS_STORAGE_VERSION);
        Loggers.AGENT.warn("Torrent storage will be cleaned");
      }
    } catch (Exception e) {
      Loggers.AGENT.warn("IOE during reading storage version. Will clean the storage", e);
    }
    final String[] names = myTorrentStorage.list();
    for (String name : names) {
      if (name.equals(TORRENTS_STORAGE_VERSION_FILE))
        continue;
      FileUtil.delete(new File(myTorrentStorage, name));
    }

    try {
      FileUtil.writeFileAndReportErrors(versionFile, String.valueOf(TORRENTS_STORAGE_VERSION));
    } catch (IOException e) {
      Loggers.AGENT.warn("Unable to write versions file. All caches will be cleaned on restart");
    }
  }
}

