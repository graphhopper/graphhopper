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
package com.graphhopper.util;

/**
 * This class implements a rather quick solution to calculate 3D distances on earth using euclidean
 * geometry mixed with Haversine formula used for the on earth distance. The haversine formula makes
 * not so much sense as it is only important for large distances where then the rather smallish
 * heights would becomes neglectable.
 * <p>
 *
 * @author Peter Karich
 */
public class DistanceCalc3D extends DistanceCalcEarth {
    /**
     * @param fromHeight in meters above 0
     * @param toHeight   in meters above 0
     */
    public double calcDist(double fromLat, double fromLon, double fromHeight,
                           double toLat, double toLon, double toHeight) {
        double len = super.calcDist(fromLat, fromLon, toLat, toLon);
        double delta = Math.abs(toHeight - fromHeight);
        return Math.sqrt(delta * delta + len * len);
    }

    @Override
    public String toString() {
        return "EXACT3D";
    }
}
