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
import com.graphhopper.routing.lm.LMProfile;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.storage.CHProfile;
import com.graphhopper.util.Parameters;

import java.util.ArrayList;
import java.util.List;

import static com.graphhopper.routing.weighting.Weighting.INFINITE_U_TURN_COSTS;

public class ProfileResolver {

    public ProfileConfig resolveProfile(EncodingManager encodingManager, List<CHProfile> chProfiles, List<LMProfile> lmProfiles, HintsMap hints) {
        // default handling
        String vehicle = hints.getVehicle();
        if (vehicle.isEmpty()) {
            vehicle = getDefaultVehicle(encodingManager).toString();
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

        String profileName = resolveProfileName(chProfiles, lmProfiles, hints);

        return new ProfileConfig(profileName)
                .setVehicle(vehicle)
                .setWeighting(weighting)
                .setTurnCosts(turnCosts);
    }

    private String resolveProfileName(List<CHProfile> chProfiles, List<LMProfile> lmProfiles, HintsMap hints) {
        boolean disableCH = hints.getBool(Parameters.CH.DISABLE, false);
        boolean disableLM = hints.getBool(Parameters.Landmark.DISABLE, false);

        String profileName;
        if (!chProfiles.isEmpty() && !disableCH) {
            profileName = selectCHProfile(chProfiles, hints).getName();
        } else if (!lmProfiles.isEmpty() && !disableLM) {
            profileName = selectLMProfile(lmProfiles, hints).getName();
        } else {
            // todonow: here we will instead select one of the existing profiles
            profileName = "unprepared_profile";
        }
        return profileName;
    }

    /**
     * @param chProfiles the CH profiles to choose from
     * @param hintsMap   a map used to describe the CH profile that shall be selected
     * @throws IllegalArgumentException if no CH profile could be selected for the given parameters
     */
    public CHProfile selectCHProfile(List<CHProfile> chProfiles, HintsMap hintsMap) {
        int numMatchingEdgeBased = 0;
        List<CHProfile> matchingProfiles = new ArrayList<>();
        for (CHProfile p : chProfiles) {
            if (!chProfileMatchesHints(p, hintsMap))
                continue;
            matchingProfiles.add(p);
            if (p.isEdgeBased()) {
                numMatchingEdgeBased++;
            }
        }

        Boolean edgeBased = getEdgeBased(hintsMap);
        Integer uTurnCosts = getUTurnCosts(hintsMap);
        if (matchingProfiles.isEmpty()) {
            throw new IllegalArgumentException("Cannot find matching CH profile for your request. Please check your parameters." +
                    "\nYou can try disabling CH using " + Parameters.CH.DISABLE + "=true" +
                    "\nrequested:  " + getCHRequestAsString(hintsMap, edgeBased, uTurnCosts) + "\navailable: " + chProfiles);
        } else if (matchingProfiles.size() == 1) {
            return matchingProfiles.get(0);
        } else {
            // special case: prefer edge-based over node-based if these are the only two options
            CHProfile match1 = matchingProfiles.get(0);
            CHProfile match2 = matchingProfiles.get(1);
            if (edgeBased == null && matchingProfiles.size() == 2 &&
                    match1.getWeighting().getName().equals(match2.getWeighting().getName()) &&
                    match1.getWeighting().getFlagEncoder().toString().equals(match2.getWeighting().getFlagEncoder().toString()) &&
                    match1.isEdgeBased() != match2.isEdgeBased()) {
                return match1.isEdgeBased() ? match1 : match2;
            }
            // special case: error if multiple edge-based matches. to differentiate between these it will be required
            // to explicitly set the profile parameter.
            if (numMatchingEdgeBased > 1 && numMatchingEdgeBased == matchingProfiles.size()) {
                throw new IllegalArgumentException("There are multiple edge-based CH profiles matching your request. You need to" +
                        " specify the profile you want to use explicitly, see here: https://github.com/graphhopper/graphhopper/pull/1934.");
            } else {
                throw new IllegalArgumentException("There are multiple CH profiles matching your request. Use the `weighting`,`vehicle`,`edge_based` and/or `u_turn_costs` parameters to be more specific." +
                        "\nYou can also try disabling CH altogether using " + Parameters.CH.DISABLE + "=true" +
                        "\nrequested:  " + getCHRequestAsString(hintsMap, edgeBased, uTurnCosts) + "\nmatched:   " + matchingProfiles + "\navailable: " + chProfiles);

            }
        }
    }

    public LMProfile selectLMProfile(List<LMProfile> lmProfiles, HintsMap hintsMap) {
        List<LMProfile> matchingProfiles = new ArrayList<>();
        for (LMProfile p : lmProfiles) {
            if (!lmProfileMatchesHints(p, hintsMap))
                continue;
            matchingProfiles.add(p);
        }
        // Note:
        // There are situations where we can use the requested encoder/weighting with an existing LM preparation, even
        // though the preparation was done with a different weighting. For example this works when the new weighting
        // only yields higher (but never lower) weights than the one that was used for the preparation. However, its not
        // trivial to check whether or not this is the case so we do not allow this for now.
        if (matchingProfiles.isEmpty()) {
            throw new IllegalArgumentException("Cannot find matching LM profile for your request. Please check your parameters." +
                    "\nYou can try disabling LM by setting " + Parameters.Landmark.DISABLE + "=true" +
                    "\nrequested: " + getLMRequestAsString(hintsMap) + "\navailable: " + lmProfilesAsStrings(lmProfiles));
        } else if (matchingProfiles.size() == 1) {
            return matchingProfiles.get(0);
        } else {
            throw new IllegalArgumentException("There are multiple LM profiles matching your request. Use the `weighting` and `vehicle` parameters to be more specific." +
                    "\nYou can also try disabling LM altogether using " + Parameters.CH.DISABLE + "=true" +
                    "\nrequested:  " + getLMRequestAsString(hintsMap) + "\nmatched:   " + lmProfilesAsStrings(matchingProfiles) + "\navailable: " + lmProfilesAsStrings(lmProfiles));
        }
    }

    protected boolean chProfileMatchesHints(CHProfile p, HintsMap hintsMap) {
        Boolean edgeBased = getEdgeBased(hintsMap);
        Integer uTurnCosts = getUTurnCosts(hintsMap);
        return (edgeBased == null || p.isEdgeBased() == edgeBased) &&
                // u-turn costs cannot be used to select one of multiple edge-based CH profiles,
                // but when they are set only edge-based profiles can match
                (uTurnCosts == null || p.isEdgeBased()) &&
                (hintsMap.getWeighting().isEmpty() || p.getWeighting().getName().equals(hintsMap.getWeighting())) &&
                (hintsMap.getVehicle().isEmpty() || p.getWeighting().getFlagEncoder().toString().equals(hintsMap.getVehicle()));
    }

    protected boolean lmProfileMatchesHints(LMProfile p, HintsMap hintsMap) {
        return (hintsMap.getWeighting().isEmpty() || p.getWeighting().getName().equals(hintsMap.getWeighting())) &&
                (hintsMap.getVehicle().isEmpty() || p.getWeighting().getFlagEncoder().toString().equals(hintsMap.getVehicle()));
    }

    /**
     * @return the first flag encoder of the encoding manager
     */
    public FlagEncoder getDefaultVehicle(EncodingManager encodingManager) {
        return encodingManager.fetchEdgeEncoders().get(0);
    }

    private String getLMRequestAsString(HintsMap map) {
        return (map.getWeighting().isEmpty() ? "*" : map.getWeighting()) +
                "|" +
                (map.getVehicle().isEmpty() ? "*" : map.getVehicle());
    }

    private String getCHRequestAsString(HintsMap hintsMap, Boolean edgeBased, Integer uTurnCosts) {
        return (hintsMap.getWeighting().isEmpty() ? "*" : hintsMap.getWeighting()) +
                "|" +
                (hintsMap.getVehicle().isEmpty() ? "*" : hintsMap.getVehicle()) +
                "|" +
                "edge_based=" + (edgeBased != null ? edgeBased : "*") +
                "|" +
                "u_turn_costs=" + (uTurnCosts != null ? uTurnCosts : "*");
    }

    private List<String> lmProfilesAsStrings(List<LMProfile> profiles) {
        List<String> result = new ArrayList<>(profiles.size());
        for (LMProfile p : profiles) {
            result.add(p.getWeighting().getName() + "|" + p.getWeighting().getFlagEncoder().toString());
        }
        return result;
    }

    private Boolean getEdgeBased(HintsMap hintsMap) {
        return hintsMap.has(Parameters.Routing.EDGE_BASED) ? hintsMap.getBool(Parameters.Routing.EDGE_BASED, false) : null;
    }

    private Integer getUTurnCosts(HintsMap hintsMap) {
        return hintsMap.has(Parameters.Routing.U_TURN_COSTS) ? hintsMap.getInt(Parameters.Routing.U_TURN_COSTS, INFINITE_U_TURN_COSTS) : null;
    }
}
