/*
 *  Copyright 2012 Peter Karich info@jetsli.de
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
package de.jetsli.graph.util;

import static java.lang.Math.*;
//import static org.apache.commons.math3.util.FastMath.*;

/**
 * Calculates the approximative distance of two points on earth.
 *
 * @author Peter Karich, info@jetsli.de
 */
public class ApproxCalcDistance extends CalcDistance {

    @Override
    public double calcDistKm(double fromLat, double fromLon, double toLat, double toLon) {
        double dLat = toRadians(toLat - fromLat);
        double dLon = toRadians(toLon - fromLon);
        double left = cos(toRadians((fromLat + toLat) / 2)) * dLon;
        double normedDist = dLat * dLat + left * left;
        return R * sqrt(normedDist);
    }

    @Override
    public double denormalizeDist(double normedDist) {
        return R * sqrt(normedDist);
    }

    @Override
    public double normalizeDist(double dist) {
        double tmp = dist / R;
        return tmp * tmp;
    }

    @Override
    public double calcNormalizedDist(double fromLat, double fromLon, double toLat, double toLon) {
        double dLat = toRadians(toLat - fromLat);
        double dLon = toRadians(toLon - fromLon);
        double left = cos(toRadians((fromLat + toLat) / 2)) * dLon;
        return dLat * dLat + left * left;
    }
}
