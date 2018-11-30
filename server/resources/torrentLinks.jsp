<%@ include file="/include.jsp" %>
<script type="text/javascript">
  if (ReactUI && ReactUI.storeUrlExtensions) {
    ReactUI.storeUrlExtensions({kind: 'artifacts', name: 'torrent', endpoint: 'newTorrentLinks.html'});
  }
  BS.Torrents.icon = window['base_uri'] + "${teamcityPluginResourcesPath}torrent.png";
  BS.Torrents.trackTreeEvents();
</script>
