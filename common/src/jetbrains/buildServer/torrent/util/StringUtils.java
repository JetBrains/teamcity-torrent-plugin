package jetbrains.buildServer.torrent.util;

import org.jetbrains.annotations.NotNull;

import java.io.File;

public class StringUtils {


  @NotNull
  public static String parseServerUrlToDirectoriesPath(@NotNull String serverUrl) {
    //remove protocol definition
    String protocolDef = ":/";
    final int colonIdx = serverUrl.indexOf(protocolDef);
    if (colonIdx > 0) {
      serverUrl = serverUrl.substring(colonIdx + protocolDef.length());
      if (serverUrl.startsWith("/")) {
        serverUrl = serverUrl.substring(1);
      }
    }
    return serverUrl.replaceAll(":", "_").replaceAll("/", File.separator);
  }
}
