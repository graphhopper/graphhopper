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

package com.graphhopper.routing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.config.Profile;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.GHUtility;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import static com.graphhopper.util.Helper.readJSONFileWithoutComments;

public class Profiles {

    public static Profile car(String name) {
        return new Profile(name).setCustomModel(loadCustomModelFromJar("car"));
    }

    private static CustomModel loadCustomModelFromJar(String name) {
        try {
            InputStream is = GHUtility.class.getResourceAsStream("/com/graphhopper/custom_models/" + name + ".json");
            if (is == null)
                throw new IllegalArgumentException("There is no built-in custom model for '" + name + "'");
            String json = readJSONFileWithoutComments(new InputStreamReader(is));
            ObjectMapper objectMapper = Jackson.newObjectMapper();
            return objectMapper.readValue(json, CustomModel.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not load built-in custom model for '" + name + "'");
        }
    }
}
