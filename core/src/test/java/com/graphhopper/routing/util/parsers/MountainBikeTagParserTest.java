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
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.PriorityCode;
import com.graphhopper.routing.util.VehicleEncodedValues;
import com.graphhopper.routing.util.VehicleTagParsers;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.Test;

import static com.graphhopper.routing.util.PriorityCode.*;
import static com.graphhopper.routing.util.parsers.BikeCommonAverageSpeedParser.MIN_SPEED;
import static com.graphhopper.routing.util.parsers.BikeCommonAverageSpeedParser.PUSHING_SECTION_SPEED;
import static org.junit.jupiter.api.Assertions.*;

public class MountainBikeTagParserTest extends AbstractBikeTagParserTester {
    @Override
    protected EncodingManager createEncodingManager() {
        return new EncodingManager.Builder().add(VehicleEncodedValues.mountainbike(new PMap())).build();
    }

    @Override
    protected VehicleTagParsers createBikeTagParsers(EncodedValueLookup lookup, PMap pMap) {
        return VehicleTagParsers.mtb(lookup, pMap);
    }

    @Test
    public void testSpeedAndPriority() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        assertPriorityAndSpeed(BAD.getValue(), 18, way);

        way.setTag("highway", "residential");
        assertPriorityAndSpeed(PREFER.getValue(), 16, way);

        // Test pushing section speeds
        way.setTag("highway", "footway");
        assertPriorityAndSpeed(SLIGHT_AVOID.getValue(), PUSHING_SECTION_SPEED, way);

        way.setTag("highway", "track");
        assertPriorityAndSpeed(PREFER.getValue(), 18, way);

        way.setTag("highway", "steps");
        assertPriorityAndSpeed(SLIGHT_AVOID.getValue(), PUSHING_SECTION_SPEED, way);
        way.clearTags();

        // test speed for allowed pushing section types
        way.setTag("highway", "track");
        way.setTag("bicycle", "yes");
        assertPriorityAndSpeed(PREFER.getValue(), 18, way);

        way.setTag("highway", "track");
        way.setTag("bicycle", "yes");
        way.setTag("tracktype", "grade3");
        assertPriorityAndSpeed(VERY_NICE.getValue(), 12, way);

        way.setTag("surface", "paved");
        assertPriorityAndSpeed(VERY_NICE.getValue(), 18, way);

        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("surface", "ground");
        assertPriorityAndSpeed(PREFER.getValue(), 16, way);
    }

    @Test
    public void testSmoothness() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "residential");
        way.setTag("smoothness", "excellent");
        assertEquals(18, getSpeedFromFlags(way), 0.01);

        way.setTag("smoothness", "bad");
        assertEquals(12, getSpeedFromFlags(way), 0.01);

        way.setTag("smoothness", "impassable");
        assertEquals(MIN_SPEED, getSpeedFromFlags(way), 0.01);

        way.setTag("smoothness", "unknown");
        assertEquals(12, getSpeedFromFlags(way), 0.01);

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("surface", "ground");
        assertEquals(16, getSpeedFromFlags(way), 0.01);

        way.setTag("smoothness", "bad");
        assertEquals(12, getSpeedFromFlags(way), 0.01);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("tracktype", "grade5");
        assertEquals(6, getSpeedFromFlags(way), 0.01);

        way.setTag("smoothness", "bad");
        assertEquals(4, getSpeedFromFlags(way), 0.01);

        way.setTag("smoothness", "impassable");
        assertEquals(MIN_SPEED, getSpeedFromFlags(way), 0.01);
    }

    @Test
    @Override
    public void testSacScale() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "service");
        way.setTag("sac_scale", "hiking");
        assertTrue(accessParser.getAccess(way).isWay());

        way.setTag("highway", "service");
        way.setTag("sac_scale", "mountain_hiking");
        assertTrue(accessParser.getAccess(way).isWay());

        way.setTag("sac_scale", "alpine_hiking");
        assertTrue(accessParser.getAccess(way).isWay());

        way.setTag("sac_scale", "demanding_alpine_hiking");
        assertTrue(accessParser.getAccess(way).canSkip());
    }

    @Test
    public void testHandleWayTagsInfluencedByRelation() {
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "track");

        ReaderRelation osmRel = new ReaderRelation(1);
        // unchanged
        assertPriorityAndSpeed(PriorityCode.PREFER.getValue(), 18, osmWay);

        // relation code is PREFER
        osmRel.setTag("route", "bicycle");
        osmRel.setTag("network", "lcn");
        assertPriorityAndSpeed(PriorityCode.PREFER.getValue(), 18, osmWay);

        // relation code is PREFER
        osmRel.setTag("network", "rcn");
        assertPriorityAndSpeed(PriorityCode.PREFER.getValue(), 18, osmWay);

        // relation code is PREFER
        osmRel.setTag("network", "ncn");
        assertPriorityAndSpeed(PriorityCode.PREFER.getValue(), 18, osmWay);

        // PREFER relation, but tertiary road
        // => no pushing section but road wayTypeCode and faster
        osmWay.clearTags();
        osmWay.setTag("highway", "tertiary");

        osmRel.setTag("route", "bicycle");
        osmRel.setTag("network", "lcn");
        assertPriorityAndSpeed(PriorityCode.PREFER.getValue(), 18, osmWay);
    }

    // Issue 407 : Always block kissing_gate execpt for mountainbikes
    @Test
    @Override
    public void testBarrierAccess() {
        // kissing_gate without bicycle tag
        ReaderNode node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "kissing_gate");
        // No barrier!
        assertFalse(accessParser.isBarrier(node));

        // kissing_gate with bicycle tag = no
        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "kissing_gate");
        node.setTag("bicycle", "no");
        // barrier!
        assertTrue(accessParser.isBarrier(node));

        // kissing_gate with bicycle tag
        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "kissing_gate");
        node.setTag("bicycle", "yes");
        // No barrier!
        assertFalse(accessParser.isBarrier(node));
    }

}
