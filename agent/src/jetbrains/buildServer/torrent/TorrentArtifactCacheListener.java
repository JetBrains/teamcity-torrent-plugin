package jetbrains.buildServer.torrent;

import com.intellij.openapi.diagnostic.Logger;
import com.turn.ttorrent.common.Torrent;
import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.agent.CurrentBuildTracker;
import jetbrains.buildServer.artifacts.ArtifactCacheProvider;
import jetbrains.buildServer.artifacts.ArtifactsCacheListener;
import jetbrains.buildServer.artifacts.LocalCache;
import jetbrains.buildServer.torrent.seeder.TorrentsDirectorySeeder;
import jetbrains.buildServer.torrent.torrent.TorrentUtil;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.NamedDaemonThreadFactory;
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

  private final TorrentsDirectorySeeder myTorrentsDirectorySeeder;
  private final CurrentBuildTracker myBuildTracker;
  private final File myTempDir;
  private ArtifactCacheProvider myArtifactCacheProvider;
  private final TorrentConfiguration myConfiguration;
  private final AgentTorrentsManager myTorrentsManager;

  public TorrentArtifactCacheListener(@NotNull final TorrentsDirectorySeeder torrentsDirectorySeeder,
                                      @NotNull final CurrentBuildTracker currentBuildTracker,
                                      @NotNull final TorrentConfiguration configuration,
                                      @NotNull final AgentTorrentsManager torrentsManager,
                                      @NotNull final File directoryForTempTorrentFiles) {
    myTorrentsDirectorySeeder = torrentsDirectorySeeder;
    myBuildTracker = currentBuildTracker;
    myConfiguration = configuration;
    myTorrentsManager = torrentsManager;
    myTempDir = directoryForTempTorrentFiles;
  }

  public void onCacheInitialized(@NotNull final ArtifactCacheProvider artifactCacheProvider) {
    myArtifactCacheProvider = artifactCacheProvider;
  }

  public void onBeforeAddOrUpdate(@NotNull File file) {
    if (isTorrentFile(file)) return;

    if (!myTorrentsManager.isTorrentEnabled())
      return;
    myTorrentsDirectorySeeder.stopSeedingSrcFile(file, false);
  }

  public void onAfterAddOrUpdate(@NotNull File file) {
    if (isTorrentFile(file)) return;

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

    publishTorrentFileAndStartSeeding(file);
  }

  private void publishTorrentFileAndStartSeeding(@NotNull final File file) {
    NamedDaemonThreadFactory tf = new NamedDaemonThreadFactory("Torrent file publisher");
    tf.newThread(new Runnable() {
      public void run() {
        final String relativePath = FileUtil.getRelativePath(myArtifactCacheProvider.getCacheDir(), file);
        if (relativePath == null)
          return;

        final ParsedArtifactPath artifactPath = new ParsedArtifactPath(normalizeSlashes(relativePath));

        File torrentFile = null;
        final String announceUrl = myConfiguration.getAnnounceUrl();
        if (announceUrl != null) {
          try {
            torrentFile = FileUtil.createTempFile(myTempDir, file.getName(), ".torrent", true);
            Torrent torrent = Torrent.create(file, URI.create(announceUrl), "TeamCity Torrent Plugin");
            torrent.save(torrentFile);
            LOG.info("Torrent file saved: " + torrentFile.getAbsolutePath() + " [" + torrent.toString() + "]");
          } catch (Exception e) {
            if (torrentFile != null) {
              LOG.warnAndDebugDetails("Failed to create torrent file: " + torrentFile.getAbsolutePath(), e);
            } else {
              LOG.warnAndDebugDetails("Failed to create torrent file", e);
            }
            return;
          }
        }

        if (torrentFile != null && torrentFile.isFile()) {
          try {
            // register newly generated torrent file in local cache, otherwise it can be deleted by background cleaner
            final LocalCache localCache = myArtifactCacheProvider.getLocalCache();
            if (localCache != null) {
              String torrentUrl = normalizeSlashes(artifactPath.getTorrentUrl());
              if (!torrentUrl.startsWith("http://")) {
                torrentUrl = "http://" + torrentUrl;
              }

              localCache.putFile(torrentUrl, torrentFile); // torrentFile copied to cache

              // compute place of the torrent file in cache
              File cachedTorrentFile = new File(myArtifactCacheProvider.getCacheDir(), artifactPath.getTorrentUrl());

              log2Build("Started seeding " + file.getAbsolutePath());
              myTorrentsManager.getTorrentsDirectorySeeder().registerSrcAndTorrentFile(file, cachedTorrentFile, true);
            }
          } catch (IOException e) {
            LOG.warnAndDebugDetails("Failed to add torrent file into HTTP cache: " + torrentFile.getAbsolutePath(), e);
          }
        }

        if (torrentFile != null) {
          FileUtil.delete(torrentFile);
        }
      }
    }).start();
  }

  @NotNull
  private String normalizeSlashes(String path) {
    return path.replace('\\', '/');
  }

  public void onBeforeDelete(@NotNull File file) {
    if (isTorrentFile(file)) return;

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

  private boolean isTorrentFile(@NotNull File file) {
    return file.getName().endsWith(".torrent");
  }
}
