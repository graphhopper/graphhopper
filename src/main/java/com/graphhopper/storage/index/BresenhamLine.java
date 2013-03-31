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
package com.graphhopper.storage.index;

import gnu.trove.set.hash.TLongHashSet;

/**
 * http://en.wikipedia.org/wiki/Bresenham%27s_line_algorithm or even better:
 * http://en.wikipedia.org/wiki/Xiaolin_Wu%27s_line_algorithm
 *
 * @author Peter Karich
 */
public class BresenhamLine {

    // http://en.wikipedia.org/wiki/Bresenham%27s_line_algorithm#Simplification
    public static void calcPoints(double lat1, double lon1, double lat2, double lon2,
            PointEmitter emitter, double deltaLat, double deltaLon) {
        boolean latIncreasing = lat1 < lat2;
        boolean lonIncreasing = lon1 < lon2;
        double dLat = Math.abs(lat2 - lat1) / deltaLat,
                sLat = latIncreasing ? deltaLat : -deltaLat;
        double dLon = Math.abs(lon2 - lon1) / deltaLon,
                sLon = lonIncreasing ? deltaLon : -deltaLon;
        double err = 2 * (dLon - dLat);

        while (true) {
            emitter.set(lat1, lon1);
            if ((!latIncreasing && lat1 <= lat2 || latIncreasing && lat1 >= lat2)
                    && (!lonIncreasing && lon1 <= lon2 || lonIncreasing && lon1 >= lon2))
                break;
            double tmpErr = err;
            if (tmpErr > -dLat) {
                err -= dLat;
                lon1 += sLon;
            }
            if (tmpErr < dLon) {
                err += dLon;
                lat1 += sLat;
            }
        }
    }
}
