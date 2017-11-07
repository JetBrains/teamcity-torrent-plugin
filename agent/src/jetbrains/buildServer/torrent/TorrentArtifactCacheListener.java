package jetbrains.buildServer.torrent;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.agent.CurrentBuildTracker;
import jetbrains.buildServer.agent.NoRunningBuildException;
import jetbrains.buildServer.agent.artifacts.ArtifactsWatcher;
import jetbrains.buildServer.artifacts.ArtifactCacheProvider;
import jetbrains.buildServer.artifacts.ArtifactsCacheListener;
import jetbrains.buildServer.artifacts.RevisionRules;
import jetbrains.buildServer.torrent.seeder.TorrentsSeeder;
import jetbrains.buildServer.torrent.torrent.TorrentUtil;
import jetbrains.buildServer.util.FileUtil;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
  private final ArtifactsWatcher myArtifactsWatcher;

  public TorrentArtifactCacheListener(@NotNull final TorrentsSeeder torrentsSeeder,
                                      @NotNull final CurrentBuildTracker currentBuildTracker,
                                      @NotNull final TorrentConfiguration configuration,
                                      @NotNull final AgentTorrentsManager torrentsManager,
                                      @NotNull final TorrentFilesFactory torrentFilesFactory,
                                      @NotNull final ArtifactsWatcher artifactsWatcher) {
    myTorrentsSeeder = torrentsSeeder;
    myBuildTracker = currentBuildTracker;
    myConfiguration = configuration;
    myTorrentsManager = torrentsManager;
    myTorrentFilesFactory = torrentFilesFactory;
    myArtifactsWatcher = artifactsWatcher;
  }

  public void onCacheInitialized(@NotNull final ArtifactCacheProvider artifactCacheProvider) {
    myArtifactCacheProvider = artifactCacheProvider;
  }

  public void onBeforeAddOrUpdate(@NotNull File file) {
    if (isTorrentFile(file)) return;

    if (!myTorrentsManager.isTorrentEnabled())
      return;
    myTorrentsSeeder.unregisterSrcFile(file);
  }

  public void onAfterAddOrUpdate(@NotNull File file) {
    if (isTorrentFile(file)) return;
    if (isTeamcityIVYFile(file)) return;

    final String absolutePath = file.getAbsolutePath();
    if (!myTorrentsManager.isTorrentEnabled()) {
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

    File createdTorrentFile = publishTorrentFileAndStartSeeding(file);

    if (createdTorrentFile == null) {
      return;
    }

    File cacheCurrentBuildDir;
    try {
      cacheCurrentBuildDir = getCurrentBuildFolderCache();
    } catch (NoRunningBuildException e) {
      logWarningThatCacheCurrentBuildNotFound(absolutePath);
      return;
    }
    if (cacheCurrentBuildDir == null) {
      logWarningThatCacheCurrentBuildNotFound(absolutePath);
      return;
    }

    createTorrentFileCopyAndAddAsArtifact(createdTorrentFile, file, cacheCurrentBuildDir);
  }

  private boolean isTeamcityIVYFile(File file) {
    return Constants.TEAMCITY_IVY.equalsIgnoreCase(file.getName());
  }

  private void logWarningThatCacheCurrentBuildNotFound(String artifactPath) {
    LOG.warn(String.format("unable to find cache folder for current build. Torrent file for %s was not send to the server", artifactPath));
  }

  private void createTorrentFileCopyAndAddAsArtifact(@NotNull File torrentFile,
                                                     @NotNull final File file,
                                                     @NotNull final File cacheCurrentBuildDir) {
    String artifactDirs = FileUtil.getRelativePath(cacheCurrentBuildDir, file.getParentFile());
    if (artifactDirs == null) {
      LOG.warn(String.format("Unable to find relative path to artifact %s. " +
              "Torrent file was not created for this artifact. Nobody can download the aftifact via bittorrent", file.getAbsolutePath()));
      return;
    }
    if (!isChildFile(cacheCurrentBuildDir, file)) {
      //reference to parent in relative path means that it is artifact from other build. We can skip it
      return;
    }
    File torrentsTempDirectory = new File(myBuildTracker.getCurrentBuild().getBuildTempDirectory(), Constants.TORRENT_FILE_COPIES_DIR);
    File dirForTorrentCopy = new File(torrentsTempDirectory, artifactDirs);
    File torrentFileCopy = new File(dirForTorrentCopy, file.getName() + TorrentUtil.TORRENT_FILE_SUFFIX);
    try {
      torrentFileCopy = torrentFileCopy.getCanonicalFile();
      FileUtils.copyFile(torrentFile, torrentFileCopy);
    } catch (IOException e) {
      LOG.debug("error in copy torrent file", e);
      return;
    }

    String torrentArtifactPath = createArtifactPath(torrentFileCopy, Constants.TORRENTS_DIR_ON_SERVER + artifactDirs);

    myArtifactsWatcher.addNewArtifactsPath(torrentArtifactPath);
  }

  private boolean isChildFile(File dir, File file) {
    return file.getAbsolutePath().startsWith(dir.getAbsolutePath());
  }

  @Nullable
  private File publishTorrentFileAndStartSeeding(@NotNull final File file) {
    final String relativePath = FileUtil.getRelativePath(myArtifactCacheProvider.getCacheDir(), file);
    if (relativePath == null)
      return null;

    File torrentFile = myTorrentFilesFactory.createTorrentFile(file);
    if (torrentFile == null) return null;

    log2Build("Started seeding " + file.getAbsolutePath());
    myTorrentsManager.getTorrentsSeeder().registerSrcAndTorrentFile(file, torrentFile, true);
    return torrentFile;
  }


  @Nullable
  private File getCurrentBuildFolderCache() {
    File cacheDir = myArtifactCacheProvider.getCacheDir();
    if (cacheDir == null) {
      return null;
    }

    File projectsDir = getProjectsDir(cacheDir);

    if (projectsDir == null) {
      return null;
    }

    File projectDir = new File(projectsDir, myBuildTracker.getCurrentBuild().getBuildTypeExternalId());
    return new File(projectDir, myBuildTracker.getCurrentBuild().getBuildId() + RevisionRules.BUILD_ID_SUFFIX);
  }

  @Nullable
  private File getProjectsDir(final File cacheDir) {
    File result = cacheDir;
    int maxCount = 10;
    int count = 0;
    while (!result.getAbsolutePath().endsWith(Constants.CACHE_STATIC_DIRS)) {
      count++;
      if (count > maxCount) {
        LOG.warn("failed get projects dir. The maximum depth of search is exceeded");
        return null;
      }
      File[] childFiles = result.listFiles(new FileFilter() {
        @Override
        public boolean accept(File pathname) {
          return pathname.isDirectory();
        }
      });
      if (childFiles == null || childFiles.length != 1) {
        LOG.warn(String.format("could not find child directory in %s, child file names is %s", result.getAbsolutePath(), Arrays.toString(childFiles)));
        return null;
      }
      result = new File(result, childFiles[0].getName());
    }
    return result;
  }

  private String createArtifactPath(File source, String destination) {
    return source.getAbsolutePath() + "=>" + destination;
  }

  public void onBeforeDelete(@NotNull File file) {
    if (isTorrentFile(file)) return;

    if (!myTorrentsManager.isTorrentEnabled())
      return;
    myTorrentsSeeder.unregisterSrcFile(file);
  }

  public void onAfterDelete(@NotNull File file) {
  }

  private void log2Build(String msg) {
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
