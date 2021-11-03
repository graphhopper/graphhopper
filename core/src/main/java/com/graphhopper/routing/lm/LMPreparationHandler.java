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
import com.graphhopper.routing.util.AreaIndex;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.StorableProperties;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.JsonFeatureCollection;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.Parameters.Landmark;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.graphhopper.util.Helper.*;

/**
 * This class deals with the A*, landmark and triangulation (ALT) preparations.
 *
 * @author Peter Karich
 */
public class LMPreparationHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(LMPreparationHandler.class);
    private int landmarkCount = 16;

    private final List<PrepareLandmarks> preparations = new ArrayList<>();
    private final List<LMProfile> lmProfiles = new ArrayList<>();
    private final Map<String, Double> maximumWeights = new HashMap<>();
    private int minNodes = -1;
    private final List<String> lmSuggestionsLocations = new ArrayList<>(5);
    private int preparationThreads;
    private boolean logDetails = false;
    private AreaIndex<SplitArea> areaIndex;

    public LMPreparationHandler() {
        setPreparationThreads(1);
    }

    public void init(GraphHopperConfig ghConfig) {
        // throw explicit error for deprecated configs
        if (ghConfig.has("prepare.lm.weightings")) {
            throw new IllegalStateException("Use profiles_lm instead of prepare.lm.weightings, see #1922 and docs/core/profiles.md");
        }

        setPreparationThreads(ghConfig.getInt(Parameters.Landmark.PREPARE + "threads", getPreparationThreads()));
        setLMProfiles(ghConfig.getLMProfiles());

        landmarkCount = ghConfig.getInt(Parameters.Landmark.COUNT, landmarkCount);
        logDetails = ghConfig.getBool(Landmark.PREPARE + "log_details", false);
        minNodes = ghConfig.getInt(Landmark.PREPARE + "min_network_size", -1);

        for (String loc : ghConfig.getString(Landmark.PREPARE + "suggestions_location", "").split(",")) {
            if (!loc.trim().isEmpty())
                lmSuggestionsLocations.add(loc.trim());
        }

        if (!isEnabled())
            return;

        String splitAreaLocation = ghConfig.getString(Landmark.PREPARE + "split_area_location", "");
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
        return !lmProfiles.isEmpty() || !preparations.isEmpty();
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
                "\nYou can try disabling LM using " + Parameters.Landmark.DISABLE + "=true" +
                "\navailable LM profiles: " + profileNames);
    }

    /**
     * This method calculates the landmark data for all profiles (optionally in parallel) or if already existent loads it.
     *
     * @return true if the preparation data for at least one profile was calculated.
     */
    public boolean loadOrDoWork(List<LMConfig> lmConfigs, GraphHopperStorage ghStorage, LocationIndex locationIndex, final boolean closeEarly) {
        createPreparations(lmConfigs, ghStorage, locationIndex);
        for (PrepareLandmarks prep : preparations) {
            // using the area index we separate certain areas from each other but we do not change the base graph for this
            // so that other algorithms still can route between these areas
            if (areaIndex != null)
                prep.setAreaIndex(areaIndex);
        }
        // load existing preparations and keep track of those that could not be loaded
        List<PrepareLandmarks> preparationsToPrepare = Collections.synchronizedList(new ArrayList<>());
        List<Callable<String>> loadingCallables = preparations.stream()
                .map(preparation -> (Callable<String>) () -> {
                    if (!preparation.loadExisting())
                        preparationsToPrepare.add(preparation);
                    return preparation.getLMConfig().getName();
                })
                .collect(Collectors.toList());
        GHUtility.runConcurrently(loadingCallables, preparationThreads);

        // prepare the preparations that could not be loaded
        StorableProperties properties = ghStorage.getProperties();
        final AtomicBoolean prepared = new AtomicBoolean(false);
        List<Callable<String>> prepareCallables = new ArrayList<>();
        for (int i = 0; i < preparationsToPrepare.size(); i++) {
            PrepareLandmarks prepare = preparationsToPrepare.get(i);
            final int count = i + 1;
            final String name = prepare.getLMConfig().getName();
            prepareCallables.add(() -> {
                LOGGER.info(count + "/" + preparationsToPrepare.size() + " calling LM prepare.doWork for " + prepare.getLMConfig().getWeighting() + " ... (" + getMemInfo() + ")");
                prepared.set(true);
                Thread.currentThread().setName(name);
                prepare.doWork();
                if (closeEarly)
                    prepare.close();
                LOGGER.info("LM {} finished {}", name, getMemInfo());
                properties.put(Landmark.PREPARE + "date." + name, createFormatter().format(new Date()));
                return name;
            });
        }
        GHUtility.runConcurrently(prepareCallables, preparationThreads);
        LOGGER.info("Finished LM preparation, {}", getMemInfo());
        return prepared.get();
    }

    /**
     * This method creates the landmark storages ready for landmark creation.
     */
    void createPreparations(List<LMConfig> lmConfigs, GraphHopperStorage ghStorage, LocationIndex locationIndex) {
        if (!preparations.isEmpty())
            throw new IllegalStateException("LM preparations were created already");
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

        for (LMConfig lmConfig : lmConfigs) {
            Double maximumWeight = maximumWeights.get(lmConfig.getName());
            if (maximumWeight == null)
                throw new IllegalStateException("maximumWeight cannot be null. Default should be just negative. " +
                        "Couldn't find " + lmConfig.getName() + " in " + maximumWeights);

            PrepareLandmarks prepareLandmarks = new PrepareLandmarks(ghStorage.getDirectory(), ghStorage,
                    lmConfig, landmarkCount).
                    setLandmarkSuggestions(lmSuggestions).
                    setMaximumWeight(maximumWeight).
                    setLogDetails(logDetails);
            if (minNodes > 1)
                prepareLandmarks.setMinimumNodes(minNodes);
            preparations.add(prepareLandmarks);
        }
    }

    private JsonFeatureCollection loadLandmarkSplittingFeatureCollection(String splitAreaLocation) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JtsModule());
        URL builtinSplittingFile = LandmarkStorage.class.getResource("map.geo.json");
        try (Reader reader = splitAreaLocation.isEmpty() ?
                new InputStreamReader(builtinSplittingFile.openStream(), UTF_CS) :
                new InputStreamReader(new FileInputStream(splitAreaLocation), UTF_CS)) {
            JsonFeatureCollection result = objectMapper.readValue(reader, JsonFeatureCollection.class);
            if (splitAreaLocation.isEmpty()) {
                LOGGER.info("Loaded built-in landmark splitting collection from {}", builtinSplittingFile);
            } else {
                LOGGER.info("Loaded landmark splitting collection from {}", splitAreaLocation);
            }
            return result;
        } catch (IOException e) {
            LOGGER.error("Problem while reading border map GeoJSON. Skipping this.", e);
            return null;
        }
    }
}
