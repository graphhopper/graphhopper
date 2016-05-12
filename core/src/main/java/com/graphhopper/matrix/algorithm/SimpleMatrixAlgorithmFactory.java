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

        if(AlgorithmOptions.MATRIX_ONE_TO_ONE.equalsIgnoreCase(algoStr)){

            AlgorithmOptions underlyingAlgo = AlgorithmOptions.start(opts).
                    algorithm(AlgorithmOptions.ASTAR_BI).build();

            return new OneToOneLoopMatrixAlgorithm(g, opts.getFlagEncoder(), opts.getWeighting(), opts.getTraversalMode(), underlyingAlgo);

        }else{
            throw new IllegalArgumentException("MatrixAlgorithm " + algoStr + " not found in " + getClass().getName());
        }
    }
}
