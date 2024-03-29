<%@ include file="/include.jsp" %>


<jsp:useBean id="torrentConfigurator" type="jetbrains.buildServer.torrent.TorrentConfigurator" scope="request"/>
<jsp:useBean id="announcedTorrentsNum" type="java.lang.Integer" scope="request"/>
<jsp:useBean id="connectedClientsNum" type="java.lang.Integer" scope="request"/>
<jsp:useBean id="seededTorrentsNum" type="java.lang.Integer" scope="request"/>
<jsp:useBean id="activePeersCount" type="java.lang.Integer" scope="request"/>
<jsp:useBean id="totalSpeedMegabytesPerSecond" type="java.lang.String" scope="request"/>
<form method="post" action="<c:url value='/admin/torrentSettings.html'/>">
  <table class="runnerFormTable">
<tr>
  <th>Torrent tracker announce URL:</th>
  <td><c:out value="${torrentConfigurator.announceUrl}"/></td>
</tr>
    <c:if test="${torrentConfigurator.trackerEnabled}">
      <tr>
        <th>Torrent tracker information:</th>
        <td>
          <ul style="margin-top:0; padding-left: 1em;">
            <li>Announced torrents: <strong>${announcedTorrentsNum}</strong></li>
            <li>Connected clients: <strong>${connectedClientsNum}</strong></li>
          </ul>
        </td>
      </tr>
    </c:if>
    <tr>
      <th>Server seeding information:</th>
      <td>
        <ul style="margin-top:0; padding-left: 1em;">
          <li>Downloading clients: <strong>${activePeersCount}</strong></li>
          <li>Total downloading speed (MB/s): <strong>${totalSpeedMegabytesPerSecond}</strong></li>
          <li>Number of currently seeded torrents: <strong>${seededTorrentsNum}
            (of ${torrentConfigurator.maxNumberOfSeededTorrents})</strong></li>
        </ul>
      </td>
    </tr>
    <tr>
      <th>Server settings:</th>
      <td>
        <div>
          <forms:checkbox name="seedingEnabled" checked="${torrentConfigurator.seedingEnabled}"/>
          <label for="seedingEnabled">Enable artifacts seeding by server</label>
        </div>
        <div>
          <forms:checkbox name="downloadEnabled" checked="${torrentConfigurator.downloadEnabled}"/>
          <label for="downloadEnabled">Allow users to download artifacts via a BitTorrent client</label>
        </div>
      </td>
    </tr>
    <tr>
      <th>Agent settings:</th>
      <td>
        <div>
          <forms:checkbox name="agentSeedingEnabled" checked="${torrentConfigurator.agentSeedingEnabled}"/>
          <label for="agentSeedingEnabled">Enable artifacts seeding by agents</label>
        </div>
        <div>
          <forms:checkbox name="agentDownloadEnabled" checked="${torrentConfigurator.agentDownloadingEnabled}"/>
          <label for="agentDownloadEnabled">Allow agents to download artifacts via BitTorrent protocol</label>
        </div>
      </td>
    </tr>
  <tr>
    <td colspan="2"><forms:submit label="Save" name="save"/></td>
  </tr>
  </table>
</form>