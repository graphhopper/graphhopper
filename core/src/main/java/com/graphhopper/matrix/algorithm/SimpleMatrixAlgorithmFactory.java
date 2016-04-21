package com.graphhopper.matrix.algorithm;

import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.storage.Graph;

/**
 * A basic MatrixAlgorithmFactory implementation supporting built-in algorithms.
 *
 * @author Pascal Büttiker
 */
public class SimpleMatrixAlgorithmFactory implements MatrixAlgorithmFactory {


    @Override
    public MatrixAlgorithm build(Graph g, AlgorithmOptions opts) {

        String algoStr = opts.getAlgorithm();

        if(MatrixAlgorithm.OneToOneLoop.equalsIgnoreCase(algoStr)){

            AlgorithmOptions underlyingAlgo = AlgorithmOptions.start(opts).
                    algorithm(AlgorithmOptions.DIJKSTRA_BI).build();

            return new OneToOneLoopMatrixAlgorithm(g, opts.getFlagEncoder(), opts.getWeighting(), opts.getTraversalMode(), underlyingAlgo);

        }else if(MatrixAlgorithm.OneToManyDijkstra.equalsIgnoreCase(algoStr)){
            throw new IllegalStateException("Not Implemented!"); // TODO Implement OneToManyDijkstra
        }else{
            throw new IllegalArgumentException("MatrixAlgorithm " + algoStr + " not found in " + getClass().getName());
        }
    }
}
