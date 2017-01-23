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

import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.RoutingAlgorithmFactory;
import com.graphhopper.routing.RoutingAlgorithmFactoryDecorator;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters.Landmark;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This class implements the A*, landmark and triangulation (ALT) decorator.
 *
 * @author Peter Karich
 */
public class LMAlgoFactoryDecorator implements RoutingAlgorithmFactoryDecorator {
    private int landmarkCount = 8;
    private int activeLandmarkCount = landmarkCount / 2;
    private final List<PrepareLandmarks> preparations = new ArrayList<>();
    private final List<String> weightingsAsStrings = new ArrayList<>();
    private final List<Weighting> weightings = new ArrayList<>();
    private boolean enabled = false;
    private boolean disablingAllowed = false;

    @Override
    public void init(CmdArgs args) {
        landmarkCount = args.getInt(Landmark.COUNT, landmarkCount);
        activeLandmarkCount = args.getInt(Landmark.ACTIVE_COUNT_DEFAULT, Math.max(2, landmarkCount / 2));
        String lmWeightingsStr = args.get("prepare.lm.weightings", "");
        if (!lmWeightingsStr.isEmpty()) {
            List<String> tmpLMWeightingList = Arrays.asList(lmWeightingsStr.split(","));
            setWeightingsAsStrings(tmpLMWeightingList);
        }

        boolean enableThis = !weightingsAsStrings.isEmpty();
        setEnabled(enableThis);
        if (enableThis)
            setDisablingAllowed(args.getBool(Landmark.INIT_DISABLING_ALLOWED, isDisablingAllowed()));
    }

    public int getLandmarks() {
        return landmarkCount;
    }

    public void setDisablingAllowed(boolean disablingAllowed) {
        this.disablingAllowed = disablingAllowed;
    }

    public final boolean isDisablingAllowed() {
        return disablingAllowed || !isEnabled();
    }

    /**
     * Enables or disables this decorator. This speed-up mode is disabled by default.
     */
    public final void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public final boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables the use of contraction hierarchies to reduce query times. Enabled by default.
     *
     * @param weightingList A list containing multiple weightings like: "fastest", "shortest" or
     *                      your own weight-calculation type.
     */
    public LMAlgoFactoryDecorator setWeightingsAsStrings(List<String> weightingList) {
        if (weightingList.isEmpty())
            throw new IllegalArgumentException("It is not allowed to pass an emtpy weightingList");

        weightingsAsStrings.clear();
        for (String strWeighting : weightingList) {
            strWeighting = strWeighting.toLowerCase();
            strWeighting = strWeighting.trim();
            addWeighting(strWeighting);
        }
        return this;
    }

    public List<String> getWeightingsAsStrings() {
        if (this.weightingsAsStrings.isEmpty())
            throw new IllegalStateException("Potential bug: weightingsAsStrings is empty");

        return this.weightingsAsStrings;
    }

    public LMAlgoFactoryDecorator addWeighting(String weighting) {
        weightingsAsStrings.add(weighting);
        return this;
    }

    /**
     * Decouple weightings from PrepareLandmarks as we need weightings for the graphstorage and the
     * graphstorage for the preparation.
     */
    public LMAlgoFactoryDecorator addWeighting(Weighting weighting) {
        weightings.add(weighting);
        return this;
    }

    public LMAlgoFactoryDecorator addPreparation(PrepareLandmarks pch) {
        preparations.add(pch);
        int lastIndex = preparations.size() - 1;
        if (lastIndex >= weightings.size())
            throw new IllegalStateException("Cannot access weighting for PrepareLandmarks with " + pch.getWeighting()
                    + ". Call add(Weighting) before");

        if (preparations.get(lastIndex).getWeighting() != weightings.get(lastIndex))
            throw new IllegalArgumentException("Weighting of PrepareContractionHierarchies " + preparations.get(lastIndex).getWeighting()
                    + " needs to be identical to previously added " + weightings.get(lastIndex));
        return this;
    }

    public boolean hasWeightings() {
        return !weightings.isEmpty();
    }

    public boolean hasPreparations() {
        return !preparations.isEmpty();
    }

    public int size() {
        return preparations.size();
    }

    public List<Weighting> getWeightings() {
        return weightings;
    }

    public List<PrepareLandmarks> getPreparations() {
        return preparations;
    }

    @Override
    public RoutingAlgorithmFactory getDecoratedAlgorithmFactory(RoutingAlgorithmFactory defaultAlgoFactory, HintsMap map) {
        if (preparations.isEmpty())
            throw new IllegalStateException("No preparations added to this decorator");

        for (final PrepareLandmarks p : preparations) {
            if (p.getWeighting().matches(map))
                return createRAFactory(p, defaultAlgoFactory);
        }

        // if the initial encoder&weighting has certain properies we can even cross query it unlike CH        
        return createRAFactory(preparations.get(0), defaultAlgoFactory);
    }

    private RoutingAlgorithmFactory createRAFactory(final PrepareLandmarks p,
                                                    final RoutingAlgorithmFactory defaultAlgoFactory) {
        return new RoutingAlgorithmFactory() {
            @Override
            public RoutingAlgorithm createAlgo(Graph g, AlgorithmOptions opts) {
                RoutingAlgorithm algo = defaultAlgoFactory.createAlgo(g, opts);
                boolean disable = opts.getHints().getBool(Landmark.DISABLE, false);
                if (disablingAllowed && disable)
                    return algo;

                return p.getDecoratedAlgorithm(g, algo, opts);
            }
        };
    }

    public void createPreparations(GraphHopperStorage ghStorage, TraversalMode traversalMode) {
        if (!isEnabled() || !preparations.isEmpty())
            return;
        if (weightings.isEmpty())
            throw new IllegalStateException("No landmark weightings found");

        for (Weighting weighting : getWeightings()) {
            PrepareLandmarks tmpPrepareLM = new PrepareLandmarks(ghStorage.getDirectory(), ghStorage,
                    weighting, traversalMode, landmarkCount, activeLandmarkCount);

            addPreparation(tmpPrepareLM);
        }
    }

    public void loadOrDoWork() {
        for (PrepareLandmarks plm : preparations) {
            if (!plm.loadExisting())
                plm.doWork();
        }
    }
}
