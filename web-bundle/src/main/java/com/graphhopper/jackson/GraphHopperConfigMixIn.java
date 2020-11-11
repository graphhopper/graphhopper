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

package com.graphhopper.jackson;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.LMProfile;

import java.util.List;

public interface GraphHopperConfigMixIn {

    @JsonProperty("profiles_ch")
    GraphHopperConfig setCHProfiles(List<CHProfile> chProfiles);

    @JsonProperty("profiles_lm")
    GraphHopperConfig setLMProfiles(List<LMProfile> lmProfiles);

    // We can add explicit configuration properties to GraphHopperConfig (for example to allow lists or nested objects),
    // everything else is stored in a HashMap
    @JsonAnySetter
    GraphHopperConfig putObject(String key, Object value);
}
