package jetbrains.buildServer.artifactsMirror;

import com.intellij.openapi.diagnostic.Logger;
import com.turn.ttorrent.common.Torrent;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.artifactsMirror.torrent.TorrentSeeder;
import jetbrains.buildServer.artifactsMirror.torrent.TorrentUtil;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

/**
 * User: Victory.Bedrosova
 * Date: 10/9/12
 * Time: 5:12 PM
 */
public class AgentTorrentsManager extends AgentLifeCycleAdapter implements LinkWatcher.LinkWatcherListener {
  private final static Logger LOG = Logger.getInstance(AgentTorrentsManager.class.getName());

  private static final String TORRENT_FOLDER_NAME = "torrents";

  @NotNull
  private final File myTorrentStorage;
  @NotNull
  private final TrackerManager myTrackerManager;
  @NotNull
  private final TorrentSeeder myTorrentSeeder = new TorrentSeeder();
  @NotNull
  private final LinkWatcher myLinkWatcher;
  private URI myTrackerAnnounceUrl;
  private int myFileSizeThresholdMb;

  public AgentTorrentsManager(@NotNull BuildAgentConfiguration agentConfiguration,
                              @NotNull EventDispatcher<AgentLifeCycleListener> eventDispatcher,
                              @NotNull TrackerManager trackerManager) throws Exception {
    eventDispatcher.addListener(this);

    myTorrentStorage = agentConfiguration.getCacheDirectory(TORRENT_FOLDER_NAME);
    myTrackerManager = trackerManager;
    myLinkWatcher = new LinkWatcher(myTorrentStorage, this);
  }

  @Override
  public void agentStarted(@NotNull BuildAgent agent) {
    try {
      myTorrentSeeder.start(InetAddress.getByName(agent.getConfiguration().getOwnAddress()));
    } catch (UnknownHostException e) {
      LOG.warn(e.toString(), e);
    }
  }

  @Override
  public void buildStarted(@NotNull AgentRunningBuild runningBuild) {
    init();
  }

  @Override
  public void agentShutdown() {
    myLinkWatcher.stop();
    myTorrentSeeder.stop();
  }

  public void targetFileChanged(@NotNull File linkFile, @NotNull File targetFile) {
    final File torrentFile = getTorrentFile(linkFile);
    stopSeeding(torrentFile);
    FileUtil.delete(torrentFile);

    if (targetFile.isFile()) {
      seed(targetFile, torrentFile);
    } else {
      FileUtil.delete(linkFile);
    }
  }

  public void seedTorrent(@NotNull File srcFile) throws IOException {
    seedTorrent(srcFile, null);
  }

  public void seedTorrent(@NotNull File srcFile, @Nullable String srcPrefix) throws IOException {
    saveLink(srcFile, srcPrefix); // all following will happen when we detect .link file on disk
  }

  private void seed(@NotNull File srcFile, @NotNull File torrentFile) {
    TorrentUtil.createTorrent(srcFile, torrentFile, myTrackerAnnounceUrl);
    myTorrentSeeder.seedTorrent(torrentFile, srcFile);
  }

  private void stopSeeding(@NotNull File torrentFile) {
    try {
      final Torrent torrent = TorrentUtil.loadTorrent(torrentFile);
      if (torrent.isSeeder()) {
        myTorrentSeeder.stopSeeding(torrentFile);
      }
    } catch (IOException e) {
      // do nothing
    }
  }

  private void saveLink(@NotNull File srcFile, @Nullable String srcPrefix) throws IOException {
    new LinkFile(getRoot(srcPrefix), srcFile).save();
  }

  @NotNull
  private File getRoot(@Nullable String srcPrefix) {
    return srcPrefix == null ? myTorrentStorage : new File(myTorrentStorage, srcPrefix);
  }

  @NotNull
  private File getTorrentFile(@NotNull File linkFile) {
    return LinkFile.getTorrentFile(linkFile);
  }

  public int getFileSizeThresholdMb() {
    return myFileSizeThresholdMb;
  }

  private void init() {
    if (!myLinkWatcher.isStarted()) {
      try {
        myTrackerAnnounceUrl = new URI(myTrackerManager.getAnnounceUrl());
      } catch (URISyntaxException e) {
        LOG.warn(e.toString(), e);
      }
      myFileSizeThresholdMb = myTrackerManager.getFileSizeThresholdMb();

      myLinkWatcher.start();
    }
  }
}
