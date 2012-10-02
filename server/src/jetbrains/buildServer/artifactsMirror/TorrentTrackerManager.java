package jetbrains.buildServer.artifactsMirror;

import com.intellij.openapi.util.io.FileUtil;
import jetbrains.buildServer.RootUrlHolder;
import jetbrains.buildServer.artifactsMirror.torrent.AnnouncedTorrent;
import jetbrains.buildServer.artifactsMirror.torrent.TorrentSeeder;
import jetbrains.buildServer.artifactsMirror.torrent.TorrentTracker;
import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.BuildServerListener;
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class TorrentTrackerManager {
  private TorrentTracker myTorrentTracker;
  private TorrentSeeder myTorrentSeeder;

  public TorrentTrackerManager(@NotNull final RootUrlHolder rootUrlHolder,
                               @NotNull EventDispatcher<BuildServerListener> dispatcher) {
    myTorrentSeeder = new TorrentSeeder();
    myTorrentTracker = new TorrentTracker(myTorrentSeeder);

    dispatcher.addListener(new BuildServerAdapter() {
      @Override
      public void serverStartup() {
        super.serverStartup();
        myTorrentTracker.start(rootUrlHolder.getRootUrl());
        myTorrentSeeder.start(rootUrlHolder.getRootUrl());
      }

      @Override
      public void serverShutdown() {
        super.serverShutdown();
        myTorrentTracker.stop();
        myTorrentSeeder.stop();
      }

      @Override
      public void cleanupFinished() {
        super.cleanupFinished();
        List<AnnouncedTorrent> removedTorrents = new ArrayList<AnnouncedTorrent>();
        for (AnnouncedTorrent ti: myTorrentTracker.getAnnouncedTorrents()) {
          if (ti.getSrcFile().isFile() && ti.getTorrentFile().isFile()) continue;

          FileUtil.delete(ti.getTorrentFile());
          removedTorrents.add(ti);
        }

        for (AnnouncedTorrent removed: removedTorrents) {
          myTorrentTracker.removeAnnouncedTorrent(removed);
        }
      }
    });
  }
}
