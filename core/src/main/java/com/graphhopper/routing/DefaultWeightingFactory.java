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
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.VehicleEncodedValues;
import com.graphhopper.routing.weighting.*;
import com.graphhopper.routing.weighting.custom.CustomModelParser;
import com.graphhopper.routing.weighting.custom.CustomProfile;
import com.graphhopper.routing.weighting.custom.CustomWeighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;

import static com.graphhopper.routing.weighting.FastestWeighting.DESTINATION_FACTOR;
import static com.graphhopper.routing.weighting.FastestWeighting.PRIVATE_FACTOR;
import static com.graphhopper.routing.weighting.TurnCostProvider.NO_TURN_COST_PROVIDER;
import static com.graphhopper.routing.weighting.Weighting.INFINITE_U_TURN_COSTS;
import static com.graphhopper.util.Helper.toLowerCase;

public class DefaultWeightingFactory implements WeightingFactory {

    private final BaseGraph graph;
    private final EncodingManager encodingManager;

    public DefaultWeightingFactory(BaseGraph graph, EncodingManager encodingManager) {
        this.graph = graph;
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

        final String vehicle = profile.getVehicle();
        if (isOutdoorVehicle(vehicle)) {
            hints.putObject(PRIVATE_FACTOR, hints.getDouble(PRIVATE_FACTOR, 1.2));
        } else {
            hints.putObject(DESTINATION_FACTOR, hints.getDouble(DESTINATION_FACTOR, 10));
            hints.putObject(PRIVATE_FACTOR, hints.getDouble(PRIVATE_FACTOR, 10));
        }
        TurnCostProvider turnCostProvider;
        if (profile.isTurnCosts() && !disableTurnCosts) {
            DecimalEncodedValue turnCostEnc = encodingManager.getDecimalEncodedValue(TurnCost.key(vehicle));
            if (turnCostEnc == null)
                throw new IllegalArgumentException("Vehicle " + vehicle + " does not support turn costs");
            int uTurnCosts = hints.getInt(Parameters.Routing.U_TURN_COSTS, INFINITE_U_TURN_COSTS);
            turnCostProvider = new DefaultTurnCostProvider(turnCostEnc, graph.getTurnCostStorage(), uTurnCosts);
        } else {
            turnCostProvider = NO_TURN_COST_PROVIDER;
        }

        String weightingStr = toLowerCase(profile.getWeighting());
        if (weightingStr.isEmpty())
            throw new IllegalArgumentException("You have to specify a weighting");

        Weighting weighting = null;
        BooleanEncodedValue accessEnc = encodingManager.getBooleanEncodedValue(VehicleAccess.key(vehicle));
        DecimalEncodedValue speedEnc = encodingManager.getDecimalEncodedValue(VehicleSpeed.key(vehicle));
        DecimalEncodedValue priorityEnc = encodingManager.hasEncodedValue(VehiclePriority.key(vehicle))
                ? encodingManager.getDecimalEncodedValue(VehiclePriority.key(vehicle))
                : null;
        if (CustomWeighting.NAME.equalsIgnoreCase(weightingStr)) {
            if (!(profile instanceof CustomProfile))
                throw new IllegalArgumentException("custom weighting requires a CustomProfile but was profile=" + profile.getName());
            CustomModel queryCustomModel = requestHints.getObject(CustomModel.KEY, null);
            CustomProfile customProfile = (CustomProfile) profile;

            queryCustomModel = CustomModel.merge(customProfile.getCustomModel(), queryCustomModel);
            weighting = CustomModelParser.createWeighting(accessEnc, speedEnc,
                    priorityEnc, encodingManager, turnCostProvider, queryCustomModel);
        } else if ("shortest".equalsIgnoreCase(weightingStr)) {
            weighting = new ShortestWeighting(accessEnc, speedEnc, turnCostProvider);
        } else if ("fastest".equalsIgnoreCase(weightingStr)) {
            if (!encodingManager.hasEncodedValue(RoadAccess.KEY))
                throw new IllegalArgumentException("fastest weighting requires road_access");
            EnumEncodedValue<RoadAccess> roadAccessEnc = encodingManager.getEnumEncodedValue(RoadAccess.KEY, RoadAccess.class);
            if (priorityEnc != null)
                weighting = new PriorityWeighting(accessEnc, speedEnc, priorityEnc, roadAccessEnc, hints, turnCostProvider);
            else
                weighting = new FastestWeighting(accessEnc, speedEnc, roadAccessEnc, hints, turnCostProvider);
        } else if ("curvature".equalsIgnoreCase(weightingStr)) {
            DecimalEncodedValue curvatureEnc = encodingManager.hasEncodedValue(EncodingManager.getKey(vehicle, "curvature"))
                    ? encodingManager.getDecimalEncodedValue(EncodingManager.getKey(vehicle, "curvature"))
                    : null;
            if (curvatureEnc == null || priorityEnc == null)
                throw new IllegalArgumentException("curvature weighting requires curvature and priority, but not found for " + vehicle);
            if (!encodingManager.hasEncodedValue(RoadAccess.KEY))
                throw new IllegalArgumentException("curvature weighting requires road_access");
            EnumEncodedValue<RoadAccess> roadAccessEnc = encodingManager.getEnumEncodedValue(RoadAccess.KEY, RoadAccess.class);
            weighting = new CurvatureWeighting(accessEnc, speedEnc, priorityEnc, curvatureEnc, roadAccessEnc, hints, turnCostProvider);
        } else if ("short_fastest".equalsIgnoreCase(weightingStr)) {
            if (!encodingManager.hasEncodedValue(RoadAccess.KEY))
                throw new IllegalArgumentException("curvature weighting requires road_access");
            EnumEncodedValue<RoadAccess> roadAccessEnc = encodingManager.getEnumEncodedValue(RoadAccess.KEY, RoadAccess.class);
            weighting = new ShortFastestWeighting(accessEnc, speedEnc, roadAccessEnc, hints, turnCostProvider);
        }

        if (weighting == null)
            throw new IllegalArgumentException("Weighting '" + weightingStr + "' not supported");

        return weighting;
    }

    public boolean isOutdoorVehicle(String name) {
        return VehicleEncodedValues.OUTDOOR_VEHICLES.contains(name);
    }
}