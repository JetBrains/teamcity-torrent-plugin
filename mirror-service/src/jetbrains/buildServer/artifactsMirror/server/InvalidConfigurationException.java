package jetbrains.buildServer.artifactsMirror.server;

public class InvalidConfigurationException extends RuntimeException {
  public InvalidConfigurationException(String message) {
    super(message);
  }
}
