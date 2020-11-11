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

import com.graphhopper.routing.util.AbstractAlgoPreparation;
import com.graphhopper.routing.util.spatialrules.SpatialRuleLookup;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.Helper;
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
    private final LMConfig lmConfig;
    private long totalPrepareTime;

    public PrepareLandmarks(Directory dir, GraphHopperStorage graph, LMConfig lmConfig, int landmarks) {
        this.graph = graph;
        this.lmConfig = lmConfig;
        lms = new LandmarkStorage(graph, dir, lmConfig, landmarks);
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

    public LandmarkStorage getLandmarkStorage() {
        return lms;
    }

    public int getSubnetworksWithLandmarks() {
        return lms.getSubnetworksWithLandmarks();
    }

    public LMConfig getLMConfig() {
        return lmConfig;
    }

    public boolean loadExisting() {
        return lms.loadExisting();
    }

    @Override
    public void doSpecificWork() {
        StopWatch sw = new StopWatch().start();
        LOGGER.info("Start calculating " + lms.getLandmarkCount() + " landmarks, weighting:" + lms.getLmSelectionWeighting() + ", " + Helper.getMemInfo());

        lms.createLandmarks();
        lms.flush();

        LOGGER.info("Calculated landmarks for " + (lms.getSubnetworksWithLandmarks() - 1) + " subnetworks, took:" + sw.stop().getSeconds() + " => "
                + lms.getLandmarksAsGeoJSON() + ", stored weights:" + lms.getLandmarkCount()
                + ", nodes:" + graph.getNodes() + ", " + Helper.getMemInfo());
        totalPrepareTime = sw.getMillis();
    }

    public long getTotalPrepareTime() {
        return totalPrepareTime;
    }

    /**
     * Convenience method to obtain a routing algo factory from the preparation.
     */
    public LMRoutingAlgorithmFactory getRoutingAlgorithmFactory() {
        return new LMRoutingAlgorithmFactory(getLandmarkStorage());
    }

    /**
     * Release landmark storage resources
     */
    void close() {
        this.lms.close();
    }
}
