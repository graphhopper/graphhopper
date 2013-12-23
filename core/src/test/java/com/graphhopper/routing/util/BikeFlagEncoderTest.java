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
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.TranslationMap.Translation;
import static com.graphhopper.util.TranslationMapTest.SINGLETON;
import org.junit.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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

        way.setTag("highway", "service");
        assertEquals(20, encoder.getSpeed(way));
        way.setTag("service", "parking_aisle");
        assertEquals(15, encoder.getSpeed(way));
        way.clearTags();

        // test speed for allowed pushing section types
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

    private String encodeDecodeWayType( String name, OSMWay way )
    {
        long allowed = 1;
        long flags = encoder.handleWayTags(way, allowed, 0);
        int pavement = encoder.getPavementCode(flags);
        int wayType = encoder.getWayTypeCode(flags);

        Translation enMap = SINGLETON.getWithFallBack(Locale.UK);
        return InstructionList.getWayName(name, pavement, wayType, enMap);
    }

    @Test
    public void testHandleWayTags()
    {
        Map<String, String> wayMap = new HashMap<String, String>();
        OSMWay way = new OSMWay(1, wayMap);
        wayMap.put("highway", "track");
        String wayType = encodeDecodeWayType("", way);
        assertEquals("pushing section, unpaved", wayType);

        wayMap.put("highway", "steps");
        wayType = encodeDecodeWayType("", way);
        assertEquals("pushing section", wayType);

        wayMap.put("highway", "steps");
        wayType = encodeDecodeWayType("Famous steps", way);
        assertEquals("Famous steps, pushing section", wayType);

        wayMap.put("highway", "path");
        wayType = encodeDecodeWayType("", way);
        assertEquals("pushing section, unpaved", wayType);

        wayMap.put("highway", "footway");
        wayType = encodeDecodeWayType("", way);
        assertEquals("pushing section", wayType);

        wayMap.put("highway", "footway");
        wayMap.put("surface", "pebblestone");
        wayType = encodeDecodeWayType("", way);
        assertEquals("pushing section", wayType);

        wayMap.put("highway", "path");
        wayMap.put("surface", "grass");
        wayType = encodeDecodeWayType("", way);
        assertEquals("pushing section, unpaved", wayType);

        wayMap.put("highway", "path");
        wayMap.put("surface", "concrete");
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
        wayMap.put("highway", "track");
        wayMap.put("foot", "yes");
        wayMap.put("surface", "paved");
        wayMap.put("tracktype", "grade1");
        wayType = encodeDecodeWayType("", way);
        assertEquals("pushing section", wayType);

        wayMap.put("highway", "track");
        wayMap.put("foot", "yes");
        wayMap.put("surface", "paved");
        wayMap.put("tracktype", "grade2");
        wayType = encodeDecodeWayType("", way);
        assertEquals("pushing section, unpaved", wayType);

        wayMap.clear();
        wayMap.put("highway", "footway");
        wayMap.put("bicycle", "yes");
        wayMap.put("surface", "grass");
        wayType = encodeDecodeWayType("", way);
        assertEquals("cycleway, unpaved", wayType);
    }

    @Test
    public void testHandleWayTagsInfluencedByRelation()
    {
        Map<String, String> wayMap = new HashMap<String, String>();
        OSMWay osmWay = new OSMWay(1, wayMap);
        wayMap.put("highway", "track");
        long allowed = encoder.acceptWay(osmWay);

        Map<String, String> relMap = new HashMap<String, String>();
        OSMRelation osmRel = new OSMRelation(1, relMap);

        long relFlags = encoder.handleRelationTags(osmRel, 0);
        // unchanged
        long flags = encoder.handleWayTags(osmWay, allowed, relFlags);
        assertEquals(14, encoder.getSpeed(flags));
        assertEquals(1, encoder.getWayTypeCode(flags));
        assertEquals(1, encoder.getPavementCode(flags));

        // relation code is PREFER
        relMap.put("route", "bicycle");
        relMap.put("network", "lcn");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        flags = encoder.handleWayTags(osmWay, allowed, relFlags);
        assertEquals(18, encoder.getSpeed(flags));
        assertEquals(1, encoder.getWayTypeCode(flags));
        assertEquals(1, encoder.getPavementCode(flags));

        // relation code is VERY_NICE
        relMap.put("network", "rcn");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        flags = encoder.handleWayTags(osmWay, allowed, relFlags);
        assertEquals(20, encoder.getSpeed(flags));

        // relation code is OUTSTANDING_NICE
        relMap.put("network", "ncn");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        flags = encoder.handleWayTags(osmWay, allowed, relFlags);
        assertEquals(24, encoder.getSpeed(flags));

        // test max and min speed
        final AtomicInteger fakeSpeed = new AtomicInteger(40);
        BikeFlagEncoder fakeEncoder = new BikeFlagEncoder()
        {
            @Override
            int relationWeightCodeToSpeed( int highwaySpeed, int relationCode )
            {
                return fakeSpeed.get();
            }
        };
        // call necessary register
        new EncodingManager().registerEdgeFlagEncoder(fakeEncoder);

        flags = fakeEncoder.handleWayTags(osmWay, allowed, 1);
        assertEquals(30, fakeEncoder.getSpeed(flags));

        fakeSpeed.set(-2);
        flags = fakeEncoder.handleWayTags(osmWay, allowed, 1);
        assertEquals(0, fakeEncoder.getSpeed(flags));

        // PREFER relation, but tertiary road
        // => no pushing section but road wayTypeCode and faster
        wayMap.clear();
        wayMap.put("highway", "tertiary");

        relMap.put("route", "bicycle");
        relMap.put("network", "lcn");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        flags = encoder.handleWayTags(osmWay, allowed, relFlags);
        assertEquals(20, encoder.getSpeed(flags));
        assertEquals(0, encoder.getWayTypeCode(flags));
    }
}
