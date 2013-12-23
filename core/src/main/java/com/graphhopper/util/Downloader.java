/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License,
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
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * @author Peter Karich
 */
public class Downloader {

    public static void main(String[] args) throws IOException {
        new Downloader("GraphHopper Downloader").downloadAndUnzip("http://graphhopper.com/public/maps/0.1/europe_germany_berlin.ghz", "somefolder",
                new ProgressListener() {
                    @Override
                    public void update(long val) {
                        System.out.println("progress:" + val);
                    }
                });
    }
    private String referrer = "http://graphhopper.com";
    private final String userAgent;
    private String acceptEncoding = "gzip, deflate";
    private int timeout = 4000;
    private int size = 1024 * 8;

    public Downloader(String userAgent) {
        this.userAgent = userAgent;
    }

    public Downloader setTimeout(int timeout) {
        this.timeout = timeout;
        return this;
    }

    public Downloader setReferrer(String referrer) {
        this.referrer = referrer;
        return this;
    }

    public InputStream fetch(HttpURLConnection conn) throws IOException {
        // create connection but before reading get the correct inputstream based on the compression
        conn.connect();
        String encoding = conn.getContentEncoding();
        InputStream is;
        if (encoding != null && encoding.equalsIgnoreCase("gzip"))
            is = new GZIPInputStream(conn.getInputStream());
        else if (encoding != null && encoding.equalsIgnoreCase("deflate"))
            is = new InflaterInputStream(conn.getInputStream(), new Inflater(true));
        else
            is = conn.getInputStream();

        return is;
    }

    public InputStream fetch(String url) throws IOException {
        return fetch((HttpURLConnection) createConnection(url));
    }

    public HttpURLConnection createConnection(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
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

    public void downloadAndUnzip(String url, String to, final ProgressListener progressListener) throws IOException {
        HttpURLConnection conn = createConnection(url);
        final int length = conn.getContentLength();
        InputStream iStream = fetch(conn);

        new Unzipper().setSize(size).unzip(iStream, new File(to), new ProgressListener() {
            @Override
            public void update(long sumBytes) {
                progressListener.update((int) (100 * sumBytes / length));
            }
        });
    }

    public String downloadAsString(String url) throws IOException {
        return readString(fetch(url));
    }

    private String readString(InputStream inputStream) throws IOException {
        String encoding = "UTF-8";
        InputStream in = new BufferedInputStream(inputStream, size);
        try {
            byte[] buffer = new byte[size];
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            int numRead;
            while ((numRead = in.read(buffer)) != -1) {
                output.write(buffer, 0, numRead);
            }
            return output.toString(encoding);
        } finally {
            in.close();
        }
    }
}
