package jetbrains.buildServer.torrent;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.StreamUtil;
import com.turn.ttorrent.common.TorrentFile;
import com.turn.ttorrent.common.TorrentMetadata;
import com.turn.ttorrent.common.TorrentParser;
import com.turn.ttorrent.common.TorrentUtils;
import jetbrains.buildServer.ArtifactsConstants;
import jetbrains.buildServer.agent.BuildAgentConfigurationEx;
import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.agent.CurrentBuildTracker;
import jetbrains.buildServer.artifacts.FileProgress;
import jetbrains.buildServer.artifacts.ProgressTrackingURLContentRetriever;
import jetbrains.buildServer.artifacts.TransportFactoryExtension;
import jetbrains.buildServer.artifacts.URLContentRetriever;
import jetbrains.buildServer.artifacts.impl.HttpTransport;
import jetbrains.buildServer.http.HttpUtil;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.messages.DefaultMessagesInfo;
import jetbrains.buildServer.torrent.seeder.TorrentsSeeder;
import jetbrains.buildServer.torrent.settings.LeechSettings;
import jetbrains.buildServer.torrent.torrent.TeamcityTorrentClient;
import jetbrains.buildServer.torrent.torrent.TorrentUtil;
import jetbrains.buildServer.torrent.util.TorrentsDownloadStatistic;
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
import java.net.URLDecoder;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static jetbrains.buildServer.torrent.Constants.TEAMCITY_IVY;

/**
 * @author Sergey.Pak
 * Date: 7/31/13
 * Time: 2:52 PM
 */
public class TorrentTransportFactory implements TransportFactoryExtension {

  public static final String TEAMCITY_TORRENTS = ArtifactsConstants.TEAMCITY_ARTIFACTS_DIR + "/torrents/";
  private final static Logger LOG = Logger.getInstance("jetbrains.TorrentPlugin");

  public static final String TEAMCITY_ARTIFACTS_TRANSPORT = "teamcity.artifacts.transport";


  private final AgentTorrentsManager myAgentTorrentsManager;
  private final CurrentBuildTracker myBuildTracker;
  private final TorrentConfiguration myConfiguration;
  private final BuildAgentConfigurationEx myAgentConfig;
  private final LeechSettings myLeechSettings;
  @NotNull
  private final TorrentFilesFactory myTorrentFilesFactory;

  public TorrentTransportFactory(@NotNull final AgentTorrentsManager agentTorrentsManager,
                                 @NotNull final CurrentBuildTracker currentBuildTracker,
                                 @NotNull final TorrentConfiguration configuration,
                                 @NotNull final BuildAgentConfigurationEx config,
                                 @NotNull final LeechSettings leechSettings,
                                 @NotNull final TorrentFilesFactory torrentFilesFactory) {
    myAgentTorrentsManager = agentTorrentsManager;
    myBuildTracker = currentBuildTracker;
    myConfiguration = configuration;
    myAgentConfig = config;
    myLeechSettings = leechSettings;
    myTorrentFilesFactory = torrentFilesFactory;
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

    if (!myAgentTorrentsManager.isTransportEnabled()) {
      return null;
    }

    return new TorrentTransport(myAgentTorrentsManager.getTorrentsSeeder(),
            createHttpClient(),
            buildLogger,
            myAgentConfig.getServerUrl(),
            myAgentTorrentsManager.getTorrentsDownloadStatistic(),
            myLeechSettings,
            myTorrentFilesFactory);
  }

  private boolean shouldUseTorrentTransport() {
    final String param = myBuildTracker.getCurrentBuild().getSharedConfigParameters().get(TEAMCITY_ARTIFACTS_TRANSPORT);
    if (param != null) {
      return param.equals(TorrentTransport.class.getSimpleName());
    }
    return myLeechSettings.isDownloadEnabled();
  }

  protected static class TorrentTransport implements URLContentRetriever, ProgressTrackingURLContentRetriever {

    private final HttpClient myHttpClient;
    private final HttpTransport myDelegate;
    private final TeamcityTorrentClient myClient;
    private final TorrentsSeeder mySeeder;
    private final BuildProgressLogger myBuildLogger;
    private final AtomicReference<Thread> myCurrentDownload;
    private final AtomicBoolean myInterrupted;
    private final LeechSettings myLeechSettings;
    @NotNull
    private final TorrentsDownloadStatistic myTorrentsDownloadStatistic;
    @NotNull
    private final TorrentFilesFactory myTorrentFilesFactory;

    private final Map<String, String> myTorrentsForArtifacts;

    protected TorrentTransport(@NotNull final TorrentsSeeder seeder,
                               @NotNull final HttpClient httpClient,
                               @NotNull final BuildProgressLogger buildLogger,
                               @NotNull final String serverUrl,
                               @NotNull final TorrentsDownloadStatistic torrentsDownloadStatistic,
                               @NotNull final LeechSettings leechSettings,
                               @NotNull final TorrentFilesFactory torrentFilesFactory) {
      myDelegate = new HttpTransport(httpClient, serverUrl);
      mySeeder = seeder;
      myLeechSettings = leechSettings;
      myClient = mySeeder.getClient();
      myTorrentFilesFactory = torrentFilesFactory;
      myTorrentsDownloadStatistic = torrentsDownloadStatistic;
      myHttpClient = httpClient;
      myBuildLogger = buildLogger;
      myTorrentsForArtifacts = new HashMap<String, String>();
      myCurrentDownload = new AtomicReference<Thread>();
      myInterrupted = new AtomicBoolean(false);
    }

    @Nullable
    @Override
    public String getDigest(@NotNull String url) throws IOException {
      return myDelegate.getDigest(url);
    }

    @Nullable
    @Override
    public String downloadUrlTo(@NotNull String url, @NotNull File target, @NotNull final FileProgress fileDownloadProgress) throws IOException {
      if (url.endsWith(TEAMCITY_IVY)) {
        // downloading teamcity-ivy.xml and parsing it:
        return parseArtifactsList(url, target);
      }
      final String decodedUrl = URLDecoder.decode(url, "UTF-8");
      ParsedArtifactPath parsedArtifactUrl = new ParsedArtifactPath(decodedUrl);

      long start = System.currentTimeMillis();

      TorrentMetadata torrent = downloadTorrent(parsedArtifactUrl);
      if (torrent == null) {
        return null;
      }

      long maxTimeForDownloadTorrentMs = 1000;
      final long downloadTime = System.currentTimeMillis() - start;
      if (downloadTime > maxTimeForDownloadTorrentMs) {
        LOG.info("torrent " + parsedArtifactUrl + " was downloaded in " + downloadTime + " ms");
      }

      File torrentFile = myTorrentFilesFactory.getTorrentFile();
      TorrentUtil.saveTorrentToFile(torrent, torrentFile);
      String hexInfoHash = torrent.getHexInfoHash();
      String name = torrent.getDirectoryName();
      List<String> fileNames = TorrentUtils.getTorrentFileNames(torrent);
      long size = 0;
      for (TorrentFile file : torrent.getFiles()) {
        size += file.size;
      }
      fileDownloadProgress.setExpectedLength(size);
      torrent = null;

      try {
        LOG.info(String.format("trying to download %s via bittorrent", url));
        final String message = "Downloading " + target.getName() + " via BitTorrent protocol.";
        myBuildLogger.logMessage(DefaultMessagesInfo.createProgressMessage(message));

        final int minSeedersForDownload = myLeechSettings.getMinSeedersForDownload();

        final int timeoutForConnectToPeersMs = 5000;

        final AtomicReference<Exception> exceptionHolder = new AtomicReference<Exception>();
        Loggers.AGENT.debug("start download file " + target.getName());

        Thread th = myClient.downloadAndShareOrFailAsync(
                torrentFile,
                fileNames,
                hexInfoHash,
                target,
                target.getParentFile(),
                fileDownloadProgress,
                myLeechSettings.getMaxPieceDownloadTime() * 1000,
                minSeedersForDownload,
                timeoutForConnectToPeersMs,
                exceptionHolder);
        myCurrentDownload.set(Thread.currentThread());
        th.join();
        myCurrentDownload.set(null);
        if (exceptionHolder.get() != null) {
          myTorrentsDownloadStatistic.fileDownloadFailed();
          Loggers.AGENT.warnAndDebugDetails("unable to download file " + name + " "
                  + exceptionHolder.get().getMessage(), exceptionHolder.get());
          throw exceptionHolder.get();
        }

        if (size != target.length()) {
          myTorrentsDownloadStatistic.fileDownloadFailed();
          log2Build(String.format("Failed to download file completely via BitTorrent protocol. Expected file size: %s, actual file size: %s", String.valueOf(size), String.valueOf(target.length())));
          return null;
        }
      } catch (InterruptedException e) {
        throw new IOException("Torrent download has been interrupted " + url, e);
      } catch (RuntimeException ex) {
        log2Build(String.format("Unable to download artifact %s: %s", url, ex.getMessage()));
        throw ex;
      } catch (Exception ex) {
        log2Build(String.format("Unable to download artifact %s: %s", url, ex.getMessage()));
        throw new IOException(ex);
      }
      // return standard digest
      String digest;
      try {
        digest = getDigest(url);
      } catch (IOException e) {
        Loggers.AGENT.warnAndDebugDetails("Unable to execute digest request by URL " + url, e);
        throw e;
      }
      myTorrentsDownloadStatistic.fileDownloaded();
      return digest;
    }

    @Override
    public String getId() {
      return "Torrent transport";
    }

    @Nullable
    public String downloadUrlTo(@NotNull final String urlString, @NotNull final File target) throws IOException {
      return downloadUrlTo(urlString, target, new FileProgress.Adapter());
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

    private TorrentMetadata downloadTorrent(@NotNull final ParsedArtifactPath parsedArtifactUrl) {
      final String torrentRelativePath = myTorrentsForArtifacts.get(parsedArtifactUrl.getArtifactPath());
      if (torrentRelativePath == null)
        return null;

      try {
        byte[] torrentData = download(parsedArtifactUrl.getTorrentUrl());
        return new TorrentParser().parse((torrentData));
      } catch (IOException e) {
        log2Build(String.format("Unable to download: %s", e.getMessage()));
      }
      final String msg = "No .torrent file for: " + parsedArtifactUrl.getArtifactPath() + ", will use default transport";
      log2Build(msg);
      Loggers.AGENT.info(msg);
      myTorrentsDownloadStatistic.fileDownloadFailed();
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
  }

}
