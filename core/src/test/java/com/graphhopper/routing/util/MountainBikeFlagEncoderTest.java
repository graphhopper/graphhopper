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
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.Test;

import static com.graphhopper.routing.util.BikeCommonFlagEncoder.PUSHING_SECTION_SPEED;
import static com.graphhopper.routing.util.PriorityCode.*;
import static org.junit.jupiter.api.Assertions.*;

public class MountainBikeFlagEncoderTest extends AbstractBikeFlagEncoderTester {
    @Override
    protected BikeCommonFlagEncoder createBikeEncoder() {
        return new MountainBikeFlagEncoder(new PMap("block_fords=true"));
    }

    @Test
    public void testGetSpeed() {
        IntsRef intsRef = GHUtility.setSpeed(10, 0, encoder, encodingManager.createEdgeFlags());
        assertEquals(10, avgSpeedEnc.getDecimal(false, intsRef), 1e-1);
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        assertEquals(18, encoder.getSpeed(way));
        assertPriority(REACH_DEST.getValue(), way);

        way.setTag("highway", "residential");
        assertEquals(16, encoder.getSpeed(way));
        assertPriority(PREFER.getValue(), way);

        // Test pushing section speeds
        way.setTag("highway", "footway");
        assertEquals(4, encoder.getSpeed(way));
        assertPriority(AVOID_IF_POSSIBLE.getValue(), way);

        way.setTag("highway", "track");
        assertEquals(18, encoder.getSpeed(way));
        assertPriority(PREFER.getValue(), way);

        way.setTag("highway", "steps");
        assertEquals(4, encoder.getSpeed(way));
        assertPriority(AVOID_IF_POSSIBLE.getValue(), way);
        way.clearTags();

        // test speed for allowed pushing section types
        way.setTag("highway", "track");
        way.setTag("bicycle", "yes");
        assertEquals(18, encoder.getSpeed(way));
        assertPriority(PREFER.getValue(), way);

        way.setTag("highway", "track");
        way.setTag("bicycle", "yes");
        way.setTag("tracktype", "grade3");
        assertPriority(VERY_NICE.getValue(), way);

        way.setTag("surface", "paved");
        assertEquals(18, encoder.getSpeed(way));
        assertPriority(VERY_NICE.getValue(), way);

        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("surface", "ground");
        assertEquals(16, encoder.getSpeed(way));
        assertPriority(PREFER.getValue(), way);
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
        assertEquals(PUSHING_SECTION_SPEED, getSpeedFromFlags(way), 0.01);

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
        assertEquals(PUSHING_SECTION_SPEED, getSpeedFromFlags(way), 0.01);

        way.setTag("smoothness", "impassable");
        assertEquals(PUSHING_SECTION_SPEED, getSpeedFromFlags(way), 0.01);
    }

    @Test
    @Override
    public void testSacScale() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "service");
        way.setTag("sac_scale", "hiking");
        assertTrue(encoder.getAccess(way).isWay());

        way.setTag("highway", "service");
        way.setTag("sac_scale", "mountain_hiking");
        assertTrue(encoder.getAccess(way).isWay());

        way.setTag("sac_scale", "alpine_hiking");
        assertTrue(encoder.getAccess(way).isWay());

        way.setTag("sac_scale", "demanding_alpine_hiking");
        assertTrue(encoder.getAccess(way).canSkip());
    }

    @Test
    public void testHandleWayTagsInfluencedByRelation() {
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "track");

        ReaderRelation osmRel = new ReaderRelation(1);
        IntsRef relFlags = encodingManager.handleRelationTags(osmRel, encodingManager.createRelationFlags());
        // unchanged
        IntsRef flags = encodingManager.handleWayTags(osmWay, accessMap, relFlags);
        assertEquals(18, avgSpeedEnc.getDecimal(false, flags), 1e-1);
        assertPriority(PriorityCode.PREFER.getValue(), osmWay);

        // relation code is PREFER
        osmRel.setTag("route", "bicycle");
        osmRel.setTag("network", "lcn");
        relFlags = encodingManager.handleRelationTags(osmRel, encodingManager.createRelationFlags());
        flags = encodingManager.handleWayTags(osmWay, accessMap, relFlags);
        assertEquals(18, avgSpeedEnc.getDecimal(false, flags), 1e-1);
        assertPriority(PriorityCode.PREFER.getValue(), osmWay);

        // relation code is PREFER
        osmRel.setTag("network", "rcn");

        relFlags = encodingManager.handleRelationTags(osmRel, encodingManager.createRelationFlags());
        flags = encodingManager.handleWayTags(osmWay, accessMap, relFlags);
        assertPriority(PriorityCode.PREFER.getValue(), osmWay);
        assertEquals(18, avgSpeedEnc.getDecimal(false, flags), 1e-1);

        // relation code is PREFER
        osmRel.setTag("network", "ncn");
        relFlags = encodingManager.handleRelationTags(osmRel, encodingManager.createRelationFlags());
        flags = encodingManager.handleWayTags(osmWay, accessMap, relFlags);
        assertPriority(PriorityCode.PREFER.getValue(), osmWay);
        assertEquals(18, avgSpeedEnc.getDecimal(false, flags), 1e-1);

        // PREFER relation, but tertiary road
        // => no pushing section but road wayTypeCode and faster
        osmWay.clearTags();
        osmWay.setTag("highway", "tertiary");

        osmRel.setTag("route", "bicycle");
        osmRel.setTag("network", "lcn");
        relFlags = encodingManager.handleRelationTags(osmRel, encodingManager.createRelationFlags());
        flags = encodingManager.handleWayTags(osmWay, accessMap, relFlags);
        assertEquals(18, avgSpeedEnc.getDecimal(false, flags), 1e-1);
        assertPriority(PriorityCode.PREFER.getValue(), osmWay);
    }

    // Issue 407 : Always block kissing_gate execpt for mountainbikes
    @Test
    @Override
    public void testBarrierAccess() {
        // kissing_gate without bicycle tag
        ReaderNode node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "kissing_gate");
        // No barrier!
        assertTrue(encoder.handleNodeTags(node) == 0);

        // kissing_gate with bicycle tag = no
        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "kissing_gate");
        node.setTag("bicycle", "no");
        // barrier!
        assertFalse(encoder.handleNodeTags(node) == 0);

        // kissing_gate with bicycle tag
        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "kissing_gate");
        node.setTag("bicycle", "yes");
        // No barrier!
        assertTrue(encoder.handleNodeTags(node) == 0);
    }

}
