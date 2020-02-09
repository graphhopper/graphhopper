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
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.RoutingAlgorithmFactory;
import com.graphhopper.routing.RoutingAlgorithmFactorySimple;
import com.graphhopper.routing.ch.CHPreparationHandler;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.routing.weighting.AbstractWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.StorableProperties;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.Parameters.Landmark;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.graphhopper.util.Helper.*;

/**
 * This class deals with the A*, landmark and triangulation (ALT) preparations.
 *
 * @author Peter Karich
 */
public class LMPreparationHandler {
    private Logger LOGGER = LoggerFactory.getLogger(LMPreparationHandler.class);
    private int landmarkCount = 16;
    private int activeLandmarkCount = 8;

    private final List<PrepareLandmarks> preparations = new ArrayList<>();
    // input weighting list from configuration file
    // one such entry can result into multiple Weighting objects e.g. fastest & car,foot => fastest|car and fastest|foot
    private final List<String> weightingsAsStrings = new ArrayList<>();
    private final List<Weighting> weightings = new ArrayList<>();
    private final Map<String, Double> maximumWeights = new HashMap<>();
    private boolean enabled = false;
    private int minNodes = -1;
    private boolean disablingAllowed = false;
    private final List<String> lmSuggestionsLocations = new ArrayList<>(5);
    private int preparationThreads;
    private ExecutorService threadPool;
    private boolean logDetails = false;

    public LMPreparationHandler() {
        setPreparationThreads(1);
    }

    public void init(GraphHopperConfig ghConfig) {
        setPreparationThreads(ghConfig.getInt(Parameters.Landmark.PREPARE + "threads", getPreparationThreads()));

        landmarkCount = ghConfig.getInt(Parameters.Landmark.COUNT, landmarkCount);
        activeLandmarkCount = ghConfig.getInt(Landmark.ACTIVE_COUNT_DEFAULT, Math.min(8, landmarkCount));
        logDetails = ghConfig.getBool(Landmark.PREPARE + "log_details", false);
        minNodes = ghConfig.getInt(Landmark.PREPARE + "min_network_size", -1);

        for (String loc : ghConfig.get(Landmark.PREPARE + "suggestions_location", "").split(",")) {
            if (!loc.trim().isEmpty())
                lmSuggestionsLocations.add(loc.trim());
        }
        String lmWeightingsStr = ghConfig.get(Landmark.PREPARE + "weightings", "");
        if (!lmWeightingsStr.isEmpty() && !lmWeightingsStr.equalsIgnoreCase("no") && !lmWeightingsStr.equalsIgnoreCase("false")) {
            List<String> tmpLMWeightingList = Arrays.asList(lmWeightingsStr.split(","));
            setWeightingsAsStrings(tmpLMWeightingList);
        }

        boolean enableThis = !weightingsAsStrings.isEmpty();
        setEnabled(enableThis);
        if (enableThis)
            setDisablingAllowed(ghConfig.getBool(Landmark.INIT_DISABLING_ALLOWED, isDisablingAllowed()));
    }

    public int getLandmarks() {
        return landmarkCount;
    }

    public LMPreparationHandler setDisablingAllowed(boolean disablingAllowed) {
        this.disablingAllowed = disablingAllowed;
        return this;
    }

    public final boolean isDisablingAllowed() {
        return disablingAllowed || !isEnabled();
    }

    /**
     * Enables or disables this handler. This speed-up mode is disabled by default.
     */
    public final LMPreparationHandler setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

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
    public LMPreparationHandler setWeightingsAsStrings(List<String> weightingList) {
        if (weightingList.isEmpty())
            throw new IllegalArgumentException("It is not allowed to pass an emtpy weightingList");

        weightingsAsStrings.clear();
        for (String strWeighting : weightingList) {
            strWeighting = toLowerCase(strWeighting);
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

    public LMPreparationHandler addWeighting(String weighting) {
        String[] str = weighting.split("\\|");
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
    public LMPreparationHandler addWeighting(Weighting weighting) {
        weightings.add(weighting);
        return this;
    }

    public LMPreparationHandler addPreparation(PrepareLandmarks pch) {
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

    /**
     * @return a {@link RoutingAlgorithmFactory} for LM or throw an error if no preparation is available for the given
     * hints
     */
    public RoutingAlgorithmFactory getAlgorithmFactory(HintsMap map) {
        if (preparations.isEmpty())
            throw new IllegalStateException("No LM preparations added yet");

        // if no weighting or vehicle is specified for this request and there is only one preparation, use it
        if ((map.getWeighting().isEmpty() || map.getVehicle().isEmpty()) && preparations.size() == 1) {
            return new LMRAFactory(preparations.get(0), new RoutingAlgorithmFactorySimple());
        }

        List<Weighting> lmWeightings = new ArrayList<>(preparations.size());
        for (final PrepareLandmarks p : preparations) {
            lmWeightings.add(p.getWeighting());
            if (p.getWeighting().matches(map))
                return new LMRAFactory(p, new RoutingAlgorithmFactorySimple());
        }

        // There are situations where we can use the requested encoder/weighting with an existing LM preparation, even
        // though the preparation was done with a different weighting. For example this works when the new weighting
        // only yields higher (but never lower) weights than the one that was used for the preparation. However, its not
        // trivial to check whether or not this is the case so we do not allow this for now.
        String requestedString = (map.getWeighting().isEmpty() ? "*" : map.getWeighting()) + "|" +
                (map.getVehicle().isEmpty() ? "*" : map.getVehicle());
        throw new IllegalArgumentException("Cannot find matching LM profile for your request." +
                "\nrequested: " + requestedString + "\navailable: " + lmWeightings);
    }

    /**
     * @see com.graphhopper.GraphHopper#calcPaths(GHRequest, GHResponse)
     */
    private static class LMRAFactory implements RoutingAlgorithmFactory {
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
            return p.getPreparedRoutingAlgorithm(g, algo, opts);
        }
    }

    /**
     * This method calculates the landmark data for all weightings (optionally in parallel) or if already existent loads it.
     *
     * @return true if the preparation data for at least one weighting was calculated.
     * @see CHPreparationHandler#prepare(StorableProperties, boolean) for a very similar method
     */
    public boolean loadOrDoWork(final StorableProperties properties, final boolean closeEarly) {
        ExecutorCompletionService<String> completionService = new ExecutorCompletionService<>(threadPool);
        int counter = 0;
        final AtomicBoolean prepared = new AtomicBoolean(false);
        for (final PrepareLandmarks plm : preparations) {
            counter++;
            final int tmpCounter = counter;
            final String name = AbstractWeighting.weightingToFileName(plm.getWeighting());
            completionService.submit(new Runnable() {
                @Override
                public void run() {
                    if (plm.loadExisting())
                        return;

                    LOGGER.info(tmpCounter + "/" + getPreparations().size() + " calling LM prepare.doWork for " + plm.getWeighting() + " ... (" + getMemInfo() + ")");
                    prepared.set(true);
                    Thread.currentThread().setName(name);
                    plm.doWork();
                    if (closeEarly) {
                        plm.close();
                    }
                    properties.put(Landmark.PREPARE + "date." + name, createFormatter().format(new Date()));
                }
            }, name);
        }

        threadPool.shutdown();

        try {
            for (int i = 0; i < preparations.size(); i++) {
                completionService.take().get();
            }
        } catch (Exception e) {
            threadPool.shutdownNow();
            throw new RuntimeException(e);
        }
        return prepared.get();
    }

    /**
     * This method creates the landmark storages ready for landmark creation.
     */
    public void createPreparations(GraphHopperStorage ghStorage, LocationIndex locationIndex) {
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
                    weighting, landmarkCount, activeLandmarkCount).
                    setLandmarkSuggestions(lmSuggestions).
                    setMaximumWeight(maximumWeight).
                    setLogDetails(logDetails);
            if (minNodes > 1)
                tmpPrepareLM.setMinimumNodes(minNodes);
            addPreparation(tmpPrepareLM);
        }
    }
}
