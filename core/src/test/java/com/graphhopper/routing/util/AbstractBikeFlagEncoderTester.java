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
import java.util.Locale;
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
        int pavement = encoder.getPavementType(flags);
        int wayType = encoder.getWayType(flags);

        TranslationMap.Translation enMap = SINGLETON.getWithFallBack(Locale.UK);
        return InstructionList.getWayName(name, pavement, wayType, enMap);
    }

    @Test
    public void testAccess()
    {
        OSMWay way = new OSMWay(1);

        way.setTag("highway", "motorway");
        assertFalse(encoder.acceptWay(way) > 0);

        way.setTag("highway", "footway");
        assertTrue(encoder.acceptWay(way) > 0);

        way.setTag("bicycle", "no");
        assertFalse(encoder.acceptWay(way) > 0);

        way.setTag("highway", "footway");
        way.setTag("bicycle", "yes");
        assertTrue(encoder.acceptWay(way) > 0);

        way.setTag("highway", "pedestrian");
        way.setTag("bicycle", "no");
        assertFalse(encoder.acceptWay(way) > 0);

        way.setTag("highway", "pedestrian");
        way.setTag("bicycle", "yes");
        assertTrue(encoder.acceptWay(way) > 0);

        way.setTag("bicycle", "yes");
        way.setTag("highway", "cycleway");
        assertTrue(encoder.acceptWay(way) > 0);

        way.clearTags();
        way.setTag("highway", "path");
        assertTrue(encoder.acceptWay(way) > 0);

        way.setTag("highway", "path");
        way.setTag("bicycle", "yes");
        assertTrue(encoder.acceptWay(way) > 0);
        way.clearTags();

        way.setTag("highway", "track");
        way.setTag("bicycle", "yes");
        assertTrue(encoder.acceptWay(way) > 0);
        way.clearTags();

        way.setTag("highway", "track");
        assertTrue(encoder.acceptWay(way) > 0);

        way.setTag("mtb", "yes");
        assertTrue(encoder.acceptWay(way) > 0);

        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("foot", "official");
        assertTrue(encoder.acceptWay(way) > 0);

        way.setTag("bicycle", "official");
        assertTrue(encoder.acceptWay(way) > 0);

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "no");
        assertFalse(encoder.acceptWay(way) > 0);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("motorroad", "yes");
        assertFalse(encoder.acceptWay(way) > 0);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("ford", "yes");
        assertFalse(encoder.acceptWay(way) > 0);
        way.setTag("bicycle", "yes");
        assertTrue(encoder.acceptWay(way) > 0);

        way.clearTags();
        way.setTag("route", "ferry");
        assertTrue(encoder.acceptWay(way) > 0);
        way.setTag("bicycle", "no");
        assertFalse(encoder.acceptWay(way) > 0);

        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("foot", "yes");
        assertFalse(encoder.acceptWay(way) > 0);
        
        way.clearTags();
        way.setTag("highway", "cycleway");
        way.setTag("cycleway", "track");
        way.setTag("railway", "abandoned");
        assertTrue(encoder.acceptWay(way) > 0);
    }

    @Test
    public void testTramStations()
    {
        OSMWay way = new OSMWay(1);
        way.setTag("highway", "secondary");
        way.setTag("railway", "rail");
        // disallow rail
        assertEquals(0, encoder.acceptWay(way));

        way = new OSMWay(1);
        way.setTag("highway", "secondary");
        way.setTag("railway", "station");
        // disallow stations
        assertEquals(0, encoder.acceptWay(way));

        way = new OSMWay(1);
        way.setTag("highway", "secondary");
        way.setTag("railway", "station");
        way.setTag("bicycle", "yes");
        // allow stations if explicitely tagged
        assertNotSame(0, encoder.acceptWay(way));

        way = new OSMWay(1);
        way.setTag("highway", "secondary");
        way.setTag("railway", "station");
        way.setTag("bicycle", "no");
        // disallow
        assertEquals(0, encoder.acceptWay(way));
    }

    @Test
    public void testHandleCommonWayTags()
    {
        OSMWay way = new OSMWay(1);
        String wayType;

        way.setTag("highway", "steps");
        wayType = encodeDecodeWayType("", way);
        assertEquals("pushing section", wayType);

        way.setTag("highway", "steps");
        wayType = encodeDecodeWayType("Famous steps", way);
        assertEquals("Famous steps, pushing section", wayType);

        way.setTag("highway", "footway");
        wayType = encodeDecodeWayType("", way);
        assertEquals("pushing section", wayType);

        way.setTag("highway", "footway");
        way.setTag("surface", "pebblestone");
        wayType = encodeDecodeWayType("", way);
        assertEquals("pushing section", wayType);

        way.setTag("highway", "residential");
        wayType = encodeDecodeWayType("", way);
        assertEquals("", wayType);

        way.setTag("highway", "cycleway");
        wayType = encodeDecodeWayType("", way);
        assertEquals("cycleway", wayType);

        way.setTag("surface", "grass");
        wayType = encodeDecodeWayType("", way);
        assertEquals("cycleway, unpaved", wayType);

        way.setTag("surface", "asphalt");
        wayType = encodeDecodeWayType("", way);
        assertEquals("cycleway", wayType);

        way.setTag("highway", "footway");
        way.setTag("bicycle", "yes");
        way.setTag("surface", "grass");
        wayType = encodeDecodeWayType("", way);
        assertEquals("cycleway, unpaved", wayType);

        way.clearTags();
        way.setTag("highway", "footway");
        way.setTag("bicycle", "yes");
        way.setTag("surface", "grass");
        wayType = encodeDecodeWayType("", way);
        assertEquals("cycleway, unpaved", wayType);
    }

}
