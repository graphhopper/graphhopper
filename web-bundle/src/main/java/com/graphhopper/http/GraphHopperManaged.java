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

import com.bedatadriven.jackson.datatype.jts.JtsModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.Profile;
import com.graphhopper.gtfs.GraphHopperGtfs;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.routing.weighting.custom.CustomProfile;
import com.graphhopper.routing.weighting.custom.CustomWeighting;
import com.graphhopper.util.*;
import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class GraphHopperManaged implements Managed {

    private final static Logger logger = LoggerFactory.getLogger(GraphHopperManaged.class);
    private final GraphHopper graphHopper;

    public GraphHopperManaged(GraphHopperConfig configuration) {
        if (configuration.has("gtfs.file")) {
            graphHopper = new GraphHopperGtfs(configuration);
        } else {
            graphHopper = new GraphHopper();
        }

        String customAreasDirectory = configuration.getString("custom_areas.directory", "");
        JsonFeatureCollection globalAreas = resolveCustomAreas(customAreasDirectory);
        String customModelFolder = configuration.getString("custom_models.directory", configuration.getString("custom_model_folder", ""));
        List<Profile> newProfiles = resolveCustomModelFiles(customModelFolder, configuration.getProfiles(), globalAreas);
        configuration.setProfiles(newProfiles);

        graphHopper.init(configuration);
    }

    public static JsonFeatureCollection resolveCustomAreas(String customAreasDirectory) {
        JsonFeatureCollection globalAreas = new JsonFeatureCollection();
        if (!customAreasDirectory.isEmpty()) {
            ObjectMapper mapper = new ObjectMapper().registerModule(new JtsModule());
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(customAreasDirectory), "*.{geojson,json}")) {
                for (Path customAreaFile : stream) {
                    try (BufferedReader reader = Files.newBufferedReader(customAreaFile, StandardCharsets.UTF_8)) {
                        globalAreas.getFeatures().addAll(mapper.readValue(reader, JsonFeatureCollection.class).getFeatures());
                    }
                }
                logger.info("Will make " + globalAreas.getFeatures().size() + " areas available to all custom profiles. Found in " + customAreasDirectory);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return globalAreas;
    }

    public static List<Profile> resolveCustomModelFiles(String customModelFolder, List<Profile> profiles, JsonFeatureCollection globalAreas) {
        ObjectMapper jsonOM = Jackson.newObjectMapper();
        List<Profile> newProfiles = new ArrayList<>();
        for (Profile profile : profiles) {
            if (!CustomWeighting.NAME.equals(profile.getWeighting())) {
                newProfiles.add(profile);
                continue;
            }
            Object cm = profile.getHints().getObject("custom_model", null);
            CustomModel customModel;
            if (cm != null) {
                try {
                    // custom_model can be an object tree (read from config) or an object (e.g. from tests)
                    customModel = jsonOM.readValue(jsonOM.writeValueAsBytes(cm), CustomModel.class);
                    newProfiles.add(new CustomProfile(profile).setCustomModel(customModel));
                } catch (Exception ex) {
                    throw new RuntimeException("Cannot load custom_model from " + cm + " for profile " + profile.getName()
                            + ". If you are trying to load from a file, use 'custom_model_file' instead.", ex);
                }
            } else {
                String customModelFileName = profile.getHints().getString("custom_model_file", "");
                if (customModelFileName.isEmpty())
                    throw new IllegalArgumentException("Missing 'custom_model' or 'custom_model_file' field in profile '"
                            + profile.getName() + "'. To use default specify custom_model_file: empty");
                if ("empty".equals(customModelFileName))
                    newProfiles.add(new CustomProfile(profile).setCustomModel(customModel = new CustomModel()));
                else {
                    if (customModelFileName.contains(File.separator))
                        throw new IllegalArgumentException("Use custom_models.directory for the custom_model_file parent");
                    if (!customModelFileName.endsWith(".json"))
                        throw new IllegalArgumentException("Yaml is no longer supported, see #2672. Use JSON with optional comments //");
                    try {
                        // Somehow dropwizard makes it very hard to find out the folder of config.yml -> use an extra parameter for the folder
                        String string = Helper.readJSONFileWithoutComments(Paths.get(customModelFolder).
                                resolve(customModelFileName).toFile().getAbsolutePath());
                        customModel = jsonOM.readValue(string, CustomModel.class);
                        newProfiles.add(new CustomProfile(profile).setCustomModel(customModel));
                    } catch (Exception ex) {
                        throw new RuntimeException("Cannot load custom_model from location " + customModelFileName + " for profile " + profile.getName(), ex);
                    }
                }
            }

            // we can fill in all areas here as in the created template we include only the areas that are used in
            // statements (see CustomModelParser)
            customModel.addAreas(globalAreas);
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
                graphHopper.getBaseGraph().toDetailsString());
    }

    public GraphHopper getGraphHopper() {
        return graphHopper;
    }

    @Override
    public void stop() {
        graphHopper.close();
    }


}
