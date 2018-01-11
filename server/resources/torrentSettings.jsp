<%@ include file="/include.jsp" %>
<jsp:useBean id="torrentConfigurator" type="jetbrains.buildServer.torrent.TorrentConfigurator" scope="request"/>
<jsp:useBean id="announcedTorrentsNum" type="java.lang.Integer" scope="request"/>
<jsp:useBean id="connectedClientsNum" type="java.lang.Integer" scope="request"/>
<jsp:useBean id="seededTorrentsNum" type="java.lang.Integer" scope="request"/>
<jsp:useBean id="downloadEnabledKey" type="java.lang.String" scope="request"/>
<jsp:useBean id="seedingEnabledKey" type="java.lang.String" scope="request"/>
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
            <li>connected/downloading clients: <strong>${connectedClientsNum}</strong></li>
            <li>number of currently seeded torrents: <strong>${seededTorrentsNum}
              (of ${torrentConfigurator.maxNumberOfSeededTorrents})</strong></li>
          </ul>
        </td>
      </tr>
    </c:if>
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
          For enable downloading/seeding artifacts by agent set next properties as <strong>true</strong> in build parameters:
          <ul style="margin-top:0; padding-left: 1em;">
            <li><strong>${downloadEnabledKey}</strong> - for enable downloading</li>
            <li><strong>${seedingEnabledKey}</strong> - for enable seeding</li>
          </ul>
        </div>
      </td>
    </tr>
  <tr>
    <td colspan="2"><forms:submit label="Save" name="save"/></td>
  </tr>
  </table>
</form>