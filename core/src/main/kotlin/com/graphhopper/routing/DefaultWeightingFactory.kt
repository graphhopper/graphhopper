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
import com.graphhopper.routing.ev.TurnRestriction
import com.graphhopper.routing.util.EncodingManager
import com.graphhopper.routing.weighting.DefaultTurnCostProvider
import com.graphhopper.routing.weighting.TurnCostProvider
import com.graphhopper.routing.weighting.TurnCostProvider.Companion.NO_TURN_COST_PROVIDER
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.routing.weighting.custom.CustomWeighting
import com.graphhopper.routing.weighting.custom.CustomWeightingBackends
import com.graphhopper.storage.BaseGraph
import com.graphhopper.util.CustomModel
import com.graphhopper.util.Helper.toLowerCase
import com.graphhopper.util.PMap
import com.graphhopper.util.Parameters
import com.graphhopper.util.TurnCostsConfig

class DefaultWeightingFactory(private val graph: BaseGraph, private val encodingManager: EncodingManager) : WeightingFactory {

    override fun createWeighting(profile: Profile, hints: PMap, disableTurnCosts: Boolean): Weighting {
        val requestHints = hints
        // Merge profile hints with request hints, the request hints take precedence.
        // Note that so far we do not check if overwriting the profile hints actually works with the preparation
        // for LM/CH. Later we should also limit the number of parameters that can be used to modify the profile.
        val mergedHints = PMap()
        mergedHints.putAll(profile.getHints())
        mergedHints.putAll(requestHints)

        val weightingStr = toLowerCase(profile.getWeighting())
        if (weightingStr.isEmpty())
            throw IllegalArgumentException("You have to specify a weighting")

        var weighting: Weighting? = null
        if (CustomWeighting.NAME.equals(weightingStr, ignoreCase = true)) {
            val queryCustomModel: CustomModel? = requestHints.getObject(CustomModel.KEY, null)
            val turnCostsConfig = profile.getTurnCostsConfig()
            if (turnCostsConfig != null && !turnCostsConfig.isAllowTurnPenaltyInRequest() && queryCustomModel != null && !queryCustomModel.getTurnPenalty().isEmpty())
                throw IllegalArgumentException("The turn_penalty feature is not supported per request for " + profile.getName() + ". Set 'allow_turn_penalty_in_request' to true in the 'turn_costs' option in the config.yml.")

            val mergedCustomModel = CustomModel.merge(profile.getCustomModel(), queryCustomModel)
            if (requestHints.has(Parameters.Routing.HEADING_PENALTY))
                mergedCustomModel.setHeadingPenalty(requestHints.getDouble(Parameters.Routing.HEADING_PENALTY, Parameters.Routing.DEFAULT_HEADING_PENALTY))

            val parameters = CustomWeightingBackends.default.createParameters(mergedCustomModel, encodingManager)
            val turnCostProvider: TurnCostProvider
            if (profile.hasTurnCosts() && !disableTurnCosts) {
                val turnRestrictionEnc = encodingManager.getTurnBooleanEncodedValue(TurnRestriction.key(profile.getName()!!))
                    ?: throw IllegalArgumentException("Cannot find turn restriction encoded value for " + profile.getName())
                val uTurnCosts = mergedHints.getInt(Parameters.Routing.U_TURN_COSTS, profile.getTurnCostsConfig()!!.getUTurnCosts())
                val tcConfig = TurnCostsConfig(profile.getTurnCostsConfig()).setUTurnCosts(uTurnCosts)
                turnCostProvider = DefaultTurnCostProvider(turnRestrictionEnc, graph, tcConfig, parameters.turnPenaltyMapping)
            } else {
                if (!mergedCustomModel.getTurnPenalty().isEmpty() && !disableTurnCosts)
                    throw IllegalArgumentException("The turn_penalty feature is not supported for " + profile.getName() + ". You have to enable this in 'turn_costs' in config.yml.")
                turnCostProvider = NO_TURN_COST_PROVIDER
            }
            weighting = CustomWeighting(turnCostProvider, parameters)
        } else if ("shortest".equals(weightingStr, ignoreCase = true)) {
            throw IllegalArgumentException("Instead of weighting=shortest use weighting=custom with a high distance_influence")
        } else if ("fastest".equals(weightingStr, ignoreCase = true)) {
            throw IllegalArgumentException("Instead of weighting=fastest use weighting=custom with a custom model that avoids road_access == DESTINATION")
        } else if ("curvature".equals(weightingStr, ignoreCase = true)) {
            throw IllegalArgumentException(
                "The curvature weighting is no longer supported since 7.0. Use a custom " +
                        "model with the EncodedValue 'curvature' instead"
            )
        } else if ("short_fastest".equals(weightingStr, ignoreCase = true)) {
            throw IllegalArgumentException("Instead of weighting=short_fastest use weighting=custom with a distance_influence")
        }

        if (weighting == null)
            throw IllegalArgumentException("Weighting '$weightingStr' not supported")

        return weighting
    }
}
