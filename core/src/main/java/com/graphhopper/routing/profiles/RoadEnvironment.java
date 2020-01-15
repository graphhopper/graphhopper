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
 * This enum the road environment of an edge. Currently road, ferry, tunnel, ford, bridge and shuttle_train. All edges
 * that do not fit get "other" as value.
 */
public enum RoadEnvironment {
    OTHER("other"), ROAD("road"), FERRY("ferry"),
    TUNNEL("tunnel"), BRIDGE("bridge"), FORD("ford"), SHUTTLE_TRAIN("shuttle_train");

    public static final String KEY = "road_environment";

    private final String name;

    RoadEnvironment(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    public static RoadEnvironment find(String name) {
        if (name == null)
            return OTHER;
        try {
            return RoadEnvironment.valueOf(Helper.toUpperCase(name));
        } catch (IllegalArgumentException ex) {
            return OTHER;
        }
    }
}
