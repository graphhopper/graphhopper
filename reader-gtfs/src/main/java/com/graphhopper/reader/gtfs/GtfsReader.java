package com.graphhopper.reader.gtfs;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.*;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.graphhopper.reader.DataReader;
import com.graphhopper.reader.dem.ElevationProvider;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.triangulate.ConformingDelaunayTriangulator;
import com.vividsolutions.jts.triangulate.ConstraintVertex;
import com.vividsolutions.jts.triangulate.quadedge.QuadEdge;
import com.vividsolutions.jts.triangulate.quadedge.QuadEdgeSubdivision;
import com.vividsolutions.jts.triangulate.quadedge.Vertex;
import gnu.trove.map.hash.TIntIntHashMap;
import org.mapdb.Fun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

import static java.time.temporal.ChronoUnit.DAYS;

class GtfsReader implements DataReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(GtfsReader.class);

    private final GraphHopperStorage graph;
    private final GtfsStorage gtfsStorage;
    private final boolean createWalkNetwork;
    private File file;

    private final DistanceCalc distCalc = Helper.DIST_EARTH;
    private final Map<String, Transfer> explicitWithinStationMinimumTransfers = new HashMap<>();
    private final NodeAccess nodeAccess;
    private int i;
    private GTFSFeed feed;
    private TIntIntHashMap times;
    private TreeMap<Integer, AbstractPtEdge> edges;
    private SetMultimap<String, Integer> stops;
    private Map<String, Integer> stopNodes = new HashMap<>();
    private SetMultimap<String, Integer> arrivals;
    private SetMultimap<String, Transfer> betweenStationTransfers;
    private final PtFlagEncoder encoder;

    GtfsReader(GraphHopperStorage ghStorage, boolean createWalkNetwork) {
        this.graph = ghStorage;
        this.graph.create(1000);
        this.gtfsStorage = (GtfsStorage) ghStorage.getExtension();
        this.nodeAccess = ghStorage.getNodeAccess();
        this.createWalkNetwork = createWalkNetwork;
        encoder = (PtFlagEncoder) graph.getEncodingManager().getEncoder("pt");
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
        feed = GTFSFeed.fromFile(file.getPath());
        i = 0;
        if (createWalkNetwork) {
            buildWalkNetwork();
        }
        buildPtNetwork();
        if (createWalkNetwork) {
            LocationIndex locationIndex = new LocationIndexTree(graph, new RAMDirectory()).prepareIndex();
            EdgeFilter filter = new EverythingButPt(encoder);
            for (Map.Entry<String, Integer> entry : stopNodes.entrySet()) {
                int enterNode = entry.getValue();
                QueryResult source = locationIndex.findClosest(nodeAccess.getLat(enterNode), nodeAccess.getLon(enterNode), filter);
                if (!source.isValid()) {
                    throw new IllegalStateException();
                } else {
                    graph.edge(source.getClosestNode(), enterNode, 0.0, false);
                    graph.edge(enterNode+1, source.getClosestNode(), 0.0, false);
                }
            }
        }
    }

    private void buildWalkNetwork() {
        Collection<ConstraintVertex> sites = new ArrayList<>();
        Map<Vertex, Integer> vertex2nodeId = new HashMap<>();
        for (Stop stop : feed.stops.values()) {
            nodeAccess.setNode(i++, stop.stop_lat, stop.stop_lon);
            ConstraintVertex site = new ConstraintVertex(new Coordinate(stop.stop_lon,stop.stop_lat));
            sites.add(site);
            vertex2nodeId.put(site, i-1);
        }
        ConformingDelaunayTriangulator conformingDelaunayTriangulator = new ConformingDelaunayTriangulator(sites, 0.0);
        conformingDelaunayTriangulator.setConstraints(new ArrayList(), new ArrayList());
        conformingDelaunayTriangulator.formInitialDelaunay();
        QuadEdgeSubdivision tin = conformingDelaunayTriangulator.getSubdivision();
        List<QuadEdge> edges = tin.getPrimaryEdges(false);
        for (QuadEdge edge : edges) {
            EdgeIteratorState ghEdge = graph.edge(vertex2nodeId.get(edge.orig()), vertex2nodeId.get(edge.dest()));
            double distance = distCalc.calcDist(
                    edge.orig().getY(),
                    edge.orig().getX(),
                    edge.dest().getY(),
                    edge.dest().getX());
            ghEdge.setDistance(distance);
            ghEdge.setFlags(encoder.setSpeed(ghEdge.getFlags(), 5.0));
            ghEdge.setFlags(encoder.setAccess(ghEdge.getFlags(), true, true));
        }
    }

    private void buildPtNetwork() {
        stops = HashMultimap.create();
        arrivals = HashMultimap.create();
        betweenStationTransfers = HashMultimap.create();
        times = new TIntIntHashMap();
        edges = new TreeMap<>();
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
            int stopEnterNode = i-1;
            stopNodes.put(stop.stop_id, stopEnterNode);
            EdgeIteratorState edge1 = graph.edge(stopEnterNode, stopEnterNode);
            setEdgeType(edge1, GtfsStorage.EdgeType.STOP_NODE_MARKER_EDGE);
            nodeAccess.setNode(i++, stop.stop_lat, stop.stop_lon);
            int stopExitNode = i-1;
            EdgeIteratorState edge2 = graph.edge(stopExitNode, stopExitNode);
            setEdgeType(edge2, GtfsStorage.EdgeType.STOP_EXIT_NODE_MARKER_EDGE);
            int time = 0;
            int prev = -1;
            NavigableSet<Fun.Tuple2<Integer, Integer>> timeNode = new TreeSet<>();
            for (Integer nodeId : stops.get(stop.stop_id)) {
                timeNode.add(new Fun.Tuple2<>(times.get(nodeId) % (24*60*60), nodeId));
            }
            for (Fun.Tuple2<Integer, Integer> e : timeNode.descendingSet()) {
                EdgeIteratorState enterTimeExpandedNetworkEdge = graph.edge(stopEnterNode, e.b, 0.0, false);
                enterTimeExpandedNetworkEdge.setName("EnterTimeExpandedNetwork-"+e.a+"-"+stop.stop_name);
                setEdgeType(enterTimeExpandedNetworkEdge, GtfsStorage.EdgeType.ENTER_TIME_EXPANDED_NETWORK);
                enterTimeExpandedNetworkEdge.setFlags(encoder.setTime(enterTimeExpandedNetworkEdge.getFlags(), e.a));
                if (prev != -1) {
                    EdgeIteratorState edge = graph.edge(e.b, prev, 0.0, false);
                    setEdgeType(edge, GtfsStorage.EdgeType.TIME_PASSES_PT_EDGE);
                    edge.setName("Wait-"+e.a+"-"+time+"-"+stop.stop_name);
                    edge.setFlags(encoder.setTime(edge.getFlags(), time-e.a));
                }
                time = e.a;
                prev = e.b;
            }
            if (!timeNode.isEmpty()) {
                EdgeIteratorState edge = graph.edge(timeNode.last().b, timeNode.first().b, 0.0, false);
                int rolloverTime = 24 * 60 * 60 - timeNode.last().a + timeNode.first().a;
                setEdgeType(edge, GtfsStorage.EdgeType.TIME_PASSES_PT_EDGE);
                edge.setName("WaitRollover-"+stop.stop_name);
                edge.setFlags(encoder.setTime(edge.getFlags(), rolloverTime));
            }
            Transfer withinStationTransfer = explicitWithinStationMinimumTransfers.get(stop.stop_id);
            insertInboundTransfers(stop.stop_id, withinStationTransfer != null ? withinStationTransfer.min_transfer_time : 0, timeNode);
            for (Transfer transfer : betweenStationTransfers.get(stop.stop_id)) {
                insertInboundTransfers(transfer.from_stop_id, transfer.min_transfer_time, timeNode);
            }
            for (Integer arrivalNodeId : arrivals.get(stop.stop_id)) {
                EdgeIteratorState edge = graph.edge(arrivalNodeId, stopExitNode, 0.0, false);
                setEdgeType(edge, GtfsStorage.EdgeType.LEAVE_TIME_EXPANDED_NETWORK);
            }
        }

        gtfsStorage.setEdges(edges);
    }

    private void insertInboundTransfers(String stop_id, int minimumTransferTime, SortedSet<Fun.Tuple2<Integer, Integer>> timeNode) {
        for (Integer arrivalNodeId : arrivals.get(stop_id)) {
            int arrivalTime = times.get(arrivalNodeId);
            SortedSet<Fun.Tuple2<Integer, Integer>> tailSet = timeNode.tailSet(new Fun.Tuple2<>(arrivalTime + minimumTransferTime, -1));
            if (!tailSet.isEmpty()) {
                Fun.Tuple2<Integer, Integer> e = tailSet.first();
                EdgeIteratorState edge = graph.edge(arrivalNodeId, e.b, 0.0, false);
                edge.setName("Transfer " + stop_id + " " + minimumTransferTime);
                setEdgeType(edge, GtfsStorage.EdgeType.TIME_PASSES_PT_EDGE);
                edge.setFlags(encoder.setTime(edge.getFlags(), e.a-arrivalTime));
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
                EdgeIteratorState edge = graph.edge(
                        i-2,
                        i-1,
                        distance,
                        false);
                edge.setName(getRouteName(feed, trip) + " " + trip.trip_headsign + " " + trip.trip_id);
                setEdgeType(edge, GtfsStorage.EdgeType.TIME_PASSES_PT_EDGE);
                edge.setFlags(encoder.setTime(edge.getFlags(), orderedStop.arrival_time - prev.departure_time));
            }
            nodeAccess.setNode(i++, stop.stop_lat, stop.stop_lon);
            times.put(i-1, orderedStop.departure_time + time);
            stops.put(orderedStop.stop_id, i-1);
            nodeAccess.setNode(i++, stop.stop_lat, stop.stop_lon);
            times.put(i-1, orderedStop.departure_time + time);
            EdgeIteratorState edge = graph.edge(
                    i-2,
                    i-1,
                    0.0,
                    false);
            edge.setName(getRouteName(feed, trip));
            int dayShift = orderedStop.departure_time / (24 * 60 * 60);
            setEdgeType(edge, GtfsStorage.EdgeType.BOARD_EDGE);
            edges.put(edge.getEdge(), new BoardEdge(0, getValidOn(validOnDay, dayShift)));
            edge = graph.edge(
                    i-3,
                    i-1,
                    0.0,
                    false);
            edge.setName(getRouteName(feed, trip));
            GtfsStorage.EdgeType timePassesPtEdge = GtfsStorage.EdgeType.TIME_PASSES_PT_EDGE;
            setEdgeType(edge, timePassesPtEdge);
            edge.setFlags(encoder.setTime(edge.getFlags(), orderedStop.departure_time - orderedStop.arrival_time));
            prev = orderedStop;
        }
    }

    private void setEdgeType(EdgeIteratorState edge, GtfsStorage.EdgeType timePassesPtEdge) {
        edge.setFlags(encoder.setEdgeType(edge.getFlags(), timePassesPtEdge));
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

    @Override
    public Date getDataDate() {
        return null;
    }
}
