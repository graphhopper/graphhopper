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
import com.graphhopper.routing.profiles.tagparsers.TagParser;
import com.graphhopper.routing.util.spatialrules.SpatialRule;
import com.graphhopper.routing.util.spatialrules.SpatialRuleLookup;
import com.graphhopper.routing.util.spatialrules.TransportationMode;
import com.graphhopper.routing.weighting.GenericWeighting;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.InstructionAnnotation;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Translation;
import com.graphhopper.util.shapes.GHPoint;

import java.util.*;
import java.util.Map.Entry;

import static com.graphhopper.routing.profiles.TagParserFactory.*;

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

    private static final Map<String, Double> DEFAULT_SPEEDS = TagParserFactory.Car.createSpeedMap();

    private IntEncodedValue accessClassEnc;
    private Map<String, Integer> accessClassMap = new HashMap<>();

    // TODO NOW move these EVs into the AbstractFlagEncoder?
    // highway and certain tags like ferry and shuttle_train which can be used here (no logical overlap)
    private final List<String> roadClasses = new ArrayList<>(Arrays.asList(
                /* reserve index=0 for unset roads (not accessible) */
            "_default",
            "motorway", "motorway_link", "motorroad", "trunk", "trunk_link",
            "primary", "primary_link", "secondary", "secondary_link", "tertiary", "tertiary_link",
            "unclassified", "residential", "living_street", "service", "road", "track",
            "forestry", "cycleway", "steps", "path", "footway", "pedestrian",
            "ferry", "shuttle_train"));
    private StringEncodedValue roadClassEnc;

    private int roadEnvTunnelValue;
    private int roadEnvBridgeValue;
    private int roadEnvFordValue;
    private StringEncodedValue roadEnvEnc;

    private boolean storeHeight = false;
    private boolean storeWeight = false;
    private boolean storeWidth = false;
    private SpatialRuleLookup spatialRuleLookup = SpatialRuleLookup.EMPTY;

    public DataFlagEncoder() {
        this(5, 5, 0);
    }

    public DataFlagEncoder(PMap properties) {
        this(properties.getInt("speed_bits", 5),
                properties.getDouble("speed_factor", 5),
                properties.getBool("turn_costs", false) ? 1 : 0);
        this.setStoreHeight(properties.getBool("store_height", false));
        this.setStoreWeight(properties.getBool("store_weight", false));
        this.setStoreWidth(properties.getBool("store_width", false));
    }

    public DataFlagEncoder(int speedBits, double speedFactor, int maxTurnCosts) {
        // TODO include turn information
        super(speedBits, speedFactor, maxTurnCosts);

        maxPossibleSpeed = 140;
        restrictions.addAll(Arrays.asList("motorcar", "motor_vehicle", "vehicle", "access"));
    }

    public Map<String, TagParser> createTagParsers(final String prefix) {
        final ReaderWayFilter osmWayFilter = new ReaderWayFilter() {
            @Override
            public boolean accept(ReaderWay way) {
                return DEFAULT_SPEEDS.containsKey(way.getTag("highway"));
            }
        };

        Map<String, TagParser> map = new HashMap<>();

        map.put("roundabout", null);
        map.put("road_class", null);
        map.put("road_environment", null);
        map.put("surface", null);

        // ugly: misusing average speed to store maximum values otherwise AbstractFlagEncoder would not be able to init averageSpeedEnc
        averageSpeedEnc = new DecimalEncodedValue(prefix + "average_speed", speedBits, 0, speedFactor, true);
        map.put(averageSpeedEnc.getName(), new TagParser() {
            @Override
            public String toString() {
                return getName();
            }

            @Override
            public String getName() {
                return averageSpeedEnc.getName();
            }

            @Override
            public EncodedValue getEncodedValue() {
                return averageSpeedEnc;
            }

            @Override
            public ReaderWayFilter getReadWayFilter() {
                return osmWayFilter;
            }

            @Override
            public void parse(IntsRef ints, ReaderWay way) {
                double maxSpeed = parseSpeed(way.getTag("maxspeed"));
                if (maxSpeed < 0) {
                    // TODO What if no maxspeed is set, but only forward and backward, and both are higher than the usually allowed?
                    maxSpeed = getSpatialRule(way).getMaxSpeed(way.getTag("highway", ""), maxSpeed);
                }
                double fwdSpeed = parseSpeed(way.getTag("maxspeed:forward"));
                if (fwdSpeed < 0 && maxSpeed > 0)
                    fwdSpeed = maxSpeed;
                if (fwdSpeed > getMaxPossibleSpeed())
                    fwdSpeed = getMaxPossibleSpeed();

                double bwdSpeed = parseSpeed(way.getTag("maxspeed:backward"));
                if (bwdSpeed < 0 && maxSpeed > 0)
                    bwdSpeed = maxSpeed;
                if (bwdSpeed > getMaxPossibleSpeed())
                    bwdSpeed = getMaxPossibleSpeed();

                // 0 is reserved for default i.e. no maxspeed sign (does not imply no speed limit)
                // TODO and 140 should be used for "none" speed limit on German Autobahn
                if (fwdSpeed > 0)
                    averageSpeedEnc.setDecimal(false, ints, fwdSpeed);

                // TODO NOW
                if (bwdSpeed > 0)
                    averageSpeedEnc.setDecimal(true, ints, bwdSpeed);
            }
        });

        map.put(prefix + "access", TagParserFactory.Car.createAccess(new BooleanEncodedValue(prefix + "access", true),
                osmWayFilter));

        // value range: [3.0m, 5.4m]
        if (isStoreHeight())
            map.put(MAX_HEIGHT, TagParserFactory.createMaxHeight(new DecimalEncodedValue(MAX_HEIGHT, 7, 0, 0.1, false),
                    osmWayFilter));
        // value range: [1.0t, 59.5t]
        if (isStoreWeight())
            map.put(MAX_WEIGHT, TagParserFactory.createMaxWeight(new DecimalEncodedValue(MAX_WEIGHT, 10, 0, 0.1, false),
                    osmWayFilter));
        // value range: [2.5m, 3.5m]
        if (isStoreWidth())
            map.put(MAX_WIDTH, TagParserFactory.createMaxWidth(new DecimalEncodedValue(MAX_WIDTH, 6, 0, 0.1, false),
                    osmWayFilter));

        // Ordered in increasingly restrictive order
        // Note: if you update this list you have to update the method getAccessTagAsInt too
        accessClassEnc = new IntEncodedValue("access_class", 2, 0, false);
        map.put("access_class", new TagParser() {
            @Override
            public String toString() {
                return getName();
            }

            @Override
            public String getName() {
                return "access_class";
            }

            @Override
            public EncodedValue getEncodedValue() {
                return accessClassEnc;
            }

            @Override
            public ReaderWayFilter getReadWayFilter() {
                return TagParserFactory.ACCEPT_IF_HIGHWAY;
            }

            @Override
            public void parse(IntsRef ints, ReaderWay way) {
                int accessValue = 0;
                Integer tmpAccessValue;
                for (String restriction : restrictions) {
                    tmpAccessValue = accessClassMap.get(way.getTag(restriction, "yes"));
                    if (tmpAccessValue != null && tmpAccessValue > accessValue) {
                        accessValue = tmpAccessValue;
                    }
                }

                if (accessValue == 0) {
                    SpatialRule.Access ac = getSpatialRule(way).getAccess(way.getTag("highway", ""), TransportationMode.MOTOR_VEHICLE, SpatialRule.Access.YES);
                    switch (ac) {
                        case YES:
                            accessValue = accessClassMap.get("yes");
                            break;
                        case CONDITIONAL:
                            accessValue = accessClassMap.get("destination");
                            break;
                        case NO:
                            accessValue = accessClassMap.get("no");
                    }
                }

                accessClassEnc.setInt(false, ints, accessValue);
            }
        });

        // Ordered in increasingly restrictive order
        // Note: if you update this list you have to update the method getAccessValue too
        List<String> accessList = Arrays.asList(
                //"designated", "permissive", "customers", "delivery",
                "yes", "destination",
                //"private", <= this currently conflicts with SpatialRule.Access, as this only allows yes, conditional, and no
                "no"
        );

        int counter = 0;
        for (String s : accessList) {
            accessClassMap.put(s, counter++);
        }

        int tmpMax = spatialRuleLookup.size() - 1;
        int bits = 32 - Integer.numberOfLeadingZeros(tmpMax);
        if (bits > 0)
            map.put(TagParserFactory.SPATIAL_RULE_ID, TagParserFactory.createSpatialRuleId(spatialRuleLookup,
                    new IntEncodedValue(TagParserFactory.SPATIAL_RULE_ID, bits, 0, false)));
        return map;
    }

    @Override
    public void initEncodedValues(String prefix, int index) {
        super.initEncodedValues(prefix, index);

        roadEnvEnc = getStringEncodedValue(TagParserFactory.ROAD_ENVIRONMENT);
        // We need transport mode additionally to highway e.g. a secondary highway can be a tunnel.
        roadEnvTunnelValue = roadEnvEnc.indexOf("tunnel");
        roadEnvBridgeValue = roadEnvEnc.indexOf("bridge");
        roadEnvFordValue = roadEnvEnc.indexOf("ford");

        roadClassEnc = getStringEncodedValue(TagParserFactory.ROAD_CLASS);
    }

    @Override
    public long handleRelationTags(ReaderRelation relation, long oldRelationFlags) {
        return 0;
    }

    @Override
    public EncodingManager.Access getAccess(ReaderWay way) {
        // important to skip unsupported highways, otherwise too many have to be removed after graph creation
        // and node removal is not yet designed for that
        if (way.hasTag("impassable", "yes") || way.hasTag("status", "impassable"))
            return EncodingManager.Access.CAN_SKIP;

        String highwayValue = way.getTag("highway");
        if (!roadClasses.contains(highwayValue)) {
            if (way.hasTag("route", ferries)) {
                String motorcarTag = way.getTag("motorcar");
                if (motorcarTag == null)
                    motorcarTag = way.getTag("motor_vehicle");

                if (motorcarTag == null && !way.hasTag("foot") && !way.hasTag("bicycle")
                        || "yes".equals(motorcarTag))
                    return EncodingManager.Access.FERRY;
            } else
                return EncodingManager.Access.CAN_SKIP;
        }
        return EncodingManager.Access.WAY;
    }

    public SpatialRule.Access getAccess(EdgeIteratorState edge) {
        int accessValue = edge.get(accessClassEnc);
        switch (accessValue) {
            case 0:
                return SpatialRule.Access.YES;
            // NOT_ACCESSIBLE_KEY
            case 3:
                return SpatialRule.Access.NO;
            default:
                return SpatialRule.Access.CONDITIONAL;
        }
    }

    @Override
    public IntsRef handleWayTags(IntsRef ints, ReaderWay way, EncodingManager.Access allowed, long relationFlags) {
        // TODO NOW: with the TagParser we should be able to avoid this method entirely
        if (allowed.canSkip())
            return ints;

        if (allowed.isFerry())
            roadClassEnc.setString(false, ints, "ferry");
        return ints;
    }

    private SpatialRule getSpatialRule(ReaderWay way) {
        GHPoint estmCentre = way.getTag("estimated_center", null);
        if (estmCentre != null) {
            return spatialRuleLookup.lookupRule(estmCentre);
        }
        return SpatialRule.EMPTY;
    }

    double[] getHighwaySpeedMap(Map<String, Double> map) {
        if (map == null)
            throw new IllegalArgumentException("Map cannot be null when calling getHighwaySpeedMap");

        double[] res = new double[roadClassEnc.getMapSize()];
        for (Entry<String, Double> e : map.entrySet()) {
            int index = roadClassEnc.indexOf(e.getKey());
            if (e.getValue() < 0)
                throw new IllegalArgumentException("Negative speed " + e.getValue() + " not allowed. highway=" + e.getKey());

            res[index] = e.getValue();
        }
        return res;
    }

    public boolean isTunnel(IntsRef ints) {
        return roadEnvEnc.getInt(false, ints) == roadEnvTunnelValue;
    }

    public boolean isBridge(IntsRef ints) {
        return roadEnvEnc.getInt(false, ints) == roadEnvBridgeValue;
    }

    public boolean isFord(IntsRef ints) {
        return roadEnvEnc.getInt(false, ints) == roadEnvFordValue;
    }

//    public double[] getTransportModeMap(Map<String, Double> map) {
//        double[] res = new double[transportModeMap.size()];
//        for (Entry<String, Double> e : map.entrySet()) {
//            Integer integ = transportModeMap.get(e.getKey());
//            if (integ == null)
//                throw new IllegalArgumentException("Graph not prepared for transport_mode=" + e.getKey());
//
//            if (e.getValue() < 0)
//                throw new IllegalArgumentException("Negative speed " + e.getValue() + " not allowed. transport_mode=" + e.getKey());
//
//            res[integ] = e.getValue();
//        }
//        return res;
//    }

//    public int getAccessType(String accessStr) {
//        // access, motor_vehicle, bike, foot, hgv, bus
//        return 0;
//    }

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

    public DataFlagEncoder setStoreHeight(boolean storeHeight) {
        this.storeHeight = storeHeight;
        return this;
    }

    public boolean isStoreHeight() {
        return storeHeight;
    }

    public DataFlagEncoder setStoreWeight(boolean storeWeight) {
        this.storeWeight = storeWeight;
        return this;
    }

    public boolean isStoreWeight() {
        return storeWeight;
    }

    public DataFlagEncoder setStoreWidth(boolean storeWidth) {
        this.storeWidth = storeWidth;
        return this;
    }

    public boolean isStoreWidth() {
        return storeWidth;
    }


    public DataFlagEncoder setSpatialRuleLookup(SpatialRuleLookup spatialRuleLookup) {
        this.spatialRuleLookup = spatialRuleLookup;
        return this;
    }

    // TODO how to replace this?
    public InstructionAnnotation getAnnotation(IntsRef ints, Translation tr) {
        if (isFord(ints)) {
            return new InstructionAnnotation(1, tr.tr("way_contains_ford"));
        }

        return super.getAnnotation(ints, tr);
    }


    @Override
    protected String getPropertiesString() {
        return super.getPropertiesString() +
                "|store_height=" + storeHeight +
                "|store_weight=" + storeWeight +
                "|store_width=" + storeWidth;
    }

    @Override
    public int getVersion() {
        return 3;
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
        HashMap<String, Double> map = new HashMap<>(DEFAULT_SPEEDS.size());
        for (Entry<String, Double> e : DEFAULT_SPEEDS.entrySet()) {
            map.put(e.getKey(), pMap.getDouble(e.getKey(), e.getValue()));
        }

        return new WeightingConfig(getHighwaySpeedMap(map));
    }

    public class WeightingConfig {
        private final double[] estimatedAverageSpeedArray;

        public WeightingConfig(double[] estimatedAverageSpeedArray) {
            this.estimatedAverageSpeedArray = estimatedAverageSpeedArray;
        }

        public double getSpeed(EdgeIteratorState edgeState) {
            int highwayKey = edgeState.get((IntEncodedValue) roadClassEnc);
            // ensure before (in createResult) that all highways that were specified in the request are known
            double speed = estimatedAverageSpeedArray[highwayKey];
            if (speed < 0)
                throw new IllegalStateException("speed was negative? " + edgeState.getEdge()
                        + ", highway:" + highwayKey);
            return speed;
        }

        public double getMaxSpecifiedSpeed() {
            double tmpSpeed = 0;
            for (double speed : estimatedAverageSpeedArray) {
                if (speed > tmpSpeed)
                    tmpSpeed = speed;
            }
            return tmpSpeed;
        }
    }
}
