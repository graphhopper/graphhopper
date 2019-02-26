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
import com.graphhopper.routing.util.spatialrules.SpatialRule;
import com.graphhopper.routing.util.spatialrules.SpatialRuleLookup;
import com.graphhopper.routing.weighting.GenericWeighting;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.InstructionAnnotation;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Translation;
import com.graphhopper.util.shapes.GHPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.graphhopper.util.Helper.isEmpty;
import static com.graphhopper.util.Helper.toLowerCase;

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

    private DecimalEncodedValue heightEncoder;
    private DecimalEncodedValue weightEncoder;
    private DecimalEncodedValue widthEncoder;
    private boolean storeHeight = false;
    private boolean storeWeight = false;
    private boolean storeWidth = false;
    private IntEncodedValue spatialEncoder;
    private ObjectEncodedValue roadEnvironmentEnc;
    private SpatialRuleLookup spatialRuleLookup = SpatialRuleLookup.EMPTY;

    public DataFlagEncoder() {
        this(5, 5, 0);
    }

    public DataFlagEncoder(PMap properties) {
        this((int) properties.getLong("speed_bits", 5),
                properties.getDouble("speed_factor", 5),
                properties.getBool("turn_costs", false) ? 1 : 0);
        this.properties = properties;
        this.setStoreHeight(properties.getBool("store_height", false));
        this.setStoreWeight(properties.getBool("store_weight", false));
        this.setStoreWidth(properties.getBool("store_width", false));
    }

    public DataFlagEncoder(int speedBits, double speedFactor, int maxTurnCosts) {
        // TODO include turn information
        super(speedBits, speedFactor, maxTurnCosts);

        maxPossibleSpeed = (int) CarMaxSpeed.UNLIMITED_SIGN;
        restrictions.addAll(Arrays.asList("motorcar", "motor_vehicle", "vehicle", "access"));
    }

    @Override
    public void createEncodedValues(List<EncodedValue> registerNewEncodedValue, String prefix, int index) {
        // TODO support different vehicle types, currently just roundabout and fwd&bwd for one vehicle type
        super.createEncodedValues(registerNewEncodedValue, prefix, index);

        /* Value range: [3.0m, 5.4m] */
        if (isStoreHeight())
            registerNewEncodedValue.add(heightEncoder = new FactorizedDecimalEncodedValue(prefix + "height", 7, 0.1, false));

        /* Value range: [1.0t, 59.5t] */
        if (isStoreWeight())
            registerNewEncodedValue.add(weightEncoder = new FactorizedDecimalEncodedValue(prefix + "weight", 10, 0.1, false));

        /* Value range: [2.5m, 3.5m] */
        if (isStoreWidth())
            registerNewEncodedValue.add(widthEncoder = new FactorizedDecimalEncodedValue(prefix + "width", 6, 0.1, false));

        int tmpMax = spatialRuleLookup.size() - 1;
        int bits = 32 - Integer.numberOfLeadingZeros(tmpMax);
        if (bits > 0)
            registerNewEncodedValue.add(spatialEncoder = new SimpleIntEncodedValue("spatial_location", bits, false));

        // workaround to init AbstractWeighting.avSpeedEnc variable that GenericWeighting does not need
        speedEncoder = new FactorizedDecimalEncodedValue("fake", 1, 1, false);

        if (!encodedValueLookup.hasEncodedValue(RoadEnvironment.KEY))
            throw new IllegalStateException("To use DataFlagEncoder you need to add the default EncodedValues. E.g. use GHUtility.addDefaultEncodedValues");

        roadEnvironmentEnc = getObjectEncodedValue(RoadEnvironment.KEY);
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

            // Road attributes (height, weight, width)
            if (isStoreHeight()) {
                List<String> heightTags = Arrays.asList("maxheight", "maxheight:physical");
                extractMeter(edgeFlags, way, heightEncoder, heightTags);
            }

            if (isStoreWeight()) {
                List<String> weightTags = Arrays.asList("maxweight", "maxgcweight");
                extractTons(edgeFlags, way, weightEncoder, weightTags);
            }

            if (isStoreWidth()) {
                List<String> widthTags = Arrays.asList("maxwidth", "maxwidth:physical");
                extractMeter(edgeFlags, way, widthEncoder, widthTags);
            }

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

            // fow now we manually skip parsing, later we have a parsing method per EncodedValue and trigger this from the EncodingManager
            if (spatialEncoder != null) {
                GHPoint estimatedCenter = way.getTag("estimated_center", null);
                if (estimatedCenter != null) {
                    SpatialRule rule = spatialRuleLookup.lookupRule(estimatedCenter);
                    spatialEncoder.setInt(false, edgeFlags, spatialRuleLookup.getSpatialId(rule));
                }
            }

            return edgeFlags;
        } catch (Exception ex) {
            throw new RuntimeException("Error while parsing way " + way.toString(), ex);
        }
    }

    private SpatialRule getSpatialRule(ReaderWay way) {
        GHPoint estmCentre = way.getTag("estimated_center", null);
        if (estmCentre != null) {
            return spatialRuleLookup.lookupRule(estmCentre);
        }
        return SpatialRule.EMPTY;
    }

    private void extractMeter(IntsRef edgeFlags, ReaderWay way, DecimalEncodedValue valueEncoder, List<String> keys) {
        String value = way.getFirstPriorityTag(keys);
        if (isEmpty(value)) return;

        double val;
        try {
            val = stringToMeter(value);
        } catch (Exception ex) {
            LOG.warn("Unable to extract meter from malformed road attribute '{}' for way (OSM_ID = {}).", value, way.getId(), ex);
            return;
        }

        try {
            valueEncoder.setDecimal(false, edgeFlags, val);
        } catch (IllegalArgumentException e) {
            LOG.warn("Unable to process value '{}' for way (OSM_ID = {}).", val, way.getId(), e);
        }

        return;
    }

    private void extractTons(IntsRef edgeFlags, ReaderWay way, DecimalEncodedValue valueEncoder, List<String> keys) {
        String value = way.getFirstPriorityTag(keys);
        if (isEmpty(value)) return;

        double val;
        try {
            val = stringToTons(value);
        } catch (Throwable t) {
            LOG.warn("Unable to extract tons from malformed road attribute '{}' for way (OSM_ID = {}).", value, way.getId(), t);
            return;
        }

        try {
            valueEncoder.setDecimal(false, edgeFlags, val);
        } catch (IllegalArgumentException e) {
            LOG.warn("Unable to process tons value '{}' for way (OSM_ID = {}).", val, way.getId(), e);
        }
    }

    public static double stringToTons(String value) {
        value = toLowerCase(value).replaceAll(" ", "").replaceAll("(tons|ton)", "t");
        value = value.replace("mgw", "").trim();
        double factor = 1;
        if (value.endsWith("t")) {
            value = value.substring(0, value.length() - 1);
        } else if (value.endsWith("lbs")) {
            value = value.substring(0, value.length() - 3);
            factor = 0.00045359237;
        }

        return Double.parseDouble(value) * factor;
    }

    public static double stringToMeter(String value) {
        value = toLowerCase(value).replaceAll(" ", "").replaceAll("(meters|meter|mtrs|mtr|mt|m\\.)", "m");
        double factor = 1;
        double offset = 0;
        value = value.replaceAll("(\\\"|\'\')", "in").replaceAll("(\'|feet)", "ft");
        if (value.startsWith("~") || value.contains("approx")) {
            value = value.replaceAll("(\\~|approx)", "").trim();
            factor = 0.8;
        }

        if (value.endsWith("in")) {
            int startIndex = value.indexOf("ft");
            String inchValue;
            if (startIndex < 0) {
                startIndex = 0;
            } else {
                startIndex += 2;
            }

            inchValue = value.substring(startIndex, value.length() - 2);
            value = value.substring(0, startIndex);
            offset = Double.parseDouble(inchValue) * 0.0254;
        }

        if (value.endsWith("ft")) {
            value = value.substring(0, value.length() - 2);
            factor *= 0.3048;
        } else if (value.endsWith("m")) {
            value = value.substring(0, value.length() - 1);
        }

        if (value.isEmpty()) {
            return offset;
        } else {
            return Double.parseDouble(value) * factor + offset;
        }
    }

    /**
     * This method returns the spatialId stored in the specified flags or -1 if not enabled for this encoder.
     */
    public int getSpatialId(IntsRef flags) {
        if (spatialEncoder == null)
            return -1;

        return spatialEncoder.getInt(false, flags);
    }

    /**
     * This method set the spatial ID (e.g. country ID) of the specified flags to the specified id. Fetch the unique
     * spatial ID via spatialRuleLookup.lookup().getSpatialId
     */
    public void setSpatialId(IntsRef flags, int id) {
        spatialEncoder.setInt(false, flags, id);
    }

    public double getHeight(EdgeIteratorState edge) {
        IntsRef edgeFlags = edge.getFlags();
        return heightEncoder.getDecimal(false, edgeFlags);
    }

    public double getWeight(EdgeIteratorState edge) {
        IntsRef edgeFlags = edge.getFlags();
        return weightEncoder.getDecimal(false, edgeFlags);
    }

    public double getWidth(EdgeIteratorState edge) {
        IntsRef edgeFlags = edge.getFlags();
        return widthEncoder.getDecimal(false, edgeFlags);
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

    @Override
    public InstructionAnnotation getAnnotation(IntsRef flags, Translation tr) {
        if (roadEnvironmentEnc.getObject(false, flags) == RoadEnvironment.FORD) {
            return new InstructionAnnotation(1, tr.tr("way_contains_ford"));
        }

        return super.getAnnotation(flags, tr);
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
        return new WeightingConfig(getObjectEncodedValue(RoadClass.KEY), speedArray);
    }

    public static class WeightingConfig {
        private final double[] speedArray;
        private final ObjectEncodedValue roadClassEnc;

        public WeightingConfig(ObjectEncodedValue roadClassEnc, double[] speedArray) {
            this.roadClassEnc = roadClassEnc;
            this.speedArray = speedArray;
        }

        public double getSpeed(EdgeIteratorState edgeState) {
            RoadClass rc = (RoadClass) edgeState.get(roadClassEnc);
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
