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

package com.graphhopper.config;

import com.graphhopper.routing.util.CustomModel;
import com.graphhopper.util.PMap;

/**
 * Corresponds to the `profiles` section in `config.yml` and specifies the properties of a routing profile. The name
 * used here needs to be used when setting up CH/LM preparations. See also the documentation in `config-example.yml'
 *
 * @see CHProfileConfig
 * @see LMProfileConfig
 */
public class ProfileConfig {
    private String name = "car";
    private String vehicle = "car";
    private String weighting = "fastest";
    private boolean turnCosts = false;
    private PMap hints = new PMap();
    private CustomModel customModel;

    public static void validateProfileName(String profileName) {
        // currently allowing dash/minus, maybe remove later
        // https://github.com/graphhopper/graphhopper/pull/1922#discussion_r383033522
        if (!profileName.matches("^[a-z0-9_\\-]*$")) {
            throw new IllegalArgumentException("Profile names may only contain lower case letters, numbers, underscores and dashs, given: " + profileName);
        }
    }

    private ProfileConfig() {
        // default constructor needed for jackson
    }

    public ProfileConfig(String name) {
        setName(name);
    }

    public String getName() {
        return name;
    }

    public ProfileConfig setName(String name) {
        validateProfileName(name);
        this.name = name;
        return this;
    }

    public String getVehicle() {
        return vehicle;
    }

    public ProfileConfig setVehicle(String vehicle) {
        this.vehicle = vehicle;
        return this;
    }

    public String getWeighting() {
        return weighting;
    }

    public ProfileConfig setWeighting(String weighting) {
        this.weighting = weighting;
        return this;
    }

    public boolean isTurnCosts() {
        return turnCosts;
    }

    public ProfileConfig setTurnCosts(boolean turnCosts) {
        this.turnCosts = turnCosts;
        return this;
    }

    public ProfileConfig setCustomModel(CustomModel customModel) {
        this.customModel = customModel;
        // TODO NOW this wiring is ugly, at least they should be named both 'base' or both 'vehicle'
        setVehicle(customModel.getBase());
        return this;
    }

    public CustomModel getCustomModel() {
        return customModel;
    }

    public PMap getHints() {
        return hints;
    }

    public ProfileConfig putHint(String key, Object value) {
        this.hints.put(key, value);
        return this;
    }

    @Override
    public String toString() {
        return "name=" + name + "|vehicle=" + vehicle + "|weighting=" + weighting + "|turnCosts=" + turnCosts + "|hints=" + hints;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProfileConfig profile = (ProfileConfig) o;
        return name.equals(profile.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
