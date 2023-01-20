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

import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.PriorityCode;
import com.graphhopper.routing.util.WayAccess;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.graphhopper.routing.util.PriorityCode.*;
import static com.graphhopper.routing.util.WayAccess.WAY;
import static com.graphhopper.routing.util.parsers.BikeCommonAverageSpeedParser.MIN_SPEED;
import static com.graphhopper.routing.util.parsers.BikeCommonAverageSpeedParser.PUSHING_SECTION_SPEED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author ratrun
 */
public class RacingBikeTagParserTest extends AbstractBikeTagParserTester {

    @Override
    protected String getParserPrefix() {
        return "racingbike";
    }

    @Test
    @Override
    public void testAvoidTunnel() {
        // tunnel is not that bad for racing bike
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "residential");
        osmWay.setTag("tunnel", "yes");
        assertPriorityAndSpeed(UNCHANGED.getValue(), 16, osmWay);

        osmWay.setTag("highway", "secondary");
        osmWay.setTag("tunnel", "yes");
        assertPriorityAndSpeed(UNCHANGED.getValue(), 20, osmWay);

        osmWay.setTag("bicycle", "designated");
        assertPriorityAndSpeed(PREFER.getValue(), 20, osmWay);
    }

    @Test
    @Override
    public void testService() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "service");
        assertPriorityAndSpeed(UNCHANGED.getValue(), 12, way);

        way.setTag("service", "parking_aisle");
        assertPriorityAndSpeed(SLIGHT_AVOID.getValue(), 4, way);
    }

    @Test
    @Override
    public void testSacScale() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "service");
        way.setTag("sac_scale", "mountain_hiking");
        assertTrue(accessParser.getAccess(way).canSkip());

        way.setTag("highway", "path");
        way.setTag("sac_scale", "hiking");
        assertTrue(accessParser.getAccess(way).isWay());

        // This looks to be tagging error:
        way.setTag("highway", "cycleway");
        way.setTag("sac_scale", "mountain_hiking");
        // we are cautious and disallow this
        assertTrue(accessParser.getAccess(way).canSkip());
    }

    @Test
    public void testGetSpeed() {
        IntsRef intsRef = encodingManager.createEdgeFlags();
        avgSpeedEnc.setDecimal(false, intsRef, 10);
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "track");
        way.setTag("tracktype", "grade3");
        // use pushing section
        assertEquals(PUSHING_SECTION_SPEED, getSpeedFromFlags(way), 1e-1);

        // Even if it is part of a cycle way
        way.setTag("bicycle", "yes");
        assertEquals(PUSHING_SECTION_SPEED, getSpeedFromFlags(way), 1e-1);

        way.clearTags();
        way.setTag("highway", "steps");
        assertEquals(2, getSpeedFromFlags(way), 1e-1);

        way.clearTags();
        way.setTag("highway", "primary");
        assertEquals(20, getSpeedFromFlags(way), 1e-1);

        way.clearTags();
        way.setTag("highway", "primary");
        way.setTag("surface", "paved");
        assertEquals(20, getSpeedFromFlags(way), 1e-1);

        way.clearTags();
        way.setTag("highway", "primary");
        way.setTag("surface", "unknownpavement");
        assertEquals(PUSHING_SECTION_SPEED, getSpeedFromFlags(way), 1e-1);
    }

    @Test
    public void testSmoothness() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "residential");
        assertEquals(16, getSpeedFromFlags(way), 0.01);

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
        assertEquals(MIN_SPEED, getSpeedFromFlags(way), 0.01);

        way.setTag("smoothness", "bad");
        assertEquals(MIN_SPEED, getSpeedFromFlags(way), 0.01);

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
    public void testHandleWayTagsInfluencedByRelation() {
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "track");
        assertEquals(MIN_SPEED, getSpeedFromFlags(osmWay), 1e-1);

        // relation code is PREFER
        ReaderRelation osmRel = new ReaderRelation(1);
        osmRel.setTag("route", "bicycle");
        osmRel.setTag("network", "lcn");
        assertPriorityAndSpeed(AVOID_MORE.getValue(), 2, osmWay, osmRel);
        // relation code is OUTSTANDING NICE but as unpaved, the speed is still PUSHING_SECTION_SPEED/2
        osmRel.setTag("network", "icn");
        assertPriorityAndSpeed(AVOID_MORE.getValue(), 2, osmWay, osmRel);

        // Now we assume bicycle=yes, anyhow still unpaved
        osmWay.setTag("bicycle", "yes");
        assertPriorityAndSpeed(AVOID_MORE.getValue(), 2, osmWay, osmRel);

        // Now we assume bicycle=yes, and paved
        osmWay.setTag("tracktype", "grade1");
        assertPriorityAndSpeed(PREFER.getValue(), 20, osmWay, osmRel);

        // Now we assume bicycle=yes, and unpaved as part of a cycle relation
        osmWay.setTag("tracktype", "grade2");
        osmWay.setTag("bicycle", "yes");
        assertPriorityAndSpeed(AVOID_MORE.getValue(), 10, osmWay, osmRel);

        // Now we assume bicycle=yes, and unpaved not part of a cycle relation
        osmWay.clearTags();
        osmWay.setTag("highway", "track");
        osmWay.setTag("tracktype", "grade3");
        assertPriorityAndSpeed(AVOID_MORE.getValue(), PUSHING_SECTION_SPEED, osmWay);

        // Now we assume bicycle=yes, and tracktype = null
        osmWay.clearTags();
        osmWay.setTag("highway", "track");
        assertPriorityAndSpeed(AVOID_MORE.getValue(), 2, osmWay);
    }

    @Test
    public void testPriority_avoidanceOfHighMaxSpeed() {
        // here we test the priority that would be calculated if the way was accessible (even when it is not)
        // therefore we need a modified parser that always yields access=WAY
        BooleanEncodedValue accessEnc = VehicleAccess.create("racingbike");
        DecimalEncodedValue speedEnc = VehicleSpeed.create("racingbike", 4, 2, false);
        DecimalEncodedValue priorityEnc = VehiclePriority.create("racingbike", 4, PriorityCode.getValue(1), false);
        EncodingManager encodingManager = EncodingManager.start()
                .add(accessEnc).add(speedEnc).add(priorityEnc)
                .add(new EnumEncodedValue<>(BikeNetwork.KEY, RouteNetwork.class))
                .add(new EnumEncodedValue<>(Smoothness.KEY, Smoothness.class))
                .build();
        List<TagParser> parsers = Arrays.asList(new RacingBikeAverageSpeedParser(encodingManager, new PMap()),
                new RacingBikePriorityParser(encodingManager, new PMap()));
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "tertiary");
        osmWay.setTag("maxspeed", "50");
        assertPriorityAndSpeed(encodingManager, priorityEnc, speedEnc, parsers, PREFER.getValue(), 20, osmWay);

        osmWay.setTag("maxspeed", "60");
        assertPriorityAndSpeed(encodingManager, priorityEnc, speedEnc, parsers, PREFER.getValue(), 20, osmWay);

        osmWay.setTag("maxspeed", "80");
        assertPriorityAndSpeed(encodingManager, priorityEnc, speedEnc, parsers, PREFER.getValue(), 20, osmWay);

        osmWay.setTag("maxspeed", "90");
        assertPriorityAndSpeed(encodingManager, priorityEnc, speedEnc, parsers, UNCHANGED.getValue(), 20, osmWay);

        osmWay.setTag("maxspeed", "120");
        assertPriorityAndSpeed(encodingManager, priorityEnc, speedEnc, parsers, UNCHANGED.getValue(), 20, osmWay);

        osmWay.setTag("highway", "motorway");
        assertPriorityAndSpeed(encodingManager, priorityEnc, speedEnc, parsers, AVOID.getValue(), 18, osmWay);

        osmWay.setTag("tunnel", "yes");
        assertPriorityAndSpeed(encodingManager, priorityEnc, speedEnc, parsers, BAD.getValue(), 18, osmWay);

        osmWay.clearTags();
        osmWay.setTag("highway", "motorway");
        osmWay.setTag("tunnel", "yes");
        osmWay.setTag("maxspeed", "80");
        assertPriorityAndSpeed(encodingManager, priorityEnc, speedEnc, parsers, BAD.getValue(), 18, osmWay);

        osmWay.clearTags();
        osmWay.setTag("highway", "motorway");
        osmWay.setTag("tunnel", "yes");
        osmWay.setTag("maxspeed", "120");
        assertPriorityAndSpeed(encodingManager, priorityEnc, speedEnc, parsers, BAD.getValue(), 18, osmWay);

        osmWay.clearTags();
        osmWay.setTag("highway", "notdefined");
        osmWay.setTag("tunnel", "yes");
        osmWay.setTag("maxspeed", "120");
        assertPriorityAndSpeed(encodingManager, priorityEnc, speedEnc, parsers, BAD.getValue(), PUSHING_SECTION_SPEED, osmWay);

        osmWay.clearTags();
        osmWay.setTag("highway", "notdefined");
        osmWay.setTag("maxspeed", "50");
        assertPriorityAndSpeed(encodingManager, priorityEnc, speedEnc, parsers, UNCHANGED.getValue(), PUSHING_SECTION_SPEED, osmWay);
    }

    private void assertPriorityAndSpeed(EncodingManager encodingManager, DecimalEncodedValue priorityEnc, DecimalEncodedValue speedEnc,
                                        List<TagParser> parsers, int expectedPrio, double expectedSpeed, ReaderWay way) {
        IntsRef edgeFlags = encodingManager.createEdgeFlags();
        for (TagParser p : parsers) edgeFlags = p.handleWayTags(edgeFlags, way, null);
        assertEquals(PriorityCode.getValue(expectedPrio), priorityEnc.getDecimal(false, edgeFlags), 0.01);
        assertEquals(expectedSpeed, speedEnc.getDecimal(false, edgeFlags), 0.1);
    }

    @Test
    public void testClassBicycle() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "tertiary");
        way.setTag("class:bicycle:roadcycling", "3");
        assertPriority(BEST.getValue(), way);

        way.setTag("class:bicycle", "-2");
        assertPriority(BEST.getValue(), way);
    }
}
