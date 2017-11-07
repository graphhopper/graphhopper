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
import com.graphhopper.reader.ReaderRelation;
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

import static com.graphhopper.routing.profiles.TagParserFactory.ROUNDABOUT;

/**
 * Abstract class which handles flag decoding and encoding. Every encoder should be registered to a
 * EncodingManager to be usable. If you want the full long to be stored you need to enable this in
 * the GraphHopperStorage.
 * <p>
 *
 * @author Peter Karich
 * @author Nop
 * @see EncodingManager
 */
public abstract class AbstractFlagEncoder implements FlagEncoder, TurnCostEncoder {
    private final static Logger LOGGER = LoggerFactory.getLogger(AbstractFlagEncoder.class);
    /* restriction definitions where order is important */
    protected final List<String> restrictions = new ArrayList<String>(5);
    protected final Set<String> intendedValues = new HashSet<String>(5);
    protected final Set<String> restrictedValues = new HashSet<String>(5);
    protected final Set<String> ferries = new HashSet<String>(5);
    protected final Set<String> oneways = new HashSet<String>(5);
    // http://wiki.openstreetmap.org/wiki/Mapfeatures#Barrier
    protected final Set<String> absoluteBarriers = new HashSet<String>(5);
    protected final Set<String> potentialBarriers = new HashSet<String>(5);
    protected final int speedBits;
    protected final double speedFactor;
    protected DecimalEncodedValue averageSpeedEnc;

    // This value determines the maximal possible speed of any road regardless the maxspeed value
    // lower values allow more compact representation of the routing graph
    protected double maxPossibleSpeed;

    protected BooleanEncodedValue accessEnc;
    protected BooleanEncodedValue roundaboutEnc;
    private long encoderBitMask;

    private final int maxTurnCosts;
    private long turnRestrictionBit;
    private EncodedValue08 turnCostEncoder;

    private long nodeBitMask;
    // private long wayBitMask;
    private long relBitMask;

    private boolean blockByDefault = true;
    private boolean blockFords = true;
    private boolean registered;

    private ConditionalTagInspector conditionalTagInspector;
    private EncodedValueLookup encodedValueLookup;

    public AbstractFlagEncoder(PMap properties) {
        throw new RuntimeException("This method must be overridden in derived classes");
    }

    public AbstractFlagEncoder(String propertiesStr) {
        this(new PMap(propertiesStr));
    }

    /**
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

    // should be called as last method in constructor, move out of the flag encoder somehow
    protected void init() {
        // we should move 'OSM to object' logic into the DataReader like OSMReader, but this is a major task as we need to convert OSM format into kind of a standard/generic format
        conditionalTagInspector = new ConditionalOSMTagInspector(DateRangeParser.createCalendar(), restrictions, restrictedValues, intendedValues);
    }

    @Override
    public boolean isRegistered() {
        return registered;
    }

    public void setRegistered(boolean registered) {
        this.registered = registered;
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

    protected void setConditionalTagInspector(ConditionalTagInspector conditionalTagInspector) {
        this.conditionalTagInspector = conditionalTagInspector;
    }

    /**
     * Defines the bits for the node flags, which are currently used for barriers only.
     *
     * @return incremented shift value pointing behind the last used bit
     */
    public int defineNodeBits(int index, int shift) {
        return shift;
    }

    /**
     * Returns a collection of all required TagParsers. If the TagParser is null the creation is done elsewhere.
     * The EncodedValues of all TagParsers can be potentially shared between FlagEncoder but then the order when adding to
     * the EncodingManager will be important.
     */
    public abstract Map<String, TagParser> createTagParsers(String prefix);

    /**
     * Defines bits used for edge flags used for access restrictions and more.
     */
    public void initEncodedValues(final String prefix, int index) {
        if (isRegistered())
            throw new IllegalStateException("Cannot call initEncodedValues after registration");

        averageSpeedEnc = getDecimalEncodedValue(prefix + "average_speed");
        if (maxPossibleSpeed > ((1 << speedBits) - 1) * speedFactor)
            throw new IllegalStateException("maxPossibleSpeed " + maxPossibleSpeed + " is too big for EncodedValue " + prefix + "average_speed");

        accessEnc = getBooleanEncodedValue(prefix + "access");
        roundaboutEnc = getBooleanEncodedValue(ROUNDABOUT);
        encoderBitMask = 1L << index;
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
    public StringEncodedValue getStringEncodedValue(String key) {
        return encodedValueLookup.getStringEncodedValue(key);
    }

    /**
     * Defines the bits which are used for relation flags.
     *
     * @return incremented shift value pointing behind the last used bit
     */
    public int defineRelationBits(int index, int shift) {
        return shift;
    }

    /**
     * Analyze the properties of a relation and create the routing flags for the second read step.
     * In the pre-parsing step this method will be called to determine the useful relation tags.
     */
    public abstract long handleRelationTags(ReaderRelation relation, long oldRelationFlags);

    /**
     * Decide whether a way is routable for a given mode of travel. This skips some ways before
     * handleWayTags is called.
     *
     * @return the encoded value to indicate if this encoder allows travel or not.
     */
    public abstract EncodingManager.Access getAccess(ReaderWay way);

    /**
     * Analyze properties of a way and create the routing flags. This method is called in the second
     * parsing step.
     */
    public abstract IntsRef handleWayTags(IntsRef ints, ReaderWay way, EncodingManager.Access acceptWay, long relationFlags);

    /**
     * Parse tags on nodes. Node tags can add to speed (like traffic_signals) where the value is
     * strict negative or blocks access (like a barrier), then the value is strict positive.This
     * method is called in the second parsing step.
     */
    public long handleNodeTags(ReaderNode node) {
        // absolute barriers always block
        if (node.hasTag("barrier", absoluteBarriers))
            return encoderBitMask;

        // movable barriers block if they are not marked as passable
        if (node.hasTag("barrier", potentialBarriers)) {
            boolean locked = false;
            if (node.hasTag("locked", "yes"))
                locked = true;

            for (String res : restrictions) {
                if (!locked && node.hasTag(res, intendedValues))
                    return 0;

                if (node.hasTag(res, restrictedValues))
                    return encoderBitMask;
            }

            if (blockByDefault)
                return encoderBitMask;
        }

        // In case explicit flag ford=no, don't block
        if (blockFords
                && (node.hasTag("highway", "ford") || node.hasTag("ford"))
                && !node.hasTag(restrictions, intendedValues)
                && !node.hasTag("ford", "no")) {
            return encoderBitMask;
        }

        return 0;
    }

    @Override
    public InstructionAnnotation getAnnotation(IntsRef ints, Translation tr) {
        return InstructionAnnotation.EMPTY;
    }

    @Override
    public IntsRef setSpeed(IntsRef ints, double speed) {
        if (speed < 0 || Double.isNaN(speed))
            throw new IllegalArgumentException("Speed cannot be negative or NaN: " + speed
                    + ", flags:" + BitUtil.LITTLE.toBitString(ints));

        if (speed < speedFactor / 2) {
            averageSpeedEnc.setDecimal(false, ints, 0);
            accessEnc.setBool(false, ints, false);
            // accessEnc.setBool(true, ints, false);
        }

        if (speed > getMaxSpeed())
            speed = getMaxSpeed();

        averageSpeedEnc.setDecimal(false, ints, speed);
        return ints;
    }

    @Override
    public double getSpeed(IntsRef ints) {
        double speedVal = averageSpeedEnc.getDecimal(false, ints);
        if (speedVal < 0)
            throw new IllegalStateException("Speed was negative!? " + speedVal);

        return speedVal;
    }

    @Override
    public IntsRef setReverseSpeed(IntsRef ints, double speed) {
        return setSpeed(ints, speed);
    }

    @Override
    public double getReverseSpeed(IntsRef ints) {
        return getSpeed(ints);
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
    public String getPrefix() {
        return toString() + ".";
    }

    @Override
    public int hashCode() {
        return 61 * 7 + this.toString().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;

        final AbstractFlagEncoder other = (AbstractFlagEncoder) obj;
        return this.toString().equals(other.toString());
    }

    /**
     * @return the speed in km/h
     */
    public static double parseSpeed(String str) {
        if (Helper.isEmpty(str))
            return -1;

        // on some German autobahns and a very few other places
        if ("none".equals(str))
            return 140;

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
    protected double getFerrySpeed(ReaderWay way, double unknownSpeed, double shortTripsSpeed, double longTripsSpeed) {
        long duration = 0;
        try {
            // During the reader process we have converted the duration value into a artificial tag called "duration:seconds".
            duration = Long.parseLong(way.getTag("duration:seconds"));
        } catch (Exception ex) {
        }
        // seconds to hours
        double durationInHours = duration / 60d / 60d;
        if (durationInHours > 0)
            try {
                // Check if our graphhopper specific artificially created estimated_distance way tag is present
                Number estimatedLength = way.getTag("estimated_distance", null);
                if (estimatedLength != null) {
                    // to km
                    double val = estimatedLength.doubleValue() / 1000;
                    // If duration AND distance is available we can calculate the speed more precisely
                    // and set both speed to the same value. Factor 1.4 slower because of waiting time!
                    double calculatedTripSpeed = val / durationInHours / 1.4;
                    // Plausibility check especially for the case of wrongly used PxM format with the intention to
                    // specify the duration in minutes, but actually using months
                    if (calculatedTripSpeed > 0.01d) {
                        // If we have a very short ferry with an average lower compared to what we can encode 
                        // then we need to avoid setting it as otherwise the edge would not be found at all any more.
                        if (Math.round(calculatedTripSpeed) > speedFactor / 2) {
                            shortTripsSpeed = Math.round(calculatedTripSpeed);
                            if (shortTripsSpeed > getMaxSpeed())
                                shortTripsSpeed = getMaxSpeed();
                            longTripsSpeed = shortTripsSpeed;
                        } else {
                            // Now we set to the lowest possible still accessible speed. 
                            shortTripsSpeed = speedFactor / 2;
                        }
                    } else {
                        long lastId = way.getNodes().isEmpty() ? -1 : way.getNodes().get(way.getNodes().size() - 1);
                        long firstId = way.getNodes().isEmpty() ? -1 : way.getNodes().get(0);
                        if (firstId != lastId)
                            LOGGER.warn("Unrealistic long duration ignored in way with way ID=" + way.getId() + " : Duration tag value="
                                    + way.getTag("duration") + " (=" + Math.round(duration / 60d) + " minutes)");
                        durationInHours = 0;
                    }
                }
            } catch (Exception ex) {
            }

        if (durationInHours == 0) {
            // unknown speed -> put penalty on ferry transport
            return unknownSpeed;
        } else if (durationInHours > 1) {
            // lengthy ferries should be faster than short trip ferry
            return longTripsSpeed;
        } else {
            return shortTripsSpeed;
        }
    }

    void setRelBitMask(int usedBits, int shift) {
        relBitMask = (1L << usedBits) - 1;
        relBitMask <<= shift;
    }

    long getRelBitMask() {
        return relBitMask;
    }

    void setNodeBitMask(int usedBits, int shift) {
        nodeBitMask = (1L << usedBits) - 1;
        nodeBitMask <<= shift;
    }

    long getNodeBitMask() {
        return nodeBitMask;
    }

    /**
     * Defines the bits reserved for storing turn restriction and turn cost
     * <p>
     *
     * @param shift bit offset for the first bit used by this encoder
     * @return incremented shift value pointing behind the last used bit
     */
    public int defineTurnBits(int index, int shift) {
        if (maxTurnCosts == 0)
            return shift;

            // optimization for turn restrictions only
        else if (maxTurnCosts == 1) {
            turnRestrictionBit = 1L << shift;
            return shift + 1;
        }

        int turnBits = Helper.countBitValue(maxTurnCosts);
        turnCostEncoder = new EncodedValue08("TurnCost", shift, turnBits, 1, 0, maxTurnCosts) {
            // override to avoid expensive Math.round
            @Override
            public final long getValue(long flags) {
                // find value
                flags &= mask;
                flags >>>= shift;
                return flags;
            }
        };
        return shift + turnBits;
    }

    @Override
    public boolean isTurnRestricted(long flags) {
        if (maxTurnCosts == 0)
            return false;

        else if (maxTurnCosts == 1)
            return (flags & turnRestrictionBit) != 0;

        return turnCostEncoder.getValue(flags) == maxTurnCosts;
    }

    @Override
    public double getTurnCost(long flags) {
        if (maxTurnCosts == 0)
            return 0;

        else if (maxTurnCosts == 1)
            return ((flags & turnRestrictionBit) == 0) ? 0 : Double.POSITIVE_INFINITY;

        long cost = turnCostEncoder.getValue(flags);
        if (cost == maxTurnCosts)
            return Double.POSITIVE_INFINITY;

        return cost;
    }

    @Override
    public long getTurnFlags(boolean restricted, double costs) {
        if (maxTurnCosts == 0)
            return 0;

        else if (maxTurnCosts == 1) {
            if (costs != 0)
                throw new IllegalArgumentException("Only restrictions are supported");

            return restricted ? turnRestrictionBit : 0;
        }

        if (restricted) {
            if (costs != 0 || Double.isInfinite(costs))
                throw new IllegalArgumentException("Restricted turn can only have infinite costs (or use 0)");
        } else if (costs >= maxTurnCosts)
            throw new IllegalArgumentException("Cost is too high. Or specifiy restricted == true");

        if (costs < 0)
            throw new IllegalArgumentException("Turn costs cannot be negative");

        if (costs >= maxTurnCosts || restricted)
            costs = maxTurnCosts;
        return turnCostEncoder.setValue(0L, (int) costs);
    }

    @Override
    public BooleanEncodedValue getAccessEncodedValue() {
        return accessEnc;
    }

    /**
     * @param way:   needed to retrieve tags
     * @param speed: speed guessed e.g. from the road type or other tags
     * @return The assumed speed.
     */
    protected double applyMaxSpeed(ReaderWay way, double speed) {
        double maxSpeed = getMaxSpeed(way);
        // We obay speed limits
        if (maxSpeed >= 0) {
            // We assume that the average speed is 90% of the allowed maximum
            return maxSpeed * 0.9;
        }
        return speed;
    }

    protected String getPropertiesString() {
        // TODO NOW how to store and restore properties of every encoded value?
        return "speed_factor=" + speedFactor + "|speed_bits=" + speedBits + "|turn_costs=" + (maxTurnCosts > 0);
    }

    @Override
    public boolean supports(Class<?> feature) {
        if (TurnWeighting.class.isAssignableFrom(feature))
            return maxTurnCosts > 0;

        return false;
    }

    public void setEncodedValueLookup(EncodedValueLookup encodedValueLookup) {
        this.encodedValueLookup = encodedValueLookup;
    }

    @Override
    public boolean supports(String key) {
        return encodedValueLookup.supports(key);
    }
}
