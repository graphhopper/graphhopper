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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopperAPI;
import com.graphhopper.PathWrapper;
import com.graphhopper.api.model.GHGeocodingRequest;
import com.graphhopper.api.model.GHGeocodingResponse;
import com.graphhopper.util.*;
import com.graphhopper.util.details.PathDetail;
import com.graphhopper.util.exceptions.*;
import com.graphhopper.util.shapes.GHPoint;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.graphhopper.util.Helper.round6;
import static com.graphhopper.util.Helper.toLowerCase;

/**
 * Client implementation for the GraphHopper Directions API Geocoding
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

    public GraphHopperGeocoding(String serviceUrl) {
        this.routeServiceUrl = serviceUrl;
        downloader = new OkHttpClient.Builder().
                connectTimeout(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS).
                readTimeout(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS).
                build();

        objectMapper = new ObjectMapper();
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

    private String buildUrl(GHGeocodingRequest request) {
        String url = routeServiceUrl + "?";

        if (request.isReverse()) {
            url += "reverse=true";
            url += "&point=" + request.getLat() + "," + request.getLon();
        } else {
            url += "reverse=false";
            url += "&q=" + request.getQuery();
            if (!Double.isNaN(request.getLat()))
                url += "&point=" + request.getLat() + "," + request.getLon();
        }

        url += "&limit=" + request.getLimit();
        url += "&locale=" + request.getLocale();
        url += "&provider=" + request.getProvider();

        if (!key.isEmpty()) {
            url += "&key=" + WebHelper.encodeURL(key);
        }

        return url;
    }

}
