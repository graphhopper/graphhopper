package com.graphhopper.reader.gtfs;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopperAPI;
import com.graphhopper.PathWrapper;
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

import static com.graphhopper.reader.gtfs.Label.reverseEdges;

public final class GraphHopperGtfs implements GraphHopperAPI {

    public static final String EARLIEST_DEPARTURE_TIME_HINT = "earliestDepartureTime";
    public static final String RANGE_QUERY_END_TIME = "rangeQueryEndTime";
    public static final String ARRIVE_BY = "arriveBy";

    private String gtfsFile;
    private boolean createWalkNetwork = false;

    private final EncodingManager encodingManager;
    private final TranslationMap trMap = new TranslationMap().doImport();

    private GraphHopperStorage graphHopperStorage;
    private LocationIndex locationIndex;
    private GtfsGraph gtfsGraph;
    private GtfsStorage gtfsStorage;

    public GraphHopperGtfs() {
        super();
        this.encodingManager = new EncodingManager(Arrays.asList(new PtFlagEncoder()), 8);
    }

    public void setGtfsFile(String gtfsFile) {
        this.gtfsFile = gtfsFile;
    }

    public void setCreateWalkNetwork(boolean createWalkNetwork) {
        this.createWalkNetwork = createWalkNetwork;
    }

    public boolean load(String graphHopperFolder) {
        if (Helper.isEmpty(graphHopperFolder))
            throw new IllegalStateException("GraphHopperLocation is not specified. Call setGraphHopperLocation or init before");

        if (graphHopperStorage != null)
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
        gtfsStorage = new GtfsStorage();
        graphHopperStorage = new GraphHopperStorage(directory, encodingManager, false, gtfsStorage);
        locationIndex = new LocationIndexTree(graphHopperStorage, directory);

        if (!new File(graphHopperFolder).exists()) {
            new GtfsReader(graphHopperStorage, createWalkNetwork).readGraph(new File(gtfsFile));
            graphHopperStorage.flush();
            locationIndex.prepareIndex();
        } else {
            graphHopperStorage.loadExisting();
            locationIndex.loadExisting();
        }

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
        return true;
    }

    @Override
    public GHResponse route(GHRequest request) {
        final int maxVisitedNodesForRequest = request.getHints().getInt(Parameters.Routing.MAX_VISITED_NODES, Integer.MAX_VALUE);
        final long requestedTimeOfDay = request.getHints().getInt(EARLIEST_DEPARTURE_TIME_HINT, 0) % (24 * 60 * 60);
        final long requestedDay = request.getHints().getInt(EARLIEST_DEPARTURE_TIME_HINT, 0) / (24 * 60 * 60);
        final long initialTime = requestedTimeOfDay + requestedDay * (24 * 60 * 60);
        final long rangeQueryEndTime = request.getHints().getLong(RANGE_QUERY_END_TIME, initialTime);
        final boolean arriveBy = request.getHints().getBool(ARRIVE_BY, false);

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
        Translation tr = trMap.getWithFallBack(locale);
        StopWatch stopWatch = new StopWatch().start();

        EdgeFilter enterFilter = new PtEnterPositionLookupEdgeFilter(encoder);
        EdgeFilter exitFilter = new PtExitPositionLookupEdgeFilter(encoder);

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


        QueryGraph queryGraph = new QueryGraph(graphHopperStorage);
        queryGraph.lookup(queryResults);

        long visitedNodesSum = 0L;

        stopWatch = new StopWatch().start();

        PtTravelTimeWeighting weighting;
        if (arriveBy) {
            weighting = new PtTravelTimeWeighting(encoder).reverse();
        } else {
            weighting = new PtTravelTimeWeighting(encoder);
        }

        GraphExplorer explorer;
        if (arriveBy) {
            explorer = new GraphExplorer(graphHopperStorage.createEdgeExplorer(new DefaultEdgeFilter(encoder, true, false)), encoder, gtfsStorage, true);
        } else {
            explorer = new GraphExplorer(graphHopperStorage.createEdgeExplorer(new DefaultEdgeFilter(encoder, false, true)), encoder, gtfsStorage, false);
        }

        MultiCriteriaLabelSetting router;
        if (arriveBy) {
           router = new MultiCriteriaLabelSetting(graphHopperStorage, weighting, maxVisitedNodesForRequest, (GtfsStorage) graphHopperStorage.getExtension(), explorer, true);
        } else {
           router = new MultiCriteriaLabelSetting(graphHopperStorage, weighting, maxVisitedNodesForRequest, (GtfsStorage) graphHopperStorage.getExtension(), explorer, false);
        }

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

            List<EdgeIteratorState> edges = new ArrayList<>();
            if (arriveBy) {
                reverseEdges(solution, graphHopperStorage)
                        .forEach(edge -> edges.add(edge.detach(false)));
            } else {
                reverseEdges(solution, graphHopperStorage)
                        .forEach(edge -> edges.add(edge.detach(true)));
                Collections.reverse(edges);
            }

            PathWrapper path = new PathWrapper();
            PointList waypoints = new PointList(queryResults.size(), true);
            for (QueryResult qr : queryResults) {
                waypoints.add(qr.getSnappedPoint());
            }
            path.setWaypoints(waypoints);
            InstructionList instructions = new InstructionList(tr);

            int numBoardings = 0;
            double distance = 0;
            for (EdgeIteratorState edge : edges) {
                int sign = Instruction.CONTINUE_ON_STREET;
                if (encoder.getEdgeType(edge.getFlags()) == GtfsStorage.EdgeType.BOARD_EDGE) {
                    if (numBoardings == 0) {
                        sign = Instruction.PT_START_TRIP;
                    } else {
                        sign = Instruction.PT_TRANSFER;
                    }
                    numBoardings++;
                }
                instructions.add(new Instruction(sign, edge.getName(), InstructionAnnotation.EMPTY, edge.fetchWayGeometry(1)));
                distance += edge.getDistance();
            }
            if (!edges.isEmpty()) {
                instructions.add(new FinishInstruction(graphHopperStorage.getNodeAccess(), edges.get(edges.size()-1).getAdjNode()));
            }
            path.setInstructions(instructions);
            PointList pointsList = new PointList();
            for (Instruction instruction : instructions) {
                pointsList.add(instruction.getPoints());
            }
            path.setPoints(pointsList);
            path.setRouteWeight(solution.currentTime);
            path.setDistance(distance);
            path.setTime(solution.currentTime * 1000);
            path.setFirstPtLegDeparture(solution.firstPtDepartureTime);
            path.setNumChanges(numBoardings - 1);
            response.add(path);
        }
        if (response.getAll().isEmpty()) {
            response.addError(new RuntimeException("No route found"));
        } else {
            Collections.sort(response.getAll(), (p1, p2) -> Double.compare(p1.getRouteWeight(), p2.getRouteWeight()));
        }
        return response;
    }

    public Graph getGraph() {
        return graphHopperStorage;
    }

    public LocationIndex getLocationIndex() {
        return locationIndex;
    }

    public GtfsGraph getGtfsGraph() {
        return gtfsGraph;
    }

    public void close() {

    }

}
