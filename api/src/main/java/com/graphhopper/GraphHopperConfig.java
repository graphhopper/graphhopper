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

import com.graphhopper.util.CmdArgs;

import java.util.ArrayList;
import java.util.List;

public class GraphHopperConfig {
    private final CmdArgs cmdArgs;
    private final List<CHProfileConfig> chProfiles;
    private final List<LMProfileConfig> lmProfiles;

    public GraphHopperConfig() {
        this(new CmdArgs());
    }

    public GraphHopperConfig(CmdArgs cmdArgs) {
        this.cmdArgs = cmdArgs;
        chProfiles = new ArrayList<>();
        lmProfiles = new ArrayList<>();
    }

    public void merge(CmdArgs cmdArgs) {
        this.cmdArgs.merge(cmdArgs);
    }

    public GraphHopperConfig put(String key, String value) {
        cmdArgs.put(key, value);
        return this;
    }

    public boolean has(String key) {
        return cmdArgs.has(key);
    }

    public String get(String key, String _default) {
        return cmdArgs.get(key, _default);
    }

    public CmdArgs getCmdArgs() {
        return cmdArgs;
    }

    public void addCHProfiles(List<CHProfileConfig> chProfiles) {
        this.chProfiles.addAll(chProfiles);
    }

    public void addLMProfiles(List<LMProfileConfig> lmProfiles) {
        this.lmProfiles.addAll(lmProfiles);
    }

    public List<CHProfileConfig> getChProfiles() {
        return chProfiles;
    }

    public List<LMProfileConfig> getLmProfiles() {
        return lmProfiles;
    }
}
