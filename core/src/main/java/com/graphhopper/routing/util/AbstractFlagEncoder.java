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
import com.graphhopper.util.EdgeIteratorState;

import java.util.*;

import static com.graphhopper.routing.util.EncodingManager.getKey;

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
    private final String name;
    protected final Set<String> intendedValues = new HashSet<>(5);
    // order is important
    protected final List<String> restrictions = new ArrayList<>(5);
    protected final Set<String> restrictedValues = new HashSet<>(5);
    protected final Set<String> ferries = new HashSet<>(5);
    protected final Set<String> oneways = new HashSet<>(5);
    // http://wiki.openstreetmap.org/wiki/Mapfeatures#Barrier
    protected final Set<String> barriers = new HashSet<>(5);
    protected final int speedBits;
    protected final double speedFactor;
    private final int maxTurnCosts;
    protected final BooleanEncodedValue accessEnc;
    protected final DecimalEncodedValue avgSpeedEnc;
    protected BooleanEncodedValue roundaboutEnc;
    // This value determines the maximal possible speed of any road regardless of the maxspeed value
    // lower values allow more compact representation of the routing graph
    protected double maxPossibleSpeed;
    private boolean blockFords = true;
    private boolean registered;
    protected EncodedValueLookup encodedValueLookup;
    private ConditionalTagInspector conditionalTagInspector;
    protected FerrySpeedCalculator ferrySpeedCalc;

    /**
     * @param speedBits    specify the number of bits used for speed
     * @param speedFactor  specify the factor to multiply the stored value (can be used to increase
     *                     or decrease accuracy of speed value)
     * @param maxTurnCosts specify the maximum value used for turn costs, if this value is reached a
     *                     turn is forbidden and results in costs of positive infinity.
     */
    protected AbstractFlagEncoder(String name, int speedBits, double speedFactor, boolean speedTwoDirections, int maxTurnCosts) {
        this.name = name;
        this.maxTurnCosts = maxTurnCosts <= 0 ? 0 : maxTurnCosts;
        this.speedBits = speedBits;
        this.speedFactor = speedFactor;

        this.accessEnc = new SimpleBooleanEncodedValue(getKey(name, "access"), true);
        this.avgSpeedEnc = new DecimalEncodedValueImpl(getKey(name, "average_speed"), speedBits, speedFactor, speedTwoDirections);

        oneways.add("yes");
        oneways.add("true");
        oneways.add("1");
        oneways.add("-1");

        ferries.add("shuttle_train");
        ferries.add("ferry");

        restrictions.addAll(OSMRoadAccessParser.toOSMRestrictions(getTransportationMode()));
    }

    protected void init(DateRangeParser dateRangeParser) {
        if (registered)
            throw new IllegalStateException("You must not register a FlagEncoder (" + this + ") twice or for two EncodingManagers!");
        registered = true;

        ferrySpeedCalc = new FerrySpeedCalculator(speedFactor / 2, maxPossibleSpeed, 5);
        setConditionalTagInspector(new ConditionalOSMTagInspector(Collections.singletonList(dateRangeParser),
                restrictions, restrictedValues, intendedValues, false));
    }

    protected void setConditionalTagInspector(ConditionalTagInspector inspector) {
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

    /**
     * Defines bits used for edge flags used for access, speed etc.
     */
    public void createEncodedValues(List<EncodedValue> registerNewEncodedValue) {
        registerNewEncodedValue.add(accessEnc);
        registerNewEncodedValue.add(avgSpeedEnc);
        roundaboutEnc = getBooleanEncodedValue(Roundabout.KEY);
    }

    /**
     * Analyze properties of a way and create the edge flags. This method is called in the second
     * parsing step.
     */
    public abstract IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way);

    /**
     * Updates the given edge flags based on node tags
     */
    public IntsRef handleNodeTags(IntsRef edgeFlags, Map<String, Object> nodeTags) {
        if (!nodeTags.isEmpty()) {
            // for now we just create a dummy reader node, because our encoders do not make use of the coordinates anyway
            ReaderNode readerNode = new ReaderNode(0, 0, 0, nodeTags);
            // block access for barriers
            if (isBarrier(readerNode)) {
                BooleanEncodedValue accessEnc = getAccessEnc();
                accessEnc.setBool(false, edgeFlags, false);
                accessEnc.setBool(true, edgeFlags, false);
            }
        }
        return edgeFlags;
    }

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
     * @return true if the given OSM node blocks access for this vehicle, false otherwise
     */
    public boolean isBarrier(ReaderNode node) {
        // note that this method will be only called for certain nodes as defined by OSMReader!
        String firstValue = node.getFirstPriorityTag(restrictions);
        if (restrictedValues.contains(firstValue) || node.hasTag("locked", "yes"))
            return true;
        else if (intendedValues.contains(firstValue))
            return false;
        else if (node.hasTag("barrier", barriers))
            return true;
        else
            return blockFords && node.hasTag("ford", "yes");
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

    /**
     * Second parsing step. Invoked after splitting the edges. Currently used to offer a hook to
     * calculate precise speed values based on elevation data stored in the specified edge.
     */
    public void applyWayTags(ReaderWay way, EdgeIteratorState edge) {
    }

    public final DecimalEncodedValue getAverageSpeedEnc() {
        if (avgSpeedEnc == null)
            throw new NullPointerException("FlagEncoder " + getName() + " not yet initialized");
        return avgSpeedEnc;
    }

    public final BooleanEncodedValue getAccessEnc() {
        if (accessEnc == null)
            throw new NullPointerException("FlagEncoder " + getName() + " not yet initialized");
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

    public final String getName() {
        return name;
    }

    @Override
    public String toString() {
        return getName();
    }
}
