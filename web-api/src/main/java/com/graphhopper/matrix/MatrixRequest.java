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
package com.graphhopper.matrix;

import com.graphhopper.util.shapes.GHPoint;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Validated local matrix input. Axis order and repeated coordinates are preserved. */
public final class MatrixRequest {
    private static final Set<String> ARRAYS = Set.of("times", "times_ms", "distances", "weights");
    private static final Set<String> ALGORITHMS = Set.of("auto", "reference", "flexible", "ch_experimental");

    private final String profile;
    private final List<GHPoint> origins;
    private final List<GHPoint> destinations;
    private final List<String> outArrays;
    private final boolean failFast;
    private final String algorithm;

    public MatrixRequest(String profile, List<GHPoint> origins, List<GHPoint> destinations,
                         List<String> outArrays, boolean failFast, String algorithm) {
        if (profile == null || profile.isBlank())
            throw invalid("'profile' must be a nonempty string");
        this.profile = profile;
        this.origins = copyAxis(origins, "from_points");
        this.destinations = copyAxis(destinations, "to_points");
        if (outArrays == null || outArrays.isEmpty())
            throw invalid("'out_arrays' must be a nonempty array");
        Set<String> seen = new HashSet<>();
        for (String array : outArrays) {
            if (array == null || !ARRAYS.contains(array) || !seen.add(array))
                throw invalid("'out_arrays' must contain unique values from times, times_ms, distances, weights");
        }
        this.outArrays = List.copyOf(outArrays);
        if (algorithm == null || !ALGORITHMS.contains(algorithm))
            throw invalid("'local.algorithm' must be auto, reference, flexible, or ch_experimental");
        this.algorithm = algorithm;
        this.failFast = failFast;
    }

    private static List<GHPoint> copyAxis(List<GHPoint> axis, String name) {
        if (axis == null || axis.isEmpty())
            throw invalid("'" + name + "' must be a nonempty coordinate array");
        List<GHPoint> copy = new ArrayList<>(axis.size());
        for (int index = 0; index < axis.size(); index++) {
            GHPoint point = axis.get(index);
            if (point == null || !Double.isFinite(point.lat) || !Double.isFinite(point.lon)
                    || point.lon < -180 || point.lon > 180 || point.lat < -90 || point.lat > 90)
                throw invalid("Invalid [longitude, latitude] coordinate in '" + name + "' at index " + index);
            copy.add(new GHPoint(point.lat, point.lon));
        }
        return List.copyOf(copy);
    }

    private static MatrixException invalid(String message) {
        return new MatrixException("INVALID_REQUEST", message);
    }

    public String getProfile() {
        return profile;
    }

    public List<GHPoint> getOrigins() {
        return copyAxis(origins, "from_points");
    }

    public List<GHPoint> getDestinations() {
        return copyAxis(destinations, "to_points");
    }

    public List<String> getOutArrays() {
        return outArrays;
    }

    public boolean isFailFast() {
        return failFast;
    }

    public String getAlgorithm() {
        return algorithm;
    }
}
