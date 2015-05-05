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

import static com.graphhopper.util.Helper.keepIn;

import com.graphhopper.reader.OSMWay;
import com.graphhopper.reader.Way;
import com.graphhopper.util.BitUtil;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PointList;

/**
 * Stores two speed values into an edge to support avoiding too much incline
 * <p>
 * @author Peter Karich
 */
public class Bike2WeightFlagEncoder extends BikeFlagEncoder
{
    private EncodedDoubleValue reverseSpeed;

    public Bike2WeightFlagEncoder()
    {
        super();
    }

    public Bike2WeightFlagEncoder( String propertiesStr )
    {
        super(propertiesStr);
    }

    public Bike2WeightFlagEncoder( int speedBits, double speedFactor, int maxTurnCosts )
    {
        super(speedBits, speedFactor, maxTurnCosts);
    }

    @Override
    public int defineWayBits( int index, int shift )
    {
        shift = super.defineWayBits(index, shift);
        reverseSpeed = new EncodedDoubleValue("Reverse Speed", shift, speedBits, speedFactor, 
                                               getHighwaySpeed("cycleway"), maxPossibleSpeed);
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
    public long handleSpeed( Way way, double speed, long encoded )
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
    public void applyWayTags( Way way, EdgeIteratorState edge )
    {
        PointList pl = edge.fetchWayGeometry(3);
        if (!pl.is3D())
            throw new IllegalStateException("To support speed calculation based on elevation data it is necessary to enable import of it.");

        long flags = edge.getFlags();

        if (way.hasTag("tunnel", "yes") || way.hasTag("bridge", "yes") || way.hasTag("highway", "steps"))
        {
            // do not change speed
            // note: although tunnel can have a difference in elevation it is very unlikely that the elevation data is correct for a tunnel
        } else
        {
            // Decrease the speed for ele increase (incline), and decrease the speed for ele decrease (decline). The speed-decrease 
            // has to be bigger (compared to the speed-increase) for the same elevation difference to simulate loosing energy and avoiding hills.
            // For the reverse speed this has to be the opposite but again keeping in mind that up+down difference.
            double incEleSum = 0, incDist2DSum = 0;
            double decEleSum = 0, decDist2DSum = 0;
            // double prevLat = pl.getLatitude(0), prevLon = pl.getLongitude(0);
            double prevEle = pl.getElevation(0);
            double fullDist2D = 0;

            fullDist2D = edge.getDistance();
            double eleDelta = pl.getElevation(pl.size() - 1) - prevEle;
            if (eleDelta > 0.1)
            {
                incEleSum = eleDelta;
                incDist2DSum = fullDist2D;
            } else if (eleDelta < -0.1)
            {
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
            if (isForward(flags))
            {
                // use weighted mean so that longer incline infuences speed more than shorter
                double speed = getSpeed(flags);
                double fwdFaster = 1 + 2 * keepIn(fwdDecline, 0, 0.2);
                fwdFaster = fwdFaster * fwdFaster;
                double fwdSlower = 1 - 5 * keepIn(fwdIncline, 0, 0.2);
                fwdSlower = fwdSlower * fwdSlower;
                speed = speed * (fwdSlower * incDist2DSum + fwdFaster * decDist2DSum + 1 * restDist2D) / fullDist2D;
                flags = this.setSpeed(flags, keepIn(speed, PUSHING_SECTION_SPEED / 2, maxSpeed));
            }

            if (isBackward(flags))
            {
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
    public String toString()
    {
        return "bike2";
    }
}
