package jetbrains.buildServer.artifactsMirror.server;

import java.io.File;
import java.io.IOException;

public class ServerMain {
  public static void main(String[] args) {
    if (args.length == 0) {
      usage();
    }

    String confFile = args[0];

    try {
      MirrorServer srv = new MirrorServer(new ServerConfiguration(new File(confFile)));
      srv.startAndWait();
    } catch (IOException e) {
      System.err.println("Input/output error accessing properties file: " + confFile + ", error: " + e.toString());
      exit();
    } catch (InvalidConfigurationException e) {
      System.err.println("Configuration specified in properties file is invalid: " + e.toString());
      exit();
    }
  }

  private static void usage() {
    System.out.println("Usage: java -jar mirror-server.jar <path to properties file>");
    exit();
  }

  private static void exit() {
    System.exit(1);
  }
}
