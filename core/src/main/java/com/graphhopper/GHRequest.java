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

import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EdgePropertyEncoder;
import com.graphhopper.routing.util.ShortestCalc;
import com.graphhopper.routing.util.WeightCalculation;
import com.graphhopper.util.shapes.GHPlace;
import java.util.HashMap;
import java.util.Map;

/**
 * GraphHopper request wrapper to simplify requesting GraphHopper.
 *
 * @author Peter Karich
 */
public class GHRequest {

    private String algo = "astar";
    private GHPlace from;
    private GHPlace to;
    private Map<String, Object> hints = new HashMap<String, Object>(5);
    private EdgePropertyEncoder encoder = new CarFlagEncoder();
    private WeightCalculation weightCalc = new ShortestCalc();

    /**
     * Calculate the path from specified startPoint (fromLat, fromLon) to
     * endPoint (toLat, toLon).
     */
    public GHRequest(double fromLat, double fromLon, double toLat, double toLon) {
        this(new GHPlace(fromLat, fromLon), new GHPlace(toLat, toLon));
    }

    /**
     * Calculate the path from specified startPoint to endPoint.
     */
    public GHRequest(GHPlace startPoint, GHPlace endPoint) {
        this.from = startPoint;
        this.to = endPoint;
    }

    public void check() {
        if (from == null)
            throw new IllegalStateException("the 'from' point needs to be initialized but was null");
        if (to == null)
            throw new IllegalStateException("the 'to' point needs to be initialized but was null");
    }

    /**
     * Possible values: astar (A* algorithm, default), astarbi (bidirectional
     * A*) dijkstra (Dijkstra), dijkstrabi and dijkstraNative (a bit faster
     * bidirectional Dijkstra).
     */
    public GHRequest algorithm(String algo) {
        this.algo = algo;
        return this;
    }

    public String algorithm() {
        return algo;
    }

    public GHPlace from() {
        return from;
    }

    public GHPlace to() {
        return to;
    }

    public GHRequest putHint(String key, Object value) {
        Object old = hints.put(key, value);
        if (old != null)
            throw new RuntimeException("Key is already associated with " + old + ", your value:" + value);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> T getHint(String key, T defaultValue) {
        Object obj = hints.get(key);
        if (obj == null)
            return defaultValue;
        return (T) obj;
    }

    @Override
    public String toString() {
        return from + " " + to + " (" + algo + ")";
    }

    public GHRequest type(WeightCalculation weightCalc) {
        this.weightCalc = weightCalc;
        return this;
    }

    public WeightCalculation type() {
        return weightCalc;
    }

    public GHRequest vehicle(EdgePropertyEncoder encoder) {
        this.encoder = encoder;
        return this;
    }

    public EdgePropertyEncoder vehicle() {
        return encoder;
    }
}
