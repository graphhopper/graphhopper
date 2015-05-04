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

        // for now allow grade1+2+3 for every country, see #253
        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("tracktype", "grade2");
        assertTrue(encoder.acceptWay(way) > 0);
        way.setTag("tracktype", "grade4");
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
        assertTrue(encoder.isFerry(encoder.acceptWay(way)));
        way.setTag("motorcar", "no");
        assertFalse(encoder.acceptWay(way) > 0);

        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("foot", "yes");
        assertFalse(encoder.acceptWay(way) > 0);
        assertFalse(encoder.isFerry(encoder.acceptWay(way)));
    }

    @Test
    public void testOneway()
    {
        OSMWay way = new OSMWay(1);
        way.setTag("highway", "primary");
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
    }

    @Test
    public void testMilitaryAccess()
    {
        OSMWay way = new OSMWay(1);
        way.setTag("highway", "track");
        way.setTag("access", "military");
        assertFalse(encoder.acceptWay(way) > 0);
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

        assertTrue(encoder.isForward(encoder.flagsDefault(true, true)));
        assertTrue(encoder.isBackward(encoder.flagsDefault(true, true)));

        assertTrue(encoder.isForward(encoder.flagsDefault(true, false)));
        assertFalse(encoder.isBackward(encoder.flagsDefault(true, false)));

        long flags = encoder.flagsDefault(true, true);
        // disable access
        assertFalse(encoder.isForward(encoder.setAccess(flags, false, false)));
        assertFalse(encoder.isBackward(encoder.setAccess(flags, false, false)));
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
    public void testRoundabout()
    {
        long flags = encoder.setAccess(0, true, true);
        long resFlags = encoder.setBool(flags, FlagEncoder.K_ROUNDABOUT, true);
        assertTrue(encoder.isBool(resFlags, FlagEncoder.K_ROUNDABOUT));
        assertTrue(encoder.isForward(resFlags));
        assertTrue(encoder.isBackward(resFlags));

        resFlags = encoder.setBool(flags, FlagEncoder.K_ROUNDABOUT, false);
        assertFalse(encoder.isBool(resFlags, FlagEncoder.K_ROUNDABOUT));
        assertTrue(encoder.isForward(resFlags));
        assertTrue(encoder.isBackward(resFlags));

        OSMWay way = new OSMWay(1);
        way.setTag("highway", "motorway");
        flags = encoder.handleWayTags(way, encoder.acceptBit, 0);
        assertTrue(encoder.isForward(flags));
        assertTrue(encoder.isBackward(flags));
        assertFalse(encoder.isBool(flags, FlagEncoder.K_ROUNDABOUT));

        way.setTag("junction", "roundabout");
        flags = encoder.handleWayTags(way, encoder.acceptBit, 0);
        assertTrue(encoder.isForward(flags));
        assertFalse(encoder.isBackward(flags));
        assertTrue(encoder.isBool(flags, FlagEncoder.K_ROUNDABOUT));
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

        // on disallowed highway, railway is allowed, sometimes incorrectly mapped
        way.setTag("highway", "track");
        assertTrue(encoder.acceptWay(way) > 0);

        // this is fully okay as sometimes old rails are on the road
        way.setTag("highway", "primary");
        way.setTag("railway", "historic");
        assertTrue(encoder.acceptWay(way) > 0);

        way.setTag("motorcar", "no");
        assertTrue(encoder.acceptWay(way) == 0);

        way = new OSMWay(1);
        way.setTag("highway", "secondary");
        way.setTag("railway", "tram");
        // but allow tram to be on the same way
        assertTrue(encoder.acceptWay(way) > 0);

        way = new OSMWay(1);
        way.setTag("route", "shuttle_train");
        way.setTag("motorcar", "yes");
        way.setTag("bicycle", "no");
        way.setTag("duration", "35");
        way.setTag("estimated_distance", 50000);
        // accept
        assertTrue(encoder.acceptWay(way) > 0);
        // calculate speed from estimated_distance and duration
        assertEquals(60, encoder.getSpeed(encoder.handleFerryTags(way, 20, 30, 40)), 1e-1);
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

        node = new OSMNode(1, -1, -1);
        node.setTag("barrier", "bollard");
        // barrier!
        assertTrue(encoder.handleNodeTags(node) > 0);

        // ignore other access tags for absolute barriers!
        node.setTag("motorcar", "yes");
        // still barrier!
        assertTrue(encoder.handleNodeTags(node) > 0);
    }

    @Test
    public void testTurnFlagEncoding_noCosts()
    {
        FlagEncoder tmpEnc = new CarFlagEncoder(8, 5, 0);
        EncodingManager em = new EncodingManager(tmpEnc);

        long flags_r0 = tmpEnc.getTurnFlags(true, 0);
        long flags_0 = tmpEnc.getTurnFlags(false, 0);

        long flags_r20 = tmpEnc.getTurnFlags(true, 0);
        long flags_20 = tmpEnc.getTurnFlags(false, 20);

        assertEquals(0, tmpEnc.getTurnCost(flags_r0), 1e-1);
        assertEquals(0, tmpEnc.getTurnCost(flags_0), 1e-1);

        assertEquals(0, tmpEnc.getTurnCost(flags_r20), 1e-1);
        assertEquals(0, tmpEnc.getTurnCost(flags_20), 1e-1);

        assertFalse(tmpEnc.isTurnRestricted(flags_r0));
        assertFalse(tmpEnc.isTurnRestricted(flags_0));

        assertFalse(tmpEnc.isTurnRestricted(flags_r20));
        assertFalse(tmpEnc.isTurnRestricted(flags_20));
    }

    @Test
    public void testTurnFlagEncoding_withCosts()
    {
        FlagEncoder tmpEncoder = new CarFlagEncoder(8, 5, 127);
        EncodingManager em = new EncodingManager(tmpEncoder);

        long flags_r0 = tmpEncoder.getTurnFlags(true, 0);
        long flags_0 = tmpEncoder.getTurnFlags(false, 0);
        assertTrue(Double.isInfinite(tmpEncoder.getTurnCost(flags_r0)));
        assertEquals(0, tmpEncoder.getTurnCost(flags_0), 1e-1);
        assertTrue(tmpEncoder.isTurnRestricted(flags_r0));
        assertFalse(tmpEncoder.isTurnRestricted(flags_0));

        long flags_r20 = tmpEncoder.getTurnFlags(true, 0);
        long flags_20 = tmpEncoder.getTurnFlags(false, 20);
        assertTrue(Double.isInfinite(tmpEncoder.getTurnCost(flags_r20)));
        assertEquals(20, tmpEncoder.getTurnCost(flags_20), 1e-1);
        assertTrue(tmpEncoder.isTurnRestricted(flags_r20));
        assertFalse(tmpEncoder.isTurnRestricted(flags_20));

        long flags_r220 = tmpEncoder.getTurnFlags(true, 0);
        try
        {
            tmpEncoder.getTurnFlags(false, 220);
            assertTrue(false);
        } catch (Exception ex)
        {
        }
        assertTrue(Double.isInfinite(tmpEncoder.getTurnCost(flags_r220)));
        assertTrue(tmpEncoder.isTurnRestricted(flags_r220));
    }

    @Test
    public void testMaxValue()
    {
        CarFlagEncoder instance = new CarFlagEncoder(8, 0.5, 0);
        EncodingManager em = new EncodingManager(instance);
        OSMWay way = new OSMWay(1);
        way.setTag("highway", "motorway_link");
        way.setTag("maxspeed", "60 mph");
        long flags = instance.handleWayTags(way, 1, 0);

        // double speed = AbstractFlagEncoder.parseSpeed("60 mph");
        // => 96.56 * 0.9 => 86.9
        assertEquals(86.9, instance.getSpeed(flags), 1e-1);
        flags = instance.reverseFlags(flags);
        assertEquals(86.9, instance.getSpeed(flags), 1e-1);
        
        // test that maxPossibleValue  is not exceeded
        way = new OSMWay(2);
        way.setTag("highway", "motorway_link");
        way.setTag("maxspeed", "70 mph");
        flags = instance.handleWayTags(way, 1, 0);
        assertEquals(100, instance.getSpeed(flags), 1e-1);
    }

    @Test
    public void testRegisterOnlyOnceAllowed()
    {
        CarFlagEncoder instance = new CarFlagEncoder(8, 0.5, 0);
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

    @Test
    public void testFordAccess()
    {
        OSMNode node = new OSMNode(0, 0.0, 0.0);
        node.setTag("ford", "yes");

        OSMWay way = new OSMWay(1);
        way.setTag("highway", "unclassified");
        way.setTag("ford", "yes");

        // Node and way are initially blocking
        assertTrue(encoder.isBlockFords());
        assertFalse(encoder.acceptWay(way) > 0);
        assertTrue(encoder.handleNodeTags(node) > 0);

        try
        {
            // Now they are passable
            encoder.setBlockFords(false);
            assertTrue(encoder.acceptWay(way) > 0);
            assertFalse(encoder.handleNodeTags(node) > 0);
        } finally
        {
            encoder.setBlockFords(true);
        }
    }

    @Test
    public void testCombination()
    {
        OSMWay way = new OSMWay(123);
        way.setTag("highway", "cycleway");
        way.setTag("sac_scale", "hiking");        

        long flags = em.acceptWay(way);
        long edgeFlags = em.handleWayTags(way, flags, 0);
        assertFalse(encoder.isBackward(edgeFlags));
        assertFalse(encoder.isForward(edgeFlags));
        assertTrue(em.getEncoder("bike").isBackward(edgeFlags));
        assertTrue(em.getEncoder("bike").isForward(edgeFlags));
    }
}
