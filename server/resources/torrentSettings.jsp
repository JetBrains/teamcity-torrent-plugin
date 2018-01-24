<%@ include file="/include.jsp" %>
<jsp:useBean id="torrentConfigurator" type="jetbrains.buildServer.torrent.TorrentConfigurator" scope="request"/>
<jsp:useBean id="announcedTorrentsNum" type="java.lang.Integer" scope="request"/>
<jsp:useBean id="connectedClientsNum" type="java.lang.Integer" scope="request"/>
<jsp:useBean id="seededTorrentsNum" type="java.lang.Integer" scope="request"/>
<jsp:useBean id="downloadEnabledKey" type="java.lang.String" scope="request"/>
<jsp:useBean id="seedingEnabledKey" type="java.lang.String" scope="request"/>
<jsp:useBean id="activePeersCount" type="java.lang.Integer" scope="request"/>
<jsp:useBean id="totalSpeedMegabytesPerSecond" type="java.lang.Double" scope="request"/>
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
            <li>announced torrents: <strong>${announcedTorrentsNum}</strong></li>
            <li>connected clients: <strong>${connectedClientsNum}</strong></li>
          </ul>
        </td>
      </tr>
    </c:if>
    <tr>
      <th>Server seeding information:</th>
      <td>
        <ul style="margin-top:0; padding-left: 1em;">
          <li>downloading clients: <strong>${activePeersCount}</strong></li>
          <li>Total uploading speed (MB/s): <strong>${totalSpeedMegabytesPerSecond}</strong></li>
          <li>number of currently seeded torrents: <strong>${seededTorrentsNum}
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
          To enable downloading/seeding artifacts from the agent set the following configuration parameters in a project or a build configuration
          <ul style="margin-top:0; padding-left: 1em;">
            <li><strong>${downloadEnabledKey}=true</strong></li>
            <li><strong>${seedingEnabledKey}=true</strong></li>
          </ul>
        </div>
      </td>
    </tr>
  <tr>
    <td colspan="2"><forms:submit label="Save" name="save"/></td>
  </tr>
  </table>
</form>