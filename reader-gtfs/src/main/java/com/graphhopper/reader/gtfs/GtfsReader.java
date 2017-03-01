package com.graphhopper.reader.gtfs;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.*;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;
import gnu.trove.map.hash.TIntIntHashMap;
import org.mapdb.Fun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.*;

import static java.time.temporal.ChronoUnit.DAYS;

class GtfsReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(GtfsReader.class);

    private static final Frequency SINGLE_FREQUENCY = new Frequency();
    static {
        SINGLE_FREQUENCY.start_time = 0;
        SINGLE_FREQUENCY.end_time = 1;
        SINGLE_FREQUENCY.headway_secs = 1;
    }

    private final GraphHopperStorage graph;
    private final LocationIndex walkNetworkIndex;
    private final GtfsStorage gtfsStorage;

    private final DistanceCalc distCalc = Helper.DIST_EARTH;
    private final Map<String, Transfer> explicitWithinStationMinimumTransfers = new HashMap<>();
    private final NodeAccess nodeAccess;
    private final String id;
    private int i;
    private GTFSFeed feed;
    private TIntIntHashMap times;
    private SetMultimap<String, Integer> stops;
    private Map<String, Integer> stopNodes = new HashMap<>();
    private SetMultimap<String, Integer> arrivals;
    private SetMultimap<String, Transfer> betweenStationTransfers;
    private final PtFlagEncoder encoder;

    GtfsReader(String id, GraphHopperStorage ghStorage, LocationIndex walkNetworkIndex) {
        this.id = id;
        this.graph = ghStorage;
        this.gtfsStorage = (GtfsStorage) ghStorage.getExtension();
        this.nodeAccess = ghStorage.getNodeAccess();
        this.walkNetworkIndex = walkNetworkIndex;
        encoder = (PtFlagEncoder) graph.getEncodingManager().getEncoder("pt");
    }

    public void readGraph() {
        feed = this.gtfsStorage.getGtfsFeeds().get(id);
        gtfsStorage.getFares().putAll(feed.fares);
        i = graph.getNodes();
        buildPtNetwork();
        EdgeFilter filter = new EverythingButPt(encoder);
        for (Map.Entry<String, Integer> entry : stopNodes.entrySet()) {
            int enterNode = entry.getValue();
            QueryResult source = walkNetworkIndex.findClosest(nodeAccess.getLat(enterNode), nodeAccess.getLon(enterNode), filter);
            Stop stop = feed.stops.get(entry.getKey());
            int streetNode;
            if (!source.isValid()) {
                streetNode = i;
                nodeAccess.setNode(i++, stop.stop_lat, stop.stop_lon);
                graph.edge(streetNode, streetNode, 0.0, false);
            } else {
                streetNode = source.getClosestNode();
            }
            EdgeIteratorState entryEdge = graph.edge(streetNode, enterNode, 0.0, false);
            setEdgeType(entryEdge, GtfsStorage.EdgeType.ENTER_PT);
            entryEdge.setName(stop.stop_name);
            EdgeIteratorState exitEdge = graph.edge(enterNode + 1, streetNode, 0.0, false);
            setEdgeType(exitEdge, GtfsStorage.EdgeType.EXIT_PT);
            exitEdge.setName(stop.stop_name);
        }
    }

    private void buildPtNetwork() {
        stops = HashMultimap.create();
        arrivals = HashMultimap.create();
        betweenStationTransfers = HashMultimap.create();
        times = new TIntIntHashMap();
        for (Transfer transfer : feed.transfers.values()) {
            if (transfer.transfer_type == 2 && !transfer.from_stop_id.equals(transfer.to_stop_id)) {
                betweenStationTransfers.put(transfer.to_stop_id, transfer);
            } else if (transfer.transfer_type == 2 && transfer.from_stop_id.equals(transfer.to_stop_id)) {
                explicitWithinStationMinimumTransfers.put(transfer.to_stop_id, transfer);
            }
        }
        LocalDate startDate = feed.calculateStats().getStartDate();
        gtfsStorage.setStartDate(startDate);
        LocalDate endDate = feed.calculateStats().getEndDate();
        BitSet alwaysValid = new BitSet((int) DAYS.between(startDate, endDate));
        alwaysValid.set(0, alwaysValid.size());
        gtfsStorage.getOperatingDayPatterns().put(alwaysValid, 0);
        HashMultimap<String, Trip> blockTrips = HashMultimap.create();
        for (Trip trip : feed.trips.values()) {
            if (trip.block_id != null) {
                blockTrips.put(trip.block_id, trip);
            } else {
                blockTrips.put("non-block-trip"+trip.trip_id, trip);
            }
        }
        blockTrips.asMap().values().forEach(unsortedTrips -> {
            ArrayList<Trip> trips = new ArrayList<>(unsortedTrips);
            trips.sort(Comparator.comparingInt(trip -> getInterpolatedStopTimesForTrip(trip.trip_id).iterator().next().departure_time));
            if (trips.stream().map(trip -> feed.getFrequencies(trip.trip_id)).distinct().count() != 1) {
                throw new RuntimeException("Found a block with frequency-based trips. Not supported.");
            }
            Collection<Frequency> frequencies = feed.getFrequencies(trips.iterator().next().trip_id);
            for (Frequency frequency : (frequencies.isEmpty() ? Collections.singletonList(SINGLE_FREQUENCY) : frequencies)) {
                for (int time = frequency.start_time; time < frequency.end_time; time += frequency.headway_secs) {
                    List<Integer> arrivalNodes = new ArrayList<>();
                    for (Trip trip : trips) {
                        Service service = feed.services.get(trip.service_id);
                        BitSet validOnDay = new BitSet((int) DAYS.between(startDate, endDate));
                        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                            if (service.activeOn(date)) {
                                validOnDay.set((int) DAYS.between(startDate, date));
                            }
                        }
                        StopTime prev = null;
                        int arrivalNode = -1;
                        int departureNode = -1;
                        for (StopTime orderedStop : getInterpolatedStopTimesForTrip(trip.trip_id)) {
                            Stop stop = feed.stops.get(orderedStop.stop_id);
                            arrivalNode = i++;
                            nodeAccess.setNode(arrivalNode, stop.stop_lat, stop.stop_lon);
                            nodeAccess.setAdditionalNodeField(arrivalNode, NodeType.INTERNAL_PT.ordinal());
                            times.put(arrivalNode, orderedStop.arrival_time + time - frequency.start_time);
                            arrivals.put(orderedStop.stop_id, arrivalNode);
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
                                edge.setFlags(encoder.setTime(edge.getFlags(), orderedStop.arrival_time - prev.departure_time));
                            }
                            nodeAccess.setNode(i++, stop.stop_lat, stop.stop_lon);
                            nodeAccess.setAdditionalNodeField(i - 1, NodeType.INTERNAL_PT.ordinal());
                            times.put(i - 1, orderedStop.departure_time + time - frequency.start_time);
                            stops.put(orderedStop.stop_id, i - 1);
                            departureNode = i++;
                            nodeAccess.setNode(departureNode, stop.stop_lat, stop.stop_lon);
                            nodeAccess.setAdditionalNodeField(departureNode, NodeType.INTERNAL_PT.ordinal());
                            times.put(departureNode, orderedStop.departure_time + time - frequency.start_time);
                            EdgeIteratorState edge = graph.edge(
                                    i - 2,
                                    departureNode,
                                    0.0,
                                    false);
                            edge.setName(getRouteName(feed, trip));
                            int dayShift = orderedStop.departure_time / (24 * 60 * 60);
                            setEdgeType(edge, GtfsStorage.EdgeType.BOARD);
                            gtfsStorage.getExtraStrings().put(edge.getEdge(), trip.trip_id);
                            BitSet validOn = getValidOn(validOnDay, dayShift);
                            int validityId;
                            if (gtfsStorage.getOperatingDayPatterns().containsKey(validOn)) {
                                validityId = gtfsStorage.getOperatingDayPatterns().get(validOn);
                            } else {
                                validityId = gtfsStorage.getOperatingDayPatterns().size();
                                gtfsStorage.getOperatingDayPatterns().put(validOn, validityId);
                            }
                            edge.setFlags(encoder.setValidityId(edge.getFlags(), validityId));
                            edge.setFlags(encoder.setTransfers(edge.getFlags(), 1));
                            edge = graph.edge(
                                    i - 3,
                                    departureNode,
                                    0.0,
                                    false);
                            edge.setName(getRouteName(feed, trip));
                            setEdgeType(edge, GtfsStorage.EdgeType.DWELL);
                            edge.setFlags(encoder.setTime(edge.getFlags(), orderedStop.departure_time - orderedStop.arrival_time));
                            if (prev == null) {
                                for (int lastTripArrivalNode : arrivalNodes) {
                                    int dwellTime = times.get(departureNode) - times.get(lastTripArrivalNode);
                                    if (dwellTime >= 0) {
                                        edge = graph.edge(
                                                lastTripArrivalNode,
                                                departureNode,
                                                0.0,
                                                false);
                                        setEdgeType(edge, GtfsStorage.EdgeType.DWELL);
                                        edge.setFlags(encoder.setTime(edge.getFlags(), dwellTime));
                                        edge.setFlags(encoder.setValidityId(edge.getFlags(), validityId));
                                    }
                                }
                            }
                            prev = orderedStop;
                        }
                        arrivalNodes.add(i - 3);
                    }
                }
            }
        });

        for (Stop stop : feed.stops.values()) {
            nodeAccess.setNode(i++, stop.stop_lat, stop.stop_lon);
            int stopEnterNode = i-1;
            nodeAccess.setAdditionalNodeField(stopEnterNode, NodeType.STOP_ENTER_NODE.ordinal());
            stopNodes.put(stop.stop_id, stopEnterNode);
            nodeAccess.setNode(i++, stop.stop_lat, stop.stop_lon);
            int stopExitNode = i-1;
            nodeAccess.setAdditionalNodeField(stopExitNode, NodeType.STOP_EXIT_NODE.ordinal());
            int time = 0;
            int prev = -1;
            NavigableSet<Fun.Tuple2<Integer, Integer>> timeNode = new TreeSet<>();
            for (Integer nodeId : stops.get(stop.stop_id)) {
                timeNode.add(new Fun.Tuple2<>(times.get(nodeId) % (24*60*60), nodeId));
            }
            for (Fun.Tuple2<Integer, Integer> e : timeNode.descendingSet()) {
                EdgeIteratorState enterTimeExpandedNetworkEdge = graph.edge(stopEnterNode, e.b, 0.0, false);
                enterTimeExpandedNetworkEdge.setName(stop.stop_name);
                setEdgeType(enterTimeExpandedNetworkEdge, GtfsStorage.EdgeType.ENTER_TIME_EXPANDED_NETWORK);
                enterTimeExpandedNetworkEdge.setFlags(encoder.setTime(enterTimeExpandedNetworkEdge.getFlags(), e.a));
                gtfsStorage.getExtraStrings().put(enterTimeExpandedNetworkEdge.getEdge(), id);
                if (prev != -1) {
                    EdgeIteratorState edge = graph.edge(e.b, prev, 0.0, false);
                    setEdgeType(edge, GtfsStorage.EdgeType.TIME_PASSES);
                    edge.setName(stop.stop_name);
                    edge.setFlags(encoder.setTime(edge.getFlags(), time-e.a));
                }
                time = e.a;
                prev = e.b;
            }
            if (!timeNode.isEmpty()) {
                EdgeIteratorState edge = graph.edge(timeNode.last().b, timeNode.first().b, 0.0, false);
                int rolloverTime = 24 * 60 * 60 - timeNode.last().a + timeNode.first().a;
                setEdgeType(edge, GtfsStorage.EdgeType.TIME_PASSES);
                edge.setName(stop.stop_name);
                edge.setFlags(encoder.setTime(edge.getFlags(), rolloverTime));
            }
            Transfer withinStationTransfer = explicitWithinStationMinimumTransfers.get(stop.stop_id);
            insertInboundTransfers(stop.stop_id, withinStationTransfer != null ? withinStationTransfer.min_transfer_time : 0, timeNode);
            for (Transfer transfer : betweenStationTransfers.get(stop.stop_id)) {
                insertInboundTransfers(transfer.from_stop_id, transfer.min_transfer_time, timeNode);
            }
            for (Integer arrivalNodeId : arrivals.get(stop.stop_id)) {
                EdgeIteratorState leaveTimeExpandedNetworkEdge = graph.edge(arrivalNodeId, stopExitNode, 0.0, false);
                setEdgeType(leaveTimeExpandedNetworkEdge, GtfsStorage.EdgeType.LEAVE_TIME_EXPANDED_NETWORK);
                int arrivalTime = times.get(arrivalNodeId);
                leaveTimeExpandedNetworkEdge.setFlags(encoder.setTime(leaveTimeExpandedNetworkEdge.getFlags(), arrivalTime));
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

    private void insertInboundTransfers(String stop_id, int minimumTransferTime, SortedSet<Fun.Tuple2<Integer, Integer>> timeNode) {
        for (Integer arrivalNodeId : arrivals.get(stop_id)) {
            int arrivalTime = times.get(arrivalNodeId);
            SortedSet<Fun.Tuple2<Integer, Integer>> tailSet = timeNode.tailSet(new Fun.Tuple2<>(arrivalTime + minimumTransferTime, -1));
            if (!tailSet.isEmpty()) {
                Fun.Tuple2<Integer, Integer> e = tailSet.first();
                EdgeIteratorState edge = graph.edge(arrivalNodeId, e.b, 0.0, false);
                setEdgeType(edge, GtfsStorage.EdgeType.TRANSFER);
                edge.setFlags(encoder.setTime(edge.getFlags(), e.a-arrivalTime));
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
