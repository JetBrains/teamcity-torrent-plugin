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
