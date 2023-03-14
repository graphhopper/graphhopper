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
import com.graphhopper.routing.util.ConditionalSpeedCalculator;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.*;
import com.graphhopper.routing.weighting.custom.CustomModelParser;
import com.graphhopper.routing.weighting.custom.CustomProfile;
import com.graphhopper.routing.weighting.custom.CustomWeighting;
import com.graphhopper.storage.ConditionalEdges;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;

import static com.graphhopper.routing.weighting.TurnCostProvider.NO_TURN_COST_PROVIDER;
import static com.graphhopper.routing.weighting.Weighting.INFINITE_U_TURN_COSTS;
import static com.graphhopper.util.Helper.toLowerCase;

public class DefaultWeightingFactory implements WeightingFactory {
    protected final GraphHopperStorage ghStorage;
    protected final EncodingManager encodingManager;

    public DefaultWeightingFactory(GraphHopperStorage ghStorage, EncodingManager encodingManager) {
        this.ghStorage = ghStorage;
        this.encodingManager = encodingManager;
    }

    @Override
    public Weighting createWeighting(Profile profile, PMap requestHints, boolean disableTurnCosts) {
        // Merge profile hints with request hints, the request hints take precedence.
        // Note that so far we do not check if overwriting the profile hints actually works with the preparation
        // for LM/CH. Later we should also limit the number of parameters that can be used to modify the profile.
        // todo: since we are not dealing with block_area here yet we cannot really apply any merging rules
        // for it, see discussion here: https://github.com/graphhopper/graphhopper/pull/1958#discussion_r395462901
        PMap hints = new PMap();
        hints.putAll(profile.getHints());
        hints.putAll(requestHints);

        FlagEncoder encoder = encodingManager.getEncoder(profile.getVehicle());
        TurnCostProvider turnCostProvider;
        if (profile.isTurnCosts() && !disableTurnCosts) {
            if (!encoder.supportsTurnCosts())
                throw new IllegalArgumentException("Encoder " + encoder + " does not support turn costs");
            int uTurnCosts = hints.getInt(Parameters.Routing.U_TURN_COSTS, INFINITE_U_TURN_COSTS);
            turnCostProvider = new DefaultTurnCostProvider(encoder, ghStorage.getTurnCostStorage(), uTurnCosts);
        } else {
            turnCostProvider = NO_TURN_COST_PROVIDER;
        }

        // ORS-GH MOD START - use weighting method determined by ORS
        String weightingStr = hints.getString("weighting_method", "").toLowerCase();
        if (Helper.isEmpty(weightingStr))
            weightingStr = toLowerCase(profile.getWeighting());
        // ORS-GH MOD END
        if (weightingStr.isEmpty())
            throw new IllegalArgumentException("You have to specify a weighting");

        Weighting weighting = null;
        if (CustomWeighting.NAME.equalsIgnoreCase(weightingStr)) {
            if (!(profile instanceof CustomProfile))
                throw new IllegalArgumentException("custom weighting requires a CustomProfile but was profile=" + profile.getName());
            CustomModel queryCustomModel = requestHints.getObject(CustomModel.KEY, null);
            CustomProfile customProfile = (CustomProfile) profile;
            if (queryCustomModel != null)
                queryCustomModel.checkLMConstraints(customProfile.getCustomModel());

            queryCustomModel = CustomModel.merge(customProfile.getCustomModel(), queryCustomModel);
            weighting = CustomModelParser.createWeighting(encoder, encodingManager, turnCostProvider, queryCustomModel);
        } else if ("shortest".equalsIgnoreCase(weightingStr)) {
            weighting = new ShortestWeighting(encoder, turnCostProvider);
        } else if ("fastest".equalsIgnoreCase(weightingStr)) {
            if (encoder.supports(PriorityWeighting.class))
                weighting = new PriorityWeighting(encoder, hints, turnCostProvider);
            else
                weighting = new FastestWeighting(encoder, hints, turnCostProvider);
        } else if ("curvature".equalsIgnoreCase(weightingStr)) {
            if (encoder.supports(CurvatureWeighting.class))
                weighting = new CurvatureWeighting(encoder, hints, turnCostProvider);

        } else if ("short_fastest".equalsIgnoreCase(weightingStr)) {
            weighting = new ShortFastestWeighting(encoder, hints, turnCostProvider);
        }
        // ORS-GH MOD START - hook for ORS-specific weightings
        else {
            weighting = handleOrsWeightings(weightingStr, hints, encoder, turnCostProvider);
        }

        weighting = applySoftWeightings(hints, encoder, weighting);
        // ORS-GH MOD END

        if (weighting == null)
            throw new IllegalArgumentException("Weighting '" + weightingStr + "' not supported");

        // ORS-GH MOD START - hook for attaching speed calculators
        setSpeedCalculator(weighting, hints);

        if (isRequestTimeDependent(hints))
            weighting = createTimeDependentAccessWeighting(weighting);
        // ORS-GH MOD END

        return weighting;
    }

    // ORS-GH MOD START - additional methods
    protected void setSpeedCalculator(Weighting weighting, PMap hints) {
        FlagEncoder encoder = weighting.getFlagEncoder();
        if (encodingManager.hasEncodedValue(EncodingManager.getKey(encoder, ConditionalEdges.SPEED)) && isRequestTimeDependent(hints))
            weighting.setSpeedCalculator(new ConditionalSpeedCalculator(weighting.getSpeedCalculator(), ghStorage, encoder));
    }

    private Weighting handleOrsWeightings(String weightingStr, PMap hints, FlagEncoder encoder, TurnCostProvider turnCostProvider) {
        if ("td_fastest".equalsIgnoreCase(weightingStr)) {
            return new FastestWeighting(encoder, hints);
        } else {
            return handleExternalOrsWeightings(weightingStr, hints, encoder, turnCostProvider);
        }
    }

    /**
     * Potentially wraps the specified weighting into a TimeDependentAccessWeighting.
     */
    private Weighting createTimeDependentAccessWeighting(Weighting weighting) {
        FlagEncoder flagEncoder = weighting.getFlagEncoder();
        if (encodingManager.hasEncodedValue(EncodingManager.getKey(flagEncoder, ConditionalEdges.ACCESS)))
            return new TimeDependentAccessWeighting(weighting, ghStorage, flagEncoder);
        else
            return weighting;
    }

    protected boolean isRequestTimeDependent(PMap hints) {
        return false;//only time-dependent requests coming from ORS are supported
    }
    // ORS-GH MOD END

    // Note: this method is only needed because ORS is split into two
    // codebases (graphHopper fork and main code base)
    protected Weighting handleExternalOrsWeightings(String weightingStr, PMap hints, FlagEncoder encoder, TurnCostProvider turnCostProvider) {
        return null; // Override in external ORS code base
    }

    // Note: this method is only needed because ORS is split into two
    // codebases (graphHopper fork and main code base)
     protected Weighting applySoftWeightings(PMap hints, FlagEncoder encoder, Weighting weighting) {
       return weighting;
    }
    // ORS-GH MOD END
}