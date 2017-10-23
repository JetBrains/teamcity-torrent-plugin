package jetbrains.buildServer.torrent;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.StreamUtil;
import com.turn.ttorrent.common.Torrent;
import com.turn.ttorrent.tracker.TrackerHelper;
import jetbrains.buildServer.ArtifactsConstants;
import jetbrains.buildServer.agent.BuildAgentConfigurationEx;
import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.agent.CurrentBuildTracker;
import jetbrains.buildServer.artifacts.ArtifactAccessor;
import jetbrains.buildServer.artifacts.ArtifactAccessorFactoryExtension;
import jetbrains.buildServer.artifacts.TransportFactoryExtension;
import jetbrains.buildServer.artifacts.URLContentRetriever;
import jetbrains.buildServer.artifacts.impl.HttpTransport;
import jetbrains.buildServer.artifacts.impl.TeamCityArtifactAccessor;
import jetbrains.buildServer.http.HttpUtil;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.torrent.seeder.TorrentsSeeder;
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
public class TorrentTransportFactory implements TransportFactoryExtension, ArtifactAccessorFactoryExtension {

  private final static Logger LOG = Logger.getInstance(TorrentTransportFactory.class.getName());

  public static final String TEAMCITY_IVY = "teamcity-ivy.xml";
  public static final String TEAMCITY_TORRENTS = ArtifactsConstants.TEAMCITY_ARTIFACTS_DIR + "/torrents/";

  public static final int MIN_SEEDERS_COUNT_TO_TRY = 2;

  public static final String TEAMCITY_ARTIFACTS_TRANSPORT = "teamcity.artifacts.transport";


  private final AgentTorrentsManager myAgentTorrentsManager;
  private final CurrentBuildTracker myBuildTracker;
  private final TorrentConfiguration myConfiguration;
  private final BuildAgentConfigurationEx myAgentConfig;

  public TorrentTransportFactory(@NotNull final AgentTorrentsManager agentTorrentsManager,
                                 @NotNull final CurrentBuildTracker currentBuildTracker,
                                 @NotNull final TorrentConfiguration configuration,
                                 @NotNull final BuildAgentConfigurationEx config) {
    myAgentTorrentsManager = agentTorrentsManager;
    myBuildTracker = currentBuildTracker;
    myConfiguration = configuration;
    myAgentConfig = config;
  }

  @Nullable
  @Override
  public ArtifactAccessor createArtifactAccessor(@NotNull Map<String, String> map) {
    return new TeamCityArtifactAccessor(getTransport(map), myConfiguration.getServerURL());
  }

  @NotNull
  @Override
  public String getType() {
    return "torrent";
  }

  private HttpClient createHttpClient() {
    String userName = myBuildTracker.getCurrentBuild().getAccessUser();
    String password = myBuildTracker.getCurrentBuild().getAccessCode();
    int connectionTimeout = 60;
    HttpClient client = HttpUtil.createHttpClient(connectionTimeout);
    client.getParams().setAuthenticationPreemptive(true);
    Credentials defaultcreds = new UsernamePasswordCredentials(userName, password);
    client.getState().setCredentials(new AuthScope(AuthScope.ANY_HOST,
                    AuthScope.ANY_PORT,
                    AuthScope.ANY_REALM),
            defaultcreds);
    String proxyHost = myAgentConfig.getServerProxyHost();
    if (proxyHost != null) {
      HttpUtil.configureProxy(client, proxyHost, myAgentConfig.getServerProxyPort(), myAgentConfig.getServerProxyCredentials());
    }
    return client;
  }

  @Nullable
  public URLContentRetriever getTransport(@NotNull Map<String, String> context) {

    final BuildProgressLogger buildLogger = myBuildTracker.getCurrentBuild().getBuildLogger();
    if (!shouldUseTorrentTransport()) {
      TorrentUtil.log2Build("BitTorrent artifacts transport is disabled in server settings", buildLogger);
      return null;
    }

    if (!myAgentTorrentsManager.isTorrentEnabled()) {
      return null;
    }

    return new TorrentTransport(myAgentTorrentsManager.getTorrentsSeeder(),
            createHttpClient(),
            buildLogger,
            myConfiguration.getServerURL());
  }

  private boolean shouldUseTorrentTransport() {
    final String param = myBuildTracker.getCurrentBuild().getSharedConfigParameters().get(TEAMCITY_ARTIFACTS_TRANSPORT);
    if (param != null) {
      return param.equals(TorrentTransport.class.getSimpleName());
    }
    return myConfiguration.isTransportEnabled();
  }

  protected static class TorrentTransport extends HttpTransport implements URLContentRetriever {

    private final HttpClient myHttpClient;
    private final TeamcityTorrentClient myClient;
    private final TorrentsSeeder mySeeder;
    private final BuildProgressLogger myBuildLogger;
    private final AtomicReference<Thread> myCurrentDownload;
    private final AtomicBoolean myInterrupted;

    private final Map<String, String> myTorrentsForArtifacts;

    protected TorrentTransport(@NotNull final TorrentsSeeder seeder,
                               @NotNull final HttpClient httpClient,
                               @NotNull final BuildProgressLogger buildLogger,
                               @NotNull final String serverUrl) {
      super(httpClient, serverUrl);
      mySeeder = seeder;
      myClient = mySeeder.getClient();
      myHttpClient = httpClient;
      myBuildLogger = buildLogger;
      myTorrentsForArtifacts = new HashMap<String, String>();
      myCurrentDownload = new AtomicReference<Thread>();
      myInterrupted = new AtomicBoolean(false);
    }

    @Nullable
    public String downloadUrlTo(@NotNull final String urlString, @NotNull final File target) throws IOException {
      ParsedArtifactPath parsedArtifactUrl = new ParsedArtifactPath(urlString);
      if (urlString.endsWith(TEAMCITY_IVY)) {
        // downloading teamcity-ivy.xml and parsing it:
        return parseArtifactsList(urlString, target);
      }

      Torrent torrent = downloadTorrent(parsedArtifactUrl);
      if (torrent == null) {
        return null;
      }

      try {
        myBuildLogger.progressStarted("Downloading " + target.getName() + " via BitTorrent protocol.");
        if (TrackerHelper.getSeedersCount(torrent) == 0) {
          log2Build("No seeders found for: " + urlString);
          return null;
        }
        final long startTime = System.currentTimeMillis();

        final AtomicReference<Exception> exceptionHolder = new AtomicReference<Exception>();
        Thread th = myClient.downloadAndShareOrFailAsync(
                torrent, target, target.getParentFile(), getDownloadTimeoutSec(), MIN_SEEDERS_COUNT_TO_TRY, myInterrupted, exceptionHolder);
        myCurrentDownload.set(th);
        th.join();
        myCurrentDownload.set(null);
        if (exceptionHolder.get() != null) {
          throw exceptionHolder.get();
        }

        // do not seed the file right now, we'll start seeding it once it appears in artifacts cache
        myClient.stopSeeding(torrent);

        if (torrent.getSize() != target.length()) {
          log2Build(String.format("Failed to download file completely via BitTorrent protocol. Expected file size: %s, actual file size: %s", String.valueOf(torrent.getSize()), String.valueOf(target.length())));
          return null;
        }

        final long took = System.currentTimeMillis() - startTime + 1; // to avoid division by zero
        final long fileSize = target.length();
        log2Build(String.format("Download successful. Avg speed %d kb/s.", fileSize / took));

        // return standard digest
        return getDigest(urlString);
      } catch (InterruptedException e) {
        throw new IOException("Torrent download has been interrupted " + urlString, e);
      } catch (RuntimeException ex) {
        log2Build(String.format("Unable to download artifact %s: %s", urlString, ex.getMessage()));
        throw ex;
      } catch (Exception ex) {
        log2Build(String.format("Unable to download artifact %s: %s", urlString, ex.getMessage()));
        throw new IOException(ex);
      } finally {
        myBuildLogger.progressFinished();
      }
    }

    public void interrupt() {
      final Thread thread = myCurrentDownload.get();
      if (thread != null) {
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
          if (artifactsSet.contains(proposedTorrentName)) {
            myTorrentsForArtifacts.put(s, proposedTorrentName);
          }
        }

        final NodeList info = (NodeList) xpath.evaluate("ivy-module/info",
                new InputSource(new ByteArrayInputStream(ivyData)), XPathConstants.NODESET);

        if (info.getLength() == 1) {
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
        LOG.warnAndDebugDetails("Failed to load downloaded torrent file, error: " + e.toString(), e);
      } catch (IOException e) {
        log2Build(String.format("Unable to download: %s", e.getMessage()));
      }
      return null;
    }

    protected byte[] download(final String urlString) throws IOException {
      final HttpMethod getMethod = new GetMethod(urlString);
      InputStream in = null;
      try {
        myHttpClient.executeMethod(getMethod);
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

    private long getDownloadTimeoutSec() {
      return TeamCityProperties.getLong("teamcity.torrent.download.timeout", 600L);
    }

  }

}
