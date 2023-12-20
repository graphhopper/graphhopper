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
import com.graphhopper.routing.ev.VehicleAccess;
import com.graphhopper.routing.ev.VehiclePriority;
import com.graphhopper.routing.ev.VehicleSpeed;
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
        return new EncodingManager.Builder().
                add(VehicleAccess.create("mtb")).
                add(VehicleSpeed.create("mtb", 4, 2, false)).
                add(VehiclePriority.create("mtb", 4, PriorityCode.getFactor(1), false)).
        build();
    }

    @Override
    protected VehicleTagParsers createBikeTagParsers(EncodedValueLookup lookup, PMap pMap) {
        return VehicleTagParsers.mtb(lookup, pMap);
    }

    @Test
    public void testSpeedAndPriority() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        assertPriorityAndSpeed(BAD, 18, way);

        way.setTag("highway", "residential");
        assertPriorityAndSpeed(PREFER, 16, way);

        // Test pushing section speeds
        way.setTag("highway", "footway");
        assertPriorityAndSpeed(SLIGHT_AVOID, PUSHING_SECTION_SPEED, way);

        way.setTag("highway", "track");
        assertPriorityAndSpeed(PREFER, 18, way);

        // test speed for allowed pushing section types
        way.setTag("highway", "track");
        way.setTag("bicycle", "yes");
        assertPriorityAndSpeed(PREFER, 18, way);

        way.setTag("highway", "track");
        way.setTag("bicycle", "yes");
        way.setTag("tracktype", "grade3");
        assertPriorityAndSpeed(VERY_NICE, 12, way);

        way.setTag("surface", "paved");
        assertPriorityAndSpeed(VERY_NICE, 18, way);

        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("surface", "ground");
        assertPriorityAndSpeed(PREFER, 16, way);
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
    public void testHandleWayTagsInfluencedByBikeAndMtbRelation() {
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "track");

        ReaderRelation osmRel = new ReaderRelation(1);
        // unchanged
        assertPriorityAndSpeed(PREFER, 18, osmWay, osmRel);

        // relation code is PREFER
        osmRel.setTag("route", "bicycle");
        osmRel.setTag("network", "lcn");
        assertPriorityAndSpeed(BEST, 18, osmWay, osmRel);

        // relation code is PREFER
        osmRel.setTag("network", "rcn");
        assertPriorityAndSpeed(PREFER, 18, osmWay, osmRel);

        // relation code is PREFER
        osmRel.setTag("network", "ncn");
        assertPriorityAndSpeed(PREFER, 18, osmWay, osmRel);

        // PREFER relation, but tertiary road
        // => no pushing section but road wayTypeCode and faster
        osmWay.clearTags();
        osmWay.setTag("highway", "tertiary");

        osmRel.setTag("route", "bicycle");
        osmRel.setTag("network", "lcn");
        assertPriorityAndSpeed(BEST, 18, osmWay, osmRel);

        osmWay.clearTags();
        osmRel.clearTags();
        osmWay.setTag("highway", "track");
        // unchanged
        assertPriorityAndSpeed(PREFER, 18, osmWay, osmRel);

        osmRel.setTag("route", "mtb");
        osmRel.setTag("network", "lcn");
        assertPriorityAndSpeed(PREFER, 18, osmWay, osmRel);

        osmRel.setTag("network", "rcn");
        assertPriorityAndSpeed(PREFER, 18, osmWay, osmRel);

        osmRel.setTag("network", "ncn");
        assertPriorityAndSpeed(PREFER, 18, osmWay, osmRel);

        osmWay.clearTags();
        osmWay.setTag("highway", "tertiary");

        osmRel.setTag("route", "mtb");
        osmRel.setTag("network", "lcn");
        assertPriorityAndSpeed(PREFER, 18, osmWay, osmRel);
    }

    // Issue 407 : Always block kissing_gate except for mountainbikes
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
