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

import static java.nio.file.StandardOpenOption.*;

import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.*;
import java.nio.file.Files;
import java.time.Duration;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * @author Peter Karich
 */
public class Downloader {
    private static final int BUFFER_SIZE = 8 * 1024;
	private static final long DEFAULT_TIMEOUT = 4_000;

    private final HttpClient client;
	private final Duration timeout;

    private boolean requestCompressed = true;

	public Downloader() {
		this(DEFAULT_TIMEOUT);
	}

    public Downloader(long timeout) {
        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .proxy(ProxySelector.getDefault())
                .build();
		this.timeout = Duration.ofMillis(timeout);
    }

    public void setRequestCompressed(boolean requestCompressed) {
        this.requestCompressed = requestCompressed;
    }

    public void downloadFile(String url, File toFile) throws IOException {
        var requestBuilder = HttpRequest.newBuilder()
                .setHeader("User-Agent", "graphhopper/" + Constants.VERSION)
                .timeout(timeout)
                .uri(URI.create(url));
        if (requestCompressed) {
            requestBuilder.setHeader("Accept-Encoding", "gzip, deflate");
        }

        HttpResponse<InputStream> response;
        try {
            response = client.send(requestBuilder.build(), new UncompressHandler());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        if (response.statusCode() != 200) {
            throw new FileNotFoundException("Download of " + url + " failed, response code: " + response.statusCode());
        }

        try (var in = response.body(); var out = Files.newOutputStream(toFile.toPath(), CREATE, WRITE)) {
            in.transferTo(out);
        }
    }

    private static class UncompressHandler implements BodyHandler<InputStream> {
        @Override
        public BodySubscriber<InputStream> apply(ResponseInfo responseInfo) {
            if (responseInfo.statusCode() != 200) {
                return BodySubscribers.replacing(null);
            }
            String encoding = responseInfo.headers().firstValue("Content-Encoding")
                    .map(String::toLowerCase).orElse("");

            var subscriber = BodySubscribers.ofInputStream();
            return switch (encoding) {
                case "deflate" -> BodySubscribers.mapping(subscriber,
                        in -> new InflaterInputStream(in, new Inflater(true), BUFFER_SIZE));
                case "gzip" -> BodySubscribers.mapping(subscriber, in -> {
                    try {
                        return new GZIPInputStream(in, BUFFER_SIZE);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
                default -> subscriber;
            };
        }
    }
}
