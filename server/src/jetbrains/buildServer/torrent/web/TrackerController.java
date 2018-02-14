package jetbrains.buildServer.torrent.web;

import com.intellij.openapi.diagnostic.Logger;
import com.turn.ttorrent.tracker.MultiAnnounceRequestProcessor;
import com.turn.ttorrent.tracker.TrackedTorrent;
import com.turn.ttorrent.tracker.TrackerRequestProcessor;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.torrent.TorrentTrackerManager;
import jetbrains.buildServer.controllers.AuthorizationInterceptor;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * @author Sergey.Pak
 *         Date: 8/12/13
 *         Time: 4:49 PM
 */
public class TrackerController extends BaseController {

  private final static Logger LOG = Logger.getInstance(TrackerController.class.getName());

  public static final String PATH = "/trackerAnnounce.html";

  private final TorrentTrackerManager myTrackerManager;
  private final MultiAnnounceRequestProcessor myMultiAnnounceRequestProcessor;


  public TrackerController(@NotNull final WebControllerManager controllerManager,
                           @NotNull final TorrentTrackerManager trackerManager,
                           @NotNull final AuthorizationInterceptor interceptor) {
    controllerManager.registerController(PATH, this);
    myTrackerManager = trackerManager;
    myMultiAnnounceRequestProcessor = new MultiAnnounceRequestProcessor(trackerManager.getTrackerService());
    interceptor.addPathNotRequiringAuth(PATH);
  }

  @Nullable
  @Override
  protected ModelAndView doHandle(@NotNull HttpServletRequest request, @NotNull final HttpServletResponse response) throws Exception {
    try {
      if (myTrackerManager.isTrackerUsesDedicatedPort() || !myTrackerManager.isTrackerRunning()) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND); // return 404, if tracker uses dedicated port or not started
        return null;
      }
      if ("POST".equalsIgnoreCase(request.getMethod())) {
        final String body = request.getReader().lines().collect(Collectors.joining("\n"));
        myMultiAnnounceRequestProcessor.process(body, request.getRequestURL().toString(), request.getRemoteAddr(), getRequestHandler(response));
      } else {
        final String queryString = request.getQueryString();
        if (queryString == null) {
          return null;
        }
        final String uri = request.getRequestURL().append("?").append(queryString).toString();
        myTrackerManager.getTrackerService().process(uri, request.getRemoteAddr(), getRequestHandler(response));
      }
    } catch (Throwable e) {
      Loggers.SERVER.warnAndDebugDetails("error in processing torrent announce. Request: " + request, e);
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
    return null;
  }

  @NotNull
  private TrackerRequestProcessor.RequestHandler getRequestHandler(@NotNull HttpServletResponse response) {
    return new TrackerRequestProcessor.RequestHandler() {
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
    };
  }

}
