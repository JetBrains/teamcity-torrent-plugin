/*
 * Copyright (c) 2000-2012 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package jetbrains.buildServer.artifactsMirror;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * User: Victory.Bedrosova
 * Date: 10/3/12
 * Time: 6:15 PM
 */
public class AgentTorrentsArtifactsPublisher implements ArtifactsPublisher {
  private final static Logger LOG = Logger.getInstance(AgentTorrentsArtifactsPublisher.class.getName());

  @NotNull
  private final AgentTorrentsManager myTorrentsManager;

  public AgentTorrentsArtifactsPublisher(@NotNull AgentTorrentsManager torrentsManager) {
    myTorrentsManager = torrentsManager;
  }

  public int publishFiles(@NotNull Map<File, String> fileStringMap) throws ArtifactPublishingFailedException {
    return announceBuildArtifacts(fileStringMap.keySet());
  }

  private int announceBuildArtifacts(@NotNull Collection<File> artifacts) {
    int num = 0;
    for (File artifact : artifacts) {
      if (announceBuildArtifact(artifact)) ++num;
    }
    return num;
  }

  private boolean announceBuildArtifact(@NotNull File artifact) {
    if (shouldCreateTorrentFor(artifact)) {
      try {
        myTorrentsManager.seedTorrent(artifact);
        return true;
      } catch (IOException e) {
        LOG.warn(e.toString(), e);
      }
    }
    return false;
  }

  private static boolean shouldCreateTorrentFor(@NotNull File artifact) {
    return artifact.isFile() &&
           artifact.length() >= TeamCityProperties.getInteger("teamcity.artifactsTorrent.sizeThresholdMb", 50) * 1024 * 1024;
  }
}
