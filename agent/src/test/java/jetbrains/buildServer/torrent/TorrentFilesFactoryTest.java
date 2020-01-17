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

import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.agent.InterruptState;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Test
public class TorrentFilesFactoryTest extends BaseTestCase {
  private TorrentFilesFactoryImpl myTorrentFilesFactory;
  private File myTorrentsDir;
  private AgentTorrentsSeeder mySeeder;
  private FakeAgentIdleTasks myFakeAgentIdleTasks;
  private BuildAgentConfigurationFixture myAgentConfigurationFixture = new BuildAgentConfigurationFixture();

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myTorrentsDir = createTempDir();
    final BuildAgentConfiguration agentConfiguration = myAgentConfigurationFixture.setUp();

    final TorrentConfiguration configuration = new FakeTorrentConfiguration();

    myFakeAgentIdleTasks = new FakeAgentIdleTasks();
    mySeeder = new AgentTorrentsSeeder(agentConfiguration, configuration);
    myTorrentFilesFactory = new TorrentFilesFactoryImpl(agentConfiguration, configuration, myFakeAgentIdleTasks, mySeeder);
  }

  public void test_factory() throws IOException {
    List<File> torrents = createTorrentFiles();

    Collection<File> actualTorrentFiles = FileUtil.findFiles(new FileFilter() {
      public boolean accept(File pathname) {
        return pathname.getName().endsWith(".torrent");
      }
    }, myTorrentsDir);

    assertTrue(actualTorrentFiles.containsAll(torrents));
  }

  public void test_cleanup() throws IOException {
    List<File> torrents = createTorrentFiles();

    File randomTorrentFile = torrents.get(torrents.size() / 2);
    mySeeder.registerSrcAndTorrentFile(createTempFile(), randomTorrentFile, true);

    myFakeAgentIdleTasks.getTask().execute(new InterruptState() {
      public boolean isInterrupted() {
        return false;
      }
    });

    Collection<File> actualTorrentFiles = FileUtil.findFiles(new FileFilter() {
      public boolean accept(File pathname) {
        return pathname.getName().endsWith(".torrent");
      }
    }, myTorrentsDir);

    assertEquals(1, actualTorrentFiles.size());
    assertTrue(actualTorrentFiles.contains(randomTorrentFile));
  }

  @NotNull
  private List<File> createTorrentFiles() throws IOException {
    List<File> torrents = new ArrayList<File>();
    int numFiles = 10;
    for (int i=0; i<numFiles; i++) {
      File srcFile = createTempFile();
      File torrent = myTorrentFilesFactory.createTorrentFile(srcFile);
      torrents.add(torrent);
    }
    return torrents;
  }

  @AfterMethod
  @Override
  protected void tearDown() throws Exception {
    myAgentConfigurationFixture.tearDown();
    super.tearDown();
  }
}
