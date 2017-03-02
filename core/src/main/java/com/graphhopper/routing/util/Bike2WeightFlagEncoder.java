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

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.util.BitUtil;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;
import com.graphhopper.util.PointList;

import static com.graphhopper.util.Helper.keepIn;

/**
 * Stores two speed values into an edge to support avoiding too much incline
 * <p>
 *
 * @author Peter Karich
 */
public class Bike2WeightFlagEncoder extends BikeFlagEncoder {
    private EncodedDoubleValue reverseSpeedEncoder;

    public Bike2WeightFlagEncoder() {
        super();
    }

    public Bike2WeightFlagEncoder(String propertiesStr) {
        super(new PMap(propertiesStr));
    }

    public Bike2WeightFlagEncoder(PMap properties) {
        super(properties);
    }

    public Bike2WeightFlagEncoder(int speedBits, double speedFactor, int maxTurnCosts) {
        super(speedBits, speedFactor, maxTurnCosts);
    }

    @Override
    public int getVersion() {
        return 2;
    }

    @Override
    public int defineWayBits(int index, int shift) {
        shift = super.defineWayBits(index, shift);
        reverseSpeedEncoder = new EncodedDoubleValue("Reverse Speed", shift, speedBits, speedFactor,
                getHighwaySpeed("cycleway"), maxPossibleSpeed);
        shift += reverseSpeedEncoder.getBits();
        return shift;
    }

    @Override
    public double getReverseSpeed(long flags) {
        return reverseSpeedEncoder.getDoubleValue(flags);
    }

    @Override
    public long setReverseSpeed(long flags, double speed) {
        if (speed < 0)
            throw new IllegalArgumentException("Speed cannot be negative: " + speed + ", flags:" + BitUtil.LITTLE.toBitString(flags));

        if (speed < speedEncoder.factor / 2)
            return setLowSpeed(flags, speed, true);

        if (speed > getMaxSpeed())
            speed = getMaxSpeed();

        return reverseSpeedEncoder.setDoubleValue(flags, speed);
    }

    @Override
    public long handleSpeed(ReaderWay way, double speed, long flags) {
        // handle oneways
        flags = super.handleSpeed(way, speed, flags);
        if (isBackward(flags))
            flags = setReverseSpeed(flags, speed);

        if (isForward(flags))
            flags = setSpeed(flags, speed);

        return flags;
    }

    @Override
    protected long setLowSpeed(long flags, double speed, boolean reverse) {
        if (reverse)
            return setBool(reverseSpeedEncoder.setDoubleValue(flags, 0), K_BACKWARD, false);

        return setBool(speedEncoder.setDoubleValue(flags, 0), K_FORWARD, false);
    }

    @Override
    public long flagsDefault(boolean forward, boolean backward) {
        long flags = super.flagsDefault(forward, backward);
        if (backward)
            return reverseSpeedEncoder.setDefaultValue(flags);

        return flags;
    }

    @Override
    public long setProperties(double speed, boolean forward, boolean backward) {
        long flags = super.setProperties(speed, forward, backward);
        if (backward)
            return setReverseSpeed(flags, speed);

        return flags;
    }

    @Override
    public long reverseFlags(long flags) {
        // swap access
        flags = super.reverseFlags(flags);

        // swap speeds 
        double otherValue = reverseSpeedEncoder.getDoubleValue(flags);
        flags = setReverseSpeed(flags, speedEncoder.getDoubleValue(flags));
        return setSpeed(flags, otherValue);
    }

    @Override
    public void applyWayTags(ReaderWay way, EdgeIteratorState edge) {
        PointList pl = edge.fetchWayGeometry(3);
        if (!pl.is3D())
            throw new IllegalStateException("To support speed calculation based on elevation data it is necessary to enable import of it.");

        long flags = edge.getFlags();

        if (way.hasTag("tunnel", "yes") || way.hasTag("bridge", "yes") || way.hasTag("highway", "steps")) {
            // do not change speed
            // note: although tunnel can have a difference in elevation it is very unlikely that the elevation data is correct for a tunnel
        } else {
            // Decrease the speed for ele increase (incline), and decrease the speed for ele decrease (decline). The speed-decrease 
            // has to be bigger (compared to the speed-increase) for the same elevation difference to simulate loosing energy and avoiding hills.
            // For the reverse speed this has to be the opposite but again keeping in mind that up+down difference.
            double incEleSum = 0, incDist2DSum = 0;
            double decEleSum = 0, decDist2DSum = 0;
            // double prevLat = pl.getLatitude(0), prevLon = pl.getLongitude(0);
            double prevEle = pl.getElevation(0);
            double fullDist2D = edge.getDistance();

            if (Double.isInfinite(fullDist2D))
                throw new IllegalStateException("Infinite distance should not happen due to #435. way ID=" + way.getId());

            // for short edges an incline makes no sense and for 0 distances could lead to NaN values for speed, see #432
            if (fullDist2D < 1)
                return;

            double eleDelta = pl.getElevation(pl.size() - 1) - prevEle;
            if (eleDelta > 0.1) {
                incEleSum = eleDelta;
                incDist2DSum = fullDist2D;
            } else if (eleDelta < -0.1) {
                decEleSum = -eleDelta;
                decDist2DSum = fullDist2D;
            }

//            // get a more detailed elevation information, but due to bad SRTM data this does not make sense now.
//            for (int i = 1; i < pl.size(); i++)
//            {
//                double lat = pl.getLatitude(i);
//                double lon = pl.getLongitude(i);
//                double ele = pl.getElevation(i);
//                double eleDelta = ele - prevEle;
//                double dist2D = distCalc.calcDist(prevLat, prevLon, lat, lon);
//                if (eleDelta > 0.1)
//                {
//                    incEleSum += eleDelta;
//                    incDist2DSum += dist2D;
//                } else if (eleDelta < -0.1)
//                {
//                    decEleSum += -eleDelta;
//                    decDist2DSum += dist2D;
//                }
//                fullDist2D += dist2D;
//                prevLat = lat;
//                prevLon = lon;
//                prevEle = ele;
//            }
            // Calculate slop via tan(asin(height/distance)) but for rather smallish angles where we can assume tan a=a and sin a=a.
            // Then calculate a factor which decreases or increases the speed.
            // Do this via a simple quadratic equation where y(0)=1 and y(0.3)=1/4 for incline and y(0.3)=2 for decline        
            double fwdIncline = incDist2DSum > 1 ? incEleSum / incDist2DSum : 0;
            double fwdDecline = decDist2DSum > 1 ? decEleSum / decDist2DSum : 0;
            double restDist2D = fullDist2D - incDist2DSum - decDist2DSum;
            double maxSpeed = getHighwaySpeed("cycleway");
            if (isForward(flags)) {
                // use weighted mean so that longer incline influences speed more than shorter
                double speed = getSpeed(flags);
                double fwdFaster = 1 + 2 * keepIn(fwdDecline, 0, 0.2);
                fwdFaster = fwdFaster * fwdFaster;
                double fwdSlower = 1 - 5 * keepIn(fwdIncline, 0, 0.2);
                fwdSlower = fwdSlower * fwdSlower;
                speed = speed * (fwdSlower * incDist2DSum + fwdFaster * decDist2DSum + 1 * restDist2D) / fullDist2D;
                flags = this.setSpeed(flags, keepIn(speed, PUSHING_SECTION_SPEED / 2, maxSpeed));
            }

            if (isBackward(flags)) {
                double speedReverse = getReverseSpeed(flags);
                double bwFaster = 1 + 2 * keepIn(fwdIncline, 0, 0.2);
                bwFaster = bwFaster * bwFaster;
                double bwSlower = 1 - 5 * keepIn(fwdDecline, 0, 0.2);
                bwSlower = bwSlower * bwSlower;
                speedReverse = speedReverse * (bwFaster * incDist2DSum + bwSlower * decDist2DSum + 1 * restDist2D) / fullDist2D;
                flags = this.setReverseSpeed(flags, keepIn(speedReverse, PUSHING_SECTION_SPEED / 2, maxSpeed));
            }
        }
        edge.setFlags(flags);
    }

    @Override
    public String toString() {
        return "bike2";
    }
}
