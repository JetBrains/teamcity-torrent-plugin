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

import com.turn.ttorrent.network.SelectorFactory;
import jetbrains.buildServer.serverSide.ReadOnlyRestrictor;
import jetbrains.buildServer.serverSide.ServerResponsibility;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.channels.Selector;

public class ServerSelectorFactory implements SelectorFactory {

  @NotNull private final ServerResponsibility myServerResponsibility;

  public ServerSelectorFactory(@NotNull final ServerResponsibility serverResponsibility) {
    myServerResponsibility = serverResponsibility;
  }

  @Override
  @NotNull
  public Selector newSelector() throws IOException {
    if (myServerResponsibility.canManageServerConfig()) {
      return Selector.open();
    } else {
      return ReadOnlyRestrictor.doReadOnlyNetworkOperation(Selector::open);
    }
  }
}
