<%@ include file="/include.jsp" %>
<jsp:useBean id="announcedTorrentsNum" type="java.lang.Integer" scope="request"/>
<jsp:useBean id="downloadingClientsNum" type="java.lang.Integer" scope="request"/>

<c:if test="${announcedTorrentsNum > 0 or downloadingClientsNum > 0}">
  <table class="runnerFormTable">
    <tr class="groupingTitle">
      <td colspan="2">
        Number of announced torrents: <strong>${announcedTorrentsNum}</strong>
      </td>
    </tr>

    <tr class="groupingTitle">
      <td colspan="2">
        Downloading clients: <strong>${downloadingClientsNum}</strong>
      </td>
    </tr>
<%--
    <c:if test="${not empty allClients}">
      <tr>
        <th>Seeded artifact</th>
        <th>Peers</th>
      </tr>

      <c:forEach items="${allClients}" var="client">
        &lt;%&ndash;@elvariable id="client" type="jetbrains.buildServer.artifactsMirror.torrent.TorrentSeeder.TorrentClient"&ndash;%&gt;
        <tr>
          <td>
            <c:out value="${client.torrent}"/>
          </td>
          <td>
            <c:set var="peers" value="${client.client.peers}"/>
            <c:choose>
              <c:when test="${empty peers}">0</c:when>
              <c:otherwise>
                ${fn:length(peers)} peer<bs:s val="${fn:length(peers)}"/>

                <c:set var="seeds" value="0"/>
                <c:forEach var="peer" items="${peers}"><c:set var="seeds" value="${peer.seed ? 1 : 0}"/></c:forEach>
                (${seeds} seed<bs:s val="${seeds}"/>)
              </c:otherwise>
            </c:choose>
          </td>
        </tr>
      </c:forEach>
    </c:if>
--%>
  </table>
</c:if>
