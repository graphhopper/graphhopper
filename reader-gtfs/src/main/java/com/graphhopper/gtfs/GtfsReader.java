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

import com.carrotsearch.hppc.IntArrayList;
import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.*;
import com.google.common.collect.HashMultimap;
import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.util.DefaultSnapFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.InMemConstructionIndex;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.EdgeIteratorState;
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

    private final PtGraph ptGraph;
    private final PtGraphOut out;
    private final InMemConstructionIndex indexBuilder;
    private LocalDate startDate;
    private LocalDate endDate;

    interface PtGraphOut {

        public void putPlatformNode(int platformEnterNode, GtfsStorageI.PlatformDescriptor platformDescriptor);

        int createEdge(int src, int dest, PtEdgeAttributes attrs);
    }

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
    private final GtfsStorage gtfsStorage;

    private final Transfers transfers;
    private final String id;
    private GTFSFeed feed;
    private final Map<String, Map<GtfsStorageI.PlatformDescriptor, NavigableMap<Integer, Integer>>> departureTimelinesByStop = new HashMap<>();
    private final Map<String, Map<GtfsStorageI.PlatformDescriptor, NavigableMap<Integer, Integer>>> arrivalTimelinesByStop = new HashMap<>();

    GtfsReader(String id, GraphHopperStorage graph, PtGraph ptGraph, PtGraphOut out, GtfsStorage gtfsStorage, LocationIndex walkNetworkIndex, Transfers transfers, InMemConstructionIndex indexBuilder) {
        this.id = id;
        this.graph = graph;
        this.gtfsStorage = gtfsStorage;
        this.walkNetworkIndex = walkNetworkIndex;
        this.feed = this.gtfsStorage.getGtfsFeeds().get(id);
        this.transfers = transfers;
        this.startDate = feed.getStartDate();
        this.endDate = feed.getEndDate();
        this.ptGraph = ptGraph;
        this.out = out;
        this.indexBuilder = indexBuilder;
    }

    void connectStopsToStreetNetwork() {
        EncodingManager em = ((GraphHopperStorage) graph).getEncodingManager();
        FlagEncoder footEncoder = em.getEncoder("foot");
        final EdgeFilter filter = new DefaultSnapFilter(new FastestWeighting(footEncoder), em.getBooleanEncodedValue(Subnetwork.key("foot")));
        for (Stop stop : feed.stops.values()) {
            int stopNode = ptGraph.createNode();
            gtfsStorage.getStationNodes().put(new GtfsStorage.FeedIdWithStopId(id, stop.stop_id), stopNode);
            if (stop.location_type == 0) { // Only stops. Not interested in parent stations for now.
                Snap locationSnap = walkNetworkIndex.findClosest(stop.stop_lat, stop.stop_lon, filter);
                if (locationSnap.isValid()) {
                    gtfsStorage.getPtToStreet().put(stopNode, locationSnap.getClosestNode());
                    gtfsStorage.getStreetToPt().put(locationSnap.getClosestNode(), stopNode);
                    System.out.printf("Associate pt stop node %d with street node %d.\n", stopNode, locationSnap.getClosestNode());
                } else {
                    System.out.println("unmatched stop");
                }
                indexBuilder.addToAllTilesOnLine(stopNode, stop.stop_lat, stop.stop_lon, stop.stop_lat, stop.stop_lon);
            }
        }
    }

    void buildPtNetwork() {
        createTrips();
        wireUpStops();
        insertGtfsTransfers();
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
            Stop stop = feed.stops.get(stopId);
            arrivalTimelines.forEach(((platformDescriptor, arrivalTimeline) ->
                    wireUpArrivalTimeline(stop, arrivalTimeline, routeType(platformDescriptor), platformDescriptor)));
        });
        departureTimelinesByStop.forEach((stopId, departureTimelines) -> {
            Stop stop = feed.stops.get(stopId);
            departureTimelines.forEach(((platformDescriptor, departureTimeline) ->
                    wireUpDepartureTimeline(stop, departureTimeline, routeType(platformDescriptor), platformDescriptor)));
        });
    }

    private void insertGtfsTransfers() {
        departureTimelinesByStop.forEach((toStopId, departureTimelines) ->
                departureTimelines.forEach((this::insertInboundTransfers)));
    }

    private void insertInboundTransfers(GtfsStorageI.PlatformDescriptor toPlatformDescriptor, NavigableMap<Integer, Integer> departureTimeline) {
        LOGGER.debug("Creating transfers to stop {}, platform {}", toPlatformDescriptor.stop_id, toPlatformDescriptor);
        List<Transfer> transfersToPlatform = transfers.getTransfersToStop(toPlatformDescriptor.stop_id, routeIdOrNull(toPlatformDescriptor));
        transfersToPlatform.forEach(transfer -> {
            ptGraph.getPlatforms(new GtfsStorage.FeedIdWithStopId(id, transfer.from_stop_id)).forEach((fromPlatformNode, fromPlatformDescriptor) -> {
                if (fromPlatformDescriptor.stop_id.equals(transfer.from_stop_id) &&
                        (transfer.from_route_id == null && fromPlatformDescriptor instanceof GtfsStorageI.RouteTypePlatform || transfer.from_route_id != null && GtfsStorageI.PlatformDescriptor.route(id, transfer.from_stop_id, transfer.from_route_id).equals(fromPlatformDescriptor))) {
                    LOGGER.debug("  Creating transfers from stop {}, platform {}", transfer.from_stop_id, fromPlatformDescriptor);
                    insertTransferEdges(fromPlatformNode, transfer.min_transfer_time, departureTimeline, toPlatformDescriptor);
                }
            });
        });
    }

    public void insertTransferEdges(int arrivalPlatformNode, int minTransferTime, GtfsStorageI.PlatformDescriptor departurePlatform) {
        insertTransferEdges(arrivalPlatformNode, minTransferTime, departureTimelinesByStop.get(departurePlatform.stop_id).get(departurePlatform), departurePlatform);
    }

    private void insertTransferEdges(int arrivalPlatformNode, int minTransferTime, NavigableMap<Integer, Integer> departureTimeline, GtfsStorageI.PlatformDescriptor departurePlatform) {
        for (PtGraph.PtEdge e : ptGraph.backEdgesAround(arrivalPlatformNode)) {
            if (e.getType() == GtfsStorage.EdgeType.LEAVE_TIME_EXPANDED_NETWORK) {
                int arrivalTime = e.getTime();
                SortedMap<Integer, Integer> tailSet = departureTimeline.tailMap(arrivalTime + minTransferTime);
                if (!tailSet.isEmpty()) {
                    int edge = out.createEdge(e.getAdjNode(), tailSet.get(tailSet.firstKey()), new PtEdgeAttributes(GtfsStorage.EdgeType.TRANSFER, tailSet.firstKey() - arrivalTime, routeType(departurePlatform), 0));
                    gtfsStorage.getPlatformDescriptorByEdge().put(edge, departurePlatform);
                }
            }
        }
    }

    void wireUpAdditionalDeparturesAndArrivals(ZoneId zoneId) {
        departureTimelinesByStop.forEach((stopId, departureTimelines) -> {
            Stop stop = feed.stops.get(stopId);
            departureTimelines.forEach(((platformDescriptor, timeline) ->
                    wireUpOrPatchDepartureTimeline(zoneId, stop, timeline, platformDescriptor)));
        });
        arrivalTimelinesByStop.forEach((stopId, arrivalTimelines) -> {
            Stop stop = feed.stops.get(stopId);
            arrivalTimelines.forEach(((platformDescriptor, timeline) ->
                    wireUpOrPatchArrivalTimeline(zoneId, stop, routeIdOrNull(platformDescriptor), timeline, platformDescriptor)));
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
            arrivalNode = ptGraph.createNode();
            arrivalTime = stopTime.arrival_time + time;
            if (prev != null) {
                int edge = out.createEdge(departureNode, arrivalNode, new PtEdgeAttributes(GtfsStorage.EdgeType.HOP, stopTime.arrival_time - prev.departure_time, -1, 0));
                gtfsStorage.getStopSequences().put(edge, stopTime.stop_sequence);
            }
            Route route = feed.routes.get(trip.trip.route_id);
            GtfsStorageI.PlatformDescriptor platform;
            if (transfers.hasNoRouteSpecificDepartureTransferRules(stopTime.stop_id)) {
                platform = GtfsStorageI.PlatformDescriptor.routeType(id, stopTime.stop_id, route.route_type);
            } else {
                platform = GtfsStorageI.PlatformDescriptor.route(id, stopTime.stop_id, route.route_id);
            }
            Map<GtfsStorageI.PlatformDescriptor, NavigableMap<Integer, Integer>> departureTimelines = departureTimelinesByStop.computeIfAbsent(stopTime.stop_id, s -> new HashMap<>());
            NavigableMap<Integer, Integer> departureTimeline = departureTimelines.computeIfAbsent(platform, s -> new TreeMap<>());
            int departureTimelineNode = departureTimeline.computeIfAbsent((stopTime.departure_time + time) % (24 * 60 * 60), t -> ptGraph.createNode());
            Map<GtfsStorageI.PlatformDescriptor, NavigableMap<Integer, Integer>> arrivalTimelines = arrivalTimelinesByStop.computeIfAbsent(stopTime.stop_id, s -> new HashMap<>());
            NavigableMap<Integer, Integer> arrivalTimeline = arrivalTimelines.computeIfAbsent(platform, s -> new TreeMap<>());
            int arrivalTimelineNode = arrivalTimeline.computeIfAbsent((stopTime.arrival_time + time) % (24 * 60 * 60), t -> ptGraph.createNode());
            departureNode = ptGraph.createNode();
            int dayShift = stopTime.departure_time / (24 * 60 * 60);
            GtfsStorage.Validity validOn = new GtfsStorage.Validity(getValidOn(trip.validOnDay, dayShift), zoneId, startDate);
            int validityId;
            if (gtfsStorage.getOperatingDayPatterns().containsKey(validOn)) {
                validityId = gtfsStorage.getOperatingDayPatterns().get(validOn);
            } else {
                validityId = gtfsStorage.getOperatingDayPatterns().size();
                gtfsStorage.getOperatingDayPatterns().put(validOn, validityId);
            }

            int boardEdge = ptGraph.createEdge(departureTimelineNode, departureNode, new PtEdgeAttributes(GtfsStorage.EdgeType.BOARD, 0, validityId, 1));
            while (boardEdges.size() < stopTime.stop_sequence) {
                boardEdges.add(-1); // Padding, so that index == stop_sequence
            }
            boardEdges.add(boardEdge);
            gtfsStorage.getStopSequences().put(boardEdge, stopTime.stop_sequence);
            gtfsStorage.getTripDescriptors().put(boardEdge, tripDescriptor.toByteArray());

            int alightEdge = ptGraph.createEdge(arrivalNode, arrivalTimelineNode, new PtEdgeAttributes(GtfsStorage.EdgeType.ALIGHT, 0, validityId, 0));
            while (alightEdges.size() < stopTime.stop_sequence) {
                alightEdges.add(-1);
            }
            alightEdges.add(alightEdge);
            gtfsStorage.getStopSequences().put(alightEdge, stopTime.stop_sequence);
            gtfsStorage.getTripDescriptors().put(alightEdge, tripDescriptor.toByteArray());

            ptGraph.createEdge(arrivalNode, departureNode, new PtEdgeAttributes(GtfsStorage.EdgeType.DWELL, stopTime.departure_time - stopTime.arrival_time, -1, 0));

            if (prev == null) {
                insertInboundBlockTransfers(arrivalNodes, tripDescriptor, departureNode, stopTime.departure_time + time, stopTime, stop, validOn, zoneId, platform);
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

    private void wireUpDepartureTimeline(Stop stop, NavigableMap<Integer, Integer> departureTimeline, int route_type, GtfsStorageI.PlatformDescriptor platformDescriptor) {
        LOGGER.debug("Creating timeline at stop {} for departure platform {}", stop.stop_id, platformDescriptor);
        int platformEnterNode = ptGraph.createNode();
        int streetNode = gtfsStorage.getStationNodes().get(new GtfsStorage.FeedIdWithStopId(platformDescriptor.feed_id, platformDescriptor.stop_id));
        int entryEdge = ptGraph.createEdge(streetNode, platformEnterNode, new PtEdgeAttributes(GtfsStorage.EdgeType.ENTER_PT, 0, route_type, 0));
        gtfsStorage.getPlatformDescriptorByEdge().put(entryEdge, platformDescriptor);
        out.putPlatformNode(platformEnterNode, platformDescriptor);
        wireUpAndConnectTimeline(platformEnterNode, departureTimeline, GtfsStorage.EdgeType.ENTER_TIME_EXPANDED_NETWORK, GtfsStorage.EdgeType.WAIT);
    }

    private void wireUpArrivalTimeline(Stop stop, NavigableMap<Integer, Integer> arrivalTimeline, int route_type, GtfsStorageI.PlatformDescriptor platformDescriptor) {
        LOGGER.debug("Creating timeline at stop {} for arrival platform {}", stop.stop_id, platformDescriptor);
        int platformExitNode = ptGraph.createNode();
        int streetNode = gtfsStorage.getStationNodes().get(new GtfsStorage.FeedIdWithStopId(platformDescriptor.feed_id, platformDescriptor.stop_id));
        int exitEdge = ptGraph.createEdge(platformExitNode, streetNode, new PtEdgeAttributes(GtfsStorage.EdgeType.EXIT_PT, 0, route_type, 0));
        gtfsStorage.getPlatformDescriptorByEdge().put(exitEdge, platformDescriptor);
        out.putPlatformNode(platformExitNode, platformDescriptor);
        wireUpAndConnectTimeline(platformExitNode, arrivalTimeline, GtfsStorage.EdgeType.LEAVE_TIME_EXPANDED_NETWORK, GtfsStorage.EdgeType.WAIT_ARRIVAL);
    }

    private void wireUpOrPatchDepartureTimeline(ZoneId zoneId, Stop stop, NavigableMap<Integer, Integer> timeline, GtfsStorageI.PlatformDescriptor route) {
        int platformEnterNode = findPlatformNode(route, GtfsStorage.EdgeType.ENTER_PT);
        if (platformEnterNode != -1) {
            patchDepartureTimeline(zoneId, timeline, platformEnterNode);
        } else {
            wireUpDepartureTimeline(stop, timeline, 0, route);
        }
    }

    private void wireUpOrPatchArrivalTimeline(ZoneId zoneId, Stop stop, String routeId, NavigableMap<Integer, Integer> timeline, GtfsStorageI.PlatformDescriptor route) {
        int platformExitNode = findPlatformNode(route, GtfsStorage.EdgeType.EXIT_PT);
        if (platformExitNode != -1) {
            patchArrivalTimeline(zoneId, timeline, platformExitNode);
        } else {
            wireUpArrivalTimeline(stop, timeline, 0, null);
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
                ptGraph.createEdge(headMap.get(headMap.lastKey()), node, new PtEdgeAttributes(GtfsStorage.EdgeType.WAIT, time - headMap.lastKey(), -1, 0));
            }
            SortedMap<Integer, Integer> tailMap = staticDepartureTimelineForRoute.tailMap(time);
            if (!tailMap.isEmpty()) {
                ptGraph.createEdge(node, tailMap.get(tailMap.firstKey()), new PtEdgeAttributes(GtfsStorage.EdgeType.WAIT, tailMap.firstKey() - time, -1, 0));
            }
            out.createEdge(platformNode, node, new PtEdgeAttributes(GtfsStorage.EdgeType.ENTER_TIME_EXPANDED_NETWORK, time, setFeedIdWithTimezone(new GtfsStorage.FeedIdWithTimezone(id, zoneId)), 0));
        });
    }

    private void patchArrivalTimeline(ZoneId zoneId, NavigableMap<Integer, Integer> timeline, int platformExitNode) {
        timeline.forEach((time, node) -> out.createEdge(node, platformExitNode, new PtEdgeAttributes(GtfsStorage.EdgeType.LEAVE_TIME_EXPANDED_NETWORK, time, setFeedIdWithTimezone(new GtfsStorage.FeedIdWithTimezone(id, zoneId)), 0)));
    }

    private NavigableMap<Integer, Integer> findDepartureTimelineForPlatform(int platformEnterNode) {
        TreeMap<Integer, Integer> result = new TreeMap<>();
        if (platformEnterNode == -1) {
            return result;
        }
        for (PtGraph.PtEdge edge : ptGraph.edgesAround(platformEnterNode)) {
            if (edge.getType() == GtfsStorage.EdgeType.ENTER_TIME_EXPANDED_NETWORK) {
                result.put(edge.getTime(), edge.getAdjNode());
            }
        }
        return result;
    }

    private int findPlatformNode(GtfsStorageI.PlatformDescriptor platformDescriptor, GtfsStorage.EdgeType edgeType) {
        //FIXME: direction
        Map<Integer, GtfsStorageI.PlatformDescriptor> platforms = ptGraph.getPlatforms(new GtfsStorage.FeedIdWithStopId(platformDescriptor.feed_id, platformDescriptor.stop_id));
        for (Map.Entry<Integer, GtfsStorageI.PlatformDescriptor> e : platforms.entrySet()) {
            if (platformDescriptor.equals(e.getValue())) {
                return e.getKey();
            }
        }
        return -1;
    }

    int addDelayedBoardEdge(ZoneId zoneId, GtfsRealtime.TripDescriptor tripDescriptor, int stopSequence, int departureTime, int departureNode, BitSet validOnDay) {
        Trip trip = feed.trips.get(tripDescriptor.getTripId());
        StopTime stopTime = feed.stop_times.get(new Fun.Tuple2(tripDescriptor.getTripId(), stopSequence));
        Map<GtfsStorageI.PlatformDescriptor, NavigableMap<Integer, Integer>> departureTimelineNodesByRoute = departureTimelinesByStop.computeIfAbsent(stopTime.stop_id, s -> new HashMap<>());
        NavigableMap<Integer, Integer> departureTimelineNodes = departureTimelineNodesByRoute.computeIfAbsent(GtfsStorageI.PlatformDescriptor.route(id, stopTime.stop_id, trip.route_id), s -> new TreeMap<>());
        int departureTimelineNode = departureTimelineNodes.computeIfAbsent(departureTime % (24 * 60 * 60), t -> ptGraph.createNode());

        int dayShift = departureTime / (24 * 60 * 60);
        GtfsStorage.Validity validOn = new GtfsStorage.Validity(getValidOn(validOnDay, dayShift), zoneId, startDate);
        int validityId;
        if (gtfsStorage.getOperatingDayPatterns().containsKey(validOn)) {
            validityId = gtfsStorage.getOperatingDayPatterns().get(validOn);
        } else {
            validityId = gtfsStorage.getOperatingDayPatterns().size();
            gtfsStorage.getOperatingDayPatterns().put(validOn, validityId);
        }

        int boardEdge = out.createEdge(departureTimelineNode, departureNode, new PtEdgeAttributes(GtfsStorage.EdgeType.BOARD, 0, validityId, 1));
        gtfsStorage.getStopSequences().put(boardEdge, stopSequence);
        gtfsStorage.getTripDescriptors().put(boardEdge, tripDescriptor.toByteArray());
        return boardEdge;
    }

    private void wireUpAndConnectTimeline(int platformNode, NavigableMap<Integer, Integer> timeNodes, GtfsStorage.EdgeType timeExpandedNetworkEdgeType, GtfsStorage.EdgeType waitEdgeType) {
        ZoneId zoneId = ZoneId.of(feed.agency.values().iterator().next().agency_timezone);
        int time = 0;
        int prev = -1;
        for (Map.Entry<Integer, Integer> e : timeNodes.descendingMap().entrySet()) {
            if (timeExpandedNetworkEdgeType == GtfsStorage.EdgeType.LEAVE_TIME_EXPANDED_NETWORK) {
                out.createEdge(e.getValue(), platformNode, new PtEdgeAttributes(timeExpandedNetworkEdgeType, e.getKey(), setFeedIdWithTimezone(new GtfsStorage.FeedIdWithTimezone(id, zoneId)),0));
            } else if (timeExpandedNetworkEdgeType == GtfsStorage.EdgeType.ENTER_TIME_EXPANDED_NETWORK) {
                out.createEdge(platformNode, e.getValue(), new PtEdgeAttributes(timeExpandedNetworkEdgeType, e.getKey(), setFeedIdWithTimezone(new GtfsStorage.FeedIdWithTimezone(id, zoneId)),0));
            } else {
                throw new RuntimeException();
            }
            if (prev != -1) {
                out.createEdge(e.getValue(), prev, new PtEdgeAttributes(waitEdgeType, time - e.getKey(), -1, 0));
            }
            time = e.getKey();
            prev = e.getValue();
        }
        if (!timeNodes.isEmpty()) {
            int rolloverTime = 24 * 60 * 60 - timeNodes.lastKey() + timeNodes.firstKey();
            out.createEdge(timeNodes.get(timeNodes.lastKey()), timeNodes.get(timeNodes.firstKey()), new PtEdgeAttributes(GtfsStorage.EdgeType.OVERNIGHT, rolloverTime, -1, 0));
        }
    }

    private int setFeedIdWithTimezone(GtfsStorage.FeedIdWithTimezone validOn) {
        int validityId;
        if (gtfsStorage.getWritableTimeZones().containsKey(validOn)) {
            validityId = gtfsStorage.getWritableTimeZones().get(validOn);
        } else {
            validityId = gtfsStorage.getWritableTimeZones().size();
            gtfsStorage.getWritableTimeZones().put(validOn, validityId);
        }
        return validityId;
    }

    private void insertInboundBlockTransfers(List<TripWithStopTimeAndArrivalNode> arrivalNodes, GtfsRealtime.TripDescriptor tripDescriptor, int departureNode, int departureTime, StopTime stopTime, Stop stop, GtfsStorage.Validity validOn, ZoneId zoneId, GtfsStorageI.PlatformDescriptor platform) {
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
                int node = ptGraph.createNode();
                int transferEdge = out.createEdge(lastTrip.arrivalNode, node, new PtEdgeAttributes(GtfsStorage.EdgeType.TRANSFER, dwellTime, -1, 0));
                gtfsStorage.getPlatformDescriptorByEdge().put(transferEdge, platform);
                int boardEdge = out.createEdge(node, departureNode, new PtEdgeAttributes(GtfsStorage.EdgeType.BOARD, 0, blockTransferValidityId, 0));
                gtfsStorage.getStopSequences().put(boardEdge, stopTime.stop_sequence);
                gtfsStorage.getTripDescriptors().put(boardEdge, tripDescriptor.toByteArray());
                accumulatorValidity.andNot(lastTrip.tripWithStopTimes.validOnDay);
            }
        }
    }

    private void insertOutboundTransfers(String toStopId, String toRouteId, int minimumTransferTime, NavigableMap<Integer, Integer> fromStopTimelineNodes) {
        ptGraph.getPlatforms(new GtfsStorage.FeedIdWithStopId(id, toStopId)).forEach((platformEnterNode, toPlatform) -> {
            if (toRouteId == null || toPlatform instanceof GtfsStorageI.RouteTypePlatform || GtfsStorageI.PlatformDescriptor.route(id, toStopId, toRouteId).equals(toPlatform)) {
                fromStopTimelineNodes.forEach((time, e) -> {
                    for (PtGraph.PtEdge j : ptGraph.edgesAround(platformEnterNode)) {
                        if (j.getType() == GtfsStorage.EdgeType.ENTER_TIME_EXPANDED_NETWORK) {
                            int departureTime = j.getTime();
                            if (departureTime < time + minimumTransferTime) {
                                continue;
                            }
                            int edge = out.createEdge(e, j.getAdjNode(), new PtEdgeAttributes(GtfsStorage.EdgeType.TRANSFER, departureTime - time, -1, 0));
                            gtfsStorage.getPlatformDescriptorByEdge().put(edge, toPlatform);
                            break;
                        }
                    }
                });
            }
        });
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
