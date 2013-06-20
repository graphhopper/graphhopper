/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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
package com.graphhopper.util.shapes;

import com.graphhopper.util.Helper;

/**
 * Specifies a place by its coordinates, name and/or node id.
 *
 * @author Peter Karich
 */
public class GHPlace {

    public double lat = Double.NaN;
    public double lon = Double.NaN;
    private int nodeId = -1;
    private String name = "";

    public GHPlace() {
    }

    public GHPlace(String name) {
        name(name);
    }

    public GHPlace(int nodeId) {
        nodeId(nodeId);
    }

    public GHPlace(double lat, double lon) {
        this.lat = lat;
        this.lon = lon;
    }

    public GHPlace nodeId(int node) {
        this.nodeId = node;
        return this;
    }

    public int nodeId() {
        return nodeId;
    }

    public GHPlace name(String name) {
        this.name = name;
        return this;
    }

    public String name() {
        return name;
    }

    public boolean isValidNodeId() {
        return nodeId != -1;
    }

    public boolean isValidName() {
        return !Helper.isEmpty(name);
    }

    public boolean isValidPoint() {
        return lat != Double.NaN;
    }

    @Override
    public String toString() {
        String str = "";
        if (isValidName())
            str += name;
        if (isValidPoint())
            str += " " + lat + ", " + lon;
        if (isValidNodeId())
            str += " (" + nodeId + ")";
        return str.trim();
    }

    /**
     * Attention: geoJson is LON,LAT
     */
    public Double[] toGeoJson() {
        return new Double[]{lon, lat};
    }
}
