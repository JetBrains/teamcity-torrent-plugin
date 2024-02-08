
package jetbrains.buildServer.torrent.web;

import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PlaceId;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.SimplePageExtension;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim Podkolzine (maxim.podkolzine@jetbrains.com)
 * @since 8.0
 */
public class TorrentLinksExtension extends SimplePageExtension {
  public TorrentLinksExtension(@NotNull PagePlaces pagePlaces,
                               @NotNull PluginDescriptor descriptor) {
    super(pagePlaces, PlaceId.ALL_PAGES_HEADER, "torrent-links", descriptor.getPluginResourcesPath("torrentLinks.jsp"));
    addCssFile(descriptor.getPluginResourcesPath("torrentLinks.css"));
    addJsFile(descriptor.getPluginResourcesPath("torrentLinks.js"));
    register();
  }
}