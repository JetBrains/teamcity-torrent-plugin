package jetbrains.buildServer.torrent;

import com.intellij.openapi.diagnostic.Logger;
import com.turn.ttorrent.common.Torrent;
import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.agent.CurrentBuildTracker;
import jetbrains.buildServer.artifacts.ArtifactCacheProvider;
import jetbrains.buildServer.artifacts.ArtifactsCacheListener;
import jetbrains.buildServer.torrent.seeder.TorrentsDirectorySeeder;
import jetbrains.buildServer.torrent.torrent.TorrentUtil;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.net.URI;

/**
 * @author Sergey.Pak
 *         Date: 10/3/13
 *         Time: 5:18 PM
 */
public class TorrentArtifactCacheListener implements ArtifactsCacheListener {
  private final static Logger LOG = Logger.getInstance(TorrentArtifactCacheListener.class.getName());

  private final TorrentsDirectorySeeder myTorrentsDirectorySeeder;
  private final CurrentBuildTracker myBuildTracker;
  private ArtifactCacheProvider myArtifactCacheProvider;
  private final TorrentConfiguration myConfiguration;
  private final AgentTorrentsManager myTorrentsManager;

  public TorrentArtifactCacheListener(@NotNull final TorrentsDirectorySeeder torrentsDirectorySeeder,
                                      @NotNull final CurrentBuildTracker currentBuildTracker,
                                      @NotNull final TorrentConfiguration configuration,
                                      @NotNull final AgentTorrentsManager torrentsManager) {
    myTorrentsDirectorySeeder = torrentsDirectorySeeder;
    myBuildTracker = currentBuildTracker;
    myConfiguration = configuration;
    myTorrentsManager = torrentsManager;
  }

  public void onCacheInitialized(@NotNull final ArtifactCacheProvider artifactCacheProvider) {
    myArtifactCacheProvider = artifactCacheProvider;
  }

  public void onBeforeAddOrUpdate(@NotNull File file) {
    if (!myTorrentsManager.isTorrentEnabled())
      return;
    myTorrentsDirectorySeeder.stopSeedingSrcFile(file, false);
  }

  public void onAfterAddOrUpdate(@NotNull File file) {
    final String absolutePath = file.getAbsolutePath();
    if (!myTorrentsManager.isTorrentEnabled()){
      LOG.debug("Torrent plugin disabled. Won't seed " + absolutePath);
      return;
    }
    if (myTorrentsDirectorySeeder.isSeedingByPath(file)) {
      LOG.debug("Already seeding " + absolutePath);
      return;
    }
    if (!TorrentUtil.shouldCreateTorrentFor(file.length(), myConfiguration)) {
      LOG.debug("Won't create torrent for " + absolutePath + ". Artifact is too small: " + file.length());
      return;
    }
    final String relativePath = FileUtil.getRelativePath(myArtifactCacheProvider.getCacheDir(), file);
    if (relativePath == null)
      return;

    final ParsedArtifactPath artifactPath = new ParsedArtifactPath(relativePath.replaceAll("\\\\", "/"));
    final String relativeTorrentPath = artifactPath.getTorrentUrl();
    final File torrentFile = new File(myArtifactCacheProvider.getCacheDir(), relativeTorrentPath);

    final String announceUrl = myConfiguration.getAnnounceUrl();
    if (!torrentFile.exists() && announceUrl != null) {
      try {
        Torrent torrent = Torrent.create(file, URI.create(announceUrl), "TeamCity Torrent Plugin");
        torrentFile.getParentFile().mkdirs();
        torrent.save(torrentFile);
      } catch (Exception e) {
        LOG.warnAndDebugDetails("Failed to create torrent file: " + torrentFile.getAbsolutePath(), e);
        return;
      }
    }

    if (torrentFile.isFile()) {
      log2Build("Started seeding " + file.getAbsolutePath());
      myTorrentsManager.getTorrentsDirectorySeeder().registerSrcAndTorrentFile(file, torrentFile, true);
    }
  }

  public void onBeforeDelete(@NotNull File file) {
    if (!myTorrentsManager.isTorrentEnabled())
      return;
    myTorrentsDirectorySeeder.stopSeedingSrcFile(file, true);
  }

  public void onAfterDelete(@NotNull File file) {
  }

  private void log2Build(String msg){
    final BuildProgressLogger buildLogger = myBuildTracker.getCurrentBuild().getBuildLogger();
    TorrentUtil.log2Build(msg, buildLogger);
  }
}
