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

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.RoutingAlgorithmFactory;
import com.graphhopper.routing.RoutingAlgorithmFactoryDecorator;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.AbstractWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.StorableProperties;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.Parameters.Landmark;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;

import static com.graphhopper.util.Parameters.Landmark.DISABLE;

/**
 * This class implements the A*, landmark and triangulation (ALT) decorator.
 *
 * @author Peter Karich
 */
public class LMAlgoFactoryDecorator implements RoutingAlgorithmFactoryDecorator {
    private Logger LOGGER = LoggerFactory.getLogger(LMAlgoFactoryDecorator.class);
    private int landmarkCount = 16;
    private int activeLandmarkCount = 8;

    private final List<PrepareLandmarks> preparations = new ArrayList<>();
    // input weighting list from configuration file
    // one such entry can result into multiple Weighting objects e.g. fastest & car,foot => fastest|car and fastest|foot
    private final List<String> weightingsAsStrings = new ArrayList<>();
    private final List<Weighting> weightings = new ArrayList<>();
    private final Map<String, Double> maximumWeights = new HashMap<>();
    private boolean enabled = false;
    private boolean disablingAllowed = false;
    private final List<String> lmSuggestionsLocations = new ArrayList<>(5);
    private int preparationThreads;
    private ExecutorService threadPool;

    public LMAlgoFactoryDecorator() {
        setPreparationThreads(1);
    }

    @Override
    public void init(CmdArgs args) {
        setPreparationThreads(args.getInt(Parameters.Landmark.PREPARE + "threads", getPreparationThreads()));

        landmarkCount = args.getInt(Parameters.Landmark.COUNT, landmarkCount);
        activeLandmarkCount = args.getInt(Landmark.ACTIVE_COUNT_DEFAULT, Math.min(8, landmarkCount));
        for (String loc : args.get("prepare.lm.suggestions_location", "").split(",")) {
            if (!loc.trim().isEmpty())
                lmSuggestionsLocations.add(loc.trim());
        }
        String lmWeightingsStr = args.get(Landmark.PREPARE + "weightings", "");
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

    public LMAlgoFactoryDecorator setDisablingAllowed(boolean disablingAllowed) {
        this.disablingAllowed = disablingAllowed;
        return this;
    }

    public final boolean isDisablingAllowed() {
        return disablingAllowed || !isEnabled();
    }

    /**
     * Enables or disables this decorator. This speed-up mode is disabled by default.
     */
    public final LMAlgoFactoryDecorator setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    @Override
    public final boolean isEnabled() {
        return enabled;
    }

    public int getPreparationThreads() {
        return preparationThreads;
    }

    /**
     * This method changes the number of threads used for preparation on import. Default is 1. Make
     * sure that you have enough memory when increasing this number!
     */
    public void setPreparationThreads(int preparationThreads) {
        this.preparationThreads = preparationThreads;
        this.threadPool = java.util.concurrent.Executors.newFixedThreadPool(preparationThreads);
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
        String str[] = weighting.split("\\|");
        double value = -1;
        if (str.length > 1) {
            PMap map = new PMap(weighting);
            value = map.getDouble("maximum", -1);
        }

        weightingsAsStrings.add(str[0]);
        maximumWeights.put(str[0], value);
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
        boolean forceFlexMode = map.getBool(DISABLE, false);
        if (!isEnabled() || disablingAllowed && forceFlexMode)
            return defaultAlgoFactory;

        if (preparations.isEmpty())
            throw new IllegalStateException("No preparations added to this decorator");

        for (final PrepareLandmarks p : preparations) {
            if (p.getWeighting().matches(map))
                return new LMRAFactory(p, defaultAlgoFactory);
        }

        // if the initial encoder&weighting has certain properies we could cross query it but for now avoid this
        return defaultAlgoFactory;
    }

    /**
     * TODO needs to be public to pick defaultAlgoFactory.weighting if the defaultAlgoFactory is a CH one.
     *
     * @see com.graphhopper.GraphHopper#calcPaths(GHRequest, GHResponse)
     */
    public static class LMRAFactory implements RoutingAlgorithmFactory {
        private RoutingAlgorithmFactory defaultAlgoFactory;
        private PrepareLandmarks p;

        public LMRAFactory(PrepareLandmarks p, RoutingAlgorithmFactory defaultAlgoFactory) {
            this.defaultAlgoFactory = defaultAlgoFactory;
            this.p = p;
        }

        public RoutingAlgorithmFactory getDefaultAlgoFactory() {
            return defaultAlgoFactory;
        }

        @Override
        public RoutingAlgorithm createAlgo(Graph g, AlgorithmOptions opts) {
            RoutingAlgorithm algo = defaultAlgoFactory.createAlgo(g, opts);
            return p.getDecoratedAlgorithm(g, algo, opts);
        }
    }

    /**
     * This method calculates the landmark data for all weightings (optionally in parallel) or if already existent loads it.
     *
     * @return true if the preparation data for at least one weighting was calculated.
     * @see com.graphhopper.routing.ch.CHAlgoFactoryDecorator#prepare(StorableProperties) for a very similar method
     */
    public boolean loadOrDoWork(final StorableProperties properties) {
        ExecutorCompletionService completionService = new ExecutorCompletionService<>(threadPool);
        int counter = 0;
        int submittedPreparations = 0;
        boolean prepared = false;
        for (final PrepareLandmarks plm : preparations) {
            counter++;
            if (plm.loadExisting())
                continue;

            prepared = true;
            LOGGER.info(counter + "/" + getPreparations().size() + " calling LM prepare.doWork for " + plm.getWeighting() + " ... (" + Helper.getMemInfo() + ")");
            final String name = AbstractWeighting.weightingToFileName(plm.getWeighting());
            completionService.submit(new Runnable() {
                @Override
                public void run() {
                    String errorKey = Landmark.PREPARE + "error." + name;
                    try {
                        Thread.currentThread().setName(name);
                        properties.put(errorKey, "LM preparation incomplete");
                        plm.doWork();
                        properties.remove(errorKey);
                        properties.put(Landmark.PREPARE + "date." + name, Helper.createFormatter().format(new Date()));
                    } catch (Exception ex) {
                        LOGGER.error("Problem while LM preparation " + name, ex);
                        properties.put(errorKey, ex.getMessage());
                    }
                }
            }, name);
            submittedPreparations++;
        }

        threadPool.shutdown();
        try {
            for (int i = 0; i < submittedPreparations; i++) {
                completionService.take().get();
            }
        } catch (Exception e) {
            threadPool.shutdownNow();
            throw new RuntimeException(e);
        }
        return prepared;
    }

    /**
     * This method creates the landmark storages ready for landmark creation.
     */
    public void createPreparations(GraphHopperStorage ghStorage, TraversalMode traversalMode, LocationIndex locationIndex) {
        if (!isEnabled() || !preparations.isEmpty())
            return;
        if (weightings.isEmpty())
            throw new IllegalStateException("No landmark weightings found");

        List<LandmarkSuggestion> lmSuggestions = new ArrayList<>(lmSuggestionsLocations.size());
        if (!lmSuggestionsLocations.isEmpty()) {
            try {
                for (String loc : lmSuggestionsLocations) {
                    lmSuggestions.add(LandmarkSuggestion.readLandmarks(loc, locationIndex));
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }

        for (Weighting weighting : getWeightings()) {
            Double maximumWeight = maximumWeights.get(weighting.getName());
            if (maximumWeight == null)
                throw new IllegalStateException("maximumWeight cannot be null. Default should be just negative. " +
                        "Couldn't find " + weighting.getName() + " in " + maximumWeights);

            PrepareLandmarks tmpPrepareLM = new PrepareLandmarks(ghStorage.getDirectory(), ghStorage,
                    weighting, traversalMode, landmarkCount, activeLandmarkCount).
                    setLandmarkSuggestions(lmSuggestions).
                    setMaximumWeight(maximumWeight);

            addPreparation(tmpPrepareLM);
        }
    }
}
