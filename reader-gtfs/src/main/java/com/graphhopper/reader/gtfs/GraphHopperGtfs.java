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

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Stop;
import com.conveyal.gtfs.model.StopTime;
import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.*;
import com.graphhopper.gtfs.fare.Fares;
import com.graphhopper.reader.osm.OSMReader;
import com.graphhopper.routing.InstructionsFromEdges;
import com.graphhopper.routing.QueryGraph;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import static com.graphhopper.reader.gtfs.Label.reverseEdges;
import static com.graphhopper.util.Parameters.PT.RANGE_QUERY_END_TIME;

public final class GraphHopperGtfs implements GraphHopperAPI {

    public static class Factory {
        private final TranslationMap translationMap;
        private final PtFlagEncoder flagEncoder;
        private final GraphHopperStorage graphHopperStorage;
        private final LocationIndex locationIndex;
        private final GtfsStorage gtfsStorage;

        private Factory(PtFlagEncoder flagEncoder, TranslationMap translationMap, GraphHopperStorage graphHopperStorage, LocationIndex locationIndex, GtfsStorage gtfsStorage) {
            this.flagEncoder = flagEncoder;
            this.translationMap = translationMap;
            this.graphHopperStorage = graphHopperStorage;
            this.locationIndex = locationIndex;
            this.gtfsStorage = gtfsStorage;
        }

        public GraphHopperGtfs createWith(GtfsRealtime.FeedMessage realtimeFeed) {
            return new GraphHopperGtfs(flagEncoder, translationMap, graphHopperStorage, locationIndex, gtfsStorage, RealtimeFeed.fromProtobuf(gtfsStorage, realtimeFeed));
        }

        public GraphHopperGtfs createWithoutRealtimeFeed() {
            return new GraphHopperGtfs(flagEncoder, translationMap, graphHopperStorage, locationIndex, gtfsStorage, RealtimeFeed.empty());
        }
    }

    public static Factory createFactory(PtFlagEncoder flagEncoder, TranslationMap translationMap, GraphHopperStorage graphHopperStorage, LocationIndex locationIndex, GtfsStorage gtfsStorage) {
        return new Factory(flagEncoder, translationMap, graphHopperStorage, locationIndex, gtfsStorage);
    }

    private final TranslationMap translationMap;
    private final PtFlagEncoder flagEncoder;
    private final GraphHopperStorage graphHopperStorage;
    private final LocationIndex locationIndex;
    private final GtfsStorage gtfsStorage;
    private final RealtimeFeed realtimeFeed;
    private final GeometryFactory geometryFactory = new GeometryFactory();

    private class RequestHandler {
        private final int maxVisitedNodesForRequest;
        private final Instant initialTime;
        private final Instant rangeQueryEndTime;
        private final boolean arriveBy;
        private final boolean ignoreTransfers;
        private final double walkSpeedKmH;
        private final double maxWalkDistancePerLeg;
        private final double maxTransferDistancePerLeg;
        private final PtTravelTimeWeighting weighting;
        private final GHPoint enter;
        private final GHPoint exit;
        private final Translation translation;

        private final GHResponse response = new GHResponse();
        private final QueryGraph queryGraph = new QueryGraph(graphHopperStorage);

        RequestHandler(GHRequest request) {
            maxVisitedNodesForRequest = request.getHints().getInt(Parameters.Routing.MAX_VISITED_NODES, Integer.MAX_VALUE);
            final String departureTimeString = request.getHints().get(Parameters.PT.EARLIEST_DEPARTURE_TIME, "");
            try {
                initialTime = Instant.parse(departureTimeString);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException(String.format("Illegal value for required parameter %s: [%s]", Parameters.PT.EARLIEST_DEPARTURE_TIME, departureTimeString));
            }
            rangeQueryEndTime = request.getHints().has(Parameters.PT.RANGE_QUERY_END_TIME) ? Instant.parse(request.getHints().get(RANGE_QUERY_END_TIME, "")) : initialTime;
            arriveBy = request.getHints().getBool(Parameters.PT.ARRIVE_BY, false);
            ignoreTransfers = request.getHints().getBool(Parameters.PT.IGNORE_TRANSFERS, false);
            walkSpeedKmH = request.getHints().getDouble(Parameters.PT.WALK_SPEED, 5.0);
            maxWalkDistancePerLeg = request.getHints().getDouble(Parameters.PT.MAX_WALK_DISTANCE_PER_LEG, Double.MAX_VALUE);
            maxTransferDistancePerLeg = request.getHints().getDouble(Parameters.PT.MAX_TRANSFER_DISTANCE_PER_LEG, Double.MAX_VALUE);
            weighting = createPtTravelTimeWeighting(flagEncoder, arriveBy, walkSpeedKmH);
            translation = translationMap.getWithFallBack(request.getLocale());
            if (request.getPoints().size() != 2) {
                throw new IllegalArgumentException("Exactly 2 points have to be specified, but was:" + request.getPoints().size());
            }
            enter = request.getPoints().get(0);
            exit = request.getPoints().get(1);
        }

        GHResponse route() {
            StopWatch stopWatch = new StopWatch().start();

            QueryResult source = findClosest(enter, 0);
            QueryResult dest = findClosest(exit, 1);
            queryGraph.lookup(Arrays.asList(source, dest)); // modifies queryGraph, source and dest!

            PointList startAndEndpoint = pointListFrom(Arrays.asList(source, dest));
            response.addDebugInfo("idLookup:" + stopWatch.stop().getSeconds() + "s");

            int startNode;
            int destNode;
            if (arriveBy) {
                startNode = dest.getClosestNode();
                destNode = source.getClosestNode();
            } else {
                startNode = source.getClosestNode();
                destNode = dest.getClosestNode();
            }
            Set<Label> solutions = findPaths(startNode, destNode);
            parseSolutionsAndAddToResponse(solutions, startAndEndpoint);
            return response;
        }

        private QueryResult findClosest(GHPoint point, int indexForErrorMessage) {
            QueryResult source = locationIndex.findClosest(point.lat, point.lon, new EverythingButPt(flagEncoder));
            if (!source.isValid()) {
                throw new PointNotFoundException("Cannot find point: " + point, indexForErrorMessage);
            }
            return source;
        }

        private void parseSolutionsAndAddToResponse(Set<Label> solutions, PointList waypoints) {
            for (Label solution : solutions) {
                response.add(parseSolutionIntoPath(initialTime, arriveBy, flagEncoder, translation, queryGraph, weighting, solution, waypoints));
            }
            response.getAll().sort(Comparator.comparingDouble(PathWrapper::getTime));
        }

        private Set<Label> findPaths(int startNode, int destNode) {
            StopWatch stopWatch = new StopWatch().start();
            GraphExplorer graphExplorer = new GraphExplorer(queryGraph, weighting, flagEncoder, gtfsStorage, realtimeFeed, arriveBy);
            MultiCriteriaLabelSetting router = new MultiCriteriaLabelSetting(graphExplorer, weighting, arriveBy, maxWalkDistancePerLeg, maxTransferDistancePerLeg, !ignoreTransfers, maxVisitedNodesForRequest);
            Set<Label> solutions = router.calcPaths(startNode, Collections.singleton(destNode), initialTime, rangeQueryEndTime);
            response.addDebugInfo("routing:" + stopWatch.stop().getSeconds() + "s");
            if (router.getVisitedNodes() >= maxVisitedNodesForRequest) {
                throw new IllegalArgumentException("No path found - maximum number of nodes exceeded: " + maxVisitedNodesForRequest);
            }
            response.getHints().put("visited_nodes.sum", router.getVisitedNodes());
            response.getHints().put("visited_nodes.average", router.getVisitedNodes());
            if (solutions.isEmpty()) {
                response.addError(new RuntimeException("No route found"));
            }
            return solutions;
        }

    }

    public GraphHopperGtfs(PtFlagEncoder flagEncoder, TranslationMap translationMap, GraphHopperStorage graphHopperStorage, LocationIndex locationIndex, GtfsStorage gtfsStorage, RealtimeFeed realtimeFeed) {
        this.flagEncoder = flagEncoder;
        this.translationMap = translationMap;
        this.graphHopperStorage = graphHopperStorage;
        this.locationIndex = locationIndex;
        this.gtfsStorage = gtfsStorage;
        this.realtimeFeed = realtimeFeed;
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

    public static GraphHopperStorage createOrLoad(GHDirectory directory, EncodingManager encodingManager, PtFlagEncoder ptFlagEncoder, GtfsStorage gtfsStorage, boolean createWalkNetwork, Collection<String> gtfsFiles, Collection<String> osmFiles) {
        GraphHopperStorage graphHopperStorage = new GraphHopperStorage(directory, encodingManager, false, gtfsStorage);
        if (graphHopperStorage.loadExisting()) {
            return graphHopperStorage;
        } else {
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
                FakeWalkNetworkBuilder.buildWalkNetwork(((GtfsStorage) graphHopperStorage.getExtension()).getGtfsFeeds().values(), graphHopperStorage, ptFlagEncoder, Helper.DIST_EARTH);
            }
            LocationIndex walkNetworkIndex;
            if (graphHopperStorage.getNodes() > 0) {
                walkNetworkIndex = new LocationIndexTree(graphHopperStorage, new RAMDirectory()).prepareIndex();
            } else {
                walkNetworkIndex = new EmptyLocationIndex();
            }
            for (int i = 0; i < id; i++) {
                new GtfsReader("gtfs_" + i, graphHopperStorage, walkNetworkIndex).readGraph();
            }
            graphHopperStorage.flush();
            return graphHopperStorage;
        }
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
        return new RequestHandler(request).route();
    }

    private static PtTravelTimeWeighting createPtTravelTimeWeighting(PtFlagEncoder encoder, boolean arriveBy, double walkSpeedKmH) {
        PtTravelTimeWeighting weighting = new PtTravelTimeWeighting(encoder, walkSpeedKmH);
        if (arriveBy) {
            weighting = weighting.reverse();
        }
        return weighting;
    }

    private PathWrapper parseSolutionIntoPath(Instant initialTime, boolean arriveBy, PtFlagEncoder encoder, Translation tr, QueryGraph queryGraph, PtTravelTimeWeighting weighting, Label solution, PointList waypoints) {
        PathWrapper path = new PathWrapper();

        List<Label.Transition> transitions = new ArrayList<>();
        if (arriveBy) {
            reverseEdges(solution, queryGraph, encoder, false)
                    .forEach(transitions::add);
        } else {
            reverseEdges(solution, queryGraph, encoder, true)
                    .forEach(transitions::add);
            Collections.reverse(transitions);
        }

        path.setWaypoints(waypoints);

        List<List<Label.Transition>> partitions = getPartitions(transitions);

        final List<Trip.Leg> legs = getLegs(encoder, tr, queryGraph, weighting, partitions);
        path.getLegs().addAll(legs);

        final InstructionList instructions = getInstructions(tr, path.getLegs());
        path.setInstructions(instructions);
        PointList pointsList = new PointList();
        for (Instruction instruction : instructions) {
            pointsList.add(instruction.getPoints());
        }
        path.addDebugInfo(String.format("Violations: %d, Last leg dist: %f", solution.nWalkDistanceConstraintViolations, solution.walkDistanceOnCurrentLeg));
        path.setPoints(pointsList);
        path.setDistance(path.getLegs().stream().mapToDouble(Trip.Leg::getDistance).sum());
        path.setTime((solution.currentTime - initialTime.toEpochMilli()) * (arriveBy ? -1 : 1));
        if (solution.firstPtDepartureTime != Long.MAX_VALUE) {
            path.setFirstPtLegDeparture(solution.firstPtDepartureTime);
        }
        path.setNumChanges((int) path.getLegs().stream()
                .filter(l -> l instanceof Trip.PtLeg)
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
        return path;
    }

    private List<List<Label.Transition>> getPartitions(List<Label.Transition> transitions) {
        List<List<Label.Transition>> partitions = new ArrayList<>();
        partitions.add(new ArrayList<>());
        final Iterator<Label.Transition> iterator = transitions.iterator();
        partitions.get(partitions.size()-1).add(iterator.next());
        iterator.forEachRemaining(transition -> {
            final List<Label.Transition> previous = partitions.get(partitions.size() - 1);
            final Label.EdgeLabel previousEdge = previous.get(previous.size() - 1).edge;
            if (previousEdge != null && (transition.edge.edgeType == GtfsStorage.EdgeType.ENTER_PT || previousEdge.edgeType == GtfsStorage.EdgeType.EXIT_PT)) {
                final ArrayList<Label.Transition> p = new ArrayList<>();
                p.add(new Label.Transition(previous.get(previous.size()-1).label, null));
                partitions.add(p);
            }
            partitions.get(partitions.size()-1).add(transition);
        });
        return partitions;
    }

    private List<Trip.Leg> getLegs(PtFlagEncoder encoder, Translation tr, QueryGraph queryGraph, PtTravelTimeWeighting weighting, List<List<Label.Transition>> partitions) {
        return partitions.stream().flatMap(partition -> parsePathIntoLegs(partition, queryGraph, encoder, weighting, tr).stream()).collect(Collectors.toList());
    }

    private InstructionList getInstructions(Translation tr, List<Trip.Leg> legs) {
        final InstructionList instructions = new InstructionList(tr);
        for (int i = 0; i< legs.size(); ++i) {
            Trip.Leg leg = legs.get(i);
            if (leg instanceof Trip.WalkLeg) {
                final Trip.WalkLeg walkLeg = ((Trip.WalkLeg) leg);
                instructions.addAll(walkLeg.instructions.subList(0, i < legs.size() - 1 ? walkLeg.instructions.size() - 1 : walkLeg.instructions.size()));
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
        return instructions;
    }

    private PointList pointListFrom(List<QueryResult> queryResults) {
        PointList waypoints = new PointList(queryResults.size(), true);
        for (QueryResult qr : queryResults) {
            waypoints.add(qr.getSnappedPoint());
        }
        return waypoints;
    }

    // We are parsing a string of edges into a hierarchical trip.
    // One could argue that one should never write a parser
    // by hand, because it is always ugly, but use a parser library.
    // The code would then read like a specification of what paths through the graph mean.
    private List<Trip.Leg> parsePathIntoLegs(List<Label.Transition> path, Graph graph, PtFlagEncoder encoder, Weighting weighting, Translation tr) {
        if (GtfsStorage.EdgeType.ENTER_PT == path.get(1).edge.edgeType) {
            final GtfsStorage.FeedIdWithTimezone feedIdWithTimezone = gtfsStorage.getTimeZones().get(path.get(1).edge.timeZoneId);
            final GTFSFeed gtfsFeed = gtfsStorage.getGtfsFeeds().get(feedIdWithTimezone.feedId);
            List<Trip.Leg> result = new ArrayList<>();
            long boardTime = -1;
            List<Label.Transition> partition = null;
            for (int i = 1; i < path.size(); i++) {
                Label.Transition transition = path.get(i);
                Label.EdgeLabel edge = path.get(i).edge;
                if (edge.edgeType == GtfsStorage.EdgeType.BOARD) {
                    boardTime = transition.label.currentTime;
                    partition = new ArrayList<>();
                }
                if (partition != null) {
                    partition.add(path.get(i));
                }
                if (EnumSet.of(GtfsStorage.EdgeType.TRANSFER, GtfsStorage.EdgeType.LEAVE_TIME_EXPANDED_NETWORK).contains(edge.edgeType)) {
                    Geometry lineString = lineStringFromEdges(partition);
                    String tripId = gtfsStorage.getExtraStrings().get(partition.get(0).edge.edgeIteratorState.getEdge());
                    final StopsFromBoardHopDwellEdges stopsFromBoardHopDwellEdges = new StopsFromBoardHopDwellEdges(feedIdWithTimezone.feedId, tripId);
                    partition.stream()
                            .filter(e -> EnumSet.of(GtfsStorage.EdgeType.HOP, GtfsStorage.EdgeType.BOARD, GtfsStorage.EdgeType.DWELL).contains(e.edge.edgeType))
                            .forEach(stopsFromBoardHopDwellEdges::next);
                    stopsFromBoardHopDwellEdges.finish();
                    List<Trip.Stop> stops = stopsFromBoardHopDwellEdges.stops;

                    com.conveyal.gtfs.model.Trip trip = gtfsFeed.trips.get(tripId);
                    result.add(new Trip.PtLeg(
                            feedIdWithTimezone.feedId,partition.get(0).edge.nTransfers == 0,
                            stops.get(0),
                            tripId,
                            trip.route_id,
                            edges(partition).map(edgeLabel -> edgeLabel.edgeIteratorState).collect(Collectors.toList()),
                            new Date(boardTime),
                            stops,
                            partition.stream().mapToDouble(t -> t.edge.distance).sum(),
                            path.get(i-1).label.currentTime - boardTime,
                            new Date(path.get(i-1).label.currentTime),
                            lineString));
                    partition = null;
                }
            }
            return result;
        } else {
            InstructionList instructions = new InstructionList(tr);
            InstructionsFromEdges instructionsFromEdges = new InstructionsFromEdges(path.get(1).edge.edgeIteratorState.getBaseNode(), graph, weighting, weighting.getFlagEncoder(), graph.getNodeAccess(), tr, instructions);
            int prevEdgeId = -1;
            for (int i=1; i<path.size(); i++) {
                EdgeIteratorState edge = path.get(i).edge.edgeIteratorState;
                instructionsFromEdges.next(edge, i, prevEdgeId);
                prevEdgeId = edge.getEdge();
            }
            instructionsFromEdges.finish();
            final Instant departureTime = Instant.ofEpochMilli(path.get(0).label.currentTime);
            final Instant arrivalTime = Instant.ofEpochMilli(path.get(path.size() - 1).label.currentTime);
            return Collections.singletonList(new Trip.WalkLeg(
                    "Walk",
                    Date.from(departureTime),
                    edges(path).map(edgeLabel -> edgeLabel.edgeIteratorState).collect(Collectors.toList()),
                    lineStringFromEdges(path),
                    edges(path).mapToDouble(edgeLabel -> edgeLabel.distance).sum(),
                    instructions.stream().collect(Collectors.toCollection(() -> new InstructionList(tr))),
                    Date.from(arrivalTime)));
        }
    }

    private Stream<Label.EdgeLabel> edges(List<Label.Transition> path) {
        return path.stream().filter(t -> t.edge != null).map(t -> t.edge);
    }

    private Geometry lineStringFromEdges(List<Label.Transition> transitions) {
        List<Coordinate> coordinates = new ArrayList<>();
        final Iterator<Label.Transition> iterator = transitions.iterator();
        iterator.next();
        coordinates.addAll(toCoordinateArray(iterator.next().edge.edgeIteratorState.fetchWayGeometry(3)));
        iterator.forEachRemaining(transition -> {
            coordinates.addAll(toCoordinateArray(transition.edge.edgeIteratorState.fetchWayGeometry(2)));
        });
        return geometryFactory.createLineString(coordinates.toArray(new Coordinate[coordinates.size()]));
    }


    public static List<Coordinate> toCoordinateArray(PointList pointList) {
        List<Coordinate> coordinates = new ArrayList<>(pointList.size());
        for (int i=0; i<pointList.size(); i++) {
            coordinates.add(pointList.getDimension() == 3 ?
                    new Coordinate(pointList.getLon(i), pointList.getLat(i)) :
                    new Coordinate(pointList.getLon(i), pointList.getLat(i), pointList.getEle(i)));
        }
        return coordinates;
    }

    private class StopsFromBoardHopDwellEdges {

        private final String tripId;
        private final List<Trip.Stop> stops = new ArrayList<>();
        private final GTFSFeed gtfsFeed;
        private long arrivalTimeFromHopEdge;
        private Stop stop = null;

        StopsFromBoardHopDwellEdges(String feedId, String tripId) {
            this.tripId = tripId;
            this.gtfsFeed = gtfsStorage.getGtfsFeeds().get(feedId);
        }

        void next(Label.Transition t) {
            long departureTime;
            switch (t.edge.edgeType) {
                case BOARD:
                    stop = findStop(t);
                    departureTime = t.label.currentTime;
                    stops.add(new Trip.Stop(stop.stop_id, stop.stop_name, geometryFactory.createPoint(new Coordinate(stop.stop_lon, stop.stop_lat)), null, Date.from(Instant.ofEpochMilli(departureTime))));
                    break;
                case HOP:
                    stop = findStop(t);
                    arrivalTimeFromHopEdge = t.label.currentTime;
                    break;
                case DWELL:
                    departureTime = t.label.currentTime;
                    stops.add(new Trip.Stop(stop.stop_id, stop.stop_name, geometryFactory.createPoint(new Coordinate(stop.stop_lon, stop.stop_lat)), Date.from(Instant.ofEpochMilli(arrivalTimeFromHopEdge)), Date.from(Instant.ofEpochMilli(departureTime))));
                    break;
                default:
                    throw new RuntimeException();
            }
        }

        private Stop findStop(Label.Transition t) {
            int stopSequence = gtfsStorage.getStopSequences().get(t.edge.edgeIteratorState.getEdge());
            StopTime stopTime = gtfsFeed.stop_times.get(new Fun.Tuple2<>(tripId, stopSequence));
            return gtfsFeed.stops.get(stopTime.stop_id);
        }

        void finish() {
            stops.add(new Trip.Stop(stop.stop_id, stop.stop_name, geometryFactory.createPoint(new Coordinate(stop.stop_lon, stop.stop_lat)), Date.from(Instant.ofEpochMilli(arrivalTimeFromHopEdge)), null));
        }

    }

}
