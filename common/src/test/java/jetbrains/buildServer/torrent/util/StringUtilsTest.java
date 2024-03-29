

package jetbrains.buildServer.torrent.util;

import org.testng.annotations.Test;

import java.io.File;

import static org.testng.Assert.assertEquals;

@Test
public class StringUtilsTest {

  public void parseServerUrlToDirectoriesPathTest() {
    String serverUrl = "http://localhost:1234/bs/bs";
    String expected = "localhost_1234" + File.separator + "bs" + File.separator + "bs";
    String actual = StringUtils.parseServerUrlToDirectoriesPath(serverUrl);
    assertEquals(actual, expected);

    serverUrl = "https://domain.com";
    expected = "domain.com";
    actual = StringUtils.parseServerUrlToDirectoriesPath(serverUrl);
    assertEquals(actual, expected);

    serverUrl = "https://domain.com/m";
    expected = "domain.com" + File.separator + "m";
    actual = StringUtils.parseServerUrlToDirectoriesPath(serverUrl);
    assertEquals(actual, expected);

    serverUrl = "https://127.0.0.1:5555/m";
    expected = "127.0.0.1_5555" + File.separator + "m";
    actual = StringUtils.parseServerUrlToDirectoriesPath(serverUrl);
    assertEquals(actual, expected);

    serverUrl = "/";
    expected = StringUtils.RESULT_FOR_EMPTY_URL;
    actual = StringUtils.parseServerUrlToDirectoriesPath(serverUrl);
    assertEquals(actual, expected);

    serverUrl = "////";
    expected = StringUtils.RESULT_FOR_EMPTY_URL;
    actual = StringUtils.parseServerUrlToDirectoriesPath(serverUrl);
    assertEquals(actual, expected);

    serverUrl = "";
    expected = StringUtils.RESULT_FOR_EMPTY_URL;
    actual = StringUtils.parseServerUrlToDirectoriesPath(serverUrl);
    assertEquals(actual, expected);

  }
}