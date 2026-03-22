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
package com.graphhopper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.config.Profile;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.routing.weighting.custom.CustomWeighting;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.JsonFeatureCollection;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.graphhopper.util.Helper.readJSONFileWithoutComments;

/**
 * Resolves {@link CustomModel} instances for profiles that use {@link CustomWeighting}.
 * Supports loading custom models inline from config or from files (built-in jar resources or external directory).
 */
class CustomModelResolver {

    private final String customModelFolder;
    private final JsonFeatureCollection globalAreas;
    private final ObjectMapper jsonOM;

    CustomModelResolver(String customModelFolder, JsonFeatureCollection globalAreas) {
        this.customModelFolder = customModelFolder;
        this.globalAreas = globalAreas;
        this.jsonOM = Jackson.newObjectMapper();
    }

    List<Profile> resolveAll(List<Profile> profiles) {
        List<Profile> newProfiles = new ArrayList<>();
        for (Profile profile : profiles) {
            if (!CustomWeighting.NAME.equals(profile.getWeighting())) {
                newProfiles.add(profile);
                continue;
            }
            CustomModel customModel;
            if (profile.hasCustomModel()) {
                if (!profile.getHints().getObject("custom_model_files", Collections.emptyList()).isEmpty())
                    throw new IllegalArgumentException("Do not use custom_model_files and custom_model together");
                Object cm = profile.getCustomModel();
                try {
                    // custom_model can be an object tree (read from config) or an object (e.g. from tests)
                    customModel = jsonOM.readValue(jsonOM.writeValueAsBytes(cm), CustomModel.class);
                    newProfiles.add(profile.setCustomModel(customModel));
                } catch (Exception ex) {
                    throw new RuntimeException("Cannot load custom_model from " + cm + " for profile " + profile.getName()
                            + ". If you are trying to load from a file, use 'custom_model_files' instead.", ex);
                }
            } else {
                if (!profile.getHints().getString("custom_model_file", "").isEmpty())
                    throw new IllegalArgumentException("Since 8.0 you must use a custom_model_files array instead of custom_model_file string");
                List<String> customModelFileNames = profile.getHints().getObject("custom_model_files", null);
                if (customModelFileNames == null)
                    throw new IllegalArgumentException("Missing 'custom_model' or 'custom_model_files' field in profile '"
                            + profile.getName() + "'. To use default specify custom_model_files: []");
                if (customModelFileNames.isEmpty()) {
                    newProfiles.add(profile.setCustomModel(customModel = new CustomModel()));
                } else {
                    customModel = new CustomModel();
                    for (String file : customModelFileNames) {
                        if (file.contains(File.separator))
                            throw new IllegalArgumentException("Use custom_models.directory for the custom_model_files parent");
                        if (!file.endsWith(".json"))
                            throw new IllegalArgumentException("Yaml is no longer supported, see #2672. Use JSON with optional comments //");

                        try {
                            String string;
                            // 1. try to load custom model from jar
                            InputStream is = GHUtility.class.getResourceAsStream("/com/graphhopper/custom_models/" + file);
                            // dropwizard makes it very hard to find out the folder of config.yml -> use an extra parameter for the folder
                            Path customModelFile = Paths.get(customModelFolder).resolve(file);
                            if (is != null) {
                                if (Files.exists(customModelFile))
                                    throw new RuntimeException("Custom model file name '" + file + "' is already used for built-in profiles. Use another name");
                                string = readJSONFileWithoutComments(new InputStreamReader(is));
                            } else {
                                // 2. try to load custom model file from external location
                                string = readJSONFileWithoutComments(customModelFile.toFile().getAbsolutePath());
                            }
                            customModel = CustomModel.merge(customModel, jsonOM.readValue(string, CustomModel.class));
                        } catch (IOException ex) {
                            throw new RuntimeException("Cannot load custom_model from location " + file + ", profile:" + profile.getName(), ex);
                        }
                    }

                    newProfiles.add(profile.setCustomModel(customModel));
                }
            }

            // we can fill in all areas here as in the created template we include only the areas that are used in
            // statements (see CustomModelParser)
            customModel.addAreas(globalAreas);
        }
        return newProfiles;
    }
}
