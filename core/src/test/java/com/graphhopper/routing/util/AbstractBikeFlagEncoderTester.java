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

import com.graphhopper.reader.OSMWay;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.TranslationMap;
import static com.graphhopper.util.TranslationMapTest.SINGLETON;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Peter Karich
 * @author ratrun
 */
public abstract class AbstractBikeFlagEncoderTester
{
    protected BikeFlagCommonEncoder encoder;

    @Before
    public void setUp()
    {
        encoder = createBikeEncoder();
    }

    abstract BikeFlagCommonEncoder createBikeEncoder();

    public double getEncodedDecodedSpeed( OSMWay way )
    {
        long allowed = encoder.acceptBit;
        long flags = encoder.handleWayTags(way, allowed, 0);
        return encoder.getSpeed(flags);
    }

    public String encodeDecodeWayType( String name, OSMWay way )
    {
        long allowed = encoder.acceptBit;
        long flags = encoder.handleWayTags(way, allowed, 0);
        int pavement = encoder.getPavementCode(flags);
        int wayType = encoder.getWayTypeCode(flags);

        TranslationMap.Translation enMap = SINGLETON.getWithFallBack(Locale.UK);
        return InstructionList.getWayName(name, pavement, wayType, enMap);
    }

    @Test
    public void testAccess()
    {
        Map<String, String> map = new HashMap<String, String>();
        OSMWay way = new OSMWay(1, map);

        map.put("highway", "motorway");
        assertFalse(encoder.acceptWay(way) > 0);

        map.put("highway", "footway");
        assertTrue(encoder.acceptWay(way) > 0);

        map.put("bicycle", "no");
        assertFalse(encoder.acceptWay(way) > 0);

        map.put("highway", "footway");
        map.put("bicycle", "yes");
        assertTrue(encoder.acceptWay(way) > 0);

        map.put("highway", "pedestrian");
        map.put("bicycle", "no");
        assertFalse(encoder.acceptWay(way) > 0);

        map.put("highway", "pedestrian");
        map.put("bicycle", "yes");
        assertTrue(encoder.acceptWay(way) > 0);

        map.put("bicycle", "yes");
        map.put("highway", "cycleway");
        assertTrue(encoder.acceptWay(way) > 0);

        map.clear();
        map.put("highway", "path");
        assertTrue(encoder.acceptWay(way) > 0);

        map.put("highway", "path");
        map.put("bicycle", "yes");
        assertTrue(encoder.acceptWay(way) > 0);
        map.clear();

        map.put("highway", "track");
        map.put("bicycle", "yes");
        assertTrue(encoder.acceptWay(way) > 0);
        map.clear();

        map.put("highway", "track");
        assertTrue(encoder.acceptWay(way) > 0);

        map.put("mtb", "yes");
        assertTrue(encoder.acceptWay(way) > 0);

        map.clear();
        map.put("highway", "path");
        map.put("foot", "official");
        assertTrue(encoder.acceptWay(way) > 0);

        map.put("bicycle", "official");
        assertTrue(encoder.acceptWay(way) > 0);

        map.clear();
        map.put("highway", "service");
        map.put("access", "no");
        assertFalse(encoder.acceptWay(way) > 0);

        map.clear();
        map.put("highway", "tertiary");
        map.put("motorroad", "yes");
        assertFalse(encoder.acceptWay(way) > 0);

        map.clear();
        map.put("highway", "track");
        map.put("ford", "yes");
        assertFalse(encoder.acceptWay(way) > 0);
        map.put("bicycle", "yes");
        assertTrue(encoder.acceptWay(way) > 0);

        map.clear();
        map.put("route", "ferry");
        assertTrue(encoder.acceptWay(way) > 0);
        map.put("bicycle", "no");
        assertFalse(encoder.acceptWay(way) > 0);

        map.clear();
        map.put("route", "ferry");
        map.put("foot", "yes");
        assertFalse(encoder.acceptWay(way) > 0);
    }

    @Test
    public void testTramStations()
    {
        Map<String, String> map = new HashMap<String, String>();
        OSMWay way = new OSMWay(1, map);
        map.put("highway", "secondary");
        map.put("railway", "rail");
        // disallow rail
        assertEquals(0, encoder.acceptWay(way));

        way = new OSMWay(1, map);
        map.put("highway", "secondary");
        map.put("railway", "station");
        // disallow stations
        assertEquals(0, encoder.acceptWay(way));

        way = new OSMWay(1, map);
        map.put("highway", "secondary");
        map.put("railway", "station");
        map.put("bicycle", "yes");
        // allow stations if explicitely tagged
        assertNotSame(0, encoder.acceptWay(way));

        way = new OSMWay(1, map);
        map.put("highway", "secondary");
        map.put("railway", "station");
        map.put("bicycle", "no");
        // disallow
        assertEquals(0, encoder.acceptWay(way));
    }

    @Test
    public void testHandleCommonWayTags()
    {
        Map<String, String> wayMap = new HashMap<String, String>();
        OSMWay way = new OSMWay(1, wayMap);
        String wayType;

        wayMap.put("highway", "steps");
        wayType = encodeDecodeWayType("", way);
        assertEquals("pushing section", wayType);

        wayMap.put("highway", "steps");
        wayType = encodeDecodeWayType("Famous steps", way);
        assertEquals("Famous steps, pushing section", wayType);

        wayMap.put("highway", "footway");
        wayType = encodeDecodeWayType("", way);
        assertEquals("pushing section", wayType);

        wayMap.put("highway", "footway");
        wayMap.put("surface", "pebblestone");
        wayType = encodeDecodeWayType("", way);
        assertEquals("pushing section", wayType);

        wayMap.put("highway", "residential");
        wayType = encodeDecodeWayType("", way);
        assertEquals("road", wayType);

        wayMap.put("highway", "cycleway");
        wayType = encodeDecodeWayType("", way);
        assertEquals("cycleway", wayType);

        wayMap.put("surface", "grass");
        wayType = encodeDecodeWayType("", way);
        assertEquals("cycleway, unpaved", wayType);

        wayMap.put("surface", "asphalt");
        wayType = encodeDecodeWayType("", way);
        assertEquals("cycleway", wayType);

        wayMap.put("highway", "footway");
        wayMap.put("bicycle", "yes");
        wayMap.put("surface", "grass");
        wayType = encodeDecodeWayType("", way);
        assertEquals("cycleway, unpaved", wayType);

        wayMap.clear();
        wayMap.put("highway", "footway");
        wayMap.put("bicycle", "yes");
        wayMap.put("surface", "grass");
        wayType = encodeDecodeWayType("", way);
        assertEquals("cycleway, unpaved", wayType);
    }

}
