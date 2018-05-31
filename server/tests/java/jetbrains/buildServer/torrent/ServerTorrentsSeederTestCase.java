/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.turn.ttorrent.client.SelectorFactoryImpl;
import com.turn.ttorrent.client.announce.TrackerClientFactoryImpl;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.RootUrlHolder;
import jetbrains.buildServer.XmlRpcHandlerManager;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.serverSide.impl.auth.SecurityContextImpl;
import jetbrains.buildServer.util.EventDispatcher;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class ServerTorrentsSeederTestCase extends BaseTestCase {
  protected EventDispatcher<BuildServerListener> myDispatcher;
  protected ServerTorrentsDirectorySeeder myTorrentsSeeder;
  protected TorrentConfigurator myConfigurator;
  private ScheduledExecutorService myExecutorService;

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final Mockery m = new Mockery();

    final ServerPaths serverPaths = new ServerPaths(createTempDir().getAbsolutePath());
    final RootUrlHolder rootUrlHolder = m.mock(RootUrlHolder.class);
    final ExecutorServices executorServices = m.mock(ExecutorServices.class);
    myExecutorService = Executors.newScheduledThreadPool(1);
    final ServerResponsibility serverResponsibility = m.mock(ServerResponsibility.class);
    m.checking(new Expectations(){{
      allowing(rootUrlHolder).getRootUrl(); will(returnValue("http://localhost:8111/"));
      allowing(executorServices).getNormalExecutorService(); will(returnValue(myExecutorService));
      allowing(serverResponsibility).canManageBuilds(); will(returnValue(true));
    }});

    final ServerSettings serverSettings = m.mock(ServerSettings.class);
    m.checking(new Expectations() {{
      allowing(serverSettings).getArtifactDirectories(); will(returnValue(Collections.singletonList(serverPaths.getArtifactsDirectory())));
    }});

    myConfigurator = new TorrentConfigurator(serverPaths, rootUrlHolder, new XmlRpcHandlerManager() {
      public void addHandler(String handlerName, Object handler) {}
      public void addSessionHandler(String handlerName, Object handler) {}
    });
    myConfigurator.setDownloadEnabled(true);
    myConfigurator.setSeedingEnabled(true);

    myDispatcher = new BuildServerListenerEventDispatcher(new SecurityContextImpl());

    myTorrentsSeeder = new ServerTorrentsDirectorySeeder(serverPaths,
            serverSettings,
            myConfigurator,
            myDispatcher,
            executorServices,
            new SelectorFactoryImpl(),
            new TrackerClientFactoryImpl(),
            serverResponsibility);
  }

  @AfterMethod
  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    myExecutorService.shutdownNow();
    myDispatcher.getMulticaster().serverShutdown();
  }
}
