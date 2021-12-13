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

package com.graphhopper.gtfs;

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.IntEncodedValue;
import com.graphhopper.routing.util.AccessFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class GraphExplorer {

    private final EdgeExplorer edgeExplorer;
    private final PtEncodedValues flagEncoder;
    private final EnumEncodedValue<GtfsStorage.EdgeType> typeEnc;
    private final GtfsStorage gtfsStorage;
    private final RealtimeFeed realtimeFeed;
    private final boolean reverse;
    private final Weighting accessEgressWeighting;
    private final BooleanEncodedValue accessEnc;
    private final boolean walkOnly;
    private final boolean ptOnly;
    private final double walkSpeedKmH;
    private final boolean ignoreValidities;
    private final IntEncodedValue validityEnc;
    private final int blockedRouteTypes;
    private final Graph graph;

    public GraphExplorer(Graph graph, Weighting accessEgressWeighting, PtEncodedValues flagEncoder, GtfsStorage gtfsStorage, RealtimeFeed realtimeFeed, boolean reverse, boolean walkOnly, boolean ptOnly, double walkSpeedKmh, boolean ignoreValidities, int blockedRouteTypes) {
        this.graph = graph;
        this.accessEgressWeighting = accessEgressWeighting;
        this.accessEnc = accessEgressWeighting.getFlagEncoder().getAccessEnc();
        this.ignoreValidities = ignoreValidities;
        this.blockedRouteTypes = blockedRouteTypes;
        AccessFilter accessEgressIn = AccessFilter.inEdges(accessEgressWeighting.getFlagEncoder().getAccessEnc());
        AccessFilter accessEgressOut = AccessFilter.outEdges(accessEgressWeighting.getFlagEncoder().getAccessEnc());
        AccessFilter ptIn = AccessFilter.inEdges(flagEncoder.getAccessEnc());
        AccessFilter ptOut = AccessFilter.outEdges(flagEncoder.getAccessEnc());
        EdgeFilter in = edgeState -> accessEgressIn.accept(edgeState) || ptIn.accept(edgeState);
        EdgeFilter out = edgeState -> accessEgressOut.accept(edgeState) || ptOut.accept(edgeState);
        this.edgeExplorer = graph.createEdgeExplorer(reverse ? in : out);
        this.flagEncoder = flagEncoder;
        this.typeEnc = flagEncoder.getTypeEnc();
        this.validityEnc = flagEncoder.getValidityIdEnc();
        this.gtfsStorage = gtfsStorage;
        this.realtimeFeed = realtimeFeed;
        this.reverse = reverse;
        this.walkOnly = walkOnly;
        this.ptOnly = ptOnly;
        this.walkSpeedKmH = walkSpeedKmh;
    }

    Stream<EdgeIteratorState> exploreEdgesAround(Label label) {
        return StreamSupport.stream(new Spliterators.AbstractSpliterator<EdgeIteratorState>(0, 0) {
            final EdgeIterator edgeIterator = edgeExplorer.setBaseNode(label.adjNode);

            @Override
            public boolean tryAdvance(Consumer<? super EdgeIteratorState> action) {
                while (edgeIterator.next()) {
                    GtfsStorage.EdgeType edgeType = edgeIterator.get(typeEnc);

                    // Optimization (around 20% in Swiss network):
                    // Only use the (single) least-wait-time edge to enter the
                    // time expanded network. Later departures are reached via
                    // WAIT edges. Algorithmically not necessary, and does not
                    // reduce total number of relaxed nodes, but takes stress
                    // off the priority queue. Additionally, when only walking,
                    // don't bother finding the enterEdge, because we are not going to enter.
                    if (edgeType == GtfsStorage.EdgeType.ENTER_TIME_EXPANDED_NETWORK) {
                        if (walkOnly) {
                            return false;
                        } else {
                            action.accept(findEnterEdge()); // fully consumes edgeIterator
                            return true;
                        }
                    }
                    if (edgeType == GtfsStorage.EdgeType.HIGHWAY) {
                        if (reverse ? edgeIterator.getReverse(accessEnc) : edgeIterator.get(accessEnc)) {
                            action.accept(edgeIterator);
                            return true;
                        } else {
                            continue;
                        }
                    }
                    if (walkOnly && edgeType != (reverse ? GtfsStorage.EdgeType.EXIT_PT : GtfsStorage.EdgeType.ENTER_PT)) {
                        continue;
                    }
                    if (!(ignoreValidities || isValidOn(edgeIterator, label.currentTime))) {
                        continue;
                    }
                    if (edgeType == GtfsStorage.EdgeType.WAIT_ARRIVAL && !reverse) {
                        continue;
                    }
                    if (edgeType == GtfsStorage.EdgeType.ENTER_PT && reverse && ptOnly) {
                        continue;
                    }
                    if (edgeType == GtfsStorage.EdgeType.EXIT_PT && !reverse && ptOnly) {
                        continue;
                    }
                    if ((edgeType == GtfsStorage.EdgeType.ENTER_PT || edgeType == GtfsStorage.EdgeType.EXIT_PT || edgeType == GtfsStorage.EdgeType.TRANSFER) && (blockedRouteTypes & (1 << edgeIterator.get(validityEnc))) != 0) {
                        continue;
                    }
                    if (edgeType == GtfsStorage.EdgeType.ENTER_PT && justExitedPt(label)) {
                        continue;
                    }
                    action.accept(edgeIterator);
                    return true;
                }
                return false;
            }

            private boolean justExitedPt(Label label) {
                if (label.edge == -1)
                    return false;
                EdgeIteratorState edgeIteratorState = graph.getEdgeIteratorState(label.edge, label.adjNode);
                GtfsStorage.EdgeType edgeType = edgeIteratorState.get(typeEnc);
                return edgeType == GtfsStorage.EdgeType.EXIT_PT;
            }

            private EdgeIteratorState findEnterEdge() {
                EdgeIteratorState first = edgeIterator.detach(false);
                long firstTT = calcTravelTimeMillis(edgeIterator, label.currentTime);
                while (edgeIterator.next()) {
                    long nextTT = calcTravelTimeMillis(edgeIterator, label.currentTime);
                    if (nextTT < firstTT) {
                        EdgeIteratorState result = edgeIterator.detach(false);
                        while (edgeIterator.next());
                        return result;
                    }
                }
                return first;
            }

        }, false);
    }

    long calcTravelTimeMillis(EdgeIteratorState edge, long earliestStartTime) {
        switch (edge.get(typeEnc)) {
            case HIGHWAY:
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
        GtfsStorage.EdgeType edgeType = edge.get(typeEnc);
        if (edgeType == GtfsStorage.EdgeType.BOARD || edgeType == GtfsStorage.EdgeType.ALIGHT) {
            final int validityId = edge.get(validityEnc);
            final GtfsStorage.Validity validity = realtimeFeed.getValidity(validityId);
            final int trafficDay = (int) ChronoUnit.DAYS.between(validity.start, Instant.ofEpochMilli(instant).atZone(validity.zoneId).toLocalDate());
            return trafficDay >= 0 && validity.validity.get(trafficDay);
        } else {
            return true;
        }
    }

    int nTransfers(EdgeIteratorState edge) {
        return edge.get(flagEncoder.getTransfersEnc());
    }

    int routeType(EdgeIteratorState edge) {
        GtfsStorage.EdgeType edgeType = edge.get(typeEnc);
        if ((edgeType == GtfsStorage.EdgeType.ENTER_PT || edgeType == GtfsStorage.EdgeType.EXIT_PT || edgeType == GtfsStorage.EdgeType.TRANSFER)) {
            return edge.get(validityEnc);
        }
        throw new RuntimeException("Edge type "+edgeType+" doesn't encode route type.");
    }

}
