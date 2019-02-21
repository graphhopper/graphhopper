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
 * This class defines a IndexBased road environment (ferry, tunnel, ford, ...) but is type safe.
 */
public class RoadEnvironment extends DefaultIndexBased {
    private static final LinkedHashMap<String, RoadEnvironment> map = create("other", "road", "ferry", "tunnel", "bridge", "ford", "shuttle_train");
    public static final RoadEnvironment OTHER = map.get("other"), ROAD = map.get("road"), FERRY = map.get("ferry"),
            TUNNEL = map.get("tunnel"), BRIDGE = map.get("bridge"), FORD = map.get("ford"), SHUTTLE_TRAIN = map.get("shuttle_train");

    public RoadEnvironment(String name, int ordinal) {
        super(name, ordinal);
    }

    public static RoadEnvironment find(String name) {
        RoadEnvironment re = map.get(name);
        return re == null ? OTHER : re;
    }

    public static ObjectEncodedValue create() {
        return new MappedObjectEncodedValue("road_environment", new ArrayList<>(map.values()));
    }

    public static LinkedHashMap<String, RoadEnvironment> create(String... list) {
        LinkedHashMap<String, RoadEnvironment> values = new LinkedHashMap<>();
        for (int counter = 0; counter < list.length; counter++) {
            values.put(list[counter], new RoadEnvironment(list[counter], counter));
        }
        return values;
    }
}
