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
package com.graphhopper.routing.util;

/**
 * Used to store a priority value in the way flags of an edge. Used in combination with
 * PriorityWeighting
 *
 * @author Peter Karich
 */
public enum PriorityCode {
    WORST(0),
    AVOID_AT_ALL_COSTS(1),
    REACH_DEST(2),
    AVOID_IF_POSSIBLE(3),
    UNCHANGED(4),
    PREFER(5),
    VERY_NICE(6),
    BEST(7);
    private final int value;

    PriorityCode(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    /**
     * This method returns the PriorityCode.value in a range between 0 and 1 suitable for direct usage in a Weighting.
     */
    public static double getFactor(int val) {
        return (double) val / BEST.getValue();
    }

}
