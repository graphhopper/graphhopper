package com.graphhopper.routing.matrix.algorithm;

import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.BidirRoutingAlgorithm;
import com.graphhopper.routing.matrix.algorithm.MatrixAlgorithm;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.querygraph.QueryRoutingCHGraph;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.RoutingCHGraph;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;


public class MatrixRoutingAlgorithmFactory {
    private final QueryRoutingCHGraph routingCHGraph;

    public MatrixRoutingAlgorithmFactory(RoutingCHGraph routingCHGraph, QueryGraph queryGraph) {
        this.routingCHGraph = new QueryRoutingCHGraph(routingCHGraph, queryGraph);
    }


    public MatrixAlgorithm createAlgo(AlgorithmOptions opts) {
        // CHECK hints instructions and calc_points are false

        String defaultAlgo = Parameters.Algorithms.DIJKSTRA_MANY_TO_MANY;
        String algo = opts.getAlgorithm();
        if (Helper.isEmpty(algo))
            algo = defaultAlgo;
        if (Parameters.Algorithms.DIJKSTRA_MANY_TO_MANY.equals(algo)) {
            if(opts.getTraversalMode() == TraversalMode.NODE_BASED){
               return new ManyToManyNode(routingCHGraph);
            }else{
                return new ManyToManyEdge(routingCHGraph);
            }
        } else {
            throw new IllegalArgumentException("Algorithm " + algo + " not supported for Matrix calculation.");
        }
    }

}
