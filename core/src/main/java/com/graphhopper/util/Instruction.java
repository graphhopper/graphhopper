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
package com.graphhopper.util;

import java.util.Collections;
import java.util.Map;

public class Instruction {
    public static final int UNKNOWN = -99;
    public static final int LEAVE_ROUNDABOUT = -6; // for future use
    public static final int TURN_SHARP_LEFT = -3;
    public static final int TURN_LEFT = -2;
    public static final int TURN_SLIGHT_LEFT = -1;
    public static final int CONTINUE_ON_STREET = 0;
    public static final int TURN_SLIGHT_RIGHT = 1;
    public static final int TURN_RIGHT = 2;
    public static final int TURN_SHARP_RIGHT = 3;
    public static final int FINISH = 4;
    public static final int REACHED_VIA = 5;
    public static final int USE_ROUNDABOUT = 6;
    public static final int IGNORE = Integer.MIN_VALUE;
    public static final int KEEP_LEFT = -7;
    public static final int KEEP_RIGHT = 7;
    public static final int PT_START_TRIP = 101;
    public static final int PT_TRANSFER = 102;
    public static final int PT_END_TRIP = 103;
    protected final InstructionAnnotation annotation;
    protected boolean rawName;
    protected int sign;
    protected String name;
    protected double distance;
    protected long time;

    private int first;
    private int last;

    /**
     * The points, distances and times have exactly the same count. The last point of this
     * instruction is not duplicated here and should be in the next one.
     */
    public Instruction(int sign, String name, InstructionAnnotation ia, int first) {
        this.sign = sign;
        this.name = name;
        this.annotation = ia;
        this.first = first;
    }

    public Instruction(int sign, String name, InstructionAnnotation ia, int first, int last) {
        this(sign, name, ia, first);
        this.last = last;
    }

    /**
     * This method does not perform translation or combination with the sign - it just uses the
     * provided name as instruction.
     */
    public void setUseRawName() {
        rawName = true;
    }

    public InstructionAnnotation getAnnotation() {
        return annotation;
    }

    /**
     * The instruction for the person/driver to execute.
     */
    public int getSign() {
        return sign;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getExtraInfoJSON() {
        return Collections.<String, Object>emptyMap();
    }

    public void setExtraInfo(String key, Object value) {
        throw new IllegalArgumentException("Key" + key + " is not a valid option");
    }

    /**
     * Distance in meter until no new instruction
     */
    public double getDistance() {
        return distance;
    }

    public Instruction setDistance(double distance) {
        this.distance = distance;
        return this;
    }

    /**
     * Duration until the next instruction, in milliseconds
     */
    public long getTime() {
        return time;
    }

    public Instruction setTime(long time) {
        this.time = time;
        return this;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        sb.append(sign).append(',');
        sb.append(name).append(',');
        sb.append(distance).append(',');
        sb.append(time);
        sb.append(')');
        return sb.toString();
    }

    void checkOne() {
        if (last < first)
            throw new IllegalStateException("last cannot be smaller than first");
    }

    public void setFirst(int first) {
        this.first = first;
    }

    public int getFirst() {
        return first;
    }

    public void setLast(int last) {
        this.last = last;
    }

    public int getLast() {
        if (last < first)
            throw new IllegalStateException("last cannot be smaller than first");
        return last;
    }

    public int getLength() {
        return last - first;
    }

    public String getTurnDescription(Translation tr) {
        if (rawName)
            return getName();

        String str;
        String streetName = getName();
        int indi = getSign();
        if (indi == Instruction.CONTINUE_ON_STREET) {
            str = Helper.isEmpty(streetName) ? tr.tr("continue") : tr.tr("continue_onto", streetName);
        } else if (indi == Instruction.PT_START_TRIP) {
            str = tr.tr("pt_start_trip", streetName);
        } else if (indi == Instruction.PT_TRANSFER) {
            str = tr.tr("pt_transfer_to", streetName);
        } else if (indi == Instruction.PT_END_TRIP) {
            str = tr.tr("pt_end_trip", streetName);
        } else {
            String dir = null;
            switch (indi) {
                case Instruction.KEEP_LEFT:
                    dir = tr.tr("keep_left");
                    break;
                case Instruction.TURN_SHARP_LEFT:
                    dir = tr.tr("turn_sharp_left");
                    break;
                case Instruction.TURN_LEFT:
                    dir = tr.tr("turn_left");
                    break;
                case Instruction.TURN_SLIGHT_LEFT:
                    dir = tr.tr("turn_slight_left");
                    break;
                case Instruction.TURN_SLIGHT_RIGHT:
                    dir = tr.tr("turn_slight_right");
                    break;
                case Instruction.TURN_RIGHT:
                    dir = tr.tr("turn_right");
                    break;
                case Instruction.TURN_SHARP_RIGHT:
                    dir = tr.tr("turn_sharp_right");
                    break;
                case Instruction.KEEP_RIGHT:
                    dir = tr.tr("keep_right");
                    break;
            }
            if (dir == null)
                str = tr.tr("unknown", indi);
            else
                str = Helper.isEmpty(streetName) ? dir : tr.tr("turn_onto", dir, streetName);
        }
        return str;
    }
}
