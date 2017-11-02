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
import com.carrotsearch.hppc.IntIntHashMap;
import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.StopTime;
import com.conveyal.gtfs.model.Trip;
import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.routing.VirtualEdgeIteratorState;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphExtension;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.BBox;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.SKIPPED;
import static java.time.temporal.ChronoUnit.DAYS;

public class RealtimeFeed {
    private final IntHashSet blockedEdges;

    private final List<VirtualEdgeIteratorState> additionalEdges;

    private RealtimeFeed(IntHashSet blockedEdges, List<VirtualEdgeIteratorState> additionalEdges) {
        this.blockedEdges = blockedEdges;
        this.additionalEdges = additionalEdges;
    }

    public static RealtimeFeed empty() {
        return new RealtimeFeed(new IntHashSet(), Collections.emptyList());
    }

    public static RealtimeFeed fromProtobuf(Graph graph, GtfsStorage staticGtfs, PtFlagEncoder encoder, GtfsRealtime.FeedMessage feedMessage) {
        String feedKey = "gtfs_0"; //FIXME
        GTFSFeed feed = staticGtfs.getGtfsFeeds().get(feedKey);
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
        final List<VirtualEdgeIteratorState> additionalEdges = new ArrayList<>();
        final Graph overlayGraph = new Graph() {
            int nNodes = 0;
            int firstEdge = graph.getAllEdges().getMaxId()+1;
            final NodeAccess nodeAccess = new NodeAccess() {
                IntIntHashMap additionalNodeFields = new IntIntHashMap();

                @Override
                public int getAdditionalNodeField(int nodeId) {
                    return 0;
                }

                @Override
                public void setAdditionalNodeField(int nodeId, int additionalValue) {
                    additionalNodeFields.put(nodeId, additionalValue);
                }

                @Override
                public boolean is3D() {
                    return false;
                }

                @Override
                public int getDimension() {
                    return 0;
                }

                @Override
                public void ensureNode(int nodeId) {

                }

                @Override
                public void setNode(int nodeId, double lat, double lon) {

                }

                @Override
                public void setNode(int nodeId, double lat, double lon, double ele) {

                }

                @Override
                public double getLatitude(int nodeId) {
                    return 0;
                }

                @Override
                public double getLat(int nodeId) {
                    return 0;
                }

                @Override
                public double getLongitude(int nodeId) {
                    return 0;
                }

                @Override
                public double getLon(int nodeId) {
                    return 0;
                }

                @Override
                public double getElevation(int nodeId) {
                    return 0;
                }

                @Override
                public double getEle(int nodeId) {
                    return 0;
                }
            };
            @Override
            public Graph getBaseGraph() {
                return null;
            }

            @Override
            public int getNodes() {
                return graph.getNodes() + nNodes;
            }

            @Override
            public NodeAccess getNodeAccess() {
                return nodeAccess;
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
                int edge = firstEdge++;
                final VirtualEdgeIteratorState newEdge = new VirtualEdgeIteratorState(-1,
                        edge, a, b, 0,0, "", new PointList());
                final VirtualEdgeIteratorState reverseNewEdge = new VirtualEdgeIteratorState(-1,
                        edge, b, a, 0,0, "", new PointList());

                newEdge.setReverseEdge(reverseNewEdge);
                reverseNewEdge.setReverseEdge(newEdge);
                additionalEdges.add(newEdge);
//                additionalEdges.add(reverseNewEdge); //FIXME
                return newEdge;
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
        final GtfsReader gtfsReader = new GtfsReader(feedKey, overlayGraph, encoder, null);
        Instant timestamp = Instant.ofEpochSecond(feedMessage.getHeader().getTimestamp());
        LocalDate dateToChange = timestamp.atZone(ZoneId.of(feed.agency.values().iterator().next().agency_timezone)).toLocalDate(); //FIXME

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
                                stopTime.stop_id = stopTimeUpdate.getStopId();
                                stopTime.trip_id = trip.trip_id;
                                final ZonedDateTime arrival_time = Instant.ofEpochSecond(stopTimeUpdate.getArrival().getTime()).atZone(ZoneId.of("America/Los_Angeles"));
                                stopTime.arrival_time = (int) Duration.between(arrival_time.truncatedTo(ChronoUnit.DAYS), arrival_time).getSeconds();
                                final ZonedDateTime departure_time = Instant.ofEpochSecond(stopTimeUpdate.getArrival().getTime()).atZone(ZoneId.of("America/Los_Angeles"));
                                stopTime.departure_time = (int) Duration.between(departure_time.truncatedTo(ChronoUnit.DAYS), departure_time).getSeconds();
                                return stopTime;
                            })
                            .collect(Collectors.toList());
                    BitSet validOnDay = new BitSet();
                    LocalDate startDate = feed.calculateStats().getStartDate();
                    validOnDay.set((int) DAYS.between(startDate, dateToChange));
                    return new GtfsReader.TripWithStopTimes(trip, stopTimes, validOnDay);
                })
                .forEach(trip -> gtfsReader.addTrips(ZoneId.systemDefault(), Collections.singletonList(trip), 0));
        gtfsReader.wireUpStops();
        gtfsReader.connectStopsToStationNodes();
        return new RealtimeFeed(blockedEdges, additionalEdges);
    }

    boolean isBlocked(int edgeId) {
        return blockedEdges.contains(edgeId);
    }

    List<VirtualEdgeIteratorState> getAdditionalEdges() {
        return additionalEdges;
    }

}
