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
 * This enum defines the track type of an edge which describes how
 * well-maintained a certain track is. All edges that do not fit get "other" as
 * value.
 * 
 * @see https://wiki.openstreetmap.org/wiki/Tracktype
 */
public enum TrackType {
    OTHER("other"), GRADE1("grade1"), GRADE2("grade2"), GRADE3("grade3"), GRADE4("grade4"),
    GRADE5("grade5");

    public static final String KEY = "track_type";

    private final String name;

    TrackType(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    public static TrackType find(String name) {
        if (name == null)
            return OTHER;
        try {
            return TrackType.valueOf(Helper.toUpperCase(name));
        } catch (IllegalArgumentException ex) {
            return OTHER;
        }
    }
}
