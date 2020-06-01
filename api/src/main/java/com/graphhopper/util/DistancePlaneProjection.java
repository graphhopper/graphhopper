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

import static java.lang.Math.sqrt;
import static java.lang.Math.toRadians;

/**
 * Calculates the approximate distance of two points on earth. Very good results if delat_lon is
 * not too big (see DistanceCalcTest), e.g. the distance is small.
 * <p>
 * http://en.wikipedia.org/wiki/Geographical_distance#Spherical_Earth_projected_to_a_plane
 * <p>
 * http://stackoverflow.com/q/1006654
 * <p>
 * http://en.wikipedia.org/wiki/Mercator_projection#Mathematics_of_the_Mercator_projection
 * http://gis.stackexchange.com/questions/4906/why-is-law-of-cosines-more-preferable-than-haversine-when-calculating-distance-b
 * <p>
 *
 * @author Peter Karich
 */
public class DistancePlaneProjection extends DistanceCalcEarth {
    @Override
    public double calcDist(double fromLat, double fromLon, double toLat, double toLon) {
        double normedDist = calcNormalizedDist(fromLat, fromLon, toLat, toLon);
        return R * sqrt(normedDist);
    }

    @Override
    public double calcDist3D(double fromLat, double fromLon, double fromHeight,
                             double toLat, double toLon, double toHeight) {
        double dEleNorm = hasElevationDiff(fromHeight, toHeight) ? calcNormalizedDist(toHeight - fromHeight) : 0;
        double normedDist = calcNormalizedDist(fromLat, fromLon, toLat, toLon);
        return R * sqrt(normedDist + dEleNorm);
    }

    @Override
    public double calcDenormalizedDist(double normedDist) {
        return R * sqrt(normedDist);
    }

    @Override
    public double calcNormalizedDist(double dist) {
        double tmp = dist / R;
        return tmp * tmp;
    }

    @Override
    public double calcNormalizedDist(double fromLat, double fromLon, double toLat, double toLon) {
        double dLat = toRadians(toLat - fromLat);
        double dLon = toRadians(toLon - fromLon);
//        double left = cos((float)toRadians((fromLat + toLat) / 2)) * dLon;
        double left = FastMath.cos((float) toRadians((fromLat + toLat) / 2)) * dLon;
        return dLat * dLat + left * left;
    }

    @Override
    public String toString() {
        return "PLANE_PROJ";
    }

    public static class FastMath {
        private static final double[] vals = new double[65536];

        public static double sin(float f) {
            return vals[(int) (f * 10430.378F) & '\uffff'];
        }

        public static double cos(float f) {
            return vals[(int) (f * 10430.378F + 16384.0F) & '\uffff'];
        }

        static {
            for (int i = 0; i < 65536; ++i) {
                vals[i] = Math.sin((double) i * 3.141592653589793D * 2.0D / 65536.0D);
            }
        }
    }
}
