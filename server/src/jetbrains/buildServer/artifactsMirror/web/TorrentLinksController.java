/*
 * Copyright (c) 2000-2012 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package jetbrains.buildServer.artifactsMirror.web;

import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifact;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifacts;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactsViewMode;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Maxim Podkolzine (maxim.podkolzine@jetbrains.com)
 * @since 8.0
 */
public class TorrentLinksController extends BaseController {
  private static final int MAX_BUILDS_IN_CACHE = 1000;
  private final ConcurrentHashMap<Long, List<String>> myTorrentsCache;

  public TorrentLinksController(@NotNull SBuildServer server,
                                @NotNull WebControllerManager webControllerManager) {
    super(server);
    myTorrentsCache = new ConcurrentHashMap<Long, List<String>>();
    webControllerManager.registerController("/torrentLinks.html", this);
  }

  @Nullable
  @Override
  protected ModelAndView doHandle(@NotNull HttpServletRequest request,
                                  @NotNull HttpServletResponse response) throws Exception {
    String buildIdParam = request.getParameter("buildId");
    String namesParam = request.getParameter("names");
    if (buildIdParam == null || namesParam == null) {
      return null;
    }

    try {
      long buildId = Long.parseLong(buildIdParam);
      List<String> names = StringUtil.split(namesParam, true, '/');

      if (names.isEmpty()) {
        return null;
      }

      List<String> torrents = myTorrentsCache.get(buildId);
      if (torrents == null) {
        torrents = getTorrentsFor(buildId);
        synchronized (this) {
          compactCacheIfNeeded();
          myTorrentsCache.put(buildId, torrents);
        }
      }

      names.retainAll(torrents);

      response.setContentType("text/plain");
      ServletOutputStream output = response.getOutputStream();
      try {
        for (String name : names) {
          output.print(name);
          output.print("/");
        }
      } finally {
        output.close();
      }
    } catch (Exception e) {
      // ignore
    }

    return null;
  }

  @NotNull
  private List<String> getTorrentsFor(long buildId) {
    SBuild build = myServer.findBuildInstanceById(buildId);
    if (build == null) {
      return Collections.emptyList();
    }

    final ArrayList<String> result = new ArrayList<String>();
    BuildArtifacts artifacts = build.getArtifacts(BuildArtifactsViewMode.VIEW_HIDDEN_ONLY);
    artifacts.iterateArtifacts(new BuildArtifacts.BuildArtifactsProcessor() {
      @NotNull
      public Continuation processBuildArtifact(@NotNull BuildArtifact artifact) {
        String name = artifact.getName();
        if (!artifact.isDirectory()) {
          if (name.endsWith(".torrent")) {
            result.add(name.substring(0, name.length() - ".torrent".length()));
          }
          return Continuation.CONTINUE;
        }
        return Continuation.CONTINUE;
      }
    });
    return result;
  }

  private void compactCacheIfNeeded() {
    if (myTorrentsCache.size() > MAX_BUILDS_IN_CACHE) {
      List<Long> keys = new ArrayList<Long>(myTorrentsCache.keySet());
      Collections.sort(keys);
      for (int i = 0; i < MAX_BUILDS_IN_CACHE / 2; ++i) {
        myTorrentsCache.remove(keys.get(i));
      }
    }
  }
}
