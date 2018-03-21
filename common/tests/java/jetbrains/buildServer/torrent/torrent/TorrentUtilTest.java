/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package jetbrains.buildServer.torrent.torrent;

import com.turn.ttorrent.client.Client;
import org.testng.annotations.Test;

import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

@Test
public class TorrentUtilTest {

  public void isConnectionManagerInitializedTest() throws Exception {
    ExecutorService es = Executors.newFixedThreadPool(2);
    ExecutorService validatorES = Executors.newFixedThreadPool(2);
    Client client = new Client(es, validatorES);
    assertFalse(TorrentUtil.isConnectionManagerInitialized(client));
    client.start(InetAddress.getLocalHost());
    assertTrue(TorrentUtil.isConnectionManagerInitialized(client));
    client.stop();
    assertTrue(TorrentUtil.isConnectionManagerInitialized(client));
    es.shutdown();
    validatorES.shutdown();
  }

}
