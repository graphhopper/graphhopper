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
package com.graphhopper.routing.ev;

/**
 * The horseback riding difficulty of an edge.
 * See <a href="https://wiki.openstreetmap.org/wiki/Key:horse_scale">Key:horse_scale</a> for details.
 */
public enum HorseScale {
    NONE("none"),
    DEMANDING("demanding"),
    DIFFICULT("difficult"),
    CRITICAL("critical"),
    DANGEROUS("dangerous"),
    IMPOSSIBLE("impossible");

    public static final String KEY = "horse_scale";

    private final String name;

    HorseScale(String name) {
        this.name = name;
    }

    public static HorseScale find(String name) {
        if (name == null)
            return NONE;
        try {
            return HorseScale.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return NONE;
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
