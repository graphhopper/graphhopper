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

import com.google.common.collect.ArrayListMultimap;
import com.graphhopper.routing.VirtualEdgeIteratorState;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PointList;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

final class GraphExplorer {

    private final EdgeExplorer edgeExplorer;
    private final PtFlagEncoder flagEncoder;
    private final GtfsStorage gtfsStorage;
    private final RealtimeFeed realtimeFeed;
    private final boolean reverse;
    private final PtTravelTimeWeighting weighting;
    private final PointList extraNodes;
    private final List<EdgeIteratorState> extraEdges = new ArrayList<>();
    private final ArrayListMultimap<Integer, VirtualEdgeIteratorState> extraEdgesBySource = ArrayListMultimap.create();
    private final ArrayListMultimap<Integer, VirtualEdgeIteratorState> extraEdgesByDestination = ArrayListMultimap.create();
    private final Graph graph;
    private final boolean walkOnly;


    GraphExplorer(Graph graph, PtTravelTimeWeighting weighting, PtFlagEncoder flagEncoder, GtfsStorage gtfsStorage, RealtimeFeed realtimeFeed, boolean reverse, PointList extraNodes, List<VirtualEdgeIteratorState> extraEdges, boolean walkOnly) {
        this.graph = graph;
        this.edgeExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(flagEncoder, reverse, !reverse));
        this.flagEncoder = flagEncoder;
        this.weighting = weighting;
        this.gtfsStorage = gtfsStorage;
        this.realtimeFeed = realtimeFeed;
        this.reverse = reverse;
        this.extraNodes = extraNodes;
        this.extraEdges.addAll(extraEdges);
        for (VirtualEdgeIteratorState extraEdge : extraEdges) {
            if (extraEdge == null) {
                throw new RuntimeException();
            }
            extraEdgesBySource.put(extraEdge.getBaseNode(), extraEdge);
            extraEdgesByDestination.put(extraEdge.getAdjNode(), new VirtualEdgeIteratorState(extraEdge.getOriginalTraversalKey(), extraEdge.getEdge(), extraEdge.getAdjNode(), extraEdge.getBaseNode(), extraEdge.getDistance(), extraEdge.getFlags(), extraEdge.getName(), extraEdge.fetchWayGeometry(3)));
        }
        this.walkOnly = walkOnly;
    }

    Stream<EdgeIteratorState> exploreEdgesAround(Label label) {
        final List<VirtualEdgeIteratorState> extraEdges = reverse ? extraEdgesByDestination.get(label.adjNode) : extraEdgesBySource.get(label.adjNode);
        return Stream.concat(
                label.adjNode < graph.getNodes() ? mainEdgesAround(label) : Stream.empty(),
                extraEdges.stream()).filter(new EdgeIteratorStatePredicate(label));
    }

    private Stream<EdgeIteratorState> mainEdgesAround(Label label) {
        return StreamSupport.stream(new Spliterators.AbstractSpliterator<EdgeIteratorState>(0, 0) {
            EdgeIterator edgeIterator = edgeExplorer.setBaseNode(label.adjNode);

            @Override
            public boolean tryAdvance(Consumer<? super EdgeIteratorState> action) {
                if (edgeIterator.next()) {
                    action.accept(edgeIterator);
                    return true;
                }
                return false;
            }


        }, false);
    }

    long calcTravelTimeMillis(EdgeIteratorState edge, long earliestStartTime) {
        GtfsStorage.EdgeType edgeType = flagEncoder.getEdgeType(edge.getFlags());
        switch (edgeType) {
            case HIGHWAY:
                return weighting.calcMillis(edge, false, -1);
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
                return flagEncoder.getTime(edge.getFlags()) * 1000;
        }
    }

    public boolean isBlocked(EdgeIteratorState edge) {
        return realtimeFeed.isBlocked(edge.getEdge());
    }

    public long getDelayFromBoardEdge(EdgeIteratorState edge, long currentTime) {
        return realtimeFeed.getDelayForBoardEdge(edge, Instant.ofEpochMilli(currentTime));
    }

    public long getDelayFromAlightEdge(EdgeIteratorState edge, long currentTime) {
        return realtimeFeed.getDelayForAlightEdge(edge, Instant.ofEpochMilli(currentTime));
    }

    private long waitingTime(EdgeIteratorState edge, long earliestStartTime) {
        return flagEncoder.getTime(edge.getFlags()) * 1000 - millisOnTravelDay(edge, earliestStartTime);
    }

    private int secondsOnTrafficDay(EdgeIteratorState edge, long instant) {
        final ZoneId zoneId = gtfsStorage.getTimeZones().get(flagEncoder.getValidityId(edge.getFlags())).zoneId;
        return Instant.ofEpochMilli(instant).atZone(zoneId).toLocalTime().toSecondOfDay();
    }

    private long millisOnTravelDay(EdgeIteratorState edge, long instant) {
        final ZoneId zoneId = gtfsStorage.getTimeZones().get(flagEncoder.getValidityId(edge.getFlags())).zoneId;
        return Instant.ofEpochMilli(instant).atZone(zoneId).toLocalTime().toNanoOfDay() / 1000000L;
    }

    private boolean isValidOn(EdgeIteratorState edge, long instant) {
        GtfsStorage.EdgeType edgeType = flagEncoder.getEdgeType(edge.getFlags());
        if (edgeType == GtfsStorage.EdgeType.BOARD || edgeType == GtfsStorage.EdgeType.ALIGHT) {
            final int validityId = flagEncoder.getValidityId(edge.getFlags());
            final GtfsStorage.Validity validity = gtfsStorage.getValidities().get(validityId);
            final int trafficDay = (int) ChronoUnit.DAYS.between(validity.start, Instant.ofEpochMilli(instant).atZone(validity.zoneId).toLocalDate());
            return trafficDay >= 0 && validity.validity.get(trafficDay);
        } else {
            return true;
        }
    }

    EdgeIteratorState getEdgeIteratorState(int edgeId, int adjNode) {
        if (edgeId == -1) {
            throw new RuntimeException();
        }
        return extraEdges.stream()
                .filter(edge -> edge.getEdge() == edgeId)
                .findFirst().orElseGet(() -> graph.getEdgeIteratorState(edgeId, adjNode));
    }

    NodeAccess getNodeAccess() {
        return graph.getNodeAccess();
    }

    public Graph getGraph() {
        return graph;
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
            final GtfsStorage.EdgeType edgeType = flagEncoder.getEdgeType(edgeIterator.getFlags());
            if (walkOnly && edgeType != GtfsStorage.EdgeType.HIGHWAY && edgeType != (reverse ? GtfsStorage.EdgeType.EXIT_PT : GtfsStorage.EdgeType.ENTER_PT)) {
                return false;
            }
            if (!isValidOn(edgeIterator, label.currentTime)) {
                return false;
            }
            if (edgeType == GtfsStorage.EdgeType.WAIT_ARRIVAL && !reverse) {
                return false;
            }
            if (edgeType == GtfsStorage.EdgeType.ENTER_TIME_EXPANDED_NETWORK && !reverse) {
                if (secondsOnTrafficDay(edgeIterator, label.currentTime) > flagEncoder.getTime(edgeIterator.getFlags())) {
                    return false;
                } else {
                    if (foundEnteredTimeExpandedNetworkEdge) {
                        return false;
                    } else {
                        foundEnteredTimeExpandedNetworkEdge = true;
                    }
                }
            } else if (edgeType == GtfsStorage.EdgeType.LEAVE_TIME_EXPANDED_NETWORK && reverse) {
                if (secondsOnTrafficDay(edgeIterator, label.currentTime) < flagEncoder.getTime(edgeIterator.getFlags())) {
                    return false;
                }
            }
            return true;
        }
    }
}
