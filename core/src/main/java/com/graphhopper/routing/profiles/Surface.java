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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.graphhopper.routing.util.EncodingManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Surface extends AbstractIndexBased {
    public static final Surface DEFAULT = new Surface("_default", 0), ASPHALT = new Surface("asphalt", 1),
            UNPAVED = new Surface("unpaved", 2), PAVED = new Surface("paved", 3),
            GRAVEL = new Surface("gravel", 4), GROUND = new Surface("ground", 5),
            DIRT = new Surface("dirt", 6), GRASS = new Surface("grass", 7),
            CONCRETE = new Surface("concrete", 8), PAVING_STONES = new Surface("paving_stones", 9),
            SAND = new Surface("sand", 10), COMPACTED = new Surface("compacted", 11),
            COBBLESTONE = new Surface("cobblestone", 12), MUD = new Surface("mud", 13),
            ICE = new Surface("ice", 14);

    private static final List<Surface> values = create("_default", "asphalt", "unpaved", "paved", "gravel", "ground", "dirt", "grass", "concrete",
            "paving_stones", "sand", "compacted", "cobblestone", "mud", "ice");

    public Surface(String name, int ordinal) {
        super(name, ordinal);
    }

    @JsonCreator
    static Surface deserialize(String name) {
        for (Surface rc : values) {
            if (rc.toString().equals(name))
                return rc;
        }
        throw new IllegalArgumentException("Cannot find Surface " + name);
    }

    public static ObjectEncodedValue create() {
        return new MappedObjectEncodedValue(EncodingManager.SURFACE, values);
    }

    public static List<Surface> create(String... values) {
        List<Surface> list = new ArrayList<>(values.length);
        for (int i = 0; i < values.length; i++) {
            list.add(new Surface(values[i], i));
        }
        return Collections.unmodifiableList(list);
    }
}
