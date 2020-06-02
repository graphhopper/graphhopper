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
        double left = FastMath.cos(toRadians((fromLat + toLat) / 2)) * dLon;
        return dLat * dLat + left * left;
    }

    @Override
    public String toString() {
        return "PLANE_PROJ";
    }

    public static class FastMath {
        private static final double PI2 = Math.PI * 0.5;

        public static double sin(double x) {
            double x2 = x * x;
            double x3 = x2 * x;
            double x5 = x2 * x3;
            double x7 = x2 * x5;
            double x9 = x2 * x7;
            double x11 = x2 * x9;
            double x13 = x2 * x11;
            double x15 = x2 * x13;
            double x17 = x2 * x15;

            double val = x;
            val -= x3 * 0.16666666666666666666666666666667;
            val += x5 * 0.00833333333333333333333333333333;
            val -= x7 * 1.984126984126984126984126984127e-4;
            val += x9 * 2.7557319223985890652557319223986e-6;
            val -= x11 * 2.5052108385441718775052108385442e-8;
            val += x13 * 1.6059043836821614599392377170155e-10;
            val -= x15 * 7.6471637318198164759011319857881e-13;
            val += x17 * 2.8114572543455207631989455830103e-15;
            return val;
        }

        public static double cos(double rad) {
            return sin(rad + PI2);
        }

    }
}
