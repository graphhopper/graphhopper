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

import com.graphhopper.util.shapes.GHPoint;

/**
 * GraphHopper request wrapper to simplify requesting GraphHopper.
 *
 * @author Peter Karich
 */
public class GHRequest {

    private String algo = "astar";
    private GHPoint from;
    private GHPoint to;
    private double precision = 1;

    /**
     * Calculate the path from specified startPoint (fromLat, fromLon) to endPoint (toLat, toLon).
     */
    public GHRequest(double fromLat, double fromLon, double toLat, double toLon) {
        this(new GHPoint(fromLat, fromLon), new GHPoint(toLat, toLon));
    }

    /**
     * Calculate the path from specified startPoint to endPoint.
     */
    public GHRequest(GHPoint startPoint, GHPoint endPoint) {
        this.from = startPoint;
        this.to = endPoint;
    }

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

    public GHPoint from() {
        return from;
    }

    public GHPoint to() {
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
