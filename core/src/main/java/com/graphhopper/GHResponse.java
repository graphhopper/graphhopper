/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper;

import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.BBox;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper to simplify output of GraphHopper.
 *
 * @author Peter Karich
 */
public class GHResponse {

    private PointList list;
    private double distance;
    private long time;
    private String debugInfo = "";
    private List<Throwable> errors = new ArrayList<Throwable>(4);

    public GHResponse() {
    }

    public GHResponse points(PointList points) {
        list = points;
        return this;
    }

    public GHResponse distance(double distance) {
        this.distance = distance;
        return this;
    }

    public double distance() {
        return distance;
    }

    public GHResponse time(long timeInSec) {
        this.time = timeInSec;
        return this;
    }

    /**
     * @return time in seconds
     */
    public long time() {
        return time;
    }

    public boolean found() {
        return list != null && !list.isEmpty();
    }

    public PointList points() {
        return list;
    }

    public BBox calcRouteBBox(BBox _fallback) {
        BBox bounds = BBox.INVERSE.clone();
        int len = list.size();
        if (len == 0)
            return _fallback;
        for (int i = 0; i < len; i++) {
            double lat = list.latitude(i);
            double lon = list.longitude(i);
            if (lat > bounds.maxLat)
                bounds.maxLat = lat;
            if (lat < bounds.minLat)
                bounds.minLat = lat;
            if (lon > bounds.maxLon)
                bounds.maxLon = lon;
            if (lon < bounds.minLon)
                bounds.minLon = lon;
        }
        return bounds;
    }

    public String debugInfo() {
        return debugInfo;
    }

    public GHResponse debugInfo(String debugInfo) {
        this.debugInfo = debugInfo;
        return this;
    }

    public boolean hasError() {
        return !errors.isEmpty();
    }

    public List<Throwable> errors() {
        return errors;
    }

    public GHResponse addError(Throwable error) {
        errors.add(error);
        return this;
    }

    @Override
    public String toString() {
        return "found:" + found() + ", nodes:" + list.size() + ": " + list.toString();
    }
}
