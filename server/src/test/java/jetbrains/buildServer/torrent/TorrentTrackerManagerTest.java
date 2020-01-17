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

package jetbrains.buildServer.torrent;

import com.turn.ttorrent.common.PeerUID;
import com.turn.ttorrent.tracker.AddressChecker;
import com.turn.ttorrent.tracker.TrackedPeer;
import com.turn.ttorrent.tracker.TrackedTorrent;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.RootUrlHolder;
import jetbrains.buildServer.XmlRpcHandlerManager;
import jetbrains.buildServer.serverSide.BuildServerListener;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.ServerResponsibility;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.util.EventDispatcher;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TorrentTrackerManagerTest extends BaseTestCase {

  private ExecutorService myExecutorService;
  private TorrentTrackerManager myTorrentTrackerManager;

  @BeforeMethod
  public void setUp() throws Exception {

    Mockery m = new Mockery();
    final XmlRpcHandlerManager rpcHandlerManager = m.mock(XmlRpcHandlerManager.class);
    final ExecutorServices executorServices = m.mock(ExecutorServices.class);
    final ServerResponsibility serverResponsibility = m.mock(ServerResponsibility.class);
    myExecutorService = Executors.newScheduledThreadPool(4);
    final RootUrlHolder rootUrlHolder = m.mock(RootUrlHolder.class);
    m.checking(new Expectations() {{
      allowing(rpcHandlerManager).addHandler(with(any(String.class)), with(any(Object.class)));
      allowing(executorServices).getNormalExecutorService(); will(returnValue(myExecutorService));
      allowing(rootUrlHolder).getRootUrl(); will(returnValue("http://localhost:8111"));
      allowing(serverResponsibility).canManageServerConfig(); will(returnValue(true));
    }});
    myTorrentTrackerManager = new TorrentTrackerManager(
            new TorrentConfigurator(new ServerPaths(createTempDir().getAbsolutePath()), rootUrlHolder, rpcHandlerManager),
            executorServices,
            new EventDispatcher<BuildServerListener>(BuildServerListener.class) {
            },
            m.mock(AddressChecker.class),
            serverResponsibility
    );

  }

  @AfterMethod
  public void tearDown() throws Exception {
    myExecutorService.shutdown();
  }

  @Test
  public void testThatOnePeerWithManyTorrentsCalculatedAsOnePeer() {
    TrackedTorrent firstTorrent = new TrackedTorrent(new byte[]{1});
    TrackedTorrent secondTorrent = new TrackedTorrent(new byte[]{2});

    myTorrentTrackerManager.startTracker();

    final String ip = "127.0.0.1";
    final int port = 6881;
    firstTorrent.getPeers().put(new PeerUID(new InetSocketAddress(ip, port), "1"),
            new TrackedPeer(firstTorrent, ip, port, ByteBuffer.allocate(10)));
    firstTorrent.getPeers().put(new PeerUID(new InetSocketAddress(ip, port), "2"),
            new TrackedPeer(secondTorrent, ip, port, ByteBuffer.allocate(10)));
    myTorrentTrackerManager.getTorrentsRepository().putIfAbsent("1", firstTorrent);
    myTorrentTrackerManager.getTorrentsRepository().putIfAbsent("2", secondTorrent);

    assertEquals(2, myTorrentTrackerManager.getTorrents().size());
    assertEquals(1, myTorrentTrackerManager.getConnectedClientsNum());

    firstTorrent.getPeers().put(new PeerUID(new InetSocketAddress(ip, port + 1), "1"),
            new TrackedPeer(firstTorrent, ip, port + 1, ByteBuffer.allocate(10)));

    assertEquals(2, myTorrentTrackerManager.getTorrents().size());
    assertEquals(2, myTorrentTrackerManager.getConnectedClientsNum());

  }
}
