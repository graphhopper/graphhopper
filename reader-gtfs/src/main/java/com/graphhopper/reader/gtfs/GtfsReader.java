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
import org.joda.time.Days;
import org.mapdb.Fun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static java.time.temporal.ChronoUnit.DAYS;

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
    private SetMultimap<String, Transfer> betweenStationTransfers;

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
        betweenStationTransfers = HashMultimap.create();
        times = new TIntIntHashMap();
        feed = GTFSFeed.fromFile(file.getPath());
        nodeAccess = ghStorage.getNodeAccess();
        edges = new TreeMap<>();
        for (Transfer transfer : feed.transfers.values()) {
            if (transfer.transfer_type == 2 && !transfer.from_stop_id.equals(transfer.to_stop_id)) {
                betweenStationTransfers.put(transfer.to_stop_id, transfer);
            } else if (transfer.transfer_type == 2 && transfer.from_stop_id.equals(transfer.to_stop_id)) {
                explicitWithinStationMinimumTransfers.put(transfer.to_stop_id, transfer);
            }
        }
        i = 0;
        j = 0;
        LocalDate startDate = feed.calculateStats().getStartDate();
        LocalDate endDate = feed.calculateStats().getEndDate();
        try {
            for (Trip trip : feed.trips.values()) {
                Service service = feed.services.get(trip.service_id);
                Collection<Frequency> frequencies = feed.getFrequencies(trip.trip_id);
                Iterable<StopTime> interpolatedStopTimesForTrip = feed.getInterpolatedStopTimesForTrip(trip.trip_id);
                int offset = 0;
                BitSet validOnDay = new BitSet((int) DAYS.between(startDate, endDate));

                for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                    if (service.activeOn(date)) {
                        validOnDay.set((int) DAYS.between(startDate, date));
                    }
                }
                if (frequencies.isEmpty()) {
                    insert(offset, interpolatedStopTimesForTrip, trip, validOnDay);
                } else {
                    for (Frequency frequency : frequencies) {
                        for (int time = frequency.start_time; time < frequency.end_time; time += frequency.headway_secs) {
                            insert(time - frequency.start_time + offset, interpolatedStopTimesForTrip, trip, validOnDay);
                        }
                    }
                }

            }
        } catch (GTFSFeed.FirstAndLastStopsDoNotHaveTimes e) {
            throw new RuntimeException(e);
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
                timeNode.add(new Fun.Tuple2<>(times.get(nodeId) % (24*60*60), nodeId));
            }
            for (Fun.Tuple2<Integer, Integer> e : timeNode) {
                EdgeIteratorState edge = ghStorage.edge(prev, e.b, 0.0, false);
                edge.setName("Wait-"+time+"-"+e.a+"-"+stop.stop_name);
                edges.put(j, new WaitInStationEdge(e.a-time));
                j++;
                time = e.a;
                prev = e.b;
            }
            EdgeIteratorState edge = ghStorage.edge(prev, i-1, 0.0, false);
            assert time <= 24*60*60;
            int rolloverTime = 24 * 60 * 60 - time;
            edge.setName("WaitRollover-"+time+"-"+stop.stop_name);
            edges.put(j, new WaitInStationEdge(rolloverTime));
            j++;
            Transfer withinStationTransfer = explicitWithinStationMinimumTransfers.get(stop.stop_id);
            insertInboundTransfers(stop.stop_id, withinStationTransfer != null ? withinStationTransfer.min_transfer_time : 0, timeNode);
            for (Transfer transfer : betweenStationTransfers.get(stop.stop_id)) {
                insertInboundTransfers(transfer.from_stop_id, transfer.min_transfer_time, timeNode);
            }
            for (Integer arrivalNodeId : arrivals.get(stop.stop_id)) {
                ghStorage.edge(i-1, arrivalNodeId);
                edges.put(j, new ExitFindingDummyEdge());
                j++;
            }
        }

        gtfsStorage.setEdges(edges);
        gtfsStorage.setRealEdgesSize(j);
        LOGGER.info("Created " + j + " edges from GTFS trip hops and transfers.");
    }

    private void insertInboundTransfers(String stop_id, int minimumTransferTime, SortedSet<Fun.Tuple2<Integer, Integer>> timeNode) {
        for (Integer arrivalNodeId : arrivals.get(stop_id)) {
            int arrivalTime = times.get(arrivalNodeId);
            SortedSet<Fun.Tuple2<Integer, Integer>> tailSet = timeNode.tailSet(new Fun.Tuple2<>(arrivalTime + minimumTransferTime, -1));
            if (!tailSet.isEmpty()) {
                Fun.Tuple2<Integer, Integer> e = tailSet.first();
                EdgeIteratorState edge = ghStorage.edge(arrivalNodeId, e.b, 0.0, false);
                edge.setName("Transfer " + stop_id + " " + minimumTransferTime);
                edges.put(j, new TimePassesPtEdge(e.a-arrivalTime));
                j++;
            }
        }
    }

    private String getRouteName(GTFSFeed feed, Trip trip) {
        Route route = feed.routes.get(trip.route_id);
        return route.route_long_name != null ? route.route_long_name : route.route_short_name;
    }

    private void insert(int time, Iterable<StopTime> stopTimes, Trip trip, BitSet validOnDay) throws GTFSFeed.FirstAndLastStopsDoNotHaveTimes {
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
                edge.setName(getRouteName(feed, trip) + " " + trip.trip_headsign + " " + trip.trip_id);
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
            edge.setName(getRouteName(feed, trip));
            edges.put(edge.getEdge(), new BoardEdge(0, validOnDay));
            j++;
            edge = ghStorage.edge(
                    i-3,
                    i-1,
                    0.0,
                    false);
            edge.setName(getRouteName(feed, trip));
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
