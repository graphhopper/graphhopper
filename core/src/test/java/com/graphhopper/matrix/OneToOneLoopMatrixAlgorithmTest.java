package com.graphhopper.matrix;

import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.util.ShortestWeighting;
import com.graphhopper.routing.util.TraversalMode;

import static com.graphhopper.util.Parameters.Algorithms.MATRIX_ONE_TO_ONE;


/**
 * Tests the OneToOneLoopMatrixAlgorithm
 */
public class OneToOneLoopMatrixAlgorithmTest extends AbstractMatrixAlgorithmTest {

    @Override
    protected AlgorithmOptions getMatrixAlgorithmOptions() {
        return AlgorithmOptions.start().
                algorithm(MATRIX_ONE_TO_ONE)
                .traversalMode(TraversalMode.NODE_BASED)
                .flagEncoder(carEncoder)
                .weighting(new ShortestWeighting(carEncoder))
                .build();
    }


}
