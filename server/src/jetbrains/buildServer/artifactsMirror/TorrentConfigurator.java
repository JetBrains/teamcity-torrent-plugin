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
import jetbrains.buildServer.configuration.ChangeListener;
import jetbrains.buildServer.configuration.ChangeObserver;
import jetbrains.buildServer.configuration.ChangeProvider;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.impl.ServerSettings;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.PropertiesUtil;
import jetbrains.buildServer.util.StringUtil;
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

public class TorrentConfigurator implements TorrentTrackerConfiguration {
  public final static String TRACKER_ENABLED = "torrent.tracker.enabled";
  public final static String OWN_ADDRESS = "torrent.ownAddress";
  public final static String SEEDER_ENABLED = "torrent.seeder.enabled";
  public final static String FILE_SIZE_THRESHOLD = "torrent.file.size.threshold.mb";
  public final static String TRANSPORT_ENABLED = "torrent.transport.enabled";
  public final static String ANNOUNCE_INTERVAL = "torrent.announce.interval.sec";
  public final static String TRACKER_TORRENT_EXPIRE_TIMEOUT = "torrent.tracker.expire.timeout.sec";
  public final static String MAX_NUMBER_OF_SEEDED_TORRENTS = "torrent.max.seeded.number";
  public final static String TRACKER_DEDICATED_PORT ="torrent.tracker.dedicated.port";
  // this is fake option to multicast announce url changes;
  public static final String ANNOUNCE_URL = "announce.url";

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

    xmlRpcHandlerManager.addHandler(XmlRpcConstants.TORRENT_TRACKER_CONFIGURATION, this);

    myConfigurationWatcher = new TorrentConfigurationWatcher();
    myConfigurationWatcher.registerListener(new ChangeListener() {
      public void changeOccured(String requestor) {
        setTrackerEnabled(getBooleanValue(TRACKER_ENABLED, true));
        setSeederEnabled(getBooleanValue(SEEDER_ENABLED, true));
        setTrackerUsesDedicatedPort(getBooleanValue(TRACKER_DEDICATED_PORT, false));
        setMaxNumberOfSeededTorrents(getIntegerValue(MAX_NUMBER_OF_SEEDED_TORRENTS, 1000));
        setFileSizeThresholdMb(getIntegerValue(FILE_SIZE_THRESHOLD, 10));
        setTrackerTorrentExpireTimeoutSec(getIntegerValue(TRACKER_TORRENT_EXPIRE_TIMEOUT, 180));
        setAnnounceIntervalSec(getIntegerValue(ANNOUNCE_INTERVAL, 60));
        setTransportEnabled(getBooleanValue(TRANSPORT_ENABLED, true));
      }
    });

    myConfigurationWatcher.registerChangeProvider(myConfigurationWatcher);
    myConfigurationWatcher.start();
  }

  private void initConfigFile(File configFile) {
    try {
      Properties props = new Properties();
      props.setProperty(SEEDER_ENABLED, "false");
      PropertiesUtil.storeProperties(props, configFile, "");
    } catch (IOException e) {
      Loggers.SERVER.warn("Failed to create configuration file: " + configFile.getAbsolutePath() + ", error: " + e.toString());
    }
  }

  private void setTrackerEnabled(boolean enabled) {
    boolean oldValue = getBooleanValue(myConfiguration, TRACKER_ENABLED, true);
    if (oldValue != enabled) {
      myConfiguration.setProperty(TRACKER_ENABLED, System.getProperty(TRACKER_ENABLED));
      propertyChanged(TRACKER_ENABLED, oldValue, enabled);
    }
  }

  private void setSeederEnabled(boolean enabled) {
    boolean oldValue = getBooleanValue(myConfiguration, SEEDER_ENABLED, true);
    if  (oldValue != enabled){
      myConfiguration.setProperty(SEEDER_ENABLED, System.getProperty(SEEDER_ENABLED));
      propertyChanged(SEEDER_ENABLED, oldValue, enabled);
    }
  }

  private void setFileSizeThresholdMb(int threshold) {
    int oldValue = getIntegerValue(myConfiguration, FILE_SIZE_THRESHOLD, 10);
    if (oldValue != threshold){
      myConfiguration.setProperty(FILE_SIZE_THRESHOLD, System.getProperty(FILE_SIZE_THRESHOLD));
      propertyChanged(FILE_SIZE_THRESHOLD, oldValue, threshold);
    }
  }

  private void setMaxNumberOfSeededTorrents(int number) {
    int oldValue = getIntegerValue(myConfiguration, MAX_NUMBER_OF_SEEDED_TORRENTS, 1000);
    if (oldValue != number){
      myConfiguration.setProperty(MAX_NUMBER_OF_SEEDED_TORRENTS, System.getProperty(MAX_NUMBER_OF_SEEDED_TORRENTS));
      propertyChanged(MAX_NUMBER_OF_SEEDED_TORRENTS, oldValue, number);
    }
  }

  private void setAnnounceIntervalSec(int sec){
    int oldValue = getIntegerValue(myConfiguration, ANNOUNCE_INTERVAL, 60);
    if (oldValue != sec){
      myConfiguration.setProperty(ANNOUNCE_INTERVAL, System.getProperty(ANNOUNCE_INTERVAL));
      propertyChanged(ANNOUNCE_INTERVAL, oldValue, sec);
    }
  }

  private void setTrackerTorrentExpireTimeoutSec(int sec){
    int oldValue = getIntegerValue(myConfiguration, TRACKER_TORRENT_EXPIRE_TIMEOUT, 180);
    if (oldValue != sec){
      myConfiguration.setProperty(TRACKER_TORRENT_EXPIRE_TIMEOUT, System.getProperty(TRACKER_TORRENT_EXPIRE_TIMEOUT));
      propertyChanged(TRACKER_TORRENT_EXPIRE_TIMEOUT, oldValue, sec);
    }
  }

  private void setTrackerUsesDedicatedPort(boolean enabled){
    boolean oldValue = getBooleanValue(myConfiguration, TRACKER_DEDICATED_PORT, false);
    if  (oldValue != enabled){
      myConfiguration.setProperty(TRACKER_DEDICATED_PORT, System.getProperty(TRACKER_DEDICATED_PORT));
      propertyChanged(TRACKER_DEDICATED_PORT, oldValue, enabled);
    }
  }

  private void setTransportEnabled(boolean enabled){
    boolean oldValue = getBooleanValue(myConfiguration, TRANSPORT_ENABLED, true);
    if  (oldValue != enabled){
      myConfiguration.setProperty(TRANSPORT_ENABLED, System.getProperty(TRANSPORT_ENABLED));
      propertyChanged(TRANSPORT_ENABLED, oldValue, enabled);
    }
  }

  public int getMaxNumberOfSeededTorrents() {
    try {
      return Integer.parseInt(System.getProperty(MAX_NUMBER_OF_SEEDED_TORRENTS));
    } catch (NumberFormatException e) {
      return 1000;
    }
  }

  public int getFileSizeThresholdMb() {
    return getIntegerValue(FILE_SIZE_THRESHOLD, 10);
  }

  public int getAnnounceIntervalSec() {
    return getIntegerValue(ANNOUNCE_INTERVAL, 60);
  }

  public int getTrackerTorrentExpireTimeoutSec() {
    return getIntegerValue(TRACKER_TORRENT_EXPIRE_TIMEOUT, 180);
  }

  public boolean isTrackerDedicatedPort(){
    return getBooleanValue(TRACKER_DEDICATED_PORT, false);
  }

  public void persistConfiguration() throws IOException {
    PropertiesUtil.storeProperties(myConfiguration, getConfigFile(), "");
  }

  public boolean isTrackerEnabled() {
    return getBooleanValue(TRACKER_ENABLED, true);
  }

  public boolean isSeederEnabled() {
    return getBooleanValue(SEEDER_ENABLED, true);
  }

  public boolean isTransportEnabled(){
    return getBooleanValue(TRANSPORT_ENABLED, true);
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

  private static boolean getBooleanValue(final String systemPropertyName, final boolean defaultValue){
    return getBooleanValue(System.getProperties(), systemPropertyName, defaultValue);
  }

  private static boolean getBooleanValue(final Properties properties, final String propertyName, final boolean defaultValue){
    final String value = properties.getProperty(propertyName);
    if (Boolean.TRUE.toString().equalsIgnoreCase(value)){
      return true;
    } else if (Boolean.FALSE.toString().equalsIgnoreCase(value)){
      return false;
    } else {
      return defaultValue;
    }
  }

  private static int getIntegerValue(final String systemPropertyName, final int defaultValue){
    return getIntegerValue(System.getProperties(), systemPropertyName, defaultValue);
  }
  private static int getIntegerValue(final Properties properties, final String systemPropertyName, final int defaultValue){
    final String value = properties.getProperty(systemPropertyName);
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException ex) {
      return defaultValue;
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
        if (!StringUtils.equals(myStoredProperties.get(s), System.getProperty(s)))
          return true;
      }
      return false;
    }

    public void resetChanged() {
      myStoredProperties.put(TRACKER_ENABLED, System.getProperty(TRACKER_ENABLED));
      myStoredProperties.put(OWN_ADDRESS, System.getProperty(OWN_ADDRESS));
      myStoredProperties.put(SEEDER_ENABLED, System.getProperty(SEEDER_ENABLED));
      myStoredProperties.put(FILE_SIZE_THRESHOLD, System.getProperty(FILE_SIZE_THRESHOLD));
      myStoredProperties.put(TRANSPORT_ENABLED, System.getProperty(TRANSPORT_ENABLED));
      myStoredProperties.put(ANNOUNCE_INTERVAL, System.getProperty(ANNOUNCE_INTERVAL));
      myStoredProperties.put(TRACKER_TORRENT_EXPIRE_TIMEOUT, System.getProperty(TRACKER_TORRENT_EXPIRE_TIMEOUT));
      myStoredProperties.put(MAX_NUMBER_OF_SEEDED_TORRENTS, System.getProperty(MAX_NUMBER_OF_SEEDED_TORRENTS));
      myStoredProperties.put(TRACKER_DEDICATED_PORT, System.getProperty(TRACKER_DEDICATED_PORT));
    }

    @Nullable
    public String getRequestor() {
      return TorrentConfigurationWatcher.class.getName();
    }
  }
}
