package com.graphhopper.matrix.algorithm;

import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.storage.Graph;

import static com.graphhopper.util.Parameters.Algorithms.*;

/**
 * A basic MatrixAlgorithmFactory implementation supporting built-in algorithms.
 *
 * @author Pascal Büttiker
 */
public class SimpleMatrixAlgorithmFactory implements MatrixAlgorithmFactory {


    @Override
    public MatrixAlgorithm createAlgo(Graph g, AlgorithmOptions opts) {

        String algoStr = opts.getAlgorithm();

        if(MATRIX_ONE_TO_ONE.equalsIgnoreCase(algoStr)){

            AlgorithmOptions underlyingAlgo = AlgorithmOptions
                    .start(opts).algorithm(ASTAR_BI)
                    .build();

            // Ensure we don't do unnecessary work
            underlyingAlgo.getHints().put("calc_paths", false);
            underlyingAlgo.getHints().put("instructions", false);

            return new OneToOneLoopMatrixAlgorithm(g, opts.getFlagEncoder(), opts.getWeighting(), opts.getTraversalMode(), underlyingAlgo);

        }else{
            throw new IllegalArgumentException("MatrixAlgorithm " + algoStr + " not found in " + getClass().getName());
        }
    }
}
