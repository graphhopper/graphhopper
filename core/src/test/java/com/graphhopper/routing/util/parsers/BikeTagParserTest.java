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
package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.PriorityCode;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.Test;

import static com.graphhopper.routing.util.PriorityCode.*;
import static com.graphhopper.routing.util.parsers.BikeCommonAverageSpeedParser.MIN_SPEED;
import static com.graphhopper.routing.util.parsers.BikeCommonAverageSpeedParser.PUSHING_SECTION_SPEED;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 * @author ratrun
 */
public class BikeTagParserTest extends AbstractBikeTagParserTester {

    @Override
    protected EncodingManager createEncodingManager() {
        return new EncodingManager.Builder()
                .add(VehicleAccess.create("bike"))
                .add(VehicleSpeed.create("bike", 4, 2, false))
                .add(VehiclePriority.create("bike", 4, PriorityCode.getFactor(1), false))
                .add(Roundabout.create())
                .add(Smoothness.create())
                .add(FerrySpeed.create())
                .add(RouteNetwork.create(BikeNetwork.KEY))
                .add(RouteNetwork.create(MtbNetwork.KEY))
                .build();
    }

    @Override
    protected BikeCommonAccessParser createAccessParser(EncodedValueLookup lookup, PMap pMap) {
        return new BikeAccessParser(lookup, pMap);
    }

    @Override
    protected BikeCommonAverageSpeedParser createAverageSpeedParser(EncodedValueLookup lookup) {
        return new BikeAverageSpeedParser(lookup);
    }

    @Override
    protected BikeCommonPriorityParser createPriorityParser(EncodedValueLookup lookup) {
        return new BikePriorityParser(lookup);
    }

    @Test
    public void testSpeedAndPriority() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        assertPriorityAndSpeed(BAD, 18, way);

        way.setTag("scenic", "yes");
        assertPriorityAndSpeed(AVOID_MORE, 18, way);

        // Pushing section: this is fine as we obey the law!
        way.clearTags();
        way.setTag("highway", "footway");
        assertPriorityAndSpeed(SLIGHT_AVOID, PUSHING_SECTION_SPEED, way);

        // Use pushing section irrespective of the pavement
        way.setTag("surface", "paved");
        assertPriorityAndSpeed(SLIGHT_AVOID, PUSHING_SECTION_SPEED, way);

        way.clearTags();
        way.setTag("highway", "path");
        assertPriorityAndSpeed(SLIGHT_AVOID, PUSHING_SECTION_SPEED, way);

        way.clearTags();
        way.setTag("highway", "secondary");
        way.setTag("bicycle", "dismount");
        assertPriorityAndSpeed(AVOID, PUSHING_SECTION_SPEED, way);

        way.clearTags();
        way.setTag("highway", "footway");
        way.setTag("bicycle", "yes");
        assertPriorityAndSpeed(PREFER, 10, way);
        way.setTag("segregated", "no");
        assertPriorityAndSpeed(PREFER, 10, way);
        way.setTag("segregated", "yes");
        assertPriorityAndSpeed(PREFER, 18, way);

        way.clearTags();
        way.setTag("highway", "footway");
        way.setTag("surface", "paved");
        way.setTag("bicycle", "yes");
        assertPriorityAndSpeed(PREFER, 10, way);
        way.setTag("surface", "cobblestone");
        assertPriorityAndSpeed(PREFER, 8, way);
        way.setTag("segregated", "yes");
        way.setTag("surface", "paved");
        assertPriorityAndSpeed(PREFER, 18, way);

        way.clearTags();
        way.setTag("highway", "platform");
        way.setTag("surface", "paved");
        way.setTag("bicycle", "yes");
        assertPriorityAndSpeed(PREFER, 10, way);
        way.setTag("segregated", "yes");
        assertPriorityAndSpeed(PREFER, 18, way);

        way.clearTags();
        way.setTag("highway", "cycleway");
        assertPriorityAndSpeed(VERY_NICE, 18, way);
        int cyclewaySpeed = 18;
        way.setTag("foot", "yes");
        way.setTag("segregated", "yes");
        assertPriorityAndSpeed(VERY_NICE, cyclewaySpeed, way);
        way.setTag("segregated", "no");
        assertPriorityAndSpeed(PREFER, cyclewaySpeed, way);

        // Make sure that "highway=cycleway" and "highway=path" with "bicycle=designated" give the same result
        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("bicycle", "designated");
        // Assume foot=no for designated in absence of a foot tag
        assertPriorityAndSpeed(VERY_NICE, cyclewaySpeed, way);
        way.setTag("foot", "yes");
        assertPriorityAndSpeed(PREFER, cyclewaySpeed, way);

        way.setTag("foot", "no");
        assertPriorityAndSpeed(VERY_NICE, cyclewaySpeed, way);

        way.setTag("segregated", "yes");
        assertPriorityAndSpeed(VERY_NICE, cyclewaySpeed, way);

        way.setTag("segregated", "no");
        assertPriorityAndSpeed(VERY_NICE, cyclewaySpeed, way);

        way.setTag("bicycle", "yes");
        assertPriorityAndSpeed(PREFER, 10, way);

        way.setTag("segregated", "yes");
        assertPriorityAndSpeed(PREFER, cyclewaySpeed, way);

        way.setTag("surface", "unpaved");
        assertPriorityAndSpeed(PREFER, 12, way);

        way.setTag("surface", "paved");
        assertPriorityAndSpeed(PREFER, 18, way);

        way.clearTags();
        way.setTag("highway", "path");
        assertPriorityAndSpeed(SLIGHT_AVOID, PUSHING_SECTION_SPEED, way);

        // use pushing section
        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("surface", "paved");
        assertPriorityAndSpeed(SLIGHT_AVOID, PUSHING_SECTION_SPEED, way);

        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("surface", "ground");
        assertPriorityAndSpeed(SLIGHT_AVOID, PUSHING_SECTION_SPEED, way);

        way.clearTags();
        way.setTag("highway", "platform");
        way.setTag("surface", "paved");
        assertPriorityAndSpeed(SLIGHT_AVOID, PUSHING_SECTION_SPEED, way);

        way.clearTags();
        way.setTag("highway", "footway");
        way.setTag("surface", "paved");
        way.setTag("bicycle", "designated");
        assertPriorityAndSpeed(VERY_NICE, cyclewaySpeed, way);

        way.clearTags();
        way.setTag("highway", "platform");
        way.setTag("surface", "paved");
        way.setTag("bicycle", "designated");
        assertPriorityAndSpeed(VERY_NICE, cyclewaySpeed, way);

        way.clearTags();
        way.setTag("highway", "track");
        assertPriorityAndSpeed(UNCHANGED, 12, way);

        way.setTag("tracktype", "grade1");
        assertPriorityAndSpeed(UNCHANGED, 18, way);

        way.setTag("highway", "track");
        way.setTag("tracktype", "grade2");
        assertPriorityAndSpeed(UNCHANGED, 12, way);

        // test speed for allowed get off the bike types
        way.setTag("highway", "track");
        way.setTag("bicycle", "yes");
        assertPriorityAndSpeed(UNCHANGED, 12, way);

        way.clearTags();
        way.setTag("highway", "steps");
        assertPriorityAndSpeed(BAD, 2, way);

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("bicycle", "use_sidepath");
        assertPriorityAndSpeed(REACH_DESTINATION, 18, way);

        way.clearTags();
        way.setTag("highway", "steps");
        way.setTag("surface", "wood");
        assertPriorityAndSpeed(BAD, MIN_SPEED, way);
        way.setTag("maxspeed", "20");
        assertPriorityAndSpeed(BAD, MIN_SPEED, way);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("surface", "paved");
        assertPriorityAndSpeed(UNCHANGED, 18, way);

        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("surface", "ground");
        assertPriorityAndSpeed(SLIGHT_AVOID, PUSHING_SECTION_SPEED, way);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("bicycle", "yes");
        way.setTag("surface", "fine_gravel");
        assertPriorityAndSpeed(UNCHANGED, 14, way);

        way.setTag("surface", "unknown_surface");
        assertPriorityAndSpeed(UNCHANGED, PUSHING_SECTION_SPEED, way);

        way.clearTags();
        way.setTag("highway", "primary");
        way.setTag("surface", "fine_gravel");
        assertPriorityAndSpeed(BAD, 14, way);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("surface", "gravel");
        way.setTag("tracktype", "grade2");
        assertPriorityAndSpeed(UNCHANGED, 12, way);

        way.clearTags();
        way.setTag("highway", "primary");
        way.setTag("surface", "paved");
        assertPriorityAndSpeed(BAD, 18, way);

        way.clearTags();
        way.setTag("highway", "primary");
        assertPriorityAndSpeed(BAD, 18, way);

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("surface", "asphalt");
        assertPriorityAndSpeed(PREFER, 18, way);

        way.clearTags();
        way.setTag("highway", "motorway");
        way.setTag("bicycle", "yes");
        assertPriorityAndSpeed(REACH_DESTINATION, 18, way);

        way.clearTags();
        way.setTag("highway", "trunk");
        assertPriorityAndSpeed(REACH_DESTINATION, 18, way);
    }

    @Test
    public void testSmoothness() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "residential");
        way.setTag("smoothness", "excellent");
        assertEquals(20, getSpeedFromFlags(way), 0.01);

        way.setTag("smoothness", "bad");
        assertEquals(12, getSpeedFromFlags(way), 0.01);

        way.setTag("smoothness", "impassable");
        assertEquals(MIN_SPEED, getSpeedFromFlags(way), 0.01);

        way.setTag("smoothness", "unknown");
        assertEquals(12, getSpeedFromFlags(way), 0.01);

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("surface", "ground");
        assertEquals(12, getSpeedFromFlags(way), 0.01);

        way.setTag("smoothness", "bad");
        assertEquals(8, getSpeedFromFlags(way), 0.01);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("tracktype", "grade5");
        assertEquals(4, getSpeedFromFlags(way), 0.01);

        way.setTag("smoothness", "bad");
        assertEquals(2, getSpeedFromFlags(way), 0.01);

        way.setTag("smoothness", "impassable");
        assertEquals(MIN_SPEED, getSpeedFromFlags(way), 0.01);
    }

    @Test
    public void testCycleway() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("surface", "paved");
        assertPriority(BAD, way);
        way.setTag("cycleway", "track");
        assertPriority(PREFER, way);

        way.clearTags();
        way.setTag("highway", "primary");
        way.setTag("cycleway:left", "lane");
        assertPriority(SLIGHT_PREFER, way);

        way.clearTags();
        way.setTag("highway", "primary");
        way.setTag("cycleway:right", "lane");
        assertPriority(SLIGHT_PREFER, way);
        way.setTag("cycleway:left", "no");
        assertPriority(SLIGHT_PREFER, way);

        way.clearTags();
        way.setTag("highway", "primary");
        way.setTag("cycleway:both", "lane");
        assertPriority(SLIGHT_PREFER, way);

        way.clearTags();
        way.setTag("highway", "primary");
        way.setTag("oneway", "yes");
        way.setTag("cycleway:left", "opposite_lane");
        assertPriority(BAD, way);

        way.clearTags();
        way.setTag("highway", "primary");
        way.setTag("oneway", "yes");
        way.setTag("cycleway", "opposite_track");
        assertPriority(SLIGHT_PREFER, way);

        way.clearTags();
        way.setTag("highway", "primary");
        way.setTag("cycleway:bicycle", "designated");
        assertPriority(PREFER, way);

        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("bicycle_road", "yes");
        assertPriority(VERY_NICE, way);

        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("cyclestreet", "yes");
        assertPriority(VERY_NICE, way);

        way.clearTags();
        way.setTag("highway", "secondary");
        way.setTag("cycleway", "lane");
        way.setTag("cycleway:lane", "advisory");
        assertPriority(SLIGHT_PREFER, way);
    }

    @Test
    public void testWayAcceptance() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "cycleway");
        way.setTag("vehicle", "no");
        assertTrue(accessParser.getAccess(way).isWay());

        // Sensless tagging: JOSM does create a warning here. We follow the highway tag:
        way.setTag("bicycle", "no");
        assertTrue(accessParser.getAccess(way).isWay());

        way.setTag("bicycle", "designated");
        assertTrue(accessParser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "motorway");
        assertTrue(accessParser.getAccess(way).canSkip());
        way.setTag("bicycle", "yes");
        assertTrue(accessParser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("bicycle", "yes");
        way.setTag("access", "no");
        assertTrue(accessParser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "bridleway");
        assertTrue(accessParser.getAccess(way).canSkip());
        way.setTag("bicycle", "yes");
        assertTrue(accessParser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("vehicle", "forestry");
        assertTrue(accessParser.getAccess(way).canSkip());
        way.setTag("vehicle", "agricultural;forestry");
        assertTrue(accessParser.getAccess(way).canSkip());
    }

    @Test
    public void testHandleWayTagsInfluencedByRelation() {
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "road");

        // unchanged
        assertPriorityAndSpeed(UNCHANGED, 12, osmWay);

        // "lcn=yes" is in fact no relation, but shall be treated the same like a relation with "network=lcn"
        osmWay.setTag("lcn", "yes");
        assertPriorityAndSpeed(PREFER, 12, osmWay);
        osmWay.removeTag("lcn");

        // relation code is PREFER
        ReaderRelation osmRel = new ReaderRelation(1);
        osmRel.setTag("route", "bicycle");
        assertPriorityAndSpeed(PREFER, 12, osmWay, osmRel);

        osmRel.setTag("network", "lcn");
        assertPriorityAndSpeed(PREFER, 12, osmWay, osmRel);

        // relation code is NICE
        osmRel.setTag("network", "rcn");
        assertPriorityAndSpeed(VERY_NICE, 12, osmWay, osmRel);
        osmWay.setTag("lcn", "yes");
        assertPriorityAndSpeed(VERY_NICE, 12, osmWay, osmRel);

        // relation code is BEST
        osmRel.setTag("network", "ncn");
        assertPriorityAndSpeed(BEST, 12, osmWay, osmRel);

        // PREFER relation, but tertiary road => no get off the bike but road wayTypeCode and faster
        osmWay.clearTags();
        osmWay.setTag("highway", "tertiary");
        osmRel.setTag("route", "bicycle");
        osmRel.setTag("network", "lcn");
        assertPriorityAndSpeed(PREFER, 18, osmWay, osmRel);
    }

    @Test
    public void testUnchangedRelationShouldNotInfluencePriority() {
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "secondary");

        ReaderRelation osmRel = new ReaderRelation(1);
        osmRel.setTag("description", "something");
        assertPriorityAndSpeed(AVOID, 18, osmWay, osmRel);
    }

    @Test
    @Override
    public void testSacScale() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "path");
        way.setTag("sac_scale", "hiking");
        assertTrue(accessParser.getAccess(way).isWay());

        way.setTag("highway", "path");
        way.setTag("sac_scale", "mountain_hiking");
        assertTrue(accessParser.getAccess(way).canSkip());

        way.setTag("highway", "cycleway");
        way.setTag("sac_scale", "hiking");
        assertTrue(accessParser.getAccess(way).isWay());

        way.setTag("highway", "cycleway");
        way.setTag("sac_scale", "mountain_hiking");
        // disallow questionable combination as too dangerous
        assertTrue(accessParser.getAccess(way).canSkip());
    }

    @Test
    public void testCalcPriority() {
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "tertiary");
        ReaderRelation osmRel = new ReaderRelation(1);
        osmRel.setTag("route", "bicycle");
        osmRel.setTag("network", "icn");
        IntsRef relFlags = osmParsers.handleRelationTags(osmRel, osmParsers.createRelationFlags());
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(encodingManager.getIntsForFlags());
        int edgeId = 0;
        osmParsers.handleWayTags(edgeId, edgeIntAccess, osmWay, relFlags);
        assertEquals(RouteNetwork.INTERNATIONAL, encodingManager.getEnumEncodedValue(BikeNetwork.KEY, RouteNetwork.class).getEnum(false, edgeId, edgeIntAccess));
        assertEquals(PriorityCode.getValue(BEST.getValue()), priorityEnc.getDecimal(false, edgeId, edgeIntAccess), .1);

        // for some highways the priority is UNCHANGED
        osmRel = new ReaderRelation(1);
        osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "track");
        edgeIntAccess = new ArrayEdgeIntAccess(encodingManager.getIntsForFlags());
        osmParsers.handleWayTags(edgeId, edgeIntAccess, osmWay, osmParsers.createRelationFlags());
        assertEquals(RouteNetwork.MISSING, encodingManager.getEnumEncodedValue(BikeNetwork.KEY, RouteNetwork.class).getEnum(false, edgeId, edgeIntAccess));
        assertEquals(PriorityCode.getValue(UNCHANGED.getValue()), priorityEnc.getDecimal(false, edgeId, edgeIntAccess), .1);

        // unknown highway tags will be excluded but priority will be unchanged
        osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "whatever");
        edgeIntAccess = new ArrayEdgeIntAccess(encodingManager.getIntsForFlags());
        osmParsers.handleWayTags(edgeId, edgeIntAccess, osmWay, osmParsers.createRelationFlags());
        assertFalse(accessParser.getAccessEnc().getBool(false, edgeId, edgeIntAccess));
        assertEquals(RouteNetwork.MISSING, encodingManager.getEnumEncodedValue(BikeNetwork.KEY, RouteNetwork.class).getEnum(false, edgeId, edgeIntAccess));
        assertEquals(PriorityCode.getValue(UNCHANGED.getValue()), priorityEnc.getDecimal(false, edgeId, edgeIntAccess), .1);
    }

    @Test
    public void testMaxSpeed() {
        // the maxspeed is well above our speed and has no effect
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "tertiary");
        way.setTag("maxspeed", "90");
        assertPriorityAndSpeed(UNCHANGED, 18, way);

        way = new ReaderWay(1);
        way.setTag("highway", "track");
        way.setTag("maxspeed", "90");
        assertPriorityAndSpeed(UNCHANGED, 12, way);

        // here we are limited by the maxspeed
        way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("maxspeed", "10");
        assertPriorityAndSpeed(VERY_NICE, 10, way);

        way = new ReaderWay(1);
        way.setTag("highway", "residential");
        way.setTag("maxspeed", "15");
        // todo: speed is larger than maxspeed tag due to rounding and storable max speed is 30
        assertPriorityAndSpeed(VERY_NICE, 16, way);
    }

    // Issue 407 : Always block kissing_gate except for mountainbikes
    @Test
    @Override
    public void testBarrierAccess() {
        // kissing_gate without bicycle tag
        ReaderNode node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "kissing_gate");
        // barrier!
        assertTrue(accessParser.isBarrier(node));

        // kissing_gate with bicycle tag
        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "kissing_gate");
        node.setTag("bicycle", "yes");
        // no barrier!
        assertFalse(accessParser.isBarrier(node));

        // Test if cattle_grid is non blocking
        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "cattle_grid");
        assertFalse(accessParser.isBarrier(node));
    }

    @Test
    public void testClassBicycle() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "tertiary");
        way.setTag("class:bicycle", "3");
        assertPriority(BEST, way);
        // Test that priority cannot get better than best
        way.setTag("scenic", "yes");
        assertPriority(BEST, way);
        way.setTag("scenic", "no");
        way.setTag("class:bicycle", "2");
        assertPriority(VERY_NICE, way);
        way.setTag("class:bicycle", "1");
        assertPriority(PREFER, way);
        way.setTag("class:bicycle", "0");
        assertPriority(UNCHANGED, way);
        way.setTag("class:bicycle", "invalidvalue");
        assertPriority(UNCHANGED, way);
        way.setTag("class:bicycle", "-1");
        assertPriority(SLIGHT_AVOID, way);
        way.setTag("class:bicycle", "-2");
        assertPriority(AVOID, way);
        way.setTag("class:bicycle", "-3");
        assertPriority(AVOID_MORE, way);

        way.setTag("highway", "residential");
        way.setTag("bicycle", "designated");
        way.setTag("class:bicycle", "3");
        assertPriority(BEST, way);

        // Now we test overriding by a specific class subtype
        way.setTag("class:bicycle:touring", "2");
        assertPriority(VERY_NICE, way);

        way.setTag("maxspeed", "15");
        assertPriority(BEST, way);
    }

    @Test
    public void testAvoidMotorway() {
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "motorway");
        osmWay.setTag("bicycle", "yes");
        assertPriority(REACH_DESTINATION, osmWay);
    }

    @Test
    public void temporalAccess() {
        int edgeId = 0;
        ArrayEdgeIntAccess access = new ArrayEdgeIntAccess(1);
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("access:conditional", "no @ (May - June)");
        accessParser.handleWayTags(edgeId, access, way, null);
        assertTrue(accessEnc.getBool(false, edgeId, access));

        access = new ArrayEdgeIntAccess(1);
        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("bicycle:conditional", "no @ (May - June)");
        accessParser.handleWayTags(edgeId, access, way, null);
        assertTrue(accessEnc.getBool(false, edgeId, access));

        access = new ArrayEdgeIntAccess(1);
        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("bicycle", "no");
        way.setTag("access:conditional", "yes @ (May - June)");
        accessParser.handleWayTags(edgeId, access, way, null);
        assertFalse(accessEnc.getBool(false, edgeId, access));

        access = new ArrayEdgeIntAccess(1);
        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("access", "no");
        way.setTag("bicycle:conditional", "yes @ (May - June)");
        accessParser.handleWayTags(edgeId, access, way, null);
        assertTrue(accessEnc.getBool(false, edgeId, access));
    }

}
