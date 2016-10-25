package com.graphhopper.reader.gtfs;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.*;
import com.graphhopper.coll.OSMIDMap;
import com.graphhopper.reader.DataReader;
import com.graphhopper.reader.dem.ElevationProvider;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

class GtfsReader implements DataReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(GtfsReader.class);

    private final GraphHopperStorage ghStorage;
    private final GtfsStorage gtfsStorage;
    private File file;
    private long nElementaryConnections;

    private final DistanceCalc distCalc = Helper.DIST_EARTH;
    private final Map<String, Transfer> explicitWithinStationMinimumTransfers = new HashMap<>();

    GtfsReader(GraphHopperStorage ghStorage) {
        this.ghStorage = ghStorage;
        this.ghStorage.create(1000);
        this.gtfsStorage = (GtfsStorage) ghStorage.getExtension();
    }

    @Override
    public DataReader setFile(File file) {
        this.file = file;
        return this;
    }

    @Override
    public DataReader setElevationProvider(ElevationProvider ep) {
        return this;
    }

    @Override
    public DataReader setWorkerThreads(int workerThreads) {
        return this;
    }

    @Override
    public DataReader setEncodingManager(EncodingManager em) {
        return this;
    }

    @Override
    public DataReader setWayPointMaxDistance(double wayPointMaxDistance) {
        return this;
    }

    @Override
    public void readGraph() throws IOException {
        GTFSFeed feed = GTFSFeed.fromFile(file.getPath());
        NodeAccess nodeAccess = ghStorage.getNodeAccess();
        TreeMap<Integer, AbstractPtEdge> edges = new TreeMap<>();
        int i = 0;
        int j = 0;
        Map<String, Integer> stops = new HashMap<>();
        for (Stop stop : feed.stops.values()) {
            nodeAccess.setNode(i++, stop.stop_lat, stop.stop_lon);
            ghStorage.edge(i-1, i-1);
            edges.put(j, new EnterLoopEdge());
            j++;

            nodeAccess.setNode(i++, stop.stop_lat, stop.stop_lon);
            stops.put(stop.stop_id, i-1);

            nodeAccess.setNode(i++, stop.stop_lat, stop.stop_lon);
            ghStorage.edge(i-1, i-1);
            edges.put(j, new ExitLoopEdge());
            j++;
        }
        LOGGER.info("Created " + i + " nodes from GTFS stops.");
        for (Transfer transfer : feed.transfers.values()) {
            if (transfer.transfer_type == 2 && !transfer.from_stop_id.equals(transfer.to_stop_id)) {
                Stop fromStop = feed.stops.get(transfer.from_stop_id);
                Stop toStop = feed.stops.get(transfer.to_stop_id);
                double distance = distCalc.calcDist(
                        fromStop.stop_lat,
                        fromStop.stop_lon,
                        toStop.stop_lat,
                        toStop.stop_lon);
                EdgeIteratorState edge = ghStorage.edge(
                        stops.get(transfer.from_stop_id),
                        stops.get(transfer.to_stop_id),
                        distance,
                        false);
                edge.setName("Transfer: " + fromStop.stop_name + " -> " + toStop.stop_name);
                edges.put(edge.getEdge(), new GtfsTransferEdge(transfer));
                j++;
            } else if (transfer.transfer_type == 2 && transfer.from_stop_id.equals(transfer.to_stop_id)) {
                explicitWithinStationMinimumTransfers.put(transfer.from_stop_id, transfer);
            }
        }
        feed.findPatterns();
        LocalDate startDate = feed.calculateStats().getStartDate();
        LocalDate endDate = feed.calculateStats().getEndDate();
        for (Pattern pattern : feed.patterns.values()) {
            try {
                List<SortedMap<Integer, Integer>> departureTimeXTravelTime = new ArrayList<>();
                for (int y = 0; y < pattern.orderedStops.size() - 1; y++) {
                    SortedMap<Integer, Integer> e = new TreeMap<>();
                    departureTimeXTravelTime.add(e);
                }
                for (String tripId : pattern.associatedTrips) {
                    Trip trip = feed.trips.get(tripId);
                    Service service = feed.services.get(trip.service_id);
                    Collection<Frequency> frequencies = feed.getFrequencies(tripId);
                    Iterable<StopTime> interpolatedStopTimesForTrip = feed.getInterpolatedStopTimesForTrip(tripId);
                    int offset = 0;
                    for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                        if (service.activeOn(date)) {
                            if (frequencies.isEmpty()) {
                                insert(offset, departureTimeXTravelTime, interpolatedStopTimesForTrip);
                            } else {
                                for (Frequency frequency : frequencies) {
                                    for (int time = frequency.start_time; time < frequency.end_time; time += frequency.headway_secs) {
                                        insert(time - frequency.start_time + offset, departureTimeXTravelTime, interpolatedStopTimesForTrip);
                                    }
                                }
                            }
                        }
                        offset += GtfsHelper.time(24, 0, 0);
                    }
                }
                int y = 0;
                String prev = null;
                for (String orderedStop : pattern.orderedStops) {
                    Stop stop = feed.stops.get(orderedStop);
                    Integer stationNode = stops.get(orderedStop);
                    nodeAccess.setNode(i++, stop.stop_lat, stop.stop_lon);
                    EdgeIteratorState accessEdge = ghStorage.edge(
                            stationNode,
                            i-1,
                            0,
                            false);
                    accessEdge.setName("Access: " + getRouteName(feed, pattern));
                    edges.put(accessEdge.getEdge(), new AccessEdge(getMinimumTransferTimeSeconds(stop)));
                    j++;
                    EdgeIteratorState egressEdge = ghStorage.edge(
                            i-1,
                            stationNode,
                            0,
                            false);
                    egressEdge.setName("Egress: " + getRouteName(feed, pattern));
                    edges.put(egressEdge.getEdge(), new EgressEdge());
                    j++;
                    EdgeIteratorState enterEdge = ghStorage.edge(
                            stationNode-1,
                            i-1,
                            0,
                            false);
                    enterEdge.setName("Enter: " + getRouteName(feed, pattern));
                    edges.put(enterEdge.getEdge(), new EnterEdge());
                    j++;
                    EdgeIteratorState exitEdge = ghStorage.edge(
                            i-1,
                            stationNode+1,
                            0,
                            false);
                    exitEdge.setName("Exit: " + getRouteName(feed, pattern));
                    edges.put(exitEdge.getEdge(), new ExitEdge());
                    j++;
                    if (prev != null) {
                        double distance = distCalc.calcDist(
                                feed.stops.get(prev).stop_lat,
                                feed.stops.get(prev).stop_lon,
                                stop.stop_lat,
                                stop.stop_lon);
                        EdgeIteratorState edge = ghStorage.edge(
                                i-2,
                                i-1,
                                distance,
                                false);
                        edge.setName(getRouteName(feed, pattern));
                        edges.put(edge.getEdge(), AbstractPatternHopEdge.createHopEdge(departureTimeXTravelTime.get(y - 1)));
                        j++;
                    }
                    prev = orderedStop;
                    y++;
                }
            } catch (GTFSFeed.FirstAndLastStopsDoNotHaveTimes e) {
                throw new RuntimeException(e);
            }
        }
        gtfsStorage.setEdges(edges);
        gtfsStorage.setRealEdgesSize(j);
        LOGGER.info("Created " + j + " edges from GTFS trip hops and transfers.");
        LOGGER.info("Created " + nElementaryConnections + " elementary connections.");
    }

    private double getMinimumTransferTimeSeconds(Stop stop) {
        Transfer transfer = explicitWithinStationMinimumTransfers.get(stop.stop_id);
        if (transfer == null) {
            return 0.0;
        } else {
            return transfer.min_transfer_time;
        }
    }

    private String getRouteName(GTFSFeed feed, Pattern pattern) {
        Route route = feed.routes.get(pattern.route_id);
        return route.route_long_name != null ? route.route_long_name : route.route_short_name;
    }

    private void insert(int time, List<SortedMap<Integer, Integer>> departureTimeXTravelTime, Iterable<StopTime> stopTimes) throws GTFSFeed.FirstAndLastStopsDoNotHaveTimes {
        StopTime prev = null;
        int i = 0;
        for (StopTime orderedStop : stopTimes) {
            if (prev != null) {
                int travelTime = (orderedStop.arrival_time - prev.departure_time);
                int departureTimeFromStartOfSchedule = prev.departure_time + time;
                addElementaryConnection(departureTimeXTravelTime, i, travelTime, departureTimeFromStartOfSchedule);
            }
            prev = orderedStop;
            i++;
        }
    }

    private void addElementaryConnection(List<SortedMap<Integer, Integer>> departureTimeXTravelTime, int i, int travelTime, int departureTimeFromStartOfSchedule) {
        nElementaryConnections++;
        if (nElementaryConnections % 1000000 == 0) {
            LOGGER.info("elementary connection " + nElementaryConnections / 1000000 + "m");
        }
        departureTimeXTravelTime.get(i - 1).put(departureTimeFromStartOfSchedule, travelTime);
    }

    @Override
    public Date getDataDate() {
        return null;
    }
}
