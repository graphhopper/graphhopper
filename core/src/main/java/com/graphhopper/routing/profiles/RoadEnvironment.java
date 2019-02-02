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

import java.util.Arrays;
import java.util.List;

public class RoadEnvironment extends AbstractIndexBased {
    public static final RoadEnvironment BRIDGE = new RoadEnvironment("bridge", 1),
            TUNNEL = new RoadEnvironment("tunnel", 2), FORD = new RoadEnvironment("ford", 3);
    private static final RoadEnvironment AERIALWAY = new RoadEnvironment("aerialway", 4);
    private static final List<RoadEnvironment> values = Arrays.asList(new RoadEnvironment("_default", 0), BRIDGE, TUNNEL, FORD, AERIALWAY);

    private RoadEnvironment(String name, int ordinal) {
        super(name, ordinal);
    }

    @JsonCreator
    static RoadEnvironment deserialize(String name) {
        for (RoadEnvironment rc : values) {
            if (rc.toString().equals(name))
                return rc;
        }
        throw new IllegalArgumentException("Cannot find RoadEnvironment " + name);
    }

    public static ObjectEncodedValue create() {
        return new MappedObjectEncodedValue(EncodingManager.ROAD_ENV, values);
    }
}
