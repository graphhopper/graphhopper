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
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Translation;
import org.junit.Before;
import org.junit.Test;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

import static com.graphhopper.routing.util.PriorityCode.*;
import static com.graphhopper.util.TranslationMapTest.SINGLETON;
import static org.junit.Assert.*;

/**
 * @author Peter Karich
 * @author ratrun
 */
public abstract class AbstractBikeFlagEncoderTester {
    protected BikeCommonFlagEncoder encoder;
    protected EncodingManager encodingManager;
    protected BooleanEncodedValue roundaboutEnc;
    protected DecimalEncodedValue priorityEnc;
    protected DecimalEncodedValue avSpeedEnc;

    @Before
    public void setUp() {
        encodingManager = EncodingManager.create(encoder = createBikeEncoder());
        roundaboutEnc = encodingManager.getBooleanEncodedValue(EncodingManager.ROUNDABOUT);
        priorityEnc = encodingManager.getDecimalEncodedValue(EncodingManager.getKey(encoder, "priority"));
        avSpeedEnc = encoder.getAverageSpeedEnc();
    }

    protected abstract BikeCommonFlagEncoder createBikeEncoder();

    protected void assertPriority(int expectedPrio, ReaderWay way) {
        assertPriority(expectedPrio, way, 0);
    }

    protected void assertPriority(int expectedPrio, ReaderWay way, long relationFlags) {
        assertEquals(expectedPrio, encoder.handlePriority(way, 18, (int) encoder.relationCodeEncoder.getValue(relationFlags)));
    }

    protected double getSpeedFromFlags(ReaderWay way) {
        IntsRef flags = encoder.handleWayTags(encodingManager.createEdgeFlags(), way, EncodingManager.Access.WAY, 0);
        return avSpeedEnc.getDecimal(false, flags);
    }

    protected String getWayTypeFromFlags(ReaderWay way) {
        return getWayTypeFromFlags(way, 0);
    }

    protected String getWayTypeFromFlags(ReaderWay way, long relationFlags) {
        IntsRef flags = encoder.handleWayTags(encodingManager.createEdgeFlags(), way, EncodingManager.Access.WAY, relationFlags);
        Translation enMap = SINGLETON.getWithFallBack(Locale.UK);
        return encoder.getAnnotation(flags, enMap).getMessage();
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
        IntsRef flags = encoder.handleWayTags(encodingManager.createEdgeFlags(), way, encoder.getAccess(way), 0);
        assertNotEquals(0, flags.ints[0]);

        way = new ReaderWay(1);
        way.setTag("highway", "track");
        way.setTag("railway", "platform");
        flags = encoder.handleWayTags(encodingManager.createEdgeFlags(), way, encoder.getAccess(way), 0);
        assertNotEquals(0, flags.ints[0]);

        way = new ReaderWay(1);
        way.setTag("highway", "track");
        way.setTag("railway", "platform");
        way.setTag("bicycle", "no");

        flags = encoder.handleWayTags(encodingManager.createEdgeFlags(), way, encoder.getAccess(way), 0);
        assertEquals(0, flags.ints[0]);
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
        assertPriority(AVOID_AT_ALL_COSTS.getValue(), osmWay);

        osmWay.setTag("bicycle", "designated");
        assertPriority(PREFER.getValue(), osmWay);
    }

    @Test
    public void testTram() {
        ReaderWay way = new ReaderWay(1);
        // very dangerous
        way.setTag("highway", "secondary");
        way.setTag("railway", "tram");
        assertPriority(AVOID_AT_ALL_COSTS.getValue(), way);

        // should be safe now
        way.setTag("bicycle", "designated");
        assertPriority(PREFER.getValue(), way);
    }

    @Test
    public void testHandleCommonWayTags() {
        ReaderWay way = new ReaderWay(1);
        String wayType;

        way.setTag("highway", "steps");
        wayType = getWayTypeFromFlags(way);
        assertEquals("get off the bike", wayType);

        way.setTag("highway", "footway");
        wayType = getWayTypeFromFlags(way);
        assertEquals("get off the bike", wayType);

        way.setTag("highway", "footway");
        way.setTag("surface", "pebblestone");
        wayType = getWayTypeFromFlags(way);
        assertEquals("get off the bike", wayType);

        way.setTag("highway", "residential");
        wayType = getWayTypeFromFlags(way);
        assertEquals("", wayType);
        assertPriority(PREFER.getValue(), way);

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("bicycle", "yes");
        wayType = getWayTypeFromFlags(way);
        assertEquals("", wayType);

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("bicycle", "designated");
        wayType = getWayTypeFromFlags(way);
        assertEquals("", wayType);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("bicycle", "designated");
        wayType = getWayTypeFromFlags(way);
        assertEquals("cycleway, unpaved", wayType);

        way.clearTags();
        way.setTag("highway", "cycleway");
        wayType = getWayTypeFromFlags(way);
        assertEquals("cycleway", wayType);
        assertPriority(VERY_NICE.getValue(), way);

        way.setTag("surface", "grass");
        wayType = getWayTypeFromFlags(way);
        assertEquals("cycleway, unpaved", wayType);

        way.setTag("surface", "asphalt");
        wayType = getWayTypeFromFlags(way);
        assertEquals("cycleway", wayType);
        assertPriority(VERY_NICE.getValue(), way);

        way.setTag("highway", "footway");
        way.setTag("bicycle", "yes");
        way.setTag("surface", "grass");
        wayType = getWayTypeFromFlags(way);
        assertEquals("small way, unpaved", wayType);

        way.setTag("bicycle", "designated");
        wayType = getWayTypeFromFlags(way);
        assertEquals("cycleway, unpaved", wayType);

        way.clearTags();
        way.setTag("highway", "footway");
        way.setTag("bicycle", "yes");
        way.setTag("surface", "grass");
        wayType = getWayTypeFromFlags(way);
        assertEquals("small way, unpaved", wayType);

        way.clearTags();
        way.setTag("railway", "platform");
        wayType = getWayTypeFromFlags(way);
        assertEquals("get off the bike", wayType);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("railway", "platform");
        wayType = getWayTypeFromFlags(way);
        assertEquals("get off the bike, unpaved", wayType);

        way.clearTags();
        way.setTag("highway", "secondary");
        way.setTag("bicycle", "dismount");
        wayType = getWayTypeFromFlags(way);
        assertEquals("get off the bike", wayType);

    }

    @Test
    public void testService() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "service");
        assertEquals(14, encoder.getSpeed(way));
        assertPriority(PREFER.getValue(), way);

        way.setTag("service", "parking_aisle");
        assertEquals(6, encoder.getSpeed(way));
        assertPriority(AVOID_IF_POSSIBLE.getValue(), way);
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
        IntsRef edgeFlags = encodingManager.createEdgeFlags();
        avSpeedEnc.setDecimal(false, edgeFlags, encoder.applyMaxSpeed(osmWay, 49));
        assertEquals(30, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);
        assertPriority(PREFER.getValue(), osmWay);
    }

    @Test
    public void testHandleWayTagsCallsHandlePriority() {
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "cycleway");
        IntsRef edgeFlags = encoder.handleWayTags(encodingManager.createEdgeFlags(), osmWay, EncodingManager.Access.WAY, 0);
        DecimalEncodedValue priorityEnc = encodingManager.getDecimalEncodedValue(EncodingManager.getKey(encoder, "priority"));
        assertEquals((double) VERY_NICE.getValue() / BEST.getValue(), priorityEnc.getDecimal(false, edgeFlags), 1e-3);
    }

    @Test
    public void testAvoidMotorway() {
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "motorway");
        osmWay.setTag("bicycle", "yes");
        assertPriority(REACH_DEST.getValue(), osmWay);
    }

    @Test
    public void testPriority() {
        IntsRef flags = encodingManager.createEdgeFlags();
        encoder.priorityWayEncoder.setDecimal(false, flags, PriorityCode.getFactor(PriorityCode.BEST.getValue()));
        DecimalEncodedValue priorityEnc = encodingManager.getDecimalEncodedValue(EncodingManager.getKey(encoder, "priority"));
        assertEquals(1, priorityEnc.getDecimal(false, flags), 1e-3);

        flags = encodingManager.createEdgeFlags();
        encoder.priorityWayEncoder.setDecimal(false, flags, PriorityCode.getFactor(PriorityCode.AVOID_IF_POSSIBLE.getValue()));
        assertEquals(3d / 7d, priorityEnc.getDecimal(false, flags), 1e-3);
    }

    @Test
    public void testBarrierAccess() {
        // by default allow access through the gate for bike & foot!
        ReaderNode node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        // no barrier!
        assertTrue(encoder.handleNodeTags(node) == 0);

        node.setTag("bicycle", "yes");
        // no barrier!
        assertTrue(encoder.handleNodeTags(node) == 0);

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        node.setTag("access", "no");
        // barrier!
        assertTrue(encoder.handleNodeTags(node) > 0);

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        node.setTag("access", "yes");
        node.setTag("bicycle", "no");
        // barrier!
        assertTrue(encoder.handleNodeTags(node) > 0);

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        node.setTag("access", "no");
        node.setTag("foot", "yes");
        // barrier!
        assertTrue(encoder.handleNodeTags(node) > 0);

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        node.setTag("access", "no");
        node.setTag("bicycle", "yes");
        // no barrier!
        assertTrue(encoder.handleNodeTags(node) == 0);
    }

    @Test
    public void testBarrierAccessFord() {
        ReaderNode node = new ReaderNode(1, -1, -1);
        node.setTag("ford", "yes");
        // barrier!
        assertTrue(encoder.handleNodeTags(node) > 0);

        node.setTag("bicycle", "yes");
        // no barrier!
        assertTrue(encoder.handleNodeTags(node) == 0);
    }

    @Test
    public void testFerries(){
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
