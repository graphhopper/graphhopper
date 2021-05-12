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
import com.graphhopper.config.Profile;
import com.graphhopper.gtfs.GraphHopperGtfs;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.routing.lm.LandmarkStorage;
import com.graphhopper.routing.util.spatialrules.SpatialRuleLookupHelper;
import com.graphhopper.routing.weighting.custom.CustomProfile;
import com.graphhopper.routing.weighting.custom.CustomWeighting;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.JsonFeatureCollection;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.shapes.BBox;
import io.dropwizard.lifecycle.Managed;
import org.locationtech.jts.geom.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static com.graphhopper.util.Helper.UTF_CS;

public class GraphHopperManaged implements Managed {

    private final static Logger logger = LoggerFactory.getLogger(GraphHopperManaged.class);
    private final GraphHopper graphHopper;

    public GraphHopperManaged(GraphHopperConfig configuration, ObjectMapper objectMapper) {
        ObjectMapper localObjectMapper = objectMapper.copy();
        localObjectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        JsonFeatureCollection landmarkSplittingFeatureCollection = loadLandmarkSplittingFeatureCollection(configuration, localObjectMapper);
        if (configuration.has("gtfs.file")) {
            graphHopper = new GraphHopperGtfs(configuration);
        } else {
            graphHopper = new GraphHopper(landmarkSplittingFeatureCollection);
        }
        if (!configuration.getString("spatial_rules.location", "").isEmpty()) {
            throw new RuntimeException("spatial_rules.location has been deprecated. Please use spatial_rules.borders_directory instead.");
        }
        String spatialRuleBordersDirLocation = configuration.getString("spatial_rules.borders_directory", "");
        if (!spatialRuleBordersDirLocation.isEmpty()) {
            final Envelope maxBounds = BBox.toEnvelope(BBox.parseBBoxString(configuration.getString("spatial_rules.max_bbox", "-180, 180, -90, 90")));
            final Path bordersDirectory = Paths.get(spatialRuleBordersDirLocation);
            List<JsonFeatureCollection> jsonFeatureCollections = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(bordersDirectory, "*.{geojson,json}")) {
                for (Path borderFile : stream) {
                    try (BufferedReader reader = Files.newBufferedReader(borderFile, StandardCharsets.UTF_8)) {
                        JsonFeatureCollection jsonFeatureCollection = localObjectMapper.readValue(reader, JsonFeatureCollection.class);
                        jsonFeatureCollections.add(jsonFeatureCollection);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            SpatialRuleLookupHelper.buildAndInjectCountrySpatialRules(graphHopper, maxBounds, jsonFeatureCollections);
        }

        String customModelFolder = configuration.getString("custom_model_folder", "");
        List<Profile> newProfiles = resolveCustomModelFiles(customModelFolder, configuration.getProfiles());
        configuration.setProfiles(newProfiles);

        graphHopper.init(configuration);
    }

    public static JsonFeatureCollection loadLandmarkSplittingFeatureCollection(GraphHopperConfig configuration, ObjectMapper localObjectMapper) {
        String splitAreaLocation = configuration.getString(Parameters.Landmark.PREPARE + "split_area_location", "");
        try (Reader reader = splitAreaLocation.isEmpty() ?
                new InputStreamReader(LandmarkStorage.class.getResource("map.geo.json").openStream(), UTF_CS) :
                new InputStreamReader(new FileInputStream(splitAreaLocation), UTF_CS)) {
            JsonFeatureCollection result = localObjectMapper.readValue(reader, JsonFeatureCollection.class);
            logger.info("Loaded landmark splitting collection from " + splitAreaLocation);
            return result;
        } catch (IOException e1) {
            logger.error("Problem while reading border map GeoJSON. Skipping this.", e1);
            return null;
        }
    }

    public static List<Profile> resolveCustomModelFiles(String customModelFolder, List<Profile> profiles) {
        ObjectMapper yamlOM = Jackson.initObjectMapper(new ObjectMapper(new YAMLFactory()));
        ObjectMapper jsonOM = Jackson.newObjectMapper();
        List<Profile> newProfiles = new ArrayList<>();
        for (Profile profile : profiles) {
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
            String customModelFileName = profile.getHints().getString("custom_model_file", "");
            if (customModelFileName.isEmpty())
                throw new IllegalArgumentException("Missing 'custom_model' or 'custom_model_file' field in profile '"
                        + profile.getName() + "'. To use default specify custom_model_file: empty");
            if ("empty".equals(customModelFileName))
                newProfiles.add(new CustomProfile(profile).setCustomModel(new CustomModel()));
            else {
                if (customModelFileName.contains(File.separator))
                    throw new IllegalArgumentException("Use custom_model_folder for the custom_model_file parent");
                // Somehow dropwizard makes it very hard to find out the folder of config.yml -> use an extra parameter for the folder
                File file = Paths.get(customModelFolder).resolve(customModelFileName).toFile();
                try {
                    CustomModel customModel = (customModelFileName.endsWith(".json") ? jsonOM : yamlOM).readValue(file, CustomModel.class);
                    newProfiles.add(new CustomProfile(profile).setCustomModel(customModel));
                } catch (Exception ex) {
                    throw new RuntimeException("Cannot load custom_model from location " + customModelFileName + " for profile " + profile.getName(), ex);
                }
            }
        }
        return newProfiles;
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


}
