BS.Torrents = {
  trackTreeEvents: function() {
    var that = this;

    $j(document).on("bs.treeLoaded", function(event, elem, options) {
      if (options && options.artifact && options.buildId) {
        that._injectTorrentLinks(elem, options.buildId);
      }
    });

    $j(document).on("bs.subtreeLoaded", function(event, elem, options) {
      if (options && options.artifact && options.buildId) {
        that._injectTorrentLinks(elem, options.buildId);
      }
    });
  },

  _injectTorrentLinks: function(treeRoot, buildId) {
    // Exclude already handled elements.
    var elements = $j(treeRoot).find("span.c").not(".torrent-handled");
    var names = [];
    elements.each(function() {
      var link = $j(this).children("a");
      var href = link.attr("href");

      // Do not ask for files inside archives and directories.
      if (href != "#" && href.indexOf("!") == -1 && href.indexOf("%21") == -1) {
        names.push(link.text());
      }
    });
    if (names.length == 0) {
      return;
    }

    var icon = BS.Torrents.icon;
    BS.ajaxRequest(window['base_uri'] + "/torrentLinks.html", {
      parameters: { buildId: buildId, names: names.join("/") },
      method : "post",
      onComplete: function(transport) {
        var text = transport.responseText;
        if (text) {
          var filesWithTorrents = {};
          var list = text.split("/");
          for (var i = 0; i < list.length; ++i ) {
            filesWithTorrents[list[i]] = true;
          }

          elements.each(function() {
            var self = $j(this);
            var link = self.children("a");
            var name = link.text();
            if (filesWithTorrents[name]) {
              var href = link.attr("href");
              var repo = "/repository/download/";
              var idx0 = href.indexOf(repo) + repo.length;
              var idx1 = href.indexOf("/", idx0 + 1);
              var idx2 = href.indexOf("/", idx1 + 1);
              var url = href.substr(0, idx2 + 1) + ".teamcity/" + name + ".torrent";

              var img = $j("<img/>").attr({
                src: icon
              }).css({verticalAlign: "text-bottom"});
              var a = $j("<a/>").attr({
                href:url,
                title: "Download torrent file for this artifact"
              }).css({marginLeft: 10}).click(function(event) {
                BS.stopPropagation(event)
              });

              a.append(img).appendTo(self);
              self.addClass("has-torrent");
            }
            self.addClass("torrent-handled");
          });
        }
      }
    });
  }
};
