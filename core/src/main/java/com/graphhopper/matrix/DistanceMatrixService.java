package com.graphhopper.matrix;

import com.graphhopper.GraphHopper;
import com.graphhopper.matrix.algorithm.MatrixAlgorithm;
import com.graphhopper.matrix.algorithm.MatrixAlgorithmFactory;
import com.graphhopper.matrix.algorithm.SimpleMatrixAlgorithmFactory;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.QueryGraph;
import com.graphhopper.routing.RoutingAlgorithmFactory;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.util.*;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.shapes.GHPoint;

import java.util.ArrayList;
import java.util.List;

import static com.graphhopper.util.Parameters.Algorithms.MATRIX_ONE_TO_ONE;

/**
 * Provides a Distance Matrix API
 *
 * @author Pascal Büttiker
 */
public class DistanceMatrixService {

    private final GraphHopper hopper; // TODO after GraphHopper refactoring, this dependency should go away
    private final MatrixAlgorithmFactory matrixAlgorithmFactory;


    public DistanceMatrixService(GraphHopper hopper){
        this(hopper, new SimpleMatrixAlgorithmFactory());
    }

    public DistanceMatrixService(GraphHopper hopper, MatrixAlgorithmFactory matrixAlgorithmFactory){
        this.hopper = hopper;
        this.matrixAlgorithmFactory = matrixAlgorithmFactory;
    }

    /**
     * Calculates a distance matrix based on the given request.
     */
    public GHMatrixResponse calculateMatrix(GHMatrixRequest request){


        AlgorithmOptions.Builder algoOptsBuilder =  buildOptions(request, hopper);


        AlgorithmOptions tmpOptions = algoOptsBuilder.build();


        Graph routingGraph = hopper.getGraphHopperStorage();
        if(hopper.isCHEnabled()) {
            routingGraph = hopper.getGraphHopperStorage().getGraph(CHGraph.class, tmpOptions.getWeighting());
        }

        QueryGraph queryGraph = new QueryGraph(routingGraph);
        Weighting weighting = hopper.createTurnWeighting(
                queryGraph,
                tmpOptions.getFlagEncoder(),
                tmpOptions.getWeighting(),
                tmpOptions.getTraversalMode());

        AlgorithmOptions algoOpts = algoOptsBuilder.weighting(weighting).build();

        List<QueryResult> originQNodes = lookupNodes(request.getOrigins(), algoOpts.getFlagEncoder());
        List<QueryResult> destinationQNodes = lookupNodes(request.getDestinations(), algoOpts.getFlagEncoder());

        List<QueryResult> merged = new ArrayList<>(originQNodes);
        merged.addAll(destinationQNodes);

        queryGraph.lookup(merged);

        int[] originNodes =  mapToNodes(originQNodes);
        int[] destinationNodes =  mapToNodes(destinationQNodes);

        MatrixAlgorithm algorithm = matrixAlgorithmFactory.createAlgo(queryGraph, algoOpts);
        DistanceMatrix matrix = algorithm.calcMatrix(originNodes, destinationNodes);

        GHMatrixResponse response = toResponse(matrix, request);

        return response;
    }

    /**
     * Creates a GHMatrixResponse from the given matrix
     */
    private GHMatrixResponse toResponse(DistanceMatrix matrix, GHMatrixRequest request){
        GHMatrixResponse response = new GHMatrixResponse(matrix);
        return response;
    }



    /**
     * Builds the AlgorithmOptions
     * // TODO Refactor: The following is almost copy & paste from GraphHopper.calcPoints()
     *
     * @param request The matrix request
     * @param hopper Instance of hopper
     * @return
     */
    private AlgorithmOptions.Builder buildOptions(GHMatrixRequest request, GraphHopper hopper){

        String algoStr = request.getAlgorithm().isEmpty() ? MATRIX_ONE_TO_ONE : request.getAlgorithm();


        TraversalMode tMode;
        String tModeStr = request.getHints().get("traversal_mode", TraversalMode.NODE_BASED.toString());
        try
        {
            tMode = TraversalMode.fromString(tModeStr);
        } catch (Exception ex)
        {
            throw new IllegalStateException("Invalid TraversalMode");
        }

        FlagEncoder encoder = hopper.getEncodingManager().getEncoder(request.getVehicle());

        RoutingAlgorithmFactory tmpAlgoFactory = hopper.getAlgorithmFactory(request.getHints());
        Weighting weighting;

        if (hopper.getCHFactoryDecorator().isEnabled())
        {
            if (tmpAlgoFactory instanceof PrepareContractionHierarchies){
                weighting = ((PrepareContractionHierarchies) tmpAlgoFactory).getWeighting();

            }else {
                throw new IllegalStateException("Although CH was enabled a non-CH algorithm factory was returned " + tmpAlgoFactory);
            }
        } else
            weighting = hopper.createWeighting(request.getHints(), encoder);

        int maxVisitedNodes = Integer.MAX_VALUE;
        int maxVisistedNodesForRequest = request.getHints().getInt("routing.maxVisitedNodes", maxVisitedNodes);
        if (maxVisistedNodesForRequest > maxVisitedNodes)
        {
            throw new IllegalStateException("The routing.maxVisitedNodes parameter has to be below or equal to:" + maxVisitedNodes);
        }

        return AlgorithmOptions.start().
                algorithm(algoStr)
                .traversalMode(tMode)
                .flagEncoder(encoder)
                .weighting(weighting)
                .maxVisitedNodes(maxVisistedNodesForRequest)
                .hints(request.getHints());
    }


    private List<QueryResult> lookupNodes(List<GHPoint> points, FlagEncoder encoder)
    {
        if(points == null) throw new IllegalArgumentException("points must not be Null");
        if(encoder == null) throw new IllegalArgumentException("encoder must not be Null");

        List<QueryResult> nodes = new ArrayList<>();

        LocationIndex locationIndex = hopper.getLocationIndex();
        EdgeFilter edgeFilter = new DefaultEdgeFilter(encoder);

        for (GHPoint point : points ) {
            nodes.add(locationIndex.findClosest(point.lat, point.lon, edgeFilter));
        }

        if(nodes.size() != points.size()){
            throw new IllegalStateException("Could not find nodes for all points!");
        }

        return nodes;
    }

    private int[] mapToNodes(List<QueryResult> nodeQueryResults){
        int[] nodes = new int[nodeQueryResults.size()];
        for (int i=0;i<nodes.length;i++) {
            nodes[i] = nodeQueryResults.get(i).getClosestNode();
        }
        return nodes;
    }


}
