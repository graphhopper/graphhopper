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
import com.conveyal.gtfs.model.*;
import com.google.common.collect.HashMultimap;
import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.IntEncodedValue;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;
import org.mapdb.Fun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static com.conveyal.gtfs.model.Entity.Writer.convertToGtfsTime;
import static java.time.temporal.ChronoUnit.DAYS;

class GtfsReader {

    private LocalDate startDate;
    private LocalDate endDate;

    static class TripWithStopTimes {
        TripWithStopTimes(Trip trip, List<StopTime> stopTimes, BitSet validOnDay, Set<Integer> cancelledArrivals, Set<Integer> cancelledDepartures) {
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
    private final Map<String, Map<GtfsStorageI.PlatformDescriptor, NavigableMap<Integer, Integer>>> departureTimelinesByStop = new HashMap<>();
    private final Map<String, Map<GtfsStorageI.PlatformDescriptor, NavigableMap<Integer, Integer>>> arrivalTimelinesByStop = new HashMap<>();
    private final PtFlagEncoder encoder;
    private final BooleanEncodedValue accessEnc;
    private final IntEncodedValue timeEnc;
    private final IntEncodedValue validityIdEnc;

    GtfsReader(String id, Graph graph, GtfsStorageI gtfsStorage, PtFlagEncoder encoder, LocationIndex walkNetworkIndex) {
        this.id = id;
        this.graph = graph;
        this.gtfsStorage = gtfsStorage;
        this.nodeAccess = graph.getNodeAccess();
        this.walkNetworkIndex = walkNetworkIndex;
        this.encoder = encoder;
        this.accessEnc = encoder.getAccessEnc();
        this.timeEnc = encoder.getTimeEnc();
        this.validityIdEnc = encoder.getValidityIdEnc();
        this.feed = this.gtfsStorage.getGtfsFeeds().get(id);
        this.transfers = this.gtfsStorage.getTransfers().get(id);
        this.i = graph.getNodes();
        this.startDate = feed.getStartDate();
        this.endDate = feed.getEndDate();
    }

    void connectStopsToStreetNetwork() {
        FlagEncoder footEncoder = ((GraphHopperStorage) graph).getEncodingManager().getEncoder("foot");
        final EdgeFilter filter = DefaultEdgeFilter.allEdges(footEncoder);
        for (Stop stop : feed.stops.values()) {
            if (stop.location_type == 0) { // Only stops. Not interested in parent stations for now.
                QueryResult locationQueryResult = walkNetworkIndex.findClosest(stop.stop_lat, stop.stop_lon, filter);
                int streetNode;
                if (!locationQueryResult.isValid()) {
                    streetNode = i++;
                    nodeAccess.setNode(streetNode, stop.stop_lat, stop.stop_lon);
                    EdgeIteratorState edge = graph.edge(streetNode, streetNode);
                    edge.set(accessEnc, true).setReverse(accessEnc, false);
                    edge.set(footEncoder.getAccessEnc(), true).setReverse(footEncoder.getAccessEnc(), false);
                    edge.set(footEncoder.getAverageSpeedEnc(), 5.0);
                } else {
                    streetNode = locationQueryResult.getClosestNode();
                }
                gtfsStorage.getStationNodes().put(stop.stop_id, streetNode);
            }
        }
    }

    void buildPtNetwork() {
        gtfsStorage.getFares().putAll(feed.fares);
        transfers = new Transfers(feed);
        gtfsStorage.getTransfers().put(id, transfers);
        createTrips();
        wireUpStops();
        insertTransfers();
    }

    private void createTrips() {
        HashMultimap<String, Trip> blockTrips = HashMultimap.create();
        for (Trip trip : feed.trips.values()) {
            if (trip.block_id != null) {
                blockTrips.put(trip.block_id, trip);
            } else {
                blockTrips.put("non-block-trip" + trip.trip_id, trip);
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
                        feed.getInterpolatedStopTimesForTrip(trip.trip_id).forEach(stopTimes::add);
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
    }

    private void wireUpStops() {
        arrivalTimelinesByStop.forEach((stopId, arrivalTimelines) -> {
            int streetNode = gtfsStorage.getStationNodes().get(stopId);
            Stop stop = feed.stops.get(stopId);
            arrivalTimelines.forEach(((platformDescriptor, arrivalTimeline) ->
                    wireUpArrivalTimeline(streetNode, stop, arrivalTimeline, routeType(platformDescriptor), platformDescriptor)));
        });
        departureTimelinesByStop.forEach((stopId, departureTimelines) -> {
            int streetNode = gtfsStorage.getStationNodes().get(stopId);
            Stop stop = feed.stops.get(stopId);
            departureTimelines.forEach(((platformDescriptor, departureTimeline) ->
                    wireUpDepartureTimeline(streetNode, stop, departureTimeline, routeType(platformDescriptor), platformDescriptor)));
        });
    }

    private void insertTransfers() {
        departureTimelinesByStop.forEach((toStopId, departureTimelines) ->
                departureTimelines.forEach(((platformDescriptor, departureTimeline) ->
                        insertTransfers(toStopId, routeIdOrNull(platformDescriptor), departureTimeline))));
    }


    private void insertTransfers(String toStopId, String toRouteId, NavigableMap<Integer, Integer> departureTimeline) {
        final Optional<Transfer> withinStationTransfer = transfers.getTransfersToStop(toStopId, toRouteId).stream().filter(t -> t.from_stop_id.equals(toStopId)).findAny();
        if (!withinStationTransfer.isPresent()) {
            insertInboundTransfers(toStopId, null, 0, departureTimeline);
        }
        transfers.getTransfersToStop(toStopId, toRouteId).forEach(transfer ->
                insertInboundTransfers(transfer.from_stop_id, transfer.from_route_id, transfer.min_transfer_time, departureTimeline));
    }

    void wireUpAdditionalDeparturesAndArrivals(ZoneId zoneId) {
        departureTimelinesByStop.forEach((stopId, departureTimelines) -> {
            int stationNode = gtfsStorage.getStationNodes().get(stopId);
            Stop stop = feed.stops.get(stopId);
            departureTimelines.forEach(((platformDescriptor, timeline) ->
                    wireUpOrPatchDepartureTimeline(zoneId, stationNode, stop, timeline, platformDescriptor)));
        });
        arrivalTimelinesByStop.forEach((stopId, arrivalTimelines) -> {
            int stationNode = gtfsStorage.getStationNodes().get(stopId);
            Stop stop = feed.stops.get(stopId);
            arrivalTimelines.forEach(((platformDescriptor, timeline) ->
                    wireUpOrPatchArrivalTimeline(zoneId, stationNode, stop, routeIdOrNull(platformDescriptor), timeline, platformDescriptor)));
        });
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
            addTrip(zoneId, time, arrivalNodes, trip, tripDescriptor.build(), frequencyBased);
        }
    }

    private static class TripWithStopTimeAndArrivalNode {
        TripWithStopTimes tripWithStopTimes;
        int arrivalNode;
        int arrivalTime;
    }

    void addTrip(ZoneId zoneId, int time, List<TripWithStopTimeAndArrivalNode> arrivalNodes, TripWithStopTimes trip, GtfsRealtime.TripDescriptor tripDescriptor, boolean frequencyBased) {
        IntArrayList boardEdges = new IntArrayList();
        IntArrayList alightEdges = new IntArrayList();
        StopTime prev = null;
        int arrivalNode = -1;
        int arrivalTime = -1;
        int departureNode = -1;
        for (StopTime stopTime : trip.stopTimes) {
            Stop stop = feed.stops.get(stopTime.stop_id);
            arrivalNode = i++;
            nodeAccess.setNode(arrivalNode, stop.stop_lat, stop.stop_lon);
            nodeAccess.setAdditionalNodeField(arrivalNode, NodeType.INTERNAL_PT.ordinal());
            arrivalTime = stopTime.arrival_time + time;
            if (prev != null) {
                Stop fromStop = feed.stops.get(prev.stop_id);
                double distance = distCalc.calcDist(
                        fromStop.stop_lat,
                        fromStop.stop_lon,
                        stop.stop_lat,
                        stop.stop_lon);
                EdgeIteratorState edge = graph.edge(departureNode, arrivalNode);
                edge.setDistance(distance);
                edge.set(accessEnc, true).setReverse(accessEnc, false);
                edge.setName(stop.stop_name);
                setEdgeTypeAndClearDistance(edge, GtfsStorage.EdgeType.HOP);
                edge.set(timeEnc, stopTime.arrival_time - prev.departure_time);
                gtfsStorage.getStopSequences().put(edge.getEdge(), stopTime.stop_sequence);
            }
            Route route = feed.routes.get(trip.trip.route_id);
            Map<GtfsStorageI.PlatformDescriptor, NavigableMap<Integer, Integer>> departureTimelines = departureTimelinesByStop.computeIfAbsent(stopTime.stop_id, s -> new HashMap<>());
            NavigableMap<Integer, Integer> departureTimeline;
            if (transfers.hasNoRouteSpecificDepartureTransferRules(stopTime.stop_id)) {
                departureTimeline = departureTimelines.computeIfAbsent(GtfsStorageI.PlatformDescriptor.routeType(route.route_type), s -> new TreeMap<>());
            } else {
                departureTimeline = departureTimelines.computeIfAbsent(GtfsStorageI.PlatformDescriptor.route(route.route_id), s -> new TreeMap<>());
            }
            int departureTimelineNode = departureTimeline.computeIfAbsent((stopTime.departure_time + time) % (24 * 60 * 60), t -> {
                final int _departureTimelineNode = i++;
                nodeAccess.setNode(_departureTimelineNode, stop.stop_lat, stop.stop_lon);
                nodeAccess.setAdditionalNodeField(_departureTimelineNode, NodeType.INTERNAL_PT.ordinal());
                return _departureTimelineNode;
            });

            Map<GtfsStorageI.PlatformDescriptor, NavigableMap<Integer, Integer>> arrivalTimelines = arrivalTimelinesByStop.computeIfAbsent(stopTime.stop_id, s -> new HashMap<>());
            NavigableMap<Integer, Integer> arrivalTimeline;
            if (transfers.hasNoRouteSpecificArrivalTransferRules(stopTime.stop_id)) {
                arrivalTimeline = arrivalTimelines.computeIfAbsent(GtfsStorageI.PlatformDescriptor.routeType(route.route_type), s -> new TreeMap<>());
            } else {
                arrivalTimeline = arrivalTimelines.computeIfAbsent(GtfsStorageI.PlatformDescriptor.route(route.route_id), s -> new TreeMap<>());
            }
            int arrivalTimelineNode = arrivalTimeline.computeIfAbsent((stopTime.arrival_time + time) % (24 * 60 * 60), t -> {
                final int _arrivalTimelineNode = i++;
                nodeAccess.setNode(_arrivalTimelineNode, stop.stop_lat, stop.stop_lon);
                nodeAccess.setAdditionalNodeField(_arrivalTimelineNode, NodeType.INTERNAL_PT.ordinal());
                return _arrivalTimelineNode;
            });
            departureNode = i++;
            nodeAccess.setNode(departureNode, stop.stop_lat, stop.stop_lon);
            nodeAccess.setAdditionalNodeField(departureNode, NodeType.INTERNAL_PT.ordinal());
            int dayShift = stopTime.departure_time / (24 * 60 * 60);
            GtfsStorage.Validity validOn = new GtfsStorage.Validity(getValidOn(trip.validOnDay, dayShift), zoneId, startDate);
            int validityId;
            if (gtfsStorage.getOperatingDayPatterns().containsKey(validOn)) {
                validityId = gtfsStorage.getOperatingDayPatterns().get(validOn);
            } else {
                validityId = gtfsStorage.getOperatingDayPatterns().size();
                gtfsStorage.getOperatingDayPatterns().put(validOn, validityId);
            }

            EdgeIteratorState boardEdge = graph.edge(departureTimelineNode, departureNode);
            boardEdge.setName(getRouteName(feed, trip.trip));
            setEdgeTypeAndClearDistance(boardEdge, GtfsStorage.EdgeType.BOARD);
            boardEdge.set(accessEnc, true).setReverse(accessEnc, false);
            while (boardEdges.size() < stopTime.stop_sequence) {
                boardEdges.add(-1); // Padding, so that index == stop_sequence
            }
            boardEdges.add(boardEdge.getEdge());
            gtfsStorage.getStopSequences().put(boardEdge.getEdge(), stopTime.stop_sequence);
            gtfsStorage.getTripDescriptors().put(boardEdge.getEdge(), tripDescriptor.toByteArray());
            boardEdge.set(validityIdEnc, validityId);
            boardEdge.set(encoder.getTransfersEnc(), 1);

            EdgeIteratorState alightEdge = graph.edge(arrivalNode, arrivalTimelineNode);
            alightEdge.setName(getRouteName(feed, trip.trip));
            setEdgeTypeAndClearDistance(alightEdge, GtfsStorage.EdgeType.ALIGHT);
            alightEdge.set(accessEnc, true).setReverse(accessEnc, false);
            while (alightEdges.size() < stopTime.stop_sequence) {
                alightEdges.add(-1);
            }
            alightEdges.add(alightEdge.getEdge());
            gtfsStorage.getStopSequences().put(alightEdge.getEdge(), stopTime.stop_sequence);
            gtfsStorage.getTripDescriptors().put(alightEdge.getEdge(), tripDescriptor.toByteArray());
            alightEdge.set(validityIdEnc, validityId);

            EdgeIteratorState dwellEdge = graph.edge(arrivalNode, departureNode);
            dwellEdge.set(accessEnc, true).setReverse(accessEnc, false);
            dwellEdge.setName(getRouteName(feed, trip.trip));
            setEdgeTypeAndClearDistance(dwellEdge, GtfsStorage.EdgeType.DWELL);
            dwellEdge.set(timeEnc, stopTime.departure_time - stopTime.arrival_time);

            if (prev == null) {
                insertInboundBlockTransfers(arrivalNodes, tripDescriptor, departureNode, stopTime.departure_time + time, stopTime, stop, validOn, zoneId);
            }
            prev = stopTime;
        }
        gtfsStorage.getBoardEdgesForTrip().put(GtfsStorage.tripKey(tripDescriptor, frequencyBased), boardEdges.toArray());
        gtfsStorage.getAlightEdgesForTrip().put(GtfsStorage.tripKey(tripDescriptor, frequencyBased), alightEdges.toArray());
        TripWithStopTimeAndArrivalNode tripWithStopTimeAndArrivalNode = new TripWithStopTimeAndArrivalNode();
        tripWithStopTimeAndArrivalNode.tripWithStopTimes = trip;
        tripWithStopTimeAndArrivalNode.arrivalNode = arrivalNode;
        tripWithStopTimeAndArrivalNode.arrivalTime = arrivalTime;
        arrivalNodes.add(tripWithStopTimeAndArrivalNode);
    }

    private void wireUpDepartureTimeline(int streetNode, Stop stop, NavigableMap<Integer, Integer> departureTimeline, int route_type, GtfsStorageI.PlatformDescriptor platformDescriptorIfStatic) {
        nodeAccess.setNode(i++, stop.stop_lat, stop.stop_lon);
        int stopEnterNode = i - 1;
        nodeAccess.setAdditionalNodeField(stopEnterNode, NodeType.STOP_ENTER_NODE.ordinal());
        EdgeIteratorState entryEdge = graph.edge(streetNode, stopEnterNode);
        entryEdge.set(accessEnc, true).setReverse(accessEnc, false);
        setEdgeTypeAndClearDistance(entryEdge, GtfsStorage.EdgeType.ENTER_PT);
        entryEdge.set(encoder.getValidityIdEnc(), route_type);
        entryEdge.setName(stop.stop_name);
        if (platformDescriptorIfStatic != null) {
            gtfsStorage.getRoutes().put(entryEdge.getEdge(), platformDescriptorIfStatic);
        }
        wireUpAndConnectTimeline(stop, stopEnterNode, departureTimeline, GtfsStorage.EdgeType.ENTER_TIME_EXPANDED_NETWORK, GtfsStorage.EdgeType.WAIT);
    }

    private void wireUpArrivalTimeline(int streetNode, Stop stop, NavigableMap<Integer, Integer> arrivalTimeline, int route_type, GtfsStorageI.PlatformDescriptor platformDescriptorIfStatic) {
        nodeAccess.setNode(i++, stop.stop_lat, stop.stop_lon);
        int stopExitNode = i - 1;
        nodeAccess.setAdditionalNodeField(stopExitNode, NodeType.STOP_EXIT_NODE.ordinal());
        EdgeIteratorState exitEdge = graph.edge(stopExitNode, streetNode);
        exitEdge.set(accessEnc, true).setReverse(accessEnc, false);
        setEdgeTypeAndClearDistance(exitEdge, GtfsStorage.EdgeType.EXIT_PT);
        exitEdge.set(encoder.getValidityIdEnc(), route_type);
        exitEdge.setName(stop.stop_name);
        if (platformDescriptorIfStatic != null) {
            gtfsStorage.getRoutes().put(exitEdge.getEdge(), platformDescriptorIfStatic);
        }
        wireUpAndConnectTimeline(stop, stopExitNode, arrivalTimeline, GtfsStorage.EdgeType.LEAVE_TIME_EXPANDED_NETWORK, GtfsStorage.EdgeType.WAIT_ARRIVAL);
    }

    private void wireUpOrPatchDepartureTimeline(ZoneId zoneId, int stationNode, Stop stop, NavigableMap<Integer, Integer> timeline, GtfsStorageI.PlatformDescriptor route) {
        int platformEnterNode = findPlatformNode(stationNode, route, GtfsStorage.EdgeType.ENTER_PT);
        if (platformEnterNode != -1) {
            patchDepartureTimeline(zoneId, timeline, platformEnterNode);
        } else {
            wireUpDepartureTimeline(stationNode, stop, timeline, 0, null);
        }
    }

    private void wireUpOrPatchArrivalTimeline(ZoneId zoneId, int stationNode, Stop stop, String routeId, NavigableMap<Integer, Integer> timeline, GtfsStorageI.PlatformDescriptor route) {
        int platformExitNode = findPlatformNode(stationNode, route, GtfsStorage.EdgeType.EXIT_PT);
        if (platformExitNode != -1) {
            patchArrivalTimeline(zoneId, timeline, platformExitNode);
        } else {
            wireUpArrivalTimeline(stationNode, stop, timeline, 0, null);
        }
        final Optional<Transfer> withinStationTransfer = transfers.getTransfersFromStop(stop.stop_id, routeId).stream().filter(t -> t.from_stop_id.equals(stop.stop_id)).findAny();
        if (!withinStationTransfer.isPresent()) {
            insertOutboundTransfers(stop.stop_id, null, 0, timeline);
        }
        transfers.getTransfersFromStop(stop.stop_id, routeId).forEach(transfer ->
                insertOutboundTransfers(transfer.from_stop_id, transfer.from_route_id, transfer.min_transfer_time, timeline));
    }

    private void patchDepartureTimeline(ZoneId zoneId, NavigableMap<Integer, Integer> timeline, int platformNode) {
        NavigableMap<Integer, Integer> staticDepartureTimelineForRoute = findDepartureTimelineForPlatform(platformNode);
        timeline.forEach((time, node) -> {
            SortedMap<Integer, Integer> headMap = staticDepartureTimelineForRoute.headMap(time);
            if (!headMap.isEmpty()) {
                EdgeIteratorState edge = graph.edge(headMap.get(headMap.lastKey()), node);
                edge.set(accessEnc, true).setReverse(accessEnc, false);
                setEdgeTypeAndClearDistance(edge, GtfsStorage.EdgeType.WAIT);
                edge.set(timeEnc, time - headMap.lastKey());
            }
            SortedMap<Integer, Integer> tailMap = staticDepartureTimelineForRoute.tailMap(time);
            if (!tailMap.isEmpty()) {
                EdgeIteratorState edge = graph.edge(node, tailMap.get(tailMap.firstKey()));
                edge.set(accessEnc, true).setReverse(accessEnc, false);
                setEdgeTypeAndClearDistance(edge, GtfsStorage.EdgeType.WAIT);
                edge.set(timeEnc, tailMap.firstKey() - time);
            }

            EdgeIteratorState edge = graph.edge(platformNode, node);
            edge.set(accessEnc, true).setReverse(accessEnc, false);
            setEdgeTypeAndClearDistance(edge, GtfsStorage.EdgeType.ENTER_TIME_EXPANDED_NETWORK);
            edge.set(timeEnc, time);
            setFeedIdWithTimezone(edge, new GtfsStorage.FeedIdWithTimezone(id, zoneId));
        });
    }

    private void patchArrivalTimeline(ZoneId zoneId, NavigableMap<Integer, Integer> timeline, int platformExitNode) {
        timeline.forEach((time, node) -> {
            EdgeIteratorState edge = graph.edge(node, platformExitNode);
            edge.set(accessEnc, true).setReverse(accessEnc, false);
            setEdgeTypeAndClearDistance(edge, GtfsStorage.EdgeType.LEAVE_TIME_EXPANDED_NETWORK);
            edge.set(timeEnc, time);
            setFeedIdWithTimezone(edge, new GtfsStorage.FeedIdWithTimezone(id, zoneId));
        });
    }

    private NavigableMap<Integer, Integer> findDepartureTimelineForPlatform(int platformEnterNode) {
        TreeMap<Integer, Integer> result = new TreeMap<>();
        if (platformEnterNode == -1) {
            return result;
        }
        EdgeIterator edge = graph.getBaseGraph().createEdgeExplorer(DefaultEdgeFilter.outEdges(encoder)).setBaseNode(platformEnterNode);
        while (edge.next()) {
            if (encoder.getEdgeType(edge) == GtfsStorage.EdgeType.ENTER_TIME_EXPANDED_NETWORK) {
                result.put(edge.get(timeEnc), edge.getAdjNode());
            }
        }
        return result;
    }

    private int findPlatformNode(int stationNode, GtfsStorageI.PlatformDescriptor platformDescriptor, GtfsStorage.EdgeType edgeType) {
        DefaultEdgeFilter filter;
        if (edgeType == GtfsStorage.EdgeType.ENTER_PT) {
            filter = DefaultEdgeFilter.outEdges(encoder);
        } else if (edgeType == GtfsStorage.EdgeType.EXIT_PT) {
            filter = DefaultEdgeFilter.inEdges(encoder);
        } else {
            throw new RuntimeException();
        }
        EdgeIterator i = graph.getBaseGraph().createEdgeExplorer(filter).setBaseNode(stationNode);
        while (i.next()) {
            if (encoder.getEdgeType(i) == edgeType) {
                if (platformDescriptor.equals(gtfsStorage.getRoutes().get(i.getEdge()))) {
                    return i.getAdjNode();
                }
            }
        }
        return -1;
    }

    int addDelayedBoardEdge(ZoneId zoneId, GtfsRealtime.TripDescriptor tripDescriptor, int stopSequence, int departureTime, int departureNode, BitSet validOnDay) {
        Trip trip = feed.trips.get(tripDescriptor.getTripId());
        StopTime stopTime = feed.stop_times.get(new Fun.Tuple2(tripDescriptor.getTripId(), stopSequence));
        Stop stop = feed.stops.get(stopTime.stop_id);
        Map<GtfsStorageI.PlatformDescriptor, NavigableMap<Integer, Integer>> departureTimelineNodesByRoute = departureTimelinesByStop.computeIfAbsent(stopTime.stop_id, s -> new HashMap<>());
        NavigableMap<Integer, Integer> departureTimelineNodes = departureTimelineNodesByRoute.computeIfAbsent(GtfsStorageI.PlatformDescriptor.route(trip.route_id), s -> new TreeMap<>());
        int departureTimelineNode = departureTimelineNodes.computeIfAbsent(departureTime % (24 * 60 * 60), t -> {
            final int _departureTimelineNode = i++;
            nodeAccess.setNode(_departureTimelineNode, stop.stop_lat, stop.stop_lon);
            nodeAccess.setAdditionalNodeField(_departureTimelineNode, NodeType.INTERNAL_PT.ordinal());
            return _departureTimelineNode;
        });

        int dayShift = departureTime / (24 * 60 * 60);
        GtfsStorage.Validity validOn = new GtfsStorage.Validity(getValidOn(validOnDay, dayShift), zoneId, startDate);
        int validityId;
        if (gtfsStorage.getOperatingDayPatterns().containsKey(validOn)) {
            validityId = gtfsStorage.getOperatingDayPatterns().get(validOn);
        } else {
            validityId = gtfsStorage.getOperatingDayPatterns().size();
            gtfsStorage.getOperatingDayPatterns().put(validOn, validityId);
        }

        EdgeIteratorState boardEdge = graph.edge(departureTimelineNode, departureNode);
        boardEdge.set(accessEnc, true).setReverse(accessEnc, false);
        boardEdge.setName(getRouteName(feed, trip));
        setEdgeTypeAndClearDistance(boardEdge, GtfsStorage.EdgeType.BOARD);
        gtfsStorage.getStopSequences().put(boardEdge.getEdge(), stopSequence);
        gtfsStorage.getTripDescriptors().put(boardEdge.getEdge(), tripDescriptor.toByteArray());
        boardEdge.set(validityIdEnc, validityId);
        boardEdge.set(encoder.getTransfersEnc(), 1);
        return boardEdge.getEdge();
    }

    private void wireUpAndConnectTimeline(Stop toStop, int platformNode, NavigableMap<Integer, Integer> timeNodes, GtfsStorage.EdgeType timeExpandedNetworkEdgeType, GtfsStorage.EdgeType waitEdgeType) {
        ZoneId zoneId = ZoneId.of(feed.agency.values().iterator().next().agency_timezone);
        int time = 0;
        int prev = -1;
        for (Map.Entry<Integer, Integer> e : timeNodes.descendingMap().entrySet()) {
            EdgeIteratorState timeExpandedNetworkEdge;
            if (timeExpandedNetworkEdgeType == GtfsStorage.EdgeType.LEAVE_TIME_EXPANDED_NETWORK) {
                timeExpandedNetworkEdge = graph.edge(e.getValue(), platformNode);
            } else if (timeExpandedNetworkEdgeType == GtfsStorage.EdgeType.ENTER_TIME_EXPANDED_NETWORK) {
                timeExpandedNetworkEdge = graph.edge(platformNode, e.getValue());
            } else {
                throw new RuntimeException();
            }
            timeExpandedNetworkEdge.set(accessEnc, true).setReverse(accessEnc, false);
            timeExpandedNetworkEdge.setName(toStop.stop_name);
            setEdgeTypeAndClearDistance(timeExpandedNetworkEdge, timeExpandedNetworkEdgeType);
            timeExpandedNetworkEdge.set(timeEnc, e.getKey());
            setFeedIdWithTimezone(timeExpandedNetworkEdge, new GtfsStorage.FeedIdWithTimezone(id, zoneId));
            if (prev != -1) {
                EdgeIteratorState waitEdge = graph.edge(e.getValue(), prev);
                waitEdge.set(accessEnc, true).setReverse(accessEnc, false);
                setEdgeTypeAndClearDistance(waitEdge, waitEdgeType);
                waitEdge.setName(toStop.stop_name);
                waitEdge.set(timeEnc, time - e.getKey());
            }
            time = e.getKey();
            prev = e.getValue();
        }
        if (!timeNodes.isEmpty()) {
            EdgeIteratorState edge = graph.edge(timeNodes.get(timeNodes.lastKey()), timeNodes.get(timeNodes.firstKey()));
            edge.set(accessEnc, true).setReverse(accessEnc, false);
            int rolloverTime = 24 * 60 * 60 - timeNodes.lastKey() + timeNodes.firstKey();
            setEdgeTypeAndClearDistance(edge, GtfsStorage.EdgeType.OVERNIGHT);
            edge.setName(toStop.stop_name);
            edge.set(timeEnc, rolloverTime);
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
        leaveTimeExpandedNetworkEdge.set(validityIdEnc, validityId);
    }

    private void insertInboundBlockTransfers(List<TripWithStopTimeAndArrivalNode> arrivalNodes, GtfsRealtime.TripDescriptor tripDescriptor, int departureNode, int departureTime, StopTime stopTime, Stop stop, GtfsStorage.Validity validOn, ZoneId zoneId) {
        BitSet accumulatorValidity = new BitSet(validOn.validity.size());
        accumulatorValidity.or(validOn.validity);
        ListIterator<TripWithStopTimeAndArrivalNode> li = arrivalNodes.listIterator(arrivalNodes.size());
        while (li.hasPrevious() && accumulatorValidity.cardinality() > 0) {
            TripWithStopTimeAndArrivalNode lastTrip = li.previous();
            int dwellTime = departureTime - lastTrip.arrivalTime;
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
                nodeAccess.setAdditionalNodeField(i - 1, NodeType.INTERNAL_PT.ordinal());
                EdgeIteratorState transferEdge = graph.edge(lastTrip.arrivalNode, i - 1);
                transferEdge.set(accessEnc, true).setReverse(accessEnc, false);
                setEdgeTypeAndClearDistance(transferEdge, GtfsStorage.EdgeType.TRANSFER);
                transferEdge.set(timeEnc, dwellTime);
                EdgeIteratorState boardEdge = graph.edge(i - 1, departureNode);
                boardEdge.set(accessEnc, true).setReverse(accessEnc, false);
                setEdgeTypeAndClearDistance(boardEdge, GtfsStorage.EdgeType.BOARD);
                boardEdge.set(validityIdEnc, blockTransferValidityId);
                gtfsStorage.getStopSequences().put(boardEdge.getEdge(), stopTime.stop_sequence);
                gtfsStorage.getTripDescriptors().put(boardEdge.getEdge(), tripDescriptor.toByteArray());
                accumulatorValidity.andNot(lastTrip.tripWithStopTimes.validOnDay);
            }
        }
    }

    private void insertInboundTransfers(String fromStopId, String from_route_id, int minimumTransferTime, NavigableMap<Integer, Integer> toStopTimelineNode) {
        int stationNode = gtfsStorage.getStationNodes().get(fromStopId);
        EdgeIterator i = graph.createEdgeExplorer().setBaseNode(stationNode);
        while (i.next()) {
            if (encoder.getEdgeType(i) == GtfsStorage.EdgeType.EXIT_PT) {
                GtfsStorageI.PlatformDescriptor routeId = gtfsStorage.getRoutes().get(i.getEdge());
                if (from_route_id == null || GtfsStorageI.PlatformDescriptor.route(from_route_id).equals(routeId)) {
                    EdgeIterator j = graph.createEdgeExplorer().setBaseNode(i.getAdjNode());
                    while (j.next()) {
                        if (encoder.getEdgeType(j) == GtfsStorage.EdgeType.LEAVE_TIME_EXPANDED_NETWORK) {
                            int arrivalTime = j.get(timeEnc);
                            SortedMap<Integer, Integer> tailSet = toStopTimelineNode.tailMap(arrivalTime + minimumTransferTime);
                            if (!tailSet.isEmpty()) {
                                EdgeIteratorState edge = graph.edge(j.getAdjNode(), tailSet.get(tailSet.firstKey()));
                                edge.set(accessEnc, true).setReverse(accessEnc, false);
                                setEdgeTypeAndClearDistance(edge, GtfsStorage.EdgeType.TRANSFER);
                                edge.set(timeEnc, tailSet.firstKey() - arrivalTime);
                            }
                        }
                    }
                }
            }
        }
    }

    private void insertOutboundTransfers(String toStopId, String toRouteId, int minimumTransferTime, NavigableMap<Integer, Integer> fromStopTimelineNodes) {
        int stationNode = gtfsStorage.getStationNodes().get(toStopId);
        EdgeIterator i = graph.getBaseGraph().createEdgeExplorer().setBaseNode(stationNode);
        while (i.next()) {
            GtfsStorage.EdgeType edgeType = encoder.getEdgeType(i);
            if (edgeType == GtfsStorage.EdgeType.ENTER_PT) {
                GtfsStorageI.PlatformDescriptor routeId = gtfsStorage.getRoutes().get(i.getEdge());
                if (toRouteId == null || routeId instanceof GtfsStorageI.RouteTypePlatform || GtfsStorageI.PlatformDescriptor.route(toRouteId).equals(routeId)) {
                    fromStopTimelineNodes.forEach((time, e) -> {
                        EdgeIterator j = graph.getBaseGraph().createEdgeExplorer().setBaseNode(i.getAdjNode());
                        while (j.next()) {
                            GtfsStorage.EdgeType edgeType2 = encoder.getEdgeType(j);
                            if (edgeType2 == GtfsStorage.EdgeType.ENTER_TIME_EXPANDED_NETWORK) {
                                int departureTime = j.get(timeEnc);
                                if (departureTime < time + minimumTransferTime) {
                                    continue;
                                }
                                EdgeIteratorState edge = graph.edge(e, j.getAdjNode());
                                edge.set(accessEnc, true).setReverse(accessEnc, false);
                                setEdgeTypeAndClearDistance(edge, GtfsStorage.EdgeType.TRANSFER);
                                edge.set(timeEnc, departureTime - time);
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

    private void setEdgeTypeAndClearDistance(EdgeIteratorState edge, GtfsStorage.EdgeType edgeType) {
        edge.setDistance(0.0);
        encoder.setEdgeType(edge, edgeType);
    }

    private BitSet getValidOn(BitSet validOnDay, int dayShift) {
        if (dayShift == 0) {
            return validOnDay;
        } else {
            BitSet bitSet = new BitSet(validOnDay.length() + 1);
            for (int i = 0; i < validOnDay.length(); i++) {
                if (validOnDay.get(i)) {
                    bitSet.set(i + 1);
                }
            }
            return bitSet;
        }
    }

    private int routeType(GtfsStorageI.PlatformDescriptor platformDescriptor) {
        if (platformDescriptor instanceof GtfsStorageI.RouteTypePlatform) {
            return ((GtfsStorageI.RouteTypePlatform) platformDescriptor).route_type;
        } else {
            return feed.routes.get(((GtfsStorageI.RoutePlatform) platformDescriptor).route_id).route_type;
        }
    }

    private String routeIdOrNull(GtfsStorageI.PlatformDescriptor platformDescriptor) {
        if (platformDescriptor instanceof GtfsStorageI.RouteTypePlatform) {
            return null;
        } else {
            return ((GtfsStorageI.RoutePlatform) platformDescriptor).route_id;
        }
    }

}
