/*
 * Copyright (c) 2000-2012 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package jetbrains.buildServer.artifactsMirror.web;

import jetbrains.buildServer.artifactsMirror.TorrentConfigurator;
import jetbrains.buildServer.artifactsMirror.TorrentTrackerManager;
import jetbrains.buildServer.controllers.admin.AdminPage;
import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * @author Maxim Podkolzine (maxim.podkolzine@jetbrains.com)
 * @since 8.0
 */
public class TrackerAdminPage extends AdminPage {
  private final TorrentTrackerManager myTorrentTrackerManager;
  private final TorrentConfigurator myTorrentConfigurator;

  protected TrackerAdminPage(@NotNull PagePlaces pagePlaces,
                             @NotNull PluginDescriptor descriptor,
                             @NotNull TorrentTrackerManager torrentTrackerManager,
                             @NotNull TorrentConfigurator torrentConfigurator) {
    super(pagePlaces, "torrentTracker", descriptor.getPluginResourcesPath("torrentTracker.jsp"), "Torrent Tracker");
    myTorrentTrackerManager = torrentTrackerManager;
    myTorrentConfigurator = torrentConfigurator;
    register();
  }

  @Override
  public void fillModel(@NotNull Map<String, Object> model, @NotNull HttpServletRequest request) {
    super.fillModel(model, request);
    model.put("torrentConfigurator", myTorrentConfigurator);
    model.put("announcedTorrentsNum", myTorrentTrackerManager.getAnnouncedTorrentsNum());
    model.put("connectedClientsNum", myTorrentTrackerManager.getConnectedClientsNum());
  }

  @NotNull
  public String getGroup() {
    return INTEGRATIONS_GROUP;
  }
}
