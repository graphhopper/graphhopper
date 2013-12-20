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

import com.graphhopper.util.InstructionUtil;
import com.graphhopper.reader.OSMWay;
import com.graphhopper.util.TranslationMap;
import com.graphhopper.util.TranslationMap.Translation;
import static com.graphhopper.util.TranslationMapTest.SINGLETON;
import org.junit.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class BikeFlagEncoderTest
{
    private final BikeFlagEncoder encoder = (BikeFlagEncoder) new EncodingManager("CAR,BIKE").getEncoder("BIKE");
    public final static TranslationMap SINGLETON = new TranslationMap().doImport();

    @Test
    public void testGetSpeed()
    {
        long result = encoder.setProperties(10, true, true);
        assertEquals(10, encoder.getSpeed(result));
        OSMWay way = new OSMWay(1);
        way.setTag("highway", "primary");
        assertEquals(18, encoder.getSpeed(way));
        
        way.setTag("highway", "residential");
        assertEquals(20, encoder.getSpeed(way));
        // Test pushing section speeds
        way.setTag("highway", "footway");
        assertEquals(4, encoder.getSpeed(way));
        way.setTag("highway", "track");
        assertEquals(4, encoder.getSpeed(way));

        way.setTag("highway", "steps");
        assertEquals(2, encoder.getSpeed(way));
        
        //Test speed for allowed pushing section types
        way.setTag("highway", "track");
        way.setTag("bicycle", "yes");
        assertEquals(20, encoder.getSpeed(way));
        
        way.setTag("highway", "track");
        way.setTag("bicycle", "yes");
        way.setTag("tracktype", "grade3");
        assertEquals(12, encoder.getSpeed(way));
        
        way.setTag("surface", "paved");
        assertEquals(20, encoder.getSpeed(way));

    }

    @Test
    public void testAccess()
    {
        Map<String, String> map = new HashMap<String, String>();
        OSMWay way = new OSMWay(1, map);

        map.put("highway", "motorway");
        assertFalse(encoder.isAllowed(way) > 0);

        map.put("highway", "footway");
        assertTrue(encoder.isAllowed(way) > 0);
      
        map.put("bicycle", "no");
        assertFalse(encoder.isAllowed(way) > 0);
        
        map.put("highway", "footway");
        map.put("bicycle", "yes");
        assertTrue(encoder.isAllowed(way) > 0);

        map.put("highway", "pedestrian");
        map.put("bicycle", "no");
        assertFalse(encoder.isAllowed(way) > 0);
        
        map.put("highway", "pedestrian");
        map.put("bicycle", "yes");
        assertTrue(encoder.isAllowed(way) > 0);
        
        map.put("bicycle", "yes");
        map.put("highway", "cycleway");
        assertTrue(encoder.isAllowed(way) > 0);

        map.clear();
        map.put("highway", "path");
        assertTrue(encoder.isAllowed(way) > 0);

        map.put("highway", "path");
        map.put("bicycle", "yes");
        assertTrue(encoder.isAllowed(way) > 0);
        map.clear();

        map.put("highway", "track");
        map.put("bicycle", "yes");
        assertTrue(encoder.isAllowed(way) > 0);
        map.clear();        

        map.put("highway", "track");
        assertTrue(encoder.isAllowed(way) > 0);
        
        map.put("mtb", "yes");
        assertTrue(encoder.isAllowed(way) > 0);
        
        map.clear();        
        map.put("highway", "path");
        map.put("foot", "official");
        assertTrue(encoder.isAllowed(way) > 0);

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
    
    private String encodeDecodeWayType(String name, OSMWay way)
    {
        
        long allowed=1;
        long flags=encoder.handleWayTags( allowed,  way, 0 );
        int pavement=encoder.getPavementCode(flags);
        int wayType=encoder.getWayTypeCode(flags);
        
        Translation enMap = SINGLETON.getWithFallBack(Locale.UK);
        return InstructionUtil.getWayName (name, pavement, wayType,  enMap);

    }
    
    @Test
    public void testhandleWayTags()
    {
        Map<String, String> map = new HashMap<String, String>();
        OSMWay way = new OSMWay(1, map);
        String wayType;
        
        map.put("highway", "track");
        wayType = encodeDecodeWayType("", way);
        assertEquals("pushing section,unpaved",wayType);
 
        map.put("highway", "steps");
        wayType = encodeDecodeWayType("", way);
        assertEquals("pushing section",wayType);
         
        map.put("highway", "steps");
        wayType = encodeDecodeWayType("Famous steps", way);
        assertEquals("Famous steps,pushing section",wayType);
                
        map.put("highway", "path");
        wayType = encodeDecodeWayType("", way);
        assertEquals("pushing section,unpaved",wayType);
        
        map.put("highway", "footway");
        wayType = encodeDecodeWayType("", way);       
        assertEquals("pushing section",wayType);
        
        map.put("highway", "footway");
        map.put("surface", "pebblestone");
        wayType = encodeDecodeWayType("", way);
        assertEquals("pushing section",wayType);

        
        map.put("highway", "path");
        map.put("surface", "grass");
        wayType = encodeDecodeWayType("", way);
        assertEquals("pushing section,unpaved",wayType);
        
        map.put("highway", "path");
        map.put("surface", "concrete");
        wayType = encodeDecodeWayType("", way);
        assertEquals("pushing section",wayType);
        
        map.put("highway", "residential");
        wayType = encodeDecodeWayType("", way);
        assertEquals("road",wayType);

        map.put("highway", "cycleway");
        wayType = encodeDecodeWayType("", way);
        assertEquals("cycleway",wayType);

        map.put("surface", "grass");
        wayType = encodeDecodeWayType("", way);
        assertEquals("cycleway,unpaved",wayType);

        map.put("surface", "asphalt");
        wayType = encodeDecodeWayType("", way);
        assertEquals("cycleway",wayType);        
        
        map.put("highway", "footway");        
        map.put("bicycle", "yes");
        map.put("surface", "grass");
        wayType = encodeDecodeWayType("", way);
        assertEquals("cycleway,unpaved",wayType);
        
        map.clear();

        map.put("highway", "track");        
        map.put("foot", "yes");
        map.put("surface", "paved");
        map.put("tracktype", "grade1");
        wayType = encodeDecodeWayType("", way);
        assertEquals("pushing section",wayType);

        map.put("highway", "track");        
        map.put("foot", "yes");
        map.put("surface", "paved");
        map.put("tracktype", "grade2");
        wayType = encodeDecodeWayType("", way);
        assertEquals("pushing section,unpaved",wayType);
        
        map.clear();
        map.put("highway", "footway");        
        map.put("bicycle", "yes");
        map.put("surface", "grass");
        wayType = encodeDecodeWayType("", way);
        
        long flags;        
        long allowed=1;
        flags=encoder.handleWayTags( allowed,  way, 4 );
        assertEquals(735, flags);
        
        flags=encoder.handleWayTags( allowed,  way, 5 );
        assertEquals(743, flags);
        
        flags=encoder.handleWayTags( allowed,  way, 6 );
        assertEquals(747, flags);

        flags=encoder.handleWayTags( allowed,  way, 7 );
        assertEquals(755, flags);

        allowed=1;
        flags=encoder.handleWayTags( allowed,  way, 18 );
        assertEquals(763, flags);

        allowed=1;
        flags=encoder.handleWayTags( allowed,  way, -18 );
        assertEquals(707, flags);
        
        
    }
}
