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
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.graphhopper.routing.util.parsers.BikeCommonAverageSpeedParser.MIN_SPEED;
import static com.graphhopper.routing.util.parsers.BikeCommonAverageSpeedParser.PUSHING_SECTION_SPEED;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author ratrun
 */
public class RacingBikeTagParserTest extends AbstractBikeTagParserTester {
    @Override
    protected EncodingManager createEncodingManager() {
        return new EncodingManager.Builder()
                .add(VehicleAccess.create("racingbike"))
                .add(VehicleSpeed.create("racingbike", 4, 2, false))
                .add(VehiclePriority.create("racingbike", 4, 0.1, false))
                .add(Roundabout.create())
                .add(Smoothness.create())
                .add(FerrySpeed.create())
                .add(RouteNetwork.create(BikeNetwork.KEY))
                .add(RouteNetwork.create(MtbNetwork.KEY))
                .build();
    }

    @Override
    protected BikeCommonAccessParser createAccessParser(EncodedValueLookup lookup, PMap pMap) {
        return new RacingBikeAccessParser(lookup, pMap);
    }

    @Override
    protected BikeCommonAverageSpeedParser createAverageSpeedParser(EncodedValueLookup lookup) {
        return new RacingBikeAverageSpeedParser(lookup);
    }

    @Override
    protected BikeCommonPriorityParser createPriorityParser(EncodedValueLookup lookup) {
        return new RacingBikePriorityParser(lookup);
    }

    @Test
    @Override
    public void testAvoidTunnel() {
        // tunnel is not that bad for racing bike
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "residential");
        osmWay.setTag("tunnel", "yes");
        assertPriorityAndSpeed(0.9, 18, osmWay);

        osmWay.setTag("highway", "secondary");
        osmWay.setTag("tunnel", "yes");
        assertPriorityAndSpeed(1.0, 24, osmWay);

        osmWay.setTag("bicycle", "designated");
        assertPriorityAndSpeed(1.2, 24, osmWay);
    }

    @Test
    @Override
    public void testService() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "service");
        assertPriorityAndSpeed(0.9, 12, way);

        way.setTag("service", "parking_aisle");
        assertPriorityAndSpeed(0.9, 4, way);
    }

    @Test
    public void testTrack() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "track");
        way.setTag("bicycle", "yes");
        assertPriorityAndSpeed(0.6, 2, way);
        way.setTag("surface", "asphalt");
        assertPriorityAndSpeed(1.3, 24, way);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("bicycle", "designated");
        way.setTag("segregated","no");
        assertPriorityAndSpeed(0.6, 2, way);
        way.setTag("surface", "asphalt");
        assertPriorityAndSpeed(1.3, 24, way);
        way.setTag("tracktype","grade1");
        assertPriorityAndSpeed(1.3, 24, way);
    }

    @Test
    public void testGetSpeed() {
        EdgeIntAccess edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(encodingManager.getBytesForFlags());
        int edgeId = 0;
        avgSpeedEnc.setDecimal(false, edgeId, edgeIntAccess, 10);
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "track");
        way.setTag("tracktype", "grade3");
        // use pushing section
        assertEquals(4, getSpeedFromFlags(way), 1e-1);

        // Even if it is part of a cycle way
        way.setTag("bicycle", "yes");
        assertEquals(4, getSpeedFromFlags(way), 1e-1);

        way.clearTags();
        way.setTag("highway", "steps");
        assertEquals(MIN_SPEED, getSpeedFromFlags(way), 1e-1);

        way.clearTags();
        way.setTag("highway", "primary");
        assertEquals(24, getSpeedFromFlags(way), 1e-1);

        way.clearTags();
        way.setTag("highway", "primary");
        way.setTag("surface", "paved");
        assertEquals(24, getSpeedFromFlags(way), 1e-1);

        way.clearTags();
        way.setTag("highway", "primary");
        way.setTag("surface", "unknownpavement");
        assertEquals(PUSHING_SECTION_SPEED, getSpeedFromFlags(way), 1e-1);
    }

    @Test
    public void testSmoothness() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "residential");
        assertEquals(18, getSpeedFromFlags(way), 0.01);

        way.setTag("smoothness", "excellent");
        assertEquals(22, getSpeedFromFlags(way), 0.01);

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
        assertEquals(MIN_SPEED, getSpeedFromFlags(way), 0.01);

        way.setTag("smoothness", "impassable");
        assertEquals(MIN_SPEED, getSpeedFromFlags(way), 0.01);
    }

    @Test
    public void testHandleWayTagsInfluencedByRelation() {
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "track");
        assertEquals(2, getSpeedFromFlags(osmWay), 1e-1);

        ReaderRelation osmRel = new ReaderRelation(1);
        osmRel.setTag("route", "bicycle");
        osmRel.setTag("network", "lcn");
        assertPriorityAndSpeed(0.6, 2, osmWay, osmRel);
        // relation code is OUTSTANDING NICE but as unpaved, the speed is still PUSHING_SECTION_SPEED/2
        osmRel.setTag("network", "icn");
        assertPriorityAndSpeed(0.6, 2, osmWay, osmRel);

        // Now we assume bicycle=yes, anyhow still unpaved
        osmWay.setTag("bicycle", "yes");
        assertPriorityAndSpeed(0.6, 2, osmWay, osmRel);

        // Now we assume bicycle=yes, and paved
        osmWay.setTag("tracktype", "grade1");
        assertPriorityAndSpeed(1.3, 24, osmWay, osmRel);

        // Now we assume bicycle=yes, and unpaved and as part of a cycle relation
        osmWay.setTag("tracktype", "grade2");
        osmWay.setTag("bicycle", "yes");
        assertPriorityAndSpeed(0.6, 10, osmWay, osmRel);

        // Now we check good surface without tracktype
        osmWay.clearTags();
        osmWay.setTag("highway", "track");
        osmWay.setTag("surface", "asphalt");
        assertPriorityAndSpeed(1.3, 24, osmWay, osmRel);

        // Now we assume bicycle=yes, and unpaved and not part of a cycle relation
        osmWay.clearTags();
        osmWay.setTag("highway", "track");
        osmWay.setTag("tracktype", "grade3");
        assertPriorityAndSpeed(0.6, 4, osmWay);
    }

    @Test
    public void testPriority_avoidanceOfHighMaxSpeed() {
        // here we test the priority that would be calculated if the way was accessible (even when it is not)
        // therefore we need a modified parser that always yields access=WAY
        BooleanEncodedValue accessEnc = VehicleAccess.create("racingbike");
        DecimalEncodedValue speedEnc = VehicleSpeed.create("racingbike", 4, 2, false);
        DecimalEncodedValue priorityEnc = VehiclePriority.create("racingbike", 4, 0.1, false);
        EncodingManager encodingManager = EncodingManager.start()
                .add(accessEnc).add(speedEnc).add(priorityEnc)
                .add(RouteNetwork.create(BikeNetwork.KEY))
                .add(Smoothness.create())
                .add(FerrySpeed.create())
                .build();
        List<TagParser> parsers = Arrays.asList(
                new RacingBikeAverageSpeedParser(encodingManager),
                new RacingBikePriorityParser(encodingManager)
        );
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "tertiary");
        osmWay.setTag("maxspeed", "50");
        assertPriorityAndSpeed(encodingManager, priorityEnc, speedEnc, parsers, 1.2, 24, osmWay);

        osmWay.setTag("maxspeed", "60");
        assertPriorityAndSpeed(encodingManager, priorityEnc, speedEnc, parsers, 1.2, 24, osmWay);

        osmWay.setTag("maxspeed", "80");
        assertPriorityAndSpeed(encodingManager, priorityEnc, speedEnc, parsers, 1.2, 24, osmWay);

        osmWay.setTag("maxspeed", "90");
        assertPriorityAndSpeed(encodingManager, priorityEnc, speedEnc, parsers, 1.0, 24, osmWay);

        osmWay.setTag("maxspeed", "120");
        assertPriorityAndSpeed(encodingManager, priorityEnc, speedEnc, parsers, 1.0, 24, osmWay);

        osmWay.setTag("highway", "motorway");
        assertPriorityAndSpeed(encodingManager, priorityEnc, speedEnc, parsers, 0.5, 18, osmWay);

        osmWay.setTag("tunnel", "yes");
        assertPriorityAndSpeed(encodingManager, priorityEnc, speedEnc, parsers, 0.2, 18, osmWay);

        osmWay.clearTags();
        osmWay.setTag("highway", "motorway");
        osmWay.setTag("tunnel", "yes");
        osmWay.setTag("maxspeed", "80");
        assertPriorityAndSpeed(encodingManager, priorityEnc, speedEnc, parsers, 0.2, 18, osmWay);

        osmWay.clearTags();
        osmWay.setTag("highway", "motorway");
        osmWay.setTag("tunnel", "yes");
        osmWay.setTag("maxspeed", "120");
        assertPriorityAndSpeed(encodingManager, priorityEnc, speedEnc, parsers, 0.2, 18, osmWay);

        osmWay.clearTags();
        osmWay.setTag("highway", "notdefined");
        osmWay.setTag("tunnel", "yes");
        osmWay.setTag("maxspeed", "120");
        assertPriorityAndSpeed(encodingManager, priorityEnc, speedEnc, parsers, 0.5, PUSHING_SECTION_SPEED, osmWay);

        osmWay.clearTags();
        osmWay.setTag("highway", "notdefined");
        osmWay.setTag("maxspeed", "50");
        assertPriorityAndSpeed(encodingManager, priorityEnc, speedEnc, parsers, 1.0, PUSHING_SECTION_SPEED, osmWay);
    }

    private void assertPriorityAndSpeed(EncodingManager encodingManager, DecimalEncodedValue priorityEnc, DecimalEncodedValue speedEnc,
                                        List<TagParser> parsers, double expectedPrio, double expectedSpeed, ReaderWay way) {
        EdgeIntAccess edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(encodingManager.getBytesForFlags());
        int edgeId = 0;
        for (TagParser p : parsers) p.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertEquals(expectedPrio, priorityEnc.getDecimal(false, edgeId, edgeIntAccess), 0.01);
        assertEquals(expectedSpeed, speedEnc.getDecimal(false, edgeId, edgeIntAccess), 0.1);
    }

    @Test
    public void testClassBicycle() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "tertiary");
        way.setTag("class:bicycle:roadcycling", "3");
        assertPriority(1.5, way);

        way.setTag("class:bicycle", "-2");
        assertPriority(1.5, way);
    }

    @Test
    public void testPreferenceForSlowSpeed() {
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "tertiary");
        assertPriority(1.2, osmWay);
    }
}
