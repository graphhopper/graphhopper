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

package com.graphhopper.gtfs.dropwizard;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.transit.realtime.GtfsRealtime;
import io.dropwizard.client.HttpClientConfiguration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class RealtimeConfiguration {

    @Valid
    @NotNull
    @JsonProperty
    private HttpClientConfiguration httpClient = new HttpClientConfiguration();

    @JsonProperty
    private List<FeedConfiguration> feeds = new ArrayList<>();

    public List<FeedConfiguration> getFeeds() {
        return feeds;
    }

    public HttpClientConfiguration getHttpClientConfiguration() {
        return httpClient;
    }
}
