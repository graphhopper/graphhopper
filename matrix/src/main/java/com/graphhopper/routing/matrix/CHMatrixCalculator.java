/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.graphhopper.routing.matrix;

import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.matrix.algorithm.MatrixAlgorithm;
import com.graphhopper.routing.matrix.algorithm.MatrixRoutingAlgorithmFactory;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.exceptions.MaximumNodesExceededException;

import java.util.List;

import static com.graphhopper.util.Parameters.Routing.MAX_VISITED_NODES;

public class CHMatrixCalculator implements MatrixCalculator {

    private final MatrixRoutingAlgorithmFactory algoFactory;
    private final AlgorithmOptions algoOpts;
    private String debug;
    private int visitedNodes;

    public CHMatrixCalculator(MatrixRoutingAlgorithmFactory algoFactory, AlgorithmOptions algoOpts) {
        this.algoFactory = algoFactory;
        this.algoOpts = algoOpts;
    }

    @Override
    public DistanceMatrix calcMatrix(List<Snap> origins, List<Snap> destinations) {
        MatrixAlgorithm algo = createAlgo();
        return calcMatrix(origins, destinations, algo);
    }

    private DistanceMatrix calcMatrix(List<Snap> origins, List<Snap> destinations, MatrixAlgorithm algo) {
        StopWatch sw = new StopWatch().start();
        DistanceMatrix matrix = algo.calcMatrix(origins, destinations);

        int maxVisitedNodes = algoOpts.getHints().getInt(MAX_VISITED_NODES, Integer.MAX_VALUE);
        if (algo.getVisitedNodes() >= maxVisitedNodes)
            throw new MaximumNodesExceededException("No path found due to maximum nodes exceeded " + maxVisitedNodes, maxVisitedNodes);
        visitedNodes = algo.getVisitedNodes();
        debug += ", " + algo.getName() + "-routing:" + sw.stop().getMillis() + " ms";
        System.out.println(debug);
        return matrix;
    }

    private MatrixAlgorithm createAlgo() {
        StopWatch sw = new StopWatch().start();
        MatrixAlgorithm algo = algoFactory.createAlgo(algoOpts);
        debug = ", algoInit:" + (sw.stop().getNanos() / 1000) + " μs";
        return algo;
    }

    @Override
    public String getDebugString() {
        return debug;
    }

    @Override
    public int getVisitedNodes() {
        return visitedNodes;
    }

}