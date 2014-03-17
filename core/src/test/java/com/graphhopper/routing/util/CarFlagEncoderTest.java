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

import com.graphhopper.reader.OSMNode;
import java.util.HashMap;
import java.util.Map;

import com.graphhopper.reader.OSMWay;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class CarFlagEncoderTest
{
    private final EncodingManager em = new EncodingManager("CAR,BIKE,FOOT");
    private final CarFlagEncoder encoder = (CarFlagEncoder) em.getEncoder("CAR");

    @Test
    public void testAccess()
    {
        Map<String, String> map = new HashMap<String, String>();
        OSMWay way = new OSMWay(1, map);
        assertFalse(encoder.acceptWay(way) > 0);
        map.put("highway", "service");
        assertTrue(encoder.acceptWay(way) > 0);
        map.put("access", "no");
        assertFalse(encoder.acceptWay(way) > 0);

        map.clear();
        map.put("highway", "track");
        map.put("motorcar", "no");
        assertFalse(encoder.acceptWay(way) > 0);

        map.clear();
        map.put("highway", "unclassified");
        map.put("ford", "yes");
        assertFalse(encoder.acceptWay(way) > 0);
        map.put("motorcar", "yes");
        assertTrue(encoder.acceptWay(way) > 0);

        map.clear();
        map.put("route", "ferry");
        assertTrue(encoder.acceptWay(way) > 0);
        map.put("motorcar", "no");
        assertFalse(encoder.acceptWay(way) > 0);

        map.clear();
        map.put("route", "ferry");
        map.put("foot", "yes");
        assertFalse(encoder.acceptWay(way) > 0);

        map.clear();
        map.put("highway", "primary");
        long flags = encoder.handleWayTags(way, encoder.acceptWay(way), 0);
        assertTrue(encoder.isForward(flags));
        assertTrue(encoder.isBackward(flags));
        map.put("oneway", "yes");
        flags = encoder.handleWayTags(way, encoder.acceptWay(way), 0);
        assertTrue(encoder.isForward(flags));
        assertFalse(encoder.isBackward(flags));
    }

    @Test
    public void testSetAccess()
    {
        assertTrue(encoder.isForward(encoder.setProperties(0, true, true)));
        assertTrue(encoder.isBackward(encoder.setProperties(0, true, true)));

        assertTrue(encoder.isForward(encoder.setProperties(0, true, false)));
        assertFalse(encoder.isBackward(encoder.setProperties(0, true, false)));

        assertFalse(encoder.isForward(encoder.setProperties(0, false, true)));
        assertTrue(encoder.isBackward(encoder.setProperties(0, false, true)));
    }

    @Test
    public void testSpeedLimitBiggerThanMaxValue()
    {
        Map<String, String> map = new HashMap<String, String>();
        OSMWay way = new OSMWay(1, map);
        map.put("highway", "trunk");
        map.put("maxspeed", "500");
        long allowed = encoder.acceptWay(way);
        long encoded = encoder.handleWayTags(way, allowed, 0);
        assertEquals(100, encoder.getSpeed(encoded), 1e-1);
    }

    @Test
    public void testSpeed()
    {
        // limit bigger than default road speed
        Map<String, String> map = new HashMap<String, String>();
        OSMWay way = new OSMWay(1, map);
        map.put("highway", "trunk");
        map.put("maxspeed", "110");
        long allowed = encoder.acceptWay(way);
        long encoded = encoder.handleWayTags(way, allowed, 0);
        assertEquals(100, encoder.getSpeed(encoded), 1e-1);

        map.clear();
        map.put("highway", "residential");
        map.put("surface", "cobblestone");
        allowed = encoder.acceptWay(way);
        encoded = encoder.handleWayTags(way, allowed, 0);
        assertEquals(30, encoder.getSpeed(encoded), 1e-1);

        map.clear();
        map.put("highway", "track");
        allowed = encoder.acceptWay(way);
        encoded = encoder.handleWayTags(way, allowed, 0);
        assertEquals(15, encoder.getSpeed(encoded), 1e-1);

        map.clear();
        map.put("highway", "track");
        map.put("tracktype", "grade1");
        allowed = encoder.acceptWay(way);
        encoded = encoder.handleWayTags(way, allowed, 0);
        assertEquals(20, encoder.getSpeed(encoded), 1e-1);

        map.clear();
        map.put("highway", "track");
        map.put("tracktype", "grade5");
        allowed = encoder.acceptWay(way);
        encoded = encoder.handleWayTags(way, allowed, 0);
        assertEquals(5, encoder.getSpeed(encoded), 1e-1);

        try
        {
            encoder.setSpeed(0, -1);
            assertTrue(false);
        } catch (IllegalArgumentException ex)
        {
        }
    }

    @Test
    public void testSetSpeed()
    {
        assertEquals(10, encoder.getSpeed(encoder.setSpeed(0, 10)), 1e-1);
    }

    @Test
    public void testRailway()
    {
        OSMWay way = new OSMWay(1);
        way.setTag("highway", "secondary");
        way.setTag("railway", "rail");
        // disallow rail
        assertEquals(0, encoder.acceptWay(way));

        way = new OSMWay(1);
        way.setTag("highway", "secondary");
        way.setTag("railway", "tram");
        // but allow tram to be on the same way
        assertNotSame(0, encoder.acceptWay(way));

        way = new OSMWay(1);
        way.setTag("route", "shuttle_train");
        way.setTag("motorcar", "yes");
        way.setTag("bicycle", "no");
        way.setTag("duration", "35");
        way.setInternalTag("estimated_distance", 50000);
        // accept
        assertNotSame(0, encoder.acceptWay(way));
        // calculate speed from estimated_distance and duration
        assertEquals(60, encoder.getSpeed(encoder.handleFerry(way, 20, 30, 40)), 1e-1);
    }

    @Test
    public void testBasics()
    {
        assertTrue(encoder.isForward(encoder.flagsDefault(true, true)));
        assertTrue(encoder.isBackward(encoder.flagsDefault(true, true)));
        assertTrue(encoder.isBoth(encoder.flagsDefault(true, true)));

        assertTrue(encoder.isForward(encoder.flagsDefault(true, false)));
        assertFalse(encoder.isBackward(encoder.flagsDefault(true, false)));
        assertFalse(encoder.isBoth(encoder.flagsDefault(true, false)));
    }

    @Test
    public void testOverwrite()
    {
        long forward = encoder.setProperties(10, true, false);
        long backward = encoder.reverseFlags(forward);
        long both = encoder.setProperties(20, true, true);
        assertTrue(encoder.canBeOverwritten(forward, forward));
        assertTrue(encoder.canBeOverwritten(backward, backward));
        assertTrue(encoder.canBeOverwritten(forward, both));
        assertTrue(encoder.canBeOverwritten(backward, both));

        assertTrue(encoder.canBeOverwritten(both, both));
        assertFalse(encoder.canBeOverwritten(both, forward));
        assertFalse(encoder.canBeOverwritten(both, backward));
        assertFalse(encoder.canBeOverwritten(forward, backward));
        assertFalse(encoder.canBeOverwritten(backward, forward));
    }

    @Test
    public void testSwapDir()
    {
        long swappedFlags = encoder.reverseFlags(encoder.flagsDefault(true, true));
        assertTrue(encoder.isForward(swappedFlags));
        assertTrue(encoder.isBackward(swappedFlags));

        swappedFlags = encoder.reverseFlags(encoder.flagsDefault(true, false));

        assertFalse(encoder.isForward(swappedFlags));
        assertTrue(encoder.isBackward(swappedFlags));

        assertEquals(0, encoder.reverseFlags(0));
    }

    @Test
    public void testBarrierAccess()
    {
        OSMNode node = new OSMNode(1, -1, -1);
        node.setTag("barrier", "lift_gate");
        node.setTag("access", "yes");
        // no barrier!
        assertTrue(encoder.analyzeNodeTags(node) == 0);

        node = new OSMNode(1, -1, -1);
        node.setTag("barrier", "lift_gate");
        node.setTag("bicycle", "yes");
        // barrier!
        assertTrue(encoder.analyzeNodeTags(node) > 0);

        node = new OSMNode(1, -1, -1);
        node.setTag("barrier", "lift_gate");
        node.setTag("access", "yes");
        node.setTag("bicycle", "yes");
        // should this be a barrier for motorcars too?
        // assertTrue(encoder.analyzeNodeTags(node) > 0);

        node = new OSMNode(1, -1, -1);
        node.setTag("barrier", "lift_gate");
        node.setTag("access", "no");
        node.setTag("motorcar", "yes");
        // no barrier!
        assertTrue(encoder.analyzeNodeTags(node) == 0);
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

        assertEquals(127, encoder.getTurnCosts(flags_r220)); // max costs is 2^7-1 = 127
        assertEquals(127, encoder.getTurnCosts(flags_220));

        assertTrue(encoder.isTurnRestricted(flags_r0));
        assertFalse(encoder.isTurnRestricted(flags_0));

        assertTrue(encoder.isTurnRestricted(flags_r20));
        assertFalse(encoder.isTurnRestricted(flags_20));

        assertTrue(encoder.isTurnRestricted(flags_r220));
        assertFalse(encoder.isTurnRestricted(flags_220));
    }

    @Test
    public void testMaxValue()
    {
        CarFlagEncoder instance = new CarFlagEncoder(8, 0.5);
        EncodingManager em = new EncodingManager(instance);
        OSMWay way = new OSMWay(1);
        way.setTag("highway", "motorway_link");
        way.setTag("maxspeed", "70 mph");
        long flags = instance.handleWayTags(way, 1, 0);

        // double speed = AbstractFlagEncoder.parseSpeed("70 mph");
        // => 112.654 * 0.9 => 101
        flags = instance.reverseFlags(flags);
        assertEquals(100, instance.getSpeed(flags), 1e-1);
    }

    @Test
    public void testRegisterOnlyOnceAllowed()
    {
        CarFlagEncoder instance = new CarFlagEncoder(8, 0.5);
        EncodingManager em = new EncodingManager(instance);
        try
        {
            em = new EncodingManager(instance);
            assertTrue(false);
        } catch (IllegalStateException ex)
        {

        }
    }
}
