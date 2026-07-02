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
 * Corresponds to an entry in the `profiles_ch` section in config.yml and specifies a routing profile that shall be
 * prepared using Contraction Hierarchies (CH)
 *
 * @see Profile
 */
class CHProfile private constructor() {
    // default constructor needed for jackson

    private var profile = ""

    constructor(profile: CHProfile) : this() {
        this.profile = profile.profile
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

    override fun toString(): String = profile

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as CHProfile
        return profile == that.profile
    }

    override fun hashCode(): Int = profile.hashCode()
}
