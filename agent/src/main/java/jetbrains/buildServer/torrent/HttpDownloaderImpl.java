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
