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
package com.graphhopper.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Contains the results of a geocoding request.
 * This is a copy of: https://github.com/graphhopper/geocoder-converter/blob/master/src/main/java/com/graphhopper/converter/api/GHResponse.java
 *
 * @author Robin Boldt
 * @author Peter Karich
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GHGeocodingResponse {

    private List<String> copyrights = new ArrayList<>(5);
    private List<GHGeocodingEntry> hits;
    private String locale = "en";

    public GHGeocodingResponse() {
        this(5);
    }

    public GHGeocodingResponse(int no) {
        hits = new ArrayList<>(no);
    }

    public void setCopyrights(List<String> copyrights) {
        this.copyrights = copyrights;
    }

    public List<String> getCopyrights() {
        return copyrights;
    }

    public GHGeocodingResponse addCopyright(String cr) {
        copyrights.add(cr);
        return this;
    }

    public void setHits(List<GHGeocodingEntry> hits) {
        this.hits = hits;
    }

    public void add(GHGeocodingEntry entry) {
        hits.add(entry);
    }

    public List<GHGeocodingEntry> getHits() {
        return hits;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

}
