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
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.PMap;

import java.util.*;

import static com.graphhopper.routing.util.PriorityCode.*;

/**
 * A flag encoder for wheelchairs.
 * <p>
 *
 * @author don-philipe
 */
public class WheelchairFlagEncoder extends FootFlagEncoder {
    final Set<String> excludeSurfaces = new HashSet<>();
    final Set<String> excludeSmoothness = new HashSet<>();
    final float maxInclinePercent = 6;
    final float maxKerbHeightCm = 3;

    /**
     * Should be only instantiated via EncodingManager
     */
    public WheelchairFlagEncoder() {
        this(4, 1);
    }

    public WheelchairFlagEncoder(PMap properties) {
        this((int) properties.getLong("speedBits", 4),
                properties.getDouble("speedFactor", 1));
        this.properties = properties;
        this.setBlockFords(properties.getBool("block_fords", true));
    }

    public WheelchairFlagEncoder(String propertiesStr) {
        this(new PMap(propertiesStr));
    }

    public WheelchairFlagEncoder(int speedBits, double speedFactor) {
        super(speedBits, speedFactor);
        restrictions.add("wheelchair");

        setBlockByDefault(false);
        absoluteBarriers.add("handrail");
        absoluteBarriers.add("wall");
        absoluteBarriers.add("turnstile");
        potentialBarriers.add("kerb");
        potentialBarriers.add("cattle_grid");
        potentialBarriers.add("motorcycle_barrier");

        safeHighwayTags.add("footway");
        safeHighwayTags.add("pedestrian");
        safeHighwayTags.add("living_street");
        safeHighwayTags.add("residential");
        safeHighwayTags.add("service");
        safeHighwayTags.add("platform");

        avoidHighwayTags.add("trunk");
        avoidHighwayTags.add("trunk_link");
        avoidHighwayTags.add("primary");
        avoidHighwayTags.add("primary_link");
        avoidHighwayTags.add("secondary");
        avoidHighwayTags.add("secondary_link");
        avoidHighwayTags.add("tertiary");
        avoidHighwayTags.add("tertiary_link");
        avoidHighwayTags.add("steps");
        avoidHighwayTags.add("track");

        excludeSurfaces.add("cobblestone");
        excludeSurfaces.add("gravel");
        excludeSurfaces.add("sand");

        excludeSmoothness.add("bad");
        excludeSmoothness.add("very_bad");
        excludeSmoothness.add("horrible");
        excludeSmoothness.add("very_horrible");
        excludeSmoothness.add("impassable");

        allowedHighwayTags.addAll(safeHighwayTags);
        allowedHighwayTags.addAll(avoidHighwayTags);
        allowedHighwayTags.add("cycleway");
        allowedHighwayTags.add("unclassified");
        allowedHighwayTags.add("road");

        maxPossibleSpeed = FERRY_SPEED;
        speedDefault = MEAN_SPEED;
        init();
    }

    @Override
    public int getVersion() {
        return 1;
    }

    /**
     * Avoid some more ways than for pedestrian like hiking trails.
     */
    @Override
    public EncodingManager.Access getAccess(ReaderWay way) {
        if (way.hasTag("wheelchair", intendedValues)) {
            return EncodingManager.Access.WAY;
        }

        if (way.getTag("sac_scale") != null) {
            return EncodingManager.Access.CAN_SKIP;
        }

        if (way.hasTag("highway", avoidHighwayTags) && !way.hasTag("sidewalk", sidewalkValues)) {
            return EncodingManager.Access.CAN_SKIP;
        }

        if (way.hasTag("surface", excludeSurfaces)) {
            if (!way.hasTag("sidewalk", sidewalkValues)) {
                return EncodingManager.Access.CAN_SKIP;
            } else {
                String sidewalk = way.getTag("sidewalk");
                if (way.hasTag("sidewalk:" + sidewalk + ":surface", excludeSurfaces)) {
                    return EncodingManager.Access.CAN_SKIP;
                }
            }
        }

        if (way.hasTag("smoothness", excludeSmoothness)) {
            if (!way.hasTag("sidewalk", sidewalkValues)) {
                return EncodingManager.Access.CAN_SKIP;
            } else {
                String sidewalk = way.getTag("sidewalk");
                if (way.hasTag("sidewalk:" + sidewalk + ":smoothness", excludeSmoothness)) {
                    return EncodingManager.Access.CAN_SKIP;
                }
            }
        }

        if (way.hasTag("incline")) {
            String tagValue = way.getTag("incline");
            if (tagValue.endsWith("%") || tagValue.endsWith("°")) {
                try {
                    double incline = Double.parseDouble(tagValue.substring(0, tagValue.length() - 1));
                    if (tagValue.endsWith("°")) {
                        incline = Math.tan(incline * Math.PI / 180) * 100;
                    }

                    if (-maxInclinePercent > incline || incline > maxInclinePercent) {
                        return EncodingManager.Access.CAN_SKIP;
                    }
                } catch (NumberFormatException ex) { }
            }
        }

        if (way.hasTag("kerb", "raised")) {
            return EncodingManager.Access.CAN_SKIP;
        }

        if (way.hasTag("kerb")) {
            String tagValue = way.getTag("kerb");
            if (tagValue.endsWith("cm") || tagValue.endsWith("mm")) {
                try {
                    float kerbHeight = Float.parseFloat(tagValue.substring(0, tagValue.length() - 2));
                    if (tagValue.endsWith("mm")) {
                        kerbHeight /= 100;
                    }

                    if (kerbHeight > maxKerbHeightCm) {
                        return EncodingManager.Access.CAN_SKIP;
                    }
                } catch (NumberFormatException ex) { }
            }
        }

        return super.getAccess(way);
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, EncodingManager.Access access, long relationFlags) {
        if (access.canSkip()) {
            return edgeFlags;
        }

        if (!access.isFerry()) {
            speedEncoder.setDecimal(false, edgeFlags, MEAN_SPEED);
        } else {
            double ferrySpeed = getFerrySpeed(way);
            setSpeed(false, edgeFlags, ferrySpeed);
        }

        accessEnc.setBool(false, edgeFlags, true);
        accessEnc.setBool(true, edgeFlags, true);

        return edgeFlags;
    }

    /**
     * First get priority from {@link FootFlagEncoder#handlePriority(ReaderWay, int)} then evaluate wheelchair specific
     * tags.
     * @param way
     * @param priorityFromRelation
     * @return a priority for the given way
     */
    @Override
    protected int handlePriority(ReaderWay way, int priorityFromRelation) {
        TreeMap<Double, Integer> weightToPrioMap = new TreeMap<>();

        weightToPrioMap.put(100d, super.handlePriority(way, priorityFromRelation));

        if (way.hasTag("wheelchair", "designated")) {
            weightToPrioMap.put(102d, VERY_NICE.getValue());
        } else if (way.hasTag("wheelchair", "limited")) {
            weightToPrioMap.put(102d, REACH_DEST.getValue());
        }

        return weightToPrioMap.lastEntry().getValue();
    }

    @Override
    public String toString() {
        return "wheelchair";
    }
}
