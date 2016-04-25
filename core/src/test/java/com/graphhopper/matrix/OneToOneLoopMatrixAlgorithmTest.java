package com.graphhopper.matrix;

import com.graphhopper.matrix.algorithm.MatrixAlgorithm;
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
                algorithm(MatrixAlgorithm.OneToOneLoop)
                .traversalMode(TraversalMode.NODE_BASED)
                .flagEncoder(carEncoder)
                .weighting(new ShortestWeighting(carEncoder))
                .build();
    }


}
