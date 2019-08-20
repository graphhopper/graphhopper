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
import com.graphhopper.routing.profiles.*;
import com.graphhopper.routing.weighting.GenericWeighting;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.InstructionAnnotation;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Translation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * This encoder tries to store all way information into a 32 or 64bit value. Later extendable to
 * multiple ints or bytes. The assumption is that edge.getFlags is cheap and can be later replaced
 * by e.g. one or more (cheap) calls of edge.getData(index).
 * <p>
 * Currently limited to motor vehicle but later could handle different modes like foot or bike too.
 *
 * @author Peter Karich
 */
public class DataFlagEncoder extends AbstractFlagEncoder {

    private static final Logger LOG = LoggerFactory.getLogger(DataFlagEncoder.class);

    private static final Map<String, Double> DEFAULT_SPEEDS = new LinkedHashMap<String, Double>() {
        {
            put("motorway", 100d);
            put("motorway_link", 70d);
            put("motorroad", 90d);
            put("trunk", 70d);
            put("trunk_link", 65d);
            put("primary", 65d);
            put("primary_link", 60d);
            put("secondary", 60d);
            put("secondary_link", 50d);
            put("tertiary", 50d);
            put("tertiary_link", 40d);
            put("unclassified", 30d);
            put("residential", 30d);
            put("living_street", 5d);
            put("service", 20d);
            put("road", 20d);
            put("forestry", 15d);
            put("track", 15d);
        }
    };

    private EnumEncodedValue<RoadEnvironment> roadEnvironmentEnc;

    public DataFlagEncoder() {
        this(5, 5, 0);
    }

    public DataFlagEncoder(PMap properties) {
        this((int) properties.getLong("speed_bits", 5),
                properties.getDouble("speed_factor", 5),
                properties.getBool("turn_costs", false) ? 1 : 0);
        this.properties = properties;
    }

    public DataFlagEncoder(int speedBits, double speedFactor, int maxTurnCosts) {
        // TODO include turn information
        super(speedBits, speedFactor, maxTurnCosts);

        maxPossibleSpeed = (int) MaxSpeed.UNLIMITED_SIGN_SPEED;
        restrictions.addAll(Arrays.asList("motorcar", "motor_vehicle", "vehicle", "access"));
    }

    @Override
    public void createEncodedValues(List<EncodedValue> registerNewEncodedValue, String prefix, int index) {
        // TODO support different vehicle types, currently just roundabout and fwd&bwd for one vehicle type
        super.createEncodedValues(registerNewEncodedValue, prefix, index);

        for (String key : Arrays.asList(RoadClass.KEY, RoadEnvironment.KEY, RoadAccess.KEY, MaxSpeed.KEY)) {
            if (!encodedValueLookup.hasEncodedValue(key))
                throw new IllegalStateException("To use DataFlagEncoder and the GenericWeighting you need to add " +
                        "the encoded value " + key + " before this '" + toString() + "' flag encoder. Order is important! " +
                        "E.g. use the config: graph.encoded_values: " + key);
        }

        // workaround to init AbstractWeighting.avSpeedEnc variable that GenericWeighting does not need
        speedEncoder = new UnsignedDecimalEncodedValue("fake", 1, 1, false);
        roadEnvironmentEnc = getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment.class);
    }

    protected void flagsDefault(IntsRef edgeFlags, boolean forward, boolean backward) {
        accessEnc.setBool(false, edgeFlags, forward);
        accessEnc.setBool(true, edgeFlags, backward);
    }

    @Override
    public long handleRelationTags(long oldRelationFlags, ReaderRelation relation) {
        return oldRelationFlags;
    }

    @Override
    public EncodingManager.Access getAccess(ReaderWay way) {
        // important to skip unsupported highways, otherwise too many have to be removed after graph creation
        // and node removal is not yet designed for that
        if (getRoadClass(way) == RoadClass.OTHER)
            return EncodingManager.Access.CAN_SKIP;

        return EncodingManager.Access.WAY;
    }

    RoadClass getRoadClass(ReaderWay way) {
        String highwayValue = way.getTag("highway");
        RoadClass rc = RoadClass.find(highwayValue);
        if (way.hasTag("impassable", "yes") || way.hasTag("status", "impassable"))
            return RoadClass.OTHER;

        if (rc == RoadClass.OTHER) {
            if (way.hasTag("route", ferries)) {
                String motorcarTag = way.getTag("motorcar");
                if (motorcarTag == null)
                    motorcarTag = way.getTag("motor_vehicle");

                if (motorcarTag == null && !way.hasTag("foot") && !way.hasTag("bicycle")
                        || "yes".equals(motorcarTag))
                    rc = RoadClass.find(way.getTag("ferry"));
            }
        }
        return rc;
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, EncodingManager.Access access, long relationFlags) {
        if (access.canSkip())
            return edgeFlags;

        try {
            // HIGHWAY
            RoadClass hwValue = getRoadClass(way);
            // exclude any routing like if you have car and need to exclude all rails or ships
            if (hwValue == RoadClass.OTHER)
                return edgeFlags;

            boolean isRoundabout = roundaboutEnc.getBool(false, edgeFlags);
            // ONEWAY (currently car only)
            boolean isOneway = way.hasTag("oneway", oneways)
                    || way.hasTag("vehicle:backward")
                    || way.hasTag("vehicle:forward")
                    || way.hasTag("motor_vehicle:backward")
                    || way.hasTag("motor_vehicle:forward");

            if (isOneway || isRoundabout) {
                boolean isBackward = way.hasTag("oneway", "-1")
                        || way.hasTag("vehicle:forward", "no")
                        || way.hasTag("motor_vehicle:forward", "no");
                if (isBackward)
                    accessEnc.setBool(true, edgeFlags, true);
                else
                    accessEnc.setBool(false, edgeFlags, true);
            } else {
                accessEnc.setBool(false, edgeFlags, true);
                accessEnc.setBool(true, edgeFlags, true);
            }

            return edgeFlags;
        } catch (Exception ex) {
            throw new RuntimeException("Error while parsing way " + way.toString(), ex);
        }
    }

    @Override
    protected void setSpeed(boolean reverse, IntsRef edgeFlags, double speed) {
        throw new RuntimeException("do not call setSpeed");
    }

    @Override
    double getSpeed(boolean reverse, IntsRef flags) {
        throw new UnsupportedOperationException("Calculate speed via more customizable Weighting.calcMillis method");
    }

    @Override
    protected double getMaxSpeed(ReaderWay way) {
        throw new RuntimeException("do not call getMaxSpeed(ReaderWay)");
    }

    @Override
    public double getMaxSpeed() {
        throw new RuntimeException("do not call getMaxSpeed");
    }

    public double getMaxPossibleSpeed() {
        return maxPossibleSpeed;
    }

    @Override
    public boolean supports(Class<?> feature) {
        boolean ret = super.supports(feature);
        if (ret)
            return true;

        return GenericWeighting.class.isAssignableFrom(feature);
    }

    @Override
    public InstructionAnnotation getAnnotation(IntsRef flags, Translation tr) {
        if (roadEnvironmentEnc.getEnum(false, flags) == RoadEnvironment.FORD) {
            return new InstructionAnnotation(1, tr.tr("way_contains_ford"));
        }

        return super.getAnnotation(flags, tr);
    }

    @Override
    public int getVersion() {
        return 4;
    }

    @Override
    public String toString() {
        return "generic";
    }

    /**
     * This method creates a Config map out of the PMap. Later on this conversion should not be
     * necessary when we read JSON.
     */
    public WeightingConfig createWeightingConfig(PMap pMap) {
        HashMap<String, Double> customSpeedMap = new HashMap<>(DEFAULT_SPEEDS.size());
        double[] speedArray = new double[DEFAULT_SPEEDS.size()];
        for (Map.Entry<String, Double> e : DEFAULT_SPEEDS.entrySet()) {
            double val = pMap.getDouble(e.getKey(), e.getValue());
            customSpeedMap.put(e.getKey(), val);
            RoadClass rc = RoadClass.find(e.getKey());
            speedArray[rc.ordinal()] = val;
        }

        // use defaults per road class in the map for average speed estimate
        return new WeightingConfig(getEnumEncodedValue(RoadClass.KEY, RoadClass.class), speedArray);
    }

    public static class WeightingConfig {
        private final double[] speedArray;
        private final EnumEncodedValue<RoadClass> roadClassEnc;

        public WeightingConfig(EnumEncodedValue<RoadClass> roadClassEnc, double[] speedArray) {
            this.roadClassEnc = roadClassEnc;
            this.speedArray = speedArray;
        }

        public double getSpeed(EdgeIteratorState edgeState) {
            RoadClass rc = edgeState.get(roadClassEnc);
            if (rc.ordinal() >= speedArray.length)
                throw new IllegalStateException("RoadClass not found in speed map " + rc);

            return speedArray[rc.ordinal()];
        }

        public double getMaxSpecifiedSpeed() {
            double tmpSpeed = 0;
            for (double speed : speedArray) {
                if (speed > tmpSpeed)
                    tmpSpeed = speed;
            }
            return tmpSpeed;
        }
    }
}
