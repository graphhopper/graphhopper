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
public abstract class AbstractBikeFlagEncoderTester {
    protected BikeCommonFlagEncoder encoder;
    protected TagParserManager encodingManager;
    protected BooleanEncodedValue roundaboutEnc;
    protected DecimalEncodedValue priorityEnc;
    protected DecimalEncodedValue avgSpeedEnc;

    @BeforeEach
    public void setUp() {
        encodingManager = TagParserManager.create(encoder = createBikeEncoder());
        roundaboutEnc = encodingManager.getBooleanEncodedValue(Roundabout.KEY);
        priorityEnc = encodingManager.getDecimalEncodedValue(EncodingManager.getKey(encoder, "priority"));
        avgSpeedEnc = encoder.getAverageSpeedEnc();
    }

    protected abstract BikeCommonFlagEncoder createBikeEncoder();

    protected void assertPriority(int expectedPrio, ReaderWay way) {
        IntsRef relFlags = encodingManager.handleRelationTags(new ReaderRelation(0), encodingManager.createRelationFlags());
        IntsRef edgeFlags = encodingManager.handleWayTags(way, relFlags);
        DecimalEncodedValue enc = encodingManager.getDecimalEncodedValue(EncodingManager.getKey(encoder.toString(), "priority"));
        assertEquals(PriorityCode.getValue(expectedPrio), enc.getDecimal(false, edgeFlags), 0.01);
    }

    protected void assertPriorityAndSpeed(int expectedPrio, double expectedSpeed, ReaderWay way) {
        assertPriorityAndSpeed(expectedPrio, expectedSpeed, way, new ReaderRelation(0));
    }

    protected void assertPriorityAndSpeed(int expectedPrio, double expectedSpeed, ReaderWay way, ReaderRelation rel) {
        IntsRef relFlags = encodingManager.handleRelationTags(rel, encodingManager.createRelationFlags());
        IntsRef edgeFlags = encodingManager.handleWayTags(way, relFlags);
        DecimalEncodedValue enc = encodingManager.getDecimalEncodedValue(EncodingManager.getKey(encoder.toString(), "priority"));
        assertEquals(PriorityCode.getValue(expectedPrio), enc.getDecimal(false, edgeFlags), 0.01);
        assertEquals(expectedSpeed, encoder.getAverageSpeedEnc().getDecimal(false, edgeFlags), 0.1);
        assertEquals(expectedSpeed, encoder.getAverageSpeedEnc().getDecimal(true, edgeFlags), 0.1);
    }

    protected double getSpeedFromFlags(ReaderWay way) {
        IntsRef relFlags = encodingManager.createRelationFlags();
        IntsRef flags = encodingManager.handleWayTags(way, relFlags);
        return avgSpeedEnc.getDecimal(false, flags);
    }

    @Test
    public void testAccess() {
        ReaderWay way = new ReaderWay(1);

        way.setTag("highway", "motorway");
        assertTrue(encoder.getAccess(way).canSkip());

        way.setTag("highway", "motorway");
        way.setTag("bicycle", "yes");
        assertTrue(encoder.getAccess(way).isWay());

        way.setTag("highway", "footway");
        assertTrue(encoder.getAccess(way).isWay());

        way.setTag("bicycle", "no");
        assertTrue(encoder.getAccess(way).canSkip());

        way.setTag("highway", "footway");
        way.setTag("bicycle", "yes");
        assertTrue(encoder.getAccess(way).isWay());

        way.setTag("highway", "pedestrian");
        way.setTag("bicycle", "no");
        assertTrue(encoder.getAccess(way).canSkip());

        way.setTag("highway", "pedestrian");
        way.setTag("bicycle", "yes");
        assertTrue(encoder.getAccess(way).isWay());

        way.setTag("bicycle", "yes");
        way.setTag("highway", "cycleway");
        assertTrue(encoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "path");
        assertTrue(encoder.getAccess(way).isWay());

        way.setTag("highway", "path");
        way.setTag("bicycle", "yes");
        assertTrue(encoder.getAccess(way).isWay());
        way.clearTags();

        way.setTag("highway", "track");
        way.setTag("bicycle", "yes");
        assertTrue(encoder.getAccess(way).isWay());
        way.clearTags();

        way.setTag("highway", "track");
        assertTrue(encoder.getAccess(way).isWay());

        way.setTag("mtb", "yes");
        assertTrue(encoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("foot", "official");
        assertTrue(encoder.getAccess(way).isWay());

        way.setTag("bicycle", "official");
        assertTrue(encoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "no");
        assertTrue(encoder.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("motorroad", "yes");
        assertTrue(encoder.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("ford", "yes");
        assertTrue(encoder.getAccess(way).canSkip());
        way.setTag("bicycle", "yes");
        assertTrue(encoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "secondary");
        way.setTag("access", "no");
        assertTrue(encoder.getAccess(way).canSkip());
        way.setTag("bicycle", "dismount");
        assertTrue(encoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "secondary");
        way.setTag("vehicle", "no");
        assertTrue(encoder.getAccess(way).canSkip());
        way.setTag("bicycle", "dismount");
        assertTrue(encoder.getAccess(way).isWay());


        way.clearTags();
        way.setTag("highway", "cycleway");
        way.setTag("cycleway", "track");
        way.setTag("railway", "abandoned");
        assertTrue(encoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "platform");
        assertTrue(encoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "platform");
        way.setTag("bicycle", "dismount");
        assertTrue(encoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "platform");
        way.setTag("bicycle", "no");
        assertTrue(encoder.getAccess(way).canSkip());

        DateFormat simpleDateFormat = Helper.createFormatter("yyyy MMM dd");

        way.clearTags();
        way.setTag("highway", "road");
        way.setTag("bicycle:conditional", "no @ (" + simpleDateFormat.format(new Date().getTime()) + ")");
        assertTrue(encoder.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "road");
        way.setTag("access", "no");
        way.setTag("bicycle:conditional", "yes @ (" + simpleDateFormat.format(new Date().getTime()) + ")");
        assertTrue(encoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("vehicle", "forestry");
        assertTrue(encoder.getAccess(way).canSkip());
        way.setTag("bicycle", "yes");
        assertTrue(encoder.getAccess(way).isWay());
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
        IntsRef relFlags = encodingManager.handleRelationTags(rel2, encodingManager.handleRelationTags(rel, encodingManager.createRelationFlags()));
        IntsRef edgeFlags = encodingManager.handleWayTags(way, relFlags);
        EnumEncodedValue<RouteNetwork> enc = encodingManager.getEnumEncodedValue(RouteNetwork.key("bike"), RouteNetwork.class);
        assertEquals(RouteNetwork.REGIONAL, enc.getEnum(false, edgeFlags));
    }

    @Test
    public void testTramStations() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("railway", "rail");
        assertTrue(encoder.getAccess(way).isWay());

        way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("railway", "station");
        assertTrue(encoder.getAccess(way).isWay());

        way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("railway", "station");
        way.setTag("bicycle", "yes");
        assertTrue(encoder.getAccess(way).isWay());

        way.setTag("bicycle", "no");
        assertTrue(encoder.getAccess(way).canSkip());

        way = new ReaderWay(1);
        way.setTag("railway", "platform");
        IntsRef flags = encoder.handleWayTags(encodingManager.createEdgeFlags(), way);
        assertNotEquals(true, flags.isEmpty());

        way = new ReaderWay(1);
        way.setTag("highway", "track");
        way.setTag("railway", "platform");
        flags = encoder.handleWayTags(encodingManager.createEdgeFlags(), way);
        assertNotEquals(true, flags.isEmpty());

        way = new ReaderWay(1);
        way.setTag("highway", "track");
        way.setTag("railway", "platform");
        way.setTag("bicycle", "no");

        flags = encoder.handleWayTags(encodingManager.createEdgeFlags(), way);
        assertTrue(flags.isEmpty());
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
        assertPriority(AVOID_MORE.getValue(), osmWay);

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
        assertPriorityAndSpeed(SLIGHT_AVOID.getValue(), 6, way);
    }

    @Test
    public void testSacScale() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "service");
        way.setTag("sac_scale", "hiking");
        // allow
        assertTrue(encoder.getAccess(way).isWay());

        way.setTag("sac_scale", "alpine_hiking");
        assertTrue(encoder.getAccess(way).canSkip());
    }

    @Test
    public void testReduceToMaxSpeed() {
        ReaderWay way = new ReaderWay(12);
        way.setTag("maxspeed", "90");
        assertEquals(12, encoder.applyMaxSpeed(way, 12), 1e-2);
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
        IntsRef edgeFlags = encoder.handleWayTags(encodingManager.createEdgeFlags(), osmWay);
        DecimalEncodedValue priorityEnc = encodingManager.getDecimalEncodedValue(EncodingManager.getKey(encoder, "priority"));
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
        assertTrue(encoder.isBarrier(node));
    }

    @Test
    public void testNoBike() {
        ReaderNode node = new ReaderNode(1, -1, -1);
        node.setTag("bicycle", "no");
        assertTrue(encoder.isBarrier(node));
    }

    @Test
    public void testBarrierAccess() {
        // by default allow access through the gate for bike & foot!
        ReaderNode node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        // no barrier!
        assertFalse(encoder.isBarrier(node));

        node.setTag("bicycle", "yes");
        // no barrier!
        assertFalse(encoder.isBarrier(node));

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        node.setTag("access", "no");
        // barrier!
        assertTrue(encoder.isBarrier(node));

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        node.setTag("access", "yes");
        node.setTag("bicycle", "no");
        // barrier!
        assertTrue(encoder.isBarrier(node));

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        node.setTag("access", "no");
        node.setTag("foot", "yes");
        // barrier!
        assertTrue(encoder.isBarrier(node));

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        node.setTag("access", "no");
        node.setTag("bicycle", "yes");
        // no barrier!
        assertFalse(encoder.isBarrier(node));
    }

    @Test
    public void testBarrierAccessFord() {
        ReaderNode node = new ReaderNode(1, -1, -1);
        node.setTag("ford", "yes");
        // barrier!
        assertTrue(encoder.isBarrier(node));

        node.setTag("bicycle", "yes");
        // no barrier!
        assertFalse(encoder.isBarrier(node));
    }

    @Test
    public void testFerries() {
        ReaderWay way = new ReaderWay(1);

        way.clearTags();
        way.setTag("route", "ferry");
        assertTrue(encoder.getAccess(way).isFerry());
        way.setTag("bicycle", "no");
        assertFalse(encoder.getAccess(way).isFerry());

        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("foot", "yes");
        assertFalse(encoder.getAccess(way).isFerry());

        // #1122
        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("bicycle", "yes");
        way.setTag("access", "private");
        assertTrue(encoder.getAccess(way).canSkip());

        // #1562, test if ferry route with bicycle
        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("bicycle", "designated");
        assertTrue(encoder.getAccess(way).isFerry());

        way.setTag("bicycle", "official");
        assertTrue(encoder.getAccess(way).isFerry());

        way.setTag("bicycle", "permissive");
        assertTrue(encoder.getAccess(way).isFerry());

        way.setTag("foot", "yes");
        assertTrue(encoder.getAccess(way).isFerry());

        way.setTag("bicycle", "no");
        assertTrue(encoder.getAccess(way).canSkip());

        way.setTag("bicycle", "designated");
        way.setTag("access", "private");
        assertTrue(encoder.getAccess(way).canSkip());

        // test if when foot is set is invalid
        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("foot", "yes");
        assertTrue(encoder.getAccess(way).canSkip());
    }
}
