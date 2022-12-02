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

import java.util.HashMap;
import java.util.Map;

/**
 * @author jansoe
 */
public class RoundaboutInstruction extends Instruction {
    private int exitNumber = 0;
    // 0 undetermined, 1 clockwise, -1 counterclockwise, 2 inconsistent
    private int clockwise = 0;
    private boolean exited = false;
    private double radian = Double.NaN;

    public RoundaboutInstruction(int sign, String name, PointList pl) {
        super(sign, name, pl);
    }

    public RoundaboutInstruction increaseExitNumber() {
        this.exitNumber += 1;
        return this;
    }

    public RoundaboutInstruction setDirOfRotation(double deltaIn) {
        if (clockwise == 0) {
            clockwise = deltaIn > 0 ? 1 : -1;
        } else {
            int clockwise2 = deltaIn > 0 ? 1 : -1;
            if (clockwise != clockwise2) {
                clockwise = 2;
            }
        }
        return this;
    }

    public RoundaboutInstruction setExited() {
        exited = true;
        return this;
    }

    public boolean isExited() {
        return exited;
    }

    public int getExitNumber() {
        if (exited && exitNumber == 0) {
            throw new IllegalStateException("RoundaboutInstruction must contain exitNumber>0");
        }
        return exitNumber;
    }

    public RoundaboutInstruction setExitNumber(int exitNumber) {
        this.exitNumber = exitNumber;
        return this;
    }

    /**
     * @return radian of angle -2PI &lt; x &lt; 2PI between roundabout entrance and exit values
     * <ul>
     * <li>&gt; 0 is for clockwise rotation</li>
     * <li>&lt; 0 is for counterclockwise rotation</li>
     * <li>NaN if direction of rotation is unclear</li>
     * </ul>
     */
    public double getTurnAngle() {
        if (Math.abs(clockwise) != 1)
            return Double.NaN;
        else
            return Math.PI * clockwise - radian;
    }

    /**
     * The radian value between entrance (in) and exit (out) of this roundabout.
     */
    public RoundaboutInstruction setRadian(double radian) {
        this.radian = radian;
        return this;
    }

    @Override
    public Map<String, Object> getExtraInfoJSON() {
        Map<String, Object> tmpMap = new HashMap<>(3);
        tmpMap.put("exit_number", getExitNumber());
        tmpMap.put("exited", this.exited);
        double tmpAngle = getTurnAngle();
        if (!Double.isNaN(tmpAngle))
            tmpMap.put("turn_angle", Helper.round(tmpAngle, 2));

        return tmpMap;

    }

    @Override
    public String getTurnDescription(Translation tr) {
        if (rawName)
            return getName();

        String str;
        String streetName = _getName();
        int indi = getSign();
        if (indi == Instruction.USE_ROUNDABOUT) {
            if (!exited) {
                str = tr.tr("roundabout_enter");
            } else {
                str = Helper.isEmpty(streetName) ? tr.tr("roundabout_exit", getExitNumber())
                        : tr.tr("roundabout_exit_onto", getExitNumber(), streetName);
            }
        } else {
            throw new IllegalStateException(indi + "no roundabout indication");
        }
        return str;
    }
}
