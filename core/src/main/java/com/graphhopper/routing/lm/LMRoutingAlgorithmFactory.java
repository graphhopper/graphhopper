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

import com.graphhopper.routing.AStar;
import com.graphhopper.routing.*;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Parameters;

import static com.graphhopper.util.Parameters.Algorithms.*;

public class LMRoutingAlgorithmFactory implements RoutingAlgorithmFactory {
    private final LandmarkStorage lms;
    private int defaultActiveLandmarks;

    public LMRoutingAlgorithmFactory(LandmarkStorage lms) {
        this.lms = lms;
        this.defaultActiveLandmarks = Math.max(1, Math.min(lms.getLandmarkCount() / 2, 12));
    }

    public LMRoutingAlgorithmFactory setDefaultActiveLandmarks(int defaultActiveLandmarks) {
        this.defaultActiveLandmarks = defaultActiveLandmarks;
        return this;
    }

    @Override
    public RoutingAlgorithm createAlgo(Graph g, Weighting w, AlgorithmOptions opts) {
        if (!lms.isInitialized())
            throw new IllegalStateException("Initialize landmark storage before creating algorithms");
        int activeLM = Math.max(1, opts.getHints().getInt(Parameters.Landmark.ACTIVE_COUNT, defaultActiveLandmarks));
        final String algoStr = opts.getAlgorithm();
        final Weighting weighting = g.wrapWeighting(w);
        if (ASTAR.equalsIgnoreCase(algoStr)) {
            double epsilon = opts.getHints().getDouble(Parameters.Algorithms.AStar.EPSILON, 1);
            AStar algo = new AStar(g, weighting, opts.getTraversalMode());
            algo.setApproximation(getApproximator(g, weighting, activeLM, epsilon));
            algo.setMaxVisitedNodes(opts.getMaxVisitedNodes());
            algo.setTimeoutMillis(opts.getTimeoutMillis());
            return algo;
        } else if (ASTAR_BI.equalsIgnoreCase(algoStr) || Helper.isEmpty(algoStr)) {
            double epsilon = opts.getHints().getDouble(Parameters.Algorithms.AStarBi.EPSILON, 1);
            AStarBidirection algo = new AStarBidirection(g, weighting, opts.getTraversalMode());
            algo.setApproximation(getApproximator(g, weighting, activeLM, epsilon));
            algo.setMaxVisitedNodes(opts.getMaxVisitedNodes());
            algo.setTimeoutMillis(opts.getTimeoutMillis());
            return algo;
        } else if (ALT_ROUTE.equalsIgnoreCase(algoStr)) {
            double epsilon = opts.getHints().getDouble(Parameters.Algorithms.AStarBi.EPSILON, 1);
            AlternativeRoute algo = new AlternativeRoute(g, weighting, opts.getTraversalMode(), opts.getHints());
            algo.setApproximation(getApproximator(g, weighting, activeLM, epsilon));
            algo.setMaxVisitedNodes(opts.getMaxVisitedNodes());
            algo.setTimeoutMillis(opts.getTimeoutMillis());
            return algo;
        } else {
            throw new IllegalArgumentException("Landmarks algorithm only supports algorithm="
                    + ASTAR + "," + ASTAR_BI + " or " + ALT_ROUTE + ", but got: " + algoStr);
        }
    }

    private LMApproximator getApproximator(Graph g, Weighting weighting, int activeLM, double epsilon) {
        return LMApproximator.forLandmarks(g, weighting, lms, activeLM).setEpsilon(epsilon);
    }
}
