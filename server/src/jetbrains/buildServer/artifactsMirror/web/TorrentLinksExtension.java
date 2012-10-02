/*
 * Copyright (c) 2000-2012 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package jetbrains.buildServer.artifactsMirror.web;

import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PlaceId;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.SimplePageExtension;
import org.jetbrains.annotations.NotNull;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * @author Maxim Podkolzine (maxim.podkolzine@jetbrains.com)
 * @since 8.0
 */
public class TorrentLinksExtension extends SimplePageExtension {
  private final PluginDescriptor myDescriptor;

  public TorrentLinksExtension(@NotNull PagePlaces pagePlaces,
                               @NotNull PluginDescriptor descriptor) {
    super(pagePlaces, PlaceId.ALL_PAGES_FOOTER, "torrent-links", descriptor.getPluginResourcesPath("torrentLinks.jsp"));
    myDescriptor = descriptor;
    addJsFile(descriptor.getPluginResourcesPath("torrentLinks.js"));
    register();
  }

  @Override
  public void fillModel(@NotNull Map<String, Object> model, @NotNull HttpServletRequest request) {
    super.fillModel(model, request);
    model.put("torrentIcon", myDescriptor.getPluginResourcesPath("torrent.png"));
  }
}
