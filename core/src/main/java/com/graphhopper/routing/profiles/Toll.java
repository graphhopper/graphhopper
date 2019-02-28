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
 * This class defines a IndexBased toll
 */
public class Toll extends DefaultIndexBased {
    public static final String KEY = "toll";
    private static final LinkedHashMap<String, Toll> map = create("no", "all", "hgv");
    public static final Toll NO = map.get("no"), ALL = map.get("all"), HGV = map.get("hgv");

    public Toll(String name, int ordinal) {
        super(name, ordinal);
    }

    public static Toll find(String name) {
        Toll re = map.get(name);
        return re == null ? NO : re;
    }

    public static ObjectEncodedValue create() {
        return new MappedObjectEncodedValue(KEY, new ArrayList<>(map.values()));
    }

    public static LinkedHashMap<String, Toll> create(String... list) {
        LinkedHashMap<String, Toll> values = new LinkedHashMap<>();
        for (int counter = 0; counter < list.length; counter++) {
            values.put(list[counter], new Toll(list[counter], counter));
        }
        return values;
    }
}
