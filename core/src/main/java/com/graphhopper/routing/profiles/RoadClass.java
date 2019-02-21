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
public class RoadClass extends DefaultIndexBased {
    private static final LinkedHashMap<String, RoadClass> map = create("other", "motorway", "motorroad", "trunk",
            "primary", "secondary", "tertiary", "residential", "unclassified", "service", "road", "track", "forestry",
            "steps", "cycleway", "path", "living_street");
    public static final RoadClass OTHER = map.get("other"), MOTORWAY = map.get("motorway"), MOTORROAD = map.get("motorroad"),
            TRUNK = map.get("trunk"), PRIMARY = map.get("primary"), SECONDARY = map.get("secondary"),
            TERTIARY = map.get("tertiary"), RESIDENTIAL = map.get("residential"), UNCLASSIFIED = map.get("unclassified"),
            SERVICE = map.get("service"), ROAD = map.get("road"), TRACK = map.get("track"),
            FORESTRY = map.get("forestry"), STEPS = map.get("steps"), CYCLEWAY = map.get("cycleway"),
            PATH = map.get("path"), LIVING_STREET = map.get("living_street");

    private RoadClass(String name, int ordinal) {
        super(name, ordinal);
    }

    public static RoadClass find(String name) {
        RoadClass rc = map.get(name);
        return rc == null ? OTHER : rc;
    }

    public static ObjectEncodedValue create() {
        return new MappedObjectEncodedValue("road_class", new ArrayList<>(map.values()));
    }

    public static LinkedHashMap<String, RoadClass> create(String... list) {
        LinkedHashMap<String, RoadClass> values = new LinkedHashMap<>();
        for (int counter = 0; counter < list.length; counter++) {
            values.put(list[counter], new RoadClass(list[counter], counter));
        }
        return values;
    }
}
