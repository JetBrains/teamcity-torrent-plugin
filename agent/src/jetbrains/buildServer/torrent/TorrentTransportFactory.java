package jetbrains.buildServer.torrent;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.StreamUtil;
import com.turn.ttorrent.common.Torrent;
import com.turn.ttorrent.tracker.TrackerHelper;
import jetbrains.buildServer.ArtifactsConstants;
import jetbrains.buildServer.NetworkUtil;
import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.agent.CurrentBuildTracker;
import jetbrains.buildServer.artifacts.DependencyResolverContext;
import jetbrains.buildServer.artifacts.TransportFactoryExtension;
import jetbrains.buildServer.artifacts.URLContentRetriever;
import jetbrains.buildServer.http.HttpUtil;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.torrent.seeder.TorrentsDirectorySeeder;
import jetbrains.buildServer.torrent.torrent.TeamcityTorrentClient;
import jetbrains.buildServer.torrent.torrent.TorrentUtil;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Sergey.Pak
 *         Date: 7/31/13
 *         Time: 2:52 PM
 */
public class TorrentTransportFactory implements TransportFactoryExtension {

  private final static Logger LOG = Logger.getInstance(TorrentTransportFactory.class.getName());

  public static final String TEAMCITY_IVY = "teamcity-ivy.xml";
  public static final String TEAMCITY_TORRENTS = ArtifactsConstants.TEAMCITY_ARTIFACTS_DIR + "/torrents/";

  public static final int MIN_SEEDERS_COUNT_TO_TRY=2;

  public static final String TEAMCITY_ARTIFACTS_TRANSPORT = "teamcity.artifacts.transport";


  private final AgentTorrentsManager myAgentTorrentsManager;
  private final CurrentBuildTracker myBuildTracker;
  private final TorrentConfiguration myConfiguration;

  public TorrentTransportFactory(@NotNull final AgentTorrentsManager agentTorrentsManager,
                                 @NotNull final CurrentBuildTracker currentBuildTracker,
                                 @NotNull final TorrentConfiguration configuration) {
    myAgentTorrentsManager = agentTorrentsManager;
    myBuildTracker = currentBuildTracker;
    myConfiguration = configuration;
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


  @Nullable
  public URLContentRetriever getTransport(@NotNull DependencyResolverContext context) {

    final BuildProgressLogger buildLogger = myBuildTracker.getCurrentBuild().getBuildLogger();
    if (!shouldUseTorrentTransport()) {
      TorrentUtil.log2Build("BitTorrent artifacts transport is disabled in server settings", buildLogger);
      return null;
    }

    if (NetworkUtil.isLocalHost(context.getServerUrl().getHost())) {
      TorrentUtil.log2Build("BitTorrent artifacts transport is not used for localhost", buildLogger);
      return null;
    }

    if (!myAgentTorrentsManager.isTorrentEnabled()){
      return null;
    }

    return new TorrentTransport(myAgentTorrentsManager.getTorrentsDirectorySeeder(),
            createHttpClient(context),
            buildLogger);
  }

  private boolean shouldUseTorrentTransport() {
    final String param = myBuildTracker.getCurrentBuild().getSharedConfigParameters().get(TEAMCITY_ARTIFACTS_TRANSPORT);
    if (param != null) {
      return param.equals(TorrentTransport.class.getSimpleName());
    }
    return myConfiguration.isTransportEnabled();
  }

  protected static class TorrentTransport implements URLContentRetriever {

    private final HttpClient myClient;
    private final TeamcityTorrentClient mySeeder;
    private final TorrentsDirectorySeeder myDirectorySeeder;
    private final BuildProgressLogger myBuildLogger;
    private final AtomicReference<Thread> myCurrentDownload;
    private final AtomicBoolean myInterrupted;

    private final Map<String, String> myTorrentsForArtifacts;

    protected TorrentTransport(@NotNull final TorrentsDirectorySeeder directorySeeder,
                               @NotNull final HttpClient client,
                               @NotNull final BuildProgressLogger buildLogger) {
      myDirectorySeeder = directorySeeder;
      mySeeder = myDirectorySeeder.getTorrentSeeder();
      myClient = client;
      myBuildLogger = buildLogger;
      myTorrentsForArtifacts = new HashMap<String, String>();
      myCurrentDownload = new AtomicReference<Thread>();
      myInterrupted = new AtomicBoolean(false);
    }

    @Nullable
    public String downloadUrlTo(@NotNull final String urlString, @NotNull final File target) throws IOException {
      ParsedArtifactPath parsedArtifactUrl = new ParsedArtifactPath(urlString);
      if (urlString.endsWith(TEAMCITY_IVY)){
        // downloading teamcity-ivy.xml and parsing it:
        final String digest = parseArtifactsList(urlString, target);
        return digest;
      }

      Torrent torrent = downloadTorrent(parsedArtifactUrl);
      if (torrent == null) {
        return null;
      }

      try {
        myBuildLogger.progressStarted("Downloading " + target.getName() + " via torrent.");
        if (TrackerHelper.getSeedersCount(torrent) == 0) {
          log2Build("no seeders for " + urlString);
          return null;
        }
        final long startTime = System.currentTimeMillis();

        final AtomicReference<Exception> occuredException = new AtomicReference<Exception>();
        if (mySeeder.isSeeding(torrent)){
          log2Build(String.format("Already seeding torrent (name: %s, hash: %s)", torrent.getName(), torrent.getHexInfoHash()));
          if (!target.exists()){
            log2Build("Target file does not exist. Will copy it from local storage");
            final File parentFolder = mySeeder.findSeedingTorrentFolder(torrent);
            File srcFile = new File(parentFolder, torrent.getName());
            if (srcFile.exists()) {
              FileUtil.copy(srcFile, target);
            }
          }
          if (target.exists()) {
            return torrent.getHexInfoHash();
          } else {
            mySeeder.stopSeeding(torrent);
          }
        }
        Thread th = mySeeder.downloadAndShareOrFailAsync(
                torrent, target, target.getParentFile(), getDownloadTimeoutSec(), MIN_SEEDERS_COUNT_TO_TRY, myInterrupted, occuredException);
        myCurrentDownload.set(th);
        th.join();
        myCurrentDownload.set(null);
        if (occuredException.get() != null){
          throw occuredException.get();
        }

        final long took = System.currentTimeMillis() - startTime + 1; // to avoid division by zero
        final long fileSize = target.length();
        log2Build(String.format("Download successfull. Avg speed %d kb/s. Saving torrent..", fileSize / took));
        File parentDir = getRealParentDir(target, parsedArtifactUrl.getArtifactPath());
        File torrentFile = new File(parentDir, parsedArtifactUrl.getTorrentPath());
        torrentFile.getParentFile().mkdirs();
        torrent.save(torrentFile);

        myDirectorySeeder.registerSrcAndTorrentFile(target, torrentFile, true);
        return torrent.getHexInfoHash();

      } catch (IOException e) {
        log2Build(String.format("Unable to download torrent for %s: %s", urlString, e.getMessage()));
        throw new IOException("Unable to download torrent for " + urlString, e);
      } catch (NoSuchAlgorithmException e) {
        throw new IOException("Unable to hash torrent for " + urlString, e);
      } catch (InterruptedException e) {
        throw new IOException("Torrent download has been interrupted " + urlString, e);
      } catch (RuntimeException ex) {
        log2Build(String.format("Unable to download artifact %s: %s", urlString, ex.getMessage()));
        throw ex;
      } catch (Exception ex){
        log2Build(String.format("Unable to download artifact %s: %s", urlString, ex.getMessage()));
        throw new IOException(ex);
      } finally {
        myBuildLogger.progressFinished();
      }
    }

    @Nullable
    public String getDigest(@NotNull final String urlString) throws IOException {
      ParsedArtifactPath parsedArtifactUrl = new ParsedArtifactPath(urlString);
      Torrent torrent = downloadTorrent(parsedArtifactUrl);
      return torrent == null ? null : torrent.getHexInfoHash();
    }

    public void interrupt() {
      final Thread thread = myCurrentDownload.get();
      if (thread != null){
        thread.interrupt();
      }
      myInterrupted.set(true);
    }

    private String parseArtifactsList(@NotNull final String teamcityIvyUrl, @NotNull final File target) {
      try {
        byte[] ivyData = download(teamcityIvyUrl);
        XPath xpath = XPathFactory.newInstance().newXPath();
        NodeList artifactList = (NodeList) xpath.evaluate("/ivy-module/publications/artifact",
                new InputSource(new ByteArrayInputStream(ivyData)), XPathConstants.NODESET);
        Set<String> artifactsSet = new HashSet<String>();
        for (int i = 0; i < artifactList.getLength(); i++) {
          Node artifact = artifactList.item(i);
          final String artifactName = artifact.getAttributes().getNamedItem("name").getTextContent();
          final String artifactExt = artifact.getAttributes().getNamedItem("ext").getTextContent();
          if (!StringUtil.isEmpty(artifactExt)) {
            artifactsSet.add(artifactName + "." + artifactExt);
          } else {
            artifactsSet.add(artifactName);
          }
        }

        for (String s : artifactsSet) {
          if (s.startsWith(ArtifactsConstants.TEAMCITY_ARTIFACTS_DIR))
            continue;
          String proposedTorrentName = String.format("%s%s.torrent", TEAMCITY_TORRENTS, s);
          if (artifactsSet.contains(proposedTorrentName)){
            myTorrentsForArtifacts.put(s, proposedTorrentName);
          }
        }

        final NodeList info = (NodeList) xpath.evaluate("ivy-module/info",
                new InputSource(new ByteArrayInputStream(ivyData)), XPathConstants.NODESET);

        if (info.getLength()==1){
          final Node infoNode = info.item(0);
          final String module = infoNode.getAttributes().getNamedItem("module").getTextContent();
          final String revision = infoNode.getAttributes().getNamedItem("revision").getTextContent();
          FileUtils.writeByteArrayToFile(target, ivyData);
          return String.format("%s_%s_%s", TEAMCITY_IVY, module, revision);
        }

      } catch (Exception e) {
        log2Build(String.format("Unknown error while parsing %s: %s", TEAMCITY_IVY, e.getMessage()));
      }
      return null;
    }

    private void log2Build(String msg) {
      TorrentUtil.log2Build(msg, myBuildLogger);
    }


    private Torrent downloadTorrent(@NotNull final ParsedArtifactPath parsedArtifactUrl) {
      final String torrentRelativePath = myTorrentsForArtifacts.get(parsedArtifactUrl.getArtifactPath());
      if (torrentRelativePath == null)
        return null;

      try {
        byte[] torrentData = download(parsedArtifactUrl.getTorrentUrl());
        return new Torrent(torrentData, true);
      } catch (NoSuchAlgorithmException e) {
        LOG.error("NoSuchAlgorithmException", e);
      } catch (IOException e) {
        log2Build(String.format("Unable to download: %s", e.getMessage()));
      }
      return null;
    }

    protected byte[] download(final String urlString) throws IOException {
      final HttpMethod getMethod = new GetMethod(urlString);
      InputStream in = null;
      try {
        myClient.executeMethod(getMethod);
        if (getMethod.getStatusCode() != HttpStatus.SC_OK) {
          throw new IOException(String.format("Problem [%d] while downloading %s: %s", getMethod.getStatusCode(), urlString, getMethod.getStatusText()));
        }
        in = getMethod.getResponseBodyAsStream();
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        StreamUtil.copyStreamContent(in, bOut);
        return bOut.toByteArray();
      } finally {
        FileUtil.close(in);
        getMethod.releaseConnection();
      }
    }

    private static File getRealParentDir(File file, String relativePath) {
      String path = file.getAbsolutePath().replaceAll("\\\\", "/");
      if (path.endsWith(relativePath)) {
        return new File(path.substring(0, path.length() - relativePath.length()));
      } else {
        return null;
      }
    }

    private long getDownloadTimeoutSec() {
      return TeamCityProperties.getLong("teamcity.torrent.download.timeout", 10L);
    }

  }

}
