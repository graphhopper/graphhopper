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
package com.graphhopper.routing.weighting.custom;

import com.graphhopper.config.Profile;
import com.graphhopper.util.CustomModel;

import java.util.Collections;

public class CustomProfile extends Profile {

    public CustomProfile(Profile profile) {
        this(profile.getName());
        setVehicle(profile.getVehicle());
        setTurnCosts(profile.isTurnCosts());
        getHints().putAll(profile.getHints());
    }

    public CustomProfile(String name) {
        super(name);
        setWeighting(CustomWeighting.NAME);
        setCustomModel(new CustomModel());
    }

    public CustomProfile setCustomModel(CustomModel customModel) {
        customModel.internal();
        getHints().putObject(CustomModel.KEY, customModel);
        getHints().putObject("custom_model_files", Collections.emptyList());
        return this;
    }

    public CustomModel getCustomModel() {
        return getHints().getObject(CustomModel.KEY, null);
    }
}
