/*
 * Copyright 2000-2020 JetBrains s.r.o.
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
