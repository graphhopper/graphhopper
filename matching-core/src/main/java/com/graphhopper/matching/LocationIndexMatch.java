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
package com.graphhopper.matching;

import com.carrotsearch.hppc.procedures.IntProcedure;
import com.graphhopper.coll.GHBitSet;
import com.graphhopper.coll.GHIntHashSet;
import com.graphhopper.coll.GHTBitSet;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIteratorState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 *
 * @author Peter Karich
 */
public class LocationIndexMatch extends LocationIndexTree {

    private static final Comparator<QueryResult> QR_COMPARATOR = new Comparator<QueryResult>() {
        @Override
        public int compare(QueryResult o1, QueryResult o2) {
            return Double.compare(o1.getQueryDistance(), o2.getQueryDistance());
        }
    };

    private final LocationIndexTree index;

    public LocationIndexMatch(GraphHopperStorage graph, LocationIndexTree index) {
        super(graph, graph.getDirectory());
        this.index = index;
    }

    /**
     * Returns all edges that are within the specified radius around the queried position.
     * Searches at most 9 cells to avoid performance problems. Hence, if the radius is larger than
     * the cell width then not all edges might be returned.
     *
     * @param radius in meters
     */
    public List<QueryResult> findNClosest(final double queryLat, final double queryLon,
            final EdgeFilter edgeFilter, double radius) {
        // Return ALL results which are very close and e.g. within the GPS signal accuracy.
        // Also important to get all edges if GPS point is close to a junction.
        final double returnAllResultsWithin = distCalc.calcNormalizedDist(radius);

        // implement a cheap priority queue via List, sublist and Collections.sort
        final List<QueryResult> queryResults = new ArrayList<QueryResult>();
        GHIntHashSet set = new GHIntHashSet();

        // Doing 2 iterations means searching 9 tiles.
        for (int iteration = 0; iteration < 2; iteration++) {
            // should we use the return value of earlyFinish?
            index.findNetworkEntries(queryLat, queryLon, set, iteration);

            final GHBitSet exploredNodes = new GHTBitSet(new GHIntHashSet(set));
            final EdgeExplorer explorer = graph.createEdgeExplorer(edgeFilter);

            set.forEach(new IntProcedure() {

                @Override
                public void apply(int node) {
                    new XFirstSearchCheck(queryLat, queryLon, exploredNodes, edgeFilter) {
                        @Override
                        protected double getQueryDistance() {
                            // do not skip search if distance is 0 or near zero (equalNormedDelta)
                            return Double.MAX_VALUE;
                        }

                        @Override
                        protected boolean check(int node, double normedDist, int wayIndex, EdgeIteratorState edge, QueryResult.Position pos) {
                            if (normedDist < returnAllResultsWithin
                                    || queryResults.isEmpty()
                                    || queryResults.get(0).getQueryDistance() > normedDist) {

                                int index = -1;
                                for (int qrIndex = 0; qrIndex < queryResults.size(); qrIndex++) {
                                    QueryResult qr = queryResults.get(qrIndex);
                                    // overwrite older queryResults which are potentially more far away than returnAllResultsWithin
                                    if (qr.getQueryDistance() > returnAllResultsWithin) {
                                        index = qrIndex;
                                        break;
                                    }

                                    // avoid duplicate edges
                                    if (qr.getClosestEdge().getEdge() == edge.getEdge()) {
                                        if (qr.getQueryDistance() < normedDist) {
                                            // do not add current edge
                                            return true;
                                        } else {
                                            // overwrite old edge with current
                                            index = qrIndex;
                                            break;
                                        }
                                    }
                                }

                                QueryResult qr = new QueryResult(queryLat, queryLon);
                                qr.setQueryDistance(normedDist);
                                qr.setClosestNode(node);
                                qr.setClosestEdge(edge.detach(false));
                                qr.setWayIndex(wayIndex);
                                qr.setSnappedPosition(pos);

                                if (index < 0) {
                                    queryResults.add(qr);
                                } else {
                                    queryResults.set(index, qr);
                                }
                            }
                            return true;
                        }
                    }.start(explorer, node);                    
                }
            });
        }

        Collections.sort(queryResults, QR_COMPARATOR);

        for (QueryResult qr : queryResults) {
            if (qr.isValid()) {
                // denormalize distance
                qr.setQueryDistance(distCalc.calcDenormalizedDist(qr.getQueryDistance()));
                qr.calcSnappedPoint(distCalc);
            } else {
                throw new IllegalStateException("Invalid QueryResult should not happen here: " + qr);
            }
        }

        return queryResults;
    }
}
