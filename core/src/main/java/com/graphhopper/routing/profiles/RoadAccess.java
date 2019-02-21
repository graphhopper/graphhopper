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

public class RoadAccess extends DefaultIndexBased {
    private static final LinkedHashMap<String, RoadAccess> map = create("other", "unlimited", "private", "customers",
            "destination", "delivery", "no", "forestry", "agricultural");
    public static final RoadAccess OTHER = map.get("other"), UNLIMITED = map.get("unlimited"), PRIVATE = map.get("private"), CUSTOMERS = map.get("customers"),
            DESTINATION = map.get("destination"), DELIVERY = map.get("delivery"), NO = map.get("no"),
            FORESTRY = map.get("forestry"), AGRICULTURAL = map.get("agricultural");

    private RoadAccess(String name, int ordinal) {
        super(name, ordinal);
    }

    public static RoadAccess find(String name) {
        RoadAccess ra = map.get(name);
        return ra == null ? OTHER : ra;
    }

    public static ObjectEncodedValue create() {
        return new MappedObjectEncodedValue("road_access", new ArrayList<>(map.values()));
    }

    public static LinkedHashMap<String, RoadAccess> create(String... list) {
        LinkedHashMap<String, RoadAccess> values = new LinkedHashMap<>();
        for (int counter = 0; counter < list.length; counter++) {
            values.put(list[counter], new RoadAccess(list[counter], counter));
        }
        return values;
    }
}
