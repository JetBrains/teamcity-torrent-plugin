package jetbrains.buildServer.torrent;

import com.intellij.openapi.util.io.StreamUtil;
import jetbrains.buildServer.util.FileUtil;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class HttpDownloaderImpl implements HttpDownloader {

    private final HttpClient myHttpClient;

    public HttpDownloaderImpl(HttpClient myHttpClient) {
        this.myHttpClient = myHttpClient;
    }

    @Override
    public byte[] download(String url) throws IOException {
        final HttpMethod getMethod = new GetMethod(url);
        InputStream in = null;
        try {
            myHttpClient.executeMethod(getMethod);
            if (getMethod.getStatusCode() != HttpStatus.SC_OK) {
                throw new IOException(String.format("Problem [%d] while downloading %s: %s", getMethod.getStatusCode(), url, getMethod.getStatusText()));
            }
            in = getMethod.getResponseBodyAsStream();
            ByteArrayOutputStream bOut = new ByteArrayOutputStream();
            StreamUtil.copyStreamContent(in, bOut);
            return bOut.toByteArray();
        } finally {
            FileUtil.close(in);
            getMethod.releaseConnection();
        }
    }
}
