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
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.TagParserFactory;
import com.graphhopper.storage.IntsRef;
import org.junit.Test;

import static com.graphhopper.routing.util.BikeCommonFlagEncoder.PUSHING_SECTION_SPEED;
import static com.graphhopper.routing.util.PriorityCode.*;
import static org.junit.Assert.*;

/**
 * @author Peter Karich
 * @author ratrun
 */
public class BikeFlagEncoderTest extends AbstractBikeFlagEncoderTester {

    DecimalEncodedValue averageSpeedEnc;
    BooleanEncodedValue accessEnc;
    DecimalEncodedValue priorityEnc;

    @Override
    protected BikeCommonFlagEncoder createBikeEncoder() {
        encodingManager = new EncodingManager.Builder().addGlobalEncodedValues(true).
                addAllFlagEncoders("bike,mtb").build();
        BikeCommonFlagEncoder encoder = (BikeCommonFlagEncoder) encodingManager.getEncoder("bike");
        averageSpeedEnc = encodingManager.getDecimalEncodedValue(encoder.getPrefix() + "average_speed");
        accessEnc = encodingManager.getBooleanEncodedValue(encoder.getPrefix() + "access");
        priorityEnc = encodingManager.getDecimalEncodedValue(encoder.getPrefix() + "priority");
        return encoder;
    }

    @Test
    public void testGetSpeed() {
        IntsRef ints = encodingManager.createIntsRef();
        averageSpeedEnc.setDecimal(false, ints, 10d);
        assertEquals(10, encoder.getSpeed(ints), 1e-1);
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        assertEquals(18, encoder.getSpeed(way));
        assertPriority(REACH_DEST.getValue(), way);

        way.setTag("scenic", "yes");
        assertEquals(18, encoder.getSpeed(way));
        assertPriority(AVOID_IF_POSSIBLE.getValue(), way);

        // Pushing section: this is fine as we obey the law!
        way.clearTags();
        way.setTag("highway", "footway");
        assertEquals(PUSHING_SECTION_SPEED, encoder.getSpeed(way));
        assertPriority(AVOID_IF_POSSIBLE.getValue(), way);

        // Use pushing section irrespective of the pavement
        way.setTag("surface", "paved");
        assertEquals(PUSHING_SECTION_SPEED, encoder.getSpeed(way));
        assertPriority(AVOID_IF_POSSIBLE.getValue(), way);

        way.clearTags();
        way.setTag("highway", "secondary");
        way.setTag("bicycle", "dismount");
        assertEquals(PUSHING_SECTION_SPEED, encoder.getSpeed(way));
        assertPriority(REACH_DEST.getValue(), way);

        way.clearTags();
        way.setTag("highway", "footway");
        way.setTag("bicycle", "yes");
        assertEquals(PUSHING_SECTION_SPEED, encoder.getSpeed(way));
        assertPriority(PREFER.getValue(), way);
        way.setTag("segregated", "no");
        assertEquals(PUSHING_SECTION_SPEED, encoder.getSpeed(way));
        assertPriority(PREFER.getValue(), way);
        way.setTag("segregated", "yes");
        assertEquals(PUSHING_SECTION_SPEED * 2, encoder.getSpeed(way));
        assertPriority(PREFER.getValue(), way);

        way.clearTags();
        way.setTag("highway", "footway");
        way.setTag("surface", "paved");
        way.setTag("bicycle", "yes");
        assertEquals(PUSHING_SECTION_SPEED, encoder.getSpeed(way));
        assertPriority(PREFER.getValue(), way);
        way.setTag("segregated", "yes");
        assertEquals(PUSHING_SECTION_SPEED * 2, encoder.getSpeed(way));
        assertPriority(PREFER.getValue(), way);

        way.clearTags();
        way.setTag("highway", "cycleway");
        assertEquals(18, encoder.getSpeed(way));
        assertPriority(VERY_NICE.getValue(), way);
        int cyclewaySpeed = encoder.getSpeed(way);
        way.setTag("foot", "yes");
        way.setTag("segregated", "yes");
        assertPriority(VERY_NICE.getValue(), way);
        assertEquals(cyclewaySpeed, encoder.getSpeed(way));
        way.setTag("segregated", "no");
        assertPriority(PREFER.getValue(), way);
        assertEquals(cyclewaySpeed, encoder.getSpeed(way));

        // Make sure that "highway=cycleway" and "highway=path" with "bicycle=designated" give the same result
        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("bicycle", "designated");
        assertEquals(cyclewaySpeed, encoder.getSpeed(way));
        // Assume foot=no for designated in absence of a foot tag
        assertPriority(VERY_NICE.getValue(), way);
        way.setTag("foot", "yes");
        assertEquals(cyclewaySpeed, encoder.getSpeed(way));
        assertPriority(PREFER.getValue(), way);

        way.setTag("foot", "no");
        assertEquals(cyclewaySpeed, encoder.getSpeed(way));
        assertPriority(VERY_NICE.getValue(), way);

        way.setTag("segregated", "yes");
        assertEquals(cyclewaySpeed, encoder.getSpeed(way));
        assertPriority(VERY_NICE.getValue(), way);

        way.setTag("segregated", "no");
        assertEquals(cyclewaySpeed, encoder.getSpeed(way));
        assertPriority(VERY_NICE.getValue(), way);

        way.setTag("bicycle", "yes");
        assertEquals(PUSHING_SECTION_SPEED, encoder.getSpeed(way));
        assertPriority(PREFER.getValue(), way);

        way.setTag("segregated", "yes");
        assertEquals(PUSHING_SECTION_SPEED * 2, encoder.getSpeed(way));
        assertPriority(PREFER.getValue(), way);

        way.setTag("surface", "unpaved");
        assertEquals(PUSHING_SECTION_SPEED * 2, encoder.getSpeed(way));

        way.setTag("surface", "paved");
        assertEquals(PUSHING_SECTION_SPEED * 2, encoder.getSpeed(way));

        way.clearTags();
        way.setTag("highway", "path");
        assertEquals(PUSHING_SECTION_SPEED, encoder.getSpeed(way));
        assertPriority(AVOID_IF_POSSIBLE.getValue(), way);

        // use pushing section
        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("surface", "paved");
        assertEquals(PUSHING_SECTION_SPEED, encoder.getSpeed(way));
        assertPriority(AVOID_IF_POSSIBLE.getValue(), way);

        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("surface", "ground");
        assertEquals(PUSHING_SECTION_SPEED, encoder.getSpeed(way));
        assertPriority(AVOID_IF_POSSIBLE.getValue(), way);

        way.clearTags();
        way.setTag("highway", "footway");
        way.setTag("surface", "paved");
        way.setTag("bicycle", "designated");
        assertEquals(cyclewaySpeed, encoder.getSpeed(way));
        assertPriority(VERY_NICE.getValue(), way);

        way.clearTags();
        way.setTag("highway", "track");
        assertEquals(12, encoder.getSpeed(way));
        assertPriority(UNCHANGED.getValue(), way);

        way.setTag("tracktype", "grade1");
        assertEquals(18, encoder.getSpeed(way));
        assertPriority(UNCHANGED.getValue(), way);

        way.setTag("highway", "track");
        way.setTag("tracktype", "grade2");
        assertEquals(12, encoder.getSpeed(way));
        assertPriority(UNCHANGED.getValue(), way);

        // test speed for allowed get off the bike types
        way.setTag("highway", "track");
        way.setTag("bicycle", "yes");
        assertEquals(12, encoder.getSpeed(way));

        way.clearTags();
        way.setTag("highway", "steps");
        assertEquals(2, encoder.getSpeed(way));
        assertPriority(AVOID_IF_POSSIBLE.getValue(), way);

        way.clearTags();
        way.setTag("highway", "steps");
        way.setTag("surface", "wood");
        assertEquals(PUSHING_SECTION_SPEED / 2, encoder.getSpeed(way));
        assertPriority(AVOID_IF_POSSIBLE.getValue(), way);
        way.setTag("maxspeed", "20");
        assertEquals(PUSHING_SECTION_SPEED / 2, encoder.getSpeed(way));
        assertPriority(AVOID_IF_POSSIBLE.getValue(), way);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("surface", "paved");
        assertEquals(18, encoder.getSpeed(way));

        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("surface", "ground");
        assertEquals(4, encoder.getSpeed(way));
        assertPriority(AVOID_IF_POSSIBLE.getValue(), way);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("bicycle", "yes");
        way.setTag("surface", "fine_gravel");
        assertEquals(18, encoder.getSpeed(way));

        way.setTag("surface", "unknown_surface");
        assertEquals(PUSHING_SECTION_SPEED, encoder.getSpeed(way));

        way.clearTags();
        way.setTag("highway", "primary");
        way.setTag("surface", "fine_gravel");
        assertEquals(18, encoder.getSpeed(way));

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("surface", "gravel");
        way.setTag("tracktype", "grade2");
        assertEquals(12, encoder.getSpeed(way));
        assertPriority(UNCHANGED.getValue(), way);

        way.clearTags();
        way.setTag("highway", "primary");
        way.setTag("surface", "paved");
        assertEquals(18, encoder.getSpeed(way));

        way.clearTags();
        way.setTag("highway", "primary");
        assertEquals(18, encoder.getSpeed(way));

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("surface", "asphalt");
        assertEquals(18, encoder.getSpeed(way));

        way.clearTags();
        way.setTag("highway", "motorway");
        way.setTag("bicycle", "yes");
        assertEquals(18, encoder.getSpeed(way));
    }

    @Test
    public void testHandleWayTags() {
        ReaderWay way = new ReaderWay(1);
        String wayType;
        way.setTag("highway", "track");
        wayType = getWayTypeFromFlags(way);
        assertEquals("small way, unpaved", wayType);

        way.clearTags();
        way.setTag("highway", "path");
        wayType = getWayTypeFromFlags(way);
        assertEquals("get off the bike, unpaved", wayType);

        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("surface", "grass");
        wayType = getWayTypeFromFlags(way);
        assertEquals("get off the bike, unpaved", wayType);

        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("surface", "concrete");
        wayType = getWayTypeFromFlags(way);
        assertEquals("get off the bike", wayType);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("foot", "yes");
        way.setTag("surface", "paved");
        way.setTag("tracktype", "grade1");
        wayType = getWayTypeFromFlags(way);
        assertEquals("", wayType);

        way.setTag("tracktype", "grade2");
        wayType = getWayTypeFromFlags(way);
        assertEquals("get off the bike, unpaved", wayType);

        way.clearTags();
        way.setTag("junction", "roundabout");
        way.setTag("highway", "tertiary");
        IntsRef ints = encodingManager.createIntsRef();
        encoder.handleWayTags(ints, way, encoder.getAccess(way), 0);
        assertTrue(encodingManager.getBooleanEncodedValue("roundabout").getBool(false, ints));
    }

    @Test
    public void testWayAcceptance() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "cycleway");
        way.setTag("vehicle", "no");
        assertTrue(encoder.getAccess(way).isWay());

        // Sensless tagging: JOSM does create a warning here. We follow the highway tag:
        way.setTag("bicycle", "no");
        assertTrue(encoder.getAccess(way).isWay());

        way.setTag("bicycle", "designated");
        assertTrue(encoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "motorway");
        assertFalse(encoder.getAccess(way).isWay());
        way.setTag("bicycle", "yes");
        assertTrue(encoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("bicycle", "yes");
        way.setTag("access", "no");
        assertTrue(encoder.getAccess(way).isWay());
    }

    @Test
    public void testOneway() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "tertiary");
        IntsRef ints = encoder.handleWayTags(encodingManager.createIntsRef(), way, encoder.getAccess(way), 0);
        assertTrue(accessEnc.getBool(false, ints));
        assertTrue(accessEnc.getBool(true, ints));
        way.setTag("oneway", "yes");
        ints = encoder.handleWayTags(encodingManager.createIntsRef(), way, encoder.getAccess(way), 0);
        assertTrue(accessEnc.getBool(false, ints));
        assertFalse(accessEnc.getBool(true, ints));
        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("oneway:bicycle", "yes");
        ints = encoder.handleWayTags(encodingManager.createIntsRef(), way, encoder.getAccess(way), 0);
        assertTrue(accessEnc.getBool(false, ints));
        assertFalse(accessEnc.getBool(true, ints));
        way.clearTags();

        way.setTag("highway", "tertiary");
        ints = encoder.handleWayTags(encodingManager.createIntsRef(), way, encoder.getAccess(way), 0);
        assertTrue(accessEnc.getBool(false, ints));
        assertTrue(accessEnc.getBool(true, ints));
        way.clearTags();

        way.setTag("highway", "tertiary");
        way.setTag("vehicle:forward", "no");
        ints = encoder.handleWayTags(encodingManager.createIntsRef(), way, encoder.getAccess(way), 0);
        assertFalse(accessEnc.getBool(false, ints));
        assertTrue(accessEnc.getBool(true, ints));
        way.clearTags();

        way.setTag("highway", "tertiary");
        way.setTag("bicycle:forward", "no");
        ints = encoder.handleWayTags(encodingManager.createIntsRef(), way, encoder.getAccess(way), 0);
        assertFalse(accessEnc.getBool(false, ints));
        assertTrue(accessEnc.getBool(true, ints));
        way.clearTags();

        way.setTag("highway", "tertiary");
        way.setTag("vehicle:backward", "no");
        ints = encoder.handleWayTags(encodingManager.createIntsRef(), way, encoder.getAccess(way), 0);
        assertTrue(accessEnc.getBool(false, ints));
        assertFalse(accessEnc.getBool(true, ints));
        way.clearTags();

        way.setTag("highway", "tertiary");
        way.setTag("motor_vehicle:backward", "no");
        ints = encoder.handleWayTags(encodingManager.createIntsRef(), way, encoder.getAccess(way), 0);
        assertTrue(accessEnc.getBool(false, ints));
        assertTrue(accessEnc.getBool(true, ints));
        way.clearTags();

        // attention bicycle:backward=no/yes has a completely different meaning!
        // https://wiki.openstreetmap.org/wiki/Key:access#One-way_restrictions
        way.setTag("highway", "tertiary");
        way.setTag("oneway", "yes");
        way.setTag("bicycle:backward", "no");
        ints = encoder.handleWayTags(encodingManager.createIntsRef(), way, encoder.getAccess(way), 0);
        assertTrue(accessEnc.getBool(false, ints));
        assertTrue(accessEnc.getBool(true, ints));

        way.setTag("bicycle:backward", "yes");
        ints = encoder.handleWayTags(encodingManager.createIntsRef(), way, encoder.getAccess(way), 0);
        assertTrue(accessEnc.getBool(false, ints));
        assertTrue(accessEnc.getBool(true, ints));

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("oneway", "yes");
        way.setTag("cycleway", "opposite");
        ints = encoder.handleWayTags(encodingManager.createIntsRef(), way, encoder.getAccess(way), 0);
        assertTrue(accessEnc.getBool(false, ints));
        assertTrue(accessEnc.getBool(true, ints));

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("oneway", "yes");
        way.setTag("cycleway:left", "opposite_lane");
        ints = encoder.handleWayTags(encodingManager.createIntsRef(), way, encoder.getAccess(way), 0);
        assertTrue(accessEnc.getBool(false, ints));
        assertTrue(accessEnc.getBool(true, ints));
    }

    @Test
    public void testHandleWayTagsInfluencedByRelation() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "road");
        ReaderRelation osmRel = new ReaderRelation(1);
        long relFlags = encoder.handleRelationTags(osmRel, 0);
        // unchanged
        IntsRef ints = encoder.handleWayTags(encodingManager.createIntsRef(), way, encoder.getAccess(way), 0);
        assertEquals(12, encoder.getSpeed(ints), 1e-1);
        assertPriority(UNCHANGED.getValue(), way, relFlags);

        // relation code is PREFER
        osmRel.setTag("route", "bicycle");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        ints = encoder.handleWayTags(encodingManager.createIntsRef(), way, encoder.getAccess(way), 0);
        assertEquals(12, encoder.getSpeed(ints), 1e-1);
        assertPriority(PREFER.getValue(), way, relFlags);
        osmRel.setTag("network", "lcn");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        ints = encoder.handleWayTags(encodingManager.createIntsRef(), way, encoder.getAccess(way), 0);
        assertEquals(12, encoder.getSpeed(ints), 1e-1);
        assertPriority(PREFER.getValue(), way, relFlags);

        // relation code is VERY_NICE
        osmRel.setTag("network", "rcn");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        assertPriority(VERY_NICE.getValue(), way, relFlags);

        // relation code is BEST
        osmRel.setTag("network", "ncn");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        assertPriority(BEST.getValue(), way, relFlags);

        // PREFER relation, but tertiary road => no get off the bike but road wayTypeCode and faster
        way.clearTags();
        way.setTag("highway", "tertiary");
        osmRel.setTag("route", "bicycle");
        osmRel.setTag("network", "lcn");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        assertPriority(PREFER.getValue(), way, relFlags);

        // A footway is not of waytype get off the bike in case that it is part of a cycle route
        osmRel.clearTags();
        way.clearTags();
        way.setTag("highway", "footway");
        way.setTag("surface", "grass");

        // First tests without a cycle route relation, this is a get off the bike
        relFlags = encoder.handleRelationTags(osmRel, 0);
        String wayType = getWayTypeFromFlags(way, relFlags);
        assertEquals("get off the bike, unpaved", wayType);

        // now as part of a cycle route relation
        osmRel.setTag("type", "route");
        osmRel.setTag("route", "bicycle");
        osmRel.setTag("network", "lcn");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        wayType = getWayTypeFromFlags(way, relFlags);
        assertEquals("small way, unpaved", wayType);

        // steps are still shown as get off the bike
        way.clearTags();
        way.setTag("highway", "steps");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        wayType = getWayTypeFromFlags(way, relFlags);
        assertEquals("get off the bike", wayType);
    }

    @Test
    public void testUnchangedRelationShouldNotInfluencePriority() {
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "secondary");

        ReaderRelation osmRel = new ReaderRelation(1);
        osmRel.setTag("description", "something");
        long relFlags = encoder.handleRelationTags(osmRel, 0);
        assertPriority(REACH_DEST.getValue(), osmWay, relFlags);
    }

    @Test
    @Override
    public void testSacScale() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "path");
        way.setTag("sac_scale", "hiking");
        // allow
        assertTrue(encoder.getAccess(way).isWay());

        way.setTag("highway", "path");
        way.setTag("sac_scale", "mountain_hiking");
        // disallow
        assertTrue(encoder.getAccess(way).canSkip());

        way.setTag("highway", "cycleway");
        way.setTag("sac_scale", "hiking");
        // allow
        assertTrue(encoder.getAccess(way).isWay());

        way.setTag("highway", "cycleway");
        way.setTag("sac_scale", "mountain_hiking");
        // disallow questionable combination as too dangerous
        assertTrue(encoder.getAccess(way).canSkip());
    }

    @Test
    public void testCalcPriority() {
        ReaderWay osmWay = new ReaderWay(1);
        ReaderRelation osmRel = new ReaderRelation(1);
        osmRel.setTag("route", "bicycle");
        osmRel.setTag("network", "icn");
        long relFlags = encoder.handleRelationTags(osmRel, 0);
        IntsRef ints = encoder.handleWayTags(encodingManager.createIntsRef(), osmWay, EncodingManager.Access.WAY, relFlags);
        assertEquals((double) BEST.getValue() / BEST.getValue(), priorityEnc.getDecimal(false, ints), 1e-3);

        // important: UNCHANGED should not get 0 priority!
        osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "somethingelse");
        ints = encoder.handleWayTags(encodingManager.createIntsRef(), osmWay, EncodingManager.Access.WAY, 0);
        assertEquals((double) UNCHANGED.getValue() / BEST.getValue(), priorityEnc.getDecimal(false, ints), 1e-3);
    }

    @Test
    public void testMaxSpeed() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("maxspeed", "10");
        EncodingManager.Access allowed = encoder.getAccess(way);
        IntsRef ints = encoder.handleWayTags(encodingManager.createIntsRef(), way, allowed, 0);
        assertEquals(10, encoder.getSpeed(ints), 1e-1);
        assertPriority(VERY_NICE.getValue(), way);

        way = new ReaderWay(1);
        way.setTag("highway", "tertiary");
        way.setTag("maxspeed", "90");
        assertEquals(20, encoder.getSpeed(encoder.setSpeed(encodingManager.createIntsRef(), encoder.applyMaxSpeed(way, 20))), 1e-1);
        assertPriority(UNCHANGED.getValue(), way);

        way = new ReaderWay(1);
        way.setTag("highway", "track");
        way.setTag("maxspeed", "90");
        assertEquals(20, encoder.getSpeed(encoder.setSpeed(encodingManager.createIntsRef(), encoder.applyMaxSpeed(way, 20))), 1e-1);
        assertPriority(UNCHANGED.getValue(), way);

        way = new ReaderWay(1);
        way.setTag("highway", "residential");
        way.setTag("maxspeed", "15");
        assertEquals(15, encoder.getSpeed(encoder.setSpeed(encodingManager.createIntsRef(), encoder.applyMaxSpeed(way, 15))), 1.0);
        allowed = encoder.getAccess(way);
        ints = encoder.handleWayTags(encodingManager.createIntsRef(), way, allowed, 0);
        assertEquals(15, encoder.getSpeed(ints), 1.0);
        assertPriority(VERY_NICE.getValue(), way);

    }

    @Test
    public void testTurnFlagEncoding_DefaultNoRestrictionsAndNoCosts() {
        // default is disabled turn costs and no restrictions
        long flags_r0 = encoder.getTurnFlags(true, 0);
        long flags_0 = encoder.getTurnFlags(false, 0);

        long flags_r20 = encoder.getTurnFlags(true, 20);
        long flags_20 = encoder.getTurnFlags(false, 20);

        assertEquals(0, encoder.getTurnCost(flags_r0), .1);
        assertEquals(0, encoder.getTurnCost(flags_0), .1);

        assertEquals(0, encoder.getTurnCost(flags_r20), .1);
        assertEquals(0, encoder.getTurnCost(flags_20), .1);

        assertFalse(encoder.isTurnRestricted(flags_r0));
        assertFalse(encoder.isTurnRestricted(flags_0));

        assertFalse(encoder.isTurnRestricted(flags_r20));
        assertFalse(encoder.isTurnRestricted(flags_20));
    }

    @Test
    public void testTurnFlagEncoding_withCosts() {
        encoder = new BikeFlagEncoder(4, 2, 127);
        new EncodingManager.Builder().addGlobalEncodedValues(true).addAll(encoder).build();

        long flags_r0 = encoder.getTurnFlags(true, 0);
        long flags_0 = encoder.getTurnFlags(false, 0);
        assertTrue(Double.isInfinite(encoder.getTurnCost(flags_r0)));
        assertEquals(0, encoder.getTurnCost(flags_0), .1);
        assertTrue(encoder.isTurnRestricted(flags_r0));
        assertFalse(encoder.isTurnRestricted(flags_0));

        long flags_r20 = encoder.getTurnFlags(true, 0);
        long flags_20 = encoder.getTurnFlags(false, 20);
        assertTrue(Double.isInfinite(encoder.getTurnCost(flags_r20)));
        assertEquals(20, encoder.getTurnCost(flags_20), .1);
        assertTrue(encoder.isTurnRestricted(flags_r20));
        assertFalse(encoder.isTurnRestricted(flags_20));

        long flags_r220 = encoder.getTurnFlags(true, 0);
        try {
            encoder.getTurnFlags(false, 220);
            assertTrue(false);
        } catch (Exception ex) {
        }
        long flags_126 = encoder.getTurnFlags(false, 126);
        assertTrue(Double.isInfinite(encoder.getTurnCost(flags_r220)));

        assertEquals(126, encoder.getTurnCost(flags_126), .1);
        assertTrue(encoder.isTurnRestricted(flags_r220));
        assertFalse(encoder.isTurnRestricted(flags_126));
    }

    // Issue 407 : Always block kissing_gate execpt for mountainbikes
    @Test
    @Override
    public void testBarrierAccess() {
        // kissing_gate without bicycle tag
        ReaderNode node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "kissing_gate");
        // barrier!
        assertFalse(encoder.handleNodeTags(node) == 0);

        // kissing_gate with bicycle tag
        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "kissing_gate");
        node.setTag("bicycle", "yes");
        // barrier!
        assertFalse(encoder.handleNodeTags(node) == 0);
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
        assertPriority(AVOID_IF_POSSIBLE.getValue(), way);
        way.setTag("class:bicycle", "-2");
        assertPriority(REACH_DEST.getValue(), way);
        way.setTag("class:bicycle", "-3");
        assertPriority(AVOID_AT_ALL_COSTS.getValue(), way);

        way.setTag("highway", "residential");
        way.setTag("bicycle", "designated");
        way.setTag("class:bicycle", "3");
        assertPriority(BEST.getValue(), way);

        // Now we test overriding by a specific class subtype
        way.setTag("class:bicycle:touring", "2");
        assertPriority(VERY_NICE.getValue(), way);

        way.setTag("maxspeed", "15");
        assertPriority(BEST.getValue(), way);
    }

}
