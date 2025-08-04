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

package com.graphhopper.http;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.net.URL;

public class FeedConfiguration {

    private URL url;
    private String agencyId;
    private String feedId;

    @JsonProperty
    public URL getUrl() {
        return url;
    }

    @JsonProperty
    public void setUrl(URL url) {
        this.url = url;
    }

    @JsonProperty
    public String getAgencyId() {
        return agencyId;
    }

    @JsonProperty
    public void setAgencyId(String agencyId) {
        this.agencyId = agencyId;
    }

    @JsonProperty
    public String getFeedId() {
        return feedId;
    }

    @JsonProperty
    public void setFeedId(String feedId) {
        this.feedId = feedId;
    }

}
