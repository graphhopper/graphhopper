package com.graphhopper.matrix;

import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.util.ShortestWeighting;
import com.graphhopper.routing.util.TraversalMode;


/**
 * Tests the OneToOneLoopMatrixAlgorithm
 */
public class OneToOneLoopMatrixAlgorithmTest extends AbstractMatrixAlgorithmTest {

    @Override
    protected AlgorithmOptions getMatrixAlgorithmOptions() {
        return AlgorithmOptions.start().
                algorithm(AlgorithmOptions.MATRIX_ONE_TO_ONE)
                .traversalMode(TraversalMode.NODE_BASED)
                .flagEncoder(carEncoder)
                .weighting(new ShortestWeighting(carEncoder))
                .build();
    }


}
