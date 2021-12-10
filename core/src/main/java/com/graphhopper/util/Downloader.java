/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.util;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.LongConsumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * @author Peter Karich
 */
public class Downloader {
    private final String userAgent;
    private String referrer = "http://graphhopper.com";
    private String acceptEncoding = "gzip, deflate";
    private int timeout = 4000;

    public Downloader(String userAgent) {
        this.userAgent = userAgent;
    }

    public static void main(String[] args) throws IOException {
        new Downloader("GraphHopper Downloader").downloadAndUnzip("http://graphhopper.com/public/maps/0.1/europe_germany_berlin.ghz", "somefolder",
                val -> System.out.println("progress:" + val));
    }

    public Downloader setTimeout(int timeout) {
        this.timeout = timeout;
        return this;
    }

    public Downloader setReferrer(String referrer) {
        this.referrer = referrer;
        return this;
    }

    /**
     * This method initiates a connect call of the provided connection and returns the response
     * stream. It only returns the error stream if it is available and readErrorStreamNoException is
     * true otherwise it throws an IOException if an error happens. Furthermore it wraps the stream
     * to decompress it if the connection content encoding is specified.
     */
    public InputStream fetch(HttpURLConnection connection, boolean readErrorStreamNoException) throws IOException {
        // create connection but before reading get the correct inputstream based on the compression and if error
        connection.connect();

        InputStream is;
        if (readErrorStreamNoException && connection.getResponseCode() >= 400 && connection.getErrorStream() != null)
            is = connection.getErrorStream();
        else
            is = connection.getInputStream();

        if (is == null)
            throw new IOException("Stream is null. Message:" + connection.getResponseMessage());

        // wrap
        try {
            String encoding = connection.getContentEncoding();
            if (encoding != null && encoding.equalsIgnoreCase("gzip"))
                is = new GZIPInputStream(is);
            else if (encoding != null && encoding.equalsIgnoreCase("deflate"))
                is = new InflaterInputStream(is, new Inflater(true));
        } catch (IOException ex) {
        }

        return is;
    }

    public InputStream fetch(String url) throws IOException {
        return fetch(createConnection(url), false);
    }

    public HttpURLConnection createConnection(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        // Will yield in a POST request: conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setUseCaches(true);
        conn.setRequestProperty("Referrer", referrer);
        conn.setRequestProperty("User-Agent", userAgent);
        // suggest respond to be gzipped or deflated (which is just another compression)
        // http://stackoverflow.com/q/3932117
        conn.setRequestProperty("Accept-Encoding", acceptEncoding);
        conn.setReadTimeout(timeout);
        conn.setConnectTimeout(timeout);
        return conn;
    }

    public void downloadFile(String url, String toFile) throws IOException {
        HttpURLConnection conn = createConnection(url);
        InputStream iStream = fetch(conn, false);
        int size = 8 * 1024;
        BufferedOutputStream writer = new BufferedOutputStream(new FileOutputStream(toFile), size);
        InputStream in = new BufferedInputStream(iStream, size);
        try {
            byte[] buffer = new byte[size];
            int numRead;
            while ((numRead = in.read(buffer)) != -1) {
                writer.write(buffer, 0, numRead);
            }
        } finally {
            Helper.close(iStream);
            Helper.close(writer);
            Helper.close(in);
        }
    }

    public void downloadAndUnzip(String url, String toFolder, final LongConsumer progressListener) throws IOException {
        HttpURLConnection conn = createConnection(url);
        final int length = conn.getContentLength();
        InputStream iStream = fetch(conn, false);

        new Unzipper().unzip(iStream, new File(toFolder), sumBytes -> progressListener.accept((int) (100 * sumBytes / length)));
    }

    public String downloadAsString(String url, boolean readErrorStreamNoException) throws IOException {
        return Helper.isToString(fetch(createConnection(url), readErrorStreamNoException));
    }
}
