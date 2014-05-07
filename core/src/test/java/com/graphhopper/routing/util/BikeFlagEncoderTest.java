/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License, 
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

import com.graphhopper.reader.OSMRelation;
import com.graphhopper.reader.OSMWay;
import static com.graphhopper.routing.util.BikeCommonFlagEncoder.PriorityCode.*;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class BikeFlagEncoderTest extends AbstractBikeFlagEncoderTester
{
    @Override
    protected BikeCommonFlagEncoder createBikeEncoder()
    {
        return (BikeCommonFlagEncoder) new EncodingManager("BIKE,MTB").getEncoder("BIKE");
    }

    @Test
    public void testGetSpeed()
    {
        long result = encoder.setProperties(10, true, true);
        assertEquals(10, encoder.getSpeed(result), 1e-1);
        OSMWay osmWay = new OSMWay(1);
        osmWay.setTag("highway", "primary");
        assertEquals(18, encoder.getSpeed(osmWay));
        assertPriority(REACH_DEST.getValue(), osmWay);

        osmWay.setTag("highway", "residential");
        assertEquals(18, encoder.getSpeed(osmWay));
        assertPriority(PREFER.getValue(), osmWay);

        osmWay.setTag("highway", "footway");
        assertEquals(4, encoder.getSpeed(osmWay));
        assertPriority(AVOID_IF_POSSIBLE.getValue(), osmWay);

        osmWay.setTag("highway", "track");
        assertEquals(18, encoder.getSpeed(osmWay));
        assertPriority(UNCHANGED.getValue(), osmWay);

        osmWay.setTag("highway", "steps");
        assertEquals(2, encoder.getSpeed(osmWay));
        assertPriority(AVOID_IF_POSSIBLE.getValue(), osmWay);

        // test speed for allowed pushing section types
        osmWay.setTag("highway", "track");
        osmWay.setTag("bicycle", "yes");
        assertEquals(18, encoder.getSpeed(osmWay));

        osmWay.setTag("highway", "track");
        osmWay.setTag("bicycle", "yes");
        osmWay.setTag("tracktype", "grade3");
        assertEquals(10, encoder.getSpeed(osmWay));

        osmWay.setTag("surface", "paved");
        assertEquals(18, encoder.getSpeed(osmWay));

        osmWay.clearTags();
        osmWay.setTag("highway", "path");
        osmWay.setTag("surface", "ground");
        assertEquals(4, encoder.getSpeed(osmWay));
        assertPriority(AVOID_IF_POSSIBLE.getValue(), osmWay);

        osmWay.clearTags();
        osmWay.setTag("highway", "track");
        osmWay.setTag("bicycle", "yes");
        osmWay.setTag("surface", "fine_gravel");
        assertEquals(18, encoder.getSpeed(osmWay));

        osmWay.clearTags();
        osmWay.setTag("highway", "track");
        osmWay.setTag("bicycle", "yes");
        osmWay.setTag("surface", "unknown_surface");
        assertEquals(4, encoder.getSpeed(osmWay));
    }

    @Test
    public void testHandleWayTags()
    {
        OSMWay way = new OSMWay(1);
        String wayType;
        way.setTag("highway", "track");
        wayType = getWayTypeFromFlags(way);
        assertEquals("", wayType);

        way.clearTags();
        way.setTag("highway", "path");
        wayType = getWayTypeFromFlags(way);
        assertEquals("pushing section, unpaved", wayType);

        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("surface", "grass");
        wayType = getWayTypeFromFlags(way);
        assertEquals("pushing section, unpaved", wayType);

        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("surface", "concrete");
        wayType = getWayTypeFromFlags(way);
        assertEquals("pushing section", wayType);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("foot", "yes");
        way.setTag("surface", "paved");
        way.setTag("tracktype", "grade1");
        wayType = getWayTypeFromFlags(way);
        assertEquals("", wayType);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("foot", "yes");
        way.setTag("surface", "paved");
        way.setTag("tracktype", "grade2");
        wayType = getWayTypeFromFlags(way);
        assertEquals("pushing section, unpaved", wayType);
    }

    @Test
    public void testHandleWayTagsInfluencedByRelation()
    {
        OSMWay osmWay = new OSMWay(1);
        osmWay.setTag("highway", "track");
        long allowed = encoder.acceptBit;

        OSMRelation osmRel = new OSMRelation(1);
        long relFlags = encoder.handleRelationTags(osmRel, 0);
        // unchanged
        long flags = encoder.handleWayTags(osmWay, allowed, relFlags);
        assertEquals(18, encoder.getSpeed(flags), 1e-1);
        assertEquals("", getWayTypeFromFlags(osmWay));
        assertPriority(UNCHANGED.getValue(), osmWay, relFlags);

        // relation code is PREFER
        osmRel.setTag("route", "bicycle");
        osmRel.setTag("network", "lcn");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        flags = encoder.handleWayTags(osmWay, allowed, relFlags);
        assertEquals(18, encoder.getSpeed(flags), 1e-1);
        assertPriority(PREFER.getValue(), osmWay, relFlags);
        assertEquals("", getWayTypeFromFlags(osmWay));

        // relation code is VERY_NICE
        osmRel.setTag("network", "rcn");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        flags = encoder.handleWayTags(osmWay, allowed, relFlags);
        assertEquals(18, encoder.getSpeed(flags), 1e-1);
        assertPriority(VERY_NICE.getValue(), osmWay, relFlags);

        // relation code is BEST
        osmRel.setTag("network", "ncn");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        flags = encoder.handleWayTags(osmWay, allowed, relFlags);
        assertEquals(18, encoder.getSpeed(flags), 1e-1);
        assertPriority(BEST.getValue(), osmWay, relFlags);

        // PREFER relation, but tertiary road => no pushing section but road wayTypeCode and faster
        osmWay.clearTags();
        osmWay.setTag("highway", "tertiary");
        osmRel.setTag("route", "bicycle");
        osmRel.setTag("network", "lcn");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        flags = encoder.handleWayTags(osmWay, allowed, relFlags);
        assertEquals(18, encoder.getSpeed(flags), 1e-1);
        assertPriority(PREFER.getValue(), osmWay, relFlags);
        assertEquals("", getWayTypeFromFlags(osmWay));
    }

    @Test
    public void testUnchangedRelationShouldNotInfluencePriority()
    {
        OSMWay osmWay = new OSMWay(1);
        osmWay.setTag("highway", "secondary");

        OSMRelation osmRel = new OSMRelation(1);
        osmRel.setTag("description", "something");
        long relFlags = encoder.handleRelationTags(osmRel, 0);
        assertPriority(REACH_DEST.getValue(), osmWay, relFlags);
    }

    @Test
    public void testCalcPriority()
    {
        long allowed = encoder.acceptBit;
        OSMWay osmWay = new OSMWay(1);
        OSMRelation osmRel = new OSMRelation(1);
        osmRel.setTag("route", "bicycle");
        osmRel.setTag("network", "icn");
        long relFlags = encoder.handleRelationTags(osmRel, 0);
        long flags = encoder.handleWayTags(osmWay, allowed, relFlags);
        assertEquals((double) BEST.getValue() / BEST.getValue(), encoder.getPriority(flags), 1e-3);

        // important: UNCHANGED should not get 0 priority!
        osmWay = new OSMWay(1);
        osmWay.setTag("highway", "somethingelse");
        flags = encoder.handleWayTags(osmWay, allowed, 0);
        assertEquals((double) UNCHANGED.getValue() / BEST.getValue(), encoder.getPriority(flags), 1e-3);
    }

    @Test
    public void testTurnFlagEncoding_noCosts()
    {
        encoder.defineTurnBits(0, 0, 0);

        long flags_r0 = encoder.getTurnFlags(true, 0);
        long flags_0 = encoder.getTurnFlags(false, 0);

        long flags_r20 = encoder.getTurnFlags(true, 20);
        long flags_20 = encoder.getTurnFlags(false, 20);

        assertEquals(0, encoder.getTurnCosts(flags_r0));
        assertEquals(0, encoder.getTurnCosts(flags_0));

        assertEquals(0, encoder.getTurnCosts(flags_r20));
        assertEquals(0, encoder.getTurnCosts(flags_20));

        assertTrue(encoder.isTurnRestricted(flags_r0));
        assertFalse(encoder.isTurnRestricted(flags_0));

        assertTrue(encoder.isTurnRestricted(flags_r20));
        assertFalse(encoder.isTurnRestricted(flags_20));
    }

    @Test
    public void testMaxSpeed()
    {
        OSMWay way = new OSMWay(1);
        way.setTag("highway", "secondary");
        way.setTag("maxspeed", "10");
        long allowed = encoder.acceptWay(way);
        long encoded = encoder.handleWayTags(way, allowed, 0);
        assertEquals(10, encoder.getSpeed(encoded), 1e-1);
    }

    @Test
    public void testTurnFlagEncoding_withCosts()
    {
        //arbitrary shift, 7 turn cost bits: [0,127]
        encoder.defineTurnBits(0, 2, 7);

        long flags_r0 = encoder.getTurnFlags(true, 0);
        long flags_0 = encoder.getTurnFlags(false, 0);

        long flags_r20 = encoder.getTurnFlags(true, 20);
        long flags_20 = encoder.getTurnFlags(false, 20);

        long flags_r220 = encoder.getTurnFlags(true, 220);
        long flags_220 = encoder.getTurnFlags(false, 220);

        assertEquals(0, encoder.getTurnCosts(flags_r0));
        assertEquals(0, encoder.getTurnCosts(flags_0));

        assertEquals(20, encoder.getTurnCosts(flags_r20));
        assertEquals(20, encoder.getTurnCosts(flags_20));

        assertEquals(127, encoder.getTurnCosts(flags_r220));
        assertEquals(127, encoder.getTurnCosts(flags_220));

        assertTrue(encoder.isTurnRestricted(flags_r0));
        assertFalse(encoder.isTurnRestricted(flags_0));

        assertTrue(encoder.isTurnRestricted(flags_r20));
        assertFalse(encoder.isTurnRestricted(flags_20));

        assertTrue(encoder.isTurnRestricted(flags_r220));
        assertFalse(encoder.isTurnRestricted(flags_220));
    }
}
