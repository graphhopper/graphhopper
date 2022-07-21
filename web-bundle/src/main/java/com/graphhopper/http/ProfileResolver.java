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
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.graphhopper.resources.RouteResource.enableEdgeBasedIfThereAreCurbsides;
import static com.graphhopper.resources.RouteResource.errorIfLegacyParameters;

public class ProfileResolver {
    private final Map<String, Profile> profilesByName;
    private final LegacyProfileResolver legacyParameterResolver;

    public ProfileResolver(List<Profile> profiles, LegacyProfileResolver legacyParameterResolver) {
        profilesByName = new LinkedHashMap<>(profiles.size());
        profiles.forEach(p -> {
            if (profilesByName.put(p.getName(), p) != null)
                throw new IllegalArgumentException("Profiles must have distinct names");
        });
        this.legacyParameterResolver = legacyParameterResolver;
    }

    public Profile resolveProfile(PMap hints) {
        String profileName = hints.getString("profile", "");
        if (Helper.isEmpty(profileName)) {
            boolean hasCurbsides = hints.getBool("has_curbsides", false);
            enableEdgeBasedIfThereAreCurbsides(hasCurbsides, hints);
            return legacyParameterResolver.resolveProfile(hints);
        } else
            errorIfLegacyParameters(hints);
        Profile profile = profilesByName.get(profileName);
        if (profile == null)
            throw new IllegalArgumentException("The requested profile '" + profileName + "' does not exist.\nAvailable profiles: " + profilesByName.keySet());
        return profile;
    }
}
