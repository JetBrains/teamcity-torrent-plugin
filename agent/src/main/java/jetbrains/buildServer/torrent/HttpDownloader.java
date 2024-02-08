

package jetbrains.buildServer.torrent;

import java.io.IOException;

public interface HttpDownloader {

    /**
     * Download content from specified url
     *
     * @param url resource url
     * @return content as byte array
     */
    byte[] download(String url) throws IOException;

}