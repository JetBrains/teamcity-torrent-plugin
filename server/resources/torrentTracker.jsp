<%@ include file="/include.jsp" %>
<jsp:useBean id="torrentConfigurator" type="jetbrains.buildServer.artifactsMirror.TorrentConfigurator" scope="request"/>
<jsp:useBean id="announcedTorrentsNum" type="java.lang.Integer" scope="request"/>
<jsp:useBean id="connectedClientsNum" type="java.lang.Integer" scope="request"/>
<jsp:useBean id="seededTorrentsNum" type="java.lang.Integer" scope="request"/>
<form method="post" action="<c:url value='/admin/torrentTrackerSettings.html'/>">
<table class="runnerFormTable">
<tr>
  <th>Torrent tracker announce URL:</th>
  <td><c:out value="${torrentConfigurator.announceUrl}"/></td>
</tr>
<tr>
  <th><label for="trackerEnabled">Torrent tracker:</label></th>
  <td>
    <forms:checkbox name="trackerEnabled" checked="${torrentConfigurator.trackerEnabled}"/><label for="trackerEnabled"> enable torrent tracker</label>
    <c:if test="${torrentConfigurator.trackerEnabled}">
    <ul style="margin-top:0; padding-left: 1em;">
      <li>announced torrents: <strong>${announcedTorrentsNum}</strong></li>
      <li>connected/downloading clients: <strong>${connectedClientsNum}</strong></li>
    </ul>
    </c:if>
  </td>
</tr>
<tr>
  <th><label for="seederEnabled">Torrent seeder:</label></th>
  <td>
    <forms:checkbox name="seederEnabled" checked="${torrentConfigurator.seederEnabled}"/><label for="seederEnabled"> enable torrent seeder</label>
    <c:if test="${torrentConfigurator.seederEnabled}">
    <ul style="margin-top:0; padding-left: 1em;">
      <li>seeded torrents: <strong>${seededTorrentsNum}</strong></li>
    </ul>
    </c:if>
  </td>
</tr>
<tr>
  <th><label for="maxNumberOfSeededTorrents">Maximum number of seeded torrents:</label></th>
  <td><forms:textField name="maxNumberOfSeededTorrents" style="width: 5em" value="${torrentConfigurator.maxNumberOfSeededTorrents}"/> (-1 - unlimited)</td>
</tr>
<tr>
  <th><label for="fileSizeThresholdMb">Artifact size threshold:</label></th>
  <td><forms:textField name="fileSizeThresholdMb" style="width: 5em" value="${torrentConfigurator.fileSizeThresholdMb}"/> Mb</td>
</tr>
<tr>
  <td colspan="2"><forms:submit label="Save" name="save"/></td>
</tr>
</table>
</form>
