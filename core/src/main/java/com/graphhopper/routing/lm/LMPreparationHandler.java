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

import com.bedatadriven.jackson.datatype.jts.JtsModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.LMProfile;
import com.graphhopper.routing.ch.CHPreparationHandler;
import com.graphhopper.routing.util.AreaIndex;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.StorableProperties;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.JsonFeatureCollection;
import com.graphhopper.util.Parameters.Landmark;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.*;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.graphhopper.util.Helper.*;

/**
 * This class deals with the A*, landmark and triangulation (ALT) preparations.
 *
 * @author Peter Karich
 */
public class LMPreparationHandler {
// ORS-GH MOD START enable logging for subclasses
    private final Logger LOGGER = LoggerFactory.getLogger(getClass());
// ORS-GH MOD END
    private int landmarkCount = 16;

    private final List<PrepareLandmarks> preparations = new ArrayList<>();
    // we first add the profiles and later read them to create the config objects (because they require
    // the actual Weightings)
    private final List<LMProfile> lmProfiles = new ArrayList<>();
    private final List<LMConfig> lmConfigs = new ArrayList<>();
    private final Map<String, Double> maximumWeights = new HashMap<>();
    private int minNodes = -1;
    private final List<String> lmSuggestionsLocations = new ArrayList<>(5);
    private int preparationThreads;
    private ExecutorService threadPool;
    private boolean logDetails = false;
    private AreaIndex<SplitArea> areaIndex;

// ORS-GH MOD START facilitate overriding in subclasses
    protected String PREPARE = Landmark.PREPARE;
    protected String DISABLE = Landmark.DISABLE;
    protected String COUNT = Landmark.COUNT;
// ORS-GH MOD END

    public LMPreparationHandler() {
        setPreparationThreads(1);
    }

    public void init(GraphHopperConfig ghConfig) {
// ORS-GH MOD START allow overriding fetching of lm profiles in order to use with core profiles
        init(ghConfig, ghConfig.getLMProfiles());
    }

    protected void init(GraphHopperConfig ghConfig, List<LMProfile> lmProfiles) {
// ORS-GH MOD END
        // throw explicit error for deprecated configs
        if (ghConfig.has("prepare.lm.weightings")) {
            throw new IllegalStateException("Use profiles_lm instead of prepare.lm.weightings, see #1922 and docs/core/profiles.md");
        }

        setPreparationThreads(ghConfig.getInt(PREPARE + "threads", getPreparationThreads()));
// ORS-GH MOD START
        //setLMProfiles(ghConfig.getLMProfiles());
        setLMProfiles(lmProfiles);
// ORS-GH MOD END

        landmarkCount = ghConfig.getInt(COUNT, landmarkCount);
        logDetails = ghConfig.getBool(PREPARE + "log_details", false);
        minNodes = ghConfig.getInt(PREPARE + "min_network_size", -1);

        for (String loc : ghConfig.getString(PREPARE + "suggestions_location", "").split(",")) {
            if (!loc.trim().isEmpty())
                lmSuggestionsLocations.add(loc.trim());
        }

        if (!isEnabled())
            return;

        String splitAreaLocation = ghConfig.getString(PREPARE + "split_area_location", "");
        JsonFeatureCollection landmarkSplittingFeatureCollection = loadLandmarkSplittingFeatureCollection(splitAreaLocation);
        if (landmarkSplittingFeatureCollection != null && !landmarkSplittingFeatureCollection.getFeatures().isEmpty()) {
            List<SplitArea> splitAreas = landmarkSplittingFeatureCollection.getFeatures().stream()
                    .map(SplitArea::fromJsonFeature)
                    .collect(Collectors.toList());
            areaIndex = new AreaIndex<>(splitAreas);
        }
    }

    public int getLandmarks() {
        return landmarkCount;
    }

    public final boolean isEnabled() {
        return !lmProfiles.isEmpty() || !lmConfigs.isEmpty() || !preparations.isEmpty();
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
        LOGGER.info("Using {} threads for lm preparation threads", preparationThreads);
        this.threadPool = java.util.concurrent.Executors.newFixedThreadPool(preparationThreads);
    }

    public LMPreparationHandler setLMProfiles(LMProfile... lmProfiles) {
        return setLMProfiles(Arrays.asList(lmProfiles));
    }

    /**
     * Enables the use of landmarks to reduce query times.
     */
    public LMPreparationHandler setLMProfiles(Collection<LMProfile> lmProfiles) {
        this.lmProfiles.clear();
        this.maximumWeights.clear();
        for (LMProfile profile : lmProfiles) {
            if (profile.usesOtherPreparation())
                continue;
            maximumWeights.put(profile.getProfile(), profile.getMaximumLMWeight());
        }
        this.lmProfiles.addAll(lmProfiles);
        return this;
    }

    public List<LMProfile> getLMProfiles() {
        return lmProfiles;
    }

    /**
     * Decouple weightings from PrepareLandmarks as we need weightings for the graphstorage and the
     * graphstorage for the preparation.
     */
    public LMPreparationHandler addLMConfig(LMConfig lmConfig) {
        lmConfigs.add(lmConfig);
        return this;
    }

    public LMPreparationHandler addPreparation(PrepareLandmarks plm) {
        preparations.add(plm);
        int lastIndex = preparations.size() - 1;
        if (lastIndex >= lmConfigs.size())
            throw new IllegalStateException("Cannot access profile for PrepareLandmarks with " + plm.getLMConfig()
                    + ". Call add(LMConfig) before");

        if (preparations.get(lastIndex).getLMConfig() != lmConfigs.get(lastIndex))
            throw new IllegalArgumentException("LMConfig of PrepareLandmarks " + preparations.get(lastIndex).getLMConfig()
                    + " needs to be identical to previously added " + lmConfigs.get(lastIndex));
        return this;
    }

    public boolean hasLMProfiles() {
        return !lmConfigs.isEmpty();
    }

    public int size() {
        return preparations.size();
    }

    public List<LMConfig> getLMConfigs() {
        return lmConfigs;
    }

    public List<PrepareLandmarks> getPreparations() {
        return preparations;
    }

    public PrepareLandmarks getPreparation(String profile) {
        if (preparations.isEmpty())
            throw new IllegalStateException("No LM preparations added yet");

        List<String> profileNames = new ArrayList<>(preparations.size());
        for (PrepareLandmarks preparation : preparations) {
            profileNames.add(preparation.getLMConfig().getName());
            if (preparation.getLMConfig().getName().equals(profile)) {
                return preparation;
            }
        }
        throw new IllegalArgumentException("Cannot find LM preparation for the requested profile: '" + profile + "'" +
                "\nYou can try disabling LM using " + DISABLE + "=true" +
                "\navailable LM profiles: " + profileNames);
    }

    /**
     * This method calculates the landmark data for all profiles (optionally in parallel) or if already existent loads it.
     *
     * @return true if the preparation data for at least one profile was calculated.
     * @see CHPreparationHandler#prepare(StorableProperties, boolean) for a very similar method
     */
    public boolean loadOrDoWork(final StorableProperties properties, final boolean closeEarly) {
        for (PrepareLandmarks prep : preparations) {
            // using the area index we separate certain areas from each other but we do not change the base graph for this
            // so that other algorithms still can route between these areas
            if (areaIndex != null)
                prep.setAreaIndex(areaIndex);
        }
        ExecutorCompletionService<String> completionService = new ExecutorCompletionService<>(threadPool);
        int counter = 0;
        final AtomicBoolean prepared = new AtomicBoolean(false);
        for (final PrepareLandmarks plm : preparations) {
            counter++;
            final int tmpCounter = counter;
            final String name = plm.getLMConfig().getName();
            completionService.submit(() -> {
                if (plm.loadExisting())
                    return;

                LOGGER.info(tmpCounter + "/" + getPreparations().size() + " calling LM prepare.doWork for " + plm.getLMConfig().getWeighting() + " ... (" + getMemInfo() + ")");
                prepared.set(true);
                Thread.currentThread().setName(name);
                plm.doWork();
                if (closeEarly) {
                    plm.close();
                }
                LOGGER.info("LM {} finished {}", name, getMemInfo());
                properties.put(PREPARE + "date." + name, createFormatter().format(new Date()));
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
        LOGGER.info("Finished LM preparation, {}", getMemInfo());
        return prepared.get();
    }

    /**
     * This method creates the landmark storages ready for landmark creation.
     */
    public void createPreparations(GraphHopperStorage ghStorage, LocationIndex locationIndex) {
        if (!isEnabled() || !preparations.isEmpty())
            return;
        if (lmConfigs.isEmpty())
            throw new IllegalStateException("No landmark weightings found");

        LOGGER.info("Creating LM preparations, {}", getMemInfo());
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

// ORS-GH MOD START abstract to a method in order to facilitate overriding
        createPreparationsInternal(ghStorage, lmSuggestions);
    }

    protected void createPreparationsInternal(GraphHopperStorage ghStorage, List<LandmarkSuggestion> lmSuggestions) {
// ORS-GH MOD END
        for (LMConfig lmConfig : lmConfigs) {
            Double maximumWeight = maximumWeights.get(lmConfig.getName());
            if (maximumWeight == null)
                throw new IllegalStateException("maximumWeight cannot be null. Default should be just negative. " +
                        "Couldn't find " + lmConfig.getName() + " in " + maximumWeights);

            PrepareLandmarks tmpPrepareLM = new PrepareLandmarks(ghStorage.getDirectory(), ghStorage,
                    lmConfig, landmarkCount).
                    setLandmarkSuggestions(lmSuggestions).
                    setMaximumWeight(maximumWeight).
                    setLogDetails(logDetails);
            if (minNodes > 1)
                tmpPrepareLM.setMinimumNodes(minNodes);
            addPreparation(tmpPrepareLM);
        }
    }

    private JsonFeatureCollection loadLandmarkSplittingFeatureCollection(String splitAreaLocation) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JtsModule());
        try (Reader reader = splitAreaLocation.isEmpty() ?
                new InputStreamReader(LandmarkStorage.class.getResource("map.geo.json").openStream(), UTF_CS) :
                new InputStreamReader(new FileInputStream(splitAreaLocation), UTF_CS)) {
            JsonFeatureCollection result = objectMapper.readValue(reader, JsonFeatureCollection.class);
            LOGGER.info("Loaded landmark splitting collection from " + splitAreaLocation);
            return result;
        } catch (IOException e) {
            LOGGER.error("Problem while reading border map GeoJSON. Skipping this.", e);
            return null;
        }
    }

// ORS-GH MOD START add methods
    public List<Weighting> getWeightings() {
        return lmConfigs.stream().map(lmConfig -> lmConfig.getWeighting()).collect(Collectors.toList());
    }

    public Map<String, Double> getMaximumWeights() {
        return maximumWeights;
    }

    public int getMinNodes() {
        return minNodes;
    }

    public boolean getLogDetails() {
        return logDetails;
    }
// ORS-GH MOD END
}
