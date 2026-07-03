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
package com.graphhopper.routing

import com.graphhopper.config.Profile
import com.graphhopper.json.Statement
import com.graphhopper.json.Statement.Else
import com.graphhopper.json.Statement.If
import com.graphhopper.json.Statement.Op.LIMIT
import com.graphhopper.json.Statement.Op.MULTIPLY
import com.graphhopper.util.CustomModel

object TestProfiles {
    @JvmStatic
    @JvmOverloads
    fun constantSpeed(name: String, speed: Double = 60.0): Profile {
        val profile = Profile(name)
        val customModel = CustomModel()
        customModel.addToSpeed(If("true", Statement.Op.LIMIT, speed.toString()))
        profile.setCustomModel(customModel)
        return profile
    }

    @JvmStatic
    @JvmOverloads
    fun accessAndSpeed(name: String, vehicle: String = name): Profile {
        val profile = Profile(name)
        val customModel = CustomModel()
            .addToPriority(If("!" + vehicle + "_access", MULTIPLY, "0"))
            .addToSpeed(If("true", LIMIT, vehicle + "_average_speed"))
        profile.setCustomModel(customModel)
        return profile
    }

    @JvmStatic
    @JvmOverloads
    fun accessSpeedAndPriority(name: String, vehicle: String = name): Profile {
        val profile = Profile(name)
        val customModel = CustomModel()
            .addToPriority(If(vehicle + "_access", MULTIPLY, vehicle + "_priority"))
            .addToPriority(Else(MULTIPLY, "0"))
            .addToSpeed(If("true", LIMIT, vehicle + "_average_speed"))
        profile.setCustomModel(customModel)
        return profile
    }
}
