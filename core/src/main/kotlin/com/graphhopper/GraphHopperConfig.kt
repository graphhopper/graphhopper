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
package com.graphhopper

import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import com.graphhopper.config.CHProfile
import com.graphhopper.config.LMProfile
import com.graphhopper.config.Profile
import com.graphhopper.util.PMap

/**
 * This class represents the global configuration for the GraphHopper class, which is typically configured via the
 * `config.yml` file. Certain fields are mapped to dedicated config objects to allow a hierarchical configuration and
 * to include lists. All other fields are mapped to a key-value (string-string) map. In the future we will start adding
 * the different configuration options as fields of this class including the default values.
 */
open class GraphHopperConfig(private val map: PMap) {
    private var profiles: MutableList<Profile> = ArrayList()
    private var chProfiles: MutableList<CHProfile> = ArrayList()
    private var lmProfiles: MutableList<LMProfile> = ArrayList()
    private var copyrights: MutableList<String> = ArrayList()

    constructor() : this(PMap()) {
        // This includes the required attribution for OpenStreetMap.
        // Do not hesitate to  mention us and link us in your about page
        // https://support.graphhopper.com/support/search/solutions?term=attribution
        copyrights.add("GraphHopper")
        copyrights.add("OpenStreetMap contributors")
    }

    constructor(otherConfig: GraphHopperConfig) : this(PMap(otherConfig.map)) {
        otherConfig.profiles.forEach { p -> profiles.add(Profile(p)) }
        otherConfig.chProfiles.forEach { p -> chProfiles.add(CHProfile(p)) }
        otherConfig.lmProfiles.forEach { p -> lmProfiles.add(LMProfile(p)) }
        copyrights.addAll(otherConfig.copyrights)
    }

    fun getProfiles(): List<Profile> {
        return profiles
    }

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    fun setProfiles(profiles: MutableList<Profile>): GraphHopperConfig {
        this.profiles = profiles
        return this
    }

    fun getCHProfiles(): List<CHProfile> {
        return chProfiles
    }

    @JsonSetter(value = "profiles_ch", nulls = Nulls.AS_EMPTY)
    fun setCHProfiles(chProfiles: MutableList<CHProfile>): GraphHopperConfig {
        this.chProfiles = chProfiles
        return this
    }

    fun getLMProfiles(): List<LMProfile> {
        return lmProfiles
    }

    @JsonSetter(value = "profiles_lm", nulls = Nulls.AS_EMPTY)
    fun setLMProfiles(lmProfiles: MutableList<LMProfile>): GraphHopperConfig {
        this.lmProfiles = lmProfiles
        return this
    }

    fun getCopyrights(): List<String> {
        return copyrights
    }

    fun setCopyrights(copyrights: MutableList<String>) {
        this.copyrights = copyrights
    }

    // We can add explicit configuration properties to GraphHopperConfig (for example to allow lists or nested objects),
    // everything else is stored in a HashMap
    @JsonAnySetter
    fun putObject(key: String, value: Any?): GraphHopperConfig {
        map.putObject(key, value)
        return this
    }

    fun has(key: String): Boolean {
        return map.has(key)
    }

    fun getBool(key: String, _default: Boolean): Boolean {
        return map.getBool(key, _default)
    }

    fun getInt(key: String, _default: Int): Int {
        return map.getInt(key, _default)
    }

    fun getLong(key: String, _default: Long): Long {
        return map.getLong(key, _default)
    }

    fun getFloat(key: String, _default: Float): Float {
        return map.getFloat(key, _default)
    }

    fun getDouble(key: String, _default: Double): Double {
        return map.getDouble(key, _default)
    }

    fun getString(key: String, _default: String?): String? {
        return map.getString(key, _default)
    }

    fun asPMap(): PMap {
        return map
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("profiles:\n")
        for (profile in profiles) {
            sb.append(profile)
            sb.append("\n")
        }
        sb.append("profiles_ch:\n")
        for (profile in chProfiles) {
            sb.append(profile)
            sb.append("\n")
        }
        sb.append("profiles_lm:\n")
        for (profile in lmProfiles) {
            sb.append(profile)
            sb.append("\n")
        }
        sb.append("properties:\n")
        for ((key, value) in map.toMap()) {
            sb.append(key).append(": ").append(value)
            sb.append("\n")
        }
        return sb.toString()
    }
}
