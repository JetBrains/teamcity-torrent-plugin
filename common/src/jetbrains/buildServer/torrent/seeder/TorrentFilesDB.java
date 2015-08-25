/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.RecentEntriesCache;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class TorrentFilesDB {
  private final static Logger LOG = Logger.getInstance(TorrentsDirectorySeeder.class.getName());

  private final static String SEPARATOR = " :: ";
  public static final String ENCODING = "UTF-8";
  private final AtomicReference<RecentEntriesCache<FileInfo, FileInfo>> myFile2TorrentMap = new AtomicReference<RecentEntriesCache<FileInfo, FileInfo>>();
  private final File myTorrentsDbFile;

  public TorrentFilesDB(@NotNull File torrentsDbPath, int maxTorrents) {
    myTorrentsDbFile = torrentsDbPath;
    myFile2TorrentMap.set(new RecentEntriesCache<FileInfo, FileInfo>(maxTorrents));
    try {
      loadDb();
    } catch (IOException e) {
      LOG.warn("Failed to load data from torrents database, error: " + e.toString(), e);
    }
  }

  public void setMaxTorrents(int maxTorrents) {
    RecentEntriesCache<FileInfo, FileInfo> curCache = myFile2TorrentMap.get();
    RecentEntriesCache<FileInfo, FileInfo> newCache = new RecentEntriesCache<FileInfo, FileInfo>(maxTorrents);
    for (FileInfo srcFile: getSortedKeys()) {
      final FileInfo torrentFile = curCache.get(srcFile);
      if (torrentFile == null) continue;
      newCache.put(srcFile, torrentFile);
    }

    myFile2TorrentMap.compareAndSet(curCache, newCache);
  }

  public void addFileAndTorrent(@NotNull File srcFile, @NotNull File torrentFile) {
    myFile2TorrentMap.get().put(new FileInfo(srcFile), new FileInfo(torrentFile));
  }

  @NotNull
  public List<File> cleanupBrokenFiles() {
    List<File> brokenTorrentFiles = new ArrayList<File>();
    for (Map.Entry<File, File> entry: getFileAndTorrentMap().entrySet()) {
      if (entry.getKey().isFile() && entry.getValue().isFile()) continue;
      myFile2TorrentMap.get().remove(new FileInfo(entry.getKey()));
      brokenTorrentFiles.add(entry.getValue());
    }
    return brokenTorrentFiles;
  }

  @NotNull
  public Map<File, File> getFileAndTorrentMap() {
    Map<File, File> res = new HashMap<File, File>();

    for (FileInfo srcFile : myFile2TorrentMap.get().keySet()) {
      final FileInfo torrentFile = myFile2TorrentMap.get().get(srcFile);
      if (torrentFile == null) continue;
      res.put(srcFile.myFile, torrentFile.myFile);
    }
    return res;
  }

  // flushes torrents database on disk
  public void flush() throws IOException {
    File parentFile = myTorrentsDbFile.getParentFile();
    if (!parentFile.isDirectory() && !parentFile.mkdirs()) {
      throw new IOException("Failed to create directory for torrent database file: " + myTorrentsDbFile.getAbsolutePath());
    }

    PrintWriter writer = null;
    try {
      OutputStreamWriter osWriter = new OutputStreamWriter(new FileOutputStream(myTorrentsDbFile), ENCODING);
      writer = new PrintWriter(osWriter);

      List<FileInfo> sorted = getSortedKeys();

      for (FileInfo srcFile : sorted) {
        final FileInfo torrentFile = myFile2TorrentMap.get().get(srcFile);
        if (torrentFile == null) continue;
        writer.print(srcFile.myFile.getAbsolutePath());
        writer.print(SEPARATOR);
        writer.print(torrentFile.myFile.getAbsolutePath());
        writer.println();
      }
    } finally {
      FileUtil.close(writer);
    }
  }

  @NotNull
  private List<FileInfo> getSortedKeys() {
    List<FileInfo> sorted = new ArrayList<FileInfo>(myFile2TorrentMap.get().keySet());
    Collections.sort(sorted, new Comparator<FileInfo>() {
      public int compare(FileInfo o1, FileInfo o2) {
        // from lowest to highest
        return new Long(o1.myLastModified).compareTo(o2.myLastModified);
      }
    });
    return sorted;
  }

  private void loadDb() throws IOException {
    // expecting rows to be sorted from oldest to newest,
    // then if max torrents limit is decreased newest entries will remove oldest in the cache automatically
    BufferedReader reader = null;
    try {
      InputStreamReader isReader = new InputStreamReader(new FileInputStream(myTorrentsDbFile), ENCODING);
      reader = new BufferedReader(isReader);
      String line;
      while ((line = reader.readLine()) != null) {
        List<String> paths = StringUtil.split(line, SEPARATOR);
        if (paths.size() != 2) continue;
        File srcFile = new File(paths.get(0));
        File torrentFile = new File(paths.get(1));

        if (srcFile.isFile() && torrentFile.isFile()) {
          addFileAndTorrent(srcFile, torrentFile);
        }
      }
    } catch (FileNotFoundException e) {
      // no database on disk
    } finally {
      FileUtil.close(reader);
    }
  }

  public void removeSrcFile(File srcFile) {
    myFile2TorrentMap.get().remove(new FileInfo(srcFile));
  }

  private static class FileInfo {
    public final File myFile;
    public final long myLastModified;

    private FileInfo(@NotNull File file) {
      myFile = file;
      myLastModified = file.lastModified();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      FileInfo fileInfo = (FileInfo) o;

      return myFile.equals(fileInfo.myFile);

    }

    @Override
    public int hashCode() {
      return myFile.hashCode();
    }
  }
}
