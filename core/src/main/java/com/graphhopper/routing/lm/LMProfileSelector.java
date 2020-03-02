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

import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.util.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is used to determine the appropriate LM profile given (or not given) some (request) parameters
 */
public class LMProfileSelector {
    private final List<LMProfile> lmProfiles;
    private final HintsMap map;

    public LMProfileSelector(List<LMProfile> lmProfiles, HintsMap map) {
        this.lmProfiles = lmProfiles;
        this.map = map;
    }

    public static LMProfile select(List<LMProfile> lmProfiles, HintsMap hintsMap) {
        return new LMProfileSelector(lmProfiles, hintsMap).select();
    }

    private LMProfile select() {
        // if no weighting or vehicle is specified for this request and there is only one preparation, use it
        if ((map.getWeighting().isEmpty() || map.getVehicle().isEmpty()) &&
                lmProfiles.size() == 1) {
            return lmProfiles.get(0);
        }
        List<LMProfile> matchingProfiles = new ArrayList<>();
        for (LMProfile p : lmProfiles) {
            if (!p.getWeighting().matches(map)) {
                continue;
            }
            matchingProfiles.add(p);
        }
        // Note:
        // There are situations where we can use the requested encoder/weighting with an existing LM preparation, even
        // though the preparation was done with a different weighting. For example this works when the new weighting
        // only yields higher (but never lower) weights than the one that was used for the preparation. However, its not
        // trivial to check whether or not this is the case so we do not allow this for now.
        if (matchingProfiles.isEmpty()) {
            throw new LMProfileSelectionException("Cannot find matching LM profile for your request. Please check your parameters." +
                    "\nYou can try disabling LM by setting " + Parameters.Landmark.DISABLE + "=true" +
                    "\nrequested: " + getRequestAsString() + "\navailable: " + profilesAsStrings(lmProfiles));
        } else if (matchingProfiles.size() == 1) {
            return matchingProfiles.get(0);
        } else {
            throw new LMProfileSelectionException("There are multiple LM profiles matching your request. Use the `weighting` and `vehicle` parameters to be more specific." +
                    "\nYou can also try disabling LM altogether using " + Parameters.CH.DISABLE + "=true" +
                    "\nrequested:  " + getRequestAsString() + "\nmatched:   " + profilesAsStrings(matchingProfiles) + "\navailable: " + profilesAsStrings(lmProfiles));
        }
    }

    private String getRequestAsString() {
        return (map.getWeighting().isEmpty() ? "*" : map.getWeighting()) +
                "|" +
                (map.getVehicle().isEmpty() ? "*" : map.getVehicle());
    }

    private List<String> profilesAsStrings(List<LMProfile> profiles) {
        List<String> result = new ArrayList<>(profiles.size());
        for (LMProfile p : profiles) {
            result.add(p.getWeighting().getName() + "|" + p.getWeighting().getFlagEncoder().toString());
        }
        return result;
    }
}
