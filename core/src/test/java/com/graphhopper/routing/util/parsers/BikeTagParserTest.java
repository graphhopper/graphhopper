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
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.Test;

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
                .add(VehiclePriority.create("bike", 4, 0.1, false))
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
        assertPriorityAndSpeed(0.5, 18, way);

        way.setTag("scenic", "yes");
        assertPriorityAndSpeed(0.6, 18, way);

        way.clearTags();
        way.setTag("highway", "living_street");
        assertPriorityAndSpeed(1.0, 6, way);

        // Pushing section: this is fine as we obey the law!
        way.clearTags();
        way.setTag("highway", "footway");
        assertPriorityAndSpeed(0.9, 6, way);

        // Use pushing section irrespective of the pavement
        way.setTag("surface", "paved");
        assertPriorityAndSpeed(0.9, 6, way);

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("bicycle", "use_sidepath");
        assertPriorityAndSpeed(0.1, 18, way);

        way.clearTags();
        way.setTag("highway", "secondary");
        way.setTag("bicycle", "dismount");
        assertPriorityAndSpeed(0.8, PUSHING_SECTION_SPEED, way);

        way.clearTags();
        way.setTag("highway", "primary");
        way.setTag("surface", "fine_gravel");
        assertPriorityAndSpeed(0.5, 14, way);

        way.clearTags();
        way.setTag("highway", "primary");
        way.setTag("surface", "paved");
        assertPriorityAndSpeed(0.5, 18, way);

        way.clearTags();
        way.setTag("highway", "primary");
        assertPriorityAndSpeed(0.5, 18, way);

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("surface", "asphalt");
        assertPriorityAndSpeed(1.2, 18, way);

        way.clearTags();
        way.setTag("highway", "motorway");
        way.setTag("bicycle", "yes");
        assertPriorityAndSpeed(0.1, 18, way);

        way.clearTags();
        way.setTag("highway", "trunk");
        assertPriorityAndSpeed(0.1, 18, way);

        way.clearTags();
        way.setTag("highway", "platform");
        way.setTag("surface", "paved");
        assertPriorityAndSpeed(0.9, 6, way);

        way.clearTags();
        way.setTag("highway", "platform");
        way.setTag("surface", "paved");
        way.setTag("bicycle", "yes");
        assertPriorityAndSpeed(1.1, 12, way);
        way.setTag("segregated", "yes");
        assertPriorityAndSpeed(1.2, 18, way);

        way.clearTags();
        way.setTag("highway", "platform");
        way.setTag("surface", "paved");
        way.setTag("bicycle", "designated");
        assertPriorityAndSpeed(1.2, 18, way);

        way.clearTags();
        way.setTag("highway", "footway");
        way.setTag("bicycle", "yes");
        assertPriorityAndSpeed(1.1, 12, way);
        way.setTag("segregated", "no");
        assertPriorityAndSpeed(1.1, 12, way);
        way.setTag("segregated", "yes");
        assertPriorityAndSpeed(1.2, 18, way);

        way.clearTags();
        way.setTag("highway", "footway");
        way.setTag("surface", "paved");
        way.setTag("bicycle", "yes");
        assertPriorityAndSpeed(1.1, 12, way);
        way.setTag("surface", "cobblestone");
        assertPriorityAndSpeed(1.1, 8, way);
        way.setTag("segregated", "yes");
        way.setTag("surface", "paved");
        assertPriorityAndSpeed(1.2, 18, way);

        way.clearTags();
        way.setTag("highway", "footway");
        way.setTag("surface", "paved");
        way.setTag("bicycle", "designated");
        assertPriorityAndSpeed(1.2, 18, way);
        way.clearTags();

        way.setTag("highway", "footway");
        way.setTag("tracktype", "grade4");
        way.setTag("bicycle", "designated");
        assertPriorityAndSpeed(1.2, 6, way);

        way.clearTags();
        way.setTag("highway", "steps");
        assertPriorityAndSpeed(0.5, 2, way);

        way.clearTags();
        way.setTag("highway", "steps");
        way.setTag("surface", "wood");
        assertPriorityAndSpeed(0.5, MIN_SPEED, way);
        way.setTag("maxspeed", "20");
        assertPriorityAndSpeed(0.5, MIN_SPEED, way);

        way.clearTags();
        way.setTag("highway", "bridleway");
        assertPriorityAndSpeed(0.8, 6, way);
        way.setTag("surface", "gravel");
        assertPriorityAndSpeed(0.8, 8, way);
        way.setTag("bicycle", "designated");
        assertPriorityAndSpeed(1.2, 12, way);
    }

    @Test
    public void testPathAndCycleway() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "path");
        assertPriorityAndSpeed(0.9, 6, way);

        // Make sure that "highway=cycleway" and "highway=path" with "bicycle=designated" give the same result
        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("bicycle", "designated");
        // Assume foot=no for designated in absence of a foot tag
        assertPriorityAndSpeed(1.2, 18, way);
        way.setTag("foot", "no");
        assertPriorityAndSpeed(1.2, 18, way);
        way.setTag("foot", "yes");
        assertPriorityAndSpeed(1.2, 18, way);

        way.setTag("segregated", "yes");
        assertPriorityAndSpeed(1.2, 18, way);
        way.setTag("segregated", "no");
        assertPriorityAndSpeed(1.2, 18, way);

        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("bicycle", "yes");
        way.setTag("foot", "yes");
        way.setTag("segregated", "no");
        assertPriorityAndSpeed(1.2, 12, way);

        way.setTag("segregated", "yes");
        assertPriorityAndSpeed(1.2, 18, way);

        way.setTag("surface", "unpaved");
        assertPriorityAndSpeed(1.2, 12, way);

        way.setTag("surface", "paved");
        assertPriorityAndSpeed(1.3, 18, way);

        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("surface", "paved");
        assertPriorityAndSpeed(0.9, 12, way);

        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("tracktype", "grade1");
        assertPriorityAndSpeed(0.9, 12, way);

        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("surface", "ground");
        assertPriorityAndSpeed(0.9, 8, way);

        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("bicycle", "designated");
        way.setTag("tracktype", "grade4");
        assertPriorityAndSpeed(1.2, 6, way);

        way.clearTags();
        way.setTag("highway", "cycleway");
        assertPriorityAndSpeed(1.3, 18, way);
        way.setTag("foot", "yes");
        assertPriorityAndSpeed(1.2, 18, way);
        way.setTag("segregated", "yes");
        assertPriorityAndSpeed(1.3, 18, way);
        way.setTag("segregated", "no");
        assertPriorityAndSpeed(1.2, 18, way);

        way.clearTags();
        way.setTag("highway", "cycleway");
        way.setTag("vehicle", "no");
        assertPriorityAndSpeed(1.3, PUSHING_SECTION_SPEED, way);
        way.setTag("bicycle", "yes");
        assertPriorityAndSpeed(1.3, 18, way);
    }

    @Test
    public void testTrack() {
        ReaderWay way = new ReaderWay(1);
        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("bicycle", "designated");
        // lower speed might be better as no surface tag, but strange tagging anyway and rare in real world
        assertPriorityAndSpeed(1.2, 18, way);
        way.setTag("segregated", "no");
        assertPriorityAndSpeed(1.2, 18, way);
        way.setTag("surface", "asphalt");
        assertPriorityAndSpeed(1.3, 18, way);
        way.setTag("tracktype", "grade1");
        assertPriorityAndSpeed(1.3, 18, way);
        way.removeTag("surface");
        assertPriorityAndSpeed(1.3, 18, way);

        way.clearTags();
        way.setTag("highway", "track");
        assertPriorityAndSpeed(1.0, 12, way);
        way.setTag("vehicle", "no");
        assertPriorityAndSpeed(1.0, PUSHING_SECTION_SPEED, way);
        way.setTag("vehicle", "forestry;agricultural");
        assertPriorityAndSpeed(1.0, PUSHING_SECTION_SPEED, way);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("surface", "concrete");
        way.setTag("vehicle", "agricultural");
        assertPriorityAndSpeed(1.0, PUSHING_SECTION_SPEED, way);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("tracktype", "grade1");
        assertPriorityAndSpeed(1.0, 18, way);

        way.setTag("highway", "track");
        way.setTag("tracktype", "grade2");
        assertPriorityAndSpeed(1.0, 12, way);

        // test speed for allowed get off the bike types
        way.setTag("highway", "track");
        way.setTag("bicycle", "yes");
        assertPriorityAndSpeed(1.0, 12, way);

        way.clearTags();
        way.setTag("highway", "track");
        assertPriorityAndSpeed(1.0, 12, way);

        way.setTag("surface", "paved");
        assertPriorityAndSpeed(1.0, 18, way);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("bicycle", "yes");
        way.setTag("surface", "fine_gravel");
        assertPriorityAndSpeed(1.0, 14, way);

        way.setTag("surface", "unknown_surface");
        assertPriorityAndSpeed(1.0, PUSHING_SECTION_SPEED, way);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("surface", "gravel");
        way.setTag("tracktype", "grade2");
        assertPriorityAndSpeed(1.0, 12, way);
    }

    @Test
    public void testSmoothness() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "residential");
        way.setTag("smoothness", "excellent");
        assertEquals(20, getSpeedFromFlags(way), 0.01);

        way.setTag("smoothness", "0.5");
        assertEquals(12, getSpeedFromFlags(way), 0.01);

        way.setTag("smoothness", "impassable");
        assertEquals(MIN_SPEED, getSpeedFromFlags(way), 0.01);

        way.setTag("smoothness", "unknown");
        assertEquals(12, getSpeedFromFlags(way), 0.01);

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("surface", "ground");
        assertEquals(10, getSpeedFromFlags(way), 0.01);

        way.setTag("smoothness", "0.5");
        assertEquals(8, getSpeedFromFlags(way), 0.01);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("tracktype", "grade5");
        assertEquals(4, getSpeedFromFlags(way), 0.01);

        way.setTag("smoothness", "0.5");
        assertEquals(2, getSpeedFromFlags(way), 0.01);

        way.setTag("smoothness", "impassable");
        assertEquals(MIN_SPEED, getSpeedFromFlags(way), 0.01);
    }

    @Test
    public void testLowMaxSpeedIsIgnored() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "residential");
        way.setTag("maxspeed", "3");
        assertEquals(18, getSpeedFromFlags(way), 0.01);
    }

    @Test
    public void testCycleway() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("surface", "paved");
        assertPriority(0.5, way);
        way.setTag("cycleway", "track");
        assertPriority(1.2, way);

        way.clearTags();
        way.setTag("highway", "primary");
        way.setTag("cycleway:left", "lane");
        assertPriority(1.1, way);

        way.clearTags();
        way.setTag("highway", "primary");
        way.setTag("cycleway:right", "lane");
        assertPriority(1.1, way);
        way.setTag("cycleway:left", "no");
        assertPriority(1.1, way);

        way.clearTags();
        way.setTag("highway", "primary");
        way.setTag("cycleway:both", "lane");
        assertPriority(1.1, way);

        way.clearTags();
        way.setTag("highway", "primary");
        way.setTag("oneway", "yes");
        way.setTag("cycleway:left", "opposite_lane");
        assertPriority(0.5, way);

        way.clearTags();
        way.setTag("highway", "primary");
        way.setTag("oneway", "yes");
        way.setTag("cycleway", "opposite_track");
        assertPriority(1.1, way);

        way.clearTags();
        way.setTag("highway", "primary");
        way.setTag("cycleway:bicycle", "designated");
        assertPriority(1.2, way);

        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("bicycle_road", "yes");
        assertPriority(1.2, way);

        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("cyclestreet", "yes");
        assertPriority(1.2, way);

        way.clearTags();
        way.setTag("highway", "secondary");
        way.setTag("cycleway", "lane");
        way.setTag("cycleway:lane", "advisory");
        assertPriority(1.1, way);
    }

    @Test
    public void testWayAcceptance() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "cycleway");
        way.setTag("vehicle", "no");
        assertTrue(accessParser.getAccess(way).isWay());

        // Senseless tagging: JOSM does create a warning here:
        way.setTag("bicycle", "no");
        assertTrue(accessParser.getAccess(way).canSkip());

        way.setTag("bicycle", "designated");
        assertTrue(accessParser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "cycleway");
        way.setTag("access", "no");
        assertTrue(accessParser.getAccess(way).canSkip());
        way.setTag("bicycle", "no");
        assertTrue(accessParser.getAccess(way).canSkip());

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
        // exclude bridleway for a single country via custom model
        way.setTag("highway", "bridleway");
        assertTrue(accessParser.getAccess(way).isWay());
        way.setTag("bicycle", "yes");
        assertTrue(accessParser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("vehicle", "forestry");
        assertTrue(accessParser.getAccess(way).isWay());
        way.setTag("vehicle", "agricultural;forestry");
        assertTrue(accessParser.getAccess(way).isWay());
    }

    @Test
    public void testPreferenceForSlowSpeed() {
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "tertiary");
        assertPriority(1.0, osmWay);
    }

    @Test
    public void testUnchangedRelationShouldNotInfluencePriority() {
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "secondary");

        ReaderRelation osmRel = new ReaderRelation(1);
        osmRel.setTag("description", "something");
        assertPriorityAndSpeed(0.8, 18, osmWay, osmRel);
    }

    @Test
    public void testMaxSpeed() {
        // the maxspeed is well above our speed and has no effect
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "tertiary");
        way.setTag("maxspeed", "90");
        assertPriorityAndSpeed(0.8, 18, way);

        way = new ReaderWay(1);
        way.setTag("highway", "track");
        way.setTag("maxspeed", "90");
        assertPriorityAndSpeed(1.0, 12, way);

        // here we are limited by the maxspeed
        way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("maxspeed", "10");
        assertPriorityAndSpeed(1.3, 10, way);

        way = new ReaderWay(1);
        way.setTag("highway", "residential");
        way.setTag("maxspeed", "15");
        // todo: speed is larger than maxspeed tag due to rounding and storable max speed is 30
        assertPriorityAndSpeed(1.3, 16, way);
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
        assertPriority(1.5, way);
        // Test that priority cannot get better than 1.5
        way.setTag("scenic", "yes");
        assertPriority(1.5, way);
        way.setTag("scenic", "no");
        way.setTag("class:bicycle", "2");
        assertPriority(1.3, way);
        way.setTag("class:bicycle", "1");
        assertPriority(1.2, way);
        way.setTag("class:bicycle", "0");
        assertPriority(1.0, way);
        way.setTag("class:bicycle", "invalidvalue");
        assertPriority(1.0, way);
        way.setTag("class:bicycle", "-1");
        assertPriority(0.8, way);
        way.setTag("class:bicycle", "-2");
        assertPriority(0.5, way);
        way.setTag("class:bicycle", "-3");
        assertPriority(0.1, way);

        way.setTag("highway", "residential");
        way.setTag("bicycle", "designated");
        way.setTag("class:bicycle", "3");
        assertPriority(1.5, way);

        // test overriding by a specific class subtype
        way.setTag("class:bicycle:touring", "2");
        assertPriority(1.3, way);

        way.setTag("maxspeed", "15");
        assertPriority(1.4, way);

        // do not overwrite better priority
        way = new ReaderWay(1);
        way.setTag("highway", "path");
        way.setTag("bicycle", "designated");
        way.setTag("surface", "asphalt");
        way.setTag("class:bicycle", "1");
        assertPriority(1.3, way);
    }

    @Test
    public void testAvoidMotorway() {
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "motorway");
        osmWay.setTag("bicycle", "yes");
        assertPriority(0.1, osmWay);
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
        way.setTag("bicycle", "no");
        way.setTag("bicycle:conditional", "yes @ (21:00-9:00)");
        accessParser.handleWayTags(edgeId, access, way, null);
        assertTrue(accessEnc.getBool(false, edgeId, access));
    }

    @Test
    public void temporalAccessWithPermit() {
        BikeCommonAccessParser tmpAccessParser = createAccessParser(encodingManager, new PMap("block_private=false"));

        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("bicycle", "no");
        way.setTag("bicycle:conditional", "permit @ (21:00-9:00)");

        int edgeId = 0;
        ArrayEdgeIntAccess access = new ArrayEdgeIntAccess(1);
        tmpAccessParser.handleWayTags(edgeId, access, way, null);
        assertTrue(accessEnc.getBool(false, edgeId, access));

        access = new ArrayEdgeIntAccess(1);
        accessParser.handleWayTags(edgeId, access, way, null);
        assertFalse(accessEnc.getBool(false, edgeId, access));
    }

    @Test
    public void testPedestrian() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "pedestrian");
        assertPriorityAndSpeed(0.9, 6, way);
        way.setTag("bicycle", "yes");
        assertPriorityAndSpeed(1.1, 12, way);
        way.setTag("surface", "asphalt");
        assertPriorityAndSpeed(1.1, 12, way);

        way.clearTags();
        way.setTag("highway", "pedestrian");
        way.setTag("cycleway:right", "track");
        assertPriorityAndSpeed(1.2, 18, way);
        way.setTag("bicycle", "yes");
        assertPriorityAndSpeed(1.2, 18, way);
    }
}
