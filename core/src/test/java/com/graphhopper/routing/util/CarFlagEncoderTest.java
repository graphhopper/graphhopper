/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the
 *  License at
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
    private EncodingManager em = new EncodingManager("CAR,BIKE,FOOT");
    private CarFlagEncoder encoder = (CarFlagEncoder) em.getEncoder("CAR");

    @Test
    public void testAccess()
    {
        Map<String, String> map = new HashMap<String, String>();
        OSMWay way = new OSMWay(1, map);
        assertFalse(encoder.isAllowed(way) > 0);
        map.put("highway", "service");
        assertTrue(encoder.isAllowed(way) > 0);
        map.put("access", "no");
        assertFalse(encoder.isAllowed(way) > 0);

        map.clear();
        map.put("highway", "track");
        map.put("motorcar", "no");
        assertFalse(encoder.isAllowed(way) > 0);

        map.clear();
        map.put("highway", "unclassified");
        map.put("ford", "yes");
        assertFalse(encoder.isAllowed(way) > 0);
        map.put("motorcar", "yes");
        assertTrue(encoder.isAllowed(way) > 0);
        
        map.clear();        
        map.put("route", "ferry");
        assertTrue(encoder.isAllowed(way) > 0);
        map.put("motorcar", "no");
        assertFalse(encoder.isAllowed(way) > 0);
    }

    @Test
    public void testSpeedLimitBiggerThanMaxValue()
    {
        Map<String, String> map = new HashMap<String, String>();
        OSMWay way = new OSMWay(1, map);
        map.put("highway", "trunk");
        map.put("maxspeed", "500");
        int allowed = encoder.isAllowed(way);
        int encoded = encoder.handleWayTags(allowed, way);
        assertEquals(100, encoder.getSpeed(encoded));                
    }

    @Test
    public void testSpeed()
    {
        // limit bigger than default road speed
        Map<String, String> map = new HashMap<String, String>();
        OSMWay way = new OSMWay(1, map);
        map.put("highway", "trunk");
        map.put("maxspeed", "110");
        int allowed = encoder.isAllowed(way);
        int encoded = encoder.handleWayTags(allowed, way);
        assertEquals(95, encoder.getSpeed(encoded));
        
        map.clear();
        map.put("highway", "residential");
        map.put("surface", "cobblestone");
        allowed = encoder.isAllowed(way);        
        encoded = encoder.handleWayTags(allowed, way);
        assertEquals(30, encoder.getSpeed(encoded));
    }

    @Test
    public void testRailway()
    {        
        Map<String, String> map = new HashMap<String, String>();
        OSMWay way = new OSMWay(1, map);
        map.put("highway", "secondary");
        map.put("railway", "rail");
        // disallow rail
        assertEquals(0, encoder.isAllowed(way));

        way = new OSMWay(1, map);
        map.put("highway", "secondary");
        map.put("railway", "tram");
        // but allow tram to be on the same way
        assertNotSame(0, encoder.isAllowed(way));
    }

    @Test
    public void testBasics()
    {
        assertTrue(encoder.isForward(encoder.flagsDefault(true)));
        assertTrue(encoder.isBackward(encoder.flagsDefault(true)));
        assertTrue(encoder.isBoth(encoder.flagsDefault(true)));

        assertTrue(encoder.isForward(encoder.flagsDefault(false)));
        assertFalse(encoder.isBackward(encoder.flagsDefault(false)));
        assertFalse(encoder.isBoth(encoder.flagsDefault(false)));
    }

    @Test
    public void testOverwrite()
    {
        int forward = encoder.flags(10, false);
        int backward = encoder.swapDirection(forward);
        int both = encoder.flags(20, true);
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
        int swappedFlags = encoder.swapDirection(encoder.flagsDefault(true));
        assertTrue(encoder.isForward(swappedFlags));
        assertTrue(encoder.isBackward(swappedFlags));

        swappedFlags = encoder.swapDirection(encoder.flagsDefault(false));

        assertFalse(encoder.isForward(swappedFlags));
        assertTrue(encoder.isBackward(swappedFlags));

        assertEquals(0, encoder.swapDirection(0));
    }
}
