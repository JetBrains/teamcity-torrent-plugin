/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.turn.ttorrent.client.Client;
import com.turn.ttorrent.client.SharedTorrent;
import com.turn.ttorrent.common.Torrent;
import com.turn.ttorrent.tracker.Tracker;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.BaseServerLoggerFacade;
import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.artifactsMirror.seeder.TorrentsDirectorySeeder;
import jetbrains.buildServer.messages.BuildMessage1;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.io.FileUtils;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;
import org.jetbrains.annotations.NotNull;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Sergey.Pak
 *         Date: 9/5/13
 *         Time: 1:41 PM
 */
@Test
public class TorrentTransportTest extends BaseTestCase {

  private static final String CONTEXT_PATH = "/httpAuth/repository/download/TC_Gaya80x_BuildDist/2063228.tcbuildid";
  public static final String SERVER_PATH = "http://localhost:12345" + CONTEXT_PATH + "/";
  private AgentRunningBuild myBuild;
  private Server myServer;
  private Map<String, File> myDownloadMap;
  private Map<String, String> myAgentParametersMap;
  private Map<String, byte[]> myDownloadHacks;
  private TorrentTransportFactory.TorrentTransport myTorrentTransport;
  private List<String> myDownloadAttempts;
  private List<String> myDownloadHackAttempts;
  private File myTempDir;
  private boolean myDownloadHonestly;
  private TorrentsDirectorySeeder myDirectorySeeder;

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myServer = new Server(12345);
    WebAppContext handler = new WebAppContext();
    handler.setResourceBase("/");
    handler.setContextPath(CONTEXT_PATH);
    myDownloadMap = new HashMap<String, File>();
    myDownloadAttempts = new ArrayList<String>();
    myDownloadHonestly = true;
    myDownloadHacks = new HashMap<String, byte[]>();
    myDownloadHackAttempts = new ArrayList<String>();
    handler.addServlet(new ServletHolder(new HttpServlet() {
      @Override
      protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        myDownloadAttempts.add(req.getPathInfo());
        final ServletOutputStream os = resp.getOutputStream();
        final File file = myDownloadMap.get(req.getPathInfo());
        final byte[] bytes = FileUtils.readFileToByteArray(file);
        os.write(bytes);
        os.close();
      }
    }),
            "/*");
    myServer.setHandler(handler);
    myServer.start();

    myAgentParametersMap = new HashMap<String, String>();

    Mockery m = new Mockery();
    myBuild = m.mock(AgentRunningBuild.class);
    final BuildProgressLogger myLogger = new BaseServerLoggerFacade() {
      @Override
      public void flush() {
      }

      @Override
      protected void log(final BuildMessage1 message) {

      }
    };

    m.checking(new Expectations(){{
      allowing(myBuild).getSharedConfigParameters(); will (returnValue(myAgentParametersMap));
      allowing(myBuild).getBuildTypeId(); will (returnValue("TC_Gaya80x_BuildDist"));
      allowing(myBuild).getBuildLogger(); will (returnValue(myLogger));
    }});

    myDirectorySeeder = new TorrentsDirectorySeeder(createTempDir(), -1, 1);

    myTorrentTransport = new TorrentTransportFactory.TorrentTransport(myDirectorySeeder,
                    new HttpClient(), myBuild.getBuildLogger()){
      @Override
      protected byte[] download(@NotNull String urlString) throws IOException {
        if (myDownloadHonestly) {
          return super.download(urlString);
        } else {
          myDownloadHackAttempts.add(urlString);
          return myDownloadHacks.get(urlString);
        }
      }
    };

    myTempDir = createTempDir();
  }

  public void testNoParam() throws IOException {
    setTorrentTransportDisabled();
    final String urlString = SERVER_PATH + "aaaa.txt";
    final String digest = myTorrentTransport.getDigest(urlString);
    assertNull(digest);
    final String digest2 = myTorrentTransport.downloadUrlTo(urlString, createTempFile());
    assertNull(digest2);
  }

  public void testTeamcityIvy() throws IOException, NoSuchAlgorithmException {
    setTorrentTransportEnabled();
    final File ivyFile = new File(myTempDir, TorrentTransportFactory.TEAMCITY_IVY);

    final String urlString = SERVER_PATH +  TorrentTransportFactory.TEAMCITY_IVY;

    final File teamcityIvyFile = new File("agent/tests/resources/" +  TorrentTransportFactory.TEAMCITY_IVY);
    myDownloadMap.put("/" + TorrentTransportFactory.TEAMCITY_IVY, teamcityIvyFile);


//    assertNull(myTorrentTransport.getDigest(urlString));
    assertNotNull(myTorrentTransport.downloadUrlTo(urlString, ivyFile));
    assertTrue(ivyFile.exists());

    final String path1 = SERVER_PATH + "MyBuild.31.zip";
    final String torrentPath1 = SERVER_PATH + ".teamcity/torrents/MyBuild.31.zip.torrent";
    final String path2 = SERVER_PATH + "MyBuild.32.zip";
    final String torrentPath2 = SERVER_PATH + ".teamcity/torrents/MyBuild.32.zip.torrent";

    final File file1 = new File(myTempDir, "MyBuild.31.zip");
    final File file2 = new File(myTempDir, "MyBuild.32.zip");

    final File torrentFile = new File("agent/tests/resources/commons-io-cio2.5_40.jar.torrent");
    final byte[] torrentBytes = FileUtils.readFileToByteArray(torrentFile);
    myDownloadHacks.put(torrentPath1, torrentBytes);
    myDownloadHacks.put(torrentPath2, torrentBytes);
    setDownloadHonestly(false);

    myTorrentTransport.downloadUrlTo(path1, file1);
    myTorrentTransport.downloadUrlTo(path2, file2);



    // shouldn't try to download the second file:
    assertEquals(1, myDownloadHackAttempts.size());
    assertEquals(torrentPath1, myDownloadHackAttempts.get(0));

  }

  public void testDownloadAndSeed() throws IOException, NoSuchAlgorithmException, InterruptedException {
    setTorrentTransportEnabled();
    setDownloadHonestly(true);

    final File storageDir = new File(myTempDir, "storageDir");
    storageDir.mkdir();
    final File downloadDir = new File(myTempDir, "downloadDir");
    downloadDir.mkdir();
    final File torrentsDir = new File(myTempDir, "torrentsDir");
    torrentsDir.mkdir();
    final String fileName = "MyBuild.31.zip";
    final File artifactFile = new File(storageDir, fileName);
    createTempFile(20250).renameTo(artifactFile);

    final File teamcityIvyFile = new File("agent/tests/resources/" +  TorrentTransportFactory.TEAMCITY_IVY);
    myDownloadMap.put("/" + TorrentTransportFactory.TEAMCITY_IVY, teamcityIvyFile);
    final String ivyUrl = SERVER_PATH +  TorrentTransportFactory.TEAMCITY_IVY;
    final File ivyFile = new File(myTempDir, TorrentTransportFactory.TEAMCITY_IVY);
    myTorrentTransport.downloadUrlTo(ivyUrl, ivyFile);
    Tracker tracker = new Tracker(6969);
    Client client = new Client();
    try {
    tracker.start(true);

    myDirectorySeeder.start(new InetAddress[]{InetAddress.getLocalHost()}, tracker.getAnnounceURI(), 5);

    final Torrent torrent = Torrent.create(artifactFile, tracker.getAnnounceURI(), "testplugin");
    final File torrentFile = new File(torrentsDir, fileName + ".torrent");
    torrent.save(torrentFile);
    myDownloadMap.put("/.teamcity/torrents/"+fileName+".torrent", torrentFile);
    client.start(InetAddress.getLocalHost());
    client.addTorrent(SharedTorrent.fromFile(torrentFile, storageDir, true));

    final File targetFile = new File(downloadDir, fileName);
    final String digest = myTorrentTransport.downloadUrlTo(SERVER_PATH + fileName, targetFile);
    assertEquals(torrent.getHexInfoHash(), digest);
    assertTrue(FileUtils.contentEquals(artifactFile, targetFile));
    } finally {
      tracker.stop();
      client.stop();
    }
  }

  private void setTorrentTransportEnabled(){
    myAgentParametersMap.put(TorrentTransportFactory.TEAMCITY_ARTIFACTS_TRANSPORT,
            TorrentTransportFactory.TorrentTransport.class.getSimpleName());
  }

  private void setTorrentTransportDisabled(){
    myAgentParametersMap.remove(TorrentTransportFactory.TEAMCITY_ARTIFACTS_TRANSPORT);
  }

  private void setDownloadHonestly(final boolean value){
    myDownloadHonestly = value;
  }


  @AfterMethod
  public void tearDown() throws Exception {
    super.tearDown();
    myServer.stop();
    myDirectorySeeder.stop();
  }
}
