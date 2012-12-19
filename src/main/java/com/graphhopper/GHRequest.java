/*
 *  Copyright 2012 Peter Karich
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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

import com.graphhopper.util.shapes.GeoPoint;

/**
 * GraphHopper request wrapper to simplify requesting GraphHopper. From and to are required
 * parameters!
 *
 * @author Peter Karich
 */
public class GHRequest {

    private String algo = "astar";
    private GeoPoint from;
    private GeoPoint to;
    private double precision = 1;

    void check() {
        if (from == null)
            throw new IllegalStateException("the 'from' point needs to be initialized but was null");
        if (to == null)
            throw new IllegalStateException("the 'to' point needs to be initialized but was null");
    }

    /**
     * Possible values: astar (A* algorithm, default), astarbi (bidirectional A*) dijkstra
     * (Dijkstra), dijkstrabi and dijkstraNative (a bit faster bidirectional Dijkstra).
     */
    public GHRequest algorithm(String algo) {
        this.algo = algo;
        return this;
    }

    public String algorithm() {
        return algo;
    }

    /**
     * Required parameters for request: to calculate the path from specified startPoint to endPoint.
     */
    public GHRequest points(double fromLat, double fromLon, double toLat, double toLon) {
        return points(new GeoPoint(fromLat, fromLon), new GeoPoint(toLat, toLon));
    }

    public GHRequest points(GeoPoint from, GeoPoint to) {
        this.from = from;
        this.to = to;
        return this;
    }

    public GeoPoint from() {
        return from;
    }

    public GeoPoint to() {
        return to;
    }

    /**
     * Reduces the node count of the resulting path, default is 1. Useful for performance or if
     * you're using the web version for network latency. If a high value in meter is specified the
     * route will be less precise along the real networks.
     */
    public GHRequest minPathPrecision(double precision) {
        this.precision = precision;
        return this;
    }

    public double minPathPrecision() {
        return precision;
    }

    @Override
    public String toString() {
        return from + " " + to + " (" + algo + ")";
    }
}
