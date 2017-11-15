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

import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Lane;
import com.graphhopper.util.PMap;

import java.util.*;


/**
 * Defines bit layout for cars. (speed, access, ferries, ...)
 * <p>
 *
 * @author Peter Karich
 * @author Nop
 */
public class CarFlagEncoder extends AbstractFlagEncoder implements LaneInfoEncoder {
    public static final int K_TURN_LANES = 4;

    protected static final int LANE_MASK = 0B1111;
    protected static final int LANE_MASK_SIZE = 4;
    public static final int NONE_LANE_CODE = 11;
    public static final int RIGHT_LANE_CODE = 12;
    public static final int LEFT_LANE_CODE = 3;
    protected final Map<String, Integer> trackTypeSpeedMap = new HashMap<String, Integer>();
    protected final Map<String, Integer> turnLaneMap = new HashMap<>();
    protected final Set<String> badSurfaceSpeedMap = new HashSet<String>();
    protected EncodedDoubleValue lanesEncoder;

    // This value determines the maximal possible on roads with bad surfaces
    protected int badSurfaceSpeed;

    // This value determines the speed for roads with access=destination
    protected int destinationSpeed;
    /**
     * A map which associates string to speed. Get some impression:
     * http://www.itoworld.com/map/124#fullscreen
     * http://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Maxspeed
     */
    protected final Map<String, Integer> defaultSpeedMap = new HashMap<String, Integer>();

    public CarFlagEncoder() {
        this(5, 5, 0);
    }

    public CarFlagEncoder(PMap properties) {
        this((int) properties.getLong("speed_bits", 5),
                properties.getDouble("speed_factor", 5),
                properties.getBool("turn_costs", false) ? 1 : 0);
        this.properties = properties;
        this.setBlockFords(properties.getBool("block_fords", true));
        this.setBlockByDefault(properties.getBool("block_barriers", true));
    }

    public CarFlagEncoder(String propertiesStr) {
        this(new PMap(propertiesStr));
    }

    public CarFlagEncoder(int speedBits, double speedFactor, int maxTurnCosts) {
        super(speedBits, speedFactor, maxTurnCosts);
        restrictions.addAll(Arrays.asList("motorcar", "motor_vehicle", "vehicle", "access"));
        restrictedValues.add("private");
        restrictedValues.add("agricultural");
        restrictedValues.add("forestry");
        restrictedValues.add("no");
        restrictedValues.add("restricted");
        restrictedValues.add("delivery");
        restrictedValues.add("military");
        restrictedValues.add("emergency");

        intendedValues.add("yes");
        intendedValues.add("permissive");

        potentialBarriers.add("gate");
        potentialBarriers.add("lift_gate");
        potentialBarriers.add("kissing_gate");
        potentialBarriers.add("swing_gate");

        absoluteBarriers.add("bollard");
        absoluteBarriers.add("stile");
        absoluteBarriers.add("turnstile");
        absoluteBarriers.add("cycle_barrier");
        absoluteBarriers.add("motorcycle_barrier");
        absoluteBarriers.add("block");
        absoluteBarriers.add("bus_trap");
        absoluteBarriers.add("sump_buster");

        trackTypeSpeedMap.put("grade1", 20); // paved
        trackTypeSpeedMap.put("grade2", 15); // now unpaved - gravel mixed with ...
        trackTypeSpeedMap.put("grade3", 10); // ... hard and soft materials        

        badSurfaceSpeedMap.add("cobblestone");
        badSurfaceSpeedMap.add("grass_paver");
        badSurfaceSpeedMap.add("gravel");
        badSurfaceSpeedMap.add("sand");
        badSurfaceSpeedMap.add("paving_stones");
        badSurfaceSpeedMap.add("dirt");
        badSurfaceSpeedMap.add("ground");
        badSurfaceSpeedMap.add("grass");
        badSurfaceSpeedMap.add("unpaved");
        badSurfaceSpeedMap.add("compacted");

        // limit speed on bad surfaces to 30 km/h
        badSurfaceSpeed = 30;

        destinationSpeed = 5;

        maxPossibleSpeed = 140;

        // autobahn
        defaultSpeedMap.put("motorway", 100);
        defaultSpeedMap.put("motorway_link", 70);
        defaultSpeedMap.put("motorroad", 90);
        // bundesstraße
        defaultSpeedMap.put("trunk", 70);
        defaultSpeedMap.put("trunk_link", 65);
        // linking bigger town
        defaultSpeedMap.put("primary", 65);
        defaultSpeedMap.put("primary_link", 60);
        // linking towns + villages
        defaultSpeedMap.put("secondary", 60);
        defaultSpeedMap.put("secondary_link", 50);
        // streets without middle line separation
        defaultSpeedMap.put("tertiary", 50);
        defaultSpeedMap.put("tertiary_link", 40);
        defaultSpeedMap.put("unclassified", 30);
        defaultSpeedMap.put("residential", 30);
        // spielstraße
        defaultSpeedMap.put("living_street", 5);
        defaultSpeedMap.put("service", 20);
        // unknown road
        defaultSpeedMap.put("road", 20);
        // forestry stuff
        defaultSpeedMap.put("track", 15);

        turnLaneMap.put("slight_left", 1);
        turnLaneMap.put("sharp_left", 2);
        turnLaneMap.put("left", LEFT_LANE_CODE);
        turnLaneMap.put("merge_to_right", 4);
        turnLaneMap.put("through;left", 5);
        turnLaneMap.put("through;right",6);
        turnLaneMap.put("through", 7);
        turnLaneMap.put("left;right", 8);
        turnLaneMap.put("reverse", 9);
        turnLaneMap.put("merge_to_left", 10);
        turnLaneMap.put("none", NONE_LANE_CODE);
        turnLaneMap.put("right", RIGHT_LANE_CODE);
        turnLaneMap.put("slight_right", 13);
        turnLaneMap.put("sharp_right", 14);
        turnLaneMap.put("left;right;through", 15);

        init();
    }

    @Override
    public int getVersion() {
        return 1;
    }

    /**
     * Define the place of the speedBits in the edge flags for car.
     */
    @Override
    public int defineWayBits(int index, int shift) {
        // first two bits are reserved for route handling in superclass
        shift = super.defineWayBits(index, shift);
        speedEncoder = new EncodedDoubleValue("Speed", shift, speedBits, speedFactor, defaultSpeedMap.get("secondary"),
                maxPossibleSpeed);
        int laneBits = 0;
        if (isLaneInfoEnabled()) {
            lanesEncoder = new EncodedDoubleValue("Lanes", shift, LANE_MASK_SIZE * 7, 1, 0,
                    (int) Math.pow(2, LANE_MASK_SIZE * 7) - 1, true);
            laneBits = lanesEncoder.getBits();
        }
        return shift + speedEncoder.getBits() + laneBits;
    }

    protected double getSpeed(ReaderWay way) {
        String highwayValue = way.getTag("highway");
        if (!Helper.isEmpty(highwayValue) && way.hasTag("motorroad", "yes")
                && highwayValue != "motorway" && highwayValue != "motorway_link") {
            highwayValue = "motorroad";
        }
        Integer speed = defaultSpeedMap.get(highwayValue);
        if (speed == null)
            throw new IllegalStateException(toString() + ", no speed found for: " + highwayValue + ", tags: " + way);

        if (highwayValue.equals("track")) {
            String tt = way.getTag("tracktype");
            if (!Helper.isEmpty(tt)) {
                Integer tInt = trackTypeSpeedMap.get(tt);
                if (tInt != null)
                    speed = tInt;
            }
        }

        return speed;
    }

    @Override
    public long acceptWay(ReaderWay way) {
        // TODO: Ferries have conditionals, like opening hours or are closed during some time in the year
        String highwayValue = way.getTag("highway");
        String firstValue = way.getFirstPriorityTag(restrictions);
        if (highwayValue == null) {
            if (way.hasTag("route", ferries)) {
                if (restrictedValues.contains(firstValue))
                    return 0;
                if (intendedValues.contains(firstValue) ||
                        // implied default is allowed only if foot and bicycle is not specified:
                        firstValue.isEmpty() && !way.hasTag("foot") && !way.hasTag("bicycle"))
                    return acceptBit | ferryBit;
            }
            return 0;
        }

        if ("track".equals(highwayValue)) {
            String tt = way.getTag("tracktype");
            if (tt != null && !tt.equals("grade1") && !tt.equals("grade2") && !tt.equals("grade3"))
                return 0;
        }

        if (!defaultSpeedMap.containsKey(highwayValue))
            return 0;

        if (way.hasTag("impassable", "yes") || way.hasTag("status", "impassable"))
            return 0;

        // multiple restrictions needs special handling compared to foot and bike, see also motorcycle
        if (!firstValue.isEmpty()) {
            if (restrictedValues.contains(firstValue) && !getConditionalTagInspector().isRestrictedWayConditionallyPermitted(way))
                return 0;
            if (intendedValues.contains(firstValue))
                return acceptBit;
        }

        // do not drive street cars into fords
        if (isBlockFords() && ("ford".equals(highwayValue) || way.hasTag("ford")))
            return 0;

        if (getConditionalTagInspector().isPermittedWayConditionallyRestricted(way))
            return 0;
        else
            return acceptBit;
    }

    @Override
    public long handleRelationTags(ReaderRelation relation, long oldRelationFlags) {
        return oldRelationFlags;
    }

    @Override
    public long handleWayTags(ReaderWay way, long allowed, long relationFlags) {
        if (!isAccept(allowed))
            return 0;

        long flags = 0;
        if (!isFerry(allowed)) {
            // get assumed speed from highway type
            double speed = getSpeed(way);
            speed = applyMaxSpeed(way, speed);

            speed = applyBadSurfaceSpeed(way, speed);

            flags = setSpeed(flags, speed);

            boolean isRoundabout = way.hasTag("junction", "roundabout") || way.hasTag("junction", "circular");
            if (isRoundabout)
                flags = setBool(flags, K_ROUNDABOUT, true);

            if (isOneway(way) || isRoundabout) {
                if (isBackwardOneway(way))
                    flags |= backwardBit;

                if (isForwardOneway(way))
                    flags |= forwardBit;
            } else
                flags |= directionBitMask;

        } else {
            double ferrySpeed = getFerrySpeed(way);
            flags = setSpeed(flags, ferrySpeed);
            flags |= directionBitMask;
        }

        for (String restriction : restrictions) {
            if (way.hasTag(restriction, "destination")) {
                // This is problematic as Speed != Time
                flags = setSpeed(flags, destinationSpeed);
            }
        }

        if (isLaneInfoEnabled()) {
            long laneFlags = encodeTurnLanes(way);
            flags = lanesEncoder.setDoubleValue(flags, laneFlags);
        }

        return flags;
    }

    @Override
    public double getDouble(long flags, int key) {
        if (key == K_TURN_LANES) {
            return lanesEncoder.getDoubleValue(flags);
        }
        return super.getDouble(flags, key);
    }

    /**
     * make sure that isOneway is called before
     */
    protected boolean isBackwardOneway(ReaderWay way) {
        return way.hasTag("oneway", "-1")
                || way.hasTag("vehicle:forward", "no")
                || way.hasTag("motor_vehicle:forward", "no");
    }

    /**
     * make sure that isOneway is called before
     */
    protected boolean isForwardOneway(ReaderWay way) {
        return !way.hasTag("oneway", "-1")
                && !way.hasTag("vehicle:forward", "no")
                && !way.hasTag("motor_vehicle:forward", "no");
    }

    protected boolean isOneway(ReaderWay way) {
        return way.hasTag("oneway", oneways)
                || way.hasTag("vehicle:backward")
                || way.hasTag("vehicle:forward")
                || way.hasTag("motor_vehicle:backward")
                || way.hasTag("motor_vehicle:forward");
    }

    public String getWayInfo(ReaderWay way) {
        String str = "";
        String highwayValue = way.getTag("highway");
        // for now only motorway links
        if ("motorway_link".equals(highwayValue)) {
            String destination = way.getTag("destination");
            if (!Helper.isEmpty(destination)) {
                int counter = 0;
                for (String d : destination.split(";")) {
                    if (d.trim().isEmpty())
                        continue;

                    if (counter > 0)
                        str += ", ";

                    str += d.trim();
                    counter++;
                }
            }
        }
        if (str.isEmpty())
            return str;
        // I18N
        if (str.contains(","))
            return "destinations: " + str;
        else
            return "destination: " + str;
    }

    /**
     * @param way   needed to retrieve tags
     * @param speed speed guessed e.g. from the road type or other tags
     * @return The assumed speed
     */
    protected double applyBadSurfaceSpeed(ReaderWay way, double speed) {
        // limit speed if bad surface
        if (badSurfaceSpeed > 0 && speed > badSurfaceSpeed && way.hasTag("surface", badSurfaceSpeedMap))
            speed = badSurfaceSpeed;
        return speed;
    }

    @Override
    public String toString() {
        return "car";
    }

    @Override
    public long encodeTurnLanes(ReaderWay readerWay) {
        String tag = readerWay.getTag("turn:lanes");
        if (tag != null) {
            Integer encoded = 0;
            String[] laneTags = tag.split("\\|", -1);
            Collections.reverse(Arrays.asList(laneTags));
            for (int i = 0; i < laneTags.length; i++) {
                String laneTag = laneTags[i];
                Integer turnCode = turnLaneMap.get(laneTag);
                if (laneTag.contains(";")) {
                    turnCode = encodeTurnLanesWithMultipleDirections(laneTag);
                }
                turnCode = turnCode == null ? NONE_LANE_CODE : turnCode;
                encoded = encoded + (turnCode << (LANE_MASK_SIZE * i));
                if (encoded < 0) {
                    return NONE_LANE_CODE;
                }
            }
            return Long.valueOf(encoded);
        }
        return NONE_LANE_CODE;
    }

    private Integer encodeTurnLanesWithMultipleDirections(String laneTag) {
        Integer turnCode;
        boolean right = false;
        boolean through = false;
        boolean left = false;
        String[] singleDirections = laneTag.split(";");
        for (String direction : singleDirections) {
            Integer code = turnLaneMap.get(direction);
            if (code != null) {
                left = left || code <= LEFT_LANE_CODE;
                right = right || code >= RIGHT_LANE_CODE;
                through = through || code == NONE_LANE_CODE || code == 7;
            }
        }
        if (left && !right && through) {
            turnCode = turnLaneMap.get("through;left");
        } else if (!left && right && through) {
            turnCode = turnLaneMap.get("through;right");
        } else if (left && right && !through) {
            turnCode = turnLaneMap.get("left;right");
        } else if (left && right && through) {
            turnCode = turnLaneMap.get("left;right;through");
        } else if (right) {
            turnCode = turnLaneMap.get("right");
        } else if (left) {
            turnCode = turnLaneMap.get("left");
        } else {
            turnCode = NONE_LANE_CODE;
        }
        return turnCode;
    }

    @Override
    public String decodeTurnLanes(long flags) {
        int mask = LANE_MASK;
        int code = (int) flags;
        int lane;
        String lanes = "";
        while ((lane = (code & mask)) != 0) {
            String turnLaneString = getTurnLaneString(lane);
            String appendedLanes = "|" + lanes;
            lanes = turnLaneString + (lanes.isEmpty() ? "" : appendedLanes);
            mask = mask << LANE_MASK_SIZE;
        }
        return lanes;
    }

    @Override
    public List<Lane> decodeTurnLanesToList(long flags) {
        int mask = LANE_MASK;
        int code = (int) flags;
        int lane;
        int shifts = 0;
        List<Lane> lanes = new ArrayList<>();
        while ((lane = (code & mask)) != 0) {
            lane = lane >> LANE_MASK_SIZE * shifts;
            String turnLaneString = getTurnLaneString(lane);
            lanes.add(new Lane(turnLaneString, lane));
            mask = mask << LANE_MASK_SIZE;
            shifts++;
        }
        Collections.reverse(lanes);
        return lanes;
    }

    @Override
    public boolean isLaneInfoEnabled() {
        if (properties != null) {
            return properties.getBool("lane_info", false);
        }
        return false;
    }

    private String getTurnLaneString(int lane) {
        for (Map.Entry<String, Integer> entry : turnLaneMap.entrySet()) {
            if (lane == entry.getValue()) {
                return entry.getKey();
            }
        }
        return "none";
    }
}
