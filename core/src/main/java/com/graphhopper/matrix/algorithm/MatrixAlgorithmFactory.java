package com.graphhopper.matrix.algorithm;

import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.storage.Graph;

/**
 * Builds MatrixAlgorithms according to specified parameters.
 *
 * @author Pascal Büttiker
 */
public interface MatrixAlgorithmFactory {

    /**
     * Creates a MatrixAlgorithm instance using the given options
     * @param g The Graph to run the algorithm on
     * @param opts The options for the algorithm
     */
    MatrixAlgorithm createAlgo(Graph g, AlgorithmOptions opts );

}
