/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

package jetbrains.buildServer.torrent.web;

import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.torrent.ServerTorrentsDirectorySeeder;
import jetbrains.buildServer.torrent.TorrentConfigurator;
import jetbrains.buildServer.torrent.torrent.TorrentUtil;
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

public abstract class AbstractLinksController extends BaseController {

  private final ServerTorrentsDirectorySeeder myTorrentsManager;
  private final TorrentConfigurator myConfigurator;

  public AbstractLinksController(@NotNull SBuildServer server,
                                 @NotNull WebControllerManager webControllerManager,
                                 @NotNull TorrentConfigurator configurator,
                                 @NotNull ServerTorrentsDirectorySeeder torrentsDirectorySeeder,
                                 @NotNull String controllerPath) {
    super(server);
    webControllerManager.registerController(controllerPath, this);
    myTorrentsManager = torrentsDirectorySeeder;
    myConfigurator = configurator;
  }

  @Nullable
  @Override
  protected ModelAndView doHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) throws Exception {
    String buildIdParam = request.getParameter("buildId");
    if (buildIdParam == null) {
      return null;
    }

    try {
      long buildId = Long.parseLong(buildIdParam);
      SBuild build = myServer.findBuildInstanceById(buildId);
      if (build != null && myConfigurator.isDownloadEnabled()) {
        Collection<File> torrentFiles = myTorrentsManager.getTorrentFiles(build);
        File baseDir = myTorrentsManager.getTorrentFilesBaseDir(build.getArtifactsDirectory());
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
