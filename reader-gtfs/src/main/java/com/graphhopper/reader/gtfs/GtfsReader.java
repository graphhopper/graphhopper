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

import com.carrotsearch.hppc.IntArrayList;
import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Frequency;
import com.conveyal.gtfs.model.Route;
import com.conveyal.gtfs.model.Service;
import com.conveyal.gtfs.model.Stop;
import com.conveyal.gtfs.model.StopTime;
import com.conveyal.gtfs.model.Transfer;
import com.conveyal.gtfs.model.Trip;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;
import gnu.trove.map.hash.TIntIntHashMap;
import org.mapdb.Fun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static com.conveyal.gtfs.model.Entity.Writer.convertToGtfsTime;
import static java.time.temporal.ChronoUnit.DAYS;

class GtfsReader {

    private LocalDate startDate;
    private LocalDate endDate;

    static class TripWithStopTimes {
        public TripWithStopTimes(Trip trip, List<StopTime> stopTimes, BitSet validOnDay, Set<Integer> cancelledArrivals, Set<Integer> cancelledDepartures) {
            this.trip = trip;
            this.stopTimes = stopTimes;
            this.validOnDay = validOnDay;
            this.cancelledArrivals = cancelledArrivals;
            this.cancelledDeparture = cancelledDepartures;
        }

        Trip trip;
        List<StopTime> stopTimes;
        BitSet validOnDay;
        Set<Integer> cancelledArrivals;
        Set<Integer> cancelledDeparture;
    }

    private static class TimelineNodeIdWithTripId {
        final String tripId;
        final String routeId;
        final int timelineNodeId;

        private TimelineNodeIdWithTripId(int timelineNodeId, String tripId, String routeId) {
            this.tripId = tripId;
            this.routeId = routeId;
            this.timelineNodeId = timelineNodeId;
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(GtfsReader.class);

    private final Graph graph;
    private final LocationIndex walkNetworkIndex;
    private final GtfsStorageI gtfsStorage;

    private final DistanceCalc distCalc = Helper.DIST_EARTH;
    private Transfers transfers;
    private final NodeAccess nodeAccess;
    private final String id;
    private int i;
    private GTFSFeed feed;
    private final TIntIntHashMap times = new TIntIntHashMap();
    private final SetMultimap<String, TimelineNodeIdWithTripId> departureTimelineNodes = HashMultimap.create();
    private final SetMultimap<String, TimelineNodeIdWithTripId> arrivalTimelineNodes = HashMultimap.create();
    private final PtFlagEncoder encoder;

    GtfsReader(String id, Graph graph, GtfsStorageI gtfsStorage, PtFlagEncoder encoder, LocationIndex walkNetworkIndex) {
        this.id = id;
        this.graph = graph;
        this.gtfsStorage = gtfsStorage;
        this.nodeAccess = graph.getNodeAccess();
        this.walkNetworkIndex = walkNetworkIndex;
        this.encoder = encoder;
        this.feed = this.gtfsStorage.getGtfsFeeds().get(id);
        this.transfers = this.gtfsStorage.getTransfers().get(id);
        this.i = graph.getNodes();
        this.startDate = feed.calculateStats().getStartDate();
        this.endDate = feed.calculateStats().getEndDate();
    }

    void readGraph() {
        gtfsStorage.getFares().putAll(feed.fares);
        transfers = new Transfers(feed);
        gtfsStorage.getTransfers().put(id, transfers);
        connectStopsToStreetNetwork();
        buildPtNetwork();
    }

    private void connectStopsToStreetNetwork() {
        EdgeFilter filter = new EverythingButPt(encoder);
        for (Stop stop : feed.stops.values()) {
            QueryResult locationQueryResult = walkNetworkIndex.findClosest(stop.stop_lat, stop.stop_lon, filter);
            int streetNode;
            if (!locationQueryResult.isValid()) {
                streetNode = i++;
                nodeAccess.setNode(streetNode, stop.stop_lat, stop.stop_lon);
                graph.edge(streetNode, streetNode, 0.0, false);
            } else {
                streetNode = locationQueryResult.getClosestNode();
            }
            gtfsStorage.getStationNodes().put(stop.stop_id, streetNode);
        }
    }

    private void buildPtNetwork() {
        HashMultimap<String, Trip> blockTrips = HashMultimap.create();
        for (Trip trip : feed.trips.values()) {
            if (trip.block_id != null) {
                blockTrips.put(trip.block_id, trip);
            } else {
                blockTrips.put("non-block-trip"+trip.trip_id, trip);
            }
        }
        blockTrips.asMap().values().forEach(unsortedTrips -> {
            List<TripWithStopTimes> trips = unsortedTrips.stream()
                    .map(trip -> {
                        Service service = feed.services.get(trip.service_id);
                        BitSet validOnDay = new BitSet((int) DAYS.between(startDate, endDate));
                        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                            if (service.activeOn(date)) {
                                validOnDay.set((int) DAYS.between(startDate, date));
                            }
                        }
                        ArrayList<StopTime> stopTimes = new ArrayList<>();
                        getInterpolatedStopTimesForTrip(trip.trip_id).forEach(stopTimes::add);
                        return new TripWithStopTimes(trip, stopTimes, validOnDay, Collections.emptySet(), Collections.emptySet());
                    })
                    .sorted(Comparator.comparingInt(trip -> trip.stopTimes.iterator().next().departure_time))
                    .collect(Collectors.toList());
            if (trips.stream().map(trip -> feed.getFrequencies(trip.trip.trip_id)).distinct().count() != 1) {
                throw new RuntimeException("Found a block with frequency-based trips. Not supported.");
            }
            ZoneId zoneId = ZoneId.of(feed.agency.get(feed.routes.get(trips.iterator().next().trip.route_id).agency_id).agency_timezone);
            Collection<Frequency> frequencies = feed.getFrequencies(trips.iterator().next().trip.trip_id);
            if (frequencies.isEmpty()) {
                addTrips(zoneId, trips, 0, false);
            } else {
                for (Frequency frequency : frequencies) {
                    for (int time = frequency.start_time; time < frequency.end_time; time += frequency.headway_secs) {
                        addTrips(zoneId, trips, time, true);
                    }
                }
            }
        });

        wireUpStops();
    }

    void wireUpStops() {
        for (Stop stop : feed.stops.values()) {
            if (stop.location_type == 0) { // Only stops. Not interested in parent stations for now.
                int streetNode = gtfsStorage.getStationNodes().get(stop.stop_id);

                if (arrivalTimelineNodes.containsKey(stop.stop_id)) {
                    final Map<String, List<TimelineNodeIdWithTripId>> arrivalTimelineNodesByRoute = arrivalTimelineNodes.get(stop.stop_id).stream().collect(Collectors.groupingBy(t -> t.routeId));

                    arrivalTimelineNodesByRoute.forEach((routeId, timelineNodesWithTripId) -> {
                        nodeAccess.setNode(i++, stop.stop_lat, stop.stop_lon);
                        int stopExitNode = i-1;
                        nodeAccess.setAdditionalNodeField(stopExitNode, NodeType.STOP_EXIT_NODE.ordinal());

                        EdgeIteratorState exitEdge = graph.edge(stopExitNode, streetNode, 0.0, false);
                        setEdgeType(exitEdge, GtfsStorage.EdgeType.EXIT_PT);
                        exitEdge.setName(stop.stop_name);
                        gtfsStorage.getRoutes().put(exitEdge.getEdge(), routeId);

                        NavigableSet<Fun.Tuple2<Integer, Integer>> timeNodes = sorted(timelineNodesWithTripId);
                        wireUpAndAndConnectArrivalTimeline(stop, routeId,stopExitNode, timeNodes);
                    });

                }

                if (departureTimelineNodes.containsKey(stop.stop_id)) {
                    final Map<String, List<TimelineNodeIdWithTripId>> departureTimelineNodesByRoute = departureTimelineNodes.get(stop.stop_id).stream().collect(Collectors.groupingBy(t -> t.routeId));

                    departureTimelineNodesByRoute.forEach((routeId, timelineNodesWithTripId) -> {
                        nodeAccess.setNode(i++, stop.stop_lat, stop.stop_lon);
                        int stopEnterNode = i-1;
                        nodeAccess.setAdditionalNodeField(stopEnterNode, NodeType.STOP_ENTER_NODE.ordinal());

                        EdgeIteratorState entryEdge = graph.edge(streetNode, stopEnterNode, 0.0, false);
                        setEdgeType(entryEdge, GtfsStorage.EdgeType.ENTER_PT);
                        entryEdge.setName(stop.stop_name);

                        NavigableSet<Fun.Tuple2<Integer, Integer>> timeNodes = sorted(timelineNodesWithTripId);
                        wireUpAndAndConnectDepartureTimeline(stop, routeId,stopEnterNode, timeNodes);
                    });
                }
            }
        }
        insertTransfers();
    }

    private NavigableSet<Fun.Tuple2<Integer, Integer>> sorted(List<TimelineNodeIdWithTripId> timelineNodesWithTripId) {
        NavigableSet<Fun.Tuple2<Integer, Integer>> timeNodes = new TreeSet<>();
        timelineNodesWithTripId.stream().map(t -> t.timelineNodeId)
                .forEach(nodeId -> timeNodes.add(new Fun.Tuple2<>(times.get(nodeId) % (24*60*60), nodeId)));
        return timeNodes;
    }

    void insertTransfers() {
        departureTimelineNodes.asMap().forEach((toStopId, timelineNodesWithTripId) -> {
            final Map<String, List<TimelineNodeIdWithTripId>> departureTimelineNodesByRoute = departureTimelineNodes.get(toStopId).stream().collect(Collectors.groupingBy(t -> t.routeId));
            departureTimelineNodesByRoute.forEach((toRouteId, timelineNodesByRoute) -> {
                NavigableSet<Fun.Tuple2<Integer, Integer>> timeNodes = sorted(timelineNodesByRoute);
                final Optional<Transfer> withinStationTransfer = transfers.getTransfersToStop(toStopId, toRouteId).stream().filter(t -> t.from_stop_id.equals(toStopId)).findAny();
                if (!withinStationTransfer.isPresent()) {
                    insertInboundTransfers(toStopId, null, 0, timeNodes);
                }
                transfers.getTransfersToStop(toStopId, toRouteId).forEach(transfer -> {
                    insertInboundTransfers(transfer.from_stop_id, transfer.from_route_id, transfer.min_transfer_time, timeNodes);
                });
            });
        });
        if (graph.getBaseGraph() != null) {
            arrivalTimelineNodes.asMap().forEach((fromStopId, timelineNodesWithTripId) -> {
                Map<String, List<TimelineNodeIdWithTripId>> arrivalTimelineNodesByRoute = timelineNodesWithTripId.stream().collect(Collectors.groupingBy(t -> t.routeId));
                arrivalTimelineNodesByRoute.forEach((fromRouteId, timelineNodesByRoute) -> {
                    NavigableSet<Fun.Tuple2<Integer, Integer>> timeNodes = sorted(timelineNodesByRoute);
                    final Optional<Transfer> withinStationTransfer = transfers.getTransfersFromStop(fromStopId, fromRouteId).stream().filter(t -> t.from_stop_id.equals(fromStopId)).findAny();
                    if (!withinStationTransfer.isPresent()) {
                        insertOutboundTransfers(fromStopId, null, 0, timeNodes);
                    }
                    transfers.getTransfersFromStop(fromStopId, fromRouteId).forEach(transfer -> {
                        insertOutboundTransfers(transfer.from_stop_id, transfer.from_route_id, transfer.min_transfer_time, timeNodes);
                    });
                });
            });
        }
    }

    private void addTrips(ZoneId zoneId, List<TripWithStopTimes> trips, int time, boolean frequencyBased) {
        List<TripWithStopTimeAndArrivalNode> arrivalNodes = new ArrayList<>();
        for (TripWithStopTimes trip : trips) {
            GtfsRealtime.TripDescriptor.Builder tripDescriptor = GtfsRealtime.TripDescriptor.newBuilder()
                    .setTripId(trip.trip.trip_id)
                    .setRouteId(trip.trip.route_id);
            if (frequencyBased) {
                tripDescriptor = tripDescriptor.setStartTime(convertToGtfsTime(time));
            }
            addTrip(zoneId, time, arrivalNodes, trip, tripDescriptor.build());
        }
    }

    private static class TripWithStopTimeAndArrivalNode {
        TripWithStopTimes tripWithStopTimes;
        int arrivalNode;
    }

    void addTrip(ZoneId zoneId, int time, List<TripWithStopTimeAndArrivalNode> arrivalNodes, GtfsReader.TripWithStopTimes trip, GtfsRealtime.TripDescriptor tripDescriptor) {
        IntArrayList boardEdges = new IntArrayList();
        IntArrayList alightEdges = new IntArrayList();
        StopTime prev = null;
        int arrivalNode = -1;
        int departureNode = -1;
        for (StopTime stopTime : trip.stopTimes) {
            Stop stop = feed.stops.get(stopTime.stop_id);
            arrivalNode = i++;
            nodeAccess.setNode(arrivalNode, stop.stop_lat, stop.stop_lon);
            nodeAccess.setAdditionalNodeField(arrivalNode, NodeType.INTERNAL_PT.ordinal());
            times.put(arrivalNode, stopTime.arrival_time + time);
            if (prev != null) {
                Stop fromStop = feed.stops.get(prev.stop_id);
                double distance = distCalc.calcDist(
                        fromStop.stop_lat,
                        fromStop.stop_lon,
                        stop.stop_lat,
                        stop.stop_lon);
                EdgeIteratorState edge = graph.edge(
                        departureNode,
                        arrivalNode,
                        distance,
                        false);
                edge.setName(stop.stop_name);
                setEdgeType(edge, GtfsStorage.EdgeType.HOP);
                edge.setFlags(encoder.setTime(edge.getFlags(), stopTime.arrival_time - prev.departure_time));
                gtfsStorage.getStopSequences().put(edge.getEdge(), stopTime.stop_sequence);
            }
            final int departureTimelineNode = i++;
            nodeAccess.setNode(departureTimelineNode, stop.stop_lat, stop.stop_lon);
            nodeAccess.setAdditionalNodeField(departureTimelineNode, NodeType.INTERNAL_PT.ordinal());
            times.put(departureTimelineNode, stopTime.departure_time + time);
            departureTimelineNodes.put(stopTime.stop_id, new TimelineNodeIdWithTripId(departureTimelineNode, trip.trip.trip_id, trip.trip.route_id));
            final int arrivalTimelineNode = i++;
            nodeAccess.setNode(arrivalTimelineNode, stop.stop_lat, stop.stop_lon);
            nodeAccess.setAdditionalNodeField(arrivalTimelineNode, NodeType.INTERNAL_PT.ordinal());
            times.put(arrivalTimelineNode, stopTime.arrival_time + time);
            arrivalTimelineNodes.put(stopTime.stop_id, new TimelineNodeIdWithTripId(arrivalTimelineNode, trip.trip.trip_id, trip.trip.route_id));
            departureNode = i++;
            nodeAccess.setNode(departureNode, stop.stop_lat, stop.stop_lon);
            nodeAccess.setAdditionalNodeField(departureNode, NodeType.INTERNAL_PT.ordinal());
            times.put(departureNode, stopTime.departure_time + time);
            int dayShift = stopTime.departure_time / (24 * 60 * 60);
            GtfsStorage.Validity validOn = new GtfsStorage.Validity(getValidOn(trip.validOnDay, dayShift), zoneId, startDate);
            int validityId;
            if (gtfsStorage.getOperatingDayPatterns().containsKey(validOn)) {
                validityId = gtfsStorage.getOperatingDayPatterns().get(validOn);
            } else {
                validityId = gtfsStorage.getOperatingDayPatterns().size();
                gtfsStorage.getOperatingDayPatterns().put(validOn, validityId);
            }

            EdgeIteratorState boardEdge = graph.edge(
                    departureTimelineNode,
                    departureNode,
                    0.0,
                    false);
            boardEdge.setName(getRouteName(feed, trip.trip));
            setEdgeType(boardEdge, GtfsStorage.EdgeType.BOARD);
            while (boardEdges.size() < stopTime.stop_sequence) {
                boardEdges.add(-1); // Padding, so that index == stop_sequence
            }
            boardEdges.add(boardEdge.getEdge());
            gtfsStorage.getStopSequences().put(boardEdge.getEdge(), stopTime.stop_sequence);
            gtfsStorage.getTripDescriptors().put(boardEdge.getEdge(), tripDescriptor.toByteArray());
            boardEdge.setFlags(encoder.setValidityId(boardEdge.getFlags(), validityId));
            boardEdge.setFlags(encoder.setTransfers(boardEdge.getFlags(), 1));

            EdgeIteratorState alightEdge = graph.edge(
                    arrivalNode,
                    arrivalTimelineNode,
                    0.0,
                    false);
            alightEdge.setName(getRouteName(feed, trip.trip));
            setEdgeType(alightEdge, GtfsStorage.EdgeType.ALIGHT);
            while (alightEdges.size() < stopTime.stop_sequence) {
                alightEdges.add(-1);
            }
            alightEdges.add(alightEdge.getEdge());
            gtfsStorage.getStopSequences().put(alightEdge.getEdge(), stopTime.stop_sequence);
            gtfsStorage.getTripDescriptors().put(alightEdge.getEdge(), tripDescriptor.toByteArray());
            alightEdge.setFlags(encoder.setValidityId(alightEdge.getFlags(), validityId));
//                            alightEdge.setFlags(encoder.setTransfers(alightEdge.getFlags(), 1));


            EdgeIteratorState dwellEdge = graph.edge(
                    arrivalNode,
                    departureNode,
                    0.0,
                    false);
            dwellEdge.setName(getRouteName(feed, trip.trip));
            setEdgeType(dwellEdge, GtfsStorage.EdgeType.DWELL);
            dwellEdge.setFlags(encoder.setTime(dwellEdge.getFlags(), stopTime.departure_time - stopTime.arrival_time));
            if (prev == null) {
                insertInboundBlockTransfers(arrivalNodes, tripDescriptor, departureNode, stopTime, stop, validOn, zoneId);
            }
            prev = stopTime;
        }
        gtfsStorage.getBoardEdgesForTrip().put(GtfsStorage.tripKey(tripDescriptor.getTripId(), tripDescriptor.getStartTime()), boardEdges.toArray());
        gtfsStorage.getAlightEdgesForTrip().put(GtfsStorage.tripKey(tripDescriptor.getTripId(), tripDescriptor.getStartTime()), alightEdges.toArray());
        TripWithStopTimeAndArrivalNode tripWithStopTimeAndArrivalNode = new TripWithStopTimeAndArrivalNode();
        tripWithStopTimeAndArrivalNode.tripWithStopTimes = trip;
        tripWithStopTimeAndArrivalNode.arrivalNode = arrivalNode;
        arrivalNodes.add(tripWithStopTimeAndArrivalNode);
    }

    int addDelayedBoardEdge(ZoneId zoneId, GtfsRealtime.TripDescriptor tripDescriptor, int stopSequence, int departureTime, int departureNode, BitSet validOnDay) {
        Trip trip = feed.trips.get(tripDescriptor.getTripId());
        final int departureTimelineNode = i++;
        StopTime stopTime = feed.stop_times.get(new Fun.Tuple2(tripDescriptor.getTripId(), stopSequence));
        Stop stop = feed.stops.get(stopTime.stop_id);
        nodeAccess.setNode(departureTimelineNode, stop.stop_lat, stop.stop_lon);
        nodeAccess.setAdditionalNodeField(departureTimelineNode, NodeType.INTERNAL_PT.ordinal());
        times.put(departureTimelineNode, departureTime);
        departureTimelineNodes.put(stopTime.stop_id, new TimelineNodeIdWithTripId(departureTimelineNode, tripDescriptor.getTripId(), trip.route_id));

        int dayShift = departureTime / (24 * 60 * 60);
        GtfsStorage.Validity validOn = new GtfsStorage.Validity(getValidOn(validOnDay, dayShift), zoneId, startDate);
        int validityId;
        if (gtfsStorage.getOperatingDayPatterns().containsKey(validOn)) {
            validityId = gtfsStorage.getOperatingDayPatterns().get(validOn);
        } else {
            validityId = gtfsStorage.getOperatingDayPatterns().size();
            gtfsStorage.getOperatingDayPatterns().put(validOn, validityId);
        }

        EdgeIteratorState boardEdge = graph.edge(
                departureTimelineNode,
                departureNode,
                0.0,
                false);
        boardEdge.setName(getRouteName(feed, trip));
        setEdgeType(boardEdge, GtfsStorage.EdgeType.BOARD);
        gtfsStorage.getStopSequences().put(boardEdge.getEdge(), stopSequence);
        gtfsStorage.getTripDescriptors().put(boardEdge.getEdge(), tripDescriptor.toByteArray());
        boardEdge.setFlags(encoder.setValidityId(boardEdge.getFlags(), validityId));
        boardEdge.setFlags(encoder.setTransfers(boardEdge.getFlags(), 1));
        return boardEdge.getEdge();
    }

    private void wireUpAndAndConnectArrivalTimeline(Stop toStop, String routeId, int stopExitNode, NavigableSet<Fun.Tuple2<Integer, Integer>> timeNodes) {
        ZoneId zoneId = ZoneId.of(feed.agency.get(feed.routes.get(routeId).agency_id).agency_timezone);
        int time = 0;
        int prev = -1;
        for (Fun.Tuple2<Integer, Integer> e : timeNodes.descendingSet()) {
            EdgeIteratorState leaveTimeExpandedNetworkEdge = graph.edge(e.b, stopExitNode, 0.0, false);
            setEdgeType(leaveTimeExpandedNetworkEdge, GtfsStorage.EdgeType.LEAVE_TIME_EXPANDED_NETWORK);
            int arrivalTime = e.a;
            leaveTimeExpandedNetworkEdge.setFlags(encoder.setTime(leaveTimeExpandedNetworkEdge.getFlags(), arrivalTime));
            setFeedIdWithTimezone(leaveTimeExpandedNetworkEdge, new GtfsStorage.FeedIdWithTimezone(id, zoneId));
            if (prev != -1) {
                EdgeIteratorState edge = graph.edge(e.b, prev, 0.0, false);
                setEdgeType(edge, GtfsStorage.EdgeType.WAIT_ARRIVAL);
                edge.setName(toStop.stop_name);
                edge.setFlags(encoder.setTime(edge.getFlags(), time-e.a));
            }
            time = e.a;
            prev = e.b;
        }
    }

    private void setFeedIdWithTimezone(EdgeIteratorState leaveTimeExpandedNetworkEdge, GtfsStorage.FeedIdWithTimezone validOn) {
        int validityId;
        if (gtfsStorage.getWritableTimeZones().containsKey(validOn)) {
            validityId = gtfsStorage.getWritableTimeZones().get(validOn);
        } else {
            validityId = gtfsStorage.getWritableTimeZones().size();
            gtfsStorage.getWritableTimeZones().put(validOn, validityId);
        }
        leaveTimeExpandedNetworkEdge.setFlags(encoder.setValidityId(leaveTimeExpandedNetworkEdge.getFlags(), validityId));
    }

    private void wireUpAndAndConnectDepartureTimeline(Stop toStop, String toRouteId, int stopEnterNode, NavigableSet<Fun.Tuple2<Integer, Integer>> timeNodes) {
        ZoneId zoneId = ZoneId.of(feed.agency.get(feed.routes.get(toRouteId).agency_id).agency_timezone);
        int time = 0;
        int prev = -1;
        for (Fun.Tuple2<Integer, Integer> e : timeNodes.descendingSet()) {
            EdgeIteratorState enterTimeExpandedNetworkEdge = graph.edge(stopEnterNode, e.b, 0.0, false);
            enterTimeExpandedNetworkEdge.setName(toStop.stop_name);
            setEdgeType(enterTimeExpandedNetworkEdge, GtfsStorage.EdgeType.ENTER_TIME_EXPANDED_NETWORK);
            enterTimeExpandedNetworkEdge.setFlags(encoder.setTime(enterTimeExpandedNetworkEdge.getFlags(), e.a));
            setFeedIdWithTimezone(enterTimeExpandedNetworkEdge, new GtfsStorage.FeedIdWithTimezone(id, zoneId));
            if (prev != -1) {
                EdgeIteratorState edge = graph.edge(e.b, prev, 0.0, false);
                setEdgeType(edge, GtfsStorage.EdgeType.WAIT);
                edge.setName(toStop.stop_name);
                edge.setFlags(encoder.setTime(edge.getFlags(), time-e.a));
            }
            time = e.a;
            prev = e.b;
        }
        if (!timeNodes.isEmpty()) {
            EdgeIteratorState edge = graph.edge(timeNodes.last().b, timeNodes.first().b, 0.0, false);
            int rolloverTime = 24 * 60 * 60 - timeNodes.last().a + timeNodes.first().a;
            setEdgeType(edge, GtfsStorage.EdgeType.OVERNIGHT);
            edge.setName(toStop.stop_name);
            edge.setFlags(encoder.setTime(edge.getFlags(), rolloverTime));
        }
    }

    private void insertInboundBlockTransfers(List<TripWithStopTimeAndArrivalNode> arrivalNodes, GtfsRealtime.TripDescriptor tripDescriptor, int departureNode, StopTime stopTime, Stop stop, GtfsStorage.Validity validOn, ZoneId zoneId) {
        BitSet accumulatorValidity = new BitSet(validOn.validity.size());
        accumulatorValidity.or(validOn.validity);
        EdgeIteratorState edge;
        ListIterator<TripWithStopTimeAndArrivalNode> li = arrivalNodes.listIterator(arrivalNodes.size());
        while(li.hasPrevious() && accumulatorValidity.cardinality() > 0) {
            TripWithStopTimeAndArrivalNode lastTrip = li.previous();
            int dwellTime = times.get(departureNode) - times.get(lastTrip.arrivalNode);
            if (dwellTime >= 0 && accumulatorValidity.intersects(lastTrip.tripWithStopTimes.validOnDay)) {
                BitSet blockTransferValidity = new BitSet(validOn.validity.size());
                blockTransferValidity.or(validOn.validity);
                blockTransferValidity.and(accumulatorValidity);
                GtfsStorage.Validity blockTransferValidOn = new GtfsStorage.Validity(blockTransferValidity, zoneId, startDate);
                int blockTransferValidityId;
                if (gtfsStorage.getOperatingDayPatterns().containsKey(blockTransferValidOn)) {
                    blockTransferValidityId = gtfsStorage.getOperatingDayPatterns().get(blockTransferValidOn);
                } else {
                    blockTransferValidityId = gtfsStorage.getOperatingDayPatterns().size();
                    gtfsStorage.getOperatingDayPatterns().put(blockTransferValidOn, blockTransferValidityId);
                }
                nodeAccess.setNode(i++, stop.stop_lat, stop.stop_lon);
                nodeAccess.setAdditionalNodeField(i-1, NodeType.INTERNAL_PT.ordinal());
                edge = graph.edge(
                        lastTrip.arrivalNode,
                        i-1,
                        0.0,
                        false);
                setEdgeType(edge, GtfsStorage.EdgeType.TRANSFER);
                edge.setFlags(encoder.setTime(edge.getFlags(), dwellTime));
                edge = graph.edge(
                        i-1,
                        departureNode,
                        0.0,
                        false);
                setEdgeType(edge, GtfsStorage.EdgeType.BOARD);
                edge.setFlags(encoder.setValidityId(edge.getFlags(), blockTransferValidityId));
                gtfsStorage.getStopSequences().put(edge.getEdge(), stopTime.stop_sequence);
                gtfsStorage.getTripDescriptors().put(edge.getEdge(), tripDescriptor.toByteArray());
                accumulatorValidity.andNot(lastTrip.tripWithStopTimes.validOnDay);
            }
        }
    }

    private Iterable<StopTime> getInterpolatedStopTimesForTrip(String trip_id) {
        try {
            return feed.getInterpolatedStopTimesForTrip(trip_id);
        } catch (GTFSFeed.FirstAndLastStopsDoNotHaveTimes e) {
            throw new RuntimeException(e);
        }
    }

    private void insertInboundTransfers(String fromStopId, String from_route_id, int minimumTransferTime, SortedSet<Fun.Tuple2<Integer, Integer>> toStopTimelineNode) {
        int stationNode = gtfsStorage.getStationNodes().get(fromStopId);
        EdgeIterator i = graph.createEdgeExplorer().setBaseNode(stationNode);
        while (i.next()) {
            GtfsStorage.EdgeType edgeType = encoder.getEdgeType(i.getFlags());
            if (edgeType == GtfsStorage.EdgeType.EXIT_PT) {
                String routeId = gtfsStorage.getRoutes().get(i.getEdge());
                if (from_route_id == null || from_route_id.equals(routeId)) {
                    EdgeIterator j = graph.createEdgeExplorer().setBaseNode(i.getAdjNode());
                    while (j.next()) {
                        GtfsStorage.EdgeType edgeType2 = encoder.getEdgeType(j.getFlags());
                        if (edgeType2 == GtfsStorage.EdgeType.LEAVE_TIME_EXPANDED_NETWORK) {
                            int arrivalTime = (int) encoder.getTime(j.getFlags());
                            SortedSet<Fun.Tuple2<Integer, Integer>> tailSet = toStopTimelineNode.tailSet(new Fun.Tuple2<>(arrivalTime + minimumTransferTime, -1));
                            if (!tailSet.isEmpty()) {
                                Fun.Tuple2<Integer, Integer> e = tailSet.first();
                                EdgeIteratorState edge = graph.edge(j.getAdjNode(), e.b, 0.0, false);
                                setEdgeType(edge, GtfsStorage.EdgeType.TRANSFER);
                                edge.setFlags(encoder.setTime(edge.getFlags(), e.a - arrivalTime));
                            }
                        }
                    }
                }
            }
        }
    }

    private void insertOutboundTransfers(String toStopId, String toRouteId, int minimumTransferTime, SortedSet<Fun.Tuple2<Integer, Integer>> fromStopTimelineNodes) {
        int stationNode = gtfsStorage.getStationNodes().get(toStopId);
        EdgeIterator i = graph.getBaseGraph().createEdgeExplorer().setBaseNode(stationNode);
        while (i.next()) {
            GtfsStorage.EdgeType edgeType = encoder.getEdgeType(i.getFlags());
            if (edgeType == GtfsStorage.EdgeType.ENTER_PT) {
                String routeId = gtfsStorage.getRoutes().get(i.getEdge());
                if (toRouteId == null || toRouteId.equals(routeId)) {
                    fromStopTimelineNodes.forEach(e -> {
                        EdgeIterator j = graph.getBaseGraph().createEdgeExplorer().setBaseNode(i.getAdjNode());
                        while (j.next()) {
                            GtfsStorage.EdgeType edgeType2 = encoder.getEdgeType(j.getFlags());
                            if (edgeType2 == GtfsStorage.EdgeType.ENTER_TIME_EXPANDED_NETWORK) {
                                int departureTime = (int) encoder.getTime(j.getFlags());
                                if (departureTime < e.a + minimumTransferTime) {
                                    continue;
                                }
                                EdgeIteratorState edge = graph.edge(e.b, j.getAdjNode(), 0.0, false);
                                setEdgeType(edge, GtfsStorage.EdgeType.TRANSFER);
                                edge.setFlags(encoder.setTime(edge.getFlags(), departureTime - e.a));
                                break;
                            }
                        }
                    });
                }
            }
        }
    }

    private String getRouteName(GTFSFeed feed, Trip trip) {
        Route route = feed.routes.get(trip.route_id);
        return (route.route_long_name != null ? route.route_long_name : route.route_short_name) + " " + trip.trip_headsign;
    }

    private void setEdgeType(EdgeIteratorState edge, GtfsStorage.EdgeType edgeType) {
        edge.setFlags(encoder.setEdgeType(edge.getFlags(), edgeType));
    }

    private BitSet getValidOn(BitSet validOnDay, int dayShift) {
        if (dayShift == 0) {
            return validOnDay;
        } else {
            BitSet bitSet = new BitSet(validOnDay.length() + 1);
            for (int i=0; i<validOnDay.length(); i++) {
                if (validOnDay.get(i)) {
                    bitSet.set(i+1);
                }
            }
            return bitSet;
        }
    }

}
