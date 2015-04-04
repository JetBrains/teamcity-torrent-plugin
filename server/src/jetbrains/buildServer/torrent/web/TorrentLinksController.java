/*
 * Copyright (c) 2000-2012 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package jetbrains.buildServer.torrent.web;

import jetbrains.buildServer.torrent.ServerTorrentsDirectorySeeder;
import jetbrains.buildServer.torrent.TorrentConfigurator;
import jetbrains.buildServer.torrent.torrent.TorrentUtil;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Maxim Podkolzine (maxim.podkolzine@jetbrains.com)
 * @since 8.0
 */
public class TorrentLinksController extends BaseController {
  private final ServerTorrentsDirectorySeeder myTorrentsManager;
  private final TorrentConfigurator myConfigurator;

  public TorrentLinksController(@NotNull SBuildServer server,
                                @NotNull WebControllerManager webControllerManager,
                                @NotNull final TorrentConfigurator configurator,
                                @NotNull ServerTorrentsDirectorySeeder torrentsDirectorySeeder) {
    super(server);
    webControllerManager.registerController("/torrentLinks.html", this);
    myTorrentsManager = torrentsDirectorySeeder;
    myConfigurator = configurator;
  }

  @Nullable
  @Override
  protected ModelAndView doHandle(@NotNull HttpServletRequest request,
                                  @NotNull HttpServletResponse response) throws Exception {
    String buildIdParam = request.getParameter("buildId");
    if (buildIdParam == null) {
      return null;
    }

    try {
      long buildId = Long.parseLong(buildIdParam);
      SBuild build = myServer.findBuildInstanceById(buildId);
      if (build != null && myConfigurator.isDownloadEnabled()) {
        Collection<File> torrentFiles = myTorrentsManager.getTorrentFiles(build);
        File baseDir = myTorrentsManager.getTorrentFilesBaseDir(build);
        List<String> paths = getArtifactsWithTorrents(baseDir, torrentFiles);

        response.setContentType("text/plain");
        ServletOutputStream output = response.getOutputStream();
        try {
          for (String name : paths) {
            output.println(name);
          }
        } finally {
          output.close();
        }
      }
    } catch (Exception e) {
      // ignore
    }

    return null;
  }

  @NotNull
  private List<String> getArtifactsWithTorrents(@NotNull File baseDir, @NotNull Collection<File> torrentFiles) {
    List<String> names = new ArrayList<String>();
    for (File f: torrentFiles) {
      String path = FileUtil.getRelativePath(baseDir, f);
      if (path == null) continue;
      path = path.replace('\\', '/');
      names.add(path.substring(0, path.length() - TorrentUtil.TORRENT_FILE_SUFFIX.length()));
    }
    return names;
  }
}
