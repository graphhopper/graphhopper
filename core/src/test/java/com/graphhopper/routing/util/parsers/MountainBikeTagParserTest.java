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
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.Test;

import static com.graphhopper.routing.util.parsers.BikeCommonAverageSpeedParser.MIN_SPEED;
import static org.junit.jupiter.api.Assertions.*;

public class MountainBikeTagParserTest extends AbstractBikeTagParserTester {
    @Override
    protected EncodingManager createEncodingManager() {
        return new EncodingManager.Builder()
                .add(VehicleAccess.create("mtb"))
                .add(VehicleSpeed.create("mtb", 4, 2, false))
                .add(VehiclePriority.create("mtb", 4, 0.1, false))
                .add(Roundabout.create())
                .add(Smoothness.create())
                .add(FerrySpeed.create())
                .add(RouteNetwork.create(BikeNetwork.KEY))
                .add(RouteNetwork.create(MtbNetwork.KEY))
                .build();
    }

    @Override
    protected BikeCommonAccessParser createAccessParser(EncodedValueLookup lookup, PMap pMap) {
        return new MountainBikeAccessParser(lookup, pMap);
    }

    @Override
    protected BikeCommonAverageSpeedParser createAverageSpeedParser(EncodedValueLookup lookup) {
        return new MountainBikeAverageSpeedParser(lookup);
    }

    @Override
    protected BikeCommonPriorityParser createPriorityParser(EncodedValueLookup lookup) {
        return new MountainBikePriorityParser(lookup);
    }

    @Test
    public void testSpeedAndPriority() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        assertPriorityAndSpeed(0.5, 18, way);

        way.setTag("highway", "residential");
        assertPriorityAndSpeed(1.0, 18, way);

        // Test pushing section speeds
        way.setTag("highway", "footway");
        assertPriorityAndSpeed(0.8, 6, way);

        way.setTag("highway", "track");
        assertPriorityAndSpeed(1.2, 12, way);

        way.setTag("bicycle", "yes");
        assertPriorityAndSpeed(1.2, 12, way);

        way.setTag("highway", "track");
        way.setTag("bicycle", "yes");
        way.setTag("tracktype", "grade3");
        assertPriorityAndSpeed(1.2, 12, way);
        way.setTag("tracktype", "grade1");
        assertPriorityAndSpeed(1.3, 18, way);

        way.setTag("surface", "paved");
        assertPriorityAndSpeed(1.3, 18, way);

        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("surface", "ground");
        assertPriorityAndSpeed(1.2, 10, way);
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
        assertEquals(14, getSpeedFromFlags(way), 0.01);

        way.setTag("smoothness", "bad");
        assertEquals(10, getSpeedFromFlags(way), 0.01);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("tracktype", "grade4");
        assertEquals(8, getSpeedFromFlags(way), 0.01);

        way.setTag("smoothness", "bad");
        assertEquals(6, getSpeedFromFlags(way), 0.01);

        way.setTag("smoothness", "impassable");
        assertEquals(MIN_SPEED, getSpeedFromFlags(way), 0.01);
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
