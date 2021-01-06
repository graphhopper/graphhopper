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

import com.graphhopper.reader.ConditionalTagInspector;
import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.conditional.ConditionalOSMTagInspector;
import com.graphhopper.reader.osm.conditional.DateRangeParser;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.parsers.OSMRoadAccessParser;
import com.graphhopper.routing.util.parsers.helpers.OSMValueExtractor;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.BitUtil;
import com.graphhopper.util.EdgeIteratorState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Abstract class which handles flag decoding and encoding. Every encoder should be registered to a
 * EncodingManager to be usable. If you want the full long to be stored you need to enable this in
 * the GraphHopperStorage.
 *
 * @author Peter Karich
 * @author Nop
 * @see EncodingManager
 */
public abstract class AbstractFlagEncoder implements FlagEncoder {
    private final static Logger logger = LoggerFactory.getLogger(AbstractFlagEncoder.class);
    protected final Set<String> intendedValues = new HashSet<>(5);
    // order is important
    protected final List<String> restrictions = new ArrayList<>(5);
    protected final Set<String> restrictedValues = new HashSet<>(5);
    protected final Set<String> ferries = new HashSet<>(5);
    protected final Set<String> oneways = new HashSet<>(5);
    // http://wiki.openstreetmap.org/wiki/Mapfeatures#Barrier
    protected final Set<String> absoluteBarriers = new HashSet<>(5);
    protected final Set<String> potentialBarriers = new HashSet<>(5);
    protected final int speedBits;
    protected final double speedFactor;
    private final int maxTurnCosts;
    private long encoderBit;
    protected BooleanEncodedValue accessEnc;
    protected BooleanEncodedValue roundaboutEnc;
    protected DecimalEncodedValue avgSpeedEnc;
    // This value determines the maximal possible speed of any road regardless of the maxspeed value
    // lower values allow more compact representation of the routing graph
    protected int maxPossibleSpeed;
    private boolean blockByDefault = true;
    private boolean blockFords = true;
    private boolean registered;
    protected EncodedValueLookup encodedValueLookup;
    private ConditionalTagInspector conditionalTagInspector;
    protected FerrySpeedCalculator ferrySpeedCalc;

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

        setConditionalTagInspector(new ConditionalOSMTagInspector(Collections.singletonList(dateRangeParser),
                restrictions, restrictedValues, intendedValues, false));
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

    /**
     * Should potential barriers block when no access limits are given?
     */
    protected void blockBarriersByDefault(boolean blockByDefault) {
        this.blockByDefault = blockByDefault;
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
        // absolute barriers always block
        if (node.hasTag("barrier", absoluteBarriers))
            return encoderBit;

        // movable barriers block if they are not marked as passable
        if (node.hasTag("barrier", potentialBarriers)) {
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

    /**
     * Most use cases do not require this method. Will still keep it accessible so that one can disable it
     * until the averageSpeedEncodedValue is moved out of the FlagEncoder.
     *
     * @Deprecated
     */
    protected void setSpeed(boolean reverse, IntsRef edgeFlags, double speed) {
        if (!isValidSpeed(speed))
            throw new IllegalArgumentException("Speed cannot be negative or NaN: " + speed + ", flags:" + BitUtil.LITTLE.toBitString(edgeFlags));

        if (speed < speedFactor / 2) {
            avgSpeedEnc.setDecimal(reverse, edgeFlags, 0);
            accessEnc.setBool(reverse, edgeFlags, false);
            return;
        }

        if (speed > getMaxSpeed())
            speed = getMaxSpeed();

        avgSpeedEnc.setDecimal(reverse, edgeFlags, speed);
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
}
