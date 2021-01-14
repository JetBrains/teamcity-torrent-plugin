/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

package jetbrains.buildServer.torrent;

import com.intellij.openapi.diagnostic.Logger;
import com.turn.ttorrent.common.TorrentCreator;
import com.turn.ttorrent.common.TorrentMetadata;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.agent.CurrentBuildTracker;
import jetbrains.buildServer.agent.NoRunningBuildException;
import jetbrains.buildServer.artifacts.ArtifactCacheProvider;
import jetbrains.buildServer.artifacts.ArtifactsCacheListener;
import jetbrains.buildServer.artifacts.RevisionRules;
import jetbrains.buildServer.torrent.seeder.TorrentsSeeder;
import jetbrains.buildServer.torrent.torrent.TorrentUtil;
import jetbrains.buildServer.torrent.util.StringUtils;
import jetbrains.buildServer.util.FileUtil;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  private final TorrentFilesFactoryImpl myTorrentFilesFactory;
  private ArtifactCacheProvider myArtifactCacheProvider;
  private final TorrentConfiguration myConfiguration;
  private final AgentTorrentsManager myTorrentsManager;
  private final BuildAgentConfiguration myAgentConfiguration;

  public TorrentArtifactCacheListener(@NotNull final TorrentsSeeder torrentsSeeder,
                                      @NotNull final CurrentBuildTracker currentBuildTracker,
                                      @NotNull final TorrentConfiguration configuration,
                                      @NotNull final AgentTorrentsManager torrentsManager,
                                      @NotNull final TorrentFilesFactoryImpl torrentFilesFactory,
                                      @NotNull final BuildAgentConfiguration agentConfiguration) {
    myTorrentsSeeder = torrentsSeeder;
    myBuildTracker = currentBuildTracker;
    myConfiguration = configuration;
    myTorrentsManager = torrentsManager;
    myTorrentFilesFactory = torrentFilesFactory;
    myAgentConfiguration = agentConfiguration;
  }

  public void onCacheInitialized(@NotNull final ArtifactCacheProvider artifactCacheProvider) {
    myArtifactCacheProvider = artifactCacheProvider;
  }

  public void onBeforeAddOrUpdate(@NotNull File file) {
    if (isTorrentFile(file)) return;

    if (!myTorrentsManager.isTransportEnabled())
      return;
    myTorrentsSeeder.unregisterSrcFile(file);
  }

  public void onAfterAddOrUpdate(@NotNull File file) {
    if (isTorrentFile(file)) return;
    if (isTeamcityIVYFile(file)) return;

    final String absolutePath = file.getAbsolutePath();
    if (!myTorrentsManager.isTransportEnabled()) {
      LOG.debug("Torrent plugin disabled. Won't seed " + absolutePath);
      return;
    }
    if (!TorrentUtil.shouldCreateTorrentFor(file.length(), myConfiguration)) {
      LOG.debug("Won't create torrent for " + absolutePath + ". Artifact is too small: " + file.length());
      return;
    }
    String announceUrl = myConfiguration.getAnnounceUrl();
    if (announceUrl == null) return;

    TorrentMetadata metadata;
    try {
      metadata = TorrentCreator.create(file, URI.create(announceUrl), "TeamCity Torrent Plugin");
    } catch (Exception e) {
      return;
    }
    if (myTorrentsSeeder.getClient().isSeeding(metadata)) {
      LOG.debug("Already seeding " + absolutePath);
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

    if (!isChildFile(cacheCurrentBuildDir, file)) {
      //reference to parent in relative path means that it is artifact from other build. We can skip it
      return;
    }

    File createdTorrentFile = publishTorrentFileAndStartSeeding(file);

    if (createdTorrentFile == null) {
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

    myTorrentsManager.saveTorrentForPublish(torrentArtifactPath);
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

    LOG.debug("Started seeding " + file.getAbsolutePath());
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

    File projectDir = new File(projectsDir, myBuildTracker.getCurrentBuild().getBuildTypeExternalId());
    return new File(projectDir, myBuildTracker.getCurrentBuild().getBuildId() + RevisionRules.BUILD_ID_SUFFIX);
  }

  @NotNull
  private File getProjectsDir(final File cacheDir) {
    String serverUrlAsDirectoriesPath = StringUtils.parseServerUrlToDirectoriesPath(myAgentConfiguration.getServerUrl());
    return new File(cacheDir, serverUrlAsDirectoriesPath + File.separator + Constants.CACHE_STATIC_DIRS);
  }

  private String createArtifactPath(File source, String destination) {
    return source.getAbsolutePath() + "=>" + destination;
  }

  public void onBeforeDelete(@NotNull File file) {
    if (isTorrentFile(file)) return;

    if (!myTorrentsManager.isTransportEnabled())
      return;
    myTorrentsSeeder.unregisterSrcFile(file);
  }

  public void onAfterDelete(@NotNull File file) {
  }

  private boolean isTorrentFile(@NotNull File file) {
    return file.getName().endsWith(".torrent");
  }
}
