/*
 *  Licensed to Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  Peter Karich licenses this file to you under the Apache License, 
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

/**
 * Wrapper to simplify output of GraphHopper.
 *
 * @author Peter Karich
 */
public class GHResponse {

    private final PointList list;
    private double distance;
    private long time;
    private String debugInfo = "";

    public GHResponse(PointList list) {
        this.list = list;
    }

    public GHResponse distance(double distance) {
        this.distance = distance;
        return this;
    }

    public double distance() {
        return distance;
    }

    public GHResponse time(long time) {
        this.time = time;
        return this;
    }

    public long time() {
        return time;
    }

    public boolean found() {
        return !list.isEmpty();
    }

    public PointList points() {
        return list;
    }

    public String debugInfo() {
        return debugInfo;
    }

    public GHResponse debugInfo(String debugInfo) {
        this.debugInfo = debugInfo;
        return this;
    }

    @Override
    public String toString() {
        return "found:" + found() + ", nodes:" + list.size() + ": " + list.toString();
    }
}
