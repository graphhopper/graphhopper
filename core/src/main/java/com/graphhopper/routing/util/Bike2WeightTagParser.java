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
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.PMap;
import com.graphhopper.util.PointList;

import static com.graphhopper.util.Helper.keepIn;

/**
 * Stores two speed values into an edge to support avoiding too much incline
 *
 * @author Peter Karich
 */
public class Bike2WeightTagParser extends BikeTagParser {

    public Bike2WeightTagParser(EncodedValueLookup lookup, PMap properties) {
        this(
                lookup.getBooleanEncodedValue(VehicleAccess.key(properties.getString("name", "bike2"))),
                lookup.getDecimalEncodedValue(VehicleSpeed.key(properties.getString("name", "bike2"))),
                lookup.getDecimalEncodedValue(VehiclePriority.key(properties.getString("name", "bike2"))),
                lookup.getEnumEncodedValue(BikeNetwork.KEY, RouteNetwork.class),
                lookup.getBooleanEncodedValue(Roundabout.KEY),
                lookup.getEnumEncodedValue(Smoothness.KEY, Smoothness.class),
                lookup.hasEncodedValue(TurnCost.key(properties.getString("name", "bike2"))) ? lookup.getDecimalEncodedValue(TurnCost.key(properties.getString("name", "bike2"))) : null,
                properties
        );
    }

    public Bike2WeightTagParser(BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc,
                                DecimalEncodedValue priorityEnc, EnumEncodedValue<RouteNetwork> bikeRouteEnc,
                                BooleanEncodedValue roundaboutEnc,
                                EnumEncodedValue<Smoothness> smoothnessEnc, DecimalEncodedValue turnCostEnc, PMap properties) {
        super(accessEnc, speedEnc, priorityEnc, bikeRouteEnc, smoothnessEnc, properties.getString("name", "bike2"),
                roundaboutEnc, turnCostEnc);
        blockPrivate(properties.getBool("block_private", true));
        blockFords(properties.getBool("block_fords", false));
    }

    @Override
    public void applyWayTags(ReaderWay way, EdgeIteratorState edge) {
        PointList pl = edge.fetchWayGeometry(FetchMode.ALL);
        if (!pl.is3D())
            throw new IllegalStateException(getName() + " requires elevation data to improve speed calculation based on it. Please enable it in config via e.g. graph.elevation.provider: srtm");

        IntsRef intsRef = edge.getFlags();
        if (way.hasTag("tunnel", "yes") || way.hasTag("bridge", "yes") || way.hasTag("highway", "steps"))
            // do not change speed
            // note: although tunnel can have a difference in elevation it is very unlikely that the elevation data is correct for a tunnel
            return;

        // Decrease the speed for ele increase (incline), and decrease the speed for ele decrease (decline). The speed-decrease
        // has to be bigger (compared to the speed-increase) for the same elevation difference to simulate losing energy and avoiding hills.
        // For the reverse speed this has to be the opposite but again keeping in mind that up+down difference.
        double incEleSum = 0, incDist2DSum = 0, decEleSum = 0, decDist2DSum = 0;
        // double prevLat = pl.getLat(0), prevLon = pl.getLon(0);
        double prevEle = pl.getEle(0);
        double fullDist2D = edge.getDistance();

        // for short edges an incline makes no sense and for 0 distances could lead to NaN values for speed, see #432
        if (fullDist2D < 2)
            return;

        double eleDelta = pl.getEle(pl.size() - 1) - prevEle;
        if (eleDelta > 0.1) {
            incEleSum = eleDelta;
            incDist2DSum = fullDist2D;
        } else if (eleDelta < -0.1) {
            decEleSum = -eleDelta;
            decDist2DSum = fullDist2D;
        }

        // Calculate slop via tan(asin(height/distance)) but for rather smallish angles where we can assume tan a=a and sin a=a.
        // Then calculate a factor which decreases or increases the speed.
        // Do this via a simple quadratic equation where y(0)=1 and y(0.3)=1/4 for incline and y(0.3)=2 for decline
        double fwdIncline = incDist2DSum > 1 ? incEleSum / incDist2DSum : 0;
        double fwdDecline = decDist2DSum > 1 ? decEleSum / decDist2DSum : 0;
        double restDist2D = fullDist2D - incDist2DSum - decDist2DSum;
        double maxSpeed = getHighwaySpeed("cycleway");
        if (accessEnc.getBool(false, intsRef)) {
            // use weighted mean so that longer incline influences speed more than shorter
            double speed = avgSpeedEnc.getDecimal(false, intsRef);
            double fwdFaster = 1 + 2 * keepIn(fwdDecline, 0, 0.2);
            fwdFaster = fwdFaster * fwdFaster;
            double fwdSlower = 1 - 5 * keepIn(fwdIncline, 0, 0.2);
            fwdSlower = fwdSlower * fwdSlower;
            speed = speed * (fwdSlower * incDist2DSum + fwdFaster * decDist2DSum + 1 * restDist2D) / fullDist2D;
            setSpeed(false, intsRef, keepIn(speed, MIN_SPEED, maxSpeed));
        }

        if (accessEnc.getBool(true, intsRef)) {
            double speedReverse = avgSpeedEnc.getDecimal(true, intsRef);
            double bwFaster = 1 + 2 * keepIn(fwdIncline, 0, 0.2);
            bwFaster = bwFaster * bwFaster;
            double bwSlower = 1 - 5 * keepIn(fwdDecline, 0, 0.2);
            bwSlower = bwSlower * bwSlower;
            speedReverse = speedReverse * (bwFaster * incDist2DSum + bwSlower * decDist2DSum + 1 * restDist2D) / fullDist2D;
            setSpeed(true, intsRef, keepIn(speedReverse, MIN_SPEED, maxSpeed));
        }
        edge.setFlags(intsRef);
    }

}
