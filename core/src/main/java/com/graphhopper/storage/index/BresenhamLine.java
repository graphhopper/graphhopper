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

    public static void bresenham(int y1, int x1, int y2, int x2, PointConsumer consumer) {
        boolean latIncreasing = y1 < y2;
        boolean lonIncreasing = x1 < x2;
        int dLat = Math.abs(y2 - y1), sLat = latIncreasing ? 1 : -1;
        int dLon = Math.abs(x2 - x1), sLon = lonIncreasing ? 1 : -1;
        int err = dLon - dLat;

        while (true) {
            consumer.set(y1, x1);
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
     * @author Peter Karich
     */
    public interface PointConsumer {
        void set(double lat, double lon);
    }
}
