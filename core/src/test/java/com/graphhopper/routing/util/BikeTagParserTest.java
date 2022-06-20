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
import com.graphhopper.reader.osm.conditional.DateRangeParser;
import com.graphhopper.routing.ev.BikeNetwork;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.RouteNetwork;
import com.graphhopper.routing.ev.Smoothness;
import com.graphhopper.routing.util.parsers.OSMBikeNetworkTagParser;
import com.graphhopper.routing.util.parsers.OSMSmoothnessParser;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.Test;

import static com.graphhopper.routing.util.BikeCommonTagParser.PUSHING_SECTION_SPEED;
import static com.graphhopper.routing.util.PriorityCode.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 * @author ratrun
 */
public class BikeTagParserTest extends AbstractBikeTagParserTester {

    @Override
    protected EncodingManager createEncodingManager() {
        return EncodingManager.create("bike");
    }

    @Override
    protected BikeCommonTagParser createBikeTagParser(EncodedValueLookup lookup, PMap pMap) {
        BikeTagParser parser = new BikeTagParser(lookup, pMap);
        parser.init(new DateRangeParser());
        return parser;
    }

    @Override
    protected OSMParsers createOSMParsers(BikeCommonTagParser parser, EncodedValueLookup lookup) {
        return new OSMParsers()
                .addRelationTagParser(relConfig -> new OSMBikeNetworkTagParser(lookup.getEnumEncodedValue(BikeNetwork.KEY, RouteNetwork.class), relConfig))
                .addWayTagParser(new OSMSmoothnessParser(lookup.getEnumEncodedValue(Smoothness.KEY, Smoothness.class)))
                .addVehicleTagParser(parser);
    }

    @Test
    public void testSpeedAndPriority() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        assertPriorityAndSpeed(AVOID.getValue(), 18, way);

        way.setTag("scenic", "yes");
        assertPriorityAndSpeed(SLIGHT_AVOID.getValue(), 18, way);

        // Pushing section: this is fine as we obey the law!
        way.clearTags();
        way.setTag("highway", "footway");
        assertPriorityAndSpeed(SLIGHT_AVOID.getValue(), PUSHING_SECTION_SPEED, way);

        // Use pushing section irrespective of the pavement
        way.setTag("surface", "paved");
        assertPriorityAndSpeed(SLIGHT_AVOID.getValue(), PUSHING_SECTION_SPEED, way);

        way.clearTags();
        way.setTag("highway", "path");
        assertPriorityAndSpeed(SLIGHT_AVOID.getValue(), PUSHING_SECTION_SPEED, way);

        way.clearTags();
        way.setTag("highway", "secondary");
        way.setTag("bicycle", "dismount");
        assertPriorityAndSpeed(AVOID.getValue(), PUSHING_SECTION_SPEED, way);

        way.clearTags();
        way.setTag("highway", "footway");
        way.setTag("bicycle", "yes");
        assertPriorityAndSpeed(PREFER.getValue(), 10, way);
        way.setTag("segregated", "no");
        assertPriorityAndSpeed(PREFER.getValue(), 10, way);
        way.setTag("segregated", "yes");
        assertPriorityAndSpeed(PREFER.getValue(), 18, way);

        way.clearTags();
        way.setTag("highway", "footway");
        way.setTag("surface", "paved");
        way.setTag("bicycle", "yes");
        assertPriorityAndSpeed(PREFER.getValue(), 10, way);
        way.setTag("surface", "cobblestone");
        assertPriorityAndSpeed(PREFER.getValue(), 8, way);
        way.setTag("segregated", "yes");
        way.setTag("surface", "paved");
        assertPriorityAndSpeed(PREFER.getValue(), 18, way);

        way.clearTags();
        way.setTag("highway", "platform");
        way.setTag("surface", "paved");
        way.setTag("bicycle", "yes");
        assertPriorityAndSpeed(PREFER.getValue(), 10, way);
        way.setTag("segregated", "yes");
        assertPriorityAndSpeed(PREFER.getValue(), 18, way);

        way.clearTags();
        way.setTag("highway", "cycleway");
        assertPriorityAndSpeed(VERY_NICE.getValue(), 18, way);
        int cyclewaySpeed = 18;
        way.setTag("foot", "yes");
        way.setTag("segregated", "yes");
        assertPriorityAndSpeed(VERY_NICE.getValue(), cyclewaySpeed, way);
        way.setTag("segregated", "no");
        assertPriorityAndSpeed(PREFER.getValue(), cyclewaySpeed, way);

        // Make sure that "highway=cycleway" and "highway=path" with "bicycle=designated" give the same result
        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("bicycle", "designated");
        // Assume foot=no for designated in absence of a foot tag
        assertPriorityAndSpeed(VERY_NICE.getValue(), cyclewaySpeed, way);
        way.setTag("foot", "yes");
        assertPriorityAndSpeed(PREFER.getValue(), cyclewaySpeed, way);

        way.setTag("foot", "no");
        assertPriorityAndSpeed(VERY_NICE.getValue(), cyclewaySpeed, way);

        way.setTag("segregated", "yes");
        assertPriorityAndSpeed(VERY_NICE.getValue(), cyclewaySpeed, way);

        way.setTag("segregated", "no");
        assertPriorityAndSpeed(VERY_NICE.getValue(), cyclewaySpeed, way);

        way.setTag("bicycle", "yes");
        assertPriorityAndSpeed(PREFER.getValue(), 10, way);

        way.setTag("segregated", "yes");
        assertPriorityAndSpeed(PREFER.getValue(), cyclewaySpeed, way);

        way.setTag("surface", "unpaved");
        assertPriorityAndSpeed(PREFER.getValue(), 14, way);

        way.setTag("surface", "paved");
        assertPriorityAndSpeed(PREFER.getValue(), 18, way);

        way.clearTags();
        way.setTag("highway", "path");
        assertPriorityAndSpeed(SLIGHT_AVOID.getValue(), PUSHING_SECTION_SPEED, way);

        // use pushing section
        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("surface", "paved");
        assertPriorityAndSpeed(SLIGHT_AVOID.getValue(), PUSHING_SECTION_SPEED, way);

        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("surface", "ground");
        assertPriorityAndSpeed(SLIGHT_AVOID.getValue(), PUSHING_SECTION_SPEED, way);

        way.clearTags();
        way.setTag("highway", "platform");
        way.setTag("surface", "paved");
        assertPriorityAndSpeed(SLIGHT_AVOID.getValue(), PUSHING_SECTION_SPEED, way);

        way.clearTags();
        way.setTag("highway", "footway");
        way.setTag("surface", "paved");
        way.setTag("bicycle", "designated");
        assertPriorityAndSpeed(VERY_NICE.getValue(), cyclewaySpeed, way);

        way.clearTags();
        way.setTag("highway", "platform");
        way.setTag("surface", "paved");
        way.setTag("bicycle", "designated");
        assertPriorityAndSpeed(VERY_NICE.getValue(), cyclewaySpeed, way);

        way.clearTags();
        way.setTag("highway", "track");
        assertPriorityAndSpeed(UNCHANGED.getValue(), 12, way);

        way.setTag("tracktype", "grade1");
        assertPriorityAndSpeed(UNCHANGED.getValue(), 18, way);

        way.setTag("highway", "track");
        way.setTag("tracktype", "grade2");
        assertPriorityAndSpeed(UNCHANGED.getValue(), 12, way);

        // test speed for allowed get off the bike types
        way.setTag("highway", "track");
        way.setTag("bicycle", "yes");
        assertPriorityAndSpeed(UNCHANGED.getValue(), 12, way);

        way.clearTags();
        way.setTag("highway", "steps");
        assertPriorityAndSpeed(SLIGHT_AVOID.getValue(), 2, way);

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("bicycle", "use_sidepath");
        assertPriorityAndSpeed(REACH_DESTINATION.getValue(), 18, way);

        way.clearTags();
        way.setTag("highway", "steps");
        way.setTag("surface", "wood");
        assertPriorityAndSpeed(SLIGHT_AVOID.getValue(), PUSHING_SECTION_SPEED / 2.0, way);
        way.setTag("maxspeed", "20");
        assertPriorityAndSpeed(SLIGHT_AVOID.getValue(), PUSHING_SECTION_SPEED / 2.0, way);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("surface", "paved");
        assertPriorityAndSpeed(UNCHANGED.getValue(), 18, way);

        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("surface", "ground");
        assertPriorityAndSpeed(SLIGHT_AVOID.getValue(), PUSHING_SECTION_SPEED, way);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("bicycle", "yes");
        way.setTag("surface", "fine_gravel");
        assertPriorityAndSpeed(UNCHANGED.getValue(), 18, way);

        way.setTag("surface", "unknown_surface");
        assertPriorityAndSpeed(UNCHANGED.getValue(), PUSHING_SECTION_SPEED, way);

        way.clearTags();
        way.setTag("highway", "primary");
        way.setTag("surface", "fine_gravel");
        assertPriorityAndSpeed(AVOID.getValue(), 18, way);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("surface", "gravel");
        way.setTag("tracktype", "grade2");
        assertPriorityAndSpeed(UNCHANGED.getValue(), 12, way);

        way.clearTags();
        way.setTag("highway", "primary");
        way.setTag("surface", "paved");
        assertPriorityAndSpeed(AVOID.getValue(), 18, way);

        way.clearTags();
        way.setTag("highway", "primary");
        assertPriorityAndSpeed(AVOID.getValue(), 18, way);

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("surface", "asphalt");
        assertPriorityAndSpeed(PREFER.getValue(), 18, way);

        way.clearTags();
        way.setTag("highway", "motorway");
        way.setTag("bicycle", "yes");
        assertPriorityAndSpeed(AVOID.getValue(), 18, way);
    }

    @Test
    public void testSmoothness() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "residential");
        way.setTag("smoothness", "excellent");
        assertEquals(20, getSpeedFromFlags(way), 0.01);

        way.setTag("smoothness", "bad");
        assertEquals(14, getSpeedFromFlags(way), 0.01);

        way.setTag("smoothness", "impassable");
        assertEquals(PUSHING_SECTION_SPEED, getSpeedFromFlags(way), 0.01);

        way.setTag("smoothness", "unknown");
        assertEquals(14, getSpeedFromFlags(way), 0.01);

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("surface", "ground");
        assertEquals(12, getSpeedFromFlags(way), 0.01);

        way.setTag("smoothness", "bad");
        assertEquals(8, getSpeedFromFlags(way), 0.01);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("tracktype", "grade5");
        assertEquals(PUSHING_SECTION_SPEED, getSpeedFromFlags(way), 0.01);

        way.setTag("smoothness", "bad");
        assertEquals(PUSHING_SECTION_SPEED, getSpeedFromFlags(way), 0.01);

        way.setTag("smoothness", "impassable");
        assertEquals(PUSHING_SECTION_SPEED, getSpeedFromFlags(way), 0.01);
    }

    @Test
    public void testCycleway() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("surface", "paved");
        assertPriority(AVOID.getValue(), way);
        way.setTag("cycleway", "track");
        assertPriority(PREFER.getValue(), way);

        way.clearTags();
        way.setTag("highway", "primary");
        way.setTag("cycleway:left", "lane");
        assertPriority(UNCHANGED.getValue(), way);

        way.clearTags();
        way.setTag("highway", "primary");
        way.setTag("cycleway:right", "lane");
        assertPriority(UNCHANGED.getValue(), way);

        way.clearTags();
        way.setTag("highway", "primary");
        way.setTag("oneway", "yes");
        way.setTag("cycleway:left", "opposite_lane");
        assertPriority(AVOID.getValue(), way);
    }

    @Test
    public void testWayAcceptance() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "cycleway");
        way.setTag("vehicle", "no");
        assertTrue(parser.getAccess(way).isWay());

        // Sensless tagging: JOSM does create a warning here. We follow the highway tag:
        way.setTag("bicycle", "no");
        assertTrue(parser.getAccess(way).isWay());

        way.setTag("bicycle", "designated");
        assertTrue(parser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "motorway");
        assertTrue(parser.getAccess(way).canSkip());
        way.setTag("bicycle", "yes");
        assertTrue(parser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("bicycle", "yes");
        way.setTag("access", "no");
        assertTrue(parser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "bridleway");
        assertTrue(parser.getAccess(way).canSkip());
        way.setTag("bicycle", "yes");
        assertTrue(parser.getAccess(way).isWay());

    }

    @Test
    public void testOneway() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "tertiary");
        IntsRef flags = parser.handleWayTags(encodingManager.createEdgeFlags(), way);
        assertTrue(parser.getAccessEnc().getBool(false, flags));
        assertTrue(parser.getAccessEnc().getBool(true, flags));
        way.setTag("oneway", "yes");
        flags = parser.handleWayTags(encodingManager.createEdgeFlags(), way);
        assertTrue(parser.getAccessEnc().getBool(false, flags));
        assertFalse(parser.getAccessEnc().getBool(true, flags));
        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("oneway:bicycle", "yes");
        flags = parser.handleWayTags(encodingManager.createEdgeFlags(), way);
        assertTrue(parser.getAccessEnc().getBool(false, flags));
        assertFalse(parser.getAccessEnc().getBool(true, flags));
        way.clearTags();

        way.setTag("highway", "tertiary");
        flags = parser.handleWayTags(encodingManager.createEdgeFlags(), way);
        assertTrue(parser.getAccessEnc().getBool(false, flags));
        assertTrue(parser.getAccessEnc().getBool(true, flags));
        way.clearTags();

        way.setTag("highway", "tertiary");
        way.setTag("vehicle:forward", "no");
        flags = parser.handleWayTags(encodingManager.createEdgeFlags(), way);
        assertFalse(parser.getAccessEnc().getBool(false, flags));
        assertTrue(parser.getAccessEnc().getBool(true, flags));
        way.clearTags();

        way.setTag("highway", "tertiary");
        way.setTag("bicycle:forward", "no");
        flags = parser.handleWayTags(encodingManager.createEdgeFlags(), way);
        assertFalse(parser.getAccessEnc().getBool(false, flags));
        assertTrue(parser.getAccessEnc().getBool(true, flags));
        way.clearTags();

        way.setTag("highway", "tertiary");
        way.setTag("vehicle:backward", "no");
        flags = parser.handleWayTags(encodingManager.createEdgeFlags(), way);
        assertTrue(parser.getAccessEnc().getBool(false, flags));
        assertFalse(parser.getAccessEnc().getBool(true, flags));
        way.clearTags();

        way.setTag("highway", "tertiary");
        way.setTag("motor_vehicle:backward", "no");
        flags = parser.handleWayTags(encodingManager.createEdgeFlags(), way);
        assertTrue(parser.getAccessEnc().getBool(false, flags));
        assertTrue(parser.getAccessEnc().getBool(true, flags));
        way.clearTags();

        way.setTag("highway", "tertiary");
        way.setTag("oneway", "yes");
        way.setTag("bicycle:backward", "no");
        flags = parser.handleWayTags(encodingManager.createEdgeFlags(), way);
        assertTrue(parser.getAccessEnc().getBool(false, flags));
        assertFalse(parser.getAccessEnc().getBool(true, flags));

        way.setTag("bicycle:backward", "yes");
        flags = parser.handleWayTags(encodingManager.createEdgeFlags(), way);
        assertTrue(parser.getAccessEnc().getBool(false, flags));
        assertTrue(parser.getAccessEnc().getBool(true, flags));

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("oneway", "yes");
        way.setTag("bicycle:backward", "yes");
        flags = parser.handleWayTags(encodingManager.createEdgeFlags(), way);
        assertTrue(parser.getAccessEnc().getBool(false, flags));
        assertTrue(parser.getAccessEnc().getBool(true, flags));

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("oneway", "-1");
        way.setTag("bicycle:forward", "yes");
        flags = parser.handleWayTags(encodingManager.createEdgeFlags(), way);
        assertTrue(parser.getAccessEnc().getBool(false, flags));
        assertTrue(parser.getAccessEnc().getBool(true, flags));

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("bicycle:forward", "use_sidepath");
        flags = parser.handleWayTags(encodingManager.createEdgeFlags(), way);
        assertTrue(parser.getAccessEnc().getBool(false, flags));
        assertTrue(parser.getAccessEnc().getBool(true, flags));

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("bicycle:forward", "use_sidepath");
        flags = parser.handleWayTags(encodingManager.createEdgeFlags(), way);
        assertTrue(parser.getAccessEnc().getBool(false, flags));
        assertTrue(parser.getAccessEnc().getBool(true, flags));

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("oneway", "yes");
        way.setTag("cycleway", "opposite");
        flags = parser.handleWayTags(encodingManager.createEdgeFlags(), way);
        assertTrue(parser.getAccessEnc().getBool(false, flags));
        assertTrue(parser.getAccessEnc().getBool(true, flags));

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("oneway", "yes");
        way.setTag("cycleway:left", "opposite_lane");
        flags = parser.handleWayTags(encodingManager.createEdgeFlags(), way);
        assertTrue(parser.getAccessEnc().getBool(false, flags));
        assertTrue(parser.getAccessEnc().getBool(true, flags));
    }

    @Test
    public void testHandleWayTagsInfluencedByRelation() {
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "road");

        // unchanged
        assertPriorityAndSpeed(UNCHANGED.getValue(), 12, osmWay);

        // relation code is
        ReaderRelation osmRel = new ReaderRelation(1);
        osmRel.setTag("route", "bicycle");
        assertPriorityAndSpeed(PREFER.getValue(), 12, osmWay, osmRel);

        osmRel.setTag("network", "lcn");
        assertPriorityAndSpeed(PREFER.getValue(), 12, osmWay, osmRel);

        // relation code is NICE
        osmRel.setTag("network", "rcn");
        assertPriorityAndSpeed(VERY_NICE.getValue(), 12, osmWay, osmRel);

        // relation code is BEST
        osmRel.setTag("network", "ncn");
        assertPriorityAndSpeed(BEST.getValue(), 12, osmWay, osmRel);

        // PREFER relation, but tertiary road => no get off the bike but road wayTypeCode and faster
        osmWay.clearTags();
        osmWay.setTag("highway", "tertiary");
        osmRel.setTag("route", "bicycle");
        osmRel.setTag("network", "lcn");
        assertPriorityAndSpeed(PREFER.getValue(), 18, osmWay, osmRel);
    }

    @Test
    public void testUnchangedRelationShouldNotInfluencePriority() {
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "secondary");

        ReaderRelation osmRel = new ReaderRelation(1);
        osmRel.setTag("description", "something");
        assertPriorityAndSpeed(AVOID.getValue(), 18, osmWay, osmRel);
    }

    @Test
    @Override
    public void testSacScale() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "path");
        way.setTag("sac_scale", "hiking");
        assertTrue(parser.getAccess(way).isWay());

        way.setTag("highway", "path");
        way.setTag("sac_scale", "mountain_hiking");
        assertTrue(parser.getAccess(way).canSkip());

        way.setTag("highway", "cycleway");
        way.setTag("sac_scale", "hiking");
        assertTrue(parser.getAccess(way).isWay());

        way.setTag("highway", "cycleway");
        way.setTag("sac_scale", "mountain_hiking");
        // disallow questionable combination as too dangerous
        assertTrue(parser.getAccess(way).canSkip());
    }

    @Test
    public void testCalcPriority() {
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "tertiary");
        ReaderRelation osmRel = new ReaderRelation(1);
        osmRel.setTag("route", "bicycle");
        osmRel.setTag("network", "icn");
        IntsRef relFlags = osmParsers.handleRelationTags(osmRel, osmParsers.createRelationFlags());
        IntsRef flags = encodingManager.createEdgeFlags();
        flags = osmParsers.handleWayTags(flags, osmWay, relFlags);
        assertEquals(RouteNetwork.INTERNATIONAL, encodingManager.getEnumEncodedValue(BikeNetwork.KEY, RouteNetwork.class).getEnum(false, flags));
        assertEquals(PriorityCode.getValue(BEST.getValue()), priorityEnc.getDecimal(false, flags), .1);

        // for some highways the priority is UNCHANGED
        osmRel = new ReaderRelation(1);
        osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "track");
        flags = encodingManager.createEdgeFlags();
        flags = osmParsers.handleWayTags(flags, osmWay, osmParsers.createRelationFlags());
        assertEquals(RouteNetwork.MISSING, encodingManager.getEnumEncodedValue(BikeNetwork.KEY, RouteNetwork.class).getEnum(false, flags));
        assertEquals(PriorityCode.getValue(UNCHANGED.getValue()), priorityEnc.getDecimal(false, flags), .1);

        // for unknown highways we should probably keep the priority unchanged, but currently it does not matter
        // because the access will be false anyway
        osmRel = new ReaderRelation(1);
        osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "whatever");
        flags = encodingManager.createEdgeFlags();
        flags = osmParsers.handleWayTags(flags, osmWay, osmParsers.createRelationFlags());
        assertEquals(RouteNetwork.MISSING, encodingManager.getEnumEncodedValue(BikeNetwork.KEY, RouteNetwork.class).getEnum(false, flags));
        assertEquals(EXCLUDE.getValue(), priorityEnc.getDecimal(false, flags), .1);
    }

    @Test
    public void testMaxSpeed() {
        // the maxspeed is well above our speed and has no effect
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "tertiary");
        way.setTag("maxspeed", "90");
        assertPriorityAndSpeed(UNCHANGED.getValue(), 18, way);

        way = new ReaderWay(1);
        way.setTag("highway", "track");
        way.setTag("maxspeed", "90");
        assertPriorityAndSpeed(UNCHANGED.getValue(), 12, way);

        // here we are limited by the maxspeed
        way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("maxspeed", "10");
        assertPriorityAndSpeed(PREFER.getValue(), 10, way);

        way = new ReaderWay(1);
        way.setTag("highway", "residential");
        way.setTag("maxspeed", "15");
        // todo: speed is larger than maxspeed tag due to rounding and storable max speed is 30
        assertPriorityAndSpeed(PREFER.getValue(), 16, way);
    }

    // Issue 407 : Always block kissing_gate except for mountainbikes
    @Test
    @Override
    public void testBarrierAccess() {
        // kissing_gate without bicycle tag
        ReaderNode node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "kissing_gate");
        // barrier!
        assertTrue(parser.isBarrier(node));

        // kissing_gate with bicycle tag
        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "kissing_gate");
        node.setTag("bicycle", "yes");
        // no barrier!
        assertFalse(parser.isBarrier(node));

        // Test if cattle_grid is non blocking
        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "cattle_grid");
        assertFalse(parser.isBarrier(node));
    }

    @Test
    public void testClassBicycle() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "tertiary");
        way.setTag("class:bicycle", "3");
        assertPriority(BEST.getValue(), way);
        // Test that priority cannot get better than best
        way.setTag("scenic", "yes");
        assertPriority(BEST.getValue(), way);
        way.setTag("scenic", "no");
        way.setTag("class:bicycle", "2");
        assertPriority(VERY_NICE.getValue(), way);
        way.setTag("class:bicycle", "1");
        assertPriority(PREFER.getValue(), way);
        way.setTag("class:bicycle", "0");
        assertPriority(UNCHANGED.getValue(), way);
        way.setTag("class:bicycle", "invalidvalue");
        assertPriority(UNCHANGED.getValue(), way);
        way.setTag("class:bicycle", "-1");
        assertPriority(SLIGHT_AVOID.getValue(), way);
        way.setTag("class:bicycle", "-2");
        assertPriority(AVOID.getValue(), way);
        way.setTag("class:bicycle", "-3");
        assertPriority(AVOID_MORE.getValue(), way);

        way.setTag("highway", "residential");
        way.setTag("bicycle", "designated");
        way.setTag("class:bicycle", "3");
        assertPriority(BEST.getValue(), way);

        // Now we test overriding by a specific class subtype
        way.setTag("class:bicycle:touring", "2");
        assertPriority(VERY_NICE.getValue(), way);

        way.setTag("maxspeed", "15");
        assertPriority(VERY_NICE.getValue(), way);
    }
}
