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

import com.graphhopper.config.CHProfileConfig;
import com.graphhopper.config.LMProfileConfig;
import com.graphhopper.config.ProfileConfig;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProfileResolver {
    private final EncodingManager encodingManager;
    private final List<ProfileConfig> profiles;
    private final List<ProfileConfig> chProfiles;
    private final List<ProfileConfig> lmProfiles;

    public ProfileResolver(EncodingManager encodingManager, List<ProfileConfig> profiles, List<CHProfileConfig> chProfiles, List<LMProfileConfig> lmProfiles) {
        this.encodingManager = encodingManager;
        this.profiles = profiles;
        Map<String, ProfileConfig> profilesByName = new HashMap<>(profiles.size());
        for (ProfileConfig p : profiles) {
            profilesByName.put(p.getName(), p);
        }
        if (profilesByName.size() != profiles.size()) {
            throw new IllegalStateException("Profiles must have distinct names");
        }
        this.chProfiles = new ArrayList<>();
        for (CHProfileConfig p : chProfiles) {
            ProfileConfig profile = profilesByName.get(p.getProfile());
            if (profile == null) {
                throw new IllegalStateException("There is no profile for CH preparation '" + p.getProfile() + "'");
            }
            this.chProfiles.add(profile);
        }
        this.lmProfiles = new ArrayList<>();
        for (LMProfileConfig p : lmProfiles) {
            ProfileConfig profile = profilesByName.get(p.getProfile());
            if (profile == null) {
                throw new IllegalStateException("There is no profile for LM preparation '" + p.getProfile() + "'");
            }
            this.lmProfiles.add(profile);
        }
    }

    public ProfileConfig resolveProfile(HintsMap hints) {
        boolean disableCH = hints.getBool(Parameters.CH.DISABLE, false);
        boolean disableLM = hints.getBool(Parameters.Landmark.DISABLE, false);

        String vehicle = hints.getVehicle();
        if (!vehicle.isEmpty() && !encodingManager.hasEncoder(hints.getVehicle()))
            throw new IllegalArgumentException("Vehicle not supported: " + vehicle + ". Supported are: " + encodingManager.toString() +
                    " You should consider using the profile parameter instead of specifying a vehicle, see #todonow");

        // unless CH/LM are disabled we select the profile based on the given request hints and the available preparations
        if (!chProfiles.isEmpty() && !disableCH) {
            return selectProfileCH(hints);
        } else if (!lmProfiles.isEmpty() && !disableLM) {
            return selectProfileLM(hints);
        } else {
            return selectProfileUnprepared(hints);
        }
    }

    /**
     * @param hintsMap a map used to describe the profile that shall be selected
     * @throws IllegalArgumentException if no profile supporting CH could be selected for the given parameters
     */
    public ProfileConfig selectProfileCH(HintsMap hintsMap) {
        List<ProfileConfig> matchingProfiles = new ArrayList<>();
        for (ProfileConfig p : chProfiles) {
            if (!chProfileMatchesHints(p, hintsMap))
                continue;
            matchingProfiles.add(p);
        }

        Boolean edgeBased = getEdgeBased(hintsMap);
        Integer uTurnCosts = getUTurnCosts(hintsMap);
        if (matchingProfiles.isEmpty()) {
            throw new IllegalArgumentException("Cannot find matching profile that supports CH for your request. Please check your parameters." +
                    "\nYou can try disabling CH using " + Parameters.CH.DISABLE + "=true" +
                    "\nrequested:  " + getCHRequestAsString(hintsMap, edgeBased, uTurnCosts) + "\navailable: " + chProfilesAsString(chProfiles));
        } else if (matchingProfiles.size() == 1) {
            return matchingProfiles.get(0);
        } else {
            // special case: prefer profile with turn costs over one without turn costs if both are available and there
            // aren't any other options
            ProfileConfig match1 = matchingProfiles.get(0);
            ProfileConfig match2 = matchingProfiles.get(1);
            if (edgeBased == null && matchingProfiles.size() == 2 &&
                    match1.getWeighting().equals(match2.getWeighting()) &&
                    match1.getVehicle().equals(match2.getVehicle()) &&
                    match1.isTurnCosts() != match2.isTurnCosts()) {
                return match1.isTurnCosts() ? match1 : match2;
            }
            throw new IllegalArgumentException("There are multiple CH profiles matching your request. Use the `weighting`,`vehicle`,`edge_based` and/or `u_turn_costs` parameters to be more specific or better use the `profile` parameter to explicitly choose a profile." +
                    "\nYou can also try disabling CH altogether using " + Parameters.CH.DISABLE + "=true" +
                    "\nrequested:  " + getCHRequestAsString(hintsMap, edgeBased, uTurnCosts) + "\nmatched:   " + chProfilesAsString(matchingProfiles) + "\navailable: " + chProfilesAsString(chProfiles));

        }
    }

    protected boolean chProfileMatchesHints(ProfileConfig p, HintsMap hintsMap) {
        Boolean edgeBased = getEdgeBased(hintsMap);
        Integer uTurnCosts = getUTurnCosts(hintsMap);
        return (edgeBased == null || p.isTurnCosts() == edgeBased) &&
                (uTurnCosts == null || uTurnCosts.equals(getUTurnCosts(p.getHints()))) &&
                (hintsMap.getWeighting().isEmpty() || p.getWeighting().equals(hintsMap.getWeighting())) &&
                (hintsMap.getVehicle().isEmpty() || p.getVehicle().equals(hintsMap.getVehicle()));
    }

    public ProfileConfig selectProfileLM(HintsMap hintsMap) {
        List<ProfileConfig> matchingProfiles = new ArrayList<>();
        for (ProfileConfig p : lmProfiles) {
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
            throw new IllegalArgumentException("There are multiple LM profiles matching your request. Use the `weighting` and `vehicle` parameters to be more specific or better use the `profile` parameter to explicitly choose a profile." +
                    "\nYou can also try disabling LM altogether using " + Parameters.Landmark.DISABLE + "=true" +
                    "\nrequested:  " + getLMRequestAsString(hintsMap) + "\nmatched:   " + lmProfilesAsStrings(matchingProfiles) + "\navailable: " + lmProfilesAsStrings(lmProfiles));
        }
    }

    protected boolean lmProfileMatchesHints(ProfileConfig p, HintsMap hintsMap) {
        return (hintsMap.getWeighting().isEmpty() || p.getWeighting().equals(hintsMap.getWeighting())) &&
                (hintsMap.getVehicle().isEmpty() || p.getVehicle().equals(hintsMap.getVehicle()));
    }

    private ProfileConfig selectProfileUnprepared(HintsMap hints) {
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

        for (ProfileConfig profile : profiles) {
            if (profile.getWeighting().equals(weighting) &&
                    profile.getVehicle().equals(vehicle) &&
                    profile.isTurnCosts() == turnCosts)
                return profile;
        }

        throw new IllegalArgumentException("No profile could be found for weighting=" + weighting + ", vehicle=" + vehicle + ", turn_costs=" + turnCosts +
                "\nYou should specify a profile using the `profile` parameter");
    }

    private FlagEncoder getDefaultVehicle() {
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

    private List<String> lmProfilesAsStrings(List<ProfileConfig> profiles) {
        List<String> result = new ArrayList<>(profiles.size());
        for (ProfileConfig p : profiles) {
            result.add(p.getWeighting() + "|" + p.getVehicle());
        }
        return result;
    }

    private List<String> chProfilesAsString(List<ProfileConfig> profiles) {
        List<String> result = new ArrayList<>(profiles.size());
        for (ProfileConfig p : profiles) {
            String str = p.getWeighting() + "|" + p.getVehicle() + "|edge_based=" + p.isTurnCosts();
            str += (p.isTurnCosts() ? "|u_turn_costs=" + p.getHints().getInt(Parameters.Routing.U_TURN_COSTS, INFINITE_U_TURN_COSTS) : "");
            result.add(str);
        }
        return result;
    }

    private Boolean getEdgeBased(PMap hintsMap) {
        return hintsMap.has(Parameters.Routing.EDGE_BASED) ? hintsMap.getBool(Parameters.Routing.EDGE_BASED, false) : null;
    }

    private Integer getUTurnCosts(PMap hintsMap) {
        return hintsMap.has(Parameters.Routing.U_TURN_COSTS) ? hintsMap.getInt(Parameters.Routing.U_TURN_COSTS, INFINITE_U_TURN_COSTS) : null;
    }
}
