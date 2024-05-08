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

import com.graphhopper.config.Profile;
import com.graphhopper.json.Statement;
import com.graphhopper.util.CustomModel;

import static com.graphhopper.json.SingleStatement.Else;
import static com.graphhopper.json.SingleStatement.If;
import static com.graphhopper.json.Statement.Op.LIMIT;
import static com.graphhopper.json.Statement.Op.MULTIPLY;

public class TestProfiles {
    public static Profile constantSpeed(String name) {
        return constantSpeed(name, 60);
    }

    public static Profile constantSpeed(String name, double speed) {
        Profile profile = new Profile(name);
        CustomModel customModel = new CustomModel();
        customModel.addToSpeed(If("true", Statement.Op.LIMIT, String.valueOf(speed)));
        profile.setCustomModel(customModel);
        return profile;
    }

    public static Profile accessAndSpeed(String vehicle) {
        return accessAndSpeed(vehicle, vehicle);
    }

    public static Profile accessAndSpeed(String name, String vehicle) {
        Profile profile = new Profile(name);
        CustomModel customModel = new CustomModel().
                addToPriority(If("!" + vehicle + "_access", MULTIPLY, "0")).
                addToSpeed(If("true", LIMIT, vehicle + "_average_speed"));
        profile.setCustomModel(customModel);
        return profile;
    }

    public static Profile accessSpeedAndPriority(String vehicle) {
        return accessSpeedAndPriority(vehicle, vehicle);
    }

    public static Profile accessSpeedAndPriority(String name, String vehicle) {
        Profile profile = new Profile(name);
        CustomModel customModel = new CustomModel().
                addToPriority(If(vehicle + "_access", MULTIPLY, vehicle + "_priority")).
                addToPriority(Else(MULTIPLY, "0")).
                addToSpeed(If("true", LIMIT, vehicle + "_average_speed"));
        profile.setCustomModel(customModel);
        return profile;
    }
}
