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
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class GraphExplorer {

    private final EdgeExplorer edgeExplorer;
    private final PtEncodedValues flagEncoder;
    private final GtfsStorage gtfsStorage;
    private final RealtimeFeed realtimeFeed;
    private final boolean reverse;
    private final Weighting accessEgressWeighting;
    private final boolean walkOnly;
    private double walkSpeedKmH;
    private boolean ignoreValidities;

    public GraphExplorer(Graph graph, Weighting accessEgressWeighting, PtEncodedValues flagEncoder, GtfsStorage gtfsStorage, RealtimeFeed realtimeFeed, boolean reverse, boolean walkOnly, double walkSpeedKmh, boolean ignoreValidities) {
        this.accessEgressWeighting = accessEgressWeighting;
        this.ignoreValidities = ignoreValidities;
        DefaultEdgeFilter accessEgressIn = DefaultEdgeFilter.inEdges(accessEgressWeighting.getFlagEncoder());
        DefaultEdgeFilter accessEgressOut = DefaultEdgeFilter.outEdges(accessEgressWeighting.getFlagEncoder());
        DefaultEdgeFilter ptIn = DefaultEdgeFilter.inEdges(flagEncoder.getAccessEnc());
        DefaultEdgeFilter ptOut = DefaultEdgeFilter.outEdges(flagEncoder.getAccessEnc());
        EdgeFilter in = edgeState -> accessEgressIn.accept(edgeState) || ptIn.accept(edgeState);
        EdgeFilter out = edgeState -> accessEgressOut.accept(edgeState) || ptOut.accept(edgeState);
        this.edgeExplorer = graph.createEdgeExplorer(reverse ? in : out);
        this.flagEncoder = flagEncoder;
        this.gtfsStorage = gtfsStorage;
        this.realtimeFeed = realtimeFeed;
        this.reverse = reverse;
        this.walkOnly = walkOnly;
        this.walkSpeedKmH = walkSpeedKmh;
    }

    Stream<EdgeIteratorState> exploreEdgesAround(Label label) {
        return allEdgesAround(label).filter(new EdgeIteratorStatePredicate(label));
    }

    private Stream<EdgeIteratorState> allEdgesAround(Label label) {
        return StreamSupport.stream(new Spliterators.AbstractSpliterator<EdgeIteratorState>(0, 0) {
            EdgeIterator edgeIterator = edgeExplorer.setBaseNode(label.adjNode);

            @Override
            public boolean tryAdvance(Consumer<? super EdgeIteratorState> action) {
                if (edgeIterator.next()) {
                    GtfsStorage.EdgeType edgeType = edgeIterator.get(flagEncoder.getTypeEnc());

                    // Optimization (around 20% in Swiss network):
                    // Only use the (single) least-wait-time edge to enter the
                    // time expanded network. Later departures are reached via
                    // WAIT edges. Algorithmically not necessary, and does not
                    // reduce total number of relaxed nodes, but takes stress
                    // off the priority queue.
                    if (edgeType == GtfsStorage.EdgeType.ENTER_TIME_EXPANDED_NETWORK) {
                        action.accept(findEnterEdge()); // fully consumes edgeIterator
                        return true;
                    }

                    action.accept(edgeIterator);
                    return true;
                }
                return false;
            }

            private EdgeIteratorState findEnterEdge() {
                ArrayList<EdgeIteratorState> allEnterEdges = new ArrayList<>();
                allEnterEdges.add(edgeIterator.detach(false));
                while (edgeIterator.next()) {
                    allEnterEdges.add(edgeIterator.detach(false));
                }
                return allEnterEdges.stream().min(Comparator.comparingLong(e -> calcTravelTimeMillis(e, label.currentTime))).get();
            }

        }, false);
    }

    long calcTravelTimeMillis(EdgeIteratorState edge, long earliestStartTime) {
        GtfsStorage.EdgeType edgeType = edge.get(flagEncoder.getTypeEnc());
        switch (edgeType) {
            case HIGHWAY:
                // todo: why do we not account for turn times here ?
                return (long) (accessEgressWeighting.calcEdgeMillis(edge, reverse) * (5.0 / walkSpeedKmH));
            case ENTER_TIME_EXPANDED_NETWORK:
                if (reverse) {
                    return 0;
                } else {
                    return waitingTime(edge, earliestStartTime);
                }
            case LEAVE_TIME_EXPANDED_NETWORK:
                if (reverse) {
                    return -waitingTime(edge, earliestStartTime);
                } else {
                    return 0;
                }
            default:
                return edge.get(flagEncoder.getTimeEnc()) * 1000;
        }
    }

    boolean isBlocked(EdgeIteratorState edge) {
        return realtimeFeed.isBlocked(edge.getEdge());
    }

    long getDelayFromBoardEdge(EdgeIteratorState edge, long currentTime) {
        return realtimeFeed.getDelayForBoardEdge(edge, Instant.ofEpochMilli(currentTime));
    }

    long getDelayFromAlightEdge(EdgeIteratorState edge, long currentTime) {
        return realtimeFeed.getDelayForAlightEdge(edge, Instant.ofEpochMilli(currentTime));
    }

    private long waitingTime(EdgeIteratorState edge, long earliestStartTime) {
        long l = edge.get(flagEncoder.getTimeEnc()) * 1000 - millisOnTravelDay(edge, earliestStartTime);
        if (!reverse) {
            if (l < 0) l = l + 24 * 60 * 60 * 1000;
        } else {
            if (l > 0) l = l - 24 * 60 * 60 * 1000;
        }
        return l;
    }

    private long millisOnTravelDay(EdgeIteratorState edge, long instant) {
        final ZoneId zoneId = gtfsStorage.getTimeZones().get(edge.get(flagEncoder.getValidityIdEnc())).zoneId;
        return Instant.ofEpochMilli(instant).atZone(zoneId).toLocalTime().toNanoOfDay() / 1000000L;
    }

    private boolean isValidOn(EdgeIteratorState edge, long instant) {
        GtfsStorage.EdgeType edgeType = edge.get(flagEncoder.getTypeEnc());
        if (edgeType == GtfsStorage.EdgeType.BOARD || edgeType == GtfsStorage.EdgeType.ALIGHT) {
            final int validityId = edge.get(flagEncoder.getValidityIdEnc());
            final GtfsStorage.Validity validity = realtimeFeed.getValidity(validityId);
            final int trafficDay = (int) ChronoUnit.DAYS.between(validity.start, Instant.ofEpochMilli(instant).atZone(validity.zoneId).toLocalDate());
            return trafficDay >= 0 && validity.validity.get(trafficDay);
        } else {
            return true;
        }
    }

    int calcNTransfers(EdgeIteratorState edge) {
        return edge.get(flagEncoder.getTransfersEnc());
    }

    private class EdgeIteratorStatePredicate implements Predicate<EdgeIteratorState> {
        private final Label label;
        boolean foundEnteredTimeExpandedNetworkEdge;

        EdgeIteratorStatePredicate(Label label) {
            this.label = label;
            foundEnteredTimeExpandedNetworkEdge = false;
        }

        @Override
        public boolean test(EdgeIteratorState edgeIterator) {
            final GtfsStorage.EdgeType edgeType = edgeIterator.get(flagEncoder.getTypeEnc());
            if (edgeType == GtfsStorage.EdgeType.HIGHWAY) {
                if (reverse) {
                    return edgeIterator.getReverse(accessEgressWeighting.getFlagEncoder().getAccessEnc());
                } else {
                    return edgeIterator.get(accessEgressWeighting.getFlagEncoder().getAccessEnc());
                }
            }
            if (walkOnly && edgeType != (reverse ? GtfsStorage.EdgeType.EXIT_PT : GtfsStorage.EdgeType.ENTER_PT)) {
                return false;
            }
            if (!(ignoreValidities || isValidOn(edgeIterator, label.currentTime))) {
                return false;
            }
            if (edgeType == GtfsStorage.EdgeType.WAIT_ARRIVAL && !reverse) {
                return false;
            }
            return true;
        }
    }
}
