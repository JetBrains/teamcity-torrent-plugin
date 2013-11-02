package jetbrains.buildServer.torrent.seeder;

import jetbrains.buildServer.BaseTestCase;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

/**
 * @author Sergey.Pak
 *         Date: 9/16/13
 *         Time: 2:54 PM
 */
@Test
public class FileLinkTest extends BaseTestCase {

  public void testWindowsLink() throws IOException {
    final File linkFile = new File("common/tests/resources/art3.windows.dat.link");
    final File targetFile = FileLink.getTargetFile(linkFile);
    final File torrentFile = FileLink.getTorrentFile(linkFile);

    assertEquals(new File("artifacts\\MyTestOne\\4\\93\\sampleDir\\art3.33.dat"), targetFile);
    assertEquals(new File("artifacts\\MyTestOne\\4\\93\\.teamcity\\torrents\\sampleDir\\art3.33.dat.torrent"), torrentFile);
  }

  public void testLinuxLink() throws IOException {
    final File linkFile = new File("common/tests/resources/art3.linux.dat.link");
    final File targetFile = FileLink.getTargetFile(linkFile);
    final File torrentFile = FileLink.getTorrentFile(linkFile);

    assertEquals(new File("artifacts/MyTestOne/4/114/sampleDir/art3.39.dat"), targetFile);
    assertEquals(new File("artifacts/MyTestOne/4/114/.teamcity/torrents/sampleDir/art3.39.dat.torrent"), torrentFile);
  }

}
