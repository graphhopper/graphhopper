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

/**
 * Simplyfies a list of points which are not too far away.
 * http://en.wikipedia.org/wiki/Ramer%E2%80%93Douglas%E2%80%93Peucker_algorithm
 *
 * @author Peter Karich
 */
public class DouglasPeucker {

    private double normedMaxDist;
    private DistanceCalc calc;

    public DouglasPeucker() {
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
     * This method removes points which are close to the line (defined by
     * maxDist).
     *
     * @return deleted nodes
     */
    public int simplify(PointList points) {
        int deleted = simplify(points, 0, points.size() - 1);
        // compress list: move points into EMPTY slots
        int freeIndex = -1;
        for (int currentIndex = 0; currentIndex < points.size(); currentIndex++) {
            if (Double.isNaN(points.latitude(currentIndex))) {
                if (freeIndex < 0)
                    freeIndex = currentIndex;
                continue;
            }

            if (freeIndex < 0)
                continue;
            points.set(freeIndex, points.latitude(currentIndex), points.longitude(currentIndex));
            // find next free index
            int max = currentIndex;
            for (int searchIndex = freeIndex; searchIndex < max; searchIndex++) {
                if (Double.isNaN(points.latitude(searchIndex))) {
                    freeIndex = searchIndex;
                    break;
                }
            }
        }
        points.setSize(points.size() - deleted);
        return deleted;
    }

    int simplify(PointList points, int fromIndex, int lastIndex) {
        if (lastIndex - fromIndex < 2)
            return 0;
        int indexWithMaxDist = -1;
        double maxDist = -1;
        double firstLat = points.latitude(fromIndex);
        double firstLon = points.longitude(fromIndex);
        double lastLat = points.latitude(lastIndex);
        double lastLon = points.longitude(lastIndex);
        for (int i = fromIndex + 1; i < lastIndex; i++) {
            double lat = points.latitude(i);
            if (Double.isNaN(lat))
                continue;
            double lon = points.longitude(i);
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
                points.set(i, Double.NaN, Double.NaN);
                counter++;
            }
        } else {
            counter = simplify(points, fromIndex, indexWithMaxDist);
            counter += simplify(points, indexWithMaxDist, lastIndex);
        }
        return counter;
    }
}
