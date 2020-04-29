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

package com.graphhopper.routing.lm;

import com.graphhopper.routing.weighting.Weighting;

import java.util.Objects;

import static com.graphhopper.config.ProfileConfig.validateProfileName;

public class LMProfile {
    private final String profileName;
    private final Weighting weighting;

    public LMProfile(String profileName, Weighting weighting) {
        validateProfileName(profileName);
        this.profileName = profileName;
        this.weighting = weighting;
    }

    public String getName() {
        return profileName;
    }

    public Weighting getWeighting() {
        return weighting;
    }

    @Override
    public String toString() {
        return profileName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LMProfile lmProfile = (LMProfile) o;
        return Objects.equals(profileName, lmProfile.profileName);
    }

    @Override
    public int hashCode() {
        return profileName.hashCode();
    }
}
