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
import com.graphhopper.routing.util.AccessFilter;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeExplorer;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class GraphExplorer {

    private final EdgeExplorer edgeExplorer;
    private final GtfsStorage gtfsStorage;
    private final RealtimeFeed realtimeFeed;
    private final boolean reverse;
    private final Weighting accessEgressWeighting;
    private final BooleanEncodedValue accessEnc;
    private final boolean walkOnly;
    private final boolean ptOnly;
    private final double walkSpeedKmH;
    private final boolean ignoreValidities;
    private final int blockedRouteTypes;
    private final Graph graph;
    private final PtGraph ptGraph;

    public GraphExplorer(Graph graph, PtGraph ptGraph, Weighting accessEgressWeighting, GtfsStorage gtfsStorage, RealtimeFeed realtimeFeed, boolean reverse, boolean walkOnly, boolean ptOnly, double walkSpeedKmh, boolean ignoreValidities, int blockedRouteTypes) {
        this.graph = graph;
        this.ptGraph = ptGraph;
        this.accessEgressWeighting = accessEgressWeighting;
        this.accessEnc = accessEgressWeighting.getFlagEncoder().getAccessEnc();
        this.ignoreValidities = ignoreValidities;
        this.blockedRouteTypes = blockedRouteTypes;
        AccessFilter accessEgressIn = AccessFilter.inEdges(accessEgressWeighting.getFlagEncoder().getAccessEnc());
        AccessFilter accessEgressOut = AccessFilter.outEdges(accessEgressWeighting.getFlagEncoder().getAccessEnc());
        this.edgeExplorer = graph.createEdgeExplorer(reverse ? accessEgressIn : accessEgressOut);
        this.gtfsStorage = gtfsStorage;
        this.realtimeFeed = realtimeFeed;
        this.reverse = reverse;
        this.walkOnly = walkOnly;
        this.ptOnly = ptOnly;
        this.walkSpeedKmH = walkSpeedKmh;
    }

    Stream<PtGraph.PtEdge> exploreEdgesAround(Label label) {
        System.out.println("start "+label.adjNode);
        return StreamSupport.stream(new Spliterators.AbstractSpliterator<PtGraph.PtEdge>(0, 0) {
            final Iterator<PtGraph.PtEdge> edgeIterator = reverse ?
                    ptGraph.backEdgesAround(label.adjNode).iterator() :
                    ptGraph.edgesAround(label.adjNode).iterator();

            @Override
            public boolean tryAdvance(Consumer<? super PtGraph.PtEdge> action) {
                while (edgeIterator.hasNext()) {
                    PtGraph.PtEdge edge = edgeIterator.next();
                    GtfsStorage.EdgeType edgeType = edge.getType();

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
                            action.accept(findEnterEdge(edge)); // fully consumes edgeIterator
                            return true;
                        }
                    }
//                    if (edgeType == GtfsStorage.EdgeType.HIGHWAY) {
//                        if (reverse ? edgeIterator.getReverse(accessEnc) : edgeIterator.get(accessEnc)) {
//                            action.accept(edgeIterator);
//                            return true;
//                        } else {
//                            continue;
//                        }
//                    }
                    if (walkOnly && edgeType != (reverse ? GtfsStorage.EdgeType.EXIT_PT : GtfsStorage.EdgeType.ENTER_PT)) {
                        continue;
                    }
                    if (!(ignoreValidities || isValidOn(edge, label.currentTime))) {
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
                    if ((edgeType == GtfsStorage.EdgeType.ENTER_PT || edgeType == GtfsStorage.EdgeType.EXIT_PT) && (blockedRouteTypes & (1 << edge.getAttrs().validityId)) != 0) {
                        continue;
                    }
                    if (edgeType == GtfsStorage.EdgeType.TRANSFER && routeTypeBlocked(edge)) {
                        continue;
                    }
                    if (edgeType == GtfsStorage.EdgeType.ENTER_PT && justExitedPt(label)) {
                        continue;
                    }
                    action.accept(edge);
                    return true;
                }
                return false;
            }

            private boolean routeTypeBlocked(PtGraph.PtEdge edge) {
                GtfsStorageI.PlatformDescriptor platformDescriptor = realtimeFeed.getPlatformDescriptorByEdge().get(edge.getId());
                int routeType = routeType(platformDescriptor);
                return (blockedRouteTypes & (1 << routeType)) != 0;
            }

            private int routeType(GtfsStorageI.PlatformDescriptor platformDescriptor) {
                if (platformDescriptor instanceof GtfsStorageI.RouteTypePlatform) {
                    return ((GtfsStorageI.RouteTypePlatform) platformDescriptor).route_type;
                } else {
                    return gtfsStorage.getGtfsFeeds().get(platformDescriptor.feed_id).routes.get(((GtfsStorageI.RoutePlatform) platformDescriptor).route_id).route_type;
                }
            }

            private boolean justExitedPt(Label label) {
                if (label.edge == -1)
                    return false;
                return ptGraph.getEdgeAttributes(label.edge).type == GtfsStorage.EdgeType.EXIT_PT;
            }

            private PtGraph.PtEdge findEnterEdge(PtGraph.PtEdge first) {
                long firstTT = calcTravelTimeMillis(first, label.currentTime);
                while (edgeIterator.hasNext()) {
                    PtGraph.PtEdge result = edgeIterator.next();
                    long nextTT = calcTravelTimeMillis(result, label.currentTime);
                    if (nextTT < firstTT) {
                        edgeIterator.forEachRemaining(ptEdge -> {});
                        return result;
                    }
                }
                return first;
            }

        }, false).peek(e -> System.out.println(e));
    }

    long calcTravelTimeMillis(PtGraph.PtEdge edge, long earliestStartTime) {
        switch (edge.getType()) {
//            case HIGHWAY:
//                return (long) (accessEgressWeighting.calcEdgeMillis(edge, reverse) * (5.0 / walkSpeedKmH));
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
                return edge.getTime() * 1000L;
        }
    }

    boolean isBlocked(PtGraph.PtEdge edge) {
        return realtimeFeed.isBlocked(edge.getId());
    }

    long getDelayFromBoardEdge(PtGraph.PtEdge edge, long currentTime) {
        return realtimeFeed.getDelayForBoardEdge(edge, Instant.ofEpochMilli(currentTime));
    }

    long getDelayFromAlightEdge(PtGraph.PtEdge edge, long currentTime) {
        return realtimeFeed.getDelayForAlightEdge(edge, Instant.ofEpochMilli(currentTime));
    }

    private long waitingTime(PtGraph.PtEdge edge, long earliestStartTime) {
        long l = edge.getTime() * 1000L - millisOnTravelDay(edge, earliestStartTime);
        if (!reverse) {
            if (l < 0) l = l + 24 * 60 * 60 * 1000;
        } else {
            if (l > 0) l = l - 24 * 60 * 60 * 1000;
        }
        return l;
    }

    private long millisOnTravelDay(PtGraph.PtEdge edge, long instant) {
        final ZoneId zoneId = gtfsStorage.getTimeZones().get(edge.getAttrs().validityId).zoneId;
        return Instant.ofEpochMilli(instant).atZone(zoneId).toLocalTime().toNanoOfDay() / 1000000L;
    }

    private boolean isValidOn(PtGraph.PtEdge edge, long instant) {
        if (edge.getType() == GtfsStorage.EdgeType.BOARD || edge.getType() == GtfsStorage.EdgeType.ALIGHT) {
            final GtfsStorage.Validity validity = realtimeFeed.getValidity(edge.getAttrs().validityId);
            final int trafficDay = (int) ChronoUnit.DAYS.between(validity.start, Instant.ofEpochMilli(instant).atZone(validity.zoneId).toLocalDate());
            return trafficDay >= 0 && validity.validity.get(trafficDay);
        } else {
            return true;
        }
    }

    int routeType(PtGraph.PtEdge edge) {
        GtfsStorage.EdgeType edgeType = edge.getType();
        if (edgeType == GtfsStorage.EdgeType.TRANSFER) {
            GtfsStorageI.PlatformDescriptor platformDescriptor = realtimeFeed.getPlatformDescriptorByEdge().get(edge.getId());
            if (platformDescriptor instanceof GtfsStorageI.RouteTypePlatform) {
                return ((GtfsStorageI.RouteTypePlatform) platformDescriptor).route_type;
            } else {
                return gtfsStorage.getGtfsFeeds().get(platformDescriptor.feed_id).routes.get(((GtfsStorageI.RoutePlatform) platformDescriptor).route_id).route_type;
            }
        } else if (edgeType == GtfsStorage.EdgeType.ENTER_PT || edgeType == GtfsStorage.EdgeType.EXIT_PT) {
            return edge.getAttrs().validityId;
        }
        throw new RuntimeException("Edge type "+edgeType+" doesn't encode route type.");
    }

}
