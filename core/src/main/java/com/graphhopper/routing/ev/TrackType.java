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
 * This enum defines the track type of an edge which describes how well-maintained a certain track is.
 * If there is no value tagged or if the value does not fit the given values, the TrackType will be MISSING.
 * grade1 is a very well-maintained road, grade5 is a poorly maintained road.
 *
 * @see <a href="https://wiki.openstreetmap.org/wiki/Tracktype">Tracktype Wiki</a>
 */
public enum TrackType {
    MISSING, GRADE1, GRADE2, GRADE3, GRADE4, GRADE5;

    public static final String KEY = "track_type";

    public static EnumEncodedValue<TrackType> create() {
        return new EnumEncodedValue<>(KEY, TrackType.class);
    }

    @Override
    public String toString() {
        return Helper.toLowerCase(super.toString());
    }

    public static TrackType find(String name) {
        if (Helper.isEmpty(name))
            return MISSING;
        try {
            return TrackType.valueOf(Helper.toUpperCase(name));
        } catch (IllegalArgumentException ex) {
            return MISSING;
        }
    }
}
