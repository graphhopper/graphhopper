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
import com.graphhopper.routing.weighting.PriorityWeighting;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;
import com.graphhopper.util.PointList;

import java.util.TreeMap;

import static com.graphhopper.routing.util.PriorityCode.*;

/**
 * Defines bit layout for hiking
 *
 * @author Peter Karich
 */
public class HikeFlagEncoder extends FootFlagEncoder {
    /**
     * Should be only instantiated via EncodingManager
     */
    public HikeFlagEncoder() {
        this(4, 1);
    }

    public HikeFlagEncoder(PMap properties) {
        this((int) properties.getLong("speed_bits", 4),
                properties.getDouble("speed_factor", 1));
        this.setBlockFords(properties.getBool("block_fords", false));
    }

    public HikeFlagEncoder(String propertiesStr) {
        this(new PMap(propertiesStr));
    }

    public HikeFlagEncoder(int speedBits, double speedFactor) {
        super(speedBits, speedFactor);

        hikingNetworkToCode.put("iwn", BEST.getValue());
        hikingNetworkToCode.put("nwn", BEST.getValue());
        hikingNetworkToCode.put("rwn", VERY_NICE.getValue());
        hikingNetworkToCode.put("lwn", VERY_NICE.getValue());

        init();
    }

    @Override
    public int getVersion() {
        return 3;
    }

    @Override
    public EncodingManager.Access getAccess(ReaderWay way) {
        String highwayValue = way.getTag("highway");
        if (highwayValue == null) {
            EncodingManager.Access acceptPotentially = EncodingManager.Access.CAN_SKIP;

            if (way.hasTag("route", ferries)) {
                String footTag = way.getTag("foot");
                if (footTag == null || "yes".equals(footTag))
                    acceptPotentially = EncodingManager.Access.FERRY;
            }

            // special case not for all acceptedRailways, only platform
            if (way.hasTag("railway", "platform"))
                acceptPotentially = EncodingManager.Access.WAY;

            if (way.hasTag("man_made", "pier"))
                acceptPotentially = EncodingManager.Access.WAY;

            if (!acceptPotentially.canSkip()) {
                if (way.hasTag(restrictions, restrictedValues) && !getConditionalTagInspector().isRestrictedWayConditionallyPermitted(way))
                    return EncodingManager.Access.CAN_SKIP;
                return acceptPotentially;
            }

            return EncodingManager.Access.CAN_SKIP;
        }

        // no need to evaluate ferries or fords - already included here
        if (way.hasTag("foot", intendedValues))
            return EncodingManager.Access.WAY;

        // check access restrictions
        if (way.hasTag(restrictions, restrictedValues) && !getConditionalTagInspector().isRestrictedWayConditionallyPermitted(way))
            return EncodingManager.Access.CAN_SKIP;

        // hiking allows all sac_scale values
        // String sacScale = way.getTag("sac_scale");
        if (way.hasTag("sidewalk", sidewalkValues))
            return EncodingManager.Access.WAY;

        if (!allowedHighwayTags.contains(highwayValue))
            return EncodingManager.Access.CAN_SKIP;

        if (way.hasTag("motorroad", "yes"))
            return EncodingManager.Access.CAN_SKIP;

        // do not get our feet wet, "yes" is already included above
        if (isBlockFords() && (way.hasTag("highway", "ford") || way.hasTag("ford")))
            return EncodingManager.Access.CAN_SKIP;

        if (getConditionalTagInspector().isPermittedWayConditionallyRestricted(way))
            return EncodingManager.Access.CAN_SKIP;
        else
            return EncodingManager.Access.WAY;
    }

    @Override
    void collect(ReaderWay way, TreeMap<Double, Integer> weightToPrioMap) {
        String highway = way.getTag("highway");
        if (way.hasTag("foot", "designated"))
            weightToPrioMap.put(100d, PREFER.getValue());

        double maxSpeed = getMaxSpeed(way);
        if (safeHighwayTags.contains(highway) || maxSpeed > 0 && maxSpeed <= 20) {
            weightToPrioMap.put(40d, PREFER.getValue());
            if (way.hasTag("tunnel", intendedValues)) {
                if (way.hasTag("sidewalk", sidewalksNoValues))
                    weightToPrioMap.put(40d, REACH_DEST.getValue());
                else
                    weightToPrioMap.put(40d, UNCHANGED.getValue());
            }
        } else if (maxSpeed > 50 || avoidHighwayTags.contains(highway)) {
            if (way.hasTag("sidewalk", sidewalksNoValues))
                weightToPrioMap.put(45d, WORST.getValue());
            else
                weightToPrioMap.put(45d, REACH_DEST.getValue());
        }

        if (way.hasTag("bicycle", "official") || way.hasTag("bicycle", "designated"))
            weightToPrioMap.put(44d, AVOID_IF_POSSIBLE.getValue());
    }


    @Override
    public void applyWayTags(ReaderWay way, EdgeIteratorState edge) {
        PointList pl = edge.fetchWayGeometry(3);
        if (!pl.is3D())
            return;

        if (way.hasTag("tunnel", "yes") || way.hasTag("bridge", "yes") || way.hasTag("highway", "steps"))
            // do not change speed
            // note: although tunnel can have a difference in elevation it is unlikely that the elevation data is correct for a tunnel
            return;

        // Decrease the speed for ele increase (incline), and slightly decrease the speed for ele decrease (decline)
        double prevEle = pl.getElevation(0);
        double fullDistance = edge.getDistance();

        // for short edges an incline makes no sense and for 0 distances could lead to NaN values for speed, see #432
        if (fullDistance < 2)
            return;

        double eleDelta = Math.abs(pl.getElevation(pl.size() - 1) - prevEle);
        double slope = eleDelta / fullDistance;

        IntsRef edgeFlags = edge.getFlags();
        if ((accessEnc.getBool(false, edgeFlags) || accessEnc.getBool(true, edgeFlags))
                && slope > 0.005) {

            // see #1679 => v_hor=4.5km/h for horizontal speed; v_vert=2*0.5km/h for vertical speed (assumption: elevation < edge distance/4.5)
            // s_3d/v=h/v_vert + s_2d/v_hor => v = s_3d / (h/v_vert + s_2d/v_hor) = sqrt(s²_2d + h²) / (h/v_vert + s_2d/v_hor)
            // slope=h/s_2d=~h/2_3d              = sqrt(1+slope²)/(slope+1/4.5) km/h
            // maximum slope is 0.37 (Ffordd Pen Llech)
            double newSpeed = Math.sqrt(1 + slope * slope) / (slope + 1 / 5.4);
            edge.set(avgSpeedEnc, Helper.keepIn(newSpeed, 1, 5));
        }
    }

    @Override
    public boolean supports(Class<?> feature) {
        if (super.supports(feature))
            return true;

        return PriorityWeighting.class.isAssignableFrom(feature);
    }

    @Override
    public String toString() {
        return "hike";
    }
}
