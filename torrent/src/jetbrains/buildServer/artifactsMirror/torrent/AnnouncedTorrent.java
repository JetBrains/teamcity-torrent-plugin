package jetbrains.buildServer.artifactsMirror.torrent;

import com.turn.ttorrent.tracker.TrackedTorrent;
import jetbrains.buildServer.util.Dates;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Date;

public class AnnouncedTorrent implements Comparable<AnnouncedTorrent> {
  private final TrackedTorrent myTorrent;
  private final File mySrcFile;
  private final Date myAnnounceDate;
  private final File myTorrentFile;

  AnnouncedTorrent(@NotNull File srcFile, @NotNull File torrentFile, @NotNull TrackedTorrent torrent) {
    mySrcFile = srcFile;
    myTorrentFile = torrentFile;
    myTorrent = torrent;
    myAnnounceDate = Dates.now();
  }

  @NotNull
  public TrackedTorrent getTorrent() {
    return myTorrent;
  }

  @NotNull
  public File getTorrentFile() {
    return myTorrentFile;
  }

  @NotNull
  public File getSrcFile() {
    return mySrcFile;
  }

  @NotNull
  public Date getAnnounceDate() {
    return myAnnounceDate;
  }

  public int compareTo(AnnouncedTorrent o) {
    return myAnnounceDate.compareTo(o.getAnnounceDate());
  }

  @NotNull
  public String getFileSize() {
    return "0";
  }
}
