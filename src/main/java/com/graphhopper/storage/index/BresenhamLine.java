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

/**
 * http://en.wikipedia.org/wiki/Bresenham%27s_line_algorithm or even better:
 * http://en.wikipedia.org/wiki/Xiaolin_Wu%27s_line_algorithm
 *
 * @author Peter Karich
 */
public class BresenhamLine {

    public static void calcPoints(double lat1, double lon1, double lat2, double lon2,
            PointEmitter emitter, double deltaLat, double deltaLon) {
        double dLat = Math.abs(lat2 - lat1) / deltaLat,
                sLat = lat1 < lat2 ? deltaLat : -deltaLat;
        double dLon = Math.abs(lon2 - lon1) / deltaLon,
                sLon = lon1 < lon2 ? deltaLon : -deltaLon;
        double err = (dLat > dLon ? dLat : -dLon) / 2;

        while (true) {
            emitter.set(lat1, lon1);

            if ((sLat < 0 && lat1 <= lat2 || sLat > 0 && lat1 >= lat2)
                    && (sLon < 0 && lon1 <= lon2 || sLon > 0 && lon1 >= lon2))
                break;
            double e2 = err;
            if (e2 > -dLat) {
                err -= dLon;
                lat1 += sLat;
            }
            if (e2 < dLon) {
                err += dLat;
                lon1 += sLon;
            }
        }
    }
}
