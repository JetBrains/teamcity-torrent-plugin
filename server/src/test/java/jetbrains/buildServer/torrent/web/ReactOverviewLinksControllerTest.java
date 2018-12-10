/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

package jetbrains.buildServer.torrent.web;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.turn.ttorrent.client.SelectorFactoryImpl;
import com.turn.ttorrent.client.announce.TrackerClientFactoryImpl;
import com.turn.ttorrent.common.TorrentCreator;
import com.turn.ttorrent.common.TorrentMetadata;
import com.turn.ttorrent.common.TorrentSerializer;
import jetbrains.buildServer.XmlRpcHandlerManager;
import jetbrains.buildServer.controllers.BaseControllerTestCase;
import jetbrains.buildServer.serverSide.RunningBuildEx;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.ServerSettings;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.torrent.ServerTorrentsDirectorySeeder;
import jetbrains.buildServer.torrent.TorrentConfigurator;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.testng.annotations.Test;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static jetbrains.buildServer.torrent.seeder.TorrentsSeeder.TORRENTS_DIT_PATH;

@Test
public class ReactOverviewLinksControllerTest extends BaseControllerTestCase<ReactOverviewLinksController> {

  @Override
  protected ReactOverviewLinksController createController() {

    final Mockery m = new Mockery();


    XmlRpcHandlerManager xmlRpcHandlerManager = new XmlRpcHandlerManager() {
      @Override
      public void addHandler(String handlerName, Object handler) {

      }

      @Override
      public void addSessionHandler(String handlerName, Object handler) {

      }
    };
    ServerPaths serverPaths = myFixture.getServerPaths();
    final ServerSettings serverSettings = m.mock(ServerSettings.class);
    m.checking(new Expectations() {{
      allowing(serverSettings).getArtifactDirectories();
      will(returnValue(Collections.singletonList(serverPaths.getArtifactsDirectory())));
    }});
    TorrentConfigurator configurator = new TorrentConfigurator(serverPaths,
            myServer, xmlRpcHandlerManager);
    configurator.setSeedingEnabled(true);
    configurator.setDownloadEnabled(true);
    ServerTorrentsDirectorySeeder torrentsDirectorySeeder = new ServerTorrentsDirectorySeeder(serverPaths, serverSettings, configurator,
            getEventDispatcher(), myFixture.getSingletonService(ExecutorServices.class),
            new SelectorFactoryImpl(), new TrackerClientFactoryImpl(), myFixture.getServerResponsibility());
    getEventDispatcher().getMulticaster().serverStartup();
    return new ReactOverviewLinksController(
            myServer,
            myWebManager,
            configurator,
            torrentsDirectorySeeder
    );
  }

  public void testJsonResponse() throws Exception {
    RunningBuildEx runningBuildEx = startBuild();
    runningBuildEx.publishArtifact("rootFile.txt", new byte[]{1, 2, 3, 4});
    runningBuildEx.publishArtifact("directory/dirFile", new byte[]{5, 6, 7, 8});
    File rootFile = new File(runningBuildEx.getArtifactsDirectory(), "rootFile.txt");

    TorrentMetadata metadata = TorrentCreator.create(rootFile, new URI("http://localhost:6969"), "test");
    byte[] torrentBytes = new TorrentSerializer().serialize(metadata);
    runningBuildEx.publishArtifact(TORRENTS_DIT_PATH + "/directory/dirFile.torrent", torrentBytes);
    runningBuildEx.publishArtifact(TORRENTS_DIT_PATH + "/rootFile.txt.torrent", torrentBytes);
    finishBuild();

    doGet("buildId", runningBuildEx.getBuildId());

    assertEquals(200, myResponse.getStatus());
    String response = new String(myResponse.getReturnedBytes(), StandardCharsets.UTF_8);

    Gson gson = new Gson();
    JsonObject res = gson.fromJson(response, JsonObject.class);
    assertTrue(res.has("hrefs"));
    assertTrue(res.has("title"));
    assertTrue(res.has("icon"));
  }
}
