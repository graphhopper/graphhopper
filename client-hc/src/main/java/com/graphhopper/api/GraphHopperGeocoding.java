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
package com.graphhopper.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.api.model.GHGeocodingRequest;
import com.graphhopper.api.model.GHGeocodingResponse;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;

import static com.graphhopper.api.GraphHopperWeb.X_GH_CLIENT_VERSION;
import static com.graphhopper.api.Version.GH_VERSION_FROM_MAVEN;

/**
 * Client implementation for the GraphHopper Directions API Geocoding. For details on how to use it, please consult
 * the documentation at: https://graphhopper.com/api/1/docs/geocoding/.
 * <p>
 * Signup for a free API key at: https://graphhopper.com
 *
 * @author Robin Boldt
 */
public class GraphHopperGeocoding {

    private final ObjectMapper objectMapper;
    private OkHttpClient downloader;
    private String routeServiceUrl;
    private String key = "";

    private final long DEFAULT_TIMEOUT = 5000;

    public GraphHopperGeocoding() {
        this("https://graphhopper.com/api/1/geocode");
    }

    /**
     * This method allows you to point the client to a different URL than the default one.
     *
     * @param serviceUrl Geocoding endpoint that is compatible with the GraphHopper geocoding API
     */
    public GraphHopperGeocoding(String serviceUrl) {
        this.routeServiceUrl = serviceUrl;
        downloader = new OkHttpClient.Builder().
                connectTimeout(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS).
                readTimeout(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS).
                build();

        objectMapper = new ObjectMapper();
    }

    /**
     * Perform a geocoding request. Both forward and revers are possible, just configure the <code>request</code>
     * accordingly.
     *
     * @param request the request to send to the API
     * @return found results for your request
     */
    public GHGeocodingResponse geocode(GHGeocodingRequest request) {
        String url = buildUrl(request);
        try {
            Request okRequest = new Request.Builder().url(url)
                    .header(X_GH_CLIENT_VERSION, GH_VERSION_FROM_MAVEN)
                    .build();
            Response rsp = getClientForRequest(request).newCall(okRequest).execute();
            ResponseBody rspBody = rsp.body();
            if (!rsp.isSuccessful())
                throw new RuntimeException(rspBody.string());
            GHGeocodingResponse geoRsp = objectMapper.readValue(rspBody.bytes(), GHGeocodingResponse.class);
            return geoRsp;
        } catch (IOException ex) {
            throw new RuntimeException("IO problem for geocoding URL " + url + ": " + ex.getMessage(), ex);
        }
    }

    public GraphHopperGeocoding setDownloader(OkHttpClient downloader) {
        this.downloader = downloader;
        return this;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public OkHttpClient getDownloader() {
        return downloader;
    }

    private OkHttpClient getClientForRequest(GHGeocodingRequest request) {
        OkHttpClient client = this.downloader;
        if (request.hasTimeout()) {
            long timeout = request.getTimeout();
            client = client.newBuilder()
                    .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                    .readTimeout(timeout, TimeUnit.MILLISECONDS)
                    .build();
        }

        return client;
    }

    private String buildUrl(GHGeocodingRequest request) {
        String url = routeServiceUrl + "?";

        if (request.isReverse()) {
            if (!request.getPoint().isValid())
                throw new IllegalArgumentException("For reverse geocoding you have to pass valid lat and long values");
            url += "reverse=true";
        } else {
            if (request.getQuery() == null)
                throw new IllegalArgumentException("For forward geocoding you have to a string for the query");
            url += "reverse=false";
            url += "&q=" + encodeURL(request.getQuery());
        }

        if (request.getPoint().isValid())
            url += "&point=" + request.getPoint().getLat() + "," + request.getPoint().getLon();

        url += "&limit=" + request.getLimit();
        url += "&locale=" + encodeURL(request.getLocale());
        url += "&provider=" + encodeURL(request.getProvider());

        if (!key.isEmpty()) {
            url += "&key=" + encodeURL(key);
        }

        return url;
    }

    private static String encodeURL(String str) {
        try {
            return URLEncoder.encode(str, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

}
