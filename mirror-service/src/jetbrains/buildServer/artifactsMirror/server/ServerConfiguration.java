package jetbrains.buildServer.artifactsMirror.server;

import com.sun.istack.internal.NotNull;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ServerConfiguration {
  private String myServerUrl;
  private String myUsername;
  private String myPassword;
  private String myAddress;
  private int myPort;

  @NotNull
  public String getServerUrl() {
    return myServerUrl;
  }

  @NotNull
  public String getUsername() {
    return myUsername;
  }

  @NotNull
  public String getPassword() {
    return myPassword;
  }

  public int getMyPort() {
    return myPort;
  }

  @Nullable
  public String getMyAddress() {
    return myAddress;
  }

  public ServerConfiguration(@NotNull File propertiesFile) throws IOException, InvalidConfigurationException {
    FileInputStream fis = null;
    Properties props = new Properties();
    try {
      fis = new FileInputStream(propertiesFile);
      props.load(fis);

      myServerUrl = props.getProperty("serverUrl");
      if (myServerUrl == null) {
        throw new InvalidConfigurationException("serverUrl property must be specified");
      }

      myUsername = props.getProperty("serverAuth.username");
      if (myUsername == null) {
        throw new InvalidConfigurationException("serverAuth.username property must be specified");
      }

      myPassword = props.getProperty("serverAuth.password");
      if (myPassword == null) {
        throw new InvalidConfigurationException("serverAuth.password property must be specified");
      }

      myAddress = props.getProperty("ownAddress");

      String portStr = props.getProperty("ownPort", "80");
      try {
        myPort = Integer.parseInt(portStr);
      } catch (NumberFormatException e) {
        throw new InvalidConfigurationException("Incorrect value specified for ownPort property: " + portStr);
      }

    } finally {
      FileUtil.close(fis);
    }
  }
}
