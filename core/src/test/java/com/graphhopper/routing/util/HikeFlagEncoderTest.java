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

import com.graphhopper.reader.ReaderWay;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Peter Karich
 */
public class HikeFlagEncoderTest {
    private final EncodingManager encodingManager = EncodingManager.create("car,hike");
    private final HikeFlagEncoder hikeEncoder = (HikeFlagEncoder) encodingManager.getEncoder("hike");

    @Test
    public void testAccess() {
        ReaderWay way = new ReaderWay(0);
        way.setTag("highway", "tertiary");
        way.setTag("access", "no");
        way.setTag("sidewalk", "both");
        way.setTag("foot", "no");
        assertTrue(hikeEncoder.getAccess(way).canSkip());
    }

    @Test
    public void testPriority() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "cycleway");
        assertEquals(PriorityCode.UNCHANGED.getValue(), hikeEncoder.handlePriority(way, 0));

        way.setTag("highway", "primary");
        assertEquals(PriorityCode.REACH_DEST.getValue(), hikeEncoder.handlePriority(way, 0));

        way.setTag("highway", "track");
        way.setTag("bicycle", "official");
        assertEquals(PriorityCode.AVOID_IF_POSSIBLE.getValue(), hikeEncoder.handlePriority(way, 0));

        way.setTag("highway", "track");
        way.setTag("bicycle", "designated");
        assertEquals(PriorityCode.AVOID_IF_POSSIBLE.getValue(), hikeEncoder.handlePriority(way, 0));

        way.setTag("highway", "cycleway");
        way.setTag("bicycle", "designated");
        way.setTag("foot", "designated");
        assertEquals(PriorityCode.PREFER.getValue(), hikeEncoder.handlePriority(way, 0));

        way.clearTags();
        way.setTag("highway", "primary");
        way.setTag("sidewalk", "yes");
        assertEquals(PriorityCode.REACH_DEST.getValue(), hikeEncoder.handlePriority(way, 0));

        way.clearTags();
        way.setTag("highway", "cycleway");
        way.setTag("sidewalk", "no");
        assertEquals(PriorityCode.UNCHANGED.getValue(), hikeEncoder.handlePriority(way, 0));

        way.clearTags();
        way.setTag("highway", "road");
        way.setTag("bicycle", "official");
        way.setTag("sidewalk", "no");
        assertEquals(PriorityCode.AVOID_IF_POSSIBLE.getValue(), hikeEncoder.handlePriority(way, 0));

        way.clearTags();
        way.setTag("highway", "trunk");
        way.setTag("sidewalk", "no");
        assertEquals(PriorityCode.WORST.getValue(), hikeEncoder.handlePriority(way, 0));
        way.setTag("sidewalk", "none");
        assertEquals(PriorityCode.WORST.getValue(), hikeEncoder.handlePriority(way, 0));

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("sidewalk", "yes");
        assertEquals(PriorityCode.PREFER.getValue(), hikeEncoder.handlePriority(way, 0));
    }

}
