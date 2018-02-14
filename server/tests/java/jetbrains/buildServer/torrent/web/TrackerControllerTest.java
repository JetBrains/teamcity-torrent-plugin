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

import com.turn.ttorrent.common.protocol.TrackerMessage;
import com.turn.ttorrent.common.protocol.http.HTTPAnnounceRequestMessage;
import com.turn.ttorrent.tracker.AddressChecker;
import jetbrains.buildServer.RootUrlHolder;
import jetbrains.buildServer.XmlRpcHandlerManager;
import jetbrains.buildServer.controllers.AuthorizationInterceptor;
import jetbrains.buildServer.controllers.BaseControllerTestCase;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.torrent.TorrentConfigurator;
import jetbrains.buildServer.torrent.TorrentTrackerManager;
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Test
public class TrackerControllerTest extends BaseControllerTestCase<TrackerController> {

  private TorrentTrackerManager myTorrentTrackerManager;

  @Override
  @SuppressWarnings("unchecked")
  protected TrackerController createController() throws IOException {

    Mockery m = new Mockery();
    final XmlRpcHandlerManager rpcHandlerManager = m.mock(XmlRpcHandlerManager.class);
    final AddressChecker addressChecker = m.mock(AddressChecker.class);
    AuthorizationInterceptor interceptor = m.mock(AuthorizationInterceptor.class);
    m.checking(new Expectations() {{
      allowing(rpcHandlerManager).addHandler(with(any(String.class)), with(any(Object.class)));
      allowing(interceptor).addPathNotRequiringAuth(with(any(String.class)));
      allowing(addressChecker).isBadAddress(with(any(String.class)));
      will(returnValue(false));
    }});

    final TorrentConfigurator configurator = new TorrentConfigurator(new ServerPaths(createTempDir().getAbsolutePath()),
            myFixture.getSingletonService(RootUrlHolder.class), rpcHandlerManager);

    myTorrentTrackerManager = new TorrentTrackerManager(
            configurator,
            myFixture.getSingletonService(ExecutorServices.class),
            myFixture.getSingletonService(EventDispatcher.class),
            addressChecker
    );

    myTorrentTrackerManager.startTracker();
    return new TrackerController(myWebManager, myTorrentTrackerManager, interceptor);
  }

  public void multiAnnounceTest() throws Exception {
    final URL url = new URL("http://localhost" + TrackerController.PATH);

    List<String> announceURLs = new ArrayList<>();
    final int torrentsCount = 5;
    for (int i = 0; i < torrentsCount; i++) {
      HTTPAnnounceRequestMessage requestMessage = getRequestMessage("infohash" + i);
      final URL announceURL = requestMessage.buildAnnounceURL(url);
      announceURLs.add(announceURL.toString());
    }

    String requestString = String.join("\n", announceURLs);

    this.myRequest.setInputStream(new ByteArrayInputStream(requestString.getBytes(StandardCharsets.UTF_8)));
    doPost();
    assertEquals(200, myResponse.getStatus());
    assertEquals(myTorrentTrackerManager.getTorrents().size(), torrentsCount);
  }

  @NotNull
  private HTTPAnnounceRequestMessage getRequestMessage(String hash) throws IOException, TrackerMessage.MessageValidationException {
    final byte[] peerId = {1, 2, 3, 4};
    return HTTPAnnounceRequestMessage.craft(
            hash.getBytes(),
            peerId,
            6881,
            0,
            1234,
            0,
            true,
            false,
            TrackerMessage.AnnounceRequestMessage.RequestEvent.STARTED,
            "127.0.0.1",
            TrackerMessage.AnnounceRequestMessage.DEFAULT_NUM_WANT
    );
  }
}
