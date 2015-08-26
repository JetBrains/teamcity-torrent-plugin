/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.torrent.seeder.ParentDirConverter;
import jetbrains.buildServer.torrent.seeder.TorrentsSeeder;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class AgentTorrentsSeeder extends TorrentsSeeder {
  public AgentTorrentsSeeder(@NotNull final BuildAgentConfiguration agentConfiguration) {
    super(agentConfiguration.getCacheDirectory(Constants.TORRENTS_DIRNAME), TeamCityProperties.getInteger("teamcity.torrents.agent.maxSeededTorrents", 5000), new ParentDirConverter() {
      @NotNull
      @Override
      public File getParentDir() {
        return agentConfiguration.getSystemDirectory();
      }
    });
  }
}
