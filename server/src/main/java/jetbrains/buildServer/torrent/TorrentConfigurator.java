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

package jetbrains.buildServer.torrent;

import com.intellij.openapi.util.SystemInfo;
import com.turn.ttorrent.Constants;
import jetbrains.buildServer.RootUrlHolder;
import jetbrains.buildServer.XmlRpcHandlerManager;
import jetbrains.buildServer.configuration.ChangeListener;
import jetbrains.buildServer.configuration.ChangeObserver;
import jetbrains.buildServer.configuration.ChangeProvider;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.torrent.settings.LeechSettings;
import jetbrains.buildServer.torrent.settings.SeedSettings;
import jetbrains.buildServer.torrent.torrent.TorrentUtil;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.PropertiesUtil;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class TorrentConfigurator implements TorrentConfiguration, SeedSettings {

  private final static int DEFAULT_MAX_NUMBER_OF_SEEDED_TORRENTS = 10000;
  public final static String SEEDING_BY_AGENT_ENABLED_STORE_KEY = "teamcity.torrent.agent.seeding.enabled";

  private final ServerPaths myServerPaths;
  @NotNull
  private final RootUrlHolder myRootUrlHolder;
  private volatile Properties myConfiguration;
  private List<PropertyChangeListener> myChangeListeners = new ArrayList<PropertyChangeListener>();
  private String myAnnounceUrl;
  private final TorrentConfigurationWatcher myConfigurationWatcher;

  public TorrentConfigurator(@NotNull final ServerPaths serverPaths,
                             @NotNull final RootUrlHolder rootUrlHolder,
                             @NotNull final XmlRpcHandlerManager xmlRpcHandlerManager) {
    myServerPaths = serverPaths;
    myRootUrlHolder = rootUrlHolder;
    File configFile = getConfigFile();
    if (!configFile.isFile()) {
      initConfigFile(configFile);
    }
    loadConfiguration(configFile);

    xmlRpcHandlerManager.addHandler(XmlRpcConstants.TORRENT_CONFIGURATION, this);

    myConfigurationWatcher = new TorrentConfigurationWatcher();
    myConfigurationWatcher.registerListener(new ChangeListener() {
      public void changeOccured(String requestor) {
        setTrackerEnabled(TeamCityProperties.getBooleanOrTrue(TRACKER_ENABLED));
        setTrackerUsesDedicatedPort(TeamCityProperties.getBoolean(TRACKER_DEDICATED_PORT));
        setMaxNumberOfSeededTorrents(TeamCityProperties.getInteger(MAX_NUMBER_OF_SEEDED_TORRENTS, DEFAULT_MAX_NUMBER_OF_SEEDED_TORRENTS));
        long newFileSize = getFileSizeThreshold();
        setFileSizeThresholdMb(newFileSize);
        setMaxConnectionsCount(TeamCityProperties.getInteger(MAX_INCOMING_CONNECTIONS, DEFAULT_MAX_CONNECTIONS));
        setTrackerTorrentExpireTimeoutSec(TeamCityProperties.getInteger(TRACKER_TORRENT_EXPIRE_TIMEOUT, DEFAULT_TRACKER_TORRENT_EXPIRE_TIMEOUT));
        setAnnounceIntervalSec(TeamCityProperties.getInteger(ANNOUNCE_INTERVAL, DEFAULT_ANNOUNCE_INTERVAL));
        setAnnounceUrl(TeamCityProperties.getProperty(ANNOUNCE_URL, myAnnounceUrl == null ? "" : myAnnounceUrl));
        final int defaultBufferSize = SystemInfo.isWindows ? DEFAULT_BUFFER_SIZE_WINDOWS : -1;
        setReceiveBufferSize(TeamCityProperties.getInteger(RECEIVE_BUFFER_SIZE, defaultBufferSize));
        setSendBufferSize(TeamCityProperties.getInteger(SEND_BUFFER_SIZE, defaultBufferSize));
      }
    });

    myConfigurationWatcher.registerChangeProvider(myConfigurationWatcher);
    myConfigurationWatcher.start();
  }

  public void setReceiveBufferSize(int newValue) {
    propertyChanged(RECEIVE_BUFFER_SIZE, -1, newValue);
  }

  public void setSendBufferSize(int newValue) {
    propertyChanged(SEND_BUFFER_SIZE, -1, newValue);
  }

  private void initConfigFile(File configFile) {
    try {
      Properties props = new Properties();
      props.setProperty(USER_DOWNLOAD_ENABLED, "false");
      configFile.getParentFile().mkdirs();
      PropertiesUtil.storeProperties(props, configFile, "");
    } catch (IOException e) {
      Loggers.SERVER.warn("Failed to create configuration file: " + configFile.getAbsolutePath() + ", error: " + e.toString());
    }
  }

  private void setTrackerEnabled(boolean enabled) {
    boolean oldValue = TorrentUtil.getBooleanValue(myConfiguration, TRACKER_ENABLED, DEFAULT_TRACKER_ENABLED);
    if (oldValue != enabled) {
      myConfiguration.setProperty(TRACKER_ENABLED, String.valueOf(enabled));
      propertyChanged(TRACKER_ENABLED, oldValue, enabled);
    }
  }

  private long getFileSizeThreshold() {
    String strValue = TeamCityProperties.getProperty(FILE_SIZE_THRESHOLD, DEFAULT_FILE_SIZE_THRESHOLD);
    try {
      return StringUtil.parseFileSize(strValue);
    } catch (NumberFormatException e) {
      Loggers.SERVER.warnAndDebugDetails("incorrect value " + strValue + " for file size threshold property", e);
      try {
        return StringUtil.parseFileSize(DEFAULT_FILE_SIZE_THRESHOLD);
      } catch (NumberFormatException nfe) {
        Loggers.SERVER.warnAndDebugDetails("incorrect default value " + DEFAULT_FILE_SIZE_THRESHOLD + " for file size threshold property", nfe);
      }
    }
    return 10*1024*1024;//10mb
  }

  private void setFileSizeThresholdMb(long threshold) {
    String oldValueStr = myConfiguration.getProperty(FILE_SIZE_THRESHOLD, DEFAULT_FILE_SIZE_THRESHOLD);
    long oldValue = 0;
    try {
      oldValue = StringUtil.parseFileSize(oldValueStr);
    } catch (NumberFormatException ignored) {}
    if (oldValue != threshold) {
      myConfiguration.setProperty(FILE_SIZE_THRESHOLD, String.valueOf(threshold));
      propertyChanged(FILE_SIZE_THRESHOLD, oldValue, threshold);
    }
  }

  private void setMaxNumberOfSeededTorrents(int number) {
    int oldValue = TorrentUtil.getIntegerValue(myConfiguration, MAX_NUMBER_OF_SEEDED_TORRENTS, DEFAULT_MAX_NUMBER_OF_SEEDED_TORRENTS);
    if (oldValue != number) {
      myConfiguration.setProperty(MAX_NUMBER_OF_SEEDED_TORRENTS, String.valueOf(number));
      propertyChanged(MAX_NUMBER_OF_SEEDED_TORRENTS, oldValue, number);
    }
  }

  private void setAnnounceIntervalSec(int sec) {
    int oldValue = TorrentUtil.getIntegerValue(myConfiguration, ANNOUNCE_INTERVAL, DEFAULT_ANNOUNCE_INTERVAL);
    if (oldValue != sec) {
      myConfiguration.setProperty(ANNOUNCE_INTERVAL, String.valueOf(sec));
      propertyChanged(ANNOUNCE_INTERVAL, oldValue, sec);
    }
  }

  @Override
  public int getPieceHashingPoolSize() {
    return TeamCityProperties.getInteger(VALIDATOR_POOL_SIZE, DEFAULT_VALIDATOR_POOL_SIZE);
  }

  private void setMaxConnectionsCount(int maxConnectionsCount) {
    int oldValue = TorrentUtil.getIntegerValue(myConfiguration, MAX_INCOMING_CONNECTIONS, DEFAULT_MAX_CONNECTIONS);
    if (oldValue != maxConnectionsCount) {
      myConfiguration.setProperty(MAX_INCOMING_CONNECTIONS, String.valueOf(maxConnectionsCount));
      propertyChanged(MAX_INCOMING_CONNECTIONS, oldValue, maxConnectionsCount);
    }
  }

  private void setTrackerTorrentExpireTimeoutSec(int sec) {
    int oldValue = TorrentUtil.getIntegerValue(myConfiguration, TRACKER_TORRENT_EXPIRE_TIMEOUT, DEFAULT_TRACKER_TORRENT_EXPIRE_TIMEOUT);
    if (oldValue != sec) {
      myConfiguration.setProperty(TRACKER_TORRENT_EXPIRE_TIMEOUT, String.valueOf(sec));
      propertyChanged(TRACKER_TORRENT_EXPIRE_TIMEOUT, oldValue, sec);
    }
  }

  private void setTrackerUsesDedicatedPort(boolean enabled) {
    boolean oldValue = TorrentUtil.getBooleanValue(myConfiguration, TRACKER_DEDICATED_PORT, DEFAULT_TRACKER_DEDICATED_PORT);
    if (oldValue != enabled) {
      myConfiguration.setProperty(TRACKER_DEDICATED_PORT, String.valueOf(enabled));
      propertyChanged(TRACKER_DEDICATED_PORT, oldValue, enabled);
    }
  }

  public void setAgentDownloadEnabled(boolean enabled) {
    boolean oldValue = TorrentUtil.getBooleanValue(myConfiguration, LeechSettings.DOWNLOAD_ENABLED, LeechSettings.DEFAULT_DOWNLOAD_ENABLED);
    if (oldValue != enabled) {
      myConfiguration.setProperty(LeechSettings.DOWNLOAD_ENABLED, String.valueOf(enabled));
    }
  }

  public void setAgentSeedingEnabled(boolean enabled) {
    boolean oldValue = TorrentUtil.getBooleanValue(myConfiguration, SEEDING_BY_AGENT_ENABLED_STORE_KEY, DEFAULT_SEEDING_ENABLED);
    if (oldValue != enabled) {
      myConfiguration.setProperty(SEEDING_BY_AGENT_ENABLED_STORE_KEY, String.valueOf(enabled));
    }
  }

  public void setDownloadEnabled(boolean enabled) {
    boolean oldValue = TorrentUtil.getBooleanValue(myConfiguration, USER_DOWNLOAD_ENABLED, DEFAULT_DOWNLOAD_ENABLED);
    if (oldValue != enabled) {
      myConfiguration.setProperty(USER_DOWNLOAD_ENABLED, String.valueOf(enabled));
      propertyChanged(USER_DOWNLOAD_ENABLED, oldValue, enabled);
    }
  }

  public void setSeedingEnabled(boolean enabled) {
    boolean oldValue = TorrentUtil.getBooleanValue(myConfiguration, SEEDING_ENABLED, DEFAULT_SEEDING_ENABLED);
    if (oldValue != enabled) {
      myConfiguration.setProperty(SEEDING_ENABLED, String.valueOf(enabled));
      propertyChanged(SEEDING_ENABLED, oldValue, enabled);
    }
  }

  public boolean isDownloadEnabled() {
    return TorrentUtil.getBooleanValue(myConfiguration, USER_DOWNLOAD_ENABLED, DEFAULT_DOWNLOAD_ENABLED);
  }

  @Override
  public int getMaxNumberOfSeededTorrents() {
    String oldPropertyName = "torrent.max.seeded.number";
    if (!"".equals(TeamCityProperties.getProperty(oldPropertyName))) {
      return TeamCityProperties.getInteger(oldPropertyName, DEFAULT_MAX_NUMBER_OF_SEEDED_TORRENTS);
    }
    return TeamCityProperties.getInteger(MAX_NUMBER_OF_SEEDED_TORRENTS, DEFAULT_MAX_NUMBER_OF_SEEDED_TORRENTS);
  }

  @Override
  public long getFileSizeThresholdBytes() {
    boolean newValueNotExist = "".equals(TeamCityProperties.getProperty(FILE_SIZE_THRESHOLD));
    if (newValueNotExist) {
      String oldPropertyName = "torrent.file.size.threshold.mb";
      return TeamCityProperties.getInteger(oldPropertyName, 10);
    }
    return getFileSizeThreshold();
  }

  @Override
  public int getWorkerPoolSize() {
    return TeamCityProperties.getInteger(WORKER_POOL_SIZE, DEFAULT_WORKER_POOL_SIZE);
  }

  @Override
  public int getSocketTimeout() {
    int defaultTimeout = (int) TimeUnit.MILLISECONDS.toSeconds(Constants.DEFAULT_SOCKET_CONNECTION_TIMEOUT_MILLIS);
    return TeamCityProperties.getInteger(SOCKET_CONNECTION_TIMEOUT, defaultTimeout);
  }

  @Override
  public int getCleanupTimeout() {
    int defaultTimeout = (int) TimeUnit.MILLISECONDS.toSeconds(Constants.DEFAULT_CLEANUP_RUN_TIMEOUT_MILLIS);
    return TeamCityProperties.getInteger(CLEANUP_TIMEOUT, defaultTimeout);
  }

  @Override
  public int getMaxConnectionsCount() {
    return TeamCityProperties.getInteger(MAX_INCOMING_CONNECTIONS, DEFAULT_MAX_CONNECTIONS);
  }

  public int getAnnounceIntervalSec() {
    return TeamCityProperties.getInteger(ANNOUNCE_INTERVAL, DEFAULT_ANNOUNCE_INTERVAL);
  }

  public int getTrackerTorrentExpireTimeoutSec() {
    return TeamCityProperties.getInteger(TRACKER_TORRENT_EXPIRE_TIMEOUT, DEFAULT_TRACKER_TORRENT_EXPIRE_TIMEOUT);
  }

  public boolean isTrackerDedicatedPort() {
    return TeamCityProperties.getBoolean(TRACKER_DEDICATED_PORT);
  }

  public void persistConfiguration() throws IOException {
    PropertiesUtil.storeProperties(myConfiguration, getConfigFile(), "");
  }

  public boolean isTrackerEnabled() {
    return TeamCityProperties.getBooleanOrTrue(TRACKER_ENABLED);
  }

  private File getConfigFile() {
    return new File(myServerPaths.getConfigDir(), "torrent-plugin.properties");
  }

  private void loadConfiguration(@NotNull File configFile) {
    Properties properties = new Properties();
    FileReader fileReader = null;
    try {
      fileReader = new FileReader(configFile);
      properties.load(fileReader);
      if (properties.get(USER_DOWNLOAD_ENABLED) == null) {
        properties.put(USER_DOWNLOAD_ENABLED, Boolean.FALSE.toString());
      }
      myConfiguration = properties;
    } catch (IOException e) {
      Loggers.SERVER.warn("Failed to load configuration file: " + configFile.getAbsolutePath() + ", error: " + e.toString());
    } finally {
      FileUtil.close(fileReader);
    }
  }

  @Override
  public boolean isSeedingEnabled() {
    return TorrentUtil.getBooleanValue(myConfiguration, SEEDING_ENABLED, DEFAULT_SEEDING_ENABLED);
  }

  public boolean isAgentSeedingEnabled() {
    return TorrentUtil.getBooleanValue(myConfiguration, SEEDING_BY_AGENT_ENABLED_STORE_KEY, DEFAULT_SEEDING_ENABLED);
  }

  public boolean isAgentDownloadingEnabled() {
    return TorrentUtil.getBooleanValue(myConfiguration, LeechSettings.DOWNLOAD_ENABLED, LeechSettings.DEFAULT_DOWNLOAD_ENABLED);
  }

  @Override
  public String getAnnounceUrl() {
    if (isTrackerEnabled())
      return myAnnounceUrl;
    else
      return null;
  }

  public void setAnnounceUrl(@NotNull final String announceUrl) {
    final String oldAnnounceUrl = myAnnounceUrl;
    myAnnounceUrl = announceUrl;
    propertyChanged(ANNOUNCE_URL, oldAnnounceUrl, announceUrl);
  }

  @NotNull
  @Override
  public String getOwnTorrentAddress() {
    return myConfiguration.getProperty(OWN_ADDRESS, "");
  }

  @NotNull
  @Override
  public String getAgentAddressPrefix() {
    return "";
  }

  @NotNull
  public String getResolvedOwnAddress() {
    String hostName = getOwnTorrentAddress();
    if (!hostName.isEmpty()) return hostName;

    try {
      return InetAddress.getLocalHost().getCanonicalHostName();
    } catch (UnknownHostException e) {
      return "localhost";
    }
  }

  @NotNull
  public String getServerAddress() {
    return myRootUrlHolder.getRootUrl();
  }

  public void addPropertyChangeListener(@NotNull final PropertyChangeListener listener) {
    myChangeListeners.add(listener);
  }

  public void removePropertyChangeListener(@NotNull final PropertyChangeListener listener) {
    myChangeListeners.remove(listener);
  }

  public void propertyChanged(String changedPropertyName, Object oldValue, Object newValue) {
    PropertyChangeEvent event = new PropertyChangeEvent(this, changedPropertyName, oldValue, newValue);
    for (PropertyChangeListener listener : myChangeListeners) {
      try {
        listener.propertyChange(event);
      } catch (Exception ex) {
      }
    }
  }

  //4tests
  protected TorrentConfigurationWatcher getConfigurationWatcher() {
    return myConfigurationWatcher;
  }

  public void stopWatcher() {
    myConfigurationWatcher.stop();
    myConfigurationWatcher.clear();
  }

  /**
   * @author Sergey.Pak
   * Date: 10/15/13
   * Time: 12:28 PM
   */
  public class TorrentConfigurationWatcher extends ChangeObserver implements ChangeProvider {

    private Map<String, String> myStoredProperties = new HashMap<String, String>();


    public TorrentConfigurationWatcher() {
      super(10000);
      resetChanged();
    }

    public boolean changesDetected() {
      for (String s : myStoredProperties.keySet()) {
        if (!StringUtil.areEqual(myStoredProperties.get(s), TeamCityProperties.getProperty(s)))
          return true;
      }
      return false;
    }

    public void resetChanged() {
      myStoredProperties.put(TRACKER_ENABLED, TeamCityProperties.getProperty(TRACKER_ENABLED));
      myStoredProperties.put(OWN_ADDRESS, TeamCityProperties.getProperty(OWN_ADDRESS));
      myStoredProperties.put(FILE_SIZE_THRESHOLD, TeamCityProperties.getProperty(FILE_SIZE_THRESHOLD));
      myStoredProperties.put(ANNOUNCE_INTERVAL, TeamCityProperties.getProperty(ANNOUNCE_INTERVAL));
      myStoredProperties.put(TRACKER_TORRENT_EXPIRE_TIMEOUT, TeamCityProperties.getProperty(TRACKER_TORRENT_EXPIRE_TIMEOUT));
      myStoredProperties.put(MAX_NUMBER_OF_SEEDED_TORRENTS, TeamCityProperties.getProperty(MAX_NUMBER_OF_SEEDED_TORRENTS));
      myStoredProperties.put(TRACKER_DEDICATED_PORT, TeamCityProperties.getProperty(TRACKER_DEDICATED_PORT));
      myStoredProperties.put(RECEIVE_BUFFER_SIZE, TeamCityProperties.getProperty(RECEIVE_BUFFER_SIZE));
      myStoredProperties.put(SEND_BUFFER_SIZE, TeamCityProperties.getProperty(SEND_BUFFER_SIZE));
    }

    @Nullable
    public String getRequestor() {
      return TorrentConfigurationWatcher.class.getName();
    }

    @NotNull
    public String describe(boolean verbose) {
      return "TeamCity Torrent plugin configuration file watcher";
    }
  }
}
