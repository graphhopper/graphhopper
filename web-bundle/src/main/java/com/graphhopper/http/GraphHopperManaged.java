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

package com.graphhopper.http;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.CustomArea;
import com.graphhopper.config.CustomAreaFile;
import com.graphhopper.config.Profile;
import com.graphhopper.gtfs.GraphHopperGtfs;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.routing.lm.LandmarkStorage;
import com.graphhopper.routing.util.area.CustomAreaHelper;
import com.graphhopper.routing.weighting.custom.CustomProfile;
import com.graphhopper.routing.weighting.custom.CustomWeighting;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.JsonFeatureCollection;
import com.graphhopper.util.Parameters;
import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static com.graphhopper.util.Helper.UTF_CS;

public class GraphHopperManaged implements Managed {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final GraphHopper graphHopper;

    public GraphHopperManaged(GraphHopperConfig configuration, ObjectMapper objectMapper) {
        ObjectMapper localObjectMapper = objectMapper.copy();
        localObjectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        String splitAreaLocation = configuration.getString(Parameters.Landmark.PREPARE + "split_area_location", "");
        JsonFeatureCollection landmarkSplittingFeatureCollection;
        try (Reader reader = splitAreaLocation.isEmpty() ? new InputStreamReader(LandmarkStorage.class.getResource("map.geo.json").openStream(), UTF_CS) : new InputStreamReader(new FileInputStream(splitAreaLocation), UTF_CS)) {
            landmarkSplittingFeatureCollection = localObjectMapper.readValue(reader, JsonFeatureCollection.class);
        } catch (IOException e1) {
            logger.error("Problem while reading border map GeoJSON. Skipping this.", e1);
            landmarkSplittingFeatureCollection = null;
        }
        if (configuration.has("gtfs.file")) {
            graphHopper = new GraphHopperGtfs(configuration);
        } else {
            graphHopper = new GraphHopper(landmarkSplittingFeatureCollection);
        }
        if (!configuration.getString("spatial_rules.location", "").isEmpty()) {
            throw new RuntimeException("spatial_rules.location has been deprecated. Please use custom_area_files instead.");
        }
        if (!configuration.getString("spatial_rules.borders_directory", "").isEmpty()) {
            throw new RuntimeException(
                            "spatial_rules.borders_directory has been deprecated. Please use custom_area_files instead. "
                                            + "See https://github.com/graphhopper/graphhopper/pull/2201");
        }
        
        List<CustomArea> customAreas = new ArrayList<>();
        for (CustomAreaFile customAreaFile : configuration.getCustomAreaFiles()) {
            try {
                customAreas.addAll(load(customAreaFile, localObjectMapper));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        if (!customAreas.isEmpty()) {
            logger.info("Loaded the following custom areas: {}", customAreas);
            configuration.setCustomAreas(customAreas);
        }

        ObjectMapper yamlOM = Jackson.initObjectMapper(new ObjectMapper(new YAMLFactory()));
        ObjectMapper jsonOM = Jackson.newObjectMapper();
        List<Profile> newProfiles = new ArrayList<>();
        for (Profile profile : configuration.getProfiles()) {
            if (!CustomWeighting.NAME.equals(profile.getWeighting())) {
                newProfiles.add(profile);
                continue;
            }
            Object cm = profile.getHints().getObject("custom_model", null);
            if (cm != null) {
                try {
                    // custom_model can be an object tree (read from config) or an object (e.g. from tests)
                    CustomModel customModel = jsonOM.readValue(jsonOM.writeValueAsBytes(cm), CustomModel.class);
                    newProfiles.add(new CustomProfile(profile).setCustomModel(customModel));
                    continue;
                } catch (Exception ex) {
                    throw new RuntimeException("Cannot load custom_model from " + cm + " for profile " + profile.getName(), ex);
                }
            }
            String customModelLocation = profile.getHints().getString("custom_model_file", "");
            if (customModelLocation.isEmpty())
                throw new IllegalArgumentException("Missing 'custom_model' or 'custom_model_file' field in profile '"
                        + profile.getName() + "'. To use default specify custom_model_file: empty");
            if ("empty".equals(customModelLocation))
                newProfiles.add(new CustomProfile(profile).setCustomModel(new CustomModel()));
            else
                try {
                    CustomModel customModel = (customModelLocation.endsWith(".json") ? jsonOM : yamlOM).readValue(new File(customModelLocation), CustomModel.class);
                    newProfiles.add(new CustomProfile(profile).setCustomModel(customModel));
                } catch (Exception ex) {
                    throw new RuntimeException("Cannot load custom_model from location " + customModelLocation + " for profile " + profile.getName(), ex);
                }
        }
        configuration.setProfiles(newProfiles);

        graphHopper.init(configuration);
    }

    @Override
    public void start() {
        graphHopper.importOrLoad();
        logger.info("loaded graph at:{}, data_reader_file:{}, encoded values:{}, {} ints for edge flags, {}",
                graphHopper.getGraphHopperLocation(), graphHopper.getOSMFile(),
                graphHopper.getEncodingManager().toEncodedValuesAsString(),
                graphHopper.getEncodingManager().getIntsForFlags(),
                graphHopper.getGraphHopperStorage().toDetailsString());
    }

    public GraphHopper getGraphHopper() {
        return graphHopper;
    }

    @Override
    public void stop() {
        graphHopper.close();
    }

    private List<CustomArea> load(CustomAreaFile customAreaFile, ObjectMapper objectMapper) throws IOException {
        final Path geojsonFile = Paths.get(customAreaFile.getLocation());
        JsonFeatureCollection jsonFeatureCollection;
        try (BufferedReader reader = Files.newBufferedReader(geojsonFile, StandardCharsets.UTF_8)) {
            jsonFeatureCollection = objectMapper.readValue(reader, JsonFeatureCollection.class);
        }

        return CustomAreaHelper.loadAreas(customAreaFile, jsonFeatureCollection);
    }

}
