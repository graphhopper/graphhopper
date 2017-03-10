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
import com.graphhopper.routing.util.spatialrules.AccessValue;
import com.graphhopper.routing.util.spatialrules.SpatialRule;
import com.graphhopper.routing.util.spatialrules.SpatialRuleLookup;
import com.graphhopper.routing.util.spatialrules.TransportationMode;
import com.graphhopper.routing.weighting.GenericWeighting;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.Map.Entry;

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
    private long bit0;
    private EncodedDoubleValue carFwdMaxspeedEncoder;
    private EncodedDoubleValue carBwdMaxspeedEncoder;
    private EncodedDoubleValue heightEncoder;
    private EncodedDoubleValue weightEncoder;
    private EncodedDoubleValue widthEncoder;
    private EncodedValue surfaceEncoder;
    private EncodedValue highwayEncoder;
    private EncodedValue transportModeEncoder;
    private EncodedValue accessEncoder;
    private boolean storeHeight = false;
    private boolean storeWeight = false;
    private boolean storeWidth = false;
    private EncodedValue spatialEncoder;
    private int spatialRules = 0;
    private SpatialRuleLookup spatialRuleLookup;

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
        this.setSpatialRules(properties.getInt("spatial_rules", 0));
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
        // Note: if you update this list you have to update the method getAccessValue too
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
    public int defineWayBits(int index, int shift) {
        // TODO use this approach in other flag encoders too then we can do a global swap for all and bit0 can be at position 0!
        bit0 = 1L << shift;
        shift++;

        // TODO support different vehicle types, currently just roundabout and fwd&bwd for one vehicle type
        shift = super.defineWayBits(index, shift);

        carFwdMaxspeedEncoder = new EncodedDoubleValue("car fwd maxspeed", shift, speedBits, speedFactor, 0, maxPossibleSpeed, true);
        shift += carFwdMaxspeedEncoder.getBits();

        carBwdMaxspeedEncoder = new EncodedDoubleValue("car bwd maxspeed", shift, speedBits, speedFactor, 0, maxPossibleSpeed, true);
        shift += carBwdMaxspeedEncoder.getBits();

        /* Value range: [3.0m, 5.4m] */
        if (isStoreHeight()) {
            heightEncoder = new EncodedDoubleValue("height restriction", shift, 7, 0.1, 0, 12, true);
            shift += heightEncoder.getBits();
        }

        /* Value range: [1.0t, 59.5t] */
        if (isStoreWeight()) {
            weightEncoder = new EncodedDoubleValue("weight restriction", shift, 10, 0.1, 0, 100, true);
            shift += weightEncoder.getBits();
        }

        /* Value range: [2.5m, 3.5m] */
        if (isStoreWidth()) {
            widthEncoder = new EncodedDoubleValue("width restriction", shift, 6, 0.1, 0, 6, true);
            shift += widthEncoder.getBits();
        }

        highwayEncoder = new EncodedValue("highway", shift, 5, 1, 0, highwayMap.size(), true);
        shift += highwayEncoder.getBits();

        surfaceEncoder = new EncodedValue("surface", shift, 4, 1, 0, surfaceMap.size(), true);
        shift += surfaceEncoder.getBits();

        transportModeEncoder = new EncodedValue("transport mode", shift, 3, 1, 0, transportModeMap.size(), true);
        shift += transportModeEncoder.getBits();

        accessEncoder = new EncodedValue("access car", shift, 3, 1, 1, 4, true);
        shift += accessEncoder.getBits();

        if (spatialRules > 0) {
            int tmpMax = spatialRules + 1;
            int bits = 32 - Integer.numberOfLeadingZeros(tmpMax);
            spatialEncoder = new EncodedValue("spatial_location", shift, bits, 1, 1, tmpMax, true);
            shift += spatialEncoder.getBits();
        }

        return shift;
    }

    @Override
    public long handleRelationTags(ReaderRelation relation, long oldRelationFlags) {
        return 0;
    }

    @Override
    public long acceptWay(ReaderWay way) {
        // important to skip unsupported highways, otherwise too many have to be removed after graph creation
        // and node removal is not yet designed for that
        if (getHighwayValue(way) == 0)
            return 0;

        return acceptBit;
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

        if (isSpatialRuleLookupEnabled() && accessValue == 0) {
            // TODO Fix transportation mode when adding other forms of transportation
            switch (getSpatialRule(way).getAccessValue(way.getTag("highway", ""), TransportationMode.MOTOR_VEHICLE, AccessValue.ACCESSIBLE)) {
                case ACCESSIBLE:
                    accessValue = accessMap.get("yes");
                    break;
                case EVENTUALLY_ACCESSIBLE:
                    accessValue = accessMap.get("destination");
                    break;
                case NOT_ACCESSIBLE:
                    accessValue = accessMap.get("no");
                    break;
            }
        }

        return accessValue;
    }

    public AccessValue getAccessValue(long flags) {
        int accessValue = (int) accessEncoder.getValue(flags);
        switch (accessValue) {
            case 0:
                return AccessValue.ACCESSIBLE;
            // NOT_ACCESSIBLE_KEY
            case 3:
                return AccessValue.NOT_ACCESSIBLE;
            default:
                return AccessValue.EVENTUALLY_ACCESSIBLE;
        }
    }

    @Override
    public long handleWayTags(ReaderWay way, long allowed, long relationFlags) {
        if (!isAccept(allowed))
            return 0;

        try {
            // HIGHWAY
            int hwValue = getHighwayValue(way);
            // exclude any routing like if you have car and need to exclude all rails or ships
            if (hwValue == 0)
                return 0;

            long flags = 0;
            if (isFerry(allowed)) {
                hwValue = highwayMap.get("ferry");
            }

            flags = highwayEncoder.setValue(0, hwValue);

            // MAXSPEED
            double maxSpeed = parseSpeed(way.getTag("maxspeed"));
            if (isSpatialRuleLookupEnabled() && maxSpeed < 0) {
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
                flags = carFwdMaxspeedEncoder.setDoubleValue(flags, fwdSpeed);

            if (bwdSpeed > 0)
                flags = carBwdMaxspeedEncoder.setDoubleValue(flags, bwdSpeed);

            // Road attributes (height, weight, width)
            if (isStoreHeight()) {
                List<String> heightTags = Arrays.asList("maxheight", "maxheight:physical");
                flags = extractMeter(way, flags, heightEncoder, heightTags);
            }

            if (isStoreWeight()) {
                List<String> weightTags = Arrays.asList("maxweight", "maxgcweight");
                flags = extractTons(way, flags, weightEncoder, weightTags);
            }

            if (isStoreWidth()) {
                List<String> widthTags = Arrays.asList("maxwidth", "maxwidth:physical");
                flags = extractMeter(way, flags, widthEncoder, widthTags);
            }

            // SURFACE
            String surfaceValue = way.getTag("surface");
            Integer sValue = surfaceMap.get(surfaceValue);
            if (sValue == null)
                sValue = 0;
            flags = surfaceEncoder.setValue(flags, sValue);

            // TRANSPORT MODE
            int tmValue = 0;
            for (String tm : transportModeList) {
                if (way.hasTag(tm)) {
                    tmValue = transportModeMap.get(tm);
                    break;
                }
            }
            flags = transportModeEncoder.setValue(flags, tmValue);

            // ROUNDABOUT
            boolean isRoundabout = way.hasTag("junction", "roundabout");
            if (isRoundabout)
                flags = setBool(flags, K_ROUNDABOUT, true);

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
                    flags |= backwardBit;
                else
                    flags |= forwardBit;
            } else
                flags |= directionBitMask;

            if (!isBit0Empty(flags))
                throw new IllegalStateException("bit0 has to be empty on creation");

            flags = accessEncoder.setValue(flags, getAccessValue(way));


            if (isSpatialRuleLookupEnabled()) {
                GHPoint estimatedCenter = way.getTag("estimated_center", null);
                if (estimatedCenter != null) {
                    SpatialRule rule = spatialRuleLookup.lookupRule(estimatedCenter);
                    flags = spatialEncoder.setValue(flags, spatialRuleLookup.getSpatialId(rule));
                }
            }

            return flags;
        } catch (Exception ex) {
            throw new RuntimeException("Error while parsing way " + way.toString(), ex);
        }
    }

    private boolean isSpatialRuleLookupEnabled() {
        if (spatialRules > 0) {
            if (spatialRuleLookup == null)
                throw new IllegalStateException("This encoder was asked to store spatial IDs for every edge, " +
                        "but no spatial lookup was specified");

            return true;
        }
        return false;
    }

    private SpatialRule getSpatialRule(ReaderWay way) {
        GHPoint estmCentre = way.getTag("estimated_center", null);
        if (estmCentre != null) {
            return spatialRuleLookup.lookupRule(estmCentre);
        }
        return SpatialRule.EMPTY;
    }

    private long extractMeter(ReaderWay way, long flags, EncodedDoubleValue valueEncoder, List<String> keys) {
        String value = way.getFirstPriorityTag(keys);
        if (Helper.isEmpty(value)) return flags;

        double val;
        try {
            val = stringToMeter(value);
        } catch (Exception ex) {
            LOG.warn("Unable to extract meter from malformed road attribute '{}' for way (OSM_ID = {}).", value, way.getId(), ex);
            return flags;
        }

        try {
            flags = valueEncoder.setDoubleValue(flags, val);
        } catch (IllegalArgumentException e) {
            LOG.warn("Unable to process value '{}' for way (OSM_ID = {}).", val, way.getId(), e);
        }

        return flags;
    }

    private long extractTons(ReaderWay way, long flags, EncodedDoubleValue valueEncoder, List<String> keys) {
        String value = way.getFirstPriorityTag(keys);
        if (Helper.isEmpty(value)) return flags;

        double val;
        try {
            val = stringToTons(value);
        } catch (Throwable t) {
            LOG.warn("Unable to extract tons from malformed road attribute '{}' for way (OSM_ID = {}).", value, way.getId(), t);
            return flags;
        }

        try {
            flags = valueEncoder.setDoubleValue(flags, val);
        } catch (IllegalArgumentException e) {
            LOG.warn("Unable to process tons value '{}' for way (OSM_ID = {}).", val, way.getId(), e);
        }

        return flags;
    }

    public static double stringToTons(String value) {
        value = value.toLowerCase().replaceAll(" ", "").replaceAll("(tons|ton)", "t");
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
        value = value.toLowerCase().replaceAll(" ", "").replaceAll("(meters|meter|mtrs|mtr|mt|m\\.)", "m");
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
    public int getSpatialId(long flags) {
        if (spatialEncoder == null)
            return -1;

        return (int) spatialEncoder.getValue(flags);
    }

    /**
     * This method set the spatial ID (e.g. country ID) of the specified flags to the specified id. Fetch the unique
     * spatial ID via spatialRuleLookup.lookup().getSpatialId
     */
    public long setSpatialId(long flags, int id) {
        return spatialEncoder.setValue(flags, id);
    }

    @Override
    public long reverseFlags(long flags) {
        // see #728 for an explanation
        return flags ^ bit0;
    }

    /**
     * Interpret flags in forward direction if bit0 is empty. This method is used when accessing
     * direction dependent values and avoid reverse flags, see #728.
     */
    private boolean isBit0Empty(long flags) {
        return (flags & bit0) == 0;
    }

    public int getHighway(EdgeIteratorState edge) {
        return (int) highwayEncoder.getValue(edge.getFlags());
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

    public double[] getHighwaySpeedMap(Map<String, Double> map) {
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
        return (int) surfaceEncoder.getValue(edge.getFlags());
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
        return (int) transportModeEncoder.getValue(edge.getFlags());
    }

    public boolean isTransportModeTunnel(EdgeIteratorState edge) {
        return transportModeEncoder.getValue(edge.getFlags()) == this.transportModeTunnelValue;
    }

    public boolean isTransportModeBridge(EdgeIteratorState edge) {
        return transportModeEncoder.getValue(edge.getFlags()) == this.transportModeBridgeValue;
    }

    public boolean isTransportModeFord(long flags) {
        return transportModeEncoder.getValue(flags) == this.transportModeFordValue;
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

    public boolean isRoundabout(EdgeIteratorState edge) {
        // use direct call instead of isBool
        return (edge.getFlags() & roundaboutBit) != 0;
    }

    public int getAccessType(String accessStr) {
        // access, motor_vehicle, bike, foot, hgv, bus
        return 0;
    }

    public final boolean isForward(EdgeIteratorState edge, int accessType) {
        // TODO shift dependent on the accessType
        // use only one bit for foot?
        long flags = edge.getFlags();
        return (flags & (isBit0Empty(flags) ? forwardBit : backwardBit)) != 0;
    }

    @Override
    public final boolean isForward(long flags) {
        // TODO remove old method
        return (flags & (isBit0Empty(flags) ? forwardBit : backwardBit)) != 0;
    }

    public final boolean isBackward(EdgeIteratorState edge, int accessType) {
        long flags = edge.getFlags();
        return (flags & (isBit0Empty(flags) ? backwardBit : forwardBit)) != 0;
    }

    @Override
    public final boolean isBackward(long flags) {
        // TODO remove old method
        return (flags & (isBit0Empty(flags) ? backwardBit : forwardBit)) != 0;
    }

    public double getMaxspeed(EdgeIteratorState edge, int accessType, boolean reverse) {
        long flags = edge.getFlags();
        if (!isBit0Empty(flags))
            reverse = !reverse;

        double val;
        if (reverse)
            val = carBwdMaxspeedEncoder.getDoubleValue(flags);
        else
            val = carFwdMaxspeedEncoder.getDoubleValue(flags);

        if (val < 0)
            throw new IllegalStateException("maxspeed cannot be negative, edge:" + edge.getEdge() + ", access type" + accessType + ", reverse:" + reverse);

        // default is 0 but return invalid speed explicitely (TODO can we do this at the value encoder level?)
        if (val == 0)
            return -1;
        return val;
    }

    public double getHeight(EdgeIteratorState edge) {
        long flags = edge.getFlags();
        return heightEncoder.getDoubleValue(flags);
    }

    public double getWeight(EdgeIteratorState edge) {
        long flags = edge.getFlags();
        return weightEncoder.getDoubleValue(flags);
    }

    public double getWidth(EdgeIteratorState edge) {
        long flags = edge.getFlags();
        return widthEncoder.getDoubleValue(flags);
    }

    @Override
    public long flagsDefault(boolean forward, boolean backward) {
        // just pick car mode to set access values?
        // throw new RuntimeException("do not call flagsDefault");
        return setAccess(0, forward, backward);
    }

    @Override
    public long setAccess(long flags, boolean forward, boolean backward) {
        // TODO we should interpret access for *any* vehicle
        // TODO in subnetwork we need to remove access for certain weighting profiles (or set of roads?)
        boolean isForward = isBit0Empty(flags);
        if (!isForward) {
            boolean tmp = forward;
            forward = backward;
            backward = tmp;
        }

        flags = forward ? flags | forwardBit : flags & ~forwardBit;
        flags = backward ? flags | backwardBit : flags & ~backwardBit;
        return flags;
    }

    @Override
    public long setSpeed(long flags, double speed) {
        throw new RuntimeException("do not call setSpeed");
    }

    @Override
    protected long setLowSpeed(long flags, double speed, boolean reverse) {
        throw new RuntimeException("do not call setLowSpeed");
    }

    @Override
    public double getSpeed(long flags) {
        throw new UnsupportedOperationException("Calculate speed via more customizable Weighting.calcMillis method");
    }

    @Override
    public long setReverseSpeed(long flags, double speed) {
        throw new RuntimeException("do not call setReverseSpeed");
    }

    @Override
    public double getReverseSpeed(long flags) {
        throw new RuntimeException("do not call getReverseSpeed");
    }

    @Override
    public long setProperties(double speed, boolean forward, boolean backward) {
        throw new RuntimeException("do not call setProperties");
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


    public DataFlagEncoder setSpatialRules(int rules) {
        this.spatialRules = rules;
        return this;
    }

    public DataFlagEncoder setSpatialRuleLookup(SpatialRuleLookup spatialRuleLookup) {
        if (spatialRuleLookup.size() != spatialRules)
            throw new IllegalArgumentException("You have to configure the encoder to accept the identical amount of rules " +
                    "that the spatial rule lookup has (" + spatialRuleLookup.size() + ") but it was " + spatialRules);

        this.spatialRuleLookup = spatialRuleLookup;
        return this;
    }

    @Override
    public InstructionAnnotation getAnnotation(long flags, Translation tr) {
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
                "|store_width=" + storeWidth +
                "|spatial_rules=" + spatialRules;
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
    public ConfigMap readStringMap(PMap weightingMap) {
        Map<String, Double> map = new HashMap<>();
        for (Entry<String, Double> e : DEFAULT_SPEEDS.entrySet()) {
            map.put(e.getKey(), weightingMap.getDouble("highways." + e.getKey(), e.getValue()));
        }

        ConfigMap cMap = new ConfigMap();
        cMap.put("highways", map);

        cloneDoubleAttribute(weightingMap, cMap, GenericWeighting.HEIGHT_LIMIT, 0d);
        cloneDoubleAttribute(weightingMap, cMap, GenericWeighting.WEIGHT_LIMIT, 0d);
        cloneDoubleAttribute(weightingMap, cMap, GenericWeighting.WIDTH_LIMIT, 0d);

        return cMap;
    }

    private void cloneDoubleAttribute(PMap weightingMap, ConfigMap cMap, String key, double _default) {
        if (weightingMap.has(key))
            cMap.put(key, weightingMap.getDouble(key, _default));
    }
}
