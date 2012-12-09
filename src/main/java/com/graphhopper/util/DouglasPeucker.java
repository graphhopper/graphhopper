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
package com.graphhopper.util;

import com.graphhopper.storage.Graph;
import gnu.trove.list.array.TIntArrayList;

/**
 * Simplyfies a list of points which are not too far away.
 * http://en.wikipedia.org/wiki/Ramer%E2%80%93Douglas%E2%80%93Peucker_algorithm
 *
 * @author Peter Karich
 */
public class DouglasPeucker {

    public static final int EMPTY = -1;
    private double normedMaxDist;
    private Graph g;
    private DistanceCalc calc;

    public DouglasPeucker(Graph g) {
        this.g = g;
        calc = new DistanceCalc();
        // 1m
        setMaxDist(1);
    }

    /**
     * distance in meter
     */
    public DouglasPeucker setMaxDist(double dist) {
        this.normedMaxDist = calc.normalizeDist(dist);
        return this;
    }

    /**
     * Points which should be removed will get the EMPTY value
     *
     * @return deleted nodes
     */
    public int simplify(TIntArrayList points) {
        return simplify(points, 0, points.size() - 1);
    }

    public int simplify(TIntArrayList points, int fromIndex, int lastIndex) {
        if (lastIndex - fromIndex < 2)
            return 0;
        int indexWithMaxDist = -1;
        double maxDist = -1;
        double firstLat = g.getLatitude(points.get(fromIndex));
        double firstLon = g.getLongitude(points.get(fromIndex));
        double lastLat = g.getLatitude(points.get(lastIndex));
        double lastLon = g.getLongitude(points.get(lastIndex));
        for (int i = fromIndex + 1; i < lastIndex; i++) {
            int tmpIndex = points.get(i);
            if (tmpIndex == EMPTY)
                continue;
            double lat = g.getLatitude(tmpIndex);
            double lon = g.getLongitude(tmpIndex);
            double dist = calc.calcNormalizedEdgeDistance(lat, lon, firstLat, firstLon, lastLat, lastLon);
            if (maxDist < dist) {
                indexWithMaxDist = i;
                maxDist = dist;
            }
        }

        if (indexWithMaxDist < 0)
            throw new IllegalStateException("maximum not found in [" + fromIndex + "," + lastIndex + "]");

        int counter = 0;
        if (maxDist < normedMaxDist) {
            for (int i = fromIndex + 1; i < lastIndex; i++) {
                points.set(i, EMPTY);
                counter++;
            }
        } else {
            counter = simplify(points, fromIndex, indexWithMaxDist);
            counter += simplify(points, indexWithMaxDist, lastIndex);
        }
        return counter;
    }
}
