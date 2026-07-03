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

package com.graphhopper.routing.lm

import com.graphhopper.config.Profile.Companion.validateProfileName
import com.graphhopper.routing.weighting.Weighting

class LMConfig(private val profileName: String, private val weighting: Weighting) {

    init {
        validateProfileName(profileName)
    }

    fun getName(): String = profileName

    fun getWeighting(): Weighting = weighting

    override fun toString(): String = profileName

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val lmConfig = other as LMConfig
        return profileName == lmConfig.profileName
    }

    override fun hashCode(): Int = profileName.hashCode()
}
