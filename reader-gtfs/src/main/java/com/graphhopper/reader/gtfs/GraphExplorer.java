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

package com.graphhopper.reader.gtfs;

import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;

import java.util.Iterator;

final class GraphExplorer {

    private final EdgeExplorer edgeExplorer;
    private final PtFlagEncoder flagEncoder;
    private final GtfsStorage gtfsStorage;
    private final RealtimeFeed realtimeFeed;
    private final boolean reverse;

    GraphExplorer(Graph graph, PtFlagEncoder flagEncoder, GtfsStorage gtfsStorage, RealtimeFeed realtimeFeed, boolean reverse) {
        this.edgeExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(flagEncoder, reverse, !reverse));
        this.flagEncoder = flagEncoder;
        this.gtfsStorage = gtfsStorage;
        this.realtimeFeed = realtimeFeed;
        this.reverse = reverse;
    }

    Iterable<EdgeIteratorState> exploreEdgesAround(Label label) {
        return new Iterable<EdgeIteratorState>() {
            EdgeIterator edgeIterator = edgeExplorer.setBaseNode(label.adjNode);

            @Override
            public Iterator<EdgeIteratorState> iterator() {
                return new Iterator<EdgeIteratorState>() {
                    boolean foundEnteredTimeExpandedNetworkEdge = false;

                    @Override
                    public boolean hasNext() {
                        while(edgeIterator.next()) {
                            final GtfsStorage.EdgeType edgeType = flagEncoder.getEdgeType(edgeIterator.getFlags());
                            final int trafficDay = (int) (label.currentTime / (24 * 60 * 60));
                            if (!isValidOn(edgeIterator, trafficDay)) {
                                continue;
                            }
                            if (realtimeFeed.isBlocked(edgeIterator.getEdge())) {
                                continue;
                            }
                            if (edgeType == GtfsStorage.EdgeType.ENTER_TIME_EXPANDED_NETWORK && !reverse) {
                                if ((int) (label.currentTime) % (24 * 60 * 60) > flagEncoder.getTime(edgeIterator.getFlags())) {
                                    continue;
                                } else {
                                    if (foundEnteredTimeExpandedNetworkEdge) {
                                        continue;
                                    } else {
                                        foundEnteredTimeExpandedNetworkEdge = true;
                                    }
                                }
                            } else if (edgeType == GtfsStorage.EdgeType.LEAVE_TIME_EXPANDED_NETWORK && reverse) {
                                if ((int) (label.currentTime) % (24 * 60 * 60) < flagEncoder.getTime(edgeIterator.getFlags())) {
                                    continue;
                                }
                            }
                            return true;
                        }
                        return false;
                    }

                    @Override
                    public EdgeIteratorState next() {
                        return edgeIterator;
                    }
                };
            }
        };
    }

    private boolean isValidOn(EdgeIteratorState edge, int trafficDay) {
        return gtfsStorage.getValidities().get(flagEncoder.getValidityId(edge.getFlags())).get(trafficDay);
    }

}
