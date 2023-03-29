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

import com.conveyal.gtfs.GTFSFeed;
import com.google.common.collect.Iterators;
import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterators;
import java.util.function.Consumer;

public final class GraphExplorer {

    private final EdgeExplorer edgeExplorer;
    private final GtfsStorage gtfsStorage;
    private final RealtimeFeed realtimeFeed;
    private final boolean reverse;
    private final Weighting accessEgressWeighting;
    private final boolean walkOnly;
    private final boolean ptOnly;
    private final double walkSpeedKmH;
    private final boolean ignoreValidities;
    private final int blockedRouteTypes;
    private final PtGraph ptGraph;
    private final Graph graph;

    public GraphExplorer(Graph graph, PtGraph ptGraph, Weighting accessEgressWeighting, GtfsStorage gtfsStorage, RealtimeFeed realtimeFeed, boolean reverse, boolean walkOnly, boolean ptOnly, double walkSpeedKmh, boolean ignoreValidities, int blockedRouteTypes) {
        this.graph = graph;
        this.ptGraph = ptGraph;
        this.accessEgressWeighting = accessEgressWeighting;
        this.ignoreValidities = ignoreValidities;
        this.blockedRouteTypes = blockedRouteTypes;
        this.edgeExplorer = graph.createEdgeExplorer();
        this.gtfsStorage = gtfsStorage;
        this.realtimeFeed = realtimeFeed;
        this.reverse = reverse;
        this.walkOnly = walkOnly;
        this.ptOnly = ptOnly;
        this.walkSpeedKmH = walkSpeedKmh;
    }

    Iterable<MultiModalEdge> exploreEdgesAround(Label label) {
        return () -> {
            Iterator<MultiModalEdge> ptEdges = label.node.ptNode != -1 ? ptEdgeStream(label.node.ptNode, label.currentTime).iterator() : Collections.emptyIterator();
            Iterator<MultiModalEdge> streetEdges = label.node.streetNode != -1 ? streetEdgeStream(label.node.streetNode).iterator() : Collections.emptyIterator();
            return Iterators.concat(ptEdges, streetEdges);
        };
    }

    private Iterable<PtGraph.PtEdge> realtimeEdgesAround(int node) {
        return () -> realtimeFeed.getAdditionalEdges().stream().filter(e -> e.getBaseNode() == node).iterator();
    }

    private Iterable<PtGraph.PtEdge> backRealtimeEdgesAround(int node) {
        return () -> realtimeFeed.getAdditionalEdges().stream()
                .filter(e -> e.getAdjNode() == node)
                .map(e -> new PtGraph.PtEdge(e.getId(), e.getAdjNode(), e.getBaseNode(), e.getAttrs()))
                .iterator();
    }


    private Iterable<MultiModalEdge> ptEdgeStream(int ptNode, long currentTime) {
        return () -> Spliterators.iterator(new Spliterators.AbstractSpliterator<MultiModalEdge>(0, 0) {
            final Iterator<PtGraph.PtEdge> edgeIterator = reverse ?
                    Iterators.concat(ptNode < ptGraph.getNodeCount() ? ptGraph.backEdgesAround(ptNode).iterator() : Collections.<PtGraph.PtEdge>emptyIterator(), backRealtimeEdgesAround(ptNode).iterator()) :
                    Iterators.concat(ptNode < ptGraph.getNodeCount() ? ptGraph.edgesAround(ptNode).iterator() : Collections.<PtGraph.PtEdge>emptyIterator(), realtimeEdgesAround(ptNode).iterator());

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
                    if ((edgeType == GtfsStorage.EdgeType.ENTER_PT || edgeType == GtfsStorage.EdgeType.EXIT_PT || edgeType == GtfsStorage.EdgeType.TRANSFER) && (blockedRouteTypes & (1 << edge.getAttrs().route_type)) != 0) {
                        continue;
                    }
                    action.accept(new MultiModalEdge(edge));
                    return true;
                }
                return false;
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

        });
    }

    private Iterable<MultiModalEdge> streetEdgeStream(int streetNode) {
        return () -> Spliterators.iterator(new Spliterators.AbstractSpliterator<MultiModalEdge>(0, 0) {
            final EdgeIterator e = edgeExplorer.setBaseNode(streetNode);

            @Override
            public boolean tryAdvance(Consumer<? super MultiModalEdge> action) {
                while (e.next()) {
                    if (!accessEgressWeighting.edgeHasNoAccess(e, reverse)) {
                        action.accept(new MultiModalEdge(e.getEdge(), e.getBaseNode(), e.getAdjNode(), (long) (accessEgressWeighting.calcEdgeMillis(e.detach(false), reverse) * (5.0 / walkSpeedKmH)), e.getDistance()));
                        return true;
                    }
                }
                return false;
            }
        });
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
        final ZoneId zoneId = edge.getAttrs().feedIdWithTimezone.zoneId;
        return Instant.ofEpochMilli(instant).atZone(zoneId).toLocalTime().toNanoOfDay() / 1000000L;
    }

    private boolean isValidOn(PtGraph.PtEdge edge, long instant) {
        if (edge.getType() == GtfsStorage.EdgeType.BOARD || edge.getType() == GtfsStorage.EdgeType.ALIGHT) {
            final GtfsStorage.Validity validity = edge.getAttrs().validity;
            final int trafficDay = (int) ChronoUnit.DAYS.between(validity.start, Instant.ofEpochMilli(instant).atZone(validity.zoneId).toLocalDate());
            return trafficDay >= 0 && validity.validity.get(trafficDay);
        } else {
            return true;
        }
    }

    public List<Label.Transition> walkPath(int[] skippedEdgesForTransfer, long currentTime) {
        EdgeIteratorState firstEdge = graph.getEdgeIteratorStateForKey(skippedEdgesForTransfer[0]);
        Label label = new Label(currentTime, null, new Label.NodeId(firstEdge.getBaseNode(), -1), 0, null, 0, 0, 0, false, null);
        for (int i : skippedEdgesForTransfer) {
            EdgeIteratorState e = graph.getEdgeIteratorStateForKey(i);
            MultiModalEdge multiModalEdge = new MultiModalEdge(e.getEdge(), e.getBaseNode(), e.getAdjNode(), (long) (accessEgressWeighting.calcEdgeMillis(e, reverse) * (5.0 / walkSpeedKmH)), e.getDistance());
            label = new Label(label.currentTime + multiModalEdge.time, multiModalEdge, new Label.NodeId(e.getAdjNode(), -1), 0, null, 0, 0, 0, false, label);
        }
        return Label.getTransitions(label, false);
    }

    public String getPatternId(GtfsRealtime.TripDescriptor tripDescriptor) {
        GTFSFeed.StopTimesForTripWithTripPatternKey s = gtfsStorage.getGtfsFeeds().get("gtfs_0").stopTimes.getUnchecked(tripDescriptor.getTripId());
        return s.pattern.pattern_id;
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
                return new Label.NodeId(gtfsStorage.getPtToStreet().getOrDefault(ptEdge.getAdjNode(), -1), ptEdge.getAdjNode());
            } else {
                return new Label.NodeId(adjNode, gtfsStorage.getStreetToPt().getOrDefault(adjNode, -1));
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

        public int getStopSequence() {
            return ptEdge.getAttrs().stop_sequence;
        }

        public GtfsRealtime.TripDescriptor getTripDescriptor() {
            return ptEdge.getAttrs().tripDescriptor;
        }

        public GtfsStorage.PlatformDescriptor getPlatformDescriptor() {
            return ptEdge.getAttrs().platformDescriptor;
        }
    }
}
