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

import com.graphhopper.reader.ConditionalSpeedInspector;
import com.graphhopper.reader.ConditionalTagInspector;
import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.conditional.ConditionalOSMTagInspector;
import com.graphhopper.reader.osm.conditional.ConditionalParser;
import com.graphhopper.reader.osm.conditional.DateRangeParser;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.parsers.OSMRoadAccessParser;
import com.graphhopper.routing.util.parsers.helpers.OSMValueExtractor;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;

import java.util.*;

/**
 * Abstract class which handles flag decoding and encoding. Every encoder should be registered to a
 * EncodingManager to be usable. If you want the full long to be stored you need to enable this in
 * the GraphHopperStorage.
 *
 * @author Peter Karich
 * @author Nop
 * @author Andrzej Oles
 * @see EncodingManager
 */
public abstract class AbstractFlagEncoder implements FlagEncoder {
    protected final Set<String> intendedValues = new HashSet<>(5);
    // order is important
    protected final List<String> restrictions = new ArrayList<>(5);
    protected final Set<String> restrictedValues = new HashSet<>(5);
    protected final Set<String> ferries = new HashSet<>(5);
    protected final Set<String> oneways = new HashSet<>(5);
    // http://wiki.openstreetmap.org/wiki/Mapfeatures#Barrier
    protected final Set<String> blockByDefaultBarriers = new HashSet<>(5); // barrier which needs to be explicitly allowed, otherwise it blocks
    protected final Set<String> passByDefaultBarriers = new HashSet<>(5);  // barrier which may be explicitly forbidden, otherwise it is allowed
    protected final int speedBits;
    protected final double speedFactor;
    private final int maxTurnCosts;
    private long encoderBit;
    protected BooleanEncodedValue accessEnc;
    protected BooleanEncodedValue roundaboutEnc;
    protected DecimalEncodedValue avgSpeedEnc;
    protected PMap properties;
    // This value determines the maximal possible speed of any road regardless of the maxspeed value
    // lower values allow more compact representation of the routing graph
    protected int maxPossibleSpeed;
    private boolean blockFords = true;
    private boolean registered;
    protected EncodedValueLookup encodedValueLookup;
    private ConditionalTagInspector conditionalTagInspector;
    protected FerrySpeedCalculator ferrySpeedCalc;
    // ORS-GH MOD START - new field
    private ConditionalSpeedInspector conditionalSpeedInspector;
    // ORS-GH MOD END

    /**
     * @param speedBits    specify the number of bits used for speed
     * @param speedFactor  specify the factor to multiple the stored value (can be used to increase
     *                     or decrease accuracy of speed value)
     * @param maxTurnCosts specify the maximum value used for turn costs, if this value is reached a
     *                     turn is forbidden and results in costs of positive infinity.
     */
    protected AbstractFlagEncoder(int speedBits, double speedFactor, int maxTurnCosts) {
        this.maxTurnCosts = maxTurnCosts <= 0 ? 0 : maxTurnCosts;
        this.speedBits = speedBits;
        this.speedFactor = speedFactor;
        oneways.add("yes");
        oneways.add("true");
        oneways.add("1");
        oneways.add("-1");

        ferries.add("shuttle_train");
        ferries.add("ferry");

        restrictions.addAll(OSMRoadAccessParser.toOSMRestrictions(getTransportationMode()));
    }

    protected void init(DateRangeParser dateRangeParser) {
        ferrySpeedCalc = new FerrySpeedCalculator(speedFactor, maxPossibleSpeed, 30, 20, 5);

        ConditionalOSMTagInspector tagInspector = new ConditionalOSMTagInspector(Collections.singletonList(dateRangeParser), restrictions, restrictedValues, intendedValues, false);
        //DateTime parser needs to go last = have highest priority in order to allow for storing unevaluated conditionals
        tagInspector.addValueParser(ConditionalParser.createDateTimeParser());
        this.setConditionalTagInspector(tagInspector);

    }

    protected void setConditionalTagInspector(ConditionalTagInspector inspector) {
        if (conditionalTagInspector != null)
            throw new IllegalStateException("You must not register a FlagEncoder (" + toString() + ") twice or for two EncodingManagers!");

        registered = true;
        conditionalTagInspector = inspector;
    }

    @Override
    public boolean isRegistered() {
        return registered;
    }

    public boolean isBlockFords() {
        return blockFords;
    }

    protected void blockFords(boolean blockFords) {
        this.blockFords = blockFords;
    }

    protected void blockPrivate(boolean blockPrivate) {
        if (!blockPrivate) {
            if (!restrictedValues.remove("private"))
                throw new IllegalStateException("no 'private' found in restrictedValues");
            intendedValues.add("private");
        }
    }

    public ConditionalTagInspector getConditionalTagInspector() {
        return conditionalTagInspector;
    }

    // ORS-GH MOD START - additional method
    public ConditionalSpeedInspector getConditionalSpeedInspector() {
        return conditionalSpeedInspector;
    }

    public void setConditionalSpeedInspector(ConditionalSpeedInspector conditionalSpeedInspector) {
        this.conditionalSpeedInspector = conditionalSpeedInspector;
    }
    // ORS-GH MOD END

    /**
     * Defines bits used for edge flags used for access, speed etc.
     */
    public void createEncodedValues(List<EncodedValue> registerNewEncodedValue, String prefix, int index) {
        // define the first 2 bits in flags for access
        registerNewEncodedValue.add(accessEnc = new SimpleBooleanEncodedValue(EncodingManager.getKey(prefix, "access"), true));
        roundaboutEnc = getBooleanEncodedValue(Roundabout.KEY);
        encoderBit = 1L << index;
    }

    /**
     * Analyze properties of a way and create the edge flags. This method is called in the second
     * parsing step.
     */
    public abstract IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, EncodingManager.Access access);

    public int getMaxTurnCosts() {
        return maxTurnCosts;
    }

    /**
     * Decide whether a way is routable for a given mode of travel. This skips some ways before
     * handleWayTags is called.
     *
     * @return the encoded value to indicate if this encoder allows travel or not.
     */
    public abstract EncodingManager.Access getAccess(ReaderWay way);

    /**
     * Parse tags on nodes. Node tags can add to speed (like traffic_signals) where the value is
     * strict negative or blocks access (like a barrier), then the value is strictly positive. This
     * method is called in the second parsing step.
     *
     * @return encoded values or 0 if not blocking or no value stored
     */
    public long handleNodeTags(ReaderNode node) {
        boolean blockByDefault = node.hasTag("barrier", blockByDefaultBarriers);
        if (blockByDefault || node.hasTag("barrier", passByDefaultBarriers)) {
            boolean locked = false;
            if (node.hasTag("locked", "yes"))
                locked = true;

            for (String res : restrictions) {
                if (!locked && node.hasTag(res, intendedValues))
                    return 0;

                if (node.hasTag(res, restrictedValues))
                    return encoderBit;
            }

            if (blockByDefault)
                return encoderBit;
            return 0;
        }

        if ((node.hasTag("highway", "ford") || node.hasTag("ford", "yes"))
                && (blockFords && !node.hasTag(restrictions, intendedValues) || node.hasTag(restrictions, restrictedValues))) {
            return encoderBit;
        }

        return 0;
    }

    @Override
    public double getMaxSpeed() {
        return maxPossibleSpeed;
    }

    /**
     * @return {@link Double#NaN} if no maxspeed found
     */
    protected double getMaxSpeed(ReaderWay way) {
        double maxSpeed = OSMValueExtractor.stringToKmh(way.getTag("maxspeed"));
        double fwdSpeed = OSMValueExtractor.stringToKmh(way.getTag("maxspeed:forward"));
        if (isValidSpeed(fwdSpeed) && (!isValidSpeed(maxSpeed) || fwdSpeed < maxSpeed))
            maxSpeed = fwdSpeed;

        double backSpeed = OSMValueExtractor.stringToKmh(way.getTag("maxspeed:backward"));
        if (isValidSpeed(backSpeed) && (!isValidSpeed(maxSpeed) || backSpeed < maxSpeed))
            maxSpeed = backSpeed;

        return maxSpeed;
    }

    /**
     * @return <i>true</i> if the given speed is not {@link Double#NaN}
     */
    protected boolean isValidSpeed(double speed) {
        return !Double.isNaN(speed);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 61 * hash + this.accessEnc.hashCode();
        hash = 61 * hash + this.toString().hashCode();
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;

        if (getClass() != obj.getClass())
            return false;
        AbstractFlagEncoder afe = (AbstractFlagEncoder) obj;
        return toString().equals(afe.toString()) && encoderBit == afe.encoderBit && accessEnc.equals(afe.accessEnc);
    }

    /**
     * Second parsing step. Invoked after splitting the edges. Currently used to offer a hook to
     * calculate precise speed values based on elevation data stored in the specified edge.
     */
    public void applyWayTags(ReaderWay way, EdgeIteratorState edge) {
    }

    public final DecimalEncodedValue getAverageSpeedEnc() {
        if (avgSpeedEnc == null)
            throw new NullPointerException("FlagEncoder " + toString() + " not yet initialized");
        return avgSpeedEnc;
    }

    public final BooleanEncodedValue getAccessEnc() {
        if (accessEnc == null)
            throw new NullPointerException("FlagEncoder " + toString() + " not yet initialized");
        return accessEnc;
    }

    protected void setSpeed(boolean reverse, IntsRef edgeFlags, double speed) {
        if (speed < speedFactor / 2) {
            avgSpeedEnc.setDecimal(reverse, edgeFlags, 0);
            accessEnc.setBool(reverse, edgeFlags, false);
        } else {
            avgSpeedEnc.setDecimal(reverse, edgeFlags, speed > getMaxSpeed() ? getMaxSpeed() : speed);
        }
    }

    /**
     * @param way   needed to retrieve tags
     * @param speed speed guessed e.g. from the road type or other tags
     * @return The assumed speed.
     */
    protected double applyMaxSpeed(ReaderWay way, double speed) {
        double maxSpeed = getMaxSpeed(way);
        // We obey speed limits
        if (isValidSpeed(maxSpeed)) {
            // We assume that the average speed is 90% of the allowed maximum
            return maxSpeed * 0.9;
        }
        return speed;
    }

    // ORS-GH MOD START - additional methods for conditional speeds
    protected double applyConditionalSpeed(String value, double speed) {
        double maxSpeed = parseSpeed(value);
        // We obey speed limits
        if (maxSpeed >= 0) {
            // We assume that the average speed is 90% of the allowed maximum
            return maxSpeed * 0.9;
        }
        return speed;
    }

    /**
     * @return the speed in km/h
     */
    public static double parseSpeed(String str) {
        if (Helper.isEmpty(str))
            return -1;

        // on some German autobahns and a very few other places
        if ("none".equals(str))
            return MaxSpeed.UNLIMITED_SIGN_SPEED;

        if (str.endsWith(":rural") || str.endsWith(":trunk"))
            return 80;

        if (str.endsWith(":urban"))
            return 50;

        if (str.equals("walk") || str.endsWith(":living_street"))
            return 6;

        try {
            int val;
            // see https://en.wikipedia.org/wiki/Knot_%28unit%29#Definitions
            int mpInteger = str.indexOf("mp");
            if (mpInteger > 0) {
                str = str.substring(0, mpInteger).trim();
                val = Integer.parseInt(str);
                return val * DistanceCalcEarth.KM_MILE;
            }

            int knotInteger = str.indexOf("knots");
            if (knotInteger > 0) {
                str = str.substring(0, knotInteger).trim();
                val = Integer.parseInt(str);
                return val * 1.852;
            }

            int kmInteger = str.indexOf("km");
            if (kmInteger > 0) {
                str = str.substring(0, kmInteger).trim();
            } else {
                kmInteger = str.indexOf("kph");
                if (kmInteger > 0) {
                    str = str.substring(0, kmInteger).trim();
                }
            }

            return Integer.parseInt(str);
        } catch (Exception ex) {
            return -1;
        }
    }
    // ORS-GH MOD END

    protected String getPropertiesString() {
        return "speed_factor=" + speedFactor + "|speed_bits=" + speedBits + "|turn_costs=" + (maxTurnCosts > 0);
    }

    @Override
    public List<EncodedValue> getEncodedValues() {
        return encodedValueLookup.getEncodedValues();
    }

    @Override
    public <T extends EncodedValue> T getEncodedValue(String key, Class<T> encodedValueType) {
        return encodedValueLookup.getEncodedValue(key, encodedValueType);
    }

    @Override
    public BooleanEncodedValue getBooleanEncodedValue(String key) {
        return encodedValueLookup.getBooleanEncodedValue(key);
    }

    @Override
    public IntEncodedValue getIntEncodedValue(String key) {
        return encodedValueLookup.getIntEncodedValue(key);
    }

    @Override
    public DecimalEncodedValue getDecimalEncodedValue(String key) {
        return encodedValueLookup.getDecimalEncodedValue(key);
    }

    @Override
    public <T extends Enum<?>> EnumEncodedValue<T> getEnumEncodedValue(String key, Class<T> enumType) {
        return encodedValueLookup.getEnumEncodedValue(key, enumType);
    }

    @Override
    public StringEncodedValue getStringEncodedValue(String key) {
        return encodedValueLookup.getStringEncodedValue(key);
    }

    public void setEncodedValueLookup(EncodedValueLookup encodedValueLookup) {
        this.encodedValueLookup = encodedValueLookup;
    }

    @Override
    public boolean supportsTurnCosts() {
        return maxTurnCosts > 0;
    }

    @Override
    public boolean supports(Class<?> feature) {
        return false;
    }

    @Override
    public boolean hasEncodedValue(String key) {
        return encodedValueLookup.hasEncodedValue(key);
    }

    public final List<String> getRestrictions() {
        return restrictions;
    }

    // ORS-GH MOD START - additional methods to handle conditional restrictions
    public EncodingManager.Access isRestrictedWayConditionallyPermitted(ReaderWay way) {
        return isRestrictedWayConditionallyPermitted(way, EncodingManager.Access.WAY);
    }

    public EncodingManager.Access isRestrictedWayConditionallyPermitted(ReaderWay way, EncodingManager.Access accept) {
        return getConditionalAccess(way, accept, true);
    }


    public EncodingManager.Access isPermittedWayConditionallyRestricted(ReaderWay way) {
        return isPermittedWayConditionallyRestricted(way, EncodingManager.Access.WAY);
    }

    public EncodingManager.Access isPermittedWayConditionallyRestricted(ReaderWay way, EncodingManager.Access accept) {
        return getConditionalAccess(way, accept, false);
    }

    private EncodingManager.Access getConditionalAccess(ReaderWay way, EncodingManager.Access accept, boolean permissive) {
        ConditionalTagInspector conditionalTagInspector = getConditionalTagInspector();
        boolean access = permissive ? conditionalTagInspector.isRestrictedWayConditionallyPermitted(way) :
                !conditionalTagInspector.isPermittedWayConditionallyRestricted(way);
        if (conditionalTagInspector.hasLazyEvaluatedConditions())
            return access ? EncodingManager.Access.PERMITTED : EncodingManager.Access.RESTRICTED;
        else
            return access ? accept : EncodingManager.Access.CAN_SKIP;
    }
    // ORS-GH MOD END
}
