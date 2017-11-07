package jetbrains.buildServer.torrent.util;

import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

public class StringUtils {

  static final String RESULT_FOR_EMPTY_URL = "emptyUrl.dat";

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

    if (StringUtil.isEmpty(serverUrl)) {
      return RESULT_FOR_EMPTY_URL;
    }

    StringBuilder result = new StringBuilder();
    for (String dirNameEncoded : serverUrl.split("/")) {
      String dirName;
      try {
        dirName = URLDecoder.decode(dirNameEncoded, "UTF-8");
      } catch (UnsupportedEncodingException e) {
        Loggers.AGENT.warn(e);
        dirName = dirNameEncoded;
      }
      result.append(FileUtil.fixDirectoryNameAllowUnicode(dirName)).append(File.separatorChar);
    }
    if (result.length() == 0) {
      return RESULT_FOR_EMPTY_URL;
    }
    result.deleteCharAt(result.length() - 1);
    return result.toString();
  }
}
