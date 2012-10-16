package jetbrains.buildServer.artifactsMirror;

import jetbrains.buildServer.configuration.ChangeListener;
import jetbrains.buildServer.configuration.FilesWatcher;
import jetbrains.buildServer.util.CollectionsUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * User: Victory.Bedrosova
 * Date: 10/15/12
 * Time: 3:57 PM
 */
public class LinkWatcher implements FilesWatcher.WatchedFilesProvider, ChangeListener {
  @NotNull
  private final File myRoot;
  @NotNull
  private final FilesWatcher myFilesWatcher;
  @NotNull
  private final LinkWatcherListener myListener;
  private boolean myStarted;

  public LinkWatcher(@NotNull File root, @NotNull LinkWatcherListener listener) {
    myRoot = root;
    myFilesWatcher = new FilesWatcher(this);
    myListener = listener;
  }

  public void start() {
    myFilesWatcher.setSleepingPeriod(5000L);
    myFilesWatcher.registerListener(this);
    myFilesWatcher.start();
    myStarted = true;
  }

  public void stop() {
    myFilesWatcher.stop();
    myStarted = false;
  }

  public File[] getWatchedFiles() throws IOException {
    final Collection<File> result = LinkFile.getTargetFiles(myRoot);
    return result.toArray(new File[result.size()]);
  }

  public void changeOccured(String requestor) {
    for (File srcFile : CollectionsUtil.join(myFilesWatcher.getRemovedFiles(),
                                             myFilesWatcher.getNewFiles(),
                                             myFilesWatcher.getModifiedFiles())) {
      final File linkFile = LinkFile.getLinkFile(srcFile, myRoot);
      if (linkFile == null) continue;
      myListener.targetFileChanged(linkFile, srcFile);
    }
  }

  public boolean isStarted() {
    return myStarted;
  }

  public static interface LinkWatcherListener {
    void targetFileChanged(@NotNull File linkFile, @NotNull File targetFile);
  }
}
