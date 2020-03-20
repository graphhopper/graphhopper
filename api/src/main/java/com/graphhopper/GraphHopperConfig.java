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

import com.graphhopper.config.CHProfileConfig;
import com.graphhopper.config.LMProfileConfig;
import com.graphhopper.config.ProfileConfig;
import com.graphhopper.util.PMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This class represents the global configuration for the GraphHopper class, which is typically configured via the
 * `config.yml` file. Certain fields are mapped to dedicated config objects to allow a hierarchical configuration and
 * to include lists. All other fields are mapped to a key-value (string-string) map. In the future we will start adding
 * the different configuration options as fields of this class including the default values.
 */
public class GraphHopperConfig {
    private List<ProfileConfig> profiles = new ArrayList<>();
    private List<CHProfileConfig> chProfiles = new ArrayList<>();
    private List<LMProfileConfig> lmProfiles = new ArrayList<>();
    private final PMap map;

    public GraphHopperConfig() {
        this(new PMap());
    }

    public GraphHopperConfig(PMap pMap) {
        this.map = pMap;
    }

    public List<ProfileConfig> getProfiles() {
        return profiles;
    }

    public GraphHopperConfig setProfiles(List<ProfileConfig> profiles) {
        this.profiles = profiles;
        return this;
    }

    public List<CHProfileConfig> getCHProfiles() {
        return chProfiles;
    }

    public GraphHopperConfig setCHProfiles(List<CHProfileConfig> chProfiles) {
        this.chProfiles = chProfiles;
        return this;
    }

    public List<LMProfileConfig> getLMProfiles() {
        return lmProfiles;
    }

    public GraphHopperConfig setLMProfiles(List<LMProfileConfig> lmProfiles) {
        this.lmProfiles = lmProfiles;
        return this;
    }

    public GraphHopperConfig putObject(String key, Object value) {
        map.putObject(key, value);
        return this;
    }

    public boolean has(String key) {
        return map.has(key);
    }

    public boolean getBool(String key, boolean _default) {
        return map.getBool(key, _default);
    }

    public int getInt(String key, int _default) {
        return map.getInt(key, _default);
    }

    public long getLong(String key, long _default) {
        return map.getLong(key, _default);
    }

    public float getFloat(String key, float _default) {
        return map.getFloat(key, _default);
    }

    public double getDouble(String key, double _default) {
        return map.getDouble(key, _default);
    }

    public String getString(String key, String _default) {
        return map.getString(key, _default);
    }

    public PMap asPMap() {
        return map;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("profiles:\n");
        for (ProfileConfig profile : profiles) {
            sb.append(profile);
            sb.append("\n");
        }
        sb.append("profiles_ch:\n");
        for (CHProfileConfig profile : chProfiles) {
            sb.append(profile);
            sb.append("\n");
        }
        sb.append("profiles_lm:\n");
        for (LMProfileConfig profile : lmProfiles) {
            sb.append(profile);
            sb.append("\n");
        }
        sb.append("properties:\n");
        for (Map.Entry<String, Object> entry : map.toMap().entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue());
            sb.append("\n");
        }
        return sb.toString();
    }
}
