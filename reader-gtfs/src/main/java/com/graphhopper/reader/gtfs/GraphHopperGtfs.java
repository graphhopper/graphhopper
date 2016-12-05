package com.graphhopper.reader.gtfs;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopperAPI;
import com.graphhopper.PathWrapper;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.QueryGraph;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.*;
import com.graphhopper.util.exceptions.PointNotFoundException;
import com.graphhopper.util.shapes.GHPoint;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class GraphHopperGtfs implements GraphHopperAPI {

    public static final String EARLIEST_DEPARTURE_TIME_HINT = "earliestDepartureTime";
    public static final String RANGE_QUERY_END_TIME = "rangeQueryEndTime";

    private final EncodingManager encodingManager;
    private final TranslationMap trMap = new TranslationMap().doImport();
    private boolean createWalkNetwork = false;
    private GtfsGraph gtfsGraph;
    private GraphHopperStorage storage;
    private LocationIndex locationIndex;
    private String gtfsFile;

    public GraphHopperGtfs() {
        super();
        this.encodingManager = new EncodingManager(Arrays.asList(new PtFlagEncoder()), 8);
    }

    public void setCreateWalkNetwork(boolean createWalkNetwork) {
        this.createWalkNetwork = createWalkNetwork;
    }

    public GtfsGraph getGtfsGraph() {
        return gtfsGraph;
    }

    public boolean load(String graphHopperFolder) {
        if (Helper.isEmpty(graphHopperFolder))
            throw new IllegalStateException("GraphHopperLocation is not specified. Call setGraphHopperLocation or init before");

        if (storage != null)
            throw new IllegalStateException("graph is already successfully loaded");

        if (graphHopperFolder.endsWith("-gh")) {
            // do nothing
        } else if (graphHopperFolder.endsWith(".osm") || graphHopperFolder.endsWith(".xml")) {
            throw new IllegalArgumentException("GraphHopperLocation cannot be the OSM file. Instead you need to use importOrLoad");
        } else if (!graphHopperFolder.contains(".")) {
            if (new File(graphHopperFolder + "-gh").exists())
                graphHopperFolder += "-gh";
        } else {
            File compressed = new File(graphHopperFolder + ".ghz");
            if (compressed.exists() && !compressed.isDirectory()) {
                try {
                    new Unzipper().unzip(compressed.getAbsolutePath(), graphHopperFolder, false);
                } catch (IOException ex) {
                    throw new RuntimeException("Couldn't extract file " + compressed.getAbsolutePath()
                            + " to " + graphHopperFolder, ex);
                }
            }
        }

        GHDirectory directory = new GHDirectory(graphHopperFolder, DAType.RAM_STORE);
        storage = new GraphHopperStorage(directory, encodingManager, false, new GtfsStorage());

        new GtfsReader(storage, createWalkNetwork).readGraph(new File(gtfsFile));
        storage.flush();

        gtfsGraph = new GtfsGraph() {
            @Override
            public EdgeFilter ptEnterPositions() {
                return new PtEnterPositionLookupEdgeFilter((PtFlagEncoder) encodingManager.getEncoder("pt"));
            }

            @Override
            public EdgeFilter ptExitPositions() {
                return new PtExitPositionLookupEdgeFilter((PtFlagEncoder) encodingManager.getEncoder("pt"));
            }

            @Override
            public EdgeFilter everythingButPt() {
                return new EverythingButPt((PtFlagEncoder) encodingManager.getEncoder("pt"));
            }

            @Override
            public Weighting fastestTravelTime() {
                return new PtTravelTimeWeighting(encodingManager.getEncoder("pt"));
            }

        };
        locationIndex = new LocationIndexTree(storage, directory);
        locationIndex.prepareIndex();
        return true;
    }

    @Override
    public GHResponse route(GHRequest request) {
        final int maxVisitedNodesForRequest = request.getHints().getInt(Parameters.Routing.MAX_VISITED_NODES, Integer.MAX_VALUE);
        final long requestedTimeOfDay = request.getHints().getInt(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, 0) % (24 * 60 * 60);
        final long requestedDay = request.getHints().getInt(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, 0) / (24 * 60 * 60);
        final long initialTime = requestedTimeOfDay + requestedDay * (24 * 60 * 60);
        final long rangeQueryEndTime = request.getHints().getLong(GraphHopperGtfs.RANGE_QUERY_END_TIME, initialTime);

        GHResponse response = new GHResponse();

        if (storage == null)
            throw new IllegalStateException("Do a successful call to load or importOrLoad before routing");

        if (storage.isClosed())
            throw new IllegalStateException("You need to create a new GraphHopper instance as it is already closed");


        try {

            PtFlagEncoder encoder = (PtFlagEncoder) encodingManager.getEncoder("pt");

            if (request.getPoints().size() != 2) {
                throw new IllegalArgumentException("Exactly 2 points have to be specified, but was:" + request.getPoints().size());
            }

            final GHPoint enter = request.getPoints().get(0);
            final GHPoint exit = request.getPoints().get(1);


            Locale locale = request.getLocale();
            Translation tr = trMap.getWithFallBack(locale);
            StopWatch stopWatch = new StopWatch().start();

            EdgeFilter enterFilter = new PtEnterPositionLookupEdgeFilter(encoder);
            EdgeFilter exitFilter = new PtExitPositionLookupEdgeFilter(encoder);

            QueryResult source = locationIndex.findClosest(enter.lat, enter.lon, enterFilter);
            if (!source.isValid()) {
                response.addError(new PointNotFoundException("Cannot find entry point: " + enter, 0));
                return response;
            }

            int startNode = source.getClosestNode();

            List<QueryResult> queryResults = new ArrayList<>();
            queryResults.add(source);
            QueryResult dest = locationIndex.findClosest(exit.lat, exit.lon, exitFilter);
            ArrayList<Integer> toNodes = new ArrayList<>();
            if (!dest.isValid()) {
                response.addError(new PointNotFoundException("Cannot find exit point: " + exit, 0));
                return response;
            }
            toNodes.add(dest.getClosestNode());
            queryResults.add(dest);

            response.addDebugInfo("idLookup:" + stopWatch.stop().getSeconds() + "s");


            QueryGraph queryGraph = new QueryGraph(storage);
            queryGraph.lookup(queryResults);

            long visitedNodesSum = 0L;

            stopWatch = new StopWatch().start();
            MultiCriteriaLabelSetting router = new MultiCriteriaLabelSetting(storage, new PtTravelTimeWeighting(encoder), maxVisitedNodesForRequest, (GtfsStorage) storage.getExtension());
            String debug = ", algoInit:" + stopWatch.stop().getSeconds() + "s";

            stopWatch = new StopWatch().start();
            List<Path> paths = router.calcPaths(startNode, new HashSet(toNodes), initialTime, rangeQueryEndTime);
            debug += ", routing:" + stopWatch.stop().getSeconds() + "s";

            for (Path path : paths) {
                if (path.getTime() < 0)
                    throw new RuntimeException("Time was negative. Please report as bug and include:" + request);
                debug += ", " + path.getDebugInfo();
            }

            response.addDebugInfo(debug);

            if (router.getVisitedNodes() >= maxVisitedNodesForRequest)
                throw new IllegalArgumentException("No path found due to maximum nodes exceeded " + maxVisitedNodesForRequest);

            visitedNodesSum += router.getVisitedNodes();

            response.getHints().put("visited_nodes.sum", visitedNodesSum);
            response.getHints().put("visited_nodes.average", (float) visitedNodesSum);

            for (Path path : paths) {
                if (path.isFound()) {
                    PathWrapper wrappedPath = new PathWrapper();
                    PointList waypoints = new PointList(queryResults.size(), true);
                    for (QueryResult qr : queryResults) {
                        waypoints.add(qr.getSnappedPoint());
                    }
                    wrappedPath.setWaypoints(waypoints);
                    InstructionList instructions = new InstructionList(tr);
                    PointList points1 = path.calcPoints();
                    List<EdgeIteratorState> edges = path.calcEdges();
                    int transfer = 0;
                    for (EdgeIteratorState edge1 : edges) {
                        int sign = Instruction.CONTINUE_ON_STREET;
                        if (encoder.getEdgeType(edge1.getFlags()) == GtfsStorage.EdgeType.BOARD_EDGE) {
                            if (transfer == 0) {
                                sign = Instruction.PT_START_TRIP;
                            } else {
                                sign = Instruction.PT_TRANSFER;
                            }

                            transfer++;
                        }

                        instructions.add(new Instruction(sign, edge1.getName(), InstructionAnnotation.EMPTY, edge1.fetchWayGeometry(1)));
                    }
                    if (!points1.isEmpty()) {
                        PointList end = new PointList();
                        end.add(points1, points1.size() - 1);
                        instructions.add(new FinishInstruction(points1, points1.size() - 1));
                    }
                    wrappedPath.setInstructions(instructions);
                    wrappedPath.setDescription(path.getDescription());
                    PointList pointsList = new PointList();
                    for (Instruction instruction : instructions) {
                        pointsList.add(instruction.getPoints());
                    }
                    wrappedPath.setPoints(pointsList);
                    wrappedPath.setRouteWeight(path.getWeight());
                    wrappedPath.setDistance(path.getDistance());
                    wrappedPath.setTime(path.getTime());
                    int numBoardings = 0;
                    for (EdgeIteratorState edge : path.calcEdges()) {
                        if (encoder.getEdgeType(edge.getFlags()) == GtfsStorage.EdgeType.BOARD_EDGE) {
                            numBoardings++;
                        }
                    }
                    wrappedPath.setNumChanges(numBoardings - 1);
                    response.add(wrappedPath);
                }
            }
            if (response.getAll().isEmpty()) {
                response.addError(new RuntimeException("No route found"));
            } else {
                Collections.sort(response.getAll(), (p1, p2) -> Double.compare(p1.getRouteWeight(), p2.getRouteWeight()));
            }
        } catch (IllegalArgumentException ex) {
            response.addError(ex);
        }
        return response;
    }

    public void setGtfsFile(String gtfsFile) {
        this.gtfsFile = gtfsFile;
    }

    public void close() {

    }

}
