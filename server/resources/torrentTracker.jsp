<%@ include file="/include.jsp" %>
<jsp:useBean id="torrentConfigurator" type="jetbrains.buildServer.artifactsMirror.TorrentConfigurator" scope="request"/>
<jsp:useBean id="announcedTorrentsNum" type="java.lang.Integer" scope="request"/>
<jsp:useBean id="connectedClientsNum" type="java.lang.Integer" scope="request"/>

<div>
Torrent tracker <strong>${torrentConfigurator.trackerEnabled ? 'enabled' : 'disabled'}</strong>
<ul>
<li>announced torrents: <strong>${announcedTorrentsNum}</strong></li>
<li>announce URL: <strong>${torrentConfigurator.announceUrl}</strong></li>
</ul>
</div>
<div>
Torrent seeder <strong>${torrentConfigurator.seederEnabled ? 'enabled' : 'disabled'}</strong>
<ul>
<li>connected/downloading clients: <strong>${connectedClientsNum}</strong></li>
</ul>
</div>
<div>
Torrent files will be created for artifacts bigger than <strong>${torrentConfigurator.fileSizeThresholdMb}</strong> Mb.
</div>
