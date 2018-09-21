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

import java.util.ArrayList;
import java.util.List;

public class RoadEnvironmentEncodedValue extends StringEncodedValue {
    public enum Key {
        DEFAULT("_default"), BRIDGE("bridge"), TUNNEL("tunnel"), FORD("ford"), AERIALWAY("aerialway");

        private final String name;

        Key(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public RoadEnvironmentEncodedValue() {
        super(EncodingManager.ROAD_ENV, getKeysAsStrings(), Key.DEFAULT.name);
    }

    // TODO should we use enum in general in this class instead of strings?
    public static List<String> getKeysAsStrings() {
        List<String> list = new ArrayList<>();
        for (Object v : Key.values()) {
            if (v == null)
                throw new IllegalArgumentException("Do not add null values");
            list.add(v.toString());
        }
        return list;
    }
}
