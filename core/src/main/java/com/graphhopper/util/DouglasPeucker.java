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
 * Simplifies a list of 2D points which are not too far away.
 * http://en.wikipedia.org/wiki/Ramer%E2%80%93Douglas%E2%80%93Peucker_algorithm
 * <p>
 * Calling simplify is thread safe.
 * <p>
 *
 * @author Peter Karich
 */
public class DouglasPeucker {
    private double normedMaxDist;
    private DistanceCalc calc;
    private boolean approx;

    public DouglasPeucker() {
        setApproximation(true);
        // 1m
        setMaxDistance(1);
    }

    public void setApproximation(boolean a) {
        approx = a;
        if (approx)
            calc = Helper.DIST_PLANE;
        else
            calc = Helper.DIST_EARTH;
    }

    /**
     * maximum distance of discrepancy (from the normal way) in meter
     */
    public DouglasPeucker setMaxDistance(double dist) {
        this.normedMaxDist = calc.calcNormalizedDist(dist);
        return this;
    }

    /**
     * Simplifies the <code>points</code>, from index 0 to size-1.
     * <p>
     * It is a wrapper method for {@link DouglasPeucker#simplify(PointList, int, int)}.
     *
     * @return The number removed points
     */
    public int simplify(PointList points) {
        return simplify(points, 0, points.size() - 1);
    }

    /**
     * Simplifies a part of the <code>points</code>. The <code>fromIndex</code> and <code>lastIndex</code>
     * are guaranteed to be kept.
     *
     * @param points    The PointList to simplify
     * @param fromIndex Start index to simplify, should be >= <code>lastIndex</code>
     * @param lastIndex Simplify up to this index
     * @return The number of removed points
     */
    public int simplify(PointList points, int fromIndex, int lastIndex) {
        int removed = 0;
        int size = lastIndex - fromIndex;
        if (approx) {
            int delta = 500;
            int segments = size / delta + 1;
            int start = fromIndex;
            for (int i = 0; i < segments; i++) {
                // start of next is end of last segment, except for the last
                removed += subSimplify(points, start, Math.min(lastIndex, start + delta));
                start += delta;
            }
        } else {
            removed = subSimplify(points, fromIndex, lastIndex);
        }

        if (removed > 0)
            compressNew(points, removed);

        return removed;
    }

    /**
     * compress list: move points into EMPTY slots
     */
    void compressNew(PointList points, int removed) {
        int freeIndex = -1;
        for (int currentIndex = 0; currentIndex < points.getSize(); currentIndex++) {
            if (Double.isNaN(points.getLatitude(currentIndex))) {
                if (freeIndex < 0)
                    freeIndex = currentIndex;

                continue;
            } else if (freeIndex < 0) {
                continue;
            }

            points.set(freeIndex, points.getLatitude(currentIndex), points.getLongitude(currentIndex), points.getElevation(currentIndex));
            points.set(currentIndex, Double.NaN, Double.NaN, Double.NaN);
            // find next free index
            int max = currentIndex;
            int searchIndex = freeIndex + 1;
            freeIndex = currentIndex;
            for (; searchIndex < max; searchIndex++) {
                if (Double.isNaN(points.getLatitude(searchIndex))) {
                    freeIndex = searchIndex;
                    break;
                }
            }
        }
        points.trimToSize(points.getSize() - removed);
    }

    // keep the points of fromIndex and lastIndex
    int subSimplify(PointList points, int fromIndex, int lastIndex) {
        if (lastIndex - fromIndex < 2) {
            return 0;
        }
        int indexWithMaxDist = -1;
        double maxDist = -1;
        double firstLat = points.getLatitude(fromIndex);
        double firstLon = points.getLongitude(fromIndex);
        double lastLat = points.getLatitude(lastIndex);
        double lastLon = points.getLongitude(lastIndex);
        for (int i = fromIndex + 1; i < lastIndex; i++) {
            double lat = points.getLatitude(i);
            if (Double.isNaN(lat)) {
                continue;
            }
            double lon = points.getLongitude(i);
            double dist = calc.calcNormalizedEdgeDistance(lat, lon, firstLat, firstLon, lastLat, lastLon);
            if (maxDist < dist) {
                indexWithMaxDist = i;
                maxDist = dist;
            }
        }

        if (indexWithMaxDist < 0) {
            throw new IllegalStateException("maximum not found in [" + fromIndex + "," + lastIndex + "]");
        }

        int counter = 0;
        if (maxDist < normedMaxDist) {
            for (int i = fromIndex + 1; i < lastIndex; i++) {
                points.set(i, Double.NaN, Double.NaN, Double.NaN);
                counter++;
            }
        } else {
            counter = subSimplify(points, fromIndex, indexWithMaxDist);
            counter += subSimplify(points, indexWithMaxDist, lastIndex);
        }
        return counter;
    }

}
