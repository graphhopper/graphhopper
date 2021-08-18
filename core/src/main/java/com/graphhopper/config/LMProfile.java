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

import com.fasterxml.jackson.annotation.JsonProperty;

import static com.graphhopper.config.Profile.validateProfileName;

/**
 * Corresponds to an entry in the `profiles_lm` section in config.yml and specifies a routing profile that shall be
 * prepared using Landmarks (LM)
 *
 * @see Profile
 */
public class LMProfile {
    private String profile = "";
    private String preparationProfile = "this";
    private double maximumLMWeight = -1;

    private LMProfile() {
        // default constructor needed for jackson
    }

    public LMProfile(String profile) {
        setProfile(profile);
    }

    public String getProfile() {
        return profile;
    }

    void setProfile(String profile) {
        validateProfileName(profile);
        this.profile = profile;
    }

    public boolean usesOtherPreparation() {
        return !preparationProfile.equals("this");
    }

    public String getPreparationProfile() {
        return preparationProfile;
    }

    public LMProfile setPreparationProfile(String preparationProfile) {
        validateProfileName(preparationProfile);
        if (maximumLMWeight >= 0)
            throw new IllegalArgumentException("Using non-default maximum_lm_weight and preparation_profile at the same time is not allowed");
        this.preparationProfile = preparationProfile;
        return this;
    }

    public double getMaximumLMWeight() {
        return maximumLMWeight;
    }

    @JsonProperty("maximum_lm_weight")
    public LMProfile setMaximumLMWeight(double maximumLMWeight) {
        if (usesOtherPreparation())
            throw new IllegalArgumentException("Using non-default maximum_lm_weight and preparation_profile at the same time is not allowed");
        this.maximumLMWeight = maximumLMWeight;
        return this;
    }

    @Override
    public String toString() {
        return profile + "|preparation_profile=" + preparationProfile + "|maximum_lm_weight=" + maximumLMWeight;
    }
}
