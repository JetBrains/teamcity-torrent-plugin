package jetbrains.buildServer.artifactsMirror;

import com.turn.ttorrent.tracker.TrackedTorrent;
import jetbrains.buildServer.RootUrlHolder;
import jetbrains.buildServer.artifactsMirror.torrent.TorrentSeeder;
import jetbrains.buildServer.artifactsMirror.torrent.TorrentTracker;
import jetbrains.buildServer.artifactsMirror.torrent.TorrentUtil;
import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.BuildServerListener;
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TorrentTrackerManager {
  private TorrentTracker myTorrentTracker;
  private TorrentSeeder myTorrentSeeder;

  public TorrentTrackerManager(@NotNull final RootUrlHolder rootUrlHolder,
                               @NotNull EventDispatcher<BuildServerListener> dispatcher) {
    myTorrentSeeder = new TorrentSeeder();
    myTorrentTracker = new TorrentTracker();

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
        List<TrackedTorrent> removedTorrents = new ArrayList<TrackedTorrent>();
        for (TrackedTorrent ti : myTorrentTracker.getTrackedTorrents()) {
          if (ti.seeders() + ti.leechers() == 0) {
            removedTorrents.add(ti);
          }
        }

        for (TrackedTorrent removed : removedTorrents) {
          myTorrentTracker.removeTrackedTorrent(removed);
        }
      }
    });
  }

  public void createTorrent(@NotNull File srcFile, @NotNull File torrentsStore) {
    TorrentUtil.getOrCreateTorrent(srcFile, torrentsStore, myTorrentTracker.getAnnounceURI());
  }

  public void seedTorrent(@NotNull File torrentFile, @NotNull File srcFile) {
    myTorrentSeeder.seedTorrent(torrentFile, srcFile);
  }

  public int getActiveClientsNum() {
    return myTorrentSeeder.getDownloadingClientsNum();
  }

  @NotNull
  public List<TorrentSeeder.TorrentClient> getAllClients() {
    return myTorrentSeeder.getTorrentClients();
  }

  public int getAnnouncedTorrentsNum() {
    return myTorrentTracker.getTrackedTorrents().size();
  }
}
