/*
 *  Licensed to Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  Peter Karich licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the
 *  License at
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

import com.graphhopper.reader.OSMWay;
import com.graphhopper.util.BitUtil;
import com.graphhopper.util.DistanceCalc3D;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PointList;
import static com.graphhopper.util.Helper.*;

/**
 * Stores two speed values into an edge to support avoiding too much incline
 * <p>
 * @author Peter Karich
 */
public class Bike2WeightFlagEncoder extends BikeFlagEncoder
{
    private final DistanceCalc3D distCalc = new DistanceCalc3D();
    private EncodedDoubleValue reverseSpeed;

    @Override
    public int defineWayBits( int index, int shift )
    {
        shift = super.defineWayBits(index, shift);
        reverseSpeed = new EncodedDoubleValue("Speed", shift, speedBits, speedFactor, getHighwaySpeed("cycleway"), 30);
        shift += reverseSpeed.getBits();
        return shift;
    }

    @Override
    public double getReverseSpeed( long flags )
    {
        return reverseSpeed.getDoubleValue(flags);
    }

    @Override
    public long setReverseSpeed( long flags, double speed )
    {
        if (speed < 0)
            throw new IllegalArgumentException("Speed cannot be negative: " + speed + ", flags:" + BitUtil.LITTLE.toBitString(flags));

        if (speed > getMaxSpeed())
            speed = getMaxSpeed();

        return reverseSpeed.setDoubleValue(flags, speed);
    }

    @Override
    public long handleSpeed( OSMWay way, double speed, long encoded )
    {
        // handle oneways
        if ((way.hasTag("oneway", oneways) || way.hasTag("junction", "roundabout"))
                && !way.hasTag("oneway:bicycle", "no")
                && !way.hasTag("cycleway", oppositeLanes))
        {

            if (way.hasTag("oneway", "-1"))
            {
                encoded |= backwardBit;
                encoded = setReverseSpeed(encoded, speed);
            } else
            {
                encoded |= forwardBit;
                encoded = setSpeed(encoded, speed);
            }
        } else
        {
            encoded |= directionBitMask;
            encoded = setSpeed(encoded, speed);
            encoded = setReverseSpeed(encoded, speed);
        }
        return encoded;
    }

    @Override
    public long flagsDefault( boolean forward, boolean backward )
    {
        long flags = super.flagsDefault(forward, backward);
        if (backward)
            return reverseSpeed.setDefaultValue(flags);

        return flags;
    }

    @Override
    public long setProperties( double speed, boolean forward, boolean backward )
    {
        long flags = super.setProperties(speed, forward, backward);
        if (backward)
            return setReverseSpeed(flags, speed);

        return flags;
    }

    @Override
    public long reverseFlags( long flags )
    {
        // swap access
        flags = super.reverseFlags(flags);

        // swap speeds 
        double otherValue = reverseSpeed.getDoubleValue(flags);
        flags = setReverseSpeed(flags, speedEncoder.getDoubleValue(flags));
        return setSpeed(flags, otherValue);
    }

    @Override
    public void applyWayTags( OSMWay way, EdgeIteratorState edge )
    {
        PointList pl = edge.fetchWayGeometry(3);
        if (!pl.is3D())
            throw new IllegalStateException("To support speed calculation based on elevation data it is necessary to enable import of it.");

        long flags = edge.getFlags();

        // Decrease the speed for ele increase (incline), and decrease the speed for ele decrease (decline). The speed-decrease 
        // has to be bigger (compared to the speed-increase) for the same elevation difference to simulate loosing energy and avoiding hills.
        // For the reverse speed this has to be the opposite but again keeping in mind that up+down difference.
        // TODO increase the speed due to a decline only if surface is okay
        if (way.hasTag("highway", "steps"))
        {
            double speed = getHighwaySpeed("steps");
            flags = setReverseSpeed(setSpeed(flags, speed), speed);
        } else
        {
            double incEleSum = 0, incDist2DSum = 0;
            double decEleSum = 0, decDist2DSum = 0;
            double prevLat = pl.getLatitude(0), prevLon = pl.getLongitude(0), prevEle = pl.getElevation(0);
            double fullDist2D = 0;
            for (int i = 1; i < pl.size(); i++)
            {
                double lat = pl.getLatitude(i);
                double lon = pl.getLongitude(i);
                double ele = pl.getElevation(i);
                double eleDelta = ele - prevEle;
                double dist2D = distCalc.calcDist(prevLat, prevLon, lat, lon);
                if (eleDelta > 0)
                {
                    incEleSum += eleDelta;
                    incDist2DSum += dist2D;
                } else
                {
                    decEleSum += -eleDelta;
                    decDist2DSum += dist2D;
                }
                fullDist2D += dist2D;
                prevLat = lat;
                prevLon = lon;
                prevEle = ele;
            }

            // Calculate slop via tan(asin(height/distance)) but for rather smallish angles where we can assume tan a=a and sin a=a.
            // Then calculate a factor which decreases or increases the speed.
            // Do this via a simple quadratic equation where y(0)=1 and y(0.3)=1/4 for incline and y(0.3)=2 for decline        
            double fwdInc = incDist2DSum > 1 ? incEleSum / incDist2DSum : 0;
            double fwdDec = decDist2DSum > 1 ? decEleSum / decDist2DSum : 0;
            double restDist2D = fullDist2D - incDist2DSum - decDist2DSum;
            double maxSpeed = getHighwaySpeed("cycleway");
            if (isForward(flags))
            {
                double speed = getSpeed(flags);
                // for decline use a maximum factor between 1 and 2
                double fwdFaster = keepIn(11.1 * fwdDec * fwdDec + 1, 1, 2);
                // for ascending use a minimum factor of 1/4 and 1
                double fwdSlower = keepIn(-8.3 * fwdInc * fwdInc + 1, .25, 1);
                // use weighted mean so that longer incline infuences speed more            
                speed = speed * (fwdSlower * incDist2DSum + fwdFaster * decDist2DSum + 1 * restDist2D) / fullDist2D;
                flags = this.setSpeed(flags, keepIn(speed, PUSHING_SECTION_SPEED, maxSpeed));
            }

            if (isBackward(flags))
            {
                double speedReverse = getReverseSpeed(flags);
                double bwFaster = keepIn(11.1 * fwdInc * fwdInc + 1, 1, 2);
                double bwSlower = keepIn(-8.3 * fwdDec * fwdDec + 1, 0.25, 1);
                speedReverse = speedReverse * (bwFaster * incDist2DSum + bwSlower * decDist2DSum + 1 * restDist2D) / fullDist2D;
                flags = this.setReverseSpeed(flags, keepIn(speedReverse, PUSHING_SECTION_SPEED, maxSpeed));
            }
        }
        edge.setFlags(flags);
    }

    @Override
    public String toString()
    {
        return "bike2";
    }
}
