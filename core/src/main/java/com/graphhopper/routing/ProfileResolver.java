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

import com.graphhopper.config.ProfileConfig;
import com.graphhopper.routing.ch.CHPreparationHandler;
import com.graphhopper.routing.ch.CHProfileSelectionException;
import com.graphhopper.routing.ch.CHProfileSelector;
import com.graphhopper.routing.lm.LMPreparationHandler;
import com.graphhopper.routing.lm.LMProfile;
import com.graphhopper.routing.lm.LMProfileSelectionException;
import com.graphhopper.routing.lm.LMProfileSelector;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.storage.CHProfile;
import com.graphhopper.util.Parameters;

public class ProfileResolver {
    private final EncodingManager encodingManager;
    private final CHPreparationHandler chPreparationHandler;
    private final LMPreparationHandler lmPreparationHandler;

    public ProfileResolver(
            EncodingManager encodingManager,
            CHPreparationHandler chPreparationHandler,
            LMPreparationHandler lmPreparationHandler) {
        this.encodingManager = encodingManager;
        this.chPreparationHandler = chPreparationHandler;
        this.lmPreparationHandler = lmPreparationHandler;
    }

    public ProfileConfig resolveProfile(HintsMap hints) {
        // default handling
        String vehicle = hints.getVehicle();
        if (vehicle.isEmpty()) {
            vehicle = getDefaultVehicle().toString();
        }
        String weighting = hints.getWeighting();
        if (weighting.isEmpty()) {
            weighting = "fastest";
        }
        if (!encodingManager.hasEncoder(vehicle))
            throw new IllegalArgumentException("Vehicle not supported: " + vehicle + ". Supported are: " + encodingManager.toString());

        FlagEncoder encoder = encodingManager.getEncoder(vehicle);
        // we use turn costs if the encoder supports it *unless* the edge_based parameter is set explicitly
        boolean turnCosts = encoder.supportsTurnCosts();
        if (hints.has(Parameters.Routing.EDGE_BASED))
            turnCosts = hints.getBool(Parameters.Routing.EDGE_BASED, false);
        if (turnCosts && !encoder.supportsTurnCosts())
            throw new IllegalArgumentException("You need to set up a turn cost storage to make use of edge_based=true, e.g. use car|turn_costs=true");

        boolean disableCH = hints.getBool(Parameters.CH.DISABLE, false);
        boolean disableLM = hints.getBool(Parameters.Landmark.DISABLE, false);

        String profileName = resolveProfileName(hints, disableCH, disableLM);
        return new ProfileConfig(profileName)
                .setVehicle(vehicle)
                .setWeighting(weighting)
                .setTurnCosts(turnCosts);
    }

    public String resolveProfileName(HintsMap map, boolean disableCH, boolean disableLM) {
        if (chPreparationHandler.isEnabled() && !disableCH) {
            return selectCHProfile(map).getName();
        } else if (lmPreparationHandler.isEnabled() && !disableLM) {
            return selectLMProfile(map).getName();
        } else {
            // todonow: here we will instead select one of the existing profiles
            return "unprepared_profile";
        }
    }

    /**
     * @return the first flag encoder of the encoding manager
     */
    public FlagEncoder getDefaultVehicle() {
        return encodingManager.fetchEdgeEncoders().get(0);
    }

    private CHProfile selectCHProfile(HintsMap map) {
        try {
            return CHProfileSelector.select(chPreparationHandler.getCHProfiles(), map);
        } catch (CHProfileSelectionException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    private LMProfile selectLMProfile(HintsMap map) {
        try {
            return LMProfileSelector.select(lmPreparationHandler.getLMProfiles(), map);
        } catch (LMProfileSelectionException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }
}
