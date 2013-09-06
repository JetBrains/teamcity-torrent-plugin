package jetbrains.buildServer.artifactsMirror;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.StreamUtil;
import com.turn.ttorrent.client.announce.*;
import com.turn.ttorrent.common.Peer;
import com.turn.ttorrent.common.Torrent;
import com.turn.ttorrent.common.protocol.TrackerMessage;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.CurrentBuildTracker;
import jetbrains.buildServer.artifacts.ArtifactDependencyInfo;
import jetbrains.buildServer.artifacts.DependencyResolverContext;
import jetbrains.buildServer.artifacts.TransportFactoryExtension;
import jetbrains.buildServer.artifacts.URLContentRetriever;
import jetbrains.buildServer.artifactsMirror.seeder.FileLink;
import jetbrains.buildServer.artifactsMirror.seeder.TorrentsDirectorySeeder;
import jetbrains.buildServer.artifactsMirror.torrent.TeamcityTorrentClient;
import jetbrains.buildServer.http.HttpUtil;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.util.FileUtil;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.*;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Sergey.Pak
 *         Date: 7/31/13
 *         Time: 2:52 PM
 */
public class TorrentTransportFactory implements TransportFactoryExtension {

  private final static Logger LOG = Logger.getInstance(TorrentTransportFactory.class.getName());

  public static final String TEAMCITY_IVY = "teamcity-ivy.xml";
  public static final String TEAMCITY_TORRENTS = ".teamcity/torrents/";

  public static final String TEAMCITY_ARTIFACTS_TRANSPORT = "teamcity.artifacts.transport";


  private static final Pattern FILE_PATH_PATTERN = Pattern.compile("(.*?)/repository/download/([^/]+)/([^/]+)/(.+?)(\\?branch=.+)?");

  private final AgentTorrentsManager myAgentTorrentsManager;
  private final CurrentBuildTracker myBuildTracker;

  public TorrentTransportFactory(@NotNull final AgentTorrentsManager agentTorrentsManager,
                                 @NotNull final CurrentBuildTracker currentBuildTracker) {
    myAgentTorrentsManager = agentTorrentsManager;
    myBuildTracker = currentBuildTracker;
  }

  private HttpClient createHttpClient(@NotNull final DependencyResolverContext context) {
    HttpClient client = HttpUtil.createHttpClient(context.getConnectionTimeout());
    client.getParams().setAuthenticationPreemptive(true);
    Credentials defaultcreds = new UsernamePasswordCredentials(context.getUsername(), context.getPassword());
    client.getState().setCredentials(new AuthScope(AuthScope.ANY_HOST,
        AuthScope.ANY_PORT,
        AuthScope.ANY_REALM),
        defaultcreds);
    return client;
  }


  @NotNull
  public URLContentRetriever getTransport(@NotNull DependencyResolverContext context) {

    return new TorrentTransport(myAgentTorrentsManager.getTorrentsDirectorySeeder(), createHttpClient(context), myBuildTracker.getCurrentBuild());
  }


  protected static class TorrentTransport implements URLContentRetriever{

    private final HttpClient myClient;
    private final TeamcityTorrentClient mySeeder;
    private final TorrentsDirectorySeeder myDirectorySeeder;
    private final AgentRunningBuild myBuild;

    protected TorrentTransport(@NotNull final TorrentsDirectorySeeder directorySeeder,
                               @NotNull final HttpClient client,
                               @NotNull final AgentRunningBuild agentBuild) {
      myDirectorySeeder = directorySeeder;
      mySeeder = myDirectorySeeder.getTorrentSeeder();
      myClient = client;
      myBuild = agentBuild;
    }

    @Nullable
    public String downloadUrlTo(@NotNull final String urlString, @NotNull final File target) throws IOException {
      Torrent torrent = getTorrent(urlString);
      if (torrent == null){
        return null;
      }
      LOG.info("Will attempt to download " + target.getName() + " via torrent.");
      try {
        final List<List<URI>> announceList = torrent.getAnnounceList();
        final AtomicBoolean hasSeeders = new AtomicBoolean(false);

        final AnnounceResponseListener listener = new AnnounceResponseListener() {
          public void handleAnnounceResponse(int interval, int complete, int incomplete, String hexInfoHash) {
            hasSeeders.set(complete > 0);
          }
          public void handleDiscoveredPeers(List<Peer> peers, String hexInfoHash) {}
        };

        for (List<URI> uriList : announceList) {
          if (hasSeeders.get()){
            break;
          }
          for (URI uri : uriList) {
            if (hasSeeders.get()){
              break;
            }
            TrackerClient client = Announce.createTrackerClient(mySeeder.getSelfPeer(), uri);
            client.register(listener);
            try {
              client.announce(TrackerMessage.AnnounceRequestMessage.RequestEvent.NONE, false, torrent);
            } catch (AnnounceException e) {

            }
          }
        }

        if (!hasSeeders.get()) {
          LOG.info("no seeders for " + urlString);
          return null;
        }

        mySeeder.downloadAndShareOrFail(torrent, target, target.getParentFile(), getDownloadTimeoutSec());
        LOG.info("Download successfull. Saving torrent..");
        String realFilePath = getFilePathFromUrl(urlString);
        File parentDir = getRealParentDir(target, realFilePath);
        File torrentFile = new File(parentDir, TEAMCITY_TORRENTS + realFilePath + ".torrent");
        torrentFile.getParentFile().mkdirs();
        torrent.save(torrentFile);
        FileLink.createLink(target, torrentFile, myDirectorySeeder.getStorageDirectory());
        return torrent.getHexInfoHash();
      } catch (IOException e) {
        throw new IOException("Unable to download torrent for " + urlString, e);
      } catch (NoSuchAlgorithmException e) {
        throw new IOException("Unable to hash torrent for " + urlString, e);
      } catch (InterruptedException e) {
        throw new IOException("Torrent download has been interrupted " + urlString, e);
      }
    }

    @Nullable
    public String getDigest(@NotNull final String urlString) throws IOException {
      Torrent torrent = getTorrent(urlString);
      return torrent == null ? null : torrent.getHexInfoHash();
    }

    protected Torrent getTorrent(@NotNull final String urlString){
      if (!shouldUseTorrentTransport()){
        LOG.debug("Shouldn't use torrent transport for build type " + myBuild.getBuildTypeId());
        return null;
      }

      if (urlString.contains(TEAMCITY_IVY)){
        LOG.debug("Skip downloading teamcity-ivy.xml");
        return null;
      }
      LOG.info("Downloading torrent for " + urlString);
      Torrent torrent = downloadTorrent(urlString);
      if (torrent == null) {
        LOG.debug("No torrent file for " + urlString);
        return null;
      }
      if (torrent.getSize() < myDirectorySeeder.getFileSizeThresholdMb()*1024*1024){
        LOG.debug(String.format("File size is lower than threshold of %dMb", myDirectorySeeder.getFileSizeThresholdMb()));
        return null;
      }
      return torrent;
    }

    protected Torrent downloadTorrent(@NotNull final  String urlString){
      // adding path here:
      final Matcher matcher = FILE_PATH_PATTERN.matcher(urlString);
      if (!matcher.matches()){
        return null;
      }
      final StringBuilder b = new StringBuilder(urlString);
      final int realPathStart = matcher.start(4);
      b.insert(realPathStart, TEAMCITY_TORRENTS);
      int suffixPos = (matcher.group(5)==null? b.length() : TEAMCITY_TORRENTS.length() + matcher.start(5));
      b.insert(suffixPos, ".torrent");
      String downloadUrl = b.toString();
      final GetMethod getMethod = new GetMethod(downloadUrl);
      InputStream in = null;
      try {
        myClient.executeMethod(getMethod);
        if (getMethod.getStatusCode() != HttpStatus.SC_OK) {
          throw new IOException("Problem [" + getMethod.getStatusCode() + "] while downloading " + downloadUrl + ": "
              + getMethod.getStatusText());
        }
        in = getMethod.getResponseBodyAsStream();
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        StreamUtil.copyStreamContent(in, bOut);
        byte[] torrentData = bOut.toByteArray();

        return new Torrent(torrentData, true);
      } catch (NoSuchAlgorithmException e) {
        LOG.error("NoSuchAlgorithmException", e);
      } catch (IOException e) {
        LOG.debug("Unable to download: " + e.getMessage());
      } finally {
        FileUtil.close(in);
        getMethod.releaseConnection();
      }
      return null;

    }

    private String getFilePathFromUrl(@NotNull final String urlString){
      final Matcher matcher = FILE_PATH_PATTERN.matcher(urlString);
      if (matcher.matches()){
        return matcher.group(4);
      } else {
        return null;
      }
    }

    private static File getRealParentDir(File file, String relativePath){
      String path = file.getAbsolutePath().replaceAll("\\\\", "/");
      if (path.endsWith(relativePath)){
        return new File(path.substring(0, path.length() - relativePath.length()));
      } else {
        return null;
      }
    }

    private long getDownloadTimeoutSec(){
      String strValue = System.getProperty("teamcity.torrent.download.timeout", "300");
      try {
        return Long.parseLong(strValue);
      } catch (NumberFormatException e){
        return 300;
      }
    }

    private boolean shouldUseTorrentTransport(){
      final String param = myBuild.getSharedConfigParameters().get(TEAMCITY_ARTIFACTS_TRANSPORT);
      return param!=null && param.contains(this.getClass().getSimpleName());
    }
  }
}
