package jetbrains.buildServer.torrent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
* @author Sergey.Pak
*         Date: 10/4/13
*         Time: 11:33 AM
*/
public class ParsedArtifactPath {
  private static final Pattern FILE_PATH_PATTERN = Pattern.compile("(.*?)/repository/download/([^/]+)/([^/]+)/(.+?)(\\?branch=.+)?");

  @NotNull
  private final String myServerUrl;
  @NotNull
  private final String myModule;
  @NotNull
  private final String myRevision;
  @NotNull
  private final String myArtifactPath;
  @Nullable
  private final String myBranch;

  ParsedArtifactPath(@NotNull final String artifactUrl) throws IllegalArgumentException{
    final Matcher matcher = FILE_PATH_PATTERN.matcher(artifactUrl);
    if (!matcher.matches()){
      throw new IllegalArgumentException("Unable to parse " + artifactUrl);
    }
    myServerUrl = matcher.group(1);
    myModule = matcher.group(2);
    myRevision = matcher.group(3);
    myArtifactPath = matcher.group(4);
    myBranch = matcher.group(5);
  }

  @NotNull
  public String getServerUrl() {
    return myServerUrl;
  }

  @NotNull
  public String getArtifactPath() {
    return myArtifactPath;
  }

  @Nullable
  public String getBranch() {
    return myBranch;
  }

  public String getTorrentUrl(){
    return String.format("%s/repository/download/%s/%s/%s%s",
            myServerUrl, myModule, myRevision, getTorrentPath(),
            myBranch == null ? "" : "?branch="+ myBranch);
  }

  public String getTorrentPath(){
    return TorrentTransportFactory.TEAMCITY_TORRENTS + myArtifactPath + ".torrent";
  }
}
