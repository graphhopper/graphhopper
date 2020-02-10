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
    private final List<String> lmProfileStrings = new ArrayList<>();
    private final List<LMProfile> lmProfiles = new ArrayList<>();
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
            setLMProfileStrings(tmpLMWeightingList);
        }

        boolean enableThis = !getLMProfileStrings().isEmpty();
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
     * Enables the use of landmarks to reduce query times.
     *
     * @param lmProfilesStrings A list containing multiple lm profiles like: "fastest", "shortest" or
     *                          your own weight-calculation type.
     */
    public LMPreparationHandler setLMProfileStrings(List<String> lmProfilesStrings) {
        lmProfileStrings.clear();
        for (String profileStr : lmProfilesStrings) {
            profileStr = toLowerCase(profileStr);
            profileStr = profileStr.trim();
            addLMProfileAsString(profileStr);
        }
        return this;
    }

    public List<String> getLMProfileStrings() {
        return lmProfileStrings;
    }

    public LMPreparationHandler addLMProfileAsString(String profile) {
        String[] str = profile.split("\\|");
        double value = -1;
        if (str.length > 1) {
            PMap map = new PMap(profile);
            value = map.getDouble("maximum", -1);
        }

        lmProfileStrings.add(str[0]);
        maximumWeights.put(str[0], value);
        return this;
    }

    /**
     * Decouple weightings from PrepareLandmarks as we need weightings for the graphstorage and the
     * graphstorage for the preparation.
     */
    public LMPreparationHandler addLMProfile(LMProfile lmProfile) {
        lmProfiles.add(lmProfile);
        return this;
    }

    public LMPreparationHandler addPreparation(PrepareLandmarks plm) {
        preparations.add(plm);
        int lastIndex = preparations.size() - 1;
        if (lastIndex >= lmProfiles.size())
            throw new IllegalStateException("Cannot access profile for PrepareLandmarks with " + plm.getLMProfile()
                    + ". Call add(LMProfile) before");

        if (preparations.get(lastIndex).getLMProfile() != lmProfiles.get(lastIndex))
            throw new IllegalArgumentException("LMProfile of PrepareLandmarks " + preparations.get(lastIndex).getLMProfile()
                    + " needs to be identical to previously added " + lmProfiles.get(lastIndex));
        return this;
    }

    public boolean hasLMProfiles() {
        return !lmProfiles.isEmpty();
    }

    public boolean hasPreparations() {
        return !preparations.isEmpty();
    }

    public int size() {
        return preparations.size();
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
            return new LMRoutingAlgorithmFactory(preparations.get(0), new RoutingAlgorithmFactorySimple());
        }

        List<String> lmProfiles = new ArrayList<>(preparations.size());
        for (final PrepareLandmarks p : preparations) {
            lmProfiles.add(p.getLMProfile().getName());
            if (p.getLMProfile().getWeighting().matches(map))
                return new LMRoutingAlgorithmFactory(p, new RoutingAlgorithmFactorySimple());
        }

        // There are situations where we can use the requested encoder/weighting with an existing LM preparation, even
        // though the preparation was done with a different weighting. For example this works when the new weighting
        // only yields higher (but never lower) weights than the one that was used for the preparation. However, its not
        // trivial to check whether or not this is the case so we do not allow this for now.
        String requestedString = (map.getWeighting().isEmpty() ? "*" : map.getWeighting()) + "|" +
                (map.getVehicle().isEmpty() ? "*" : map.getVehicle());
        throw new IllegalArgumentException("Cannot find matching LM profile for your request." +
                "\nrequested: " + requestedString + "\navailable: " + lmProfiles);
    }

    /**
     * @see com.graphhopper.GraphHopper#calcPaths(GHRequest, GHResponse)
     */
    private static class LMRoutingAlgorithmFactory implements RoutingAlgorithmFactory {
        private RoutingAlgorithmFactory defaultAlgoFactory;
        private PrepareLandmarks p;

        public LMRoutingAlgorithmFactory(PrepareLandmarks p, RoutingAlgorithmFactory defaultAlgoFactory) {
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
     * This method calculates the landmark data for all profiles (optionally in parallel) or if already existent loads it.
     *
     * @return true if the preparation data for at least one profile was calculated.
     * @see CHPreparationHandler#prepare(StorableProperties, boolean) for a very similar method
     */
    public boolean loadOrDoWork(final StorableProperties properties, final boolean closeEarly) {
        ExecutorCompletionService<String> completionService = new ExecutorCompletionService<>(threadPool);
        int counter = 0;
        final AtomicBoolean prepared = new AtomicBoolean(false);
        for (final PrepareLandmarks plm : preparations) {
            counter++;
            final int tmpCounter = counter;
            final String name = plm.getLMProfile().getName();
            completionService.submit(new Runnable() {
                @Override
                public void run() {
                    if (plm.loadExisting())
                        return;

                    LOGGER.info(tmpCounter + "/" + getPreparations().size() + " calling LM prepare.doWork for " + plm.getLMProfile().getWeighting() + " ... (" + getMemInfo() + ")");
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
        if (lmProfiles.isEmpty())
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

        for (LMProfile lmProfile : lmProfiles) {
            Double maximumWeight = maximumWeights.get(lmProfile.getWeighting().getName());
            if (maximumWeight == null)
                throw new IllegalStateException("maximumWeight cannot be null. Default should be just negative. " +
                        "Couldn't find " + lmProfile.getName() + " in " + maximumWeights);

            PrepareLandmarks tmpPrepareLM = new PrepareLandmarks(ghStorage.getDirectory(), ghStorage,
                    lmProfile, landmarkCount, activeLandmarkCount).
                    setLandmarkSuggestions(lmSuggestions).
                    setMaximumWeight(maximumWeight).
                    setLogDetails(logDetails);
            if (minNodes > 1)
                tmpPrepareLM.setMinimumNodes(minNodes);
            addPreparation(tmpPrepareLM);
        }
    }
}
