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
import static com.graphhopper.routing.util.PriorityCode.*;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 * @author ratrun
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
        OSMWay way = new OSMWay(1);
        way.setTag("highway", "primary");
        assertEquals(18, encoder.getSpeed(way));
        assertPriority(REACH_DEST.getValue(), way);

        way.setTag("highway", "footway");
        assertEquals(4, encoder.getSpeed(way));
        assertPriority(AVOID_IF_POSSIBLE.getValue(), way);

        way.setTag("highway", "track");
        assertEquals(12, encoder.getSpeed(way));
        assertPriority(UNCHANGED.getValue(), way);

        way.setTag("tracktype", "grade1");
        assertEquals(18, encoder.getSpeed(way));
        assertPriority(UNCHANGED.getValue(), way);

        way.setTag("highway", "track");
        way.setTag("tracktype", "grade2");
        assertEquals(12, encoder.getSpeed(way));
        assertPriority(UNCHANGED.getValue(), way);

        // test speed for allowed get off the bike types
        way.setTag("highway", "track");
        way.setTag("bicycle", "yes");
        assertEquals(12, encoder.getSpeed(way));

        way.clearTags();
        way.setTag("highway", "steps");
        assertEquals(2, encoder.getSpeed(way));
        assertPriority(AVOID_IF_POSSIBLE.getValue(), way);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("surface", "paved");
        assertEquals(18, encoder.getSpeed(way));

        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("surface", "ground");
        assertEquals(4, encoder.getSpeed(way));
        assertPriority(AVOID_IF_POSSIBLE.getValue(), way);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("bicycle", "yes");
        way.setTag("surface", "fine_gravel");
        assertEquals(18, encoder.getSpeed(way));

        way.setTag("surface", "unknown_surface");
        assertEquals(4, encoder.getSpeed(way));

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("surface", "asphalt");
        assertEquals(18, encoder.getSpeed(way));

        way.clearTags();
        way.setTag("highway", "motorway");
        way.setTag("bicycle", "yes");
        assertEquals(18, encoder.getSpeed(way));
    }

    @Test
    public void testHandleWayTags()
    {
        OSMWay way = new OSMWay(1);
        String wayType;
        way.setTag("highway", "track");
        wayType = getWayTypeFromFlags(way);
        assertEquals("way, unpaved", wayType);

        way.clearTags();
        way.setTag("highway", "path");
        wayType = getWayTypeFromFlags(way);
        assertEquals("get off the bike, unpaved", wayType);

        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("surface", "grass");
        wayType = getWayTypeFromFlags(way);
        assertEquals("get off the bike, unpaved", wayType);

        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("surface", "concrete");
        wayType = getWayTypeFromFlags(way);
        assertEquals("get off the bike", wayType);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("foot", "yes");
        way.setTag("surface", "paved");
        way.setTag("tracktype", "grade1");
        wayType = getWayTypeFromFlags(way);
        assertEquals("", wayType);

        way.setTag("tracktype", "grade2");
        wayType = getWayTypeFromFlags(way);
        assertEquals("get off the bike, unpaved", wayType);

        way.clearTags();
        way.setTag("junction", "roundabout");
        way.setTag("highway", "tertiary");
        long flags = encoder.handleWayTags(way, encoder.acceptWay(way), 0);
        assertTrue(encoder.isBool(flags, FlagEncoder.K_ROUNDABOUT));
    }

    @Test
    public void testOneway()
    {
        OSMWay way = new OSMWay(1);
        way.setTag("highway", "tertiary");
        long flags = encoder.handleWayTags(way, encoder.acceptWay(way), 0);
        assertTrue(encoder.isForward(flags));
        assertTrue(encoder.isBackward(flags));
        way.setTag("oneway", "yes");
        flags = encoder.handleWayTags(way, encoder.acceptWay(way), 0);
        assertTrue(encoder.isForward(flags));
        assertFalse(encoder.isBackward(flags));
        way.clearTags();

        way.setTag("highway", "tertiary");
        flags = encoder.handleWayTags(way, encoder.acceptWay(way), 0);
        assertTrue(encoder.isForward(flags));
        assertTrue(encoder.isBackward(flags));
        way.clearTags();

        way.setTag("highway", "tertiary");
        way.setTag("vehicle:forward", "no");
        flags = encoder.handleWayTags(way, encoder.acceptWay(way), 0);
        assertFalse(encoder.isForward(flags));
        assertTrue(encoder.isBackward(flags));
        way.clearTags();

        way.setTag("highway", "tertiary");
        way.setTag("vehicle:backward", "no");
        flags = encoder.handleWayTags(way, encoder.acceptWay(way), 0);
        assertTrue(encoder.isForward(flags));
        assertFalse(encoder.isBackward(flags));
        way.clearTags();

        way.setTag("highway", "tertiary");
        way.setTag("motor_vehicle:backward", "no");
        flags = encoder.handleWayTags(way, encoder.acceptWay(way), 0);
        assertTrue(encoder.isForward(flags));
        assertTrue(encoder.isBackward(flags));
        way.clearTags();

        // attention bicycle:backward=no/yes has a completely different meaning!
        // https://wiki.openstreetmap.org/wiki/Key:access#One-way_restrictions
        way.setTag("highway", "tertiary");
        way.setTag("oneway", "yes");
        way.setTag("bicycle:backward", "no");
        flags = encoder.handleWayTags(way, encoder.acceptWay(way), 0);
        assertTrue(encoder.isForward(flags));
        assertTrue(encoder.isBackward(flags));        

        way.setTag("bicycle:backward", "yes");
        flags = encoder.handleWayTags(way, encoder.acceptWay(way), 0);
        assertTrue(encoder.isForward(flags));
        assertTrue(encoder.isBackward(flags));
        way.clearTags();
    }
    
    @Test
    public void testHandleWayTagsInfluencedByRelation()
    {
        OSMWay osmWay = new OSMWay(1);
        osmWay.setTag("highway", "road");
        long allowed = encoder.acceptBit;

        OSMRelation osmRel = new OSMRelation(1);
        long relFlags = encoder.handleRelationTags(osmRel, 0);
        // unchanged
        long flags = encoder.handleWayTags(osmWay, allowed, relFlags);
        assertEquals(12, encoder.getSpeed(flags), 1e-1);
        assertPriority(UNCHANGED.getValue(), osmWay, relFlags);

        // relation code is PREFER
        osmRel.setTag("route", "bicycle");
        osmRel.setTag("network", "lcn");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        flags = encoder.handleWayTags(osmWay, allowed, relFlags);
        assertEquals(12, encoder.getSpeed(flags), 1e-1);
        assertPriority(PREFER.getValue(), osmWay, relFlags);

        // relation code is VERY_NICE
        osmRel.setTag("network", "rcn");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        assertPriority(VERY_NICE.getValue(), osmWay, relFlags);

        // relation code is BEST
        osmRel.setTag("network", "ncn");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        assertPriority(BEST.getValue(), osmWay, relFlags);

        // PREFER relation, but tertiary road => no get off the bike but road wayTypeCode and faster
        osmWay.clearTags();
        osmWay.setTag("highway", "tertiary");
        osmRel.setTag("route", "bicycle");
        osmRel.setTag("network", "lcn");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        assertPriority(PREFER.getValue(), osmWay, relFlags);

        // A footway is not of waytype get off the bike in case that it is part of a cycle route
        osmRel.clearTags();
        osmWay.clearTags();
        osmWay.setTag("highway", "footway");
        osmWay.setTag("surface", "grass");

        // First tests without a cycle route relation, this is a get off the bike
        relFlags = encoder.handleRelationTags(osmRel, 0);
        String wayType = getWayTypeFromFlags(osmWay, relFlags);
        assertEquals("get off the bike, unpaved", wayType);

        // now as part of a cycle route relation
        osmRel.setTag("type", "route");
        osmRel.setTag("route", "bicycle");
        osmRel.setTag("network", "lcn");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        wayType = getWayTypeFromFlags(osmWay, relFlags);
        assertEquals("way, unpaved", wayType);

        // steps are still shown as get off the bike
        osmWay.clearTags();
        osmWay.setTag("highway", "steps");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        wayType = getWayTypeFromFlags(osmWay, relFlags);
        assertEquals("get off the bike", wayType);
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
    @Override
    public void testSacScale()
    {
        OSMWay way = new OSMWay(1);
        way.setTag("highway", "path");
        way.setTag("sac_scale", "hiking");
        // allow
        assertEquals(1, encoder.acceptWay(way));

        way.setTag("highway", "path");
        way.setTag("sac_scale", "mountain_hiking");
        // disallow
        assertEquals(0, encoder.acceptWay(way));

        way.setTag("highway", "cycleway");
        way.setTag("sac_scale", "hiking");
        // allow
        assertTrue(encoder.acceptWay(way) > 0);

        way.setTag("highway", "cycleway");
        way.setTag("sac_scale", "mountain_hiking");
        // disallow
        assertEquals(0, encoder.acceptWay(way));
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
        assertEquals((double) BEST.getValue() / BEST.getValue(), encoder.getDouble(flags, PriorityWeighting.KEY), 1e-3);

        // important: UNCHANGED should not get 0 priority!
        osmWay = new OSMWay(1);
        osmWay.setTag("highway", "somethingelse");
        flags = encoder.handleWayTags(osmWay, allowed, 0);
        assertEquals((double) UNCHANGED.getValue() / BEST.getValue(), encoder.getDouble(flags, PriorityWeighting.KEY), 1e-3);
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
    public void testTurnFlagEncoding_DefaultNoRestrictionsAndNoCosts()
    {
        // default is disabled turn costs and no restrictions
        long flags_r0 = encoder.getTurnFlags(true, 0);
        long flags_0 = encoder.getTurnFlags(false, 0);

        long flags_r20 = encoder.getTurnFlags(true, 20);
        long flags_20 = encoder.getTurnFlags(false, 20);

        assertEquals(0, encoder.getTurnCost(flags_r0), .1);
        assertEquals(0, encoder.getTurnCost(flags_0), .1);

        assertEquals(0, encoder.getTurnCost(flags_r20), .1);
        assertEquals(0, encoder.getTurnCost(flags_20), .1);

        assertFalse(encoder.isTurnRestricted(flags_r0));
        assertFalse(encoder.isTurnRestricted(flags_0));

        assertFalse(encoder.isTurnRestricted(flags_r20));
        assertFalse(encoder.isTurnRestricted(flags_20));
    }

    @Test
    public void testTurnFlagEncoding_withCosts()
    {
        encoder = new BikeFlagEncoder(4, 2, 127);
        new EncodingManager(encoder);

        long flags_r0 = encoder.getTurnFlags(true, 0);
        long flags_0 = encoder.getTurnFlags(false, 0);
        assertTrue(Double.isInfinite(encoder.getTurnCost(flags_r0)));
        assertEquals(0, encoder.getTurnCost(flags_0), .1);
        assertTrue(encoder.isTurnRestricted(flags_r0));
        assertFalse(encoder.isTurnRestricted(flags_0));

        long flags_r20 = encoder.getTurnFlags(true, 0);
        long flags_20 = encoder.getTurnFlags(false, 20);
        assertTrue(Double.isInfinite(encoder.getTurnCost(flags_r20)));
        assertEquals(20, encoder.getTurnCost(flags_20), .1);
        assertTrue(encoder.isTurnRestricted(flags_r20));
        assertFalse(encoder.isTurnRestricted(flags_20));

        long flags_r220 = encoder.getTurnFlags(true, 0);
        try
        {
            encoder.getTurnFlags(false, 220);
            assertTrue(false);
        } catch (Exception ex)
        {
        }
        long flags_126 = encoder.getTurnFlags(false, 126);
        assertTrue(Double.isInfinite(encoder.getTurnCost(flags_r220)));

        assertEquals(126, encoder.getTurnCost(flags_126), .1);
        assertTrue(encoder.isTurnRestricted(flags_r220));
        assertFalse(encoder.isTurnRestricted(flags_126));
    }
}
