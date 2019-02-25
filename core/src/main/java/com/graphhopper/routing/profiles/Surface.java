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

import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * This class defines a IndexBased but is type safe.
 */
public class Surface extends DefaultIndexBased {
    public static final String KEY = "surface";
    private static final LinkedHashMap<String, Surface> map = create("other", "cobblestone", "asphalt", "paved", "unpaved",
            "ground", "gravel", "concrete", "paving_stone", "dirt", "parking", "grass", "sand");
    public static final Surface OTHER = map.get("other"), COBBLESTONE = map.get("cobblestone"),
            ASPHALT = map.get("asphalt"), PAVED = map.get("paved"), UNPAVED = map.get("unpaved"),
            GROUND = map.get("ground"), GRAVEL = map.get("gravel"), CONCRETE = map.get("concrete"),
            PAVING_STONE = map.get("paving_stone"), DIRT = map.get("dirt"),
            PARKING = map.get("parking"), GRASS = map.get("grass"), SAND = map.get("sand");

    public Surface(String name, int ordinal) {
        super(name, ordinal);
    }

    public static Surface find(String name) {
        Surface s = map.get(name);
        return s == null ? OTHER : s;
    }

    public static ObjectEncodedValue create() {
        return new MappedObjectEncodedValue(KEY, new ArrayList<>(map.values()));
    }

    public static LinkedHashMap<String, Surface> create(String... list) {
        LinkedHashMap<String, Surface> values = new LinkedHashMap<>();
        for (int counter = 0; counter < list.length; counter++) {
            values.put(list[counter], new Surface(list[counter], counter));
        }
        return values;
    }
}
