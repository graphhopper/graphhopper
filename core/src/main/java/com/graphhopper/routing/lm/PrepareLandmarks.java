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
import com.graphhopper.routing.util.AbstractAlgoPreparation;
import com.graphhopper.routing.util.spatialrules.SpatialRuleLookup;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.Parameters.Landmark;
import com.graphhopper.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * This class does the preprocessing for the ALT algorithm (A* , landmark, triangle inequality).
 * <p>
 * http://www.siam.org/meetings/alenex05/papers/03agoldberg.pdf
 *
 * @author Peter Karich
 */
public class PrepareLandmarks extends AbstractAlgoPreparation {
    private static final Logger LOGGER = LoggerFactory.getLogger(PrepareLandmarks.class);
    private final Graph graph;
    private final LandmarkStorage lms;
    private final Weighting weighting;
    private int defaultActiveLandmarks;

    public PrepareLandmarks(Directory dir, GraphHopperStorage graph, Weighting weighting,
                            int landmarks, int activeLandmarks) {
        if (activeLandmarks > landmarks)
            throw new IllegalArgumentException("Default value for active landmarks " + activeLandmarks
                    + " should be less or equal to landmark count of " + landmarks);
        this.graph = graph;
        this.defaultActiveLandmarks = activeLandmarks;
        this.weighting = weighting;

        lms = new LandmarkStorage(graph, dir, weighting, landmarks);
    }

    /**
     * @see LandmarkStorage#setLandmarkSuggestions(List)
     */
    public PrepareLandmarks setLandmarkSuggestions(List<LandmarkSuggestion> landmarkSuggestions) {
        lms.setLandmarkSuggestions(landmarkSuggestions);
        return this;
    }

    /**
     * @see LandmarkStorage#setSpatialRuleLookup(SpatialRuleLookup)
     */
    public PrepareLandmarks setSpatialRuleLookup(SpatialRuleLookup ruleLookup) {
        lms.setSpatialRuleLookup(ruleLookup);
        return this;
    }

    /**
     * @see LandmarkStorage#setMaximumWeight(double)
     */
    public PrepareLandmarks setMaximumWeight(double maximumWeight) {
        lms.setMaximumWeight(maximumWeight);
        return this;
    }

    /**
     * @see LandmarkStorage#setLMSelectionWeighting(Weighting)
     */
    public void setLMSelectionWeighting(Weighting w) {
        lms.setLMSelectionWeighting(w);
    }

    /**
     * @see LandmarkStorage#setMinimumNodes(int)
     */
    public void setMinimumNodes(int nodes) {
        if (nodes < 2)
            throw new IllegalArgumentException("minimum node count must be at least 2");

        lms.setMinimumNodes(nodes);
    }

    public PrepareLandmarks setLogDetails(boolean logDetails) {
        lms.setLogDetails(logDetails);
        return this;
    }

    LandmarkStorage getLandmarkStorage() {
        return lms;
    }

    public int getSubnetworksWithLandmarks() {
        return lms.getSubnetworksWithLandmarks();
    }

    public Weighting getWeighting() {
        return weighting;
    }

    public boolean loadExisting() {
        return lms.loadExisting();
    }

    @Override
    public void doSpecificWork() {
        StopWatch sw = new StopWatch().start();
        LOGGER.info("Start calculating " + lms.getLandmarkCount() + " landmarks, default active lms:"
                + defaultActiveLandmarks + ", weighting:" + lms.getLmSelectionWeighting() + ", " + Helper.getMemInfo());

        lms.createLandmarks();
        lms.flush();

        LOGGER.info("Calculated landmarks for " + (lms.getSubnetworksWithLandmarks() - 1) + " subnetworks, took:" + sw.stop().getSeconds() + " => "
                + lms.getLandmarksAsGeoJSON() + ", stored weights:" + lms.getLandmarkCount()
                + ", nodes:" + graph.getNodes() + ", " + Helper.getMemInfo());
    }

    public RoutingAlgorithm getDecoratedAlgorithm(Graph qGraph, RoutingAlgorithm algo, AlgorithmOptions opts) {
        int activeLM = Math.max(1, opts.getHints().getInt(Landmark.ACTIVE_COUNT, defaultActiveLandmarks));
        if (algo instanceof AStar) {
            if (!lms.isInitialized())
                throw new IllegalStateException("Initialize landmark storage before creating algorithms");

            double epsilon = opts.getHints().getDouble(Parameters.Algorithms.AStar.EPSILON, 1);
            AStar astar = (AStar) algo;
            astar.setApproximation(new LMApproximator(qGraph, this.graph.getNodes(), lms, activeLM, lms.getFactor(), false).
                    setEpsilon(epsilon));
            return algo;
        } else if (algo instanceof AStarBidirection) {
            if (!lms.isInitialized())
                throw new IllegalStateException("Initialize landmark storage before creating algorithms");

            double epsilon = opts.getHints().getDouble(Parameters.Algorithms.AStarBi.EPSILON, 1);
            AStarBidirection astarbi = (AStarBidirection) algo;
            astarbi.setApproximation(new LMApproximator(qGraph, this.graph.getNodes(), lms, activeLM, lms.getFactor(), false).
                    setEpsilon(epsilon));
            return algo;
        } else if (algo instanceof AlternativeRoute) {
            if (!lms.isInitialized())
                throw new IllegalStateException("Initialize landmark storage before creating algorithms");

            double epsilon = opts.getHints().getDouble(Parameters.Algorithms.AStarBi.EPSILON, 1);
            AlternativeRoute altRoute = (AlternativeRoute) algo;
            altRoute.setApproximation(new LMApproximator(qGraph, this.graph.getNodes(), lms, activeLM, lms.getFactor(), false).
                    setEpsilon(epsilon));
            // landmark algorithm follows good compromise between fast response and exploring 'interesting' paths so we
            // can decrease this exploration factor further (1->dijkstra, 0.8->bidir. A*)
            altRoute.setMaxExplorationFactor(0.6);
        }

        return algo;
    }
}
