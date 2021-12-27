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
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.Optional;
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

    Stream<MultiModalEdge> exploreEdgesAround(Label label) {
        return Stream.concat(label.node.ptNode != -1 ? ptEdgeStream(label.node.ptNode, label.currentTime) : Stream.empty(),
                label.node.streetNode != -1 ? streetEdgeStream(label.node.streetNode) : Stream.empty())
                .peek(e -> System.out.println(e));
    }

    private Stream<MultiModalEdge> ptEdgeStream(int ptNode, long currentTime) {
        return StreamSupport.stream(new Spliterators.AbstractSpliterator<MultiModalEdge>(0, 0) {
            final Iterator<PtGraph.PtEdge> edgeIterator = reverse ?
                    ptGraph.backEdgesAround(ptNode).iterator() :
                    ptGraph.edgesAround(ptNode).iterator();

            @Override
            public boolean tryAdvance(Consumer<? super MultiModalEdge> action) {
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
                            action.accept(new MultiModalEdge(findEnterEdge(edge))); // fully consumes edgeIterator
                            return true;
                        }
                    }
                    if (walkOnly && edgeType != (reverse ? GtfsStorage.EdgeType.EXIT_PT : GtfsStorage.EdgeType.ENTER_PT)) {
                        continue;
                    }
                    if (!(ignoreValidities || isValidOn(edge, currentTime))) {
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
                    action.accept(new MultiModalEdge(edge));
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

            private PtGraph.PtEdge findEnterEdge(PtGraph.PtEdge first) {
                long firstTT = calcTravelTimeMillis(first, currentTime);
                while (edgeIterator.hasNext()) {
                    PtGraph.PtEdge result = edgeIterator.next();
                    long nextTT = calcTravelTimeMillis(result, currentTime);
                    if (nextTT < firstTT) {
                        edgeIterator.forEachRemaining(ptEdge -> {
                        });
                        return result;
                    }
                }
                return first;
            }

        }, false);
    }

    private Stream<MultiModalEdge> streetEdgeStream(int streetNode) {
        return StreamSupport.stream(new Spliterators.AbstractSpliterator<EdgeIteratorState>(0, 0) {
            final EdgeIterator edgeIterator = edgeExplorer.setBaseNode(streetNode);

            @Override
            public boolean tryAdvance(Consumer<? super EdgeIteratorState> action) {
                while (edgeIterator.next()) {
                    if (reverse ? edgeIterator.getReverse(accessEnc) : edgeIterator.get(accessEnc)) {
                        action.accept(edgeIterator);
                        return true;
                    }
                }
                return false;
            }
        }, false)
                .map(e -> new MultiModalEdge(e.getEdge(), e.getBaseNode(), e.getAdjNode(), (long) (accessEgressWeighting.calcEdgeMillis(e.detach(false), reverse) * (5.0 / walkSpeedKmH)), e.getDistance()));
    }

    long calcTravelTimeMillis(MultiModalEdge edge, long earliestStartTime) {
        switch (edge.getType()) {
            case ENTER_TIME_EXPANDED_NETWORK:
                if (reverse) {
                    return 0;
                } else {
                    return waitingTime(edge.ptEdge, earliestStartTime);
                }
            case LEAVE_TIME_EXPANDED_NETWORK:
                if (reverse) {
                    return -waitingTime(edge.ptEdge, earliestStartTime);
                } else {
                    return 0;
                }
            default:
                return edge.getTime();
        }
    }

    long calcTravelTimeMillis(PtGraph.PtEdge edge, long earliestStartTime) {
        switch (edge.getType()) {
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
                return edge.getTime();
        }
    }

    public boolean isBlocked(MultiModalEdge edge) {
        return realtimeFeed.isBlocked(edge.getId());
    }

    long getDelayFromBoardEdge(MultiModalEdge edge, long currentTime) {
        return realtimeFeed.getDelayForBoardEdge(edge.ptEdge, Instant.ofEpochMilli(currentTime));
    }

    long getDelayFromAlightEdge(MultiModalEdge edge, long currentTime) {
        return realtimeFeed.getDelayForAlightEdge(edge.ptEdge, Instant.ofEpochMilli(currentTime));
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
        return edge.getRouteType();
    }

    public class MultiModalEdge {
        private int baseNode;
        private int adjNode;
        private long time;
        private double distance;
        private int edge;
        private PtGraph.PtEdge ptEdge;

        public MultiModalEdge(PtGraph.PtEdge ptEdge) {
            this.ptEdge = ptEdge;
        }

        public MultiModalEdge(int edge, int baseNode, int adjNode, long time, double distance) {
            this.edge = edge;
            this.baseNode = baseNode;
            this.adjNode = adjNode;
            this.time = time;
            this.distance = distance;
        }

        public GtfsStorage.EdgeType getType() {
            return ptEdge != null ? ptEdge.getType() : GtfsStorage.EdgeType.HIGHWAY;
        }

        public int getTransfers() {
            return ptEdge != null ? ptEdge.getAttrs().transfers : 0;
        }

        public int getId() {
            return ptEdge != null ? ptEdge.getId() : edge;
        }

        public Label.NodeId getAdjNode() {
            if (ptEdge != null) {
                int streetNode = Optional.ofNullable(gtfsStorage.getPtToStreet().get(ptEdge.getAdjNode())).orElse(-1);
                return new Label.NodeId(streetNode, ptEdge.getAdjNode());
            } else {
                int ptNode = Optional.ofNullable(gtfsStorage.getStreetToPt().get(adjNode)).orElse(-1);
                return new Label.NodeId(adjNode, ptNode);
            }
        }

        public long getTime() {
            return ptEdge != null ? ptEdge.getTime() * 1000L : time;
        }

        @Override
        public String toString() {
            return "MultiModalEdge{" + baseNode + "->" + adjNode +
                    ", time=" + time +
                    ", edge=" + edge +
                    ", ptEdge=" + ptEdge +
                    '}';
        }

        public double getDistance() {
            return distance;
        }

        public int getRouteType() {
            return ptEdge.getRouteType();
        }
    }
}
