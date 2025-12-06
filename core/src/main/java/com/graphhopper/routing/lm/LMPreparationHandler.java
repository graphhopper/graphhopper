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
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.util.AreaIndex;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.BaseGraph;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.graphhopper.util.Helper.*;

/**
 * This class deals with the A*, landmark and triangulation (ALT) preparations.
 *
 * @author Peter Karich
 */
public class LMPreparationHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(LMPreparationHandler.class);
    private int landmarkCount = 16;
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
        return !lmProfiles.isEmpty();
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

    /**
     * Loads the landmark data for all given configs if available.
     *
     * @return the loaded landmark storages
     */
    public List<LandmarkStorage> load(List<LMConfig> lmConfigs, BaseGraph baseGraph, EncodedValueLookup encodedValueLookup) {
        List<LandmarkStorage> loaded = Collections.synchronizedList(new ArrayList<>());
        Stream<Runnable> loadingRunnables = lmConfigs.stream()
                .map(lmConfig -> () -> {
                    // todo: specifying ghStorage and landmarkCount should not be necessary, because all we want to do
                    //       is load the landmark data and these parameters are only needed to calculate the landmarks.
                    //       we should also work towards a separation of the storage and preparation related code in
                    //       landmark storage
                    LandmarkStorage lms = new LandmarkStorage(baseGraph, encodedValueLookup, baseGraph.getDirectory(), lmConfig, landmarkCount);
                    if (lms.loadExisting())
                        loaded.add(lms);
                    else {
                        // todo: this is very ugly. all we wanted to do was see if the landmarks exist already, but now
                        //       we need to remove the DAs from the directory. This is because otherwise we cannot
                        //       create these DataAccess again when we actually prepare the landmarks that don't exist
                        //       yet.
                        baseGraph.getDirectory().remove("landmarks_" + lmConfig.getName());
                        baseGraph.getDirectory().remove("landmarks_subnetwork_" + lmConfig.getName());
                    }
                });
        GHUtility.runConcurrently(loadingRunnables, preparationThreads);
        return loaded;
    }

    /**
     * Prepares the landmark data for all given configs
     */
    public List<PrepareLandmarks> prepare(List<LMConfig> lmConfigs, BaseGraph baseGraph, EncodingManager encodingManager, StorableProperties properties, LocationIndex locationIndex, final boolean closeEarly) {
        if (lmConfigs.isEmpty()) {
            LOGGER.info("There are no LMs to prepare");
            return Collections.emptyList();
        }
        List<PrepareLandmarks> preparations = createPreparations(lmConfigs, baseGraph, encodingManager, locationIndex);
        List<Runnable> prepareRunnables = new ArrayList<>();
        for (int i = 0; i < preparations.size(); i++) {
            PrepareLandmarks prepare = preparations.get(i);
            final int count = i + 1;
            final String name = prepare.getLMConfig().getName();
            prepareRunnables.add(() -> {
                LOGGER.info(count + "/" + lmConfigs.size() + " calling LM prepare.doWork for " + prepare.getLMConfig().getName() + " ... (" + getMemInfo() + ")");
                Thread.currentThread().setName(name);
                prepare.doWork();
                if (closeEarly)
                    prepare.close();
                LOGGER.info("LM {} finished {}", name, getMemInfo());
                properties.put(Landmark.PREPARE + "date." + name, createFormatter().format(new Date()));
            });
        }
        GHUtility.runConcurrently(prepareRunnables.stream(), preparationThreads);
        LOGGER.info("Finished LM preparation, {}", getMemInfo());
        return preparations;
    }

    /**
     * This method creates the landmark storages ready for landmark creation.
     */
    List<PrepareLandmarks> createPreparations(List<LMConfig> lmConfigs, BaseGraph graph, EncodedValueLookup encodedValueLookup, LocationIndex locationIndex) {
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

        List<PrepareLandmarks> preparations = new ArrayList<>();
        for (LMConfig lmConfig : lmConfigs) {
            Double maximumWeight = maximumWeights.get(lmConfig.getName());
            if (maximumWeight == null)
                throw new IllegalStateException("maximumWeight cannot be null. Default should be just negative. " +
                        "Couldn't find " + lmConfig.getName() + " in " + maximumWeights);

            PrepareLandmarks prepareLandmarks = new PrepareLandmarks(graph.getDirectory(), graph, encodedValueLookup,
                    lmConfig, landmarkCount).
                    setLandmarkSuggestions(lmSuggestions).
                    setMaximumWeight(maximumWeight).
                    setLogDetails(logDetails);
            if (minNodes > 1)
                prepareLandmarks.setMinimumNodes(minNodes);
            // using the area index we separate certain areas from each other but we do not change the base graph for this
            // so that other algorithms still can route between these areas
            if (areaIndex != null)
                prepareLandmarks.setAreaIndex(areaIndex);
            preparations.add(prepareLandmarks);
        }
        return preparations;
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
