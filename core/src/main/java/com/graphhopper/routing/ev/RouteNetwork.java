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
package com.graphhopper.routing.ev;

import com.graphhopper.util.Helper;

/**
 * This enum defines the route network of an edge when part of a hiking or biking network.
 * If not tagged the value will be MISSING (default) and all edges that do not fit get OTHER as value.
 */
public enum RouteNetwork {

    MISSING("missing"), INTERNATIONAL("international"), NATIONAL("national"), REGIONAL("regional"),
    LOCAL("local"), OTHER("other");

    public static String key(String prefix) {
        return prefix + "_network";
    }

    public static EnumEncodedValue<RouteNetwork> create(String name) {
        return new EnumEncodedValue<>(name, RouteNetwork.class);
    }

    private final String name;

    RouteNetwork(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    public static RouteNetwork find(String name) {
        if (Helper.isEmpty(name))
            return MISSING;
        try {
            return RouteNetwork.valueOf(Helper.toUpperCase(name));
        } catch (IllegalArgumentException ex) {
            return MISSING;
        }
    }
}
