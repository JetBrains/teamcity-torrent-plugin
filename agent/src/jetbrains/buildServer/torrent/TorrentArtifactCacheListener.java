package jetbrains.buildServer.torrent;

import com.intellij.openapi.diagnostic.Logger;
import com.turn.ttorrent.common.Torrent;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.agent.CurrentBuildTracker;
import jetbrains.buildServer.agent.NoRunningBuildException;
import jetbrains.buildServer.artifacts.ArtifactCacheProvider;
import jetbrains.buildServer.artifacts.ArtifactsCacheListener;
import jetbrains.buildServer.artifacts.LocalCache;
import jetbrains.buildServer.torrent.seeder.TorrentsSeeder;
import jetbrains.buildServer.torrent.torrent.TorrentUtil;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.URI;

/**
 * @author Sergey.Pak
 *         Date: 10/3/13
 *         Time: 5:18 PM
 */
public class TorrentArtifactCacheListener implements ArtifactsCacheListener {
  private final static Logger LOG = Logger.getInstance(TorrentArtifactCacheListener.class.getName());

  private final TorrentsSeeder myTorrentsSeeder;
  private final CurrentBuildTracker myBuildTracker;
  private final TorrentFilesFactory myTorrentFilesFactory;
  private ArtifactCacheProvider myArtifactCacheProvider;
  private final TorrentConfiguration myConfiguration;
  private final AgentTorrentsManager myTorrentsManager;

  public TorrentArtifactCacheListener(@NotNull final TorrentsSeeder torrentsSeeder,
                                      @NotNull final CurrentBuildTracker currentBuildTracker,
                                      @NotNull final TorrentConfiguration configuration,
                                      @NotNull final AgentTorrentsManager torrentsManager,
                                      @NotNull final TorrentFilesFactory torrentFilesFactory) {
    myTorrentsSeeder = torrentsSeeder;
    myBuildTracker = currentBuildTracker;
    myConfiguration = configuration;
    myTorrentsManager = torrentsManager;
    myTorrentFilesFactory = torrentFilesFactory;
  }

  public void onCacheInitialized(@NotNull final ArtifactCacheProvider artifactCacheProvider) {
    myArtifactCacheProvider = artifactCacheProvider;
  }

  public void onBeforeAddOrUpdate(@NotNull File file) {
    if (isTorrentFile(file)) return;

    if (!myTorrentsManager.isTorrentEnabled())
      return;
    myTorrentsSeeder.stopSeedingSrcFile(file, false);
  }

  public void onAfterAddOrUpdate(@NotNull File file) {
    if (isTorrentFile(file)) return;

    final String absolutePath = file.getAbsolutePath();
    if (!myTorrentsManager.isTorrentEnabled()){
      LOG.debug("Torrent plugin disabled. Won't seed " + absolutePath);
      return;
    }
    if (myTorrentsSeeder.isSeedingByPath(file)) {
      LOG.debug("Already seeding " + absolutePath);
      return;
    }
    if (!TorrentUtil.shouldCreateTorrentFor(file.length(), myConfiguration)) {
      LOG.debug("Won't create torrent for " + absolutePath + ". Artifact is too small: " + file.length());
      return;
    }

    publishTorrentFileAndStartSeeding(file);
  }

  private void publishTorrentFileAndStartSeeding(@NotNull final File file) {
    final String relativePath = FileUtil.getRelativePath(myArtifactCacheProvider.getCacheDir(), file);
    if (relativePath == null)
      return;

    File torrentFile = myTorrentFilesFactory.createTorrentFile(file);
    if (torrentFile == null) return;

    log2Build("Started seeding " + file.getAbsolutePath());
    myTorrentsManager.getTorrentsSeeder().registerSrcAndTorrentFile(file, torrentFile, true);
  }

  public void onBeforeDelete(@NotNull File file) {
    if (isTorrentFile(file)) return;

    if (!myTorrentsManager.isTorrentEnabled())
      return;
    myTorrentsSeeder.stopSeedingSrcFile(file, true);
  }

  public void onAfterDelete(@NotNull File file) {
  }

  private void log2Build(String msg){
    try {
      AgentRunningBuild currentBuild = myBuildTracker.getCurrentBuild();
      final BuildProgressLogger buildLogger = currentBuild.getBuildLogger();
      TorrentUtil.log2Build(msg, buildLogger);
    } catch (NoRunningBuildException e) {
      // build finished?
    }
  }

  private boolean isTorrentFile(@NotNull File file) {
    return file.getName().endsWith(".torrent");
  }
}
