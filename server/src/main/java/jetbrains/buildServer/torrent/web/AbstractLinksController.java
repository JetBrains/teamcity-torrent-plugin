

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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

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
        Map<File, String> torrentsAndArtifacts = getArtifactsWithTorrents(baseDir, torrentFiles);

        writeResponse(response, torrentsAndArtifacts, build);
        response.setStatus(HttpServletResponse.SC_OK);
      }
    } catch (Exception e) {
      // ignore
    }

    return null;
  }

  protected abstract void writeResponse(@NotNull HttpServletResponse response,
                                        Map<File, String> torrentsAndArtifacts,
                                        @NotNull SBuild build) throws IOException;

  @NotNull
  private Map<File, String> getArtifactsWithTorrents(@NotNull File baseDir, @NotNull Collection<File> torrentFiles) {
    Map<File, String> torrentFilesAndArtifactsPath = new HashMap<>();
    for (File f: torrentFiles) {
      String path = FileUtil.getRelativePath(baseDir, f);
      if (path == null) continue;
      path = path.replace('\\', '/');
      torrentFilesAndArtifactsPath.put(f, path.substring(0, path.length() - TorrentUtil.TORRENT_FILE_SUFFIX.length()));
    }
    return torrentFilesAndArtifactsPath;
  }
}