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

public class ProfileResolver {
    protected final Map<String, Profile> profilesByName;

    public ProfileResolver(List<Profile> profiles) {
        profilesByName = new LinkedHashMap<>(profiles.size());
        profiles.forEach(p -> {
            if (profilesByName.put(p.getName(), p) != null)
                throw new IllegalArgumentException("Profiles must have distinct names");
        });
    }

    public String resolveProfile(PMap hints) {
        String profileName = hints.getString("profile", "");
        if (profileName.isEmpty())
            throw new IllegalArgumentException("profile parameter required");
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

    public static void errorIfLegacyParameters(PMap hints) {
        if (hints.has("weighting"))
            throw new IllegalArgumentException("The 'weighting' parameter is no longer supported." +
                    " You used 'weighting=" + hints.getString("weighting", "") + "'");
        if (hints.has("vehicle"))
            throw new IllegalArgumentException("The 'vehicle' parameter is no longer supported." +
                    " You used 'vehicle=" + hints.getString("vehicle", "") + "'");
        if (hints.has("edge_based"))
            throw new IllegalArgumentException("The 'edge_based' parameter is no longer supported." +
                    " You used 'edge_based=" + hints.getBool("edge_based", false) + "'");
        if (hints.has("turn_costs"))
            throw new IllegalArgumentException("The 'turn_costs' parameter is no longer supported." +
                    " You used 'turn_costs=" + hints.getBool("turn_costs", false) + "'");
    }

}
