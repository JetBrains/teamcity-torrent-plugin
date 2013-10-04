package jetbrains.buildServer.artifactsMirror;

import com.intellij.openapi.diagnostic.Logger;
import com.turn.ttorrent.common.Torrent;
import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.agent.CurrentBuildTracker;
import jetbrains.buildServer.artifacts.ArtifactCacheProvider;
import jetbrains.buildServer.artifacts.ArtifactsCacheListener;
import jetbrains.buildServer.artifactsMirror.seeder.FileLink;
import jetbrains.buildServer.artifactsMirror.seeder.TorrentsDirectorySeeder;
import jetbrains.buildServer.artifactsMirror.torrent.TorrentUtil;
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
  private final TorrentTrackerConfiguration myConfiguration;

  public TorrentArtifactCacheListener(@NotNull final TorrentsDirectorySeeder torrentsDirectorySeeder,
                                      @NotNull final CurrentBuildTracker currentBuildTracker,
                                      @NotNull final TorrentTrackerConfiguration configuration) {
    myTorrentsDirectorySeeder = torrentsDirectorySeeder;
    myBuildTracker = currentBuildTracker;
    myConfiguration = configuration;
  }

  public void onCacheInitialized(@NotNull final ArtifactCacheProvider artifactCacheProvider) {
    LOG.info("onCacheInitialized");
    myArtifactCacheProvider = artifactCacheProvider;
  }

  public void onBeforeAddOrUpdate(@NotNull File file) {
    LOG.info("onBeforeAddOrUpdate " + file.getAbsolutePath());
  }

  public void onAfterAddOrUpdate(@NotNull File file) {
    final String absolutePath = file.getAbsolutePath();
    LOG.info("onAfterAddOrUpdate " + absolutePath);
    if (myTorrentsDirectorySeeder.isSeedingByPath(file)) {
      LOG.info("Already seeding " + absolutePath);
      return;
    }
    if (!TorrentUtil.shouldCreateTorrentFor(file.length(), myConfiguration)) {
      LOG.info("Won't create torrent for " + absolutePath);
      return;
    }
    final String relativePath = FileUtil.getRelativePath(myArtifactCacheProvider.getCacheDir(), file);
    if (relativePath == null)
      return;

    final ParsedArtifactPath artifactPath = new ParsedArtifactPath(relativePath.replaceAll("\\\\", "/"));
    final String relativeTorrentPath = artifactPath.getTorrentUrl();
    final File torrentFile = new File(myArtifactCacheProvider.getCacheDir(), relativeTorrentPath);
    try {
      final Torrent torrent;
      if (!torrentFile.exists()) {
        torrent = Torrent.create(file, URI.create(myConfiguration.getAnnounceUrl()), "teamcity torrent plugin");
        torrentFile.getParentFile().mkdirs();
        torrent.save(torrentFile);
        final File linkDir = new File(myTorrentsDirectorySeeder.getStorageDirectory(), artifactPath.getRelativeLinkPath()).getParentFile();
        linkDir.mkdirs();

        FileLink.createLink(file, torrentFile, linkDir);

      } else {
        torrent = Torrent.load(torrentFile);
      }
      log2Build("Started seeding " + file.getAbsolutePath());
      myTorrentsDirectorySeeder.getTorrentSeeder().seedTorrent(torrent, file);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void onBeforeDelete(@NotNull File file) {
    LOG.info("onBeforeDelete " + file.getAbsolutePath());
    myTorrentsDirectorySeeder.getTorrentSeeder().stopSeedingByPath(file);
  }

  public void onAfterDelete(@NotNull File file) {
    LOG.info("onAfterDelete " + file.getAbsolutePath());
  }

  private void log2Build(String msg){
    final BuildProgressLogger buildLogger = myBuildTracker.getCurrentBuild().getBuildLogger();
    TorrentUtil.log2Build(msg, buildLogger);
  }
}
