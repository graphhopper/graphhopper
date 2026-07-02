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

package com.graphhopper.config

import com.fasterxml.jackson.annotation.JsonProperty
import com.graphhopper.config.Profile.Companion.validateProfileName

/**
 * Corresponds to an entry in the `profiles_lm` section in config.yml and specifies a routing profile that shall be
 * prepared using Landmarks (LM)
 *
 * @see Profile
 */
class LMProfile private constructor() {
    // default constructor needed for jackson

    private var profile = ""
    private var preparationProfile = "this"
    private var maximumLMWeight = -1.0

    constructor(profile: LMProfile) : this() {
        this.profile = profile.profile
        this.preparationProfile = profile.preparationProfile
        this.maximumLMWeight = profile.maximumLMWeight
    }

    constructor(profile: String) : this() {
        setProfile(profile)
    }

    fun getProfile(): String = profile

    // the explicit @JsonProperty replaces the package-private visibility of the java version,
    // which kotlin cannot express
    @JsonProperty("profile")
    private fun setProfile(profile: String) {
        validateProfileName(profile)
        this.profile = profile
    }

    fun usesOtherPreparation(): Boolean = preparationProfile != "this"

    fun getPreparationProfile(): String = preparationProfile

    fun setPreparationProfile(preparationProfile: String): LMProfile {
        validateProfileName(preparationProfile)
        if (maximumLMWeight >= 0)
            throw IllegalArgumentException("Using non-default maximum_lm_weight and preparation_profile at the same time is not allowed")
        this.preparationProfile = preparationProfile
        return this
    }

    fun getMaximumLMWeight(): Double = maximumLMWeight

    @JsonProperty("maximum_lm_weight")
    fun setMaximumLMWeight(maximumLMWeight: Double): LMProfile {
        if (usesOtherPreparation())
            throw IllegalArgumentException("Using non-default maximum_lm_weight and preparation_profile at the same time is not allowed")
        this.maximumLMWeight = maximumLMWeight
        return this
    }

    override fun toString(): String = "$profile|preparation_profile=$preparationProfile|maximum_lm_weight=$maximumLMWeight"
}
