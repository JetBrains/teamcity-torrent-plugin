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

import jetbrains.buildServer.RootUrlHolder;
import jetbrains.buildServer.XmlRpcHandlerManager;
import jetbrains.buildServer.artifactsMirror.web.TrackerController;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.BuildServerListener;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.serverSide.impl.ServerSettings;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.PropertiesUtil;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class TorrentConfigurator implements TorrentTrackerConfiguration {
  public final static String TRACKER_ENABLED = "torrent.tracker.enabled";
  public final static String OWN_ADDRESS = "torrent.ownAddress";
  public final static String SEEDER_ENABLED = "torrent.seeder.enabled";
  public final static String FILE_SIZE_THRESHOLD = "file.size.threshold.mb";
  public final static String ANNOUNCE_INTERVAL = "announce.interval.sec";
  public final static String TRACKER_TORRENT_EXPIRE_TIMEOUT = "tracker.torrent.expire.timeout.sec";
  public final static String MAX_NUMBER_OF_SEEDED_TORRENTS = "max.seeded.torrents.number";
  public final static String TRACKER_USES_DEDICATED_PORT ="tracker.dedicated.port";
  // this is fake option to multicast announce url changes;
  public static final String ANNOUNCE_URL = "announce.url";

  private final ServerPaths myServerPaths;
  private final ServerSettings myServerSettings;
  private volatile Properties myConfiguration;
  private List<PropertyChangeListener> myChangeListeners = new ArrayList<PropertyChangeListener>();
  private String myAnnounceUrl;

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

    xmlRpcHandlerManager.addHandler(XmlRpcConstants.TORRENT_TRACKER_CONFIGURATION, this);
  }

  private void initConfigFile(File configFile) {
    try {
      Properties props = new Properties();
      props.setProperty(TRACKER_ENABLED, "false");
      props.setProperty(SEEDER_ENABLED, "false");
      props.setProperty(FILE_SIZE_THRESHOLD, "10");
      props.setProperty(MAX_NUMBER_OF_SEEDED_TORRENTS, "1000");
      props.setProperty(TRACKER_USES_DEDICATED_PORT, Boolean.TRUE.toString());
      props.setProperty(ANNOUNCE_INTERVAL, "60");
      props.setProperty(TRACKER_TORRENT_EXPIRE_TIMEOUT, "180");
      PropertiesUtil.storeProperties(props, configFile, "");
    } catch (IOException e) {
      Loggers.SERVER.warn("Failed to create configuration file: " + configFile.getAbsolutePath() + ", error: " + e.toString());
    }
  }

  public void setTrackerEnabled(boolean enabled) {
    boolean oldValue = isTrackerEnabled();
    if (oldValue != enabled) {
      myConfiguration.setProperty(TRACKER_ENABLED, Boolean.toString(enabled));
      propertyChanged(TRACKER_ENABLED, oldValue, enabled);
    }
  }

  public void setSeederEnabled(boolean enabled) {
    boolean oldValue = isSeederEnabled();
    if  (oldValue != enabled){
      myConfiguration.setProperty(SEEDER_ENABLED, Boolean.toString(enabled));
      propertyChanged(SEEDER_ENABLED, oldValue, enabled);
    }
  }

  public void setFileSizeThresholdMb(int threshold) {
    int oldValue = getFileSizeThresholdMb();
    if (oldValue != threshold){
      myConfiguration.setProperty(FILE_SIZE_THRESHOLD, Integer.toString(threshold));
      propertyChanged(FILE_SIZE_THRESHOLD, oldValue, threshold);
    }
  }

  public void setMaxNumberOfSeededTorrents(int number) {
    int oldValue = getMaxNumberOfSeededTorrents();
    if (oldValue != number){
      myConfiguration.setProperty(MAX_NUMBER_OF_SEEDED_TORRENTS, Integer.toString(number));
      propertyChanged(MAX_NUMBER_OF_SEEDED_TORRENTS, oldValue, number);
    }
  }

  public void setAnnounceIntervalSec(int sec){
    int oldValue = getAnnounceIntervalSec();
    if (oldValue != sec){
      myConfiguration.setProperty(ANNOUNCE_INTERVAL, Integer.toString(sec));
      propertyChanged(ANNOUNCE_INTERVAL, oldValue, sec);
    }
  }

  public void setTrackerTorrentExpireTimeoutSec(int sec){
    int oldValue = getTrackerTorrentExpireTimeoutSec();
    if (oldValue != sec){
      myConfiguration.setProperty(TRACKER_TORRENT_EXPIRE_TIMEOUT, Integer.toString(sec));
      propertyChanged(TRACKER_TORRENT_EXPIRE_TIMEOUT, oldValue, sec);
    }
  }

  public void setTrackerUsesDedicatedPort(boolean enabled){
    boolean oldValue = isTrackerDedicatedPort();
    if  (oldValue != enabled){
      myConfiguration.setProperty(TRACKER_USES_DEDICATED_PORT, Boolean.toString(enabled));
      propertyChanged(TRACKER_USES_DEDICATED_PORT, oldValue, enabled);
    }
  }

  public int getMaxNumberOfSeededTorrents() {
    try {
      return Integer.parseInt(myConfiguration.getProperty(MAX_NUMBER_OF_SEEDED_TORRENTS));
    } catch (NumberFormatException e) {
      return 1000;
    }
  }

  public int getFileSizeThresholdMb() {
    try {
      return Integer.parseInt(myConfiguration.getProperty(FILE_SIZE_THRESHOLD, "10"));
    } catch (NumberFormatException e) {
      return 10;
    }
  }

  public int getAnnounceIntervalSec() {
    try {
      return Integer.parseInt(myConfiguration.getProperty(ANNOUNCE_INTERVAL, "60"));
    } catch (NumberFormatException e) {
      return 60;
    }
  }

  public int getTrackerTorrentExpireTimeoutSec() {
    try {
      return Integer.parseInt(myConfiguration.getProperty(TRACKER_TORRENT_EXPIRE_TIMEOUT, "180"));
    } catch (NumberFormatException e) {
      return 180;
    }
  }

  public boolean isTrackerDedicatedPort(){
    return isEnabled(TRACKER_USES_DEDICATED_PORT);
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
    return StringUtil.isTrue(myConfiguration.getProperty(configParam, "false"));
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
    } catch (IOException e) {
      Loggers.SERVER.warn("Failed to load configuration file: " + configFile.getAbsolutePath() + ", error: " + e.toString());
    } finally {
      FileUtil.close(fileReader);
    }

    myConfiguration = properties;
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

  @NotNull
  public String getOwnAddress() {
    String hostName = myConfiguration.getProperty(OWN_ADDRESS);
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

  public boolean getTrackerUsesDedicatedPort(){
    return isEnabled(TRACKER_USES_DEDICATED_PORT);
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
}
