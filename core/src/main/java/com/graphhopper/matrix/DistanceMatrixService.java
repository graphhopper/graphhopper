package com.graphhopper.matrix;

import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.matrix.algorithm.MatrixAlgorithm;
import com.graphhopper.matrix.algorithm.MatrixAlgorithmFactory;
import com.graphhopper.matrix.algorithm.SimpleMatrixAlgorithmFactory;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.QueryGraph;
import com.graphhopper.routing.RoutingAlgorithmFactory;
import com.graphhopper.routing.ch.CHAlgoFactoryDecorator;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.shapes.GHPoint;

import java.util.ArrayList;
import java.util.List;

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
        Weighting weighting = hopper.createTurnWeighting(tmpOptions.getWeighting(), queryGraph, tmpOptions.getFlagEncoder());
        AlgorithmOptions algoOpts = algoOptsBuilder.weighting(weighting).build();

        List<QueryResult> originQNodes = lookupNodes(request.getOrigins(), algoOpts.getFlagEncoder());
        List<QueryResult> destinationQNodes = lookupNodes(request.getDestinations(), algoOpts.getFlagEncoder());

        List<QueryResult> merged = new ArrayList<>(originQNodes);
        merged.addAll(destinationQNodes);

        queryGraph.lookup(merged);

        List<Integer> originNodes =  mapToNodes(originQNodes);
        List<Integer> destinationNodes =  mapToNodes(destinationQNodes);

        MatrixAlgorithm algorithm = matrixAlgorithmFactory.build(queryGraph, algoOpts);
        DistanceMatrix matrix = algorithm.calcMatrix(originNodes, destinationNodes);

        GHMatrixResponse response = toResponse(matrix, request);

        return response;
    }

    /**
     * Maps the internal node based DistanceMatrix to more meaningful GHPoints
     *
     */
    private GHMatrixResponse toResponse(DistanceMatrix matrix, GHMatrixRequest request){
        GHMatrixResponse response = new GHMatrixResponse();

        for(int i=0; i< matrix.getRows().size(); i++){
            DistanceMatrix.DistanceRow r = matrix.getRow(i);
            GHMatrixResponse.GHMatrixDistanceRow ghRow = response.addRow(request.getOrigins().get(i));
            for(int j=0; j< r.getDestinations().size(); j++){
               DistanceMatrix.DestinationInfo d = r.getDestinations().get(j);
                GHMatrixResponse.GHMatrixDestinationInfo destination = ghRow.getDestinations().get(j);
                ghRow.addDestination(destination.destination, d.distance, d.time);
            }
        }
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

        String algoStr = request.getAlgorithm().isEmpty() ? AlgorithmOptions.MATRIX_ONE_TO_ONE : request.getAlgorithm();


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


        GHResponse dummy = new GHResponse();    // TODO Improve / refactor lookup - API
        List<QueryResult> nodes = hopper.lookup(points, encoder, dummy);

        if(nodes.size() != points.size()){
            throw new IllegalStateException("Could not find nodes for all points!");
        }

        return nodes;
    }

    private List<Integer> mapToNodes(List<QueryResult> nodeQueryResults){
        List<Integer> nodes = new ArrayList<>();
        for (QueryResult r : nodeQueryResults) {
            nodes.add(r.getClosestNode());
        }
        return nodes;
    }


}
