
package jetbrains.buildServer.torrent.web;

import jetbrains.buildServer.controllers.AuthorizationInterceptor;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.controllers.admin.AdminPage;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.torrent.ServerTorrentsDirectorySeeder;
import jetbrains.buildServer.torrent.TorrentConfigurator;
import jetbrains.buildServer.torrent.TorrentTrackerManager;
import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.text.DecimalFormat;
import java.util.Map;

/**
 * @author Maxim Podkolzine (maxim.podkolzine@jetbrains.com)
 * @since 8.0
 */
public class TorrentSettingsAdminPage extends AdminPage {
  private static final String TAB_ID = "torrentSettings";
  private final TorrentTrackerManager myTorrentTrackerManager;
  private final TorrentConfigurator myTorrentConfigurator;
  private final ServerTorrentsDirectorySeeder myTorrentSeeder;
  private final SecurityContext mySecurityContext;

  public TorrentSettingsAdminPage(@NotNull PagePlaces pagePlaces,
                                  @NotNull WebControllerManager controllerManager,
                                  @NotNull PluginDescriptor descriptor,
                                  @NotNull SecurityContext securityContext,
                                  @NotNull AuthorizationInterceptor authInterceptor,
                                  @NotNull TorrentTrackerManager torrentTrackerManager,
                                  @NotNull TorrentConfigurator torrentConfigurator,
                                  @NotNull ServerTorrentsDirectorySeeder torrentSeeder) {
    super(pagePlaces, TAB_ID, descriptor.getPluginResourcesPath("torrentSettings.jsp"), "Torrent Settings");
    myTorrentTrackerManager = torrentTrackerManager;
    myTorrentConfigurator = torrentConfigurator;
    mySecurityContext = securityContext;
    myTorrentSeeder = torrentSeeder;
    final String pagePath = "/admin/torrentSettings.html";
    authInterceptor.addPathBasedPermissionsChecker(pagePath, (holder, request) -> {
      if (!holder.isPermissionGrantedGlobally(Permission.CHANGE_SERVER_SETTINGS)) {
        throw new AccessDeniedException(holder, "You do not have enough permissions to access the torrent settings page");
      }
    });
    register();

    controllerManager.registerController(pagePath, new BaseController() {
      @Override
      protected ModelAndView doHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) throws Exception {
        if (request.getParameter("save") != null) {
          boolean seedingEnabled = request.getParameter("seedingEnabled") != null;
          boolean downloadEnabled = request.getParameter("downloadEnabled") != null;
          boolean agentSeedingEnabled = request.getParameter("agentSeedingEnabled") != null;
          boolean agentDownloadEnabled = request.getParameter("agentDownloadEnabled") != null;
          myTorrentConfigurator.setSeedingEnabled(seedingEnabled);
          myTorrentConfigurator.setDownloadEnabled(downloadEnabled);
          myTorrentConfigurator.setAgentDownloadEnabled(agentDownloadEnabled);
          myTorrentConfigurator.setAgentSeedingEnabled(agentSeedingEnabled);
          myTorrentConfigurator.persistConfiguration();
        }
        return new ModelAndView(new RedirectView(request.getContextPath() + "/admin/admin.html?item=" + TAB_ID));
      }
    });
  }

  @Override
  public boolean isAvailable(@NotNull HttpServletRequest request) {
    return mySecurityContext.getAuthorityHolder().isPermissionGrantedGlobally(Permission.CHANGE_SERVER_SETTINGS);
  }

  @Override
  public void fillModel(@NotNull Map<String, Object> model, @NotNull HttpServletRequest request) {
    super.fillModel(model, request);
    model.put("torrentConfigurator", myTorrentConfigurator);
    model.put("announcedTorrentsNum", myTorrentTrackerManager.getAnnouncedTorrentsNum());
    model.put("connectedClientsNum", myTorrentTrackerManager.getConnectedClientsNum());
    model.put("seededTorrentsNum", myTorrentSeeder.getNumberOfSeededTorrents());
    model.put("activePeersCount", myTorrentSeeder.getPeers().size());
    final double speedBytesPerSecond = myTorrentSeeder.getPeers().stream().mapToDouble(it -> it.getULRate().get()).sum();
    final DecimalFormat decimalFormat = new DecimalFormat("#.###");
    model.put("totalSpeedMegabytesPerSecond", decimalFormat.format(speedBytesPerSecond / (1024 * 1024)));
  }

  @NotNull
  public String getGroup() {
    return INTEGRATIONS_GROUP;
  }
}