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
 * This enum defines the sidewalk encoded value, parsed from sidewalk, sidewalk:left, sidewalk:right and
 * sidewalk:both OSM tags. If not tagged or unknown the value will be MISSING.
 * <p>
 * This encoded value stores two directions. The main sidewalk tag encodes direction as its value
 * (left, right, both), while the directional keys (sidewalk:left, sidewalk:right) encode presence
 * as their value (yes, no, separate).
 *
 * @see <a href="https://wiki.openstreetmap.org/wiki/Key:sidewalk">Key:sidewalk</a>
 */
public enum Sidewalk {
    MISSING, YES, SEPARATE, NO;

    public static final String KEY = "sidewalk";

    public static EnumEncodedValue<Sidewalk> create() {
        return new EnumEncodedValue<>(KEY, Sidewalk.class, true);
    }

    @Override
    public String toString() {
        return Helper.toLowerCase(super.toString());
    }

    public static Sidewalk find(String name) {
        if (Helper.isEmpty(name))
            return MISSING;
        if ("none".equals(name))
            return NO;
        try {
            return Sidewalk.valueOf(Helper.toUpperCase(name));
        } catch (IllegalArgumentException ex) {
            return MISSING;
        }
    }
}
