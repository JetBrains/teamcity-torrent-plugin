package jetbrains.buildServer.artifactsMirror;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.artifactsMirror.seeder.LinkFile;
import jetbrains.buildServer.artifactsMirror.seeder.TorrentFileFactory;
import jetbrains.buildServer.artifactsMirror.seeder.TorrentsDirectorySeeder;
import jetbrains.buildServer.artifactsMirror.torrent.TorrentUtil;
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * User: Victory.Bedrosova
 * Date: 10/9/12
 * Time: 5:12 PM
 */
public class AgentTorrentsManager extends AgentLifeCycleAdapter {
  private final static Logger LOG = Logger.getInstance(AgentTorrentsManager.class.getName());

  private static final String TORRENT_FOLDER_NAME = "torrents";

  @NotNull
  private final TrackerManager myTrackerManager;
  private volatile URI myTrackerAnnounceUrl;
  private volatile Integer myFileSizeThresholdMb;
  private TorrentsDirectorySeeder myTorrentsDirectorySeeder;

  public AgentTorrentsManager(@NotNull BuildAgentConfiguration agentConfiguration,
                              @NotNull EventDispatcher<AgentLifeCycleListener> eventDispatcher,
                              @NotNull TrackerManager trackerManager) throws Exception {
    eventDispatcher.addListener(this);

    File torrentsStorage = agentConfiguration.getCacheDirectory(TORRENT_FOLDER_NAME);
    myTrackerManager = trackerManager;
    myTorrentsDirectorySeeder = new TorrentsDirectorySeeder(torrentsStorage, new TorrentFileFactory() {
      @Nullable
      public File createTorrentFile(@NotNull File sourceFile, @NotNull File parentDir) throws IOException {
        return AgentTorrentsManager.this.createTorrentFile(sourceFile, parentDir);
      }
    });
  }

  private File createTorrentFile(File sourceFile, File parentDir) {
    if (!settingsInited()) return null;
    if (sourceFile.length() >= myFileSizeThresholdMb) {
      File torrentFile = new File(parentDir, sourceFile.getName() + ".torrent");
      TorrentUtil.createTorrent(sourceFile, torrentFile, myTrackerAnnounceUrl);
      return torrentFile;
    }
    return null;
  }

  private boolean settingsInited() {
    return myTrackerAnnounceUrl != null && myFileSizeThresholdMb != null;
  }

  private boolean initSettings() {
    try {
      myTrackerAnnounceUrl = new URI(myTrackerManager.getAnnounceUrl());
    } catch (URISyntaxException e) {
      LOG.warn(e.toString(), e);
      return false;
    }
    myFileSizeThresholdMb = myTrackerManager.getFileSizeThresholdMb();
    return true;
  }

  @Override
  public void agentStarted(@NotNull BuildAgent agent) {
    myTorrentsDirectorySeeder.start();
  }

  @Override
  public void buildStarted(@NotNull AgentRunningBuild runningBuild) {
    initSettings();
  }

  @Override
  public void agentShutdown() {
    myTorrentsDirectorySeeder.stop();
  }

  public boolean announceNewFile(@NotNull File srcFile, @NotNull String namespace) {
    if (!settingsInited()) return false;
    if (srcFile.length() >= myFileSizeThresholdMb) {
      File linkDir = new File(myTorrentsDirectorySeeder.getStorageDirectory(), namespace);
      linkDir.mkdirs();
      if (!linkDir.isDirectory()) return false;
      try {
        LinkFile.createLink(srcFile, linkDir);
      } catch (IOException e) {
        return false;
      }
    }

    return true;
  }
}
