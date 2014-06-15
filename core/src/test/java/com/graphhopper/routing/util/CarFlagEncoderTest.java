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
        OSMWay way = new OSMWay(1);
        assertFalse(encoder.acceptWay(way) > 0);
        way.setTag("highway", "service");
        assertTrue(encoder.acceptWay(way) > 0);
        way.setTag("access", "no");
        assertFalse(encoder.acceptWay(way) > 0);

        way.clearTags();
        way.setTag("highway", "track");
        assertTrue(encoder.acceptWay(way) > 0);

        way.setTag("motorcar", "no");
        assertFalse(encoder.acceptWay(way) > 0);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("tracktype", "grade2");
        // disallow too rough tracks
        assertFalse(encoder.acceptWay(way) > 0);

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "no");
        way.setTag("motorcar", "yes");
        assertTrue(encoder.acceptWay(way) > 0);

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "delivery");
        assertFalse(encoder.acceptWay(way) > 0);

        way.clearTags();
        way.setTag("highway", "unclassified");
        way.setTag("ford", "yes");
        assertFalse(encoder.acceptWay(way) > 0);
        way.setTag("motorcar", "yes");
        assertTrue(encoder.acceptWay(way) > 0);

        way.clearTags();
        way.setTag("route", "ferry");
        assertTrue(encoder.acceptWay(way) > 0);
        way.setTag("motorcar", "no");
        assertFalse(encoder.acceptWay(way) > 0);

        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("foot", "yes");
        assertFalse(encoder.acceptWay(way) > 0);

        way.clearTags();
        way.setTag("highway", "primary");
        long flags = encoder.handleWayTags(way, encoder.acceptWay(way), 0);
        assertTrue(encoder.isBool(flags, FlagEncoder.K_FORWARD));
        assertTrue(encoder.isBool(flags, FlagEncoder.K_BACKWARD));
        way.setTag("oneway", "yes");
        flags = encoder.handleWayTags(way, encoder.acceptWay(way), 0);
        assertTrue(encoder.isBool(flags, FlagEncoder.K_FORWARD));
        assertFalse(encoder.isBool(flags, FlagEncoder.K_BACKWARD));
    }

    @Test
    public void testSetAccess()
    {
        assertTrue(encoder.isBool(encoder.setProperties(0, true, true), FlagEncoder.K_FORWARD));
        assertTrue(encoder.isBool(encoder.setProperties(0, true, true), FlagEncoder.K_BACKWARD));

        assertTrue(encoder.isBool(encoder.setProperties(0, true, false), FlagEncoder.K_FORWARD));
        assertFalse(encoder.isBool(encoder.setProperties(0, true, false), FlagEncoder.K_BACKWARD));

        assertFalse(encoder.isBool(encoder.setProperties(0, false, true), FlagEncoder.K_FORWARD));
        assertTrue(encoder.isBool(encoder.setProperties(0, false, true), FlagEncoder.K_BACKWARD));

        assertTrue(encoder.isBool(encoder.flagsDefault(true, true), FlagEncoder.K_FORWARD));
        assertTrue(encoder.isBool(encoder.flagsDefault(true, true), FlagEncoder.K_BACKWARD));

        assertTrue(encoder.isBool(encoder.flagsDefault(true, false), FlagEncoder.K_FORWARD));
        assertFalse(encoder.isBool(encoder.flagsDefault(true, false), FlagEncoder.K_BACKWARD));

        long flags = encoder.flagsDefault(true, true);
        // disable access
        assertFalse(encoder.isBool(encoder.setAccess(flags, false, false), FlagEncoder.K_FORWARD));
        assertFalse(encoder.isBool(encoder.setAccess(flags, false, false), FlagEncoder.K_BACKWARD));
    }

    @Test
    public void testMaxSpeed()
    {
        OSMWay way = new OSMWay(1);
        way.setTag("highway", "trunk");
        way.setTag("maxspeed", "500");
        long allowed = encoder.acceptWay(way);
        long encoded = encoder.handleWayTags(way, allowed, 0);
        assertEquals(100, encoder.getSpeed(encoded), 1e-1);

        way = new OSMWay(1);
        way.setTag("highway", "primary");
        way.setTag("maxspeed:backward", "10");
        way.setTag("maxspeed:forward", "20");
        encoded = encoder.handleWayTags(way, encoder.acceptWay(way), 0);
        assertEquals(10, encoder.getSpeed(encoded), 1e-1);

        way = new OSMWay(1);
        way.setTag("highway", "primary");
        way.setTag("maxspeed:forward", "20");
        encoded = encoder.handleWayTags(way, encoder.acceptWay(way), 0);
        assertEquals(20, encoder.getSpeed(encoded), 1e-1);

        way = new OSMWay(1);
        way.setTag("highway", "primary");
        way.setTag("maxspeed:backward", "20");
        encoded = encoder.handleWayTags(way, encoder.acceptWay(way), 0);
        assertEquals(20, encoder.getSpeed(encoded), 1e-1);
    }

    @Test
    public void testSpeed()
    {
        // limit bigger than default road speed
        OSMWay way = new OSMWay(1);
        way.setTag("highway", "trunk");
        way.setTag("maxspeed", "110");
        long allowed = encoder.acceptWay(way);
        long encoded = encoder.handleWayTags(way, allowed, 0);
        assertEquals(100, encoder.getSpeed(encoded), 1e-1);

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("surface", "cobblestone");
        allowed = encoder.acceptWay(way);
        encoded = encoder.handleWayTags(way, allowed, 0);
        assertEquals(30, encoder.getSpeed(encoded), 1e-1);

        way.clearTags();
        way.setTag("highway", "track");
        allowed = encoder.acceptWay(way);
        encoded = encoder.handleWayTags(way, allowed, 0);
        assertEquals(15, encoder.getSpeed(encoded), 1e-1);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("tracktype", "grade1");
        allowed = encoder.acceptWay(way);
        encoded = encoder.handleWayTags(way, allowed, 0);
        assertEquals(20, encoder.getSpeed(encoded), 1e-1);

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
    public void testFerry()
    {
        assertTrue(encoder.isBool(encoder.setBool(0, FlagEncoder.K_FERRY, true), FlagEncoder.K_FERRY));
        assertFalse(encoder.isBool(encoder.setBool(0, FlagEncoder.K_FERRY, false), FlagEncoder.K_FERRY));
    }

    @Test
    public void testRailway()
    {
        OSMWay way = new OSMWay(1);
        way.setTag("highway", "secondary");
        way.setTag("railway", "rail");
        // disallow rail
        assertTrue(encoder.acceptWay(way) == 0);

        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("railway", "abandoned");
        assertTrue(encoder.acceptWay(way) == 0);

        way.setTag("highway", "track");
        assertTrue(encoder.acceptWay(way) > 0);

        way.setTag("motorcar", "no");
        assertTrue(encoder.acceptWay(way) == 0);

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
        way.setTag("estimated_distance", 50000);
        // accept
        assertNotSame(0, encoder.acceptWay(way));
        // calculate speed from estimated_distance and duration
        assertEquals(60, encoder.getSpeed(encoder.handleFerryTags(way, 20, 30, 40)), 1e-1);
    }

    @Test
    public void testSwapDir()
    {
        long swappedFlags = encoder.reverseFlags(encoder.flagsDefault(true, true));
        assertTrue(encoder.isBool(swappedFlags, FlagEncoder.K_FORWARD));
        assertTrue(encoder.isBool(swappedFlags, FlagEncoder.K_BACKWARD));

        swappedFlags = encoder.reverseFlags(encoder.flagsDefault(true, false));

        assertFalse(encoder.isBool(swappedFlags, FlagEncoder.K_FORWARD));
        assertTrue(encoder.isBool(swappedFlags, FlagEncoder.K_BACKWARD));

        assertEquals(0, encoder.reverseFlags(0));
    }

    @Test
    public void testBarrierAccess()
    {
        OSMNode node = new OSMNode(1, -1, -1);
        node.setTag("barrier", "lift_gate");
        node.setTag("access", "yes");
        // no barrier!
        assertTrue(encoder.handleNodeTags(node) == 0);

        node = new OSMNode(1, -1, -1);
        node.setTag("barrier", "lift_gate");
        node.setTag("bicycle", "yes");
        // barrier!
        assertTrue(encoder.handleNodeTags(node) > 0);

        node = new OSMNode(1, -1, -1);
        node.setTag("barrier", "lift_gate");
        node.setTag("access", "yes");
        node.setTag("bicycle", "yes");
        // should this be a barrier for motorcars too?
        // assertTrue(encoder.handleNodeTags(node) > 0);

        node = new OSMNode(1, -1, -1);
        node.setTag("barrier", "lift_gate");
        node.setTag("access", "no");
        node.setTag("motorcar", "yes");
        // no barrier!
        assertTrue(encoder.handleNodeTags(node) == 0);
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

    @Test
    public void testSetToMaxSpeed()
    {
        OSMWay way = new OSMWay(12);
        way.setTag("maxspeed", "90");
        assertEquals(90, encoder.getMaxSpeed(way), 1e-2);
    }
}
