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

import java.util.Map;
import java.util.TreeMap;

/**
 * Used to store a priority value in the way flags of an edge. Used in combination with
 * PriorityWeighting
 *
 * @author Peter Karich
 */
public enum PriorityCode {
    EXCLUDE(0),
    REACH_DESTINATION(1),
    VERY_BAD(3),
    BAD(5),
    AVOID_MORE(6),
    AVOID(8),
    SLIGHT_AVOID(9),
    UNCHANGED(10),
    SLIGHT_PREFER(11),
    PREFER(12),
    VERY_NICE(13),
    BEST(15);

    private final int value;
    public static final TreeMap<Integer, PriorityCode> VALUES = new TreeMap<>();

    static {
        PriorityCode[] v = values();
        for (PriorityCode priorityCode : v) {
            VALUES.put(priorityCode.getValue(), priorityCode);
        }
    }

    PriorityCode(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static double getFactor(int value) {
        return (double) value / 10.0;
    }

    public static double getValue(int value) {
        return getFactor(value);
    }

    public PriorityCode worse() {
        Map.Entry<Integer, PriorityCode> ret = VALUES.lowerEntry(this.getValue());
        return ret == null ? EXCLUDE : ret.getValue();
    }

    public static PriorityCode valueOf(int integ) {
        Map.Entry<Integer, PriorityCode> ret = VALUES.ceilingEntry(integ);
        return ret == null ? BEST : ret.getValue();
    }

    public PriorityCode better() {
        Map.Entry<Integer, PriorityCode> ret = VALUES.higherEntry(this.getValue());
        return ret == null ? BEST : ret.getValue();
    }
}
