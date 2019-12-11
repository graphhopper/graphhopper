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
import com.graphhopper.routing.profiles.*;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.*;
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
    /* restriction definitions where order is important */
    protected final List<String> restrictions = new ArrayList<>(5);
    protected final Set<String> intendedValues = new HashSet<>(5);
    protected final Set<String> restrictedValues = new HashSet<>(5);
    protected final Set<String> ferries = new HashSet<>(5);
    protected final Set<String> oneways = new HashSet<>(5);
    // http://wiki.openstreetmap.org/wiki/Mapfeatures#Barrier
    protected final Set<String> absoluteBarriers = new HashSet<>(5);
    protected final Set<String> potentialBarriers = new HashSet<>(5);
    protected final int speedBits;
    protected final double speedFactor;
    protected double speedDefault;
    private final int maxTurnCosts;
    private long encoderBit;
    protected BooleanEncodedValue accessEnc;
    protected BooleanEncodedValue roundaboutEnc;
    protected DecimalEncodedValue avgSpeedEnc;
    // This value determines the maximal possible speed of any road regardless of the maxspeed value
    // lower values allow more compact representation of the routing graph
    protected int maxPossibleSpeed;
    /* Edge Flag Encoder fields */
    private long nodeBitMask;
    private long relBitMask;
    private boolean blockByDefault = true;
    private boolean blockFords = true;
    private boolean registered;
    protected EncodedValueLookup encodedValueLookup;

    // Speeds from CarFlagEncoder
    protected static final double UNKNOWN_DURATION_FERRY_SPEED = 5;
    protected static final double SHORT_TRIP_FERRY_SPEED = 20;
    protected static final double LONG_TRIP_FERRY_SPEED = 30;

    private ConditionalTagInspector conditionalTagInspector;

    public AbstractFlagEncoder(PMap properties) {
        throw new RuntimeException("This method must be overridden in derived classes");
    }

    public AbstractFlagEncoder(String propertiesStr) {
        this(new PMap(propertiesStr));
    }

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
    }

    protected void init(DateRangeParser dateRangeParser) {
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
    public void setBlockByDefault(boolean blockByDefault) {
        this.blockByDefault = blockByDefault;
    }

    public boolean isBlockFords() {
        return blockFords;
    }

    public void setBlockFords(boolean blockFords) {
        this.blockFords = blockFords;
    }

    public ConditionalTagInspector getConditionalTagInspector() {
        return conditionalTagInspector;
    }

    /**
     * Defines the bits for the node flags, which are currently used for barriers only.
     * <p>
     *
     * @return incremented shift value pointing behind the last used bit
     */
    public int defineNodeBits(int index, int shift) {
        return shift;
    }

    /**
     * Defines bits used for edge flags used for access, speed etc.
     *
     * @return incremented shift value pointing behind the last used bit
     */
    public void createEncodedValues(List<EncodedValue> registerNewEncodedValue, String prefix, int index) {
        // define the first 2 speedBits in flags for routing
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

    /**
     * Sets default flags with specified access.
     */
    protected void flagsDefault(IntsRef edgeFlags, boolean forward, boolean backward) {
        if (forward)
            avgSpeedEnc.setDecimal(false, edgeFlags, speedDefault);
        if (backward && avgSpeedEnc.isStoreTwoDirections())
            avgSpeedEnc.setDecimal(true, edgeFlags, speedDefault);
        accessEnc.setBool(false, edgeFlags, forward);
        accessEnc.setBool(true, edgeFlags, backward);
    }

    @Override
    public double getMaxSpeed() {
        return maxPossibleSpeed;
    }

    /**
     * @return -1 if no maxspeed found
     */
    protected double getMaxSpeed(ReaderWay way) {
        double maxSpeed = parseSpeed(way.getTag("maxspeed"));
        double fwdSpeed = parseSpeed(way.getTag("maxspeed:forward"));
        if (fwdSpeed >= 0 && (maxSpeed < 0 || fwdSpeed < maxSpeed))
            maxSpeed = fwdSpeed;

        double backSpeed = parseSpeed(way.getTag("maxspeed:backward"));
        if (backSpeed >= 0 && (maxSpeed < 0 || backSpeed < maxSpeed))
            maxSpeed = backSpeed;

        return maxSpeed;
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

    /**
     * Second parsing step. Invoked after splitting the edges. Currently used to offer a hook to
     * calculate precise speed values based on elevation data stored in the specified edge.
     */
    public void applyWayTags(ReaderWay way, EdgeIteratorState edge) {
    }

    /**
     * Special handling for ferry ways.
     */
    protected double getFerrySpeed(ReaderWay way) {
        long duration = 0;

        try {
            // During the reader process we have converted the duration value into a artificial tag called "duration:seconds".
            duration = Long.parseLong(way.getTag("duration:seconds"));
        } catch (Exception ex) {
        }
        // seconds to hours
        double durationInHours = duration / 60d / 60d;
        // Check if our graphhopper specific artificially created estimated_distance way tag is present
        Number estimatedLength = way.getTag("estimated_distance", null);
        if (durationInHours > 0)
            try {
                if (estimatedLength != null) {
                    double estimatedLengthInKm = estimatedLength.doubleValue() / 1000;
                    // If duration AND distance is available we can calculate the speed more precisely
                    // and set both speed to the same value. Factor 1.4 slower because of waiting time!
                    double calculatedTripSpeed = estimatedLengthInKm / durationInHours / 1.4;
                    // Plausibility check especially for the case of wrongly used PxM format with the intention to
                    // specify the duration in minutes, but actually using months
                    if (calculatedTripSpeed > 0.01d) {
                        if (calculatedTripSpeed > getMaxSpeed()) {
                            return getMaxSpeed();
                        }
                        // If the speed is lower than the speed we can store, we have to set it to the minSpeed, but > 0
                        if (Math.round(calculatedTripSpeed) < speedFactor / 2) {
                            return speedFactor / 2;
                        }

                        return Math.round(calculatedTripSpeed);
                    } else {
                        long lastId = way.getNodes().isEmpty() ? -1 : way.getNodes().get(way.getNodes().size() - 1);
                        long firstId = way.getNodes().isEmpty() ? -1 : way.getNodes().get(0);
                        if (firstId != lastId)
                            logger.warn("Unrealistic long duration ignored in way with way ID=" + way.getId() + " : Duration tag value="
                                    + way.getTag("duration") + " (=" + Math.round(duration / 60d) + " minutes)");
                        durationInHours = 0;
                    }
                }
            } catch (Exception ex) {
            }

        if (durationInHours == 0) {
            if (estimatedLength != null && estimatedLength.doubleValue() <= 300)
                return speedFactor / 2;
            // unknown speed -> put penalty on ferry transport
            return UNKNOWN_DURATION_FERRY_SPEED;
        } else if (durationInHours > 1) {
            // lengthy ferries should be faster than short trip ferry
            return LONG_TRIP_FERRY_SPEED;
        } else {
            return SHORT_TRIP_FERRY_SPEED;
        }
    }

    void setNodeBitMask(int usedBits, int shift) {
        nodeBitMask = (1L << usedBits) - 1;
        nodeBitMask <<= shift;
    }

    long getNodeBitMask() {
        return nodeBitMask;
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
        if (speed < 0 || Double.isNaN(speed))
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

    double getSpeed(IntsRef edgeFlags) {
        return getSpeed(false, edgeFlags);
    }

    double getSpeed(boolean reverse, IntsRef edgeFlags) {
        double speedVal = avgSpeedEnc.getDecimal(reverse, edgeFlags);
        if (speedVal < 0)
            throw new IllegalStateException("Speed was negative!? " + speedVal);

        return speedVal;
    }

    /**
     * @param way   needed to retrieve tags
     * @param speed speed guessed e.g. from the road type or other tags
     * @return The assumed speed.
     */
    protected double applyMaxSpeed(ReaderWay way, double speed) {
        double maxSpeed = getMaxSpeed(way);
        // We obey speed limits
        if (maxSpeed >= 0) {
            // We assume that the average speed is 90% of the allowed maximum
            return maxSpeed * 0.9;
        }
        return speed;
    }

    protected String getPropertiesString() {
        return "speed_factor=" + speedFactor + "|speed_bits=" + speedBits + "|turn_costs=" + (maxTurnCosts > 0);
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
    public <T extends Enum> EnumEncodedValue<T> getEnumEncodedValue(String key, Class<T> enumType) {
        return encodedValueLookup.getEnumEncodedValue(key, enumType);
    }

    public void setEncodedValueLookup(EncodedValueLookup encodedValueLookup) {
        this.encodedValueLookup = encodedValueLookup;
    }

    @Override
    public boolean supports(Class<?> feature) {
        if (TurnWeighting.class.isAssignableFrom(feature))
            return maxTurnCosts > 0;

        return false;
    }

    @Override
    public boolean hasEncodedValue(String key) {
        return encodedValueLookup.hasEncodedValue(key);
    }
}
