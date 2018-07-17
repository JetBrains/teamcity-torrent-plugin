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

package jetbrains.buildServer.torrent;

import com.turn.ttorrent.client.announce.AnnounceException;
import com.turn.ttorrent.client.announce.HTTPTrackerClient;
import com.turn.ttorrent.client.announce.TrackerClient;
import com.turn.ttorrent.client.announce.TrackerClientFactory;
import com.turn.ttorrent.common.AnnounceableInformation;
import com.turn.ttorrent.common.Peer;
import com.turn.ttorrent.common.protocol.AnnounceRequestMessage;
import jetbrains.buildServer.serverSide.ReadOnlyRestrictor;
import jetbrains.buildServer.serverSide.ServerResponsibility;
import org.jetbrains.annotations.NotNull;

import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.UnknownServiceException;
import java.util.List;

public class ServerTrackerClientFactory implements TrackerClientFactory {

  @NotNull
  private final ServerResponsibility myServerResponsibility;

  public ServerTrackerClientFactory(@NotNull ServerResponsibility serverResponsibility) {
    myServerResponsibility = serverResponsibility;
  }

  @Override
  public TrackerClient createTrackerClient(List<Peer> peers, URI tracker) throws UnknownHostException, UnknownServiceException {
    if (myServerResponsibility.canManageServerConfig()) {
      return new HTTPTrackerClient(peers, tracker);
    } else {
      return new ReadOnlyHttpClient(peers, tracker);
    }
  }

  private static class ReadOnlyHttpClient extends HTTPTrackerClient {

    ReadOnlyHttpClient(List<Peer> peers, URI tracker) {
      super(peers, tracker);
    }

    @Override
    public void announce(AnnounceRequestMessage.RequestEvent event, boolean inhibitEvents, AnnounceableInformation torrentInfo, List<Peer> adresses) throws AnnounceException {
      ReadOnlyRestrictor.doReadOnlyNetworkOperation(() -> super.announce(event, inhibitEvents, torrentInfo, adresses));
    }

    @Override
    protected void multiAnnounce(AnnounceRequestMessage.RequestEvent event, boolean inhibitEvent, List<? extends AnnounceableInformation> torrents, List<Peer> addresses) throws AnnounceException, ConnectException {
      try {
        ReadOnlyRestrictor.doReadOnlyNetworkOperation(() -> ReadOnlyHttpClient.super.multiAnnounce(event, inhibitEvent, torrents, addresses));
      } catch (AnnounceException | ConnectException e) {
        throw e;
      } catch (Exception e) {
        throw new AnnounceException(e);
      }
    }
  }
}
