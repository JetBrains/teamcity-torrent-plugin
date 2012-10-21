/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package jetbrains.buildServer.artifactsMirror.web;

import com.intellij.openapi.util.io.StreamUtil;
import jetbrains.buildServer.artifactsMirror.ServerTorrentsDirectorySeeder;
import jetbrains.buildServer.artifactsMirror.torrent.TorrentUtil;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.serverSide.BuildsManager;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import jetbrains.buildServer.web.util.WebUtil;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;

public class DownloadTorrentController extends BaseController {
  private final ServerTorrentsDirectorySeeder myTorrentsManager;
  private final BuildsManager myBuildsManager;

  public DownloadTorrentController(@NotNull WebControllerManager controllerManager, @NotNull ServerTorrentsDirectorySeeder torrentsDirectorySeeder, @NotNull BuildsManager buildsManager) {
    controllerManager.registerController("/downloadTorrent.html", this);
    myTorrentsManager = torrentsDirectorySeeder;
    myBuildsManager = buildsManager;
  }

  @Override
  protected ModelAndView doHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) throws Exception {
    String buildIdParam = request.getParameter("buildId");
    String path = request.getParameter("file");
    String torrentPath = path + TorrentUtil.TORRENT_FILE_SUFFIX;

    File torrentFile = null;
    long buildId = Long.parseLong(buildIdParam);
    SBuild build = myBuildsManager.findBuildInstanceById(buildId);
    if (build != null) {
      torrentFile = myTorrentsManager.getTorrentFile(build, torrentPath);
      if (!torrentFile.isFile()) {
        torrentFile = null;
      }
    }

    if (torrentFile == null) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
    } else {
      response.setContentType(WebUtil.getMimeType(request, torrentFile.getName()));
      WebUtil.setContentDisposition(request, response, torrentFile.getName());
      ServletOutputStream output = response.getOutputStream();
      FileInputStream fis = null;
      try {
        fis = new FileInputStream(torrentFile);
        StreamUtil.copyStreamContent(fis, output);
      } finally {
        FileUtil.close(fis);
        output.close();
      }
    }

    return null;
  }
}
