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

/**
 * Store valid values of the OSM tag mtb:scale=*. See
 * https://wiki.openstreetmap.org/wiki/Key:mtb:scale for details.
 * 
 * Values are stored as enum because the OSM key has a value 0 with a meaning and we would like to
 * be able to store an undefined value ("none") as well.
 * 
 * @author Michael Reichert
 *
 */
public enum MtbScale {
    NONE("none"),
    MTB_0("0"),
    MTB_1("1"),
    MTB_2("2"),
    MTB_3("3"),
    MTB_4("4"),
    MTB_5("5"),
    MTB_6("6");

    public static final String KEY = "mtb_scale";

    private final String name;

    MtbScale(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    public static MtbScale find(String name) {
        if (name == null) {
            return NONE;
        }
        try {
            return MtbScale.valueOf("MTB_" + name);
        } catch (IllegalArgumentException ex) {
            return NONE;
        }
    }
}