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
package com.graphhopper.storage.index;

/**
 * We need the supercover line. The best algorithm is a 'voxel grid traversal algorithm' and
 * described in "A Fast Voxel Traversal Algorithm for Ray Tracing" by John Amanatides and Andrew Woo
 * (1987): http://www.cse.yorku.ca/~amana/research/grid.pdf
 * <p>
 * Other methods we used are Bresenham (only integer start and end values) and Xiaolin Wu (anti
 * aliasing). See some discussion here: http://stackoverflow.com/a/3234074/194609 and here
 * http://stackoverflow.com/q/24679963/194609
 * <p>
 *
 * @author Peter Karich
 */
public class BresenhamLine {
    public static void calcPoints(int y1, int x1, int y2, int x2, PointEmitter emitter) {
        bresenham(y1, x1, y2, x2, emitter);
    }

    public static void bresenham(int y1, int x1, int y2, int x2, PointEmitter emitter) {
        boolean latIncreasing = y1 < y2;
        boolean lonIncreasing = x1 < x2;
        int dLat = Math.abs(y2 - y1), sLat = latIncreasing ? 1 : -1;
        int dLon = Math.abs(x2 - x1), sLon = lonIncreasing ? 1 : -1;
        int err = dLon - dLat;

        while (true) {
            emitter.set(y1, x1);
            if (y1 == y2 && x1 == x2)
                break;

            int tmpErr = 2 * err;
            if (tmpErr > -dLat) {
                err -= dLat;
                x1 += sLon;
            }

            if (tmpErr < dLon) {
                err += dLon;
                y1 += sLat;
            }
        }
    }

    /**
     * Calls the Bresenham algorithm but make it working for double values
     */
    public static void calcPoints(final double lat1, final double lon1,
                                  final double lat2, final double lon2,
                                  final PointEmitter emitter,
                                  final double offsetLat, final double offsetLon,
                                  final double deltaLat, final double deltaLon) {
        // round to make results of bresenham closer to correct solution
        int y1 = (int) ((lat1 - offsetLat) / deltaLat);
        int x1 = (int) ((lon1 - offsetLon) / deltaLon);
        int y2 = (int) ((lat2 - offsetLat) / deltaLat);
        int x2 = (int) ((lon2 - offsetLon) / deltaLon);
        bresenham(y1, x1, y2, x2, new PointEmitter() {
            @Override
            public void set(double lat, double lon) {
                // +.1 to move more near the center of the tile
                emitter.set((lat + .1) * deltaLat + offsetLat, (lon + .1) * deltaLon + offsetLon);
            }
        });
    }
}
