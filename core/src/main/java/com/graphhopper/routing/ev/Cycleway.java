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
 * This enum defines the cycleway encoded value, parsed from cycleway, cycleway:left, cycleway:right and
 * cycleway:both OSM tags. If not tagged or unknown the value will be MISSING.
 * <p>
 * This encoded value stores two directions. The OSM tags cycleway:right and cycleway:left map to forward and reverse
 * respectively. The deprecated opposite_lane and opposite_track tags are stored as LANE and TRACK in the reverse
 * direction.
 *
 * @see <a href="https://wiki.openstreetmap.org/wiki/Key:cycleway">Key:cycleway</a>
 */
public enum Cycleway {
    MISSING, TRACK, LANE, SHARED_LANE, SHOULDER, SEPARATE, NO;

    public static final String KEY = "cycleway";

    public static EnumEncodedValue<Cycleway> create() {
        return new EnumEncodedValue<>(KEY, Cycleway.class, true);
    }

    @Override
    public String toString() {
        return Helper.toLowerCase(super.toString());
    }

    public static Cycleway find(String name) {
        if (Helper.isEmpty(name))
            return MISSING;
        switch (name) {
            case "sidepath":
                return SEPARATE;
            case "crossing":
                return TRACK;
            case "share_busway", "shared":
                return SHARED_LANE;
        }
        try {
            return Cycleway.valueOf(Helper.toUpperCase(name));
        } catch (IllegalArgumentException ex) {
            return MISSING;
        }
    }
}
