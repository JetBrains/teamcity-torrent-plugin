package jetbrains.buildServer.artifactsMirror;

import com.intellij.openapi.diagnostic.Logger;
import com.turn.ttorrent.common.Torrent;
import jetbrains.buildServer.agent.AgentLifeCycleAdapter;
import jetbrains.buildServer.agent.AgentLifeCycleListener;
import jetbrains.buildServer.agent.BuildAgent;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.artifactsMirror.torrent.TorrentSeeder;
import jetbrains.buildServer.artifactsMirror.torrent.TorrentUtil;
import jetbrains.buildServer.configuration.ChangeListener;
import jetbrains.buildServer.configuration.FilesWatcher;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * User: Victory.Bedrosova
 * Date: 10/9/12
 * Time: 5:12 PM
 */
public class AgentTorrentsManager extends AgentLifeCycleAdapter implements FilesWatcher.WatchedFilesProvider, ChangeListener {
  private final static Logger LOG = Logger.getInstance(AgentTorrentsManager.class.getName());

  private static final String TORRENT_FOLDER_NAME = "torrents";
  private static final String LINK_FILE_SUFFIX = ".link";
  private static final String TORRENT_TRACKER_PARAM_NAME = "teamcity.torrent.tracker.url";

  @NotNull
  private final File myTorrentStorage;
  @NotNull
  private URI myTorrentTrackerUri;
  @NotNull
  private final TorrentSeeder myTorrentSeeder = new TorrentSeeder();
  @NotNull
  private FilesWatcher myFilesWatcher;


  public AgentTorrentsManager(@NotNull BuildAgentConfiguration agentConfiguration,
                              @NotNull EventDispatcher<AgentLifeCycleListener> eventDispatcher) throws Exception {
    eventDispatcher.addListener(this);
    myTorrentStorage = agentConfiguration.getCacheDirectory(TORRENT_FOLDER_NAME);
  }

  @Override
  public void agentStarted(@NotNull BuildAgent agent) {

    try {
      myTorrentTrackerUri = new URI(agent.getConfiguration().getConfigurationParameters().get(TORRENT_TRACKER_PARAM_NAME));

      myTorrentSeeder.start(InetAddress.getByName(agent.getConfiguration().getOwnAddress()));

      myFilesWatcher = new FilesWatcher(this);
      myFilesWatcher.setSleepingPeriod(5000L);
      myFilesWatcher.registerListener(this);
      myFilesWatcher.start();
    } catch (Exception e) {
      LOG.warn(e.toString(), e);
    }
  }

  @Override
  public void agentShutdown() {
    myFilesWatcher.stop();
    myTorrentSeeder.stop();
    clearTorrentFiles();
  }

  public File[] getWatchedFiles() throws IOException {
    final List<File> result = new ArrayList<File>();
    for (File linkFile : getLinkFiles()) {
      final File srcFile = getSrcFile(linkFile);
      if (nonNull(srcFile)) {
        result.add(srcFile);
      }
    }
    return result.toArray(new File[result.size()]);
  }

  public void changeOccured(String requestor) {
    for (File srcFile : CollectionsUtil.join(myFilesWatcher.getRemovedFiles(), myFilesWatcher.getModifiedFiles())) {
      doStopSeeding(srcFile);
    }
    for (File srcFile : CollectionsUtil.join(myFilesWatcher.getNewFiles(), myFilesWatcher.getModifiedFiles())) {
      try {
        doSeed(srcFile);
      } catch (IOException e) {
        LOG.warn(e.toString(), e);
        doStopSeeding(srcFile);
      }
    }
  }

  public void seedTorrent(@NotNull File srcFile) throws IOException {
    saveLink(srcFile, getLinkFile(createTorrent(srcFile)));
  }

  private void doSeed(@NotNull File srcFile) throws IOException {
    final Torrent torrent = createTorrent(srcFile);
    final File torrentFile = getTorrentFile(torrent);
    torrent.save(torrentFile);
    myTorrentSeeder.seedTorrent(torrentFile, srcFile);
  }

  private void doStopSeeding(@NotNull File srcFile) {
    final String prevHash = getPreviousHash(srcFile);

    if (prevHash == null) {
      //report
      return;
    }

    final File torrentFile = getTorrentFile(prevHash);
    myTorrentSeeder.stopSeeding(torrentFile);
    FileUtil.delete(torrentFile);
    FileUtil.delete(getLinkFile(prevHash));
  }

  @NotNull
  private List<File> getFiles(@NotNull final String suffix) {
    final File[] files = myTorrentStorage.listFiles(new FilenameFilter() {
      public boolean accept(File dir, String name) {
        return name.endsWith(suffix);
      }
    });
    return files == null ? Collections.<File>emptyList() : Arrays.asList(files);
  }

  @NotNull
  private List<File> getLinkFiles() {
    return getFiles(LINK_FILE_SUFFIX);
  }

  @NotNull
  private List<File> getTorrentFiles() {
    return getFiles(TorrentUtil.TORRENT_FILE_SUFFIX);
  }

  @Nullable
  private File getSrcFile(@NotNull File linkFile) {
    try {
      return new File(FileUtil.readText(linkFile));
    } catch (IOException e) {
      LOG.warn(e.toString());
      return null;
    }
  }

  private boolean nonNull(@Nullable Object o) {
    return o != null;
  }

  @Nullable
  private String getPreviousHash(@NotNull File srcFile) {
    for (File linkFile : getLinkFiles()) {
      final File file = getSrcFile(linkFile);
      if (srcFile.equals(file)) return linkFile.getName().replace(LINK_FILE_SUFFIX, "");
    }
    return null;
  }

  @NotNull
  private Torrent createTorrent(@NotNull File srcFile) {
    try {
      return Torrent.create(srcFile, myTorrentTrackerUri, "TeamCity agent");
    } catch (Exception e) {
      throw new RuntimeException(e.toString(), e);
    }
  }

  @NotNull
  private File getTorrentFile(@NotNull Torrent torrent) {
    return getTorrentFile(torrent.getHexInfoHash());
  }

  @NotNull
  private File getTorrentFile(@NotNull String hash) {
    return new File(myTorrentStorage, hash + TorrentUtil.TORRENT_FILE_SUFFIX);
  }

  @NotNull
  private File getLinkFile(@NotNull Torrent torrent) {
    return new File(myTorrentStorage, torrent.getHexInfoHash() + LINK_FILE_SUFFIX);
  }

  @NotNull
  private File getLinkFile(@NotNull String hash) {
    return new File(myTorrentStorage, hash + LINK_FILE_SUFFIX);
  }

  private void saveLink(@NotNull File srcFile, @NotNull File linkFile) throws IOException {
    FileUtil.writeFile(linkFile, srcFile.getAbsolutePath(), "UTF-8");
  }

  private void clearTorrentFiles() {
    for (File torrentFile : getTorrentFiles()) {
      FileUtil.delete(torrentFile);
    }
  }
}
