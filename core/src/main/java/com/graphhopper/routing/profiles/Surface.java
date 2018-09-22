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

import com.graphhopper.routing.util.EncodingManager;

public enum Surface {
    DEFAULT("_default"), ASPHALT("asphalt"), UNPAVED("unpaved"), PAVED("paved"), GRAVEL("gravel"), GROUND("ground"),
    DIRT("dirt"), GRASS("grass"), CONCRETE("concrete"), PAVING_STONES("paving_stones"), SAND("sand"),
    COMPACTED("compacted"), COBBLESTONE("cobblestone"), MUD("mud"), ICE("ice");
    String name;

    Surface(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    public static EnumEncodedValue<Surface> create() {
        return new EnumEncodedValue<>(EncodingManager.SURFACE, values(), DEFAULT);
    }
}
