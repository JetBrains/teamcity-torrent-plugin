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
package jetbrains.buildServer.torrent.web;

import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.torrent.ServerTorrentsDirectorySeeder;
import jetbrains.buildServer.torrent.TorrentConfigurator;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jetbrains.annotations.NotNull;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * @author Maxim Podkolzine (maxim.podkolzine@jetbrains.com)
 * @since 8.0
 */
public class TorrentLinksController extends AbstractLinksController {

  public TorrentLinksController(@NotNull SBuildServer server,
                                @NotNull WebControllerManager webControllerManager,
                                @NotNull final TorrentConfigurator configurator,
                                @NotNull ServerTorrentsDirectorySeeder torrentsDirectorySeeder) {
    super(server, webControllerManager, configurator, torrentsDirectorySeeder, "/torrentLinks.html");
  }

  @Override
  protected void writeResponse(@NotNull HttpServletResponse response, Map<File, String> torrentsAndArtifacts, @NotNull SBuild build) throws IOException {
    response.setContentType("text/plain");
    try (ServletOutputStream output = response.getOutputStream()) {
      for (String name : torrentsAndArtifacts.values()) {
        output.println(name);
      }
    }
  }
}
