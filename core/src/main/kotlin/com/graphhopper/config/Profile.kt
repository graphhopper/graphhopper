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

import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.graphhopper.util.CustomModel
import com.graphhopper.util.Helper
import com.graphhopper.util.PMap
import com.graphhopper.util.TurnCostsConfig

/**
 * Corresponds to an entry of the `profiles` section in `config.yml` and specifies the properties of a routing profile.
 * The name used here needs to be used when setting up CH/LM preparations. See also the documentation in
 * `config-example.yml'
 *
 * @see CHProfile
 * @see LMProfile
 */
class Profile private constructor() {
    // default constructor needed for jackson

    private var name: String? = null
    private var turnCostsConfig: TurnCostsConfig? = null
    private var weighting = "custom"
    private var hints = PMap()

    constructor(name: String) : this() {
        setName(name)
        setCustomModel(CustomModel())
    }

    constructor(p: Profile) : this() {
        // !! keeps the original NPE behavior when copying a profile without a name
        setName(p.getName()!!)
        setTurnCostsConfig(p.getTurnCostsConfig())
        setWeighting(p.getWeighting())
        hints = PMap(p.getHints())
    }

    fun getName(): String? = name

    fun setName(name: String): Profile {
        validateProfileName(name)
        this.name = name
        return this
    }

    fun setTurnCostsConfig(turnCostsConfig: TurnCostsConfig?): Profile {
        this.turnCostsConfig = turnCostsConfig
        return this
    }

    @JsonProperty("turn_costs")
    fun getTurnCostsConfig(): TurnCostsConfig? = turnCostsConfig

    fun getWeighting(): String = weighting

    fun setWeighting(weighting: String): Profile {
        this.weighting = weighting
        return this
    }

    fun setCustomModel(customModel: CustomModel?): Profile {
        customModel?.internal()
        getHints().putObject(CustomModel.KEY, customModel)
        return this
    }

    fun getCustomModel(): CustomModel? = getHints().getObject(CustomModel.KEY, null)

    fun hasTurnCosts(): Boolean = turnCostsConfig != null

    @JsonIgnore
    fun getHints(): PMap = hints

    @JsonAnySetter
    fun putHint(key: String, value: Any?): Profile {
        if (key == "u_turn_costs")
            throw IllegalArgumentException("u_turn_costs no longer accepted in profile. Use the turn costs configuration instead, see docs/migration/config-migration-08-09.md")
        if (key == "vehicle")
            throw IllegalArgumentException("vehicle no longer accepted in profile, see docs/migration/config-migration-08-09.md")
        this.hints.putObject(key, value)
        return this
    }

    override fun toString(): String = createContentString(emptyList())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val profile = other as Profile
        // name!! keeps the original NPE behavior for a profile without a name
        return name!! == profile.name
    }

    private fun createContentString(excludedHints: List<String>): String {
        // used to check against stored custom models, see #2026
        val filteredHints = PMap(hints)
        excludedHints.forEach { filteredHints.remove(it) }
        return "name=$name|turn_costs={$turnCostsConfig}|weighting=$weighting|hints=$filteredHints"
    }

    // name!! keeps the original NPE behavior (Kotlin's nullable hashCode would return 0)
    override fun hashCode(): Int = name!!.hashCode()

    @JvmOverloads
    fun getVersion(excludedHints: List<String> = emptyList()): Int =
        Helper.staticHashCode(createContentString(excludedHints))

    companion object {
        @JvmStatic
        fun validateProfileName(profileName: String) {
            if (!profileName.matches("^[a-z0-9_\\-]+$".toRegex())) {
                throw IllegalArgumentException("Profile names may only contain lower case letters, numbers and underscores, given: $profileName")
            }
        }
    }
}
