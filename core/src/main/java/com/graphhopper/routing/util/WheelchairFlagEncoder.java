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
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.PMap;
import com.graphhopper.util.PointList;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

import static com.graphhopper.routing.util.PriorityCode.REACH_DEST;
import static com.graphhopper.routing.util.PriorityCode.VERY_NICE;

/**
 * A flag encoder for wheelchairs.
 *
 * @author don-philipe
 */
public class WheelchairFlagEncoder extends FootFlagEncoder {
    private final Set<String> excludeSurfaces = new HashSet<>();
    private final Set<String> excludeSmoothness = new HashSet<>();
    private final Set<String> excludeHighwayTags = new HashSet<>();
    private final int maxInclinePercent = 6;

    /**
     * Should be only instantiated via EncodingManager
     */
    public WheelchairFlagEncoder() {
        this(4, 1);
    }

    public WheelchairFlagEncoder(PMap properties) {
        this(properties.getInt("speed_bits", 4), properties.getDouble("speed_factor", 1));

        blockPrivate(properties.getBool("block_private", true));
        blockFords(properties.getBool("block_fords", false));
        blockBarriersByDefault(properties.getBool("block_barriers", false));
    }

    protected WheelchairFlagEncoder(int speedBits, double speedFactor) {
        super(speedBits, speedFactor);

        restrictions.add("wheelchair");
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

        excludeHighwayTags.add("trunk");
        excludeHighwayTags.add("trunk_link");
        excludeHighwayTags.add("primary");
        excludeHighwayTags.add("primary_link");
        excludeHighwayTags.add("secondary");
        excludeHighwayTags.add("secondary_link");
        excludeHighwayTags.add("tertiary");
        excludeHighwayTags.add("tertiary_link");
        excludeHighwayTags.add("steps");
        excludeHighwayTags.add("track");

        excludeSurfaces.add("cobblestone");
        excludeSurfaces.add("gravel");
        excludeSurfaces.add("sand");

        excludeSmoothness.add("bad");
        excludeSmoothness.add("very_bad");
        excludeSmoothness.add("horrible");
        excludeSmoothness.add("very_horrible");
        excludeSmoothness.add("impassable");

        allowedHighwayTags.addAll(safeHighwayTags);
        allowedHighwayTags.add("cycleway");
        allowedHighwayTags.add("unclassified");
        allowedHighwayTags.add("road");

        maxPossibleSpeed = FERRY_SPEED;
        speedTwoDirections = true;
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

        if (way.hasTag("highway", excludeHighwayTags) && !way.hasTag("sidewalk", sidewalkValues)) {
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
                } catch (NumberFormatException ex) {
                }
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

                    int maxKerbHeightCm = 3;
                    if (kerbHeight > maxKerbHeightCm) {
                        return EncodingManager.Access.CAN_SKIP;
                    }
                } catch (NumberFormatException ex) {
                }
            }
        }

        return super.getAccess(way);
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, EncodingManager.Access access) {
        if (access.canSkip()) {
            return edgeFlags;
        }

        accessEnc.setBool(false, edgeFlags, true);
        accessEnc.setBool(true, edgeFlags, true);
        if (!access.isFerry()) {
            setSpeed(edgeFlags, true, true, MEAN_SPEED);
        } else {
            double ferrySpeed = ferrySpeedCalc.getSpeed(way);
            setSpeed(edgeFlags, true, true, ferrySpeed);
        }

        return edgeFlags;
    }

    /**
     * Calculate slopes from elevation data and set speed according to that. In-/declines between smallInclinePercent
     * and maxInclinePercent will reduce speed to SLOW_SPEED. In-/declines above maxInclinePercent will result in zero
     * speed.
     */
    @Override
    public void applyWayTags(ReaderWay way, EdgeIteratorState edge) {
        PointList pl = edge.fetchWayGeometry(FetchMode.ALL);

        double prevEle = pl.getEle(0);
        double fullDist2D = edge.getDistance();

        if (Double.isInfinite(fullDist2D)) {
            throw new IllegalStateException("Infinite distance should not happen due to #435. way ID=" + way.getId());
        }

        if (fullDist2D < 1) {
            return;
        }

        double eleDelta = pl.getEle(pl.size() - 1) - prevEle;
        double elePercent = eleDelta / fullDist2D * 100;
        int smallInclinePercent = 3;
        if (elePercent > smallInclinePercent && elePercent < maxInclinePercent) {
            setFwdBwdSpeed(edge, SLOW_SPEED, MEAN_SPEED);
        } else if (elePercent < -smallInclinePercent && elePercent > -maxInclinePercent) {
            setFwdBwdSpeed(edge, MEAN_SPEED, SLOW_SPEED);
        } else if (elePercent > maxInclinePercent || elePercent < -maxInclinePercent) {
            edge.set(accessEnc, false);
            edge.setReverse(accessEnc, false);
        }
    }

    /**
     * Sets the given speed values to the given edge depending on the forward and backward accessibility of the edge.
     *
     * @param edge     the edge to set speed for
     * @param fwdSpeed speed value in forward direction
     * @param bwdSpeed speed value in backward direction
     */
    private void setFwdBwdSpeed(EdgeIteratorState edge, int fwdSpeed, int bwdSpeed) {
        if (edge.get(accessEnc))
            edge.set(avgSpeedEnc, fwdSpeed);

        if (edge.getReverse(accessEnc))
            edge.setReverse(avgSpeedEnc, bwdSpeed);
    }

    /**
     * First get priority from {@link FootFlagEncoder#handlePriority(ReaderWay, Integer)} then evaluate wheelchair specific
     * tags.
     *
     * @return a priority for the given way
     */
    @Override
    protected int handlePriority(ReaderWay way, Integer priorityFromRelation) {
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
