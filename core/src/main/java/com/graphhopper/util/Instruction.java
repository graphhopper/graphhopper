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
import java.util.List;
import java.util.Map;

public class Instruction {
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
    public static final int PT_START_TRIP = 101;
    public static final int PT_TRANSFER = 102;
    private static final AngleCalc AC = Helper.ANGLE_CALC;
    protected final PointList points;
    protected final InstructionAnnotation annotation;
    protected boolean rawName;
    protected int sign;
    protected String name;
    protected double distance;
    protected long time;

    /**
     * The points, distances and times have exactly the same count. The last point of this
     * instruction is not duplicated here and should be in the next one.
     */
    public Instruction(int sign, String name, InstructionAnnotation ia, PointList pl) {
        this.sign = sign;
        this.name = name;
        this.points = pl;
        this.annotation = ia;
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
     * Time in time until no new instruction
     */
    public long getTime() {
        return time;
    }

    public Instruction setTime(long time) {
        this.time = time;
        return this;
    }

    /**
     * Latitude of the location where this instruction should take place.
     */
    double getFirstLat() {
        return points.getLatitude(0);
    }

    /**
     * Longitude of the location where this instruction should take place.
     */
    double getFirstLon() {
        return points.getLongitude(0);
    }

    double getFirstEle() {
        return points.getElevation(0);
    }

    public PointList getPoints() {
        return points;
    }

    /**
     * This method returns a list of gpx entries where the time (in time) is relative to the first
     * which is 0. It does NOT contain the last point which is the first of the next instruction.
     * <p>
     *
     * @return the time offset to add for the next instruction
     */
    long fillGPXList(List<GPXEntry> list, long time,
                     Instruction prevInstr, Instruction nextInstr, boolean firstInstr) {
        checkOne();
        int len = points.size();
        long prevTime = time;
        double lat = points.getLatitude(0);
        double lon = points.getLongitude(0);
        double ele = Double.NaN;
        boolean is3D = points.is3D();
        if (is3D)
            ele = points.getElevation(0);

        for (int i = 0; i < len; i++) {
            list.add(new GPXEntry(lat, lon, ele, prevTime));

            boolean last = i + 1 == len;
            double nextLat = last ? nextInstr.getFirstLat() : points.getLatitude(i + 1);
            double nextLon = last ? nextInstr.getFirstLon() : points.getLongitude(i + 1);
            double nextEle = is3D ? (last ? nextInstr.getFirstEle() : points.getElevation(i + 1)) : Double.NaN;
            if (is3D)
                prevTime = Math.round(prevTime + this.time * Helper.DIST_3D.calcDist(nextLat, nextLon, nextEle, lat, lon, ele) / distance);
            else
                prevTime = Math.round(prevTime + this.time * Helper.DIST_3D.calcDist(nextLat, nextLon, lat, lon) / distance);

            lat = nextLat;
            lon = nextLon;
            ele = nextEle;
        }
        return time + this.time;
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

    /**
     * Return the direction like 'NE' based on the first tracksegment of the instruction. If
     * Instruction does not contain enough coordinate points, an empty string will be returned.
     */
    String calcDirection(Instruction nextI) {
        double azimuth = calcAzimuth(nextI);
        if (Double.isNaN(azimuth))
            return "";

        return AC.azimuth2compassPoint(azimuth);
    }

    /**
     * Return the azimuth in degree based on the first tracksegment of this instruction. If this
     * instruction contains less than 2 points then NaN will be returned or the specified
     * instruction will be used if that is the finish instruction.
     */
    public double calcAzimuth(Instruction nextI) {
        double nextLat;
        double nextLon;

        if (points.getSize() >= 2) {
            nextLat = points.getLatitude(1);
            nextLon = points.getLongitude(1);
        } else if (nextI != null && points.getSize() == 1) {
            nextLat = nextI.points.getLatitude(0);
            nextLon = nextI.points.getLongitude(0);
        } else {
            return Double.NaN;
        }

        double lat = points.getLatitude(0);
        double lon = points.getLongitude(0);
        return AC.calcAzimuth(lat, lon, nextLat, nextLon);
    }

    void checkOne() {
        if (points.size() < 1)
            throw new IllegalStateException("Instruction must contain at least one point " + toString());
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
        } else {
            String dir = null;
            switch (indi) {
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
            }
            if (dir == null) {
                return "unknown " + (Helper.isEmpty(streetName) ? "" : streetName);
                // throw new IllegalStateException("Turn indication not found " + indi);
            }

            str = Helper.isEmpty(streetName) ? dir : tr.tr("turn_onto", dir, streetName);
        }
        return str;
    }
}
