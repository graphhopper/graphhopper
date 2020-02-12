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

package com.graphhopper.routing.lm;

import com.graphhopper.routing.*;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.Parameters;

public class LMRoutingAlgorithmFactory implements RoutingAlgorithmFactory {
    private final RoutingAlgorithmFactory defaultAlgoFactory;
    private final LandmarkStorage lms;
    private final Weighting prepareWeighting;
    private final int defaultActiveLandmarks;
    private final int numBaseNodes;

    public LMRoutingAlgorithmFactory(LandmarkStorage lms, int defaultActiveLandmarks, int numBaseNodes) {
        this.defaultAlgoFactory = new RoutingAlgorithmFactorySimple();
        this.lms = lms;
        this.defaultActiveLandmarks = defaultActiveLandmarks;
        this.prepareWeighting = lms.getWeighting();
        this.numBaseNodes = numBaseNodes;
    }

    @Override
    public RoutingAlgorithm createAlgo(Graph g, AlgorithmOptions opts) {
        RoutingAlgorithm algo = defaultAlgoFactory.createAlgo(g, opts);
        return getPreparedRoutingAlgorithm(g, algo, opts);
    }

    private RoutingAlgorithm getPreparedRoutingAlgorithm(Graph qGraph, RoutingAlgorithm algo, AlgorithmOptions opts) {
        int activeLM = Math.max(1, opts.getHints().getInt(Parameters.Landmark.ACTIVE_COUNT, defaultActiveLandmarks));
        if (algo instanceof AStar) {
            if (!lms.isInitialized())
                throw new IllegalStateException("Initialize landmark storage before creating algorithms");

            double epsilon = opts.getHints().getDouble(Parameters.Algorithms.AStar.EPSILON, 1);
            AStar astar = (AStar) algo;
            astar.setApproximation(new LMApproximator(qGraph, prepareWeighting, numBaseNodes, lms, activeLM, lms.getFactor(), false).
                    setEpsilon(epsilon));
            return algo;
        } else if (algo instanceof AStarBidirection) {
            if (!lms.isInitialized())
                throw new IllegalStateException("Initialize landmark storage before creating algorithms");

            double epsilon = opts.getHints().getDouble(Parameters.Algorithms.AStarBi.EPSILON, 1);
            AStarBidirection astarbi = (AStarBidirection) algo;
            astarbi.setApproximation(new LMApproximator(qGraph, prepareWeighting, numBaseNodes, lms, activeLM, lms.getFactor(), false).
                    setEpsilon(epsilon));
            return algo;
        } else if (algo instanceof AlternativeRoute) {
            if (!lms.isInitialized())
                throw new IllegalStateException("Initialize landmark storage before creating algorithms");

            double epsilon = opts.getHints().getDouble(Parameters.Algorithms.AStarBi.EPSILON, 1);
            AlternativeRoute altRoute = (AlternativeRoute) algo;
            altRoute.setApproximation(new LMApproximator(qGraph, prepareWeighting, numBaseNodes, lms, activeLM, lms.getFactor(), false).
                    setEpsilon(epsilon));
            // landmark algorithm follows good compromise between fast response and exploring 'interesting' paths so we
            // can decrease this exploration factor further (1->dijkstra, 0.8->bidir. A*)
            altRoute.setMaxExplorationFactor(0.6);
        }
        return algo;
    }
}
