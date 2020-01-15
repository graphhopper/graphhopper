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
 * This enum defines the road surface of an edge like unpaved or asphalt. If not tagged it will be "other".
 */
public enum Surface {
    OTHER("other"),
    PAVED("paved"), ASPHALT("asphalt"), CONCRETE("concrete"), PAVING_STONES("paving_stones"), COBBLESTONE("cobblestone"),
    UNPAVED("unpaved"), COMPACTED("compacted"), FINE_GRAVEL("fine_gravel"), GRAVEL("gravel"),
    GROUND("ground"), DIRT("dirt"), GRASS("grass"), SAND("sand");

    public static final String KEY = "surface";

    private final String name;

    Surface(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    public static Surface find(String name) {
        if (name == null)
            return OTHER;
        try {
            return Surface.valueOf(Helper.toUpperCase(name));
        } catch (IllegalArgumentException ex) {
            return OTHER;
        }
    }
}
