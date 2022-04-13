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
 * Used to store a penalty value in the way flags of an edge. Used in
 * combination with PenaltyWeighting
 *
 * @author Hazel Court
 */
public enum PenaltyCode {
    EXCLUDE(511),
    REACH_DESTINATION(15),
    VERY_BAD(13),
    BAD(12),
    AVOID_MORE(11),
    AVOID(10),
    SLIGHT_AVOID(9),
    UNCHANGED(8),
    SLIGHT_PREFER(6),
    PREFER(5),
    VERY_NICE(3),
    BEST(1);

    private final int value;

    PenaltyCode(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static double getFactor(int value) {
        return (double) value;
    }

    public static double getValue(int value) {
        return getFactor(value);
    }
}
