package com.graphhopper.reader.gtfs;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Stop;
import com.conveyal.gtfs.model.StopTime;
import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.*;
import com.graphhopper.Trip;
import com.graphhopper.gtfs.fare.*;
import com.graphhopper.reader.osm.OSMReader;
import com.graphhopper.routing.InstructionsFromEdges;
import com.graphhopper.routing.QueryGraph;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.*;
import com.graphhopper.util.exceptions.PointNotFoundException;
import com.graphhopper.util.shapes.GHPoint;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import org.mapdb.Fun;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.ZipFile;

import static com.graphhopper.reader.gtfs.Label.reverseEdges;

public final class GraphHopperGtfs implements GraphHopperAPI {

    public static class Factory {
        private final TranslationMap translationMap;
        private final EncodingManager encodingManager;
        private final GraphHopperStorage graphHopperStorage;
        private final LocationIndex locationIndex;
        private final GtfsStorage gtfsStorage;

        private Factory(EncodingManager encodingManager, TranslationMap translationMap, GraphHopperStorage graphHopperStorage, LocationIndex locationIndex, GtfsStorage gtfsStorage) {
            this.encodingManager = encodingManager;
            this.translationMap = translationMap;
            this.graphHopperStorage = graphHopperStorage;
            this.locationIndex = locationIndex;
            this.gtfsStorage = gtfsStorage;
        }

        public GraphHopperGtfs createWith(GtfsRealtime.FeedMessage realtimeFeed) {
            return new GraphHopperGtfs(encodingManager, translationMap, graphHopperStorage, locationIndex, gtfsStorage, RealtimeFeed.fromProtobuf(gtfsStorage, realtimeFeed));
        }

        public GraphHopperGtfs createWithoutRealtimeFeed() {
            return new GraphHopperGtfs(encodingManager, translationMap, graphHopperStorage, locationIndex, gtfsStorage, RealtimeFeed.empty());
        }
    }

    public static Factory createFactory(EncodingManager encodingManager, TranslationMap translationMap, GraphHopperStorage graphHopperStorage, LocationIndex locationIndex, GtfsStorage gtfsStorage) {
        return new Factory(encodingManager, translationMap, graphHopperStorage, locationIndex, gtfsStorage);
    }

    public static final String EARLIEST_DEPARTURE_TIME_HINT = "earliestDepartureTime";
    public static final String RANGE_QUERY_END_TIME = "rangeQueryEndTime";
    public static final String ARRIVE_BY = "arriveBy";
    public static final String IGNORE_TRANSFERS = "ignoreTransfers";
    public static final String WALK_SPEED_KM_H = "walkSpeedKmH";
    public static final String MAX_WALK_DISTANCE_PER_LEG = "maxWalkDistancePerLeg";
    public static final String MAX_TRANSFER_DISTANCE_PER_LEG = "maxTransferDistancePerLeg";

    private final TranslationMap translationMap;
    private final EncodingManager encodingManager;
    private final GraphHopperStorage graphHopperStorage;
    private final LocationIndex locationIndex;
    private final GtfsStorage gtfsStorage;
    private final RealtimeFeed realtimeFeed;

    public GraphHopperGtfs(EncodingManager encodingManager, TranslationMap translationMap, GraphHopperStorage graphHopperStorage, LocationIndex locationIndex, GtfsStorage gtfsStorage, RealtimeFeed realtimeFeed) {
        this.encodingManager = encodingManager;
        this.translationMap = translationMap;
        this.graphHopperStorage = graphHopperStorage;
        this.locationIndex = locationIndex;
        this.gtfsStorage = gtfsStorage;
        this.realtimeFeed = realtimeFeed;
    }

    public static GraphHopperGtfs create(String graphHopperFolder, String gtfsFile, boolean createWalkNetwork) {
        EncodingManager encodingManager = createEncodingManager();
        GtfsStorage gtfsStorage = createGtfsStorage();
        GHDirectory directory = createGHDirectory(graphHopperFolder);
        GraphHopperStorage graphHopperStorage = createOrLoad(directory, encodingManager, gtfsStorage, createWalkNetwork, Collections.singleton(gtfsFile), Collections.emptyList());
        LocationIndex locationIndex = createOrLoadIndex(directory, graphHopperStorage);
        return createFactory(encodingManager, createTranslationMap(), graphHopperStorage, locationIndex, gtfsStorage)
                .createWithoutRealtimeFeed();
    }

    public static GtfsStorage createGtfsStorage() {
        return new GtfsStorage();
    }

    public static GHDirectory createGHDirectory(String graphHopperFolder) {
        return new GHDirectory(graphHopperFolder, DAType.RAM_STORE);
    }

    public static TranslationMap createTranslationMap() {
        return new TranslationMap().doImport();
    }

    public static EncodingManager createEncodingManager() {
        return new EncodingManager(Arrays.asList(new PtFlagEncoder()), 8);
    }

    public static GraphHopperStorage createOrLoad(GHDirectory directory, EncodingManager encodingManager, GtfsStorage gtfsStorage, boolean createWalkNetwork, Collection<String> gtfsFiles, Collection<String> osmFiles) {
        GraphHopperStorage graphHopperStorage = new GraphHopperStorage(directory, encodingManager, false, gtfsStorage);
        if (!new File(directory.getLocation()).exists()) {
            graphHopperStorage.create(1000);
            for (String osmFile : osmFiles) {
                OSMReader osmReader = new OSMReader(graphHopperStorage);
                osmReader.setFile(new File(osmFile));
                osmReader.setDontCreateStorage(true);
                try {
                    osmReader.readGraph();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            int id = 0;
            for (String gtfsFile : gtfsFiles) {
                try {
                    ((GtfsStorage) graphHopperStorage.getExtension()).loadGtfsFromFile("gtfs_" + id++, new ZipFile(gtfsFile));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            if (createWalkNetwork) {
                FakeWalkNetworkBuilder.buildWalkNetwork(((GtfsStorage) graphHopperStorage.getExtension()).getGtfsFeeds().values(), graphHopperStorage, (PtFlagEncoder) encodingManager.getEncoder("pt"), Helper.DIST_EARTH);
            }
            LocationIndex walkNetworkIndex;
            if (graphHopperStorage.getNodes() > 0 ) {
                walkNetworkIndex = new LocationIndexTree(graphHopperStorage, new RAMDirectory()).prepareIndex();
            } else {
                walkNetworkIndex = new EmptyLocationIndex();
            }
            for (int i=0;i<id;i++) {
                new GtfsReader("gtfs_" + i, graphHopperStorage, walkNetworkIndex).readGraph();
            }
            graphHopperStorage.flush();
        } else {
            graphHopperStorage.loadExisting();
        }
        return graphHopperStorage;
    }


    public static LocationIndex createOrLoadIndex(GHDirectory directory, GraphHopperStorage graphHopperStorage) {
        LocationIndex locationIndex = new LocationIndexTree(graphHopperStorage, directory);
        if (!locationIndex.loadExisting()) {
            locationIndex.prepareIndex();
        }
        return locationIndex;
    }

    public boolean load(String graphHopperFolder) {
        throw new IllegalStateException("We are always loaded, or we wouldn't exist.");
    }

    @Override
    public GHResponse route(GHRequest request) {
        final int maxVisitedNodesForRequest = request.getHints().getInt(Parameters.Routing.MAX_VISITED_NODES, Integer.MAX_VALUE);
        final long initialTime = Duration.between(gtfsStorage.getStartDate().atStartOfDay(), LocalDateTime.parse(request.getHints().get(EARLIEST_DEPARTURE_TIME_HINT, "earliestDepartureTime is a required parameter"))).getSeconds();
        final long rangeQueryEndTime = request.getHints().has(RANGE_QUERY_END_TIME) ? Duration.between(gtfsStorage.getStartDate().atStartOfDay(), LocalDateTime.parse(request.getHints().get(RANGE_QUERY_END_TIME, ""))).getSeconds() : initialTime;
        final boolean arriveBy = request.getHints().getBool(ARRIVE_BY, false);
        final boolean ignoreTransfers = request.getHints().getBool(IGNORE_TRANSFERS, false);
        final double walkSpeedKmH = request.getHints().getDouble(WALK_SPEED_KM_H, 5.0);
        final double maxWalkDistancePerLeg = request.getHints().getDouble(MAX_WALK_DISTANCE_PER_LEG, Double.MAX_VALUE);
        final double maxTransferDistancePerLeg = request.getHints().getDouble(MAX_TRANSFER_DISTANCE_PER_LEG, Double.MAX_VALUE);

        GHResponse response = new GHResponse();

        if (graphHopperStorage == null)
            throw new IllegalStateException("Do a successful call to load or importOrLoad before routing");

        if (graphHopperStorage.isClosed())
            throw new IllegalStateException("You need to create a new GraphHopper instance as it is already closed");

        PtFlagEncoder encoder = (PtFlagEncoder) encodingManager.getEncoder("pt");

        if (request.getPoints().size() != 2) {
            throw new IllegalArgumentException("Exactly 2 points have to be specified, but was:" + request.getPoints().size());
        }

        final GHPoint enter = request.getPoints().get(0);
        final GHPoint exit = request.getPoints().get(1);


        Locale locale = request.getLocale();
        Translation tr = translationMap.getWithFallBack(locale);
        StopWatch stopWatch = new StopWatch().start();

        EdgeFilter enterFilter = new EverythingButPt(encoder);
        EdgeFilter exitFilter = new EverythingButPt(encoder);

        List<QueryResult> queryResults = new ArrayList<>();

        QueryResult source = locationIndex.findClosest(enter.lat, enter.lon, enterFilter);
        if (!source.isValid()) {
            response.addError(new PointNotFoundException("Cannot find entry point: " + enter, 0));
            return response;
        }
        queryResults.add(source);

        QueryResult dest = locationIndex.findClosest(exit.lat, exit.lon, exitFilter);
        if (!dest.isValid()) {
            response.addError(new PointNotFoundException("Cannot find exit point: " + exit, 0));
            return response;
        }
        queryResults.add(dest);

        QueryGraph queryGraph = new QueryGraph(graphHopperStorage);
        queryGraph.lookup(queryResults);

        int startNode;
        int destNode;
        if (arriveBy) {
            startNode = dest.getClosestNode();
            destNode = source.getClosestNode();
        } else {
            startNode = source.getClosestNode();
            destNode = dest.getClosestNode();
        }

        ArrayList<Integer> toNodes = new ArrayList<>();
        toNodes.add(destNode);

        response.addDebugInfo("idLookup:" + stopWatch.stop().getSeconds() + "s");

        long visitedNodesSum = 0L;

        stopWatch = new StopWatch().start();

        PtTravelTimeWeighting weighting = createPtTravelTimeWeighting(encoder, arriveBy, ignoreTransfers, walkSpeedKmH);

        GraphExplorer graphExplorer = new GraphExplorer(queryGraph, encoder, gtfsStorage, realtimeFeed, arriveBy);

        MultiCriteriaLabelSetting router = new MultiCriteriaLabelSetting(graphExplorer, weighting, arriveBy, maxWalkDistancePerLeg, maxTransferDistancePerLeg, !ignoreTransfers, maxVisitedNodesForRequest);

        String debug = ", algoInit:" + stopWatch.stop().getSeconds() + "s";

        stopWatch = new StopWatch().start();
        Set<Label> solutions = router.calcPaths(startNode, new HashSet(toNodes), initialTime, rangeQueryEndTime);
        debug += ", routing:" + stopWatch.stop().getSeconds() + "s";

        response.addDebugInfo(debug);

        if (router.getVisitedNodes() >= maxVisitedNodesForRequest)
            throw new IllegalArgumentException("No path found due to maximum nodes exceeded " + maxVisitedNodesForRequest);

        visitedNodesSum += router.getVisitedNodes();

        response.getHints().put("visited_nodes.sum", visitedNodesSum);
        response.getHints().put("visited_nodes.average", (float) visitedNodesSum);

        for (Label solution : solutions) {

            List<Label.Transition> transitions = new ArrayList<>();
            if (arriveBy) {
                reverseEdges(solution, queryGraph, false)
                        .forEach(transitions::add);
            } else {
                reverseEdges(solution, queryGraph, true)
                        .forEach(transitions::add);
                Collections.reverse(transitions);
            }

            PathWrapper path = new PathWrapper();
            PointList waypoints = new PointList(queryResults.size(), true);
            for (QueryResult qr : queryResults) {
                waypoints.add(qr.getSnappedPoint());
            }
            path.setWaypoints(waypoints);

            List<List<Label.Transition>> partitions = new ArrayList<>();
            for (Label.Transition transition : transitions) {
                if (partitions.isEmpty() || encoder.getEdgeType(transition.edge.getFlags()) == GtfsStorage.EdgeType.ENTER_TIME_EXPANDED_NETWORK || encoder.getEdgeType(partitions.get(partitions.size()-1).get(partitions.get(partitions.size()-1).size()-1).edge.getFlags()) == GtfsStorage.EdgeType.LEAVE_TIME_EXPANDED_NETWORK) {
                    partitions.add(new ArrayList<>());
                }
                partitions.get(partitions.size()-1).add(transition);
            }

            path.getLegs().addAll(partitions.stream().flatMap(partition -> legs(partition, queryGraph, encoder, weighting, tr).stream()).collect(Collectors.toList()));

            final InstructionList instructions = new InstructionList(tr);
            for (int i=0; i<path.getLegs().size(); ++i) {
                Trip.Leg leg = path.getLegs().get(i);
                if (leg instanceof Trip.WalkLeg) {
                    final Trip.WalkLeg walkLeg = ((Trip.WalkLeg) leg);
                    for (Instruction instruction : walkLeg.instructions.subList(0, i < path.getLegs().size()-1 ? walkLeg.instructions.size()-1 : walkLeg.instructions.size())) {
                        instructions.add(instruction);
                    }
                } else if (leg instanceof Trip.PtLeg) {
                    final Trip.PtLeg ptLeg = ((Trip.PtLeg) leg);
                    final PointList pl;
                    if (!ptLeg.isInSameVehicleAsPrevious) {
                        pl = new PointList();
                        final Instruction departureInstruction = new Instruction(Instruction.PT_START_TRIP, ptLeg.trip_headsign, InstructionAnnotation.EMPTY, pl);
                        departureInstruction.setDistance(leg.getDistance());
                        departureInstruction.setTime(ptLeg.travelTime);
                        instructions.add(departureInstruction);
                    } else {
                        pl = instructions.get(instructions.size()-2).getPoints();
                    }
                    pl.add(ptLeg.boardStop.geometry.getY(), ptLeg.boardStop.geometry.getX());
                    for (Trip.Stop stop : ptLeg.stops.subList(0, ptLeg.stops.size()-1)) {
                        pl.add(stop.geometry.getY(), stop.geometry.getX());
                    }
                    final PointList arrivalPointList = new PointList();
                    final Trip.Stop arrivalStop = ptLeg.stops.get(ptLeg.stops.size()-1);
                    arrivalPointList.add(arrivalStop.geometry.getY(), arrivalStop.geometry.getX());
                    Instruction arrivalInstruction = new Instruction(Instruction.PT_END_TRIP, arrivalStop.name, InstructionAnnotation.EMPTY, arrivalPointList);
                    if (ptLeg.isInSameVehicleAsPrevious) {
                        instructions.replaceLast(arrivalInstruction);
                    } else {
                        instructions.add(arrivalInstruction);
                    }
                }
            }

            path.setInstructions(instructions);
            PointList pointsList = new PointList();
            for (Instruction instruction : path.getInstructions()) {
                pointsList.add(instruction.getPoints());
            }
            path.addDebugInfo(String.format("Violations: %d, Last leg dist: %f", solution.nWalkDistanceConstraintViolations, solution.walkDistanceOnCurrentLeg));
            path.setPoints(pointsList);
            path.setDistance(path.getLegs().stream().mapToDouble(Trip.Leg::getDistance).sum());
            path.setTime((solution.currentTime - initialTime) * 1000 * (arriveBy ? -1 : 1));
            path.setFirstPtLegDeparture(solution.firstPtDepartureTime);
            path.setNumChanges((int) path.getLegs().stream()
                    .filter(l->l instanceof Trip.PtLeg)
                    .filter(l -> !((Trip.PtLeg) l).isInSameVehicleAsPrevious)
                    .count() - 1);
            com.graphhopper.gtfs.fare.Trip faresTrip = new com.graphhopper.gtfs.fare.Trip();
            path.getLegs().stream()
                    .filter(leg -> leg instanceof Trip.PtLeg)
                    .map(leg -> (Trip.PtLeg) leg)
                    .findFirst()
                    .ifPresent(firstPtLeg -> {
                        LocalDateTime firstPtDepartureTime = GtfsHelper.localDateTimeFromDate(firstPtLeg.departureTime);
                        path.getLegs().stream()
                                .filter(leg -> leg instanceof Trip.PtLeg)
                                .map(leg -> (Trip.PtLeg) leg)
                                .map(ptLeg -> {
                                    final GTFSFeed gtfsFeed = gtfsStorage.getGtfsFeeds().get(ptLeg.feedId);
                                    return new com.graphhopper.gtfs.fare.Trip.Segment(gtfsFeed.trips.get(ptLeg.tripId).route_id, Duration.between(firstPtDepartureTime, GtfsHelper.localDateTimeFromDate(ptLeg.departureTime)).getSeconds(), gtfsFeed.stops.get(ptLeg.boardStop.stop_id).zone_id, gtfsFeed.stops.get(ptLeg.stops.get(ptLeg.stops.size() - 1).stop_id).zone_id, ptLeg.stops.stream().map(s -> gtfsFeed.stops.get(s.stop_id).zone_id).collect(Collectors.toSet()));
                                })
                                .forEach(faresTrip.segments::add);
                        Fares.cheapestFare(gtfsStorage.getFares(), faresTrip)
                                .ifPresent(amount -> path.setFare(amount.getAmount()));
                    });
            response.add(path);
        }
        if (response.getAll().isEmpty()) {
            response.addError(new RuntimeException("No route found"));
        } else {
            response.getAll().sort(Comparator.comparingDouble(PathWrapper::getTime));
        }
        return response;
    }

    private static PtTravelTimeWeighting createPtTravelTimeWeighting(PtFlagEncoder encoder, boolean arriveBy, boolean ignoreTransfers, double walkSpeedKmH) {
        PtTravelTimeWeighting weighting = new PtTravelTimeWeighting(encoder, walkSpeedKmH);
        if (arriveBy) {
            weighting = weighting.reverse();
        }
//        if (ignoreTransfers) {
//            weighting = weighting.ignoringNumberOfTransfers();
//        }
        return weighting;
    }

    // Ugly: What we are writing here is a parser. We are parsing a string of edges
    // into a hierarchical trip.
    // One could argue that one should never write a parser
    // by hand, because it is always ugly, but use a parser library.
    // The code would then read like a specification of what paths through the graph mean.
    private List<Trip.Leg> legs(List<Label.Transition> path, Graph graph, PtFlagEncoder encoder, Weighting weighting, Translation tr) {
        GeometryFactory geometryFactory = new GeometryFactory();
        if (GtfsStorage.EdgeType.ENTER_TIME_EXPANDED_NETWORK == encoder.getEdgeType(path.get(0).edge.getFlags())) {
            String feedId = gtfsStorage.getExtraStrings().get(path.get(0).edge.getEdge());
            final GTFSFeed gtfsFeed = gtfsStorage.getGtfsFeeds().get(feedId);
            List<Trip.Leg> result = new ArrayList<>();
            LocalDateTime boardTime = null;
            List<EdgeIteratorState> partition = null;
            for (int i=1; i<path.size(); i++) {
                Label.Transition transition = path.get(i);
                LocalDateTime time = gtfsStorage.getStartDate().atStartOfDay().plusSeconds(transition.label.currentTime);

                EdgeIteratorState edge = path.get(i).edge;
                GtfsStorage.EdgeType edgeType = encoder.getEdgeType(edge.getFlags());
                if (edgeType == GtfsStorage.EdgeType.BOARD) {
                    boardTime = time;
                    partition = new ArrayList<>();
                }
                if (partition != null) {
                    partition.add(edge);
                }
                if (EnumSet.of(GtfsStorage.EdgeType.TRANSFER, GtfsStorage.EdgeType.LEAVE_TIME_EXPANDED_NETWORK).contains(edgeType)) {
                    Geometry lineString = lineStringFromEdges(geometryFactory, partition);
                    String tripId = gtfsStorage.getExtraStrings().get(partition.get(0).getEdge());
                    List<EdgeIteratorState> edges = partition.stream()
                            .filter(e -> EnumSet.of(GtfsStorage.EdgeType.HOP, GtfsStorage.EdgeType.BOARD).contains(encoder.getEdgeType(e.getFlags())))
                            .collect(Collectors.toList());
                    List<Trip.Stop> stops = edges.stream()
                            .map(e -> stopFromHopEdge(geometryFactory, feedId, tripId, gtfsStorage.getStopSequences().get(e.getEdge())))
                            .collect(Collectors.toList());
                    com.conveyal.gtfs.model.Trip trip = gtfsFeed.trips.get(tripId);
                    result.add(new Trip.PtLeg(
                            feedId,
                            encoder.getTransfers(partition.get(0).getFlags()) == 0,
                            stops.get(0),
                            tripId,
                            trip.route_id,
                            partition,
                            Date.from(boardTime.atZone(ZoneId.systemDefault()).toInstant()),
                            stops,
                            partition.stream().mapToDouble(EdgeIteratorState::getDistance).sum(),
                            Duration.between(boardTime, time).toMillis(),
                            lineString));
                    partition = null;
                }
            }
            return result;
        } else {
            InstructionList instructions = new InstructionList(tr);
            InstructionsFromEdges instructionsFromEdges = new InstructionsFromEdges(path.get(0).edge.getBaseNode(), graph, weighting, weighting.getFlagEncoder(), graph.getNodeAccess(), tr, instructions);
            int prevEdgeId = -1;
            for (int i=0; i<path.size(); i++) {
                EdgeIteratorState edge = path.get(i).edge;
                instructionsFromEdges.next(edge, i, prevEdgeId);
                prevEdgeId = edge.getEdge();
            }
            instructionsFromEdges.finish();
            return Collections.singletonList(new Trip.WalkLeg(
                    "Walk",
                    path.stream().map(t -> t.edge).collect(Collectors.toList()),
                    lineStringFromEdges(geometryFactory, path.stream().map(t -> t.edge).collect(Collectors.toList())),
                    path.stream().mapToDouble(t -> t.edge.getDistance()).sum(),
                    StreamSupport.stream(instructions.spliterator(), false).collect(Collectors.toCollection(() -> new InstructionList(tr)))));
        }
    }

    private static Geometry lineStringFromEdges(GeometryFactory geometryFactory, List<EdgeIteratorState> edges) {
        return Stream.concat(Stream.of(edges.get(0).fetchWayGeometry(3)),
                edges.stream().map(edge -> edge.fetchWayGeometry(2)))
                .flatMap(pointList -> pointList.toGeoJson().stream())
                    .map(doubles -> new Coordinate(doubles[0], doubles[1]))
                    .collect(Collectors.collectingAndThen(Collectors.toList(),
                            coords -> geometryFactory.createLineString(coords.toArray(new Coordinate[]{}))));
    }

    private Trip.Stop stopFromHopEdge(GeometryFactory geometryFactory, String feedId, String tripId, int stopSequence) {
        GTFSFeed gtfsFeed = gtfsStorage.getGtfsFeeds().get(feedId);
        StopTime stopTime = gtfsFeed.stop_times.get(new Fun.Tuple2<>(tripId, stopSequence));
        Stop stop = gtfsFeed.stops.get(stopTime.stop_id);
        return new Trip.Stop(stop.stop_id, stop.stop_name, geometryFactory.createPoint(new Coordinate(stop.stop_lon, stop.stop_lat)));
    }

}
