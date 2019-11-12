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

package com.graphhopper.routing.ch;

import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.storage.CHProfile;
import com.graphhopper.util.Parameters;

import java.util.ArrayList;
import java.util.List;

import static com.graphhopper.routing.weighting.TurnWeighting.INFINITE_U_TURN_COSTS;

/**
 * This class is used to determine the appropriate CH profile given (or not given) some (request) parameters
 */
public class CHProfileSelector {
    private final List<CHProfile> chProfiles;
    // variables describing the requested CH profile
    private final String weighting;
    private final String vehicle;
    private final Boolean edgeBased;
    private final Integer uTurnCosts;

    private CHProfileSelector(List<CHProfile> chProfiles, HintsMap hintsMap) {
        this.chProfiles = chProfiles;
        weighting = hintsMap.getWeighting();
        vehicle = hintsMap.getVehicle();
        edgeBased = hintsMap.has(Parameters.Routing.EDGE_BASED) ? hintsMap.getBool(Parameters.Routing.EDGE_BASED, false) : null;
        uTurnCosts = hintsMap.has(Parameters.Routing.U_TURN_COSTS) ? hintsMap.getInt(Parameters.Routing.U_TURN_COSTS, INFINITE_U_TURN_COSTS) : null;
    }

    /**
     * @param chProfiles the CH profiles to choose from
     * @param hintsMap   a map used to describe the CH profile that shall be selected
     * @throws CHProfileSelectionException if no CH profile could be selected for the given parameters
     */
    public static CHProfile select(List<CHProfile> chProfiles, HintsMap hintsMap) {
        return new CHProfileSelector(chProfiles, hintsMap).select();
    }

    private CHProfile select() {

        List<CHProfile> matchingProfiles = new ArrayList<>();
        for (CHProfile p : chProfiles) {
            if (edgeBased != null && p.isEdgeBased() != edgeBased) {
                continue;
            }
            if (uTurnCosts != null && p.getUTurnCostsInt() != uTurnCosts) {
                continue;
            }
            if (!weighting.isEmpty() && !getWeighting(p).equals(weighting)) {
                continue;
            }
            if (!vehicle.isEmpty() && !getVehicle(p).equals(vehicle)) {
                continue;
            }
            matchingProfiles.add(p);
        }

        if (matchingProfiles.isEmpty()) {
            throw new CHProfileSelectionException("Cannot find matching CH profile for your request.\nrequested:  " + getRequestAsString() + "\navailable: " + chProfiles);
        } else if (matchingProfiles.size() == 1) {
            return matchingProfiles.get(0);
        } else {
            // special case: prefer edge-based over node-based if these are the only two options
            CHProfile match1 = matchingProfiles.get(0);
            CHProfile match2 = matchingProfiles.get(1);
            if (edgeBased == null && matchingProfiles.size() == 2 &&
                    getWeighting(match1).equals(getWeighting(match2)) &&
                    getVehicle(match1).equals(getVehicle(match2)) &&
                    match1.isEdgeBased() != match2.isEdgeBased()) {
                return match1.isEdgeBased() ? match1 : match2;
            }
            throw new CHProfileSelectionException("There are multiple CH profiles matching your request. Use the `weighting`,`vehicle`,`edge_based` and/or `u_turn_costs` parameters to be more specific." +
                    "\nrequested:  " + getRequestAsString() + "\nmatched:   " + matchingProfiles + "\navailable: " + chProfiles);
        }
    }

    private String getVehicle(CHProfile match1) {
        return match1.getWeighting().getFlagEncoder().toString();
    }

    private String getWeighting(CHProfile match1) {
        return match1.getWeighting().getName();
    }

    private String getRequestAsString() {
        StringBuilder sb = new StringBuilder();
        sb.append(weighting.isEmpty() ? "*" : weighting);
        sb.append("|");
        sb.append(vehicle.isEmpty() ? "*" : vehicle);
        sb.append("|");
        sb.append("edge_based=").append(edgeBased != null ? edgeBased : "*");
        sb.append("|");
        sb.append("u_turn_costs=").append(uTurnCosts != null ? uTurnCosts : "*");
        return sb.toString();
    }
}
