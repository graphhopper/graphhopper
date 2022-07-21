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

package com.graphhopper.http;

import com.graphhopper.config.CHProfile;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.graphhopper.routing.weighting.Weighting.INFINITE_U_TURN_COSTS;

/**
 * Before the `profile` parameter was introduced in #1958 the cost-function used for route calculations could be
 * specified by setting the vehicle and weighting parameters as well as the turn_costs/edge_based flags. This class does
 * the conversion between these legacy parameters and the corresponding profile. To resolve a profile we consider both
 * the request parameters as well as the available LM/CH preparations.
 * Note that this class is meant to be only used for the top-most web layer, while the GH engine should only deal with
 * the profile parameter.
 */
public class LegacyProfileResolver {
    private final EncodingManager encodingManager;
    private final List<Profile> profiles;
    private final List<Profile> chProfiles;
    private final List<Profile> lmProfiles;

    public LegacyProfileResolver(EncodingManager encodingManager, List<Profile> profiles, List<CHProfile> chProfiles, List<LMProfile> lmProfiles) {
        this.encodingManager = encodingManager;
        this.profiles = profiles;
        Map<String, Profile> profilesByName = new HashMap<>(profiles.size());
        for (Profile p : profiles) {
            profilesByName.put(p.getName(), p);
        }
        if (profilesByName.size() != profiles.size()) {
            throw new IllegalStateException("Profiles must have distinct names");
        }
        this.chProfiles = new ArrayList<>();
        for (CHProfile p : chProfiles) {
            Profile profile = profilesByName.get(p.getProfile());
            if (profile == null) {
                throw new IllegalStateException("There is no profile for CH preparation '" + p.getProfile() + "'");
            }
            this.chProfiles.add(profile);
        }
        this.lmProfiles = new ArrayList<>();
        for (LMProfile p : lmProfiles) {
            Profile profile = profilesByName.get(p.getProfile());
            if (profile == null) {
                throw new IllegalStateException("There is no profile for LM preparation '" + p.getProfile() + "'");
            }
            this.lmProfiles.add(profile);
        }
    }

    public Profile resolveProfile(PMap hints) {
        boolean disableCH = hints.getBool(Parameters.CH.DISABLE, false);
        boolean disableLM = hints.getBool(Parameters.Landmark.DISABLE, false);

        String vehicle = hints.getString("vehicle", "").toLowerCase();
        if (!vehicle.isEmpty()) {
            List<String> availableVehicles = encodingManager.getVehicles();
            if (!availableVehicles.contains(vehicle))
                throw new IllegalArgumentException("Vehicle not supported: `" + vehicle + "`. Supported are: `" + String.join(",", availableVehicles) +
                        "`\nYou should consider using the `profile` parameter instead of specifying a vehicle." +
                        "\nAvailable profiles: " + getProfileNames() +
                        "\nTo learn more about profiles, see: docs/core/profiles.md");
        }

        // we select the profile based on the given request hints and the available profiles
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
    public Profile selectProfileCH(PMap hintsMap) {
        List<Profile> matchingProfiles = new ArrayList<>();
        for (Profile p : chProfiles) {
            if (!chProfileMatchesHints(p, hintsMap))
                continue;
            matchingProfiles.add(p);
        }

        Boolean edgeBased = getEdgeBased(hintsMap);
        Integer uTurnCosts = getUTurnCosts(hintsMap);
        if (matchingProfiles.isEmpty()) {
            throw new IllegalArgumentException("Cannot find matching profile that supports CH for your request. Please check your parameters." +
                    "\nYou can try disabling CH using " + Parameters.CH.DISABLE + "=true" +
                    "\nrequested:  " + getCHRequestAsString(hintsMap, edgeBased, uTurnCosts) + "\navailable: " + chProfilesAsString(chProfiles) +
                    "\nYou should consider using the `profile` parameter. The available profiles are: " + getProfileNames() +
                    "\nTo learn more about profiles, see: docs/core/profiles.md");
        } else if (matchingProfiles.size() == 1) {
            return matchingProfiles.get(0);
        } else {
            // special case: prefer profile with turn costs over one without turn costs if both are available and there
            // aren't any other options
            Profile match1 = matchingProfiles.get(0);
            Profile match2 = matchingProfiles.get(1);
            if (edgeBased == null && matchingProfiles.size() == 2 &&
                    match1.getWeighting().equals(match2.getWeighting()) &&
                    match1.getVehicle().equals(match2.getVehicle()) &&
                    match1.isTurnCosts() != match2.isTurnCosts()) {
                return match1.isTurnCosts() ? match1 : match2;
            }
            throw new IllegalArgumentException("There are multiple CH profiles matching your request. Use the `weighting`," +
                    "`vehicle`,`turn_costs` and/or `u_turn_costs` parameters to be more specific." +
                    "\nYou can also try disabling CH altogether using " + Parameters.CH.DISABLE + "=true" +
                    "\nrequested:  " + getCHRequestAsString(hintsMap, edgeBased, uTurnCosts) + "\nmatched:   " + chProfilesAsString(matchingProfiles) + "\navailable: " + chProfilesAsString(chProfiles) +
                    "\nYou should consider using the `profile` parameter. The available profiles are: " + getProfileNames() +
                    "\nTo learn more about profiles, see: docs/core/profiles.md");
        }
    }

    protected boolean chProfileMatchesHints(Profile p, PMap hintsMap) {
        Boolean edgeBased = getEdgeBased(hintsMap);
        Integer uTurnCosts = getUTurnCosts(hintsMap);
        return (edgeBased == null || p.isTurnCosts() == edgeBased) &&
                (uTurnCosts == null || uTurnCosts.equals(getUTurnCosts(p.getHints()))) &&
                (!hintsMap.has("weighting") || p.getWeighting().equalsIgnoreCase(hintsMap.getString("weighting", ""))) &&
                (!hintsMap.has("vehicle") || p.getVehicle().equalsIgnoreCase(hintsMap.getString("vehicle", "")));
    }

    public Profile selectProfileLM(PMap hintsMap) {
        List<Profile> matchingProfiles = new ArrayList<>();
        for (Profile p : lmProfiles) {
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
                    "\nrequested:  " + getRequestAsString(hintsMap) + "\navailable: " + profilesAsString(lmProfiles) +
                    "\nYou should consider using the `profile` parameter. The available profiles are: " + getProfileNames() +
                    "\nTo learn more about profiles, see: docs/core/profiles.md");
        } else if (matchingProfiles.size() == 1) {
            return matchingProfiles.get(0);
        } else {
            // special case: prefer profile with turn costs over one without turn costs if both are available and there
            // aren't any other options
            Profile match1 = matchingProfiles.get(0);
            Profile match2 = matchingProfiles.get(1);
            Boolean edgeBased = getEdgeBased(hintsMap);
            if (edgeBased == null && matchingProfiles.size() == 2 &&
                    match1.getWeighting().equals(match2.getWeighting()) &&
                    match1.getVehicle().equals(match2.getVehicle()) &&
                    match1.isTurnCosts() != match2.isTurnCosts()) {
                return match1.isTurnCosts() ? match1 : match2;
            }
            throw new IllegalArgumentException("There are multiple LM profiles matching your request. Use the `weighting`," +
                    " `vehicle` and `turn_costs` parameters to be more specific." +
                    "\nYou can also try disabling LM altogether using " + Parameters.Landmark.DISABLE + "=true" +
                    "\nrequested:  " + getRequestAsString(hintsMap) + "\nmatched:   " + profilesAsString(matchingProfiles) + "\navailable: " + profilesAsString(lmProfiles) +
                    "\nYou should consider using the `profile` parameter. The available profiles are: " + getProfileNames() +
                    "\nTo learn more about profiles, see: docs/core/profiles.md");
        }
    }

    protected boolean lmProfileMatchesHints(Profile p, PMap hints) {
        return profileMatchesHints(p, hints);
    }

    private Profile selectProfileUnprepared(PMap hints) {
        List<Profile> matchingProfiles = new ArrayList<>();
        for (Profile p : profiles) {
            if (!profileMatchesHints(p, hints))
                continue;
            matchingProfiles.add(p);
        }
        if (matchingProfiles.isEmpty()) {
            throw new IllegalArgumentException("Cannot find matching profile for your request. Please check your parameters." +
                    "\nrequested: " + getRequestAsString(hints) + "\navailable: " + profilesAsString(profiles) +
                    "\nYou should consider using the `profile` parameter. The available profiles are: " + getProfileNames() +
                    "\nTo learn more about profiles, see: docs/core/profiles.md");
        } else if (matchingProfiles.size() == 1) {
            return matchingProfiles.get(0);
        } else {
            // special case: prefer profile with turn costs over one without turn costs if both are available and there
            // aren't any other options
            Profile match1 = matchingProfiles.get(0);
            Profile match2 = matchingProfiles.get(1);
            Boolean edgeBased = getEdgeBased(hints);
            if (edgeBased == null && matchingProfiles.size() == 2 &&
                    match1.getWeighting().equals(match2.getWeighting()) &&
                    match1.getVehicle().equals(match2.getVehicle()) &&
                    match1.isTurnCosts() != match2.isTurnCosts()) {
                return match1.isTurnCosts() ? match1 : match2;
            }
            throw new IllegalArgumentException("There are multiple profiles matching your request. Use the `weighting`," +
                    " `vehicle and `turn_costs` parameters to be more specific." +
                    "\nrequested:  " + getRequestAsString(hints) + "\nmatched:   " + profilesAsString(matchingProfiles) + "\navailable: " + profilesAsString(profiles) +
                    "\nYou should consider using the `profile` parameter. The available profiles are: " + getProfileNames() +
                    "\nTo learn more about profiles, see: docs/core/profiles.md");
        }
    }

    protected boolean profileMatchesHints(Profile p, PMap hints) {
        Boolean edgeBased = getEdgeBased(hints);
        return (edgeBased == null || p.isTurnCosts() == edgeBased) &&
                (!hints.has("weighting") || p.getWeighting().equalsIgnoreCase(hints.getString("weighting", ""))) &&
                (!hints.has("vehicle") || p.getVehicle().equalsIgnoreCase(hints.getString("vehicle", "")));
    }

    private String getRequestAsString(PMap map) {
        Boolean edgeBased = getEdgeBased(map);
        return (!map.has("weighting") ? "*" : map.getString("weighting", "")) +
                "|" +
                (!map.has("vehicle") ? "*" : map.getString("vehicle", "")) +
                "|" +
                "turn_costs=" + (edgeBased != null ? edgeBased : "*");
    }

    private String getCHRequestAsString(PMap hintsMap, Boolean edgeBased, Integer uTurnCosts) {
        return (!hintsMap.has("weighting") ? "*" : hintsMap.getString("weighting", "")) +
                "|" +
                (!hintsMap.has("vehicle") ? "*" : hintsMap.getString("vehicle", "")) +
                "|" +
                "turn_costs=" + (edgeBased != null ? edgeBased : "*") +
                "|" +
                "u_turn_costs=" + (uTurnCosts != null ? uTurnCosts : "*");
    }

    private List<String> profilesAsString(List<Profile> profiles) {
        List<String> result = new ArrayList<>(profiles.size());
        for (Profile p : profiles) {
            result.add(p.getWeighting() + "|" + p.getVehicle() + "|turn_costs=" + p.isTurnCosts());
        }
        return result;
    }

    private List<String> chProfilesAsString(List<Profile> profiles) {
        List<String> result = new ArrayList<>(profiles.size());
        for (Profile p : profiles) {
            String str = p.getWeighting() + "|" + p.getVehicle() + "|turn_costs=" + p.isTurnCosts();
            str += (p.isTurnCosts() ? "|u_turn_costs=" + p.getHints().getInt(Parameters.Routing.U_TURN_COSTS, INFINITE_U_TURN_COSTS) : "");
            result.add(str);
        }
        return result;
    }

    private List<String> getProfileNames() {
        List<String> result = new ArrayList<>(profiles.size());
        for (Profile p : profiles) {
            result.add(p.getName());
        }
        return result;
    }

    private Boolean getEdgeBased(PMap hintsMap) {
        if (hintsMap.has(Parameters.Routing.TURN_COSTS))
            return hintsMap.getBool(Parameters.Routing.TURN_COSTS, false);
        else if (hintsMap.has(Parameters.Routing.EDGE_BASED))
            return hintsMap.getBool(Parameters.Routing.EDGE_BASED, false);
        else
            return null;
    }

    private Integer getUTurnCosts(PMap hintsMap) {
        return hintsMap.has(Parameters.Routing.U_TURN_COSTS) ? hintsMap.getInt(Parameters.Routing.U_TURN_COSTS, INFINITE_U_TURN_COSTS) : null;
    }
}
