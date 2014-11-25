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

import jetbrains.buildServer.XmlRpcHandlerManager;
import jetbrains.buildServer.configuration.ChangeListener;
import jetbrains.buildServer.configuration.ChangeObserver;
import jetbrains.buildServer.configuration.ChangeProvider;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.impl.ServerSettings;
import jetbrains.buildServer.torrent.torrent.TorrentUtil;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.PropertiesUtil;
import org.apache.commons.lang.StringUtils;
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

public class TorrentConfigurator implements TorrentConfiguration {

  private final ServerPaths myServerPaths;
  private final ServerSettings myServerSettings;
  private volatile Properties myConfiguration;
  private List<PropertyChangeListener> myChangeListeners = new ArrayList<PropertyChangeListener>();
  private String myAnnounceUrl;
  private final TorrentConfigurationWatcher myConfigurationWatcher;

  public TorrentConfigurator(@NotNull final ServerPaths serverPaths,
                             @NotNull final ServerSettings serverSettings,
                             @NotNull final XmlRpcHandlerManager xmlRpcHandlerManager) {
    myServerPaths = serverPaths;
    myServerSettings = serverSettings;
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
        setSeederEnabled(TeamCityProperties.getBooleanOrTrue(SEEDER_ENABLED));
        setTrackerUsesDedicatedPort(TeamCityProperties.getBoolean(TRACKER_DEDICATED_PORT));
        setMaxNumberOfSeededTorrents(TeamCityProperties.getInteger(MAX_NUMBER_OF_SEEDED_TORRENTS, DEFAULT_MAX_NUMBER_OF_SEEDED_TORRENTS));
        setFileSizeThresholdMb(TeamCityProperties.getInteger(FILE_SIZE_THRESHOLD, DEFAULT_FILE_SIZE_THRESHOLD));
        setTrackerTorrentExpireTimeoutSec(TeamCityProperties.getInteger(TRACKER_TORRENT_EXPIRE_TIMEOUT, DEFAULT_TRACKER_TORRENT_EXPIRE_TIMEOUT));
        setAnnounceIntervalSec(TeamCityProperties.getInteger(ANNOUNCE_INTERVAL, DEFAULT_ANNOUNCE_INTERVAL));
        setAnnounceUrl(TeamCityProperties.getProperty(ANNOUNCE_URL));
      }
    });

    myConfigurationWatcher.registerChangeProvider(myConfigurationWatcher);
    myConfigurationWatcher.start();
  }

  private void initConfigFile(File configFile) {
    try {
      Properties props = new Properties();
      props.setProperty(TRANSPORT_ENABLED, "false");
      props.setProperty(DOWNLOAD_ENABLED, "false");
      props.setProperty(TORRENT_ENABLED, "false");
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

  private void setSeederEnabled(boolean enabled) {
    boolean oldValue = TorrentUtil.getBooleanValue(myConfiguration, SEEDER_ENABLED, DEFAULT_SEEDER_ENABLED);
    if  (oldValue != enabled){
      myConfiguration.setProperty(SEEDER_ENABLED, String.valueOf(enabled));
      propertyChanged(SEEDER_ENABLED, oldValue, enabled);
    }
  }

  private void setFileSizeThresholdMb(int threshold) {
    int oldValue = TorrentUtil.getIntegerValue(myConfiguration, FILE_SIZE_THRESHOLD, DEFAULT_FILE_SIZE_THRESHOLD);
    if (oldValue != threshold){
      myConfiguration.setProperty(FILE_SIZE_THRESHOLD, String.valueOf(threshold));
      propertyChanged(FILE_SIZE_THRESHOLD, oldValue, threshold);
    }
  }

  private void setMaxNumberOfSeededTorrents(int number) {
    int oldValue = TorrentUtil.getIntegerValue(myConfiguration, MAX_NUMBER_OF_SEEDED_TORRENTS, DEFAULT_MAX_NUMBER_OF_SEEDED_TORRENTS);
    if (oldValue != number){
      myConfiguration.setProperty(MAX_NUMBER_OF_SEEDED_TORRENTS, String.valueOf(number));
      propertyChanged(MAX_NUMBER_OF_SEEDED_TORRENTS, oldValue, number);
    }
  }

  private void setAnnounceIntervalSec(int sec){
    int oldValue = TorrentUtil.getIntegerValue(myConfiguration, ANNOUNCE_INTERVAL, DEFAULT_ANNOUNCE_INTERVAL);
    if (oldValue != sec){
      myConfiguration.setProperty(ANNOUNCE_INTERVAL, String.valueOf(sec));
      propertyChanged(ANNOUNCE_INTERVAL, oldValue, sec);
    }
  }

  private void setTrackerTorrentExpireTimeoutSec(int sec){
    int oldValue = TorrentUtil.getIntegerValue(myConfiguration, TRACKER_TORRENT_EXPIRE_TIMEOUT, DEFAULT_TRACKER_TORRENT_EXPIRE_TIMEOUT);
    if (oldValue != sec){
      myConfiguration.setProperty(TRACKER_TORRENT_EXPIRE_TIMEOUT, String.valueOf(sec));
      propertyChanged(TRACKER_TORRENT_EXPIRE_TIMEOUT, oldValue, sec);
    }
  }

  private void setTrackerUsesDedicatedPort(boolean enabled){
    boolean oldValue = TorrentUtil.getBooleanValue(myConfiguration, TRACKER_DEDICATED_PORT, DEFAULT_TRACKER_DEDICATED_PORT);
    if  (oldValue != enabled){
      myConfiguration.setProperty(TRACKER_DEDICATED_PORT, String.valueOf(enabled));
      propertyChanged(TRACKER_DEDICATED_PORT, oldValue, enabled);
    }
  }

  public void setTransportEnabled(boolean enabled){
    boolean oldValue = TorrentUtil.getBooleanValue(myConfiguration, TRANSPORT_ENABLED, DEFAULT_TRANSPORT_ENABLED);
    if  (oldValue != enabled){
      myConfiguration.setProperty(TRANSPORT_ENABLED, String.valueOf(enabled));
      propertyChanged(TRANSPORT_ENABLED, oldValue, enabled);
    }
  }

  public void setDownloadEnabled(boolean enabled){
    boolean oldValue = TorrentUtil.getBooleanValue(myConfiguration, DOWNLOAD_ENABLED, DEFAULT_DOWNLOAD_ENABLED);
    if  (oldValue != enabled){
      myConfiguration.setProperty(DOWNLOAD_ENABLED, String.valueOf(enabled));
      propertyChanged(DOWNLOAD_ENABLED, oldValue, enabled);
    }
  }

  public void setTorrentEnabled(boolean enabled){
    boolean oldValue = TorrentUtil.getBooleanValue(myConfiguration, TORRENT_ENABLED, DEFAULT_TORRENT_ENABLED);
    if  (oldValue != enabled){
      myConfiguration.setProperty(TORRENT_ENABLED, String.valueOf(enabled));
      propertyChanged(TORRENT_ENABLED, oldValue, enabled);
    }
  }

  public boolean isDownloadEnabled(){
    return TorrentUtil.getBooleanValue(myConfiguration, DOWNLOAD_ENABLED, DEFAULT_DOWNLOAD_ENABLED);
  }

  public int getMaxNumberOfSeededTorrents() {
    return TeamCityProperties.getInteger(MAX_NUMBER_OF_SEEDED_TORRENTS, DEFAULT_MAX_NUMBER_OF_SEEDED_TORRENTS);
  }

  public int getFileSizeThresholdMb() {
    return TeamCityProperties.getInteger(FILE_SIZE_THRESHOLD, DEFAULT_FILE_SIZE_THRESHOLD);
  }

  public int getAnnounceIntervalSec() {
    return TeamCityProperties.getInteger(ANNOUNCE_INTERVAL, DEFAULT_ANNOUNCE_INTERVAL);
  }

  public int getTrackerTorrentExpireTimeoutSec() {
    return TeamCityProperties.getInteger(TRACKER_TORRENT_EXPIRE_TIMEOUT, DEFAULT_TRACKER_TORRENT_EXPIRE_TIMEOUT);
  }

  public boolean isTrackerDedicatedPort(){
    return TeamCityProperties.getBoolean(TRACKER_DEDICATED_PORT);
  }

  public void persistConfiguration() throws IOException {
    PropertiesUtil.storeProperties(myConfiguration, getConfigFile(), "");
  }

  public boolean isTrackerEnabled() {
    return TeamCityProperties.getBooleanOrTrue(TRACKER_ENABLED);
  }

  public boolean isSeederEnabled() {
    return TeamCityProperties.getBooleanOrTrue(SEEDER_ENABLED) && isTorrentEnabled();
  }

  public boolean isTransportEnabled(){
    return TorrentUtil.getBooleanValue(myConfiguration, TRANSPORT_ENABLED, DEFAULT_TRANSPORT_ENABLED);
  }

  public boolean isTorrentEnabled() {
    return TorrentUtil.getBooleanValue(myConfiguration, TORRENT_ENABLED, DEFAULT_TORRENT_ENABLED);
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
      if (properties.get(TRANSPORT_ENABLED) == null){
        properties.put(TRANSPORT_ENABLED, Boolean.FALSE.toString());
      }
      if (properties.get(DOWNLOAD_ENABLED) == null){
        properties.put(DOWNLOAD_ENABLED, Boolean.FALSE.toString());
      }
      myConfiguration = properties;
      myConfiguration.put(TORRENT_ENABLED, String.valueOf(isDownloadEnabled() || isTransportEnabled()));
    } catch (IOException e) {
      Loggers.SERVER.warn("Failed to load configuration file: " + configFile.getAbsolutePath() + ", error: " + e.toString());
    } finally {
      FileUtil.close(fileReader);
    }

  }

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

  @Nullable
  public String getOwnAddress() {
    return myConfiguration.getProperty(OWN_ADDRESS);
  }

  @NotNull
  public String getResolvedOwnAddress() {
    String hostName = getOwnAddress();
    if (hostName != null) return hostName;

    try {
      return InetAddress.getLocalHost().getCanonicalHostName();
    } catch (UnknownHostException e) {
      return "localhost";
    }
  }

  @NotNull
  public String getServerAddress(){
    return myServerSettings.getRootUrl();
  }

  public void addPropertyChangeListener(@NotNull final PropertyChangeListener listener){
    myChangeListeners.add(listener);
  }

  public void removePropertyChangeListener(@NotNull final PropertyChangeListener listener){
    myChangeListeners.remove(listener);
  }

  public void propertyChanged(String changedPropertyName, Object oldValue, Object newValue){
    PropertyChangeEvent event = new PropertyChangeEvent(this, changedPropertyName, oldValue, newValue);
    for (PropertyChangeListener listener : myChangeListeners) {
      try {
      listener.propertyChange(event);
      } catch (Exception ex){}
    }
  }

  //4tests
  protected TorrentConfigurationWatcher getConfigurationWatcher() {
    return myConfigurationWatcher;
  }

  /**
   * @author Sergey.Pak
   *         Date: 10/15/13
   *         Time: 12:28 PM
   */
  public class TorrentConfigurationWatcher extends ChangeObserver implements ChangeProvider {

    private Map<String, String> myStoredProperties = new HashMap<String, String>();


    public TorrentConfigurationWatcher() {
      super(10000);
      resetChanged();
    }

    public boolean changesDetected() {
      for (String s : myStoredProperties.keySet()) {
        if (!StringUtils.equals(myStoredProperties.get(s), TeamCityProperties.getProperty(s)))
          return true;
      }
      return false;
    }

    public void resetChanged() {
      myStoredProperties.put(TRACKER_ENABLED, TeamCityProperties.getProperty(TRACKER_ENABLED));
      myStoredProperties.put(OWN_ADDRESS, TeamCityProperties.getProperty(OWN_ADDRESS));
      myStoredProperties.put(SEEDER_ENABLED, TeamCityProperties.getProperty(SEEDER_ENABLED));
      myStoredProperties.put(FILE_SIZE_THRESHOLD, TeamCityProperties.getProperty(FILE_SIZE_THRESHOLD));
      myStoredProperties.put(TRANSPORT_ENABLED, TeamCityProperties.getProperty(TRANSPORT_ENABLED));
      myStoredProperties.put(ANNOUNCE_INTERVAL, TeamCityProperties.getProperty(ANNOUNCE_INTERVAL));
      myStoredProperties.put(TRACKER_TORRENT_EXPIRE_TIMEOUT, TeamCityProperties.getProperty(TRACKER_TORRENT_EXPIRE_TIMEOUT));
      myStoredProperties.put(MAX_NUMBER_OF_SEEDED_TORRENTS, TeamCityProperties.getProperty(MAX_NUMBER_OF_SEEDED_TORRENTS));
      myStoredProperties.put(TRACKER_DEDICATED_PORT, TeamCityProperties.getProperty(TRACKER_DEDICATED_PORT));
    }

    @Nullable
    public String getRequestor() {
      return TorrentConfigurationWatcher.class.getName();
    }
  }
}
