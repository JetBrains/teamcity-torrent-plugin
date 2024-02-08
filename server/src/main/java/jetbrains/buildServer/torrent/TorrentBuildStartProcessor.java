

package jetbrains.buildServer.torrent;

import jetbrains.buildServer.serverSide.BuildStartContext;
import jetbrains.buildServer.serverSide.BuildStartContextProcessor;
import jetbrains.buildServer.torrent.settings.LeechSettings;
import jetbrains.buildServer.torrent.settings.SeedSettings;
import org.jetbrains.annotations.NotNull;

public class TorrentBuildStartProcessor implements BuildStartContextProcessor {

  @NotNull
  private final TorrentConfigurator myConfigurator;

  public TorrentBuildStartProcessor(@NotNull TorrentConfigurator configurator) {
    myConfigurator = configurator;
  }

  @Override
  public void updateParameters(@NotNull BuildStartContext context) {
    addParameterIfNotExist(LeechSettings.DOWNLOAD_ENABLED, myConfigurator.isAgentDownloadingEnabled(), context);
    addParameterIfNotExist(SeedSettings.SEEDING_ENABLED, myConfigurator.isAgentSeedingEnabled(), context);
  }

  private void addParameterIfNotExist(String name, boolean value, BuildStartContext context) {
    if (!context.getSharedParameters().containsKey(name)) {
      context.addSharedParameter(name, String.valueOf(value));
    }
  }
}