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

import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.Helper;

/**
 * Same as full index but calculates distance to all edges too
 * <p>
 *
 * @author Peter Karich
 */
public class Location2IDFullWithEdgesIndex implements LocationIndex {
    private final Graph graph;
    private final NodeAccess nodeAccess;
    private DistanceCalc calc = Helper.DIST_EARTH;
    private boolean closed = false;

    public Location2IDFullWithEdgesIndex(Graph g) {
        this.graph = g;
        this.nodeAccess = g.getNodeAccess();
    }

    @Override
    public boolean loadExisting() {
        return true;
    }

    @Override
    public LocationIndex setResolution(int resolution) {
        return this;
    }

    @Override
    public LocationIndex setApproximation(boolean approxDist) {
        if (approxDist) {
            calc = Helper.DIST_PLANE;
        } else {
            calc = Helper.DIST_EARTH;
        }
        return this;
    }

    @Override
    public LocationIndex prepareIndex() {
        return this;
    }

    @Override
    public QueryResult findClosest(double queryLat, double queryLon, EdgeFilter filter) {
        if (isClosed())
            throw new IllegalStateException("You need to create a new LocationIndex instance as it is already closed");

        QueryResult res = new QueryResult(queryLat, queryLon);
        double foundDist = Double.MAX_VALUE;
        AllEdgesIterator iter = graph.getAllEdges();
        while (iter.next()) {
            if (!filter.accept(iter)) {
                continue;
            }
            for (int i = 0, node; i < 2; i++) {
                if (i == 0) {
                    node = iter.getBaseNode();
                } else {
                    node = iter.getAdjNode();
                }

                double fromLat = nodeAccess.getLatitude(node);
                double fromLon = nodeAccess.getLongitude(node);
                double fromDist = calc.calcDist(fromLat, fromLon, queryLat, queryLon);
                if (fromDist < 0)
                    continue;

                if (fromDist < foundDist) {
                    res.setQueryDistance(fromDist);
                    res.setClosestEdge(iter.detach(false));
                    res.setClosestNode(node);
                    foundDist = fromDist;
                }

                // process the next stuff only for baseNode
                if (i > 0)
                    continue;

                int toNode = iter.getAdjNode();
                double toLat = nodeAccess.getLatitude(toNode);
                double toLon = nodeAccess.getLongitude(toNode);

                if (calc.validEdgeDistance(queryLat, queryLon,
                        fromLat, fromLon, toLat, toLon)) {
                    double distEdge = calc.calcDenormalizedDist(calc.calcNormalizedEdgeDistance(queryLat, queryLon,
                            fromLat, fromLon, toLat, toLon));
                    if (distEdge < foundDist) {
                        res.setQueryDistance(distEdge);
                        res.setClosestNode(node);
                        res.setClosestEdge(iter);
                        if (fromDist > calc.calcDist(toLat, toLon, queryLat, queryLon))
                            res.setClosestNode(toNode);

                        foundDist = distEdge;
                    }
                }
            }
        }
        return res;
    }

    @Override
    public LocationIndex create(long size) {
        return this;
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public long getCapacity() {
        return 0;
    }

    @Override
    public void setSegmentSize(int bytes) {
    }
}
