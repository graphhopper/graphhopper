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
import com.carrotsearch.hppc.IntIntHashMap;
import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.*;
import com.google.common.collect.HashMultimap;
import com.google.transit.realtime.GtfsRealtime;
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
    private final IntIntHashMap times = new IntIntHashMap();
    private final Map<String, Map<String, NavigableMap<Integer, Integer>>> departureTimelineNodes = new HashMap<>();
    private final Map<String, Map<String, NavigableMap<Integer, Integer>>> arrivalTimelineNodes = new HashMap<>();
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
        this.startDate = feed.getStartDate();
        this.endDate = feed.getEndDate();
    }

    void readGraph() {
        gtfsStorage.getFares().putAll(feed.fares);
        transfers = new Transfers(feed);
        gtfsStorage.getTransfers().put(id, transfers);
        buildPtNetwork();
    }

    void connectStopsToStreetNetwork() {
        FlagEncoder footEncoder = ((GraphHopperStorage) graph).getEncodingManager().getEncoder("foot");
        final EdgeFilter filter = DefaultEdgeFilter.allEdges(footEncoder);
        for (Stop stop : feed.stops.values()) {
            QueryResult locationQueryResult = walkNetworkIndex.findClosest(stop.stop_lat, stop.stop_lon, filter);
            int streetNode;
            if (!locationQueryResult.isValid()) {
                streetNode = i++;
                nodeAccess.setNode(streetNode, stop.stop_lat, stop.stop_lon);
                EdgeIteratorState edge = graph.edge(streetNode, streetNode);
                edge.setFlags(encoder.setAccess(edge.getFlags(), true, false));
                edge.setFlags(footEncoder.setAccess(edge.getFlags(), true, false));
                edge.setFlags(footEncoder.setSpeed(edge.getFlags(), 5.0));
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
                    final Map<String, NavigableMap<Integer, Integer>> arrivalTimelineNodesByRoute = arrivalTimelineNodes.get(stop.stop_id);

                    arrivalTimelineNodesByRoute.forEach((routeId, timelineNodesWithTripId) -> {
                        Route route = feed.routes.get(routeId);
                        nodeAccess.setNode(i++, stop.stop_lat, stop.stop_lon);
                        int stopExitNode = i-1;
                        nodeAccess.setAdditionalNodeField(stopExitNode, NodeType.STOP_EXIT_NODE.ordinal());

                        EdgeIteratorState exitEdge = graph.edge(stopExitNode, streetNode);
                        exitEdge.setFlags(encoder.setAccess(exitEdge.getFlags(), true, false));
                        setEdgeTypeAndClearDistance(exitEdge, GtfsStorage.EdgeType.EXIT_PT);
                        exitEdge.setFlags(encoder.setValidityId(exitEdge.getFlags(), route.route_type));
                        exitEdge.setName(stop.stop_name);
                        gtfsStorage.getRoutes().put(exitEdge.getEdge(), routeId);

                        wireUpAndAndConnectArrivalTimeline(stop, routeId,stopExitNode, timelineNodesWithTripId);
                    });

                }

                if (departureTimelineNodes.containsKey(stop.stop_id)) {
                    final Map<String, NavigableMap<Integer, Integer>> departureTimelineNodesByRoute = departureTimelineNodes.get(stop.stop_id);

                    departureTimelineNodesByRoute.forEach((routeId, timelineNodesWithTripId) -> {
                        Route route = feed.routes.get(routeId);
                        nodeAccess.setNode(i++, stop.stop_lat, stop.stop_lon);
                        int stopEnterNode = i-1;
                        nodeAccess.setAdditionalNodeField(stopEnterNode, NodeType.STOP_ENTER_NODE.ordinal());

                        EdgeIteratorState entryEdge = graph.edge(streetNode, stopEnterNode);
                        entryEdge.setFlags(encoder.setAccess(entryEdge.getFlags(), true, false));
                        setEdgeTypeAndClearDistance(entryEdge, GtfsStorage.EdgeType.ENTER_PT);
                        entryEdge.setFlags(encoder.setValidityId(entryEdge.getFlags(), route.route_type));
                        entryEdge.setName(stop.stop_name);
                        gtfsStorage.getRoutes().put(entryEdge.getEdge(), routeId);

                        wireUpAndAndConnectDepartureTimeline(stop, routeId,stopEnterNode, timelineNodesWithTripId);
                    });
                }
            }
        }
        insertTransfers();
    }

    void wireUpAdditionalDepartures(ZoneId zoneId) {
        for (Stop stop : feed.stops.values()) {
            int stationNode = gtfsStorage.getStationNodes().get(stop.stop_id);
            final Map<String, NavigableMap<Integer, Integer>> departureTimelineNodesByRoute = departureTimelineNodes.getOrDefault(stop.stop_id, Collections.emptyMap());
            departureTimelineNodesByRoute.forEach((routeId, timeline) -> {
                int platformNode = findPlatformEnterNode(stationNode, routeId);
                if (platformNode != -1) {
                    NavigableMap<Integer, Integer> staticTimelineNodesForRoute = findDepartureTimelineNodesForRoute(stationNode, routeId);
                    timeline.forEach((time, node) -> {
                        SortedMap<Integer, Integer> headMap = staticTimelineNodesForRoute.headMap(time);
                        if (!headMap.isEmpty()) {
                            EdgeIteratorState edge = graph.edge(headMap.get(headMap.lastKey()), node);
                            edge.setFlags(encoder.setAccess(edge.getFlags(), true, false));
                            setEdgeTypeAndClearDistance(edge, GtfsStorage.EdgeType.WAIT);
                            edge.setFlags(encoder.setTime(edge.getFlags(), time - headMap.lastKey()));
                        }
                        SortedMap<Integer, Integer> tailMap = staticTimelineNodesForRoute.tailMap(time);
                        if (!tailMap.isEmpty()) {
                            EdgeIteratorState edge = graph.edge(node, tailMap.get(tailMap.firstKey()));
                            edge.setFlags(encoder.setAccess(edge.getFlags(), true, false));
                            setEdgeTypeAndClearDistance(edge, GtfsStorage.EdgeType.WAIT);
                            edge.setFlags(encoder.setTime(edge.getFlags(), tailMap.firstKey() - time));
                        }

                        EdgeIteratorState edge = graph.edge(platformNode, node);
                        edge.setFlags(encoder.setAccess(edge.getFlags(), true, false));
                        setEdgeTypeAndClearDistance(edge, GtfsStorage.EdgeType.ENTER_TIME_EXPANDED_NETWORK);
                        edge.setFlags(encoder.setTime(edge.getFlags(), time));
                        setFeedIdWithTimezone(edge, new GtfsStorage.FeedIdWithTimezone(id, zoneId));
                    });
                } else {
                    nodeAccess.setNode(i++, stop.stop_lat, stop.stop_lon);
                    int stopEnterNode = i-1;
                    nodeAccess.setAdditionalNodeField(stopEnterNode, NodeType.STOP_ENTER_NODE.ordinal());
                    EdgeIteratorState entryEdge = graph.edge(stationNode, stopEnterNode);
                    entryEdge.setFlags(encoder.setAccess(entryEdge.getFlags(), true, false));
                    setEdgeTypeAndClearDistance(entryEdge, GtfsStorage.EdgeType.ENTER_PT);
                    entryEdge.setName(stop.stop_name);
                    wireUpAndAndConnectDepartureTimeline(stop, routeId,stopEnterNode, timeline);
                }
            });
            final Map<String, NavigableMap<Integer, Integer>> arrivalTimelineNodesByRoute = arrivalTimelineNodes.getOrDefault(stop.stop_id, Collections.emptyMap());
            arrivalTimelineNodesByRoute.forEach((routeId, timeline) -> {
                int platformNode = findPlatformExitNode(stationNode, routeId);
                if (platformNode != -1) {
                    timeline.forEach((time, node) -> {
                        EdgeIteratorState edge = graph.edge(node, platformNode);
                        edge.setFlags(encoder.setAccess(edge.getFlags(), true, false));
                        setEdgeTypeAndClearDistance(edge, GtfsStorage.EdgeType.LEAVE_TIME_EXPANDED_NETWORK);
                        edge.setFlags(encoder.setTime(edge.getFlags(), time));
                        setFeedIdWithTimezone(edge, new GtfsStorage.FeedIdWithTimezone(id, zoneId));
                    });
                } else {
                    nodeAccess.setNode(i++, stop.stop_lat, stop.stop_lon);
                    int stopExitNode = i-1;
                    nodeAccess.setAdditionalNodeField(stopExitNode, NodeType.STOP_EXIT_NODE.ordinal());
                    EdgeIteratorState exitEdge = graph.edge(stopExitNode, stationNode);
                    exitEdge.setFlags(encoder.setAccess(exitEdge.getFlags(), true, false));
                    setEdgeTypeAndClearDistance(exitEdge, GtfsStorage.EdgeType.EXIT_PT);
                    exitEdge.setName(stop.stop_name);
                    wireUpAndAndConnectArrivalTimeline(stop, routeId,stopExitNode, timeline);
                }
                final Optional<Transfer> withinStationTransfer = transfers.getTransfersFromStop(stop.stop_id, routeId).stream().filter(t -> t.from_stop_id.equals(stop.stop_id)).findAny();
                if (!withinStationTransfer.isPresent()) {
                    insertOutboundTransfers(stop.stop_id, null, 0, timeline);
                }
                transfers.getTransfersFromStop(stop.stop_id, routeId).forEach(transfer -> {
                    insertOutboundTransfers(transfer.from_stop_id, transfer.from_route_id, transfer.min_transfer_time, timeline);
                });
            });
        }
    }

    private NavigableMap<Integer, Integer> findDepartureTimelineNodesForRoute(int stationNode, String routeId) {
        TreeMap<Integer, Integer> result = new TreeMap<>();
        int node = findPlatformEnterNode(stationNode, routeId);
        if (node == -1) {
            return result;
        }
        EdgeIterator edge = graph.getBaseGraph().createEdgeExplorer(DefaultEdgeFilter.outEdges(encoder)).setBaseNode(node);
        while (edge.next()) {
            if (encoder.getEdgeType(edge.getFlags()) == GtfsStorage.EdgeType.ENTER_TIME_EXPANDED_NETWORK) {
                result.put((int) encoder.getTime(edge.getFlags()), edge.getAdjNode());
            }
        }
        return result;
    }

    private int findPlatformEnterNode(int stationNode, String routeId) {
        EdgeIterator i = graph.getBaseGraph().createEdgeExplorer(DefaultEdgeFilter.outEdges(encoder)).setBaseNode(stationNode);
        while (i.next()) {
            GtfsStorage.EdgeType edgeType = encoder.getEdgeType(i.getFlags());
            if (edgeType == GtfsStorage.EdgeType.ENTER_PT) {
                if (routeId.equals(gtfsStorage.getRoutes().get(i.getEdge()))) {
                    return i.getAdjNode();
                }
            }
        }
        return -1;
    }

    private int findPlatformExitNode(int stationNode, String routeId) {
        EdgeIterator i = graph.getBaseGraph().createEdgeExplorer(DefaultEdgeFilter.inEdges(encoder)).setBaseNode(stationNode);
        while (i.next()) {
            GtfsStorage.EdgeType edgeType = encoder.getEdgeType(i.getFlags());
            if (edgeType == GtfsStorage.EdgeType.EXIT_PT) {
                if (routeId.equals(gtfsStorage.getRoutes().get(i.getEdge()))) {
                    return i.getAdjNode();
                }
            }
        }
        return -1;
    }

    void insertTransfers() {
        departureTimelineNodes.forEach((toStopId, timelineNodesWithTripId) -> {
            timelineNodesWithTripId.forEach((toRouteId, timelineNodesByRoute) -> {
                final Optional<Transfer> withinStationTransfer = transfers.getTransfersToStop(toStopId, toRouteId).stream().filter(t -> t.from_stop_id.equals(toStopId)).findAny();
                if (!withinStationTransfer.isPresent()) {
                    insertInboundTransfers(toStopId, null, 0, timelineNodesByRoute);
                }
                transfers.getTransfersToStop(toStopId, toRouteId).forEach(transfer -> {
                    insertInboundTransfers(transfer.from_stop_id, transfer.from_route_id, transfer.min_transfer_time, timelineNodesByRoute);
                });
            });
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
    }

    void addTrip(ZoneId zoneId, int time, List<TripWithStopTimeAndArrivalNode> arrivalNodes, TripWithStopTimes trip, GtfsRealtime.TripDescriptor tripDescriptor, boolean frequencyBased) {
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
                EdgeIteratorState edge = graph.edge(departureNode, arrivalNode);
                edge.setDistance(distance);
                edge.setFlags(encoder.setAccess(edge.getFlags(), true, false));
                edge.setName(stop.stop_name);
                setEdgeTypeAndClearDistance(edge, GtfsStorage.EdgeType.HOP);
                edge.setFlags(encoder.setTime(edge.getFlags(), stopTime.arrival_time - prev.departure_time));
                gtfsStorage.getStopSequences().put(edge.getEdge(), stopTime.stop_sequence);
            }
            Map<String, NavigableMap<Integer, Integer>> departureTimelineNodesByRoute = departureTimelineNodes.computeIfAbsent(stopTime.stop_id, s -> new HashMap<>());
            NavigableMap<Integer, Integer> departureTimelineNodes = departureTimelineNodesByRoute.computeIfAbsent(trip.trip.route_id, s -> new TreeMap<>());
            int departureTimelineNode = departureTimelineNodes.computeIfAbsent((stopTime.departure_time + time) % (24 * 60 * 60), t -> {
                final int _departureTimelineNode = i++;
                nodeAccess.setNode(_departureTimelineNode, stop.stop_lat, stop.stop_lon);
                nodeAccess.setAdditionalNodeField(_departureTimelineNode, NodeType.INTERNAL_PT.ordinal());
                times.put(_departureTimelineNode, stopTime.departure_time + time);
                return _departureTimelineNode;
            });
            Map<String, NavigableMap<Integer, Integer>> arrivalTimelineNodesByRoute = arrivalTimelineNodes.computeIfAbsent(stopTime.stop_id, s -> new HashMap<>());
            NavigableMap<Integer, Integer> arrivalTimelineNodes = arrivalTimelineNodesByRoute.computeIfAbsent(trip.trip.route_id, s -> new TreeMap<>());
            int arrivalTimelineNode = arrivalTimelineNodes.computeIfAbsent((stopTime.arrival_time + time) % (24 * 60 * 60), t -> {
                final int _arrivalTimelineNode = i++;
                nodeAccess.setNode(_arrivalTimelineNode, stop.stop_lat, stop.stop_lon);
                nodeAccess.setAdditionalNodeField(_arrivalTimelineNode, NodeType.INTERNAL_PT.ordinal());
                times.put(_arrivalTimelineNode, stopTime.arrival_time + time);
                return _arrivalTimelineNode;
            });
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

            EdgeIteratorState boardEdge = graph.edge(departureTimelineNode, departureNode);
            boardEdge.setFlags(encoder.setAccess(boardEdge.getFlags(), true, false));
            boardEdge.setName(getRouteName(feed, trip.trip));
            setEdgeTypeAndClearDistance(boardEdge, GtfsStorage.EdgeType.BOARD);
            while (boardEdges.size() < stopTime.stop_sequence) {
                boardEdges.add(-1); // Padding, so that index == stop_sequence
            }
            boardEdges.add(boardEdge.getEdge());
            gtfsStorage.getStopSequences().put(boardEdge.getEdge(), stopTime.stop_sequence);
            gtfsStorage.getTripDescriptors().put(boardEdge.getEdge(), tripDescriptor.toByteArray());
            boardEdge.setFlags(encoder.setValidityId(boardEdge.getFlags(), validityId));
            boardEdge.setFlags(encoder.setTransfers(boardEdge.getFlags(), 1));

            EdgeIteratorState alightEdge = graph.edge(arrivalNode, arrivalTimelineNode);
            alightEdge.setFlags(encoder.setAccess(alightEdge.getFlags(), true, false));
            alightEdge.setName(getRouteName(feed, trip.trip));
            setEdgeTypeAndClearDistance(alightEdge, GtfsStorage.EdgeType.ALIGHT);
            while (alightEdges.size() < stopTime.stop_sequence) {
                alightEdges.add(-1);
            }
            alightEdges.add(alightEdge.getEdge());
            gtfsStorage.getStopSequences().put(alightEdge.getEdge(), stopTime.stop_sequence);
            gtfsStorage.getTripDescriptors().put(alightEdge.getEdge(), tripDescriptor.toByteArray());
            alightEdge.setFlags(encoder.setValidityId(alightEdge.getFlags(), validityId));

            EdgeIteratorState dwellEdge = graph.edge(arrivalNode, departureNode);
            dwellEdge.setFlags(encoder.setAccess(dwellEdge.getFlags(), true, false));
            dwellEdge.setName(getRouteName(feed, trip.trip));
            setEdgeTypeAndClearDistance(dwellEdge, GtfsStorage.EdgeType.DWELL);
            dwellEdge.setFlags(encoder.setTime(dwellEdge.getFlags(), stopTime.departure_time - stopTime.arrival_time));
            if (prev == null) {
                insertInboundBlockTransfers(arrivalNodes, tripDescriptor, departureNode, stopTime, stop, validOn, zoneId);
            }
            prev = stopTime;
        }
        gtfsStorage.getBoardEdgesForTrip().put(GtfsStorage.tripKey(tripDescriptor, frequencyBased), boardEdges.toArray());
        gtfsStorage.getAlightEdgesForTrip().put(GtfsStorage.tripKey(tripDescriptor, frequencyBased), alightEdges.toArray());
        TripWithStopTimeAndArrivalNode tripWithStopTimeAndArrivalNode = new TripWithStopTimeAndArrivalNode();
        tripWithStopTimeAndArrivalNode.tripWithStopTimes = trip;
        tripWithStopTimeAndArrivalNode.arrivalNode = arrivalNode;
        arrivalNodes.add(tripWithStopTimeAndArrivalNode);
    }

    int addDelayedBoardEdge(ZoneId zoneId, GtfsRealtime.TripDescriptor tripDescriptor, int stopSequence, int departureTime, int departureNode, BitSet validOnDay) {
        Trip trip = feed.trips.get(tripDescriptor.getTripId());
        StopTime stopTime = feed.stop_times.get(new Fun.Tuple2(tripDescriptor.getTripId(), stopSequence));
        Stop stop = feed.stops.get(stopTime.stop_id);
        Map<String, NavigableMap<Integer, Integer>> departureTimelineNodesByRoute = departureTimelineNodes.computeIfAbsent(stopTime.stop_id, s -> new HashMap<>());
        NavigableMap<Integer, Integer> departureTimelineNodes = departureTimelineNodesByRoute.computeIfAbsent(trip.route_id, s -> new TreeMap<>());
        int departureTimelineNode = departureTimelineNodes.computeIfAbsent(departureTime % (24 * 60 * 60), t -> {
            final int _departureTimelineNode = i++;
            nodeAccess.setNode(_departureTimelineNode, stop.stop_lat, stop.stop_lon);
            nodeAccess.setAdditionalNodeField(_departureTimelineNode, NodeType.INTERNAL_PT.ordinal());
            times.put(_departureTimelineNode, departureTime);
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
        boardEdge.setFlags(encoder.setAccess(boardEdge.getFlags(), true, false));
        boardEdge.setName(getRouteName(feed, trip));
        setEdgeTypeAndClearDistance(boardEdge, GtfsStorage.EdgeType.BOARD);
        gtfsStorage.getStopSequences().put(boardEdge.getEdge(), stopSequence);
        gtfsStorage.getTripDescriptors().put(boardEdge.getEdge(), tripDescriptor.toByteArray());
        boardEdge.setFlags(encoder.setValidityId(boardEdge.getFlags(), validityId));
        boardEdge.setFlags(encoder.setTransfers(boardEdge.getFlags(), 1));
        return boardEdge.getEdge();
    }

    private void wireUpAndAndConnectArrivalTimeline(Stop toStop, String routeId, int stopExitNode, NavigableMap<Integer, Integer> timeNodes) {
        ZoneId zoneId = ZoneId.of(feed.agency.get(feed.routes.get(routeId).agency_id).agency_timezone);
        int time = 0;
        int prev = -1;
        for (Map.Entry<Integer, Integer> e : timeNodes.descendingMap().entrySet()) {
            EdgeIteratorState leaveTimeExpandedNetworkEdge = graph.edge(e.getValue(), stopExitNode);
            leaveTimeExpandedNetworkEdge.setFlags(encoder.setAccess(leaveTimeExpandedNetworkEdge.getFlags(), true, false));
            setEdgeTypeAndClearDistance(leaveTimeExpandedNetworkEdge, GtfsStorage.EdgeType.LEAVE_TIME_EXPANDED_NETWORK);
            int arrivalTime = e.getKey();
            leaveTimeExpandedNetworkEdge.setFlags(encoder.setTime(leaveTimeExpandedNetworkEdge.getFlags(), arrivalTime));
            setFeedIdWithTimezone(leaveTimeExpandedNetworkEdge, new GtfsStorage.FeedIdWithTimezone(id, zoneId));
            if (prev != -1) {
                EdgeIteratorState edge = graph.edge(e.getValue(), prev);
                edge.setFlags(encoder.setAccess(edge.getFlags(), true, false));
                setEdgeTypeAndClearDistance(edge, GtfsStorage.EdgeType.WAIT_ARRIVAL);
                edge.setName(toStop.stop_name);
                edge.setFlags(encoder.setTime(edge.getFlags(), time-e.getKey()));
            }
            time = e.getKey();
            prev = e.getValue();
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

    private void wireUpAndAndConnectDepartureTimeline(Stop toStop, String toRouteId, int stopEnterNode, NavigableMap<Integer, Integer> timeNodes) {
        ZoneId zoneId = ZoneId.of(feed.agency.get(feed.routes.get(toRouteId).agency_id).agency_timezone);
        int time = 0;
        int prev = -1;
        for (Map.Entry<Integer, Integer> e : timeNodes.descendingMap().entrySet()) {
            EdgeIteratorState enterTimeExpandedNetworkEdge = graph.edge(stopEnterNode, e.getValue());
            enterTimeExpandedNetworkEdge.setFlags(encoder.setAccess(enterTimeExpandedNetworkEdge.getFlags(), true, false));
            enterTimeExpandedNetworkEdge.setName(toStop.stop_name);
            setEdgeTypeAndClearDistance(enterTimeExpandedNetworkEdge, GtfsStorage.EdgeType.ENTER_TIME_EXPANDED_NETWORK);
            enterTimeExpandedNetworkEdge.setFlags(encoder.setTime(enterTimeExpandedNetworkEdge.getFlags(), e.getKey()));
            setFeedIdWithTimezone(enterTimeExpandedNetworkEdge, new GtfsStorage.FeedIdWithTimezone(id, zoneId));
            if (prev != -1) {
                EdgeIteratorState edge = graph.edge(e.getValue(), prev);
                edge.setFlags(encoder.setAccess(edge.getFlags(), true, false));
                setEdgeTypeAndClearDistance(edge, GtfsStorage.EdgeType.WAIT);
                edge.setName(toStop.stop_name);
                edge.setFlags(encoder.setTime(edge.getFlags(), time-e.getKey()));
            }
            time = e.getKey();
            prev = e.getValue();
        }
        if (!timeNodes.isEmpty()) {
            EdgeIteratorState edge = graph.edge(timeNodes.get(timeNodes.lastKey()), timeNodes.get(timeNodes.firstKey()));
            edge.setFlags(encoder.setAccess(edge.getFlags(), true, false));
            int rolloverTime = 24 * 60 * 60 - timeNodes.lastKey() + timeNodes.firstKey();
            setEdgeTypeAndClearDistance(edge, GtfsStorage.EdgeType.OVERNIGHT);
            edge.setName(toStop.stop_name);
            edge.setFlags(encoder.setTime(edge.getFlags(), rolloverTime));
        }
    }

    private void insertInboundBlockTransfers(List<TripWithStopTimeAndArrivalNode> arrivalNodes, GtfsRealtime.TripDescriptor tripDescriptor, int departureNode, StopTime stopTime, Stop stop, GtfsStorage.Validity validOn, ZoneId zoneId) {
        BitSet accumulatorValidity = new BitSet(validOn.validity.size());
        accumulatorValidity.or(validOn.validity);
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
                EdgeIteratorState transferEdge = graph.edge(lastTrip.arrivalNode,i-1);
                transferEdge.setFlags(encoder.setAccess(transferEdge.getFlags(), true, false));
                setEdgeTypeAndClearDistance(transferEdge, GtfsStorage.EdgeType.TRANSFER);
                transferEdge.setFlags(encoder.setTime(transferEdge.getFlags(), dwellTime));
                EdgeIteratorState boardEdge = graph.edge(i-1, departureNode);
                boardEdge.setFlags(encoder.setAccess(boardEdge.getFlags(), true, false));
                setEdgeTypeAndClearDistance(boardEdge, GtfsStorage.EdgeType.BOARD);
                boardEdge.setFlags(encoder.setValidityId(boardEdge.getFlags(), blockTransferValidityId));
                gtfsStorage.getStopSequences().put(boardEdge.getEdge(), stopTime.stop_sequence);
                gtfsStorage.getTripDescriptors().put(boardEdge.getEdge(), tripDescriptor.toByteArray());
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

    private void insertInboundTransfers(String fromStopId, String from_route_id, int minimumTransferTime, NavigableMap<Integer, Integer> toStopTimelineNode) {
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
                            SortedMap<Integer, Integer> tailSet = toStopTimelineNode.tailMap(arrivalTime + minimumTransferTime);
                            if (!tailSet.isEmpty()) {
                                EdgeIteratorState edge = graph.edge(j.getAdjNode(), tailSet.get(tailSet.firstKey()));
                                edge.setFlags(encoder.setAccess(edge.getFlags(), true, false));
                                setEdgeTypeAndClearDistance(edge, GtfsStorage.EdgeType.TRANSFER);
                                edge.setFlags(encoder.setTime(edge.getFlags(), tailSet.firstKey() - arrivalTime));
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
            GtfsStorage.EdgeType edgeType = encoder.getEdgeType(i.getFlags());
            if (edgeType == GtfsStorage.EdgeType.ENTER_PT) {
                String routeId = gtfsStorage.getRoutes().get(i.getEdge());
                if (toRouteId == null || toRouteId.equals(routeId)) {
                    fromStopTimelineNodes.forEach((time, e) -> {
                        EdgeIterator j = graph.getBaseGraph().createEdgeExplorer().setBaseNode(i.getAdjNode());
                        while (j.next()) {
                            GtfsStorage.EdgeType edgeType2 = encoder.getEdgeType(j.getFlags());
                            if (edgeType2 == GtfsStorage.EdgeType.ENTER_TIME_EXPANDED_NETWORK) {
                                int departureTime = (int) encoder.getTime(j.getFlags());
                                if (departureTime < time + minimumTransferTime) {
                                    continue;
                                }
                                EdgeIteratorState edge = graph.edge(e, j.getAdjNode());
                                edge.setFlags(encoder.setAccess(edge.getFlags(), true, false));
                                setEdgeTypeAndClearDistance(edge, GtfsStorage.EdgeType.TRANSFER);
                                edge.setFlags(encoder.setTime(edge.getFlags(), departureTime - time));
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
