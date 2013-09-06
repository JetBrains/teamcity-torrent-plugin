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

import com.turn.ttorrent.common.Torrent;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.artifactsMirror.seeder.TorrentsDirectorySeeder;
import jetbrains.buildServer.artifactsMirror.torrent.TeamcityTorrentClient;
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
  private TempFiles myTempFiles;
  private Server myServer;
  private Map<String, File> myDownloadMap;
  private Map<String, String> myAgentParametersMap;
  private Map<String, Torrent> myDownloadHacks;
  private TorrentTransportFactory.TorrentTransport myTorrentTransport;
  private List<String> myDownloadAttempts;
  private List<String> myDownloadHackAttempts;
  private File myTempDir;
  private boolean myDownloadHonestly;
  private TorrentsDirectorySeeder myDirectorySeeder;

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myTempFiles = new TempFiles();
    myServer = new Server(12345);
    WebAppContext handler = new WebAppContext();
    handler.setResourceBase("/");
    handler.setContextPath(CONTEXT_PATH);
    myDownloadMap = new HashMap<String, File>();
    myDownloadAttempts = new ArrayList<String>();
    myDownloadHonestly = true;
    myDownloadHacks = new HashMap<String, Torrent>();
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
    m.checking(new Expectations(){{
      allowing(myBuild).getSharedConfigParameters(); will (returnValue(myAgentParametersMap));
      allowing(myBuild).getBuildTypeId(); will (returnValue("TC_Gaya80x_BuildDist"));
    }});

    myDirectorySeeder = new TorrentsDirectorySeeder(myTempFiles.createTempDir(), -1, 1);

    myTorrentTransport = new TorrentTransportFactory.TorrentTransport(myDirectorySeeder,
                    new HttpClient(),
                    myBuild){
      @Override
      protected Torrent downloadTorrent(@NotNull String urlString) {
        if (myDownloadHonestly) {
          return super.downloadTorrent(urlString);
        } else {
          myDownloadHackAttempts.add(urlString);
          return myDownloadHacks.get(urlString);
        }
      }
    };

    myTempDir = myTempFiles.createTempDir();
  }

  public void testNoParam() throws IOException {
    setTorrentTransportDisabled();
    final String urlString = SERVER_PATH + "aaaa.txt";
    final String digest = myTorrentTransport.getDigest(urlString);
    assertNull(digest);
    final String digest2 = myTorrentTransport.downloadUrlTo(urlString, myTempFiles.createTempFile());
    assertNull(digest2);
  }

  public void testTeamcityIvy() throws IOException {
    setTorrentTransportEnabled();
    final File tempFile = new File(myTempDir, "testTeamcityIvy");

    final String urlString = SERVER_PATH + "teamcity-ivy.xml";
    assertNull(myTorrentTransport.getDigest(urlString));
    assertNull(myTorrentTransport.downloadUrlTo(urlString, tempFile));
    assertFalse(tempFile.exists());
    final String urlStringBranch = SERVER_PATH + "teamcity-ivy.xml?branch=myBranch";
    assertNull(myTorrentTransport.getDigest(urlStringBranch));
    assertNull(myTorrentTransport.downloadUrlTo(urlStringBranch, tempFile));
    assertFalse(tempFile.exists());
  }

  public void testSmallFileSize() throws IOException, NoSuchAlgorithmException {
    setTorrentTransportEnabled();
    setDownloadHonestly(false);
    myDirectorySeeder.setFileSizeThresholdMb(15);

    final File tempFile = new File(myTempDir, "testSmallFileSize");
    final String fileName = "commons-io-cio2.5_40.jar";
    final File torrentFile = new File("agent/tests/resources/commons-io-cio2.5_40.jar.torrent");
    myDownloadMap.put("/.teamcity/torrents/commons-io-cio2.5_40.jar.torrent", torrentFile);

    final String urlString = SERVER_PATH + fileName;
    myDownloadHacks.put(urlString, Torrent.load(torrentFile));

    assertNull(myTorrentTransport.getDigest(urlString));
    assertEquals(1, myDownloadHackAttempts.size());
    assertEquals(urlString, myDownloadHackAttempts.get(0));

    assertNull(myTorrentTransport.downloadUrlTo(urlString, tempFile));
    assertEquals(2, myDownloadHackAttempts.size());
    assertEquals(urlString, myDownloadHackAttempts.get(1));
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
  }
}
