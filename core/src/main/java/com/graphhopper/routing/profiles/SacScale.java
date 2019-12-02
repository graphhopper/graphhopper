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
package com.graphhopper.routing.profiles;

import com.graphhopper.util.Helper;

/**
 * Store valid values of the OSM tag sac_scale=*. See
 * https://wiki.openstreetmap.org/wiki/Key:sac_scale for details. 
 * 
 * @author Michael Reichert
 *
 */
public enum SacScale {
    NONE("none"),
    HIKING("hiking"),
    MOUNTAIN_HIKING("mountain_hiking"),
    DEMANDING_MOUNTAIN_HIKING("demanding_mountain_hiking"),
    ALPINE_HIKING("alpine_hiking"),
    DEMANDING_ALPINE_HIKING("demanding_alpine_hiking"),
    DIFFICULT_ALPINE_HIKING("difficult_alpine_hiking");

    public static final String KEY = "sac_scale";

    private final String name;

    SacScale(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    public static SacScale find(String name) {
        if (name == null)
            return NONE;
        try {
            return SacScale.valueOf(Helper.toUpperCase(name));
        } catch (IllegalArgumentException ex) {
            return NONE;
        }
    }
}
