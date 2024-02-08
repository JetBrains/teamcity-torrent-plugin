

package jetbrains.buildServer.torrent.seeder;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.RecentEntriesCache;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class TorrentFilesDB {
  private final static Logger LOG = Logger.getInstance(TorrentsSeeder.class.getName());

  private final static String SEPARATOR = " || ";
  public static final String ENCODING = "UTF-8";
  private final AtomicReference<Map<FileInfo, FileInfo>> myFile2TorrentMap = new AtomicReference<Map<FileInfo, FileInfo>>();
  private final File myTorrentsDbFile;
  private final PathConverter myPathConverter;
  private final CacheListener myCacheListener;
  private volatile boolean myIsDBChanged = false;

  public TorrentFilesDB(@NotNull File torrentsDbPath, int maxTorrents, @Nullable PathConverter pathConverter, @Nullable CacheListener cacheListener) {
    myTorrentsDbFile = torrentsDbPath;
    myPathConverter = pathConverter == null ? new SimplePathConverter() : pathConverter;
    myCacheListener = cacheListener;
    myFile2TorrentMap.set(createCache(maxTorrents));
    try {
      loadDb();
    } catch (IOException e) {
      LOG.warn("Failed to load data from torrents database, error: " + e.toString(), e);
    }
  }

  public void setMaxTorrents(int maxTorrents) {
    synchronized (myFile2TorrentMap) {
      Map<FileInfo, FileInfo> curCache = myFile2TorrentMap.get();
      Map<FileInfo, FileInfo> newCache = createCache(maxTorrents);
      for (FileInfo srcFile: getSortedKeys()) {
        final FileInfo torrentFile = curCache.get(srcFile);
        if (torrentFile == null) continue;
        newCache.put(srcFile, torrentFile);
      }

      myFile2TorrentMap.set(newCache);
    }
  }

  public void addFileAndTorrent(@NotNull File srcFile, @NotNull File torrentFile) {
    String srcPath = myPathConverter.convertToPath(srcFile);
    String torrentPath = myPathConverter.convertToPath(torrentFile);
    synchronized (myFile2TorrentMap) {
      myIsDBChanged = true;
      myFile2TorrentMap.get().put(new FileInfo(srcPath), new FileInfo(torrentPath));
    }
  }

  @NotNull
  public List<File> cleanupBrokenFiles() {
    List<File> brokenTorrentFiles = new ArrayList<File>();

    Map<FileInfo, File> toRemove = new HashMap<FileInfo, File>();

    Map<FileInfo, FileInfo> cacheCopy;
    synchronized (myFile2TorrentMap) {
      final Map<FileInfo, FileInfo> cache = myFile2TorrentMap.get();
      cacheCopy = new HashMap<FileInfo, FileInfo>(cache);
    }

    for (Map.Entry<FileInfo, FileInfo> entry : cacheCopy.entrySet()) {
      final FileInfo torrentInfo = cacheCopy.get(entry.getKey());

      File srcFile = entry.getKey().getFile();
      File torrentFile = torrentInfo != null ? torrentInfo.getFile() : null;

      if (torrentFile == null || !srcFile.isFile() || !torrentFile.isFile()) {
        toRemove.put(entry.getKey(), torrentFile);
        brokenTorrentFiles.add(torrentFile);
      }
    }

    synchronized (myFile2TorrentMap) {
      final Map<FileInfo, FileInfo> cache = myFile2TorrentMap.get();
      if (!toRemove.isEmpty()) {
        myIsDBChanged = true;
      }
      cache.keySet().removeAll(toRemove.keySet());
    }


    for (Map.Entry<FileInfo, File> e: toRemove.entrySet()) {
      notifyOnRemove(new AbstractMap.SimpleEntry<File, File>(e.getKey().getFile(), e.getValue()));
    }

    return brokenTorrentFiles;
  }

  @NotNull
  public Map<File, File> getFileAndTorrentMap() {
    Map<File, File> res = new HashMap<File, File>();

    synchronized (myFile2TorrentMap) {
      for (Map.Entry<FileInfo, FileInfo> entry : myFile2TorrentMap.get().entrySet()) {
        File src = entry.getKey().getFile();
        File torrent = entry.getValue().getFile();

        res.put(src, torrent);
      }
    }

    return res;
  }

  // flushes torrents database on disk if in memory db has changes
  public void flush() throws IOException {

    if (!myIsDBChanged) return;

    File parentFile = myTorrentsDbFile.getParentFile();
    if (!parentFile.isDirectory() && !parentFile.mkdirs()) {
      throw new IOException("Failed to create directory for torrent database file: " + myTorrentsDbFile.getAbsolutePath());
    }

    PrintWriter writer = null;
    try {
      OutputStreamWriter osWriter = new OutputStreamWriter(new FileOutputStream(myTorrentsDbFile), ENCODING);
      writer = new PrintWriter(osWriter);

      List<FileInfo> sorted = getSortedKeys();

      synchronized (myFile2TorrentMap) {
        for (FileInfo srcFile : sorted) {
          final FileInfo torrentFile = myFile2TorrentMap.get().get(srcFile);
          if (torrentFile == null) continue;

          String srcPath = srcFile.myPath;
          String torrentPath = torrentFile.myPath;

          writer.print(srcPath);
          writer.print(SEPARATOR);
          writer.print(torrentPath);
          writer.println();
        }
        myIsDBChanged = false;
      }
    } finally {
      FileUtil.close(writer);
    }
  }

  @NotNull
  private List<FileInfo> getSortedKeys() {
    List<FileInfo> sorted;
    synchronized (myFile2TorrentMap) {
      sorted = new ArrayList<FileInfo>(myFile2TorrentMap.get().keySet());
    }
    // cache lastModifiedTime on first read to avoid IllegalArgumentException: Comparison method violates its general contract (TW-44581)
    final Map<FileInfo, Long> lastModifiedCache = new HashMap<FileInfo, Long>();
    Collections.sort(sorted, new Comparator<FileInfo>() {
      public int compare(FileInfo o1, FileInfo o2) {
        // from lowest to highest
        if (lastModifiedCache.get(o1) == null){
          lastModifiedCache.put(o1, o1.lastModified());
        }
        if (lastModifiedCache.get(o2) == null){
          lastModifiedCache.put(o2, o2.lastModified());
        }
        final int compareTime = lastModifiedCache.get(o1).compareTo(lastModifiedCache.get(o2));
        return compareTime != 0 ? compareTime : o1.getFile().getAbsolutePath().compareTo(o2.getFile().getAbsolutePath());
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
      int counter = 0;
      LOG.info("try to load torrents from " + myTorrentsDbFile.getAbsolutePath());
      while ((line = reader.readLine()) != null) {
        List<String> paths = StringUtil.split(line, SEPARATOR);
        if (paths.size() != 2) continue;

        File srcFile = myPathConverter.convertToFile(paths.get(0));
        File torrentFile = myPathConverter.convertToFile(paths.get(1));

        counter++;
        addFileAndTorrent(srcFile, torrentFile);
      }
      LOG.info("Loaded " + counter + " torrents");
    } catch (FileNotFoundException e) {
      // no database on disk
      LOG.info("torrents.db file is not found in " + myTorrentsDbFile.getParent());
    } finally {
      FileUtil.close(reader);
    }
  }

  public void removeSrcFile(@NotNull File srcFile) {
    String path = myPathConverter.convertToPath(srcFile);
    FileInfo removedTorrent;
    synchronized (myFile2TorrentMap) {
      removedTorrent = myFile2TorrentMap.get().remove(new FileInfo(path));
      if (removedTorrent != null) {
        myIsDBChanged = true;
      }
    }

    if (removedTorrent != null) {
      notifyOnRemove(new AbstractMap.SimpleEntry<File, File>(srcFile, removedTorrent.getFile()));
    }
  }

  private class FileInfo {
    public final String myPath;

    private FileInfo(@NotNull String path) {
      myPath = path;
    }

    @NotNull
    public File getFile() {
      return myPathConverter.convertToFile(myPath);
    }

    public long lastModified() {
      return myPathConverter.convertToFile(myPath).lastModified();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      FileInfo fileInfo = (FileInfo) o;

      return myPath.equals(fileInfo.myPath);
    }

    @Override
    public int hashCode() {
      return myPath.hashCode();
    }
  }

  private static class SimplePathConverter implements PathConverter {
    @NotNull
    public File convertToFile(@NotNull String path) {
      return new File(path);
    }

    @NotNull
    public String convertToPath(@NotNull File file) {
      return file.getAbsolutePath();
    }
  }

  public interface CacheListener {
    void onRemove(@NotNull Map.Entry<File, File> removedEntry);
  }

  @NotNull
  private Map<FileInfo, FileInfo> createCache(final int maxTorrents) {
    return new LinkedHashMap<FileInfo, FileInfo>(maxTorrents, 0.8f, true) {
      @Override
      protected boolean removeEldestEntry(final Map.Entry<FileInfo, FileInfo> eldest) {
        boolean remove = size() > maxTorrents;
        if (!remove) return false;
        notifyOnRemove(new SimpleEntry<File, File>(eldest.getKey().getFile(), eldest.getValue().getFile()));
        return true;
      }
    };
  }

  private void notifyOnRemove(@NotNull Map.Entry<File, File> removed) {
    if (myCacheListener != null) {
      myCacheListener.onRemove(removed);
    }
  }
}