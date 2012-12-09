/*
 *  Copyright 2012 Peter Karich 
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
package com.graphhopper.storage;

import com.graphhopper.util.DistanceCosProjection;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.EdgeIterator;

/**
 * Same as full index but calculates distance to all edges too
 *
 * @author Peter Karich
 */
public class Location2IDFullWithEdgesIndex implements Location2IDIndex {

    private DistanceCalc calc = new DistanceCalc();
    private Graph g;

    public Location2IDFullWithEdgesIndex(Graph g) {
        this.g = g;
    }

    @Override
    public Location2IDIndex setPrecision(boolean approxDist) {
        if (approxDist)
            calc = new DistanceCosProjection();
        else
            calc = new DistanceCalc();
        return this;
    }

    @Override
    public Location2IDIndex prepareIndex(int capacity) {
        return this;
    }

    @Override public int findID(double queryLat, double queryLon) {
        int locs = g.getNodes();
        int id = -1;
        double foundDist = Double.MAX_VALUE;
        for (int fromNode = 0; fromNode < locs; fromNode++) {
            double fromLat = g.getLatitude(fromNode);
            double fromLon = g.getLongitude(fromNode);
            double fromDist = calc.calcDist(fromLat, fromLon, queryLat, queryLon);
            if (fromDist < 0)
                continue;

            if (fromDist < foundDist) {
                id = fromNode;
                foundDist = fromDist;
            }
            EdgeIterator iter = g.getOutgoing(fromNode);
            while (iter.next()) {
                int toNode = iter.node();
                double toLat = g.getLatitude(toNode);
                double toLon = g.getLongitude(toNode);

                if (calc.validEdgeDistance(queryLat, queryLon,
                        fromLat, fromLon, toLat, toLon)) {
                    double distEdge = calc.denormalizeDist(calc.calcNormalizedEdgeDistance(queryLat, queryLon,
                            fromLat, fromLon, toLat, toLon));
                    if (distEdge < foundDist) {
                        if (fromDist < calc.calcDist(toLat, toLon, queryLat, queryLon))
                            id = fromNode;
                        else
                            id = toNode;
                        foundDist = distEdge;
                    }
                }
            }
        }
        return id;
    }

    @Override
    public float calcMemInMB() {
        return 0;
    }
}
