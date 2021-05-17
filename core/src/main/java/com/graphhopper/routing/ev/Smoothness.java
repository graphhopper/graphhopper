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
 * This enum defines the road smoothness of an edge. If not tagged the value will be MISSING, which is the default. 
 * All unknown smoothness tags will get OTHER .
 */
public enum Smoothness {
    // Order is important to make it roughly comparable
    MISSING("missing"),
    EXCELLENT("excellent"), GOOD("good"), INTERMEDIATE("intermediate"), BAD("bad"), VERY_BAD("very_bad"),
    HORRIBLE("horrible"), VERY_HORRIBLE("very_horrible"), IMPASSABLE("impassable"),
    OTHER("other");

    public static final String KEY = "smoothness";

    private final String name;

    Smoothness(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    public static Smoothness find(String name) {
        if (Helper.isEmpty(name))
            return MISSING;
        try {
            return Smoothness.valueOf(Helper.toUpperCase(name));
        } catch (IllegalArgumentException ex) {
            return OTHER;
        }
    }
}
