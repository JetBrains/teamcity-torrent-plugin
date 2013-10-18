<%@ include file="/include.jsp" %>
<jsp:useBean id="torrentConfigurator" type="jetbrains.buildServer.artifactsMirror.TorrentConfigurator" scope="request"/>
<jsp:useBean id="announcedTorrentsNum" type="java.lang.Integer" scope="request"/>
<jsp:useBean id="connectedClientsNum" type="java.lang.Integer" scope="request"/>
<jsp:useBean id="seededTorrentsNum" type="java.lang.Integer" scope="request"/>
<table class="runnerFormTable">
<tr>
  <th>Torrent tracker announce URL:</th>
  <td><c:out value="${torrentConfigurator.announceUrl}"/></td>
</tr>
<tr>
  <th>Torrent tracker:</th>
  <td>
    <%--<forms:checkbox name="trackerEnabled" checked="${torrentConfigurator.trackerEnabled}"/><label for="trackerEnabled"> enable torrent tracker</label>--%>
    <c:if test="${torrentConfigurator.trackerEnabled}">
    <ul style="margin-top:0; padding-left: 1em;">
      <li>announced torrents: <strong>${announcedTorrentsNum}</strong></li>
      <li>connected/downloading clients: <strong>${connectedClientsNum}</strong></li>
      <li>number of currently seeded torrents: <strong>${seededTorrentsNum} (of ${torrentConfigurator.maxNumberOfSeededTorrents})</strong></li>
    </ul>
    </c:if>
  </td>
</tr>
</table>
