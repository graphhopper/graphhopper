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

import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.conditional.ConditionalOSMTagInspector;
import com.graphhopper.reader.osm.conditional.ConditionalTagInspector;
import com.graphhopper.reader.osm.conditional.DateRangeParser;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.VehicleAccess;
import com.graphhopper.routing.util.parsers.OSMRoadAccessParser;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.routing.util.parsers.helpers.OSMValueExtractor;
import com.graphhopper.storage.IntsRef;

import java.util.*;

import static java.util.Collections.emptyMap;

/**
 * Abstract class which handles flag decoding and encoding. Every encoder should be registered to a
 * EncodingManager to be usable.
 *
 * @author Peter Karich
 * @author Nop
 * @see EncodingManager
 */
public abstract class VehicleTagParser implements TagParser {
    protected final Set<String> intendedValues = new HashSet<>(5);
    // order is important
    protected final List<String> restrictions = new ArrayList<>(5);
    protected final Set<String> restrictedValues = new HashSet<>(5);
    protected final Set<String> ferries = new HashSet<>(5);
    protected final Set<String> oneways = new HashSet<>(5);
    // http://wiki.openstreetmap.org/wiki/Mapfeatures#Barrier
    protected final Set<String> barriers = new HashSet<>(5);
    protected final BooleanEncodedValue accessEnc;
    protected final DecimalEncodedValue avgSpeedEnc;
    protected final BooleanEncodedValue roundaboutEnc;
    // This value determines the maximal possible speed of any road regardless of the maxspeed value
    // lower values allow more compact representation of the routing graph
    protected final double maxPossibleSpeed;
    private boolean blockFords = true;
    private ConditionalTagInspector conditionalTagInspector;
    protected final FerrySpeedCalculator ferrySpeedCalc;

    protected VehicleTagParser(BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc, BooleanEncodedValue roundaboutEnc,
                               TransportationMode transportationMode, double maxPossibleSpeed) {
        this.maxPossibleSpeed = maxPossibleSpeed;

        this.accessEnc = accessEnc;
        this.avgSpeedEnc = speedEnc;
        this.roundaboutEnc = roundaboutEnc;

        oneways.add("yes");
        oneways.add("true");
        oneways.add("1");
        oneways.add("-1");

        ferries.add("shuttle_train");
        ferries.add("ferry");

        ferrySpeedCalc = new FerrySpeedCalculator(speedEnc.getSmallestNonZeroValue(), maxPossibleSpeed, 5);
        restrictions.addAll(OSMRoadAccessParser.toOSMRestrictions(transportationMode));
    }

    public VehicleTagParser init(DateRangeParser dateRangeParser) {
        setConditionalTagInspector(new ConditionalOSMTagInspector(Collections.singletonList(dateRangeParser),
                restrictions, restrictedValues, intendedValues, false));
        return this;
    }

    protected void setConditionalTagInspector(ConditionalTagInspector inspector) {
        conditionalTagInspector = inspector;
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

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, IntsRef relationFlags) {
        edgeFlags = handleWayTags(edgeFlags, way);
        if (!edgeFlags.isEmpty()) {
            Map<String, Object> nodeTags = way.getTag("node_tags", emptyMap());
            handleNodeTags(edgeFlags, nodeTags);
        }
        return edgeFlags;
    }

    /**
     * Analyze properties of a way and create the edge flags. This method is called in the second
     * parsing step.
     */
    protected abstract IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way);

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

    public double getMaxSpeed() {
        return maxPossibleSpeed;
    }

    /**
     * @return {@link Double#NaN} if no maxspeed found
     */
    protected static double getMaxSpeed(ReaderWay way, boolean bwd) {
        double maxSpeed = OSMValueExtractor.stringToKmh(way.getTag("maxspeed"));
        double directedMaxSpeed = OSMValueExtractor.stringToKmh(way.getTag(bwd ? "maxspeed:backward" : "maxspeed:forward"));
        if (isValidSpeed(directedMaxSpeed) && (!isValidSpeed(maxSpeed) || directedMaxSpeed < maxSpeed))
            maxSpeed = directedMaxSpeed;
        return maxSpeed;
    }

    /**
     * @return <i>true</i> if the given speed is not {@link Double#NaN}
     */
    protected static boolean isValidSpeed(double speed) {
        return !Double.isNaN(speed);
    }

    public final DecimalEncodedValue getAverageSpeedEnc() {
        return avgSpeedEnc;
    }

    public final BooleanEncodedValue getAccessEnc() {
        return accessEnc;
    }

    protected void setSpeed(boolean reverse, IntsRef edgeFlags, double speed) {
        // special case when speed is non-zero but would be "rounded down" to 0 due to the low precision of the EncodedValue
        if (speed > 0.1 && speed < avgSpeedEnc.getSmallestNonZeroValue())
            speed = avgSpeedEnc.getSmallestNonZeroValue();
        if (speed < avgSpeedEnc.getSmallestNonZeroValue()) {
            avgSpeedEnc.setDecimal(reverse, edgeFlags, 0);
            accessEnc.setBool(reverse, edgeFlags, false);
        } else {
            avgSpeedEnc.setDecimal(reverse, edgeFlags, Math.min(speed, getMaxSpeed()));
        }
    }

    public final List<String> getRestrictions() {
        return restrictions;
    }

    public final String getName() {
        String name = accessEnc.getName().replaceAll("_access", "");
        // safety check for the time being
        String expectedKey = VehicleAccess.key(name);
        if (!accessEnc.getName().equals(expectedKey))
            throw new IllegalStateException("Expected access key '" + expectedKey + "', but got: " + accessEnc.getName());
        return name;
    }

    @Override
    public String toString() {
        return getName();
    }
}
