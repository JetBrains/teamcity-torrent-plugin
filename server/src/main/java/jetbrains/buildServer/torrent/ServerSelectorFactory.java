

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