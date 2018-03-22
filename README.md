

[![official JetBrains project](http://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)


 TeamCity Torrent plugin
 ===========================

 With this plugin, users and build agents can download TeamCity build artifacts faster,
 especially in a distributed environment.

## 1. Downloading binaries
 
 The latest build of the plugin is available on / can be downloaded from the public TeamCity server:
    * [for TeamCity 2017.x]( http://teamcity.jetbrains.com/repository/download/TeamCityPluginsByJetBrains_TorrentPlugin_TorrentPluginTeamcity20172Compatible/.lastPinned/torrent-plugin.zip)  

 ## 2. Building sources


 Run the following command in the root of the checked out repository:
 
    `mvn clean package`

 ## 3. Installing
 
 Install the plugin as described in the [TeamCity documentation](http://confluence.jetbrains.com/display/TCDL/Installing+Additional+Plugins).


## 4. Setting up the plugin

 Once you restart the server, a new link, Torrent Settings, will appear in the Administration area. The plugin is disabled by default. You can enable it on this page.
 If the plugin works correctly and you checked both options on the Torrent settings page, then once a large enough artifact is published, you should see the torrent icon near the name of the artifact.
 Clicking this icon should download .torrent file, which you can open it with your favorite torrent client. 
 
## 5. Tech notes

* server and agent bind on the first available port in the 6881-6889 interval, so these ports should be reachable.
* Torrent files are created only for large artifact (by default more then 10mb), which means small files cannot be downloaded via BitTorrent.
* Plugin supports the following configuration parameters (can be specified on the project or build configuration level):
  * teamcity.torrent.peer.download.enabled (true by default): this parameter controls usage of BitTorrent protocol for artifacts downloading on agents
  * teamcity.torrent.peer.seeding.enabled (true by default): this parameter controls seeding of artifacts from agents via  BitTorrent protocol
  * teamcity.torrent.seeder.minFileSize (10M by default): this parameter controls artifacts size threshold; smaller artifacts won't be downloaded via BitTorrent protocol
