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
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.text.DateFormat;
import java.util.Date;

import static com.graphhopper.routing.util.PriorityCode.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 * @author ratrun
 */
public abstract class AbstractBikeTagParserTester {
    protected EncodingManager encodingManager;
    protected BikeCommonTagParser parser;
    protected OSMParsers osmParsers;
    protected BooleanEncodedValue roundaboutEnc;
    protected DecimalEncodedValue priorityEnc;
    protected DecimalEncodedValue avgSpeedEnc;
    protected BooleanEncodedValue accessEnc;

    @BeforeEach
    public void setUp() {
        encodingManager = createEncodingManager();
        parser = createBikeTagParser(encodingManager, new PMap("block_fords=true"));
        osmParsers = createOSMParsers(parser, encodingManager);
        roundaboutEnc = encodingManager.getBooleanEncodedValue(Roundabout.KEY);
        priorityEnc = encodingManager.getDecimalEncodedValue(VehiclePriority.key(parser.getName()));
        avgSpeedEnc = parser.getAverageSpeedEnc();
        accessEnc = parser.getAccessEnc();
    }

    protected abstract EncodingManager createEncodingManager();

    protected abstract BikeCommonTagParser createBikeTagParser(EncodedValueLookup lookup, PMap pMap);

    protected abstract OSMParsers createOSMParsers(BikeCommonTagParser parser, EncodedValueLookup lookup);

    protected void assertPriority(int expectedPrio, ReaderWay way) {
        IntsRef relFlags = osmParsers.handleRelationTags(new ReaderRelation(0), osmParsers.createRelationFlags());
        IntsRef edgeFlags = encodingManager.createEdgeFlags();
        osmParsers.handleWayTags(edgeFlags, way, relFlags);
        assertEquals(PriorityCode.getValue(expectedPrio), priorityEnc.getDecimal(false, edgeFlags), 0.01);
    }

    protected void assertPriorityAndSpeed(int expectedPrio, double expectedSpeed, ReaderWay way) {
        assertPriorityAndSpeed(expectedPrio, expectedSpeed, way, new ReaderRelation(0));
    }

    protected void assertPriorityAndSpeed(int expectedPrio, double expectedSpeed, ReaderWay way, ReaderRelation rel) {
        IntsRef relFlags = osmParsers.handleRelationTags(rel, osmParsers.createRelationFlags());
        IntsRef edgeFlags = encodingManager.createEdgeFlags();
        osmParsers.handleWayTags(edgeFlags, way, relFlags);
        DecimalEncodedValue enc = encodingManager.getDecimalEncodedValue(VehiclePriority.key(parser.toString()));
        assertEquals(PriorityCode.getValue(expectedPrio), enc.getDecimal(false, edgeFlags), 0.01);
        assertEquals(expectedSpeed, parser.getAverageSpeedEnc().getDecimal(false, edgeFlags), 0.1);
        assertEquals(expectedSpeed, parser.getAverageSpeedEnc().getDecimal(true, edgeFlags), 0.1);
    }

    protected double getSpeedFromFlags(ReaderWay way) {
        IntsRef relFlags = osmParsers.createRelationFlags();
        IntsRef flags = encodingManager.createEdgeFlags();
        osmParsers.handleWayTags(flags, way, relFlags);
        return avgSpeedEnc.getDecimal(false, flags);
    }

    @Test
    public void testAccess() {
        ReaderWay way = new ReaderWay(1);

        way.setTag("highway", "motorway");
        assertTrue(parser.getAccess(way).canSkip());

        way.setTag("highway", "motorway");
        way.setTag("bicycle", "yes");
        assertTrue(parser.getAccess(way).isWay());

        way.setTag("highway", "footway");
        assertTrue(parser.getAccess(way).isWay());

        way.setTag("bicycle", "no");
        assertTrue(parser.getAccess(way).canSkip());

        way.setTag("highway", "footway");
        way.setTag("bicycle", "yes");
        assertTrue(parser.getAccess(way).isWay());

        way.setTag("highway", "pedestrian");
        way.setTag("bicycle", "no");
        assertTrue(parser.getAccess(way).canSkip());

        way.setTag("highway", "pedestrian");
        way.setTag("bicycle", "yes");
        assertTrue(parser.getAccess(way).isWay());

        way.setTag("bicycle", "yes");
        way.setTag("highway", "cycleway");
        assertTrue(parser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "path");
        assertTrue(parser.getAccess(way).isWay());

        way.setTag("highway", "path");
        way.setTag("bicycle", "yes");
        assertTrue(parser.getAccess(way).isWay());
        way.clearTags();

        way.setTag("highway", "track");
        way.setTag("bicycle", "yes");
        assertTrue(parser.getAccess(way).isWay());
        way.clearTags();

        way.setTag("highway", "track");
        assertTrue(parser.getAccess(way).isWay());

        way.setTag("mtb", "yes");
        assertTrue(parser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("foot", "official");
        assertTrue(parser.getAccess(way).isWay());

        way.setTag("bicycle", "official");
        assertTrue(parser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "no");
        assertTrue(parser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("motorroad", "yes");
        assertTrue(parser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("ford", "yes");
        assertTrue(parser.getAccess(way).canSkip());
        way.setTag("bicycle", "yes");
        assertTrue(parser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "secondary");
        way.setTag("access", "no");
        assertTrue(parser.getAccess(way).canSkip());
        way.setTag("bicycle", "dismount");
        assertTrue(parser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "secondary");
        way.setTag("vehicle", "no");
        assertTrue(parser.getAccess(way).canSkip());
        way.setTag("bicycle", "dismount");
        assertTrue(parser.getAccess(way).isWay());


        way.clearTags();
        way.setTag("highway", "cycleway");
        way.setTag("cycleway", "track");
        way.setTag("railway", "abandoned");
        assertTrue(parser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "platform");
        assertTrue(parser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "platform");
        way.setTag("bicycle", "dismount");
        assertTrue(parser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "platform");
        way.setTag("bicycle", "no");
        assertTrue(parser.getAccess(way).canSkip());

        DateFormat simpleDateFormat = Helper.createFormatter("yyyy MMM dd");

        way.clearTags();
        way.setTag("highway", "road");
        way.setTag("bicycle:conditional", "no @ (" + simpleDateFormat.format(new Date().getTime()) + ")");
        assertTrue(parser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "road");
        way.setTag("access", "no");
        way.setTag("bicycle:conditional", "yes @ (" + simpleDateFormat.format(new Date().getTime()) + ")");
        assertTrue(parser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("vehicle", "forestry");
        assertTrue(parser.getAccess(way).canSkip());
        way.setTag("bicycle", "yes");
        assertTrue(parser.getAccess(way).isWay());
    }

    @Test
    public void testRelation() {
        ReaderWay way = new ReaderWay(1);

        way.setTag("highway", "track");
        way.setTag("bicycle", "yes");
        way.setTag("foot", "yes");
        way.setTag("motor_vehicle", "agricultural");
        way.setTag("surface", "gravel");
        way.setTag("tracktype", "grade3");

        ReaderRelation rel = new ReaderRelation(0);
        rel.setTag("type", "route");
        rel.setTag("network", "rcn");
        rel.setTag("route", "bicycle");

        ReaderRelation rel2 = new ReaderRelation(1);
        rel2.setTag("type", "route");
        rel2.setTag("network", "lcn");
        rel2.setTag("route", "bicycle");

        // two relation tags => we currently cannot store a list, so pick the lower ordinal 'regional'
        // Example https://www.openstreetmap.org/way/213492914 => two hike 84544, 2768803 and two bike relations 3162932, 5254650
        IntsRef relFlags = osmParsers.handleRelationTags(rel2, osmParsers.handleRelationTags(rel, osmParsers.createRelationFlags()));
        IntsRef edgeFlags = encodingManager.createEdgeFlags();
        osmParsers.handleWayTags(edgeFlags, way, relFlags);
        EnumEncodedValue<RouteNetwork> enc = encodingManager.getEnumEncodedValue(RouteNetwork.key("bike"), RouteNetwork.class);
        assertEquals(RouteNetwork.REGIONAL, enc.getEnum(false, edgeFlags));
    }

    @Test
    public void testTramStations() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("railway", "rail");
        assertTrue(parser.getAccess(way).isWay());

        way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("railway", "station");
        assertTrue(parser.getAccess(way).isWay());

        way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("railway", "station");
        way.setTag("bicycle", "yes");
        assertTrue(parser.getAccess(way).isWay());

        way.setTag("bicycle", "no");
        assertTrue(parser.getAccess(way).canSkip());

        way = new ReaderWay(1);
        way.setTag("railway", "platform");
        IntsRef edgeFlags = encodingManager.createEdgeFlags();
        parser.handleWayTags(edgeFlags, way);
        assertEquals(4.0, avgSpeedEnc.getDecimal(false, edgeFlags));
        assertTrue(accessEnc.getBool(false, edgeFlags));

        way = new ReaderWay(1);
        way.setTag("highway", "track");
        way.setTag("railway", "platform");
        edgeFlags = encodingManager.createEdgeFlags();
        parser.handleWayTags(edgeFlags, way);
        // todonow: why is this 18 for MTB, 2 for racingbike and 12 for normal bike?
        assertEquals(12.0, avgSpeedEnc.getDecimal(false, edgeFlags));
        assertTrue(accessEnc.getBool(false, edgeFlags));

        way = new ReaderWay(1);
        way.setTag("highway", "track");
        way.setTag("railway", "platform");
        way.setTag("bicycle", "no");

        edgeFlags = encodingManager.createEdgeFlags();
        parser.handleWayTags(edgeFlags, way);
        assertEquals(0.0, avgSpeedEnc.getDecimal(false, edgeFlags));
        assertFalse(accessEnc.getBool(false, edgeFlags));
    }

    @Test
    public void testAvoidTunnel() {
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "residential");
        assertPriority(PREFER.getValue(), osmWay);

        osmWay.setTag("tunnel", "yes");
        assertPriority(UNCHANGED.getValue(), osmWay);

        osmWay.setTag("highway", "secondary");
        osmWay.setTag("tunnel", "yes");
        assertPriority(BAD.getValue(), osmWay);

        osmWay.setTag("bicycle", "designated");
        assertPriority(PREFER.getValue(), osmWay);
    }

    @Test
    public void testTram() {
        ReaderWay way = new ReaderWay(1);
        // very dangerous
        way.setTag("highway", "secondary");
        way.setTag("railway", "tram");
        assertPriority(AVOID_MORE.getValue(), way);

        // should be safe now
        way.setTag("bicycle", "designated");
        assertPriority(PREFER.getValue(), way);
    }

    @Test
    public void testService() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "service");
        assertPriorityAndSpeed(PREFER.getValue(), 14, way);

        way.setTag("service", "parking_aisle");
        assertPriorityAndSpeed(SLIGHT_AVOID.getValue(), 4, way);
    }

    @Test
    public void testSacScale() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "service");
        way.setTag("sac_scale", "hiking");
        // allow
        assertTrue(parser.getAccess(way).isWay());

        way.setTag("sac_scale", "alpine_hiking");
        assertTrue(parser.getAccess(way).canSkip());
    }

    @Test
    public void testReduceToMaxSpeed() {
        ReaderWay way = new ReaderWay(12);
        way.setTag("maxspeed", "90");
        assertEquals(12, parser.applyMaxSpeed(way, 12, true), 1e-2);
    }

    @Test
    public void testPreferenceForSlowSpeed() {
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "tertiary");
        assertPriority(PREFER.getValue(), osmWay);
    }

    @Test
    public void testHandleWayTagsCallsHandlePriority() {
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "cycleway");
        IntsRef edgeFlags = encodingManager.createEdgeFlags();
        parser.handleWayTags(edgeFlags, osmWay);
        DecimalEncodedValue priorityEnc = encodingManager.getDecimalEncodedValue(VehiclePriority.key(parser.getName()));
        assertEquals(PriorityCode.getValue(VERY_NICE.getValue()), priorityEnc.getDecimal(false, edgeFlags), 1e-3);
    }

    @Test
    public void testAvoidMotorway() {
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "motorway");
        osmWay.setTag("bicycle", "yes");
        assertPriority(AVOID.getValue(), osmWay);
    }

    @Test
    public void testLockedGate() {
        ReaderNode node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        node.setTag("locked", "yes");
        assertTrue(parser.isBarrier(node));
    }

    @Test
    public void testNoBike() {
        ReaderNode node = new ReaderNode(1, -1, -1);
        node.setTag("bicycle", "no");
        assertTrue(parser.isBarrier(node));
    }

    @Test
    public void testBarrierAccess() {
        // by default allow access through the gate for bike & foot!
        ReaderNode node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        // no barrier!
        assertFalse(parser.isBarrier(node));

        node.setTag("bicycle", "yes");
        // no barrier!
        assertFalse(parser.isBarrier(node));

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        node.setTag("access", "no");
        // barrier!
        assertTrue(parser.isBarrier(node));

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        node.setTag("access", "yes");
        node.setTag("bicycle", "no");
        // barrier!
        assertTrue(parser.isBarrier(node));

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        node.setTag("access", "no");
        node.setTag("foot", "yes");
        // barrier!
        assertTrue(parser.isBarrier(node));

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        node.setTag("access", "no");
        node.setTag("bicycle", "yes");
        // no barrier!
        assertFalse(parser.isBarrier(node));
    }

    @Test
    public void testBarrierAccessFord() {
        ReaderNode node = new ReaderNode(1, -1, -1);
        node.setTag("ford", "yes");
        // barrier!
        assertTrue(parser.isBarrier(node));

        node.setTag("bicycle", "yes");
        // no barrier!
        assertFalse(parser.isBarrier(node));
    }

    @Test
    public void testFerries() {
        ReaderWay way = new ReaderWay(1);

        way.clearTags();
        way.setTag("route", "ferry");
        assertTrue(parser.getAccess(way).isFerry());
        way.setTag("bicycle", "no");
        assertFalse(parser.getAccess(way).isFerry());

        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("foot", "yes");
        assertFalse(parser.getAccess(way).isFerry());

        // #1122
        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("bicycle", "yes");
        way.setTag("access", "private");
        assertTrue(parser.getAccess(way).canSkip());

        // #1562, test if ferry route with bicycle
        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("bicycle", "designated");
        assertTrue(parser.getAccess(way).isFerry());

        way.setTag("bicycle", "official");
        assertTrue(parser.getAccess(way).isFerry());

        way.setTag("bicycle", "permissive");
        assertTrue(parser.getAccess(way).isFerry());

        way.setTag("foot", "yes");
        assertTrue(parser.getAccess(way).isFerry());

        way.setTag("bicycle", "no");
        assertTrue(parser.getAccess(way).canSkip());

        way.setTag("bicycle", "designated");
        way.setTag("access", "private");
        assertTrue(parser.getAccess(way).canSkip());

        // test if when foot is set is invalid
        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("foot", "yes");
        assertTrue(parser.getAccess(way).canSkip());
    }

    @Test
    void privateAndFords() {
        // defaults: do not block fords, block private
        BikeCommonTagParser bike = createBikeTagParser(encodingManager, new PMap());
        assertFalse(bike.isBlockFords());
        assertTrue(bike.restrictedValues.contains("private"));
        assertFalse(bike.intendedValues.contains("private"));

        // block fords, unblock private
        bike = createBikeTagParser(encodingManager, new PMap("block_fords=true|block_private=false"));
        assertTrue(bike.isBlockFords());
        assertFalse(bike.restrictedValues.contains("private"));
        assertTrue(bike.intendedValues.contains("private"));
    }
}
