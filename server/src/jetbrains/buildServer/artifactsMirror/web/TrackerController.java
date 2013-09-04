package jetbrains.buildServer.artifactsMirror.web;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.StreamUtil;
import com.turn.ttorrent.tracker.TrackedTorrent;
import com.turn.ttorrent.tracker.Tracker;
import com.turn.ttorrent.tracker.TrackerRequestProcessor;
import jetbrains.buildServer.NetworkUtil;
import jetbrains.buildServer.artifactsMirror.TorrentConfigurator;
import jetbrains.buildServer.artifactsMirror.TorrentTrackerManager;
import jetbrains.buildServer.controllers.AuthorizationInterceptor;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Sergey.Pak
 *         Date: 8/12/13
 *         Time: 4:49 PM
 */
public class TrackerController extends BaseController {

  private final static Logger LOG = Logger.getInstance(TrackerController.class.getName());

  public static final String PATH = "/trackerAnnounce.html";

  private final TorrentTrackerManager myTrackerManager;


  public TrackerController(@NotNull final WebControllerManager controllerManager,
                           @NotNull final TorrentTrackerManager trackerManager,
                           @NotNull final AuthorizationInterceptor interceptor) {
    controllerManager.registerController(PATH, this);
    myTrackerManager = trackerManager;
    interceptor.addPathNotRequiringAuth(PATH);
  }

  @Nullable
  @Override
  protected ModelAndView doHandle(@NotNull HttpServletRequest request, @NotNull final HttpServletResponse response) throws Exception {
    if (myTrackerManager.isTrackerUsesDedicatedPort() || !myTrackerManager.isTrackerRunning()){
      response.setStatus(HttpServletResponse.SC_NOT_FOUND); // return 404, if tracker uses dedicated port or not started
    }
    if (request.getQueryString() == null) {
      return null;
    }
    final String uri = request.getRequestURL().append("?").append(request.getQueryString()).toString();
    myTrackerManager.getTrackerService().process(uri, request.getLocalAddr(), new TrackerRequestProcessor.RequestHandler() {
      public void serveResponse(int code, String description, ByteBuffer responseData) {
        response.setStatus(code);
        try {
          final WritableByteChannel channel = Channels.newChannel(response.getOutputStream());
          channel.write(responseData);
        } catch (IOException e) {}
      }

      public ConcurrentMap<String, TrackedTorrent> getTorrentsMap() {
        return myTrackerManager.getTorrents();
      }
    });
    return null;
  }

}
