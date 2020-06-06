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

package com.graphhopper.routing;

import com.carrotsearch.hppc.IntSet;
import com.graphhopper.routing.weighting.AvoidEdgesWeighting;
import com.graphhopper.util.Parameters;

/**
 * This path calculator allows calculating a path with a set of avoided edges
 */
public class PathCalculatorWithAvoidedEdges {
    private final FlexiblePathCalculator pathCalculator;
    private final AvoidEdgesWeighting avoidPreviousPathsWeighting;
    private int visitedNodes;

    public PathCalculatorWithAvoidedEdges(FlexiblePathCalculator pathCalculator) {
        this.pathCalculator = pathCalculator;
        // we make the path calculator use our avoid edges weighting
        avoidPreviousPathsWeighting = new AvoidEdgesWeighting(pathCalculator.getAlgoOpts().getWeighting())
                .setEdgePenaltyFactor(5);
        AlgorithmOptions algoOpts = AlgorithmOptions.start(pathCalculator.getAlgoOpts()).
                algorithm(Parameters.Algorithms.ASTAR_BI).
                weighting(avoidPreviousPathsWeighting).build();
        algoOpts.getHints().putObject(Parameters.Algorithms.AStarBi.EPSILON, 2);
        pathCalculator.setAlgoOpts(algoOpts);
    }

    public Path calcPath(int from, int to, IntSet avoidedEdges) {
        Path path = pathCalculator.calcPaths(from, to, new EdgeRestrictions()).get(0);
        avoidPreviousPathsWeighting.setAvoidedEdges(avoidedEdges);
        visitedNodes = pathCalculator.getVisitedNodes();
        return path;
    }

    public int getVisitedNodes() {
        return visitedNodes;
    }
}
