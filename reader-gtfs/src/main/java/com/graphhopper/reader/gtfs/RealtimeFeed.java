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

import com.carrotsearch.hppc.IntHashSet;
import com.conveyal.gtfs.model.StopTime;
import com.conveyal.gtfs.model.Trip;
import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphExtension;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.shapes.BBox;

import java.time.LocalDate;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.SKIPPED;

public class RealtimeFeed {
    private final IntHashSet blockedEdges;

    private RealtimeFeed(IntHashSet blockedEdges) {
        this.blockedEdges = blockedEdges;
    }

    public static RealtimeFeed empty() {
        return new RealtimeFeed(new IntHashSet());
    }

    public static RealtimeFeed fromProtobuf(GtfsStorage staticGtfs, GtfsRealtime.FeedMessage feedMessage) {
        final IntHashSet blockedEdges = new IntHashSet();
        feedMessage.getEntityList().stream()
            .filter(GtfsRealtime.FeedEntity::hasTripUpdate)
            .map(GtfsRealtime.FeedEntity::getTripUpdate)
            .forEach(tripUpdate -> {
                final int[] boardEdges = staticGtfs.getBoardEdgesForTrip().get(tripUpdate.getTrip());
                final int[] leaveEdges = staticGtfs.getAlightEdgesForTrip().get(tripUpdate.getTrip());
                tripUpdate.getStopTimeUpdateList().stream()
                        .filter(stopTimeUpdate -> stopTimeUpdate.getScheduleRelationship() == SKIPPED)
                        .mapToInt(stu -> stu.getStopSequence()-1) // stop sequence number is 1-based, not 0-based
                        .forEach(skippedStopSequenceNumber -> {
                            blockedEdges.add(boardEdges[skippedStopSequenceNumber]);
                            blockedEdges.add(leaveEdges[skippedStopSequenceNumber]);
                        });
            });
        final Graph graph = new Graph() {

            @Override
            public Graph getBaseGraph() {
                return null;
            }

            @Override
            public int getNodes() {
                return 0;
            }

            @Override
            public NodeAccess getNodeAccess() {
                return null;
            }

            @Override
            public BBox getBounds() {
                return null;
            }

            @Override
            public EdgeIteratorState edge(int a, int b) {
                return null;
            }

            @Override
            public EdgeIteratorState edge(int a, int b, double distance, boolean bothDirections) {
                return null;
            }

            @Override
            public EdgeIteratorState getEdgeIteratorState(int edgeId, int adjNode) {
                return null;
            }

            @Override
            public AllEdgesIterator getAllEdges() {
                return null;
            }

            @Override
            public EdgeExplorer createEdgeExplorer(EdgeFilter filter) {
                return null;
            }

            @Override
            public EdgeExplorer createEdgeExplorer() {
                return null;
            }

            @Override
            public Graph copyTo(Graph g) {
                return null;
            }

            @Override
            public GraphExtension getExtension() {
                return staticGtfs;
            }
        };
        final GtfsReader gtfsReader = new GtfsReader("wurst", graph, null, null);
        feedMessage.getEntityList().stream()
                .filter(GtfsRealtime.FeedEntity::hasTripUpdate)
                .map(GtfsRealtime.FeedEntity::getTripUpdate)
                .filter(tripUpdate -> tripUpdate.getTrip().getScheduleRelationship() == GtfsRealtime.TripDescriptor.ScheduleRelationship.ADDED)
                .map(tripUpdate -> {
                    Trip trip = new Trip();
                    trip.trip_id = tripUpdate.getTrip().getTripId();
                    trip.route_id = tripUpdate.getTrip().getRouteId();
                    final List<StopTime> stopTimes = tripUpdate.getStopTimeUpdateList().stream()
                            .map(stopTimeUpdate -> {
                                final StopTime stopTime = new StopTime();
                                stopTime.stop_sequence = stopTimeUpdate.getStopSequence();
                                // stopTime.arrival_time = stopTimeUpdate.getArrival().getTime();
                                return stopTime;
                            })
                            .collect(Collectors.toList());
                    BitSet validity = new BitSet();
                    return new GtfsReader.TripWithStopTimes(trip, stopTimes, validity);
                })
                .forEach(trip -> gtfsReader.addTrips(LocalDate.now(), LocalDate.now(), Collections.singletonList(trip), 0));
        return new RealtimeFeed(blockedEdges);
    }

    boolean isBlocked(int edgeId) {
        return blockedEdges.contains(edgeId);
    }
}
