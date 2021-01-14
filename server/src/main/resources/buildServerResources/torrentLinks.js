/*
 * Copyright 2000-2021 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

(function($) {
  BS.Torrents = {
    trackTreeEvents: function() {
      var that = this;
      $(document).on("bs.treeLoaded", function(event, elem, options) {
        if (options && options.artifact && options.buildId) {
          that._injectTorrentLinks(elem, options.buildId);
        }
      });

      $(document).on("bs.subtreeLoaded", function(event, elem, options) {
        if (options && options.artifact && options.buildId) {
          that._injectTorrentLinks(elem, options.buildId);
        }
      });
    },

    _injectTorrentLinks: function(treeRoot, buildId) {
      var that = this;
      var elements = $(treeRoot).find("span.c").not(".torrent-handled");

      BS.ajaxRequest(window['base_uri'] + "/torrentLinks.html", {
        parameters: { buildId: buildId },
        method : "post",
        onComplete: function(transport) {
          var text = transport.responseText;
          if (text) {
            var filesWithTorrents = {};
            var list = text.split("\n");
            for (var i = 0; i < list.length; ++i ) {
              filesWithTorrents[list[i].trim()] = true;
            }

            elements.each(function() {
              var el = $(this);
              var link = el.children("a");
              var href = link.attr("href");
              if (href == '#') return;

              var repo = "/repository/download/";
              var idx0 = href.indexOf(repo) + repo.length;
              var idx1 = href.indexOf("/", idx0 + 1);
              var idx2 = href.indexOf("/", idx1 + 1);

              var path = href.substr(idx2+1);
              if (filesWithTorrents[path]
                      || filesWithTorrents[path.replace(/\+/g, ' ')]
                      || filesWithTorrents[decodeURIComponent(path)]) {
                var url = window['base_uri'] + "/downloadTorrent.html?buildId=" + buildId + "&file=" + path;

                var img = $('<img class="tree-torrent-icon"/>').attr({
                  src: that.icon
                });

                var a = $('<a class="tree-torrent-link"/>').attr({
                  href: url,
                  title: "Download torrent file for this artifact"
                }).click(function(event) {
                          BS.stopPropagation(event)
                        });

                a.append(img).appendTo(el);
                el.addClass("has-torrent");
              }
              el.addClass("torrent-handled");
            });
          }
        }
      });
    }
  };

})(jQuery);
