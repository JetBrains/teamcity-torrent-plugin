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
import net.sf.ehcache.Cache;
import net.sf.ehcache.Element;
import net.sf.ehcache.store.MemoryStoreEvictionPolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author Maxim Podkolzine (maxim.podkolzine@jetbrains.com)
 * @since 8.0
 */
public class TorrentLinksController extends BaseController {
  private static final int MAX_ELEMENTS_IN_MEMORY = 1024;

  private static final int SMALL_TIME_TO_LIVE_SECONDS = 120;        // 2 minutes
  private static final int DEFAULT_TIME_TO_LIVE_SECONDS = 1800;     // 30 minutes
  private static final int BIG_TIME_TO_LIVE_SECONDS = 7200;         // 2 hours

  private static final int SMALL_DELTA = 60 * 1000;                 // 1 minute
  private static final int NORMAL_DELTA = 600 * 1000;               // 10 minutes

  private final Cache myTorrentsCache;

  public TorrentLinksController(@NotNull SBuildServer server,
                                @NotNull WebControllerManager webControllerManager) {
    super(server);
    webControllerManager.registerController("/torrentLinks.html", this);
    myTorrentsCache = new Cache("torrents-in-builds-cache", MAX_ELEMENTS_IN_MEMORY,
                                MemoryStoreEvictionPolicy.LRU, false, "/tmp",
                                false, DEFAULT_TIME_TO_LIVE_SECONDS, DEFAULT_TIME_TO_LIVE_SECONDS,
                                false, 1000, null);
    myTorrentsCache.initialise();
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

      Element element = myTorrentsCache.get(buildId);
      @SuppressWarnings("unchecked")
      List<String> torrents = element != null ? (List<String>) element.getObjectValue() : null;
      if (torrents == null) {
        torrents = new ArrayList<String>();
        CachePolicy policy = getTorrentsFor(buildId, torrents);
        int ttl = DEFAULT_TIME_TO_LIVE_SECONDS;
        switch (policy) {
          case SMALL_TTL:  ttl = SMALL_TIME_TO_LIVE_SECONDS; break;
          case NORMAL_TTL: ttl = DEFAULT_TIME_TO_LIVE_SECONDS; break;
          case BIG_TTL:    ttl = BIG_TIME_TO_LIVE_SECONDS; break;
        }
        myTorrentsCache.put(new Element(buildId, torrents, false, ttl, ttl));
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
  private CachePolicy getTorrentsFor(long buildId, @NotNull final List<String> result) {
    SBuild build = myServer.findBuildInstanceById(buildId);
    if (build == null) {
      return CachePolicy.NORMAL_TTL;
    }

    Date finishDate = build.getFinishDate();
    if (finishDate == null) {
      return CachePolicy.SMALL_TTL;             // running build
    }

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

    long delta = System.currentTimeMillis() - finishDate.getTime();
    return delta < SMALL_DELTA ? CachePolicy.SMALL_TTL :
           delta < NORMAL_DELTA ? CachePolicy.NORMAL_TTL : CachePolicy.BIG_TTL;
  }

  private static enum CachePolicy {
    SMALL_TTL,
    NORMAL_TTL,
    BIG_TTL
  }
}
