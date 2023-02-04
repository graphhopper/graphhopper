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

import com.graphhopper.core.util.shapes.GHPoint;

/**
 * A geocoding request following https://graphhopper.com/api/1/docs/geocoding/
 *
 * @author Robin Boldt
 */
public class GHGeocodingRequest {

    private final boolean reverse;

    private final GHPoint point;

    private final String query;

    private final String locale;
    private final int limit;
    private final String provider;
    private final long timeout;

    /**
     * This is a wrapper to build a reverse geocoding request
     */
    public GHGeocodingRequest(double lat, double lon, String locale, int limit) {
        this(true, new GHPoint(lat, lon), null, locale, limit, "default", -1);
    }

    /**
     * This is a wrapper to build a reverse geocoding request
     */
    public GHGeocodingRequest(GHPoint point, String locale, int limit) {
        this(true, point, null, locale, limit, "default", -1);
    }

    /**
     * This is a wrapper to build a forward geocoding request
     */
    public GHGeocodingRequest(String query, String locale, int limit) {
        this(false, null, query, locale, limit, "default", -1);
    }

    public GHGeocodingRequest(boolean reverse, GHPoint point, String query, String locale, int limit, String provider, long timeout) {
        this.reverse = reverse;
        if (point == null) {
            this.point = new GHPoint();
        } else {
            this.point = point;
        }
        this.query = query;
        this.locale = locale;
        this.limit = limit;
        this.provider = provider;
        this.timeout = timeout;
    }

    public boolean isReverse() {
        return reverse;
    }

    public GHPoint getPoint() {
        return point;
    }

    public String getQuery() {
        return query;
    }

    public String getLocale() {
        return locale;
    }

    public int getLimit() {
        return limit;
    }

    public String getProvider() {
        return provider;
    }

    public boolean hasTimeout() {
        return this.timeout > 0;
    }

    public long getTimeout() {
        return timeout;
    }
}
