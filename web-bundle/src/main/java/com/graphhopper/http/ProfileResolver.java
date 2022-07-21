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

import com.graphhopper.config.Profile;
import com.graphhopper.util.PMap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.graphhopper.util.Parameters.Routing.*;

public class ProfileResolver {
    protected final Map<String, Profile> profilesByName;
    private final LegacyProfileResolver legacyProfileResolver;

    public ProfileResolver(List<Profile> profiles, LegacyProfileResolver legacyProfileResolver) {
        profilesByName = new LinkedHashMap<>(profiles.size());
        profiles.forEach(p -> {
            if (profilesByName.put(p.getName(), p) != null)
                throw new IllegalArgumentException("Profiles must have distinct names");
        });
        this.legacyProfileResolver = legacyProfileResolver;
    }

    public String resolveProfile(PMap hints) {
        String profileName = hints.getString("profile", "");
        if (profileName.isEmpty()) {
            boolean hasCurbsides = hints.getBool("has_curbsides", false);
            enableEdgeBasedIfThereAreCurbsides(hasCurbsides, hints);
            return legacyProfileResolver.resolveProfile(hints).getName();
        }
        errorIfLegacyParameters(hints);
        String profile = doResolveProfile(profileName, hints);
        if (profile == null)
            throw new IllegalArgumentException("The requested profile '" + profileName + "' does not exist.\nAvailable profiles: " + profilesByName.keySet());
        return profile;
    }

    protected String doResolveProfile(String profileName, PMap hints) {
        Profile profile = profilesByName.get(profileName);
        return profile == null ? null : profile.getName();
    }

    public static void enableEdgeBasedIfThereAreCurbsides(boolean hasCurbsides, PMap hints) {
        if (hasCurbsides) {
            if (!hints.getBool(TURN_COSTS, true))
                throw new IllegalArgumentException("Disabling '" + TURN_COSTS + "' when using '" + CURBSIDE + "' is not allowed");
            if (!hints.getBool(EDGE_BASED, true))
                throw new IllegalArgumentException("Disabling '" + EDGE_BASED + "' when using '" + CURBSIDE + "' is not allowed");
            hints.putObject(EDGE_BASED, true);
        }
    }

    public static void errorIfLegacyParameters(PMap hints) {
        if (hints.has("weighting"))
            throw new IllegalArgumentException("Since you are using the 'profile' parameter, do not use the 'weighting' parameter." +
                    " You used 'weighting=" + hints.getString("weighting", "") + "'");
        if (hints.has("vehicle"))
            throw new IllegalArgumentException("Since you are using the 'profile' parameter, do not use the 'vehicle' parameter." +
                    " You used 'vehicle=" + hints.getString("vehicle", "") + "'");
        if (hints.has("edge_based"))
            throw new IllegalArgumentException("Since you are using the 'profile' parameter, do not use the 'edge_based' parameter." +
                    " You used 'edge_based=" + hints.getBool("edge_based", false) + "'");
        if (hints.has("turn_costs"))
            throw new IllegalArgumentException("Since you are using the 'profile' parameter, do not use the 'turn_costs' parameter." +
                    " You used 'turn_costs=" + hints.getBool("turn_costs", false) + "'");
    }

}
