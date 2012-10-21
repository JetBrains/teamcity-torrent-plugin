/*
 * Copyright (c) 2000-2012 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package jetbrains.buildServer.artifactsMirror.web;

import jetbrains.buildServer.artifactsMirror.ServerTorrentsDirectorySeeder;
import jetbrains.buildServer.artifactsMirror.TorrentConfigurator;
import jetbrains.buildServer.artifactsMirror.TorrentTrackerManager;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.controllers.admin.AdminPage;
import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * @author Maxim Podkolzine (maxim.podkolzine@jetbrains.com)
 * @since 8.0
 */
public class TrackerAdminPage extends AdminPage {
  private static final String TAB_ID = "torrentTracker";
  private final TorrentTrackerManager myTorrentTrackerManager;
  private final TorrentConfigurator myTorrentConfigurator;
  private final ServerTorrentsDirectorySeeder myTorrentSeeder;

  protected TrackerAdminPage(@NotNull PagePlaces pagePlaces,
                             @NotNull WebControllerManager controllerManager,
                             @NotNull PluginDescriptor descriptor,
                             @NotNull TorrentTrackerManager torrentTrackerManager,
                             @NotNull TorrentConfigurator torrentConfigurator,
                             @NotNull ServerTorrentsDirectorySeeder torrentSeeder) {
    super(pagePlaces, TAB_ID, descriptor.getPluginResourcesPath("torrentTracker.jsp"), "Torrent Tracker");
    myTorrentTrackerManager = torrentTrackerManager;
    myTorrentConfigurator = torrentConfigurator;
    myTorrentSeeder = torrentSeeder;
    register();

    controllerManager.registerController("/admin/torrentTrackerSettings.html", new BaseController() {
      @Override
      protected ModelAndView doHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) throws Exception {
        if (request.getParameter("save") != null) {
          boolean trackerEnabled = request.getParameter("trackerEnabled") != null;
          boolean seederEnabled = request.getParameter("seederEnabled") != null;
          String threshold = request.getParameter("fileSizeThresholdMb");
          myTorrentConfigurator.setTrackerEnabled(trackerEnabled);
          myTorrentConfigurator.setSeederEnabled(seederEnabled);
          try {
            myTorrentConfigurator.setFileSizeThresholdMb(Integer.parseInt(threshold));
          } catch (NumberFormatException e) {
            //
          }
          myTorrentConfigurator.persistConfiguration();
        }
        return new ModelAndView(new RedirectView("/admin/admin.html?item=" + TAB_ID));
      }
    });
  }

  @Override
  public void fillModel(@NotNull Map<String, Object> model, @NotNull HttpServletRequest request) {
    super.fillModel(model, request);
    model.put("torrentConfigurator", myTorrentConfigurator);
    model.put("announcedTorrentsNum", myTorrentTrackerManager.getAnnouncedTorrentsNum());
    model.put("connectedClientsNum", myTorrentTrackerManager.getConnectedClientsNum());
    model.put("seededTorrentsNum", myTorrentSeeder.getNumberOfSeededTorrents());
  }

  @NotNull
  public String getGroup() {
    return INTEGRATIONS_GROUP;
  }
}
