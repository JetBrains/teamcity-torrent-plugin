/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package jetbrains.buildServer.artifactsMirror;

import jetbrains.buildServer.XmlRpcHandlerManager;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.BuildServerListener;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.PropertiesUtil;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

public class TorrentConfigurator implements TorrentTrackerConfiguration {
  public final static String TRACKER_ENABLED = "torrent.tracker.enabled";
  public final static String SEEDER_ENABLED = "torrent.seeder.enabled";
  public final static String FILE_SIZE_THRESHOLD = "file.size.threshold.mb";
  public final static String MAX_NUMBER_OF_SEEDED_TORRENTS = "max.seeded.torrents.number";

  private final ServerTorrentsDirectorySeeder mySeederManager;
  private final TorrentTrackerManager myTrackerManager;
  private final ServerPaths myServerPaths;
  private volatile Properties myConfiguration;

  public TorrentConfigurator(@NotNull ServerPaths serverPaths,
                             @NotNull EventDispatcher<BuildServerListener> dispatcher,
                             @NotNull ServerTorrentsDirectorySeeder torrentsDirectorySeeder,
                             @NotNull TorrentTrackerManager trackerManager,
                             @NotNull XmlRpcHandlerManager xmlRpcHandlerManager) {
    mySeederManager = torrentsDirectorySeeder;
    myTrackerManager = trackerManager;
    myServerPaths = serverPaths;
    dispatcher.addListener(new BuildServerAdapter() {
      @Override
      public void serverShutdown() {
        super.serverShutdown();
        mySeederManager.stopSeeder();
        myTrackerManager.stopTracker();
      }

      @Override
      public void serverStartup() {
        super.serverStartup();
        if (isEnabled(TRACKER_ENABLED)) {
          myTrackerManager.startTracker();
        }
        if (isEnabled(SEEDER_ENABLED)) {
          startSeeder();
        }
      }
    });

    File configFile = getConfigFile();
    if (!configFile.isFile()) {
      initConfigFile(configFile);
    }
    loadConfiguration(configFile);

    xmlRpcHandlerManager.addHandler(XmlRpcConstants.TORRENT_TRACKER_CONFIGURATION, this);
  }

  private void initConfigFile(File configFile) {
    try {
      Properties props = new Properties();
      props.setProperty(TRACKER_ENABLED, "true");
      props.setProperty(SEEDER_ENABLED, "true");
      props.setProperty(FILE_SIZE_THRESHOLD, "10");
      props.setProperty(MAX_NUMBER_OF_SEEDED_TORRENTS, "1000");
      PropertiesUtil.storeProperties(props, configFile, "");
    } catch (IOException e) {
      Loggers.SERVER.warn("Failed to create configuration file: " + configFile.getAbsolutePath() + ", error: " + e.toString());
    }
  }

  public void setTrackerEnabled(boolean enabled) {
    boolean changed = isTrackerEnabled() != enabled;
    myConfiguration.setProperty(TRACKER_ENABLED, Boolean.toString(enabled));
    if (changed) {
      if (enabled) {
        myTrackerManager.startTracker();
      } else {
        myTrackerManager.stopTracker();
      }
    }
  }

  public void setSeederEnabled(boolean enabled) {
    boolean changed = isSeederEnabled() != enabled;
    myConfiguration.setProperty(SEEDER_ENABLED, Boolean.toString(enabled));
    if (changed) {
      if (enabled) {
        startSeeder();
      } else {
        mySeederManager.stopSeeder();
      }
    }
  }

  public void setFileSizeThresholdMb(int threshold) {
    myConfiguration.setProperty(FILE_SIZE_THRESHOLD, Integer.toString(threshold));
    mySeederManager.setFileSizeThreshold(threshold);
  }

  public void setMaxNumberOfSeededTorrents(int number) {
    myConfiguration.setProperty(MAX_NUMBER_OF_SEEDED_TORRENTS, Integer.toString(number));
    mySeederManager.setMaxNumberOfSeededTorrents(number);
  }

  public int getMaxNumberOfSeededTorrents() {
    try {
      return Integer.parseInt(myConfiguration.getProperty(MAX_NUMBER_OF_SEEDED_TORRENTS));
    } catch (NumberFormatException e) {
      return 1000;
    }
  }

  private void startSeeder() {
    mySeederManager.setFileSizeThreshold(getFileSizeThresholdMb());
    mySeederManager.setMaxNumberOfSeededTorrents(getFileSizeThresholdMb());
    mySeederManager.startSeeder();
  }

  public int getFileSizeThresholdMb() {
    try {
      return Integer.parseInt(myConfiguration.getProperty(FILE_SIZE_THRESHOLD, "10"));
    } catch (NumberFormatException e) {
      return 10;
    }
  }

  public void persistConfiguration() throws IOException {
    PropertiesUtil.storeProperties(myConfiguration, getConfigFile(), "");
  }

  public boolean isTrackerEnabled() {
    return isEnabled(TRACKER_ENABLED);
  }

  public boolean isSeederEnabled() {
    return isEnabled(SEEDER_ENABLED);
  }

  private boolean isEnabled(@NotNull String configParam) {
    return StringUtil.isTrue(myConfiguration.getProperty(configParam, "true"));
  }

  private File getConfigFile() {
    return new File(myServerPaths.getConfigDir(), "artifacts-mirror.properties");
  }

  private void loadConfiguration(@NotNull File configFile) {
    Properties properties = new Properties();
    FileReader fileReader = null;
    try {
      fileReader = new FileReader(configFile);
      properties.load(fileReader);
    } catch (IOException e) {
      Loggers.SERVER.warn("Failed to load configuration file: " + configFile.getAbsolutePath() + ", error: " + e.toString());
    } finally {
      FileUtil.close(fileReader);
    }

    myConfiguration = properties;
  }

  public String getAnnounceUrl() {
    return myTrackerManager.getAnnounceUrl();
  }
}
