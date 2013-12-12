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

import com.graphhopper.reader.OSMWay;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class BikeFlagEncoderTest
{
    private final BikeFlagEncoder encoder = (BikeFlagEncoder) new EncodingManager("CAR,BIKE").getEncoder("BIKE");

    @Test
    public void testGetSpeed()
    {
        long result = encoder.setProperties(10, true, true);
        assertEquals(10, encoder.getSpeed(result));
        OSMWay way = new OSMWay(1);
        way.setTag("highway", "primary");
        assertEquals(16, encoder.getSpeed(way));

        way.setTag("surface", "paved");
        assertEquals(25, encoder.getSpeed(way));
    }

    @Test
    public void testAccess()
    {
        Map<String, String> map = new HashMap<String, String>();
        OSMWay way = new OSMWay(1, map);

        map.put("highway", "motorway");
        assertFalse(encoder.isAllowed(way) > 0);

        map.put("highway", "footway");
        map.put("bicycle", "no");
        assertFalse(encoder.isAllowed(way) > 0);
        
        map.put("highway", "footway");
        map.put("bicycle", "yes");
        assertTrue(encoder.isAllowed(way) > 0);

        map.put("bicycle", "yes");
        map.put("highway", "cycleway");
        assertTrue(encoder.isAllowed(way) > 0);

        map.clear();
        map.put("highway", "path");
        assertFalse(encoder.isAllowed(way) > 0);

        map.put("highway", "path");
        map.put("bicycle", "yes");
        assertTrue(encoder.isAllowed(way) > 0);
        map.clear();

        map.put("highway", "path");
        map.put("foot", "official");
        assertFalse(encoder.isAllowed(way) > 0);

        map.put("bicycle", "official");
        assertTrue(encoder.isAllowed(way) > 0);

        map.clear();
        map.put("highway", "service");
        map.put("access", "no");
        assertFalse(encoder.isAllowed(way) > 0);

        map.clear();
        map.put("highway", "tertiary");
        map.put("motorroad", "yes");
        assertFalse(encoder.isAllowed(way) > 0);

        map.clear();
        map.put("highway", "track");
        map.put("ford", "yes");
        assertFalse(encoder.isAllowed(way) > 0);
        map.put("bicycle", "yes");
        assertTrue(encoder.isAllowed(way) > 0);

        map.clear();
        map.put("route", "ferry");
        assertTrue(encoder.isAllowed(way) > 0);
        map.put("bicycle", "no");
        assertFalse(encoder.isAllowed(way) > 0);

        map.clear();
        map.put("route", "ferry");
        map.put("foot", "yes");
        assertFalse(encoder.isAllowed(way) > 0);
    }

    @Test
    public void testTramStations()
    {
        Map<String, String> map = new HashMap<String, String>();
        OSMWay way = new OSMWay(1, map);
        map.put("highway", "secondary");
        map.put("railway", "rail");
        // disallow rail
        assertEquals(0, encoder.isAllowed(way));

        way = new OSMWay(1, map);
        map.put("highway", "secondary");
        map.put("railway", "station");
        // disallow stations
        assertEquals(0, encoder.isAllowed(way));

        way = new OSMWay(1, map);
        map.put("highway", "secondary");
        map.put("railway", "station");
        map.put("bicycle", "yes");
        // allow stations if explicitely tagged
        assertNotSame(0, encoder.isAllowed(way));

        way = new OSMWay(1, map);
        map.put("highway", "secondary");
        map.put("railway", "station");
        map.put("bicycle", "no");
        // disallow
        assertEquals(0, encoder.isAllowed(way));
    }
}
