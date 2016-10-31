package com.graphhopper.reader.gtfs;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.*;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.graphhopper.reader.DataReader;
import com.graphhopper.reader.dem.ElevationProvider;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;
import gnu.trove.map.hash.TIntIntHashMap;
import org.mapdb.Fun;
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

    private final DistanceCalc distCalc = Helper.DIST_EARTH;
    private final Map<String, Transfer> explicitWithinStationMinimumTransfers = new HashMap<>();
    private NodeAccess nodeAccess;
    private int i;
    private GTFSFeed feed;
    private TIntIntHashMap times;
    private TreeMap<Integer, AbstractPtEdge> edges;
    private int j;
    private SetMultimap<String, Integer> stops;
    private SetMultimap<String, Integer> arrivals;


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
        stops = HashMultimap.create();
        arrivals = HashMultimap.create();
        times = new TIntIntHashMap();
        feed = GTFSFeed.fromFile(file.getPath());
        nodeAccess = ghStorage.getNodeAccess();
        edges = new TreeMap<>();
        i = 0;
        j = 0;
        feed.findPatterns();
        LocalDate startDate = feed.calculateStats().getStartDate();
        LocalDate endDate = feed.calculateStats().getStartDate().plusDays(1); // TODO
        for (Pattern pattern : feed.patterns.values()) {
            try {
                for (String tripId : pattern.associatedTrips) {
                    Trip trip = feed.trips.get(tripId);
                    Service service = feed.services.get(trip.service_id);
                    Collection<Frequency> frequencies = feed.getFrequencies(tripId);
                    Iterable<StopTime> interpolatedStopTimesForTrip = feed.getInterpolatedStopTimesForTrip(tripId);
                    int offset = 0;
                    for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                        if (service.activeOn(date)) {
                            if (frequencies.isEmpty()) {
                                insert(offset, interpolatedStopTimesForTrip, pattern);
                            } else {
                                for (Frequency frequency : frequencies) {
                                    for (int time = frequency.start_time; time < frequency.end_time; time += frequency.headway_secs) {
                                        insert(time - frequency.start_time + offset, interpolatedStopTimesForTrip, pattern);
                                    }
                                }
                            }
                        }
                        offset += GtfsHelper.time(24, 0, 0);
                    }
                }
            } catch (GTFSFeed.FirstAndLastStopsDoNotHaveTimes e) {
                throw new RuntimeException(e);
            }
        }

        for (Stop stop : feed.stops.values()) {
            nodeAccess.setNode(i++, stop.stop_lat, stop.stop_lon);
            ghStorage.edge(i-1, i-1);
            edges.put(j, new EnterLoopEdge());
            j++;
            ghStorage.edge(i-1, i-1);
            edges.put(j, new ExitLoopEdge());
            j++;
            int time = 0;
            int prev = i-1;
            SortedSet<Fun.Tuple2<Integer, Integer>> timeNode = new TreeSet<>();
            for (Integer nodeId : stops.get(stop.stop_id)) {
                timeNode.add(new Fun.Tuple2<>(times.get(nodeId), nodeId));
            }
            for (Fun.Tuple2<Integer, Integer> e : timeNode) {
                EdgeIteratorState edge = ghStorage.edge(prev, e.b, 0.0, false);
                edge.setName("Wait-"+time+"-"+e.a+"-"+stop.stop_name);
                edges.put(j, new WaitInStationEdge(e.a-time));
                j++;
                time = e.a;
                prev = e.b;
            }
            for (Integer arrivalNodeId : arrivals.get(stop.stop_id)) {
                int arrivalTime = times.get(arrivalNodeId);
                SortedSet<Fun.Tuple2<Integer, Integer>> tailSet = timeNode.tailSet(new Fun.Tuple2<>(arrivalTime, -1));
                if (!tailSet.isEmpty()) {
                    Fun.Tuple2<Integer, Integer> e = tailSet.first();
                    ghStorage.edge(arrivalNodeId, e.b, 0.0, false);
                    edges.put(j, new TimePassesPtEdge(e.a-arrivalTime));
                    j++;
                }
                ghStorage.edge(i-1, arrivalNodeId);
                edges.put(j, new ExitFindingDummyEdge());
                j++;
            }
        }
//        for (Transfer transfer : feed.transfers.values()) {
//            if (transfer.transfer_type == 2 && !transfer.from_stop_id.equals(transfer.to_stop_id)) {
//                Stop fromStop = feed.stops.get(transfer.from_stop_id);
//                Stop toStop = feed.stops.get(transfer.to_stop_id);
//                double distance = distCalc.calcDist(
//                        fromStop.stop_lat,
//                        fromStop.stop_lon,
//                        toStop.stop_lat,
//                        toStop.stop_lon);
//                EdgeIteratorState edge = ghStorage.edge(
//                        stops.get(transfer.from_stop_id),
//                        stops.get(transfer.to_stop_id),
//                        distance,
//                        false);
//                edge.setName("Transfer: " + fromStop.stop_name + " -> " + toStop.stop_name);
//                edges.put(edge.getEdge(), new GtfsTransferEdge(transfer));
//                j++;
//            } else if (transfer.transfer_type == 2 && transfer.from_stop_id.equals(transfer.to_stop_id)) {
//                explicitWithinStationMinimumTransfers.put(transfer.from_stop_id, transfer);
//            }
//        }

        gtfsStorage.setEdges(edges);
        gtfsStorage.setRealEdgesSize(j);
        LOGGER.info("Created " + j + " edges from GTFS trip hops and transfers.");
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

    private void insert(int time, Iterable<StopTime> stopTimes, Pattern pattern) throws GTFSFeed.FirstAndLastStopsDoNotHaveTimes {
        StopTime prev = null;
        for (StopTime orderedStop : stopTimes) {
            Stop stop = feed.stops.get(orderedStop.stop_id);
            nodeAccess.setNode(i++, stop.stop_lat, stop.stop_lon);
            times.put(i-1, orderedStop.arrival_time + time);
            arrivals.put(orderedStop.stop_id, i-1);
            if (prev != null) {
                Stop fromStop = feed.stops.get(prev.stop_id);
                double distance = distCalc.calcDist(
                        fromStop.stop_lat,
                        fromStop.stop_lon,
                        stop.stop_lat,
                        stop.stop_lon);
                EdgeIteratorState edge = ghStorage.edge(
                        i-2,
                        i-1,
                        distance,
                        false);
                edge.setName(getRouteName(feed, pattern));
                edges.put(edge.getEdge(), new HopEdge(orderedStop.arrival_time - prev.departure_time));
                j++;
            }
            nodeAccess.setNode(i++, stop.stop_lat, stop.stop_lon);
            times.put(i-1, orderedStop.departure_time + time);
            stops.put(orderedStop.stop_id, i-1);
            nodeAccess.setNode(i++, stop.stop_lat, stop.stop_lon);
            times.put(i-1, orderedStop.departure_time + time);
            EdgeIteratorState edge = ghStorage.edge(
                    i-2,
                    i-1,
                    0.0,
                    false);
            edge.setName(getRouteName(feed, pattern));
            edges.put(edge.getEdge(), new TimePassesPtEdge(0));
            j++;
            edge = ghStorage.edge(
                    i-3,
                    i-1,
                    0.0,
                    false);
            edge.setName(getRouteName(feed, pattern));
            edges.put(edge.getEdge(), new DwellEdge(orderedStop.departure_time - orderedStop.arrival_time));
            j++;
            prev = orderedStop;
        }
    }

    @Override
    public Date getDataDate() {
        return null;
    }
}
