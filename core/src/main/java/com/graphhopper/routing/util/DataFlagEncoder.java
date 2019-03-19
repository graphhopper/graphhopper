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
import com.graphhopper.routing.util.spatialrules.TransportationMode;
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
import java.util.Map.Entry;

import static com.graphhopper.routing.util.spatialrules.SpatialRule.Access.*;
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
    private final Map<String, Integer> surfaceMap = new HashMap<>();
    private final Map<String, Integer> highwayMap = new HashMap<>();
    private final Map<String, Integer> accessMap = new HashMap<>();
    private final List<String> transportModeList = new ArrayList<>();
    private final Map<String, Integer> transportModeMap = new HashMap<>();
    private final int transportModeTunnelValue;
    private final int transportModeBridgeValue;
    private final int transportModeFordValue;
    private IntEncodedValue dynAccessEncoder;
    private DecimalEncodedValue carMaxspeedEncoder;
    private DecimalEncodedValue heightEncoder;
    private DecimalEncodedValue weightEncoder;
    private DecimalEncodedValue widthEncoder;
    private IntEncodedValue surfaceEncoder;
    private IntEncodedValue highwayEncoder;
    private IntEncodedValue transportModeEncoder;
    private boolean storeHeight = false;
    private boolean storeWeight = false;
    private boolean storeWidth = false;
    private IntEncodedValue spatialEncoder;
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

        maxPossibleSpeed = 140;
        //
        // TODO restrictions (agricultural, emergency, destination, private, delivery, customers)
        //

        // highway and certain tags like ferry and shuttle_train which can be used here (no logical overlap)
        List<String> highwayList = Arrays.asList(
                /* reserve index=0 for unset roads (not accessible) */
                "_default",
                "motorway", "motorway_link", "motorroad", "trunk", "trunk_link",
                "primary", "primary_link", "secondary", "secondary_link", "tertiary", "tertiary_link",
                "unclassified", "residential", "living_street", "service", "road", "track",
                "forestry", "cycleway", "steps", "path", "footway", "pedestrian",
                "ferry", "shuttle_train");
        int counter = 0;
        for (String hw : highwayList) {
            highwayMap.put(hw, counter++);
        }

        // We need transport mode additionally to highway e.g. a secondary highway can be a tunnel.
        // Also 'roundabout' needs a separate bit as a tunnel or a bridge can be a roundabout at the same time.
        transportModeList.addAll(Arrays.asList("_default", "bridge", "tunnel", "ford", "aerialway"));
        counter = 0;
        for (String tm : transportModeList) {
            transportModeMap.put(tm, counter++);
        }
        transportModeTunnelValue = transportModeMap.get("tunnel");
        transportModeBridgeValue = transportModeMap.get("bridge");
        transportModeFordValue = transportModeMap.get("ford");

        List<String> surfaceList = Arrays.asList("_default", "asphalt", "unpaved", "paved", "gravel",
                "ground", "dirt", "grass", "concrete", "paving_stones", "sand", "compacted", "cobblestone", "mud", "ice");
        counter = 0;
        for (String s : surfaceList) {
            surfaceMap.put(s, counter++);
        }

        restrictions.addAll(Arrays.asList("motorcar", "motor_vehicle", "vehicle", "access"));

        // Ordered in increasingly restrictive order
        // Note: if you update this list you have to update the method getAccess too
        List<String> accessList = Arrays.asList(
                //"designated", "permissive", "customers", "delivery",
                "yes", "destination", "private", "no"
        );

        counter = 0;
        for (String s : accessList) {
            accessMap.put(s, counter++);
        }

        accessMap.put("designated", accessMap.get("yes"));
        accessMap.put("permissive", accessMap.get("yes"));

        accessMap.put("customers", accessMap.get("destination"));
        accessMap.put("delivery", accessMap.get("destination"));

        // accessMap.put("forestry", accessMap.get("agricultural"));
    }

    @Override
    public void createEncodedValues(List<EncodedValue> registerNewEncodedValue, String prefix, int index) {
        // TODO support different vehicle types, currently just roundabout and fwd&bwd for one vehicle type
        super.createEncodedValues(registerNewEncodedValue, prefix, index);

        registerNewEncodedValue.add(carMaxspeedEncoder = new FactorizedDecimalEncodedValue(prefix + "car_maxspeed", speedBits, speedFactor, true));

        /* Value range: [3.0m, 5.4m] */
        if (isStoreHeight())
            registerNewEncodedValue.add(heightEncoder = new FactorizedDecimalEncodedValue(prefix + "height", 7, 0.1, false));

        /* Value range: [1.0t, 59.5t] */
        if (isStoreWeight())
            registerNewEncodedValue.add(weightEncoder = new FactorizedDecimalEncodedValue(prefix + "weight", 10, 0.1, false));

        /* Value range: [2.5m, 3.5m] */
        if (isStoreWidth())
            registerNewEncodedValue.add(widthEncoder = new FactorizedDecimalEncodedValue(prefix + "width", 6, 0.1, false));

        registerNewEncodedValue.add(highwayEncoder = new SimpleIntEncodedValue(prefix + "highway", 5, false));
        registerNewEncodedValue.add(surfaceEncoder = new SimpleIntEncodedValue(prefix + "surface", 4, false));
        registerNewEncodedValue.add(transportModeEncoder = new SimpleIntEncodedValue(prefix + "transport_mode", 3, false));
        registerNewEncodedValue.add(dynAccessEncoder = new SimpleIntEncodedValue(prefix + "car_dyn_access", 3, false));

        int tmpMax = spatialRuleLookup.size() - 1;
        int bits = 32 - Integer.numberOfLeadingZeros(tmpMax);
        if (bits > 0)
            registerNewEncodedValue.add(spatialEncoder = new SimpleIntEncodedValue("spatial_location", bits, false));

        // workaround to init AbstractWeighting.avSpeedEnc variable that GenericWeighting does not need
        speedEncoder = carMaxspeedEncoder;
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
        if (getHighwayValue(way) == 0)
            return EncodingManager.Access.CAN_SKIP;

        return EncodingManager.Access.WAY;
    }

    int getHighwayValue(ReaderWay way) {
        String highwayValue = way.getTag("highway");
        Integer hwValue = highwayMap.get(highwayValue);
        if (way.hasTag("impassable", "yes") || way.hasTag("status", "impassable"))
            hwValue = 0;

        if (hwValue == null) {
            hwValue = 0;
            if (way.hasTag("route", ferries)) {
                String motorcarTag = way.getTag("motorcar");
                if (motorcarTag == null)
                    motorcarTag = way.getTag("motor_vehicle");

                if (motorcarTag == null && !way.hasTag("foot") && !way.hasTag("bicycle")
                        || "yes".equals(motorcarTag))
                    hwValue = highwayMap.get("ferry");
            }
        }
        return hwValue;
    }

    int getAccessValue(ReaderWay way) {
        int accessValue = 0;
        Integer tmpAccessValue;
        for (String restriction : restrictions) {
            tmpAccessValue = accessMap.get(way.getTag(restriction, "yes"));
            if (tmpAccessValue != null && tmpAccessValue > accessValue) {
                accessValue = tmpAccessValue;
            }
        }

        if (accessValue == 0) {
            // TODO Fix transportation mode when adding other forms of transportation
            switch (getSpatialRule(way).getAccess(way.getTag("highway", ""), TransportationMode.MOTOR_VEHICLE, YES)) {
                case YES:
                    accessValue = accessMap.get("yes");
                    break;
                case CONDITIONAL:
                    accessValue = accessMap.get("destination");
                    break;
                case NO:
                    accessValue = accessMap.get("no");
                    break;
            }
        }

        return accessValue;
    }

    public SpatialRule.Access getAccessValue(IntsRef flags) {
        int accessValue = dynAccessEncoder.getInt(false, flags);
        switch (accessValue) {
            case 0:
                return YES;
            // NOT_ACCESSIBLE_KEY
            case 3:
                return NO;
            default:
                return CONDITIONAL;
        }
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, EncodingManager.Access access, long relationFlags) {
        if (access.canSkip())
            return edgeFlags;

        try {
            // HIGHWAY
            int hwValue = getHighwayValue(way);
            // exclude any routing like if you have car and need to exclude all rails or ships
            if (hwValue == 0)
                return edgeFlags;

            if (access.isFerry())
                hwValue = highwayMap.get("ferry");

            highwayEncoder.setInt(false, edgeFlags, hwValue);

            // MAXSPEED
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
                carMaxspeedEncoder.setDecimal(false, edgeFlags, fwdSpeed);

            if (bwdSpeed > 0)
                carMaxspeedEncoder.setDecimal(true, edgeFlags, bwdSpeed);

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

            // SURFACE
            String surfaceValue = way.getTag("surface");
            Integer sValue = surfaceMap.get(surfaceValue);
            if (sValue == null)
                sValue = 0;
            surfaceEncoder.setInt(false, edgeFlags, sValue);

            // TRANSPORT MODE
            int tmValue = 0;
            for (String tm : transportModeList) {
                if (way.hasTag(tm)) {
                    tmValue = transportModeMap.get(tm);
                    break;
                }
            }
            transportModeEncoder.setInt(false, edgeFlags, tmValue);

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

            dynAccessEncoder.setInt(false, edgeFlags, getAccessValue(way));

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

    public int getHighway(EdgeIteratorState edge) {
        return highwayEncoder.getInt(false, edge.getFlags());
    }

    /**
     * Do not use within weighting as this is suboptimal from performance point of view.
     */
    public String getHighwayAsString(EdgeIteratorState edge) {
        int val = getHighway(edge);
        for (Entry<String, Integer> e : highwayMap.entrySet()) {
            if (e.getValue() == val)
                return e.getKey();
        }
        return null;
    }

    double[] getHighwaySpeedMap(Map<String, Double> map) {
        if (map == null)
            throw new IllegalArgumentException("Map cannot be null when calling getHighwaySpeedMap");

        double[] res = new double[highwayMap.size()];
        for (Entry<String, Double> e : map.entrySet()) {
            Integer integ = highwayMap.get(e.getKey());
            if (integ == null)
                throw new IllegalArgumentException("Graph not prepared for highway=" + e.getKey());

            if (e.getValue() < 0)
                throw new IllegalArgumentException("Negative speed " + e.getValue() + " not allowed. highway=" + e.getKey());

            res[integ] = e.getValue();
        }
        return res;
    }

    public int getSurface(EdgeIteratorState edge) {
        return surfaceEncoder.getInt(false, edge.getFlags());
    }

    public String getSurfaceAsString(EdgeIteratorState edge) {
        int val = getSurface(edge);
        for (Entry<String, Integer> e : surfaceMap.entrySet()) {
            if (e.getValue() == val)
                return e.getKey();
        }
        return null;
    }

    public int getTransportMode(EdgeIteratorState edge) {
        return transportModeEncoder.getInt(false, edge.getFlags());
    }

    public boolean isTransportModeTunnel(EdgeIteratorState edge) {
        return transportModeEncoder.getInt(false, edge.getFlags()) == transportModeTunnelValue;
    }

    public boolean isTransportModeBridge(EdgeIteratorState edge) {
        return transportModeEncoder.getInt(false, edge.getFlags()) == transportModeBridgeValue;
    }

    public boolean isTransportModeFord(IntsRef edgeFlags) {
        return transportModeEncoder.getInt(false, edgeFlags) == transportModeFordValue;
    }

    public String getTransportModeAsString(EdgeIteratorState edge) {
        int val = getTransportMode(edge);
        for (Entry<String, Integer> e : transportModeMap.entrySet()) {
            if (e.getValue() == val)
                return e.getKey();
        }
        return null;
    }

    public double[] getTransportModeMap(Map<String, Double> map) {
        double[] res = new double[transportModeMap.size()];
        for (Entry<String, Double> e : map.entrySet()) {
            Integer integ = transportModeMap.get(e.getKey());
            if (integ == null)
                throw new IllegalArgumentException("Graph not prepared for transport_mode=" + e.getKey());

            if (e.getValue() < 0)
                throw new IllegalArgumentException("Negative speed " + e.getValue() + " not allowed. transport_mode=" + e.getKey());

            res[integ] = e.getValue();
        }
        return res;
    }

    public int getAccessType(String accessStr) {
        // access, motor_vehicle, bike, foot, hgv, bus
        return 0;
    }

    public double getMaxspeed(EdgeIteratorState edge, int accessType, boolean reverse) {
        double val = reverse ? edge.getReverse(carMaxspeedEncoder) : edge.get(carMaxspeedEncoder);
        if (val < 0)
            throw new IllegalStateException("maxspeed cannot be negative, edge:" + edge.getEdge() + ", access type" + accessType + ", reverse:" + reverse);

        // default is 0 but return invalid speed explicitely (TODO can we do this at the value encoder level?)
        if (val == 0)
            return -1;
        return val;
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
        if (isTransportModeFord(flags)) {
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
        private final double[] speedArray;

        public WeightingConfig(double[] speedArray) {
            this.speedArray = speedArray;
        }

        public double getSpeed(EdgeIteratorState edgeState) {
            int highwayKey = getHighway(edgeState);
            // ensure before (in createResult) that all highways that were specified in the request are known
            double speed = speedArray[highwayKey];
            if (speed < 0)
                throw new IllegalStateException("speed was negative? " + edgeState.getEdge()
                        + ", highway:" + highwayKey);
            return speed;
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
