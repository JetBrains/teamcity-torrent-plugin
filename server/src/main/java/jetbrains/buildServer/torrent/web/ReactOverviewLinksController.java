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

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
 * controller provides information about torrent files for artofacts of specified build.
 *
 * @see <a href="https://youtrack.jetbrains.com/issue/TW-56931"></a>
 * @since 2018.2
 */
public class ReactOverviewLinksController extends AbstractLinksController {

  private final Gson myGson = new Gson();

  public ReactOverviewLinksController(@NotNull SBuildServer server,
                                      @NotNull WebControllerManager webControllerManager,
                                      @NotNull TorrentConfigurator configurator,
                                      @NotNull ServerTorrentsDirectorySeeder torrentsDirectorySeeder) {
    super(server, webControllerManager, configurator, torrentsDirectorySeeder,
            "/newTorrentLinks.html");
  }

  @Override
  protected void writeResponse(@NotNull HttpServletResponse response,
                               Map<File, String> torrentsAndArtifacts,
                               @NotNull SBuild build) throws IOException {
    JsonObject res = new JsonObject();

    res.add("hrefs", getLinks(torrentsAndArtifacts, build));
    res.add("icon", getIconObject());
    res.addProperty("title", "Download torrent file for this artifact");
    String jsonResult = myGson.toJson(res);

    response.setContentType("application/json");
    try (ServletOutputStream output = response.getOutputStream()) {
      output.print(jsonResult);
    }
  }

  private JsonElement getIconObject() {
    JsonObject res = new JsonObject();
    res.addProperty("name", "torrent");
    return res;
  }

  private JsonObject getLinks(Map<File, String> torrentsAndArtifacts, SBuild build) {
    JsonObject result = new JsonObject();
    for (Map.Entry<File, String> entry : torrentsAndArtifacts.entrySet()) {
      result.addProperty(entry.getValue(), getLinkToTorrent(build.getBuildId(), entry.getValue()));
    }
    return result;
  }

  private String getLinkToTorrent(long buildId, String relativePathToArtifact) {
    //gson will escape suspicious symbols
    return "/downloadTorrent.html?buildId=" + buildId + "&file=" + relativePathToArtifact;
  }
}
