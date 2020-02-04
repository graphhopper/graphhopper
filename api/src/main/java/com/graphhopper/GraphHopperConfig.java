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

package com.graphhopper;

import com.graphhopper.util.PMap;
import com.graphhopper.util.Profile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// todonow: docs
public class GraphHopperConfig {
    private final List<Profile> profiles = new ArrayList<>();
    private final PMap map;

    public GraphHopperConfig() {
        this(new PMap());
    }

    public GraphHopperConfig(PMap pMap) {
        this.map = pMap;
    }

    public GraphHopperConfig put(String key, Object value) {
        map.put(key, value);
        return this;
    }

    public boolean has(String key) {
        return map.has(key);
    }

    public List<Profile> getProfiles() {
        return profiles;
    }

    public boolean getBool(String key, boolean _default) {
        return map.getBool(key, _default);
    }

    public int getInt(String key, int _default) {
        return map.getInt(key, _default);
    }

    public double getDouble(String key, double _default) {
        return map.getDouble(key, _default);
    }

    public String get(String key, String _default) {
        return map.get(key, _default);
    }

    public PMap asPMap() {
        return map;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("profiles:\n");
        for (Profile profile : profiles) {
            sb.append(profile);
            sb.append("\n");
        }
        sb.append("properties:\n");
        for (Map.Entry<String, String> entry : map.toMap().entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue());
            sb.append("\n");
        }
        return sb.toString();
    }
}
