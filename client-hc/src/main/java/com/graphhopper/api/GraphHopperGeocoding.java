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
import com.graphhopper.http.WebHelper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;

import java.util.concurrent.TimeUnit;

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
            Request okRequest = new Request.Builder().url(url).build();
            ResponseBody rspBody = getClientForRequest(request).newCall(okRequest).execute().body();
            return objectMapper.readValue(rspBody.bytes(), GHGeocodingResponse.class);
        } catch (Exception ex) {
            throw new RuntimeException("Problem performing geocoding for " + url + ": " + ex.getMessage(), ex);
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
            url += "&q=" + request.getQuery();
        }

        if (request.getPoint().isValid())
            url += "&point=" + request.getPoint().getLat() + "," + request.getPoint().getLon();

        url += "&limit=" + request.getLimit();
        url += "&locale=" + request.getLocale();
        url += "&provider=" + request.getProvider();

        if (!key.isEmpty()) {
            url += "&key=" + WebHelper.encodeURL(key);
        }

        return url;
    }

}
