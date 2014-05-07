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
import static com.graphhopper.routing.util.BikeCommonFlagEncoder.PriorityCode.*;
import com.graphhopper.util.Translation;
import static com.graphhopper.util.TranslationMapTest.SINGLETON;
import java.util.Locale;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Peter Karich
 * @author ratrun
 */
public abstract class AbstractBikeFlagEncoderTester
{
    protected BikeCommonFlagEncoder encoder;

    @Before
    public void setUp()
    {
        encoder = createBikeEncoder();
    }

    protected abstract BikeCommonFlagEncoder createBikeEncoder();

    protected void assertPriority( int expectedPrio, OSMWay way )
    {
        assertPriority(expectedPrio, way, 0);
    }

    protected void assertPriority( int expectedPrio, OSMWay way, long relationFlags )
    {
        assertEquals(expectedPrio, encoder.handlePriority(way, (int) encoder.relationCodeEncoder.getValue(relationFlags)));
    }

    protected double getSpeedFromFlags( OSMWay way )
    {
        long allowed = encoder.acceptBit;
        long flags = encoder.handleWayTags(way, allowed, 0);
        return encoder.getSpeed(flags);
    }

    protected String getWayTypeFromFlags( OSMWay way )
    {
        long allowed = encoder.acceptBit;
        long flags = encoder.handleWayTags(way, allowed, 0);
        Translation enMap = SINGLETON.getWithFallBack(Locale.UK);
        return encoder.getAnnotation(flags, enMap).getMessage();
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
    public void testAvoidTunnel()
    {
        OSMWay osmWay = new OSMWay(1);
        osmWay.setTag("highway", "residential");
        osmWay.setTag("tunnel", "yes");
        assertPriority(UNCHANGED.getValue(), osmWay);

        osmWay.setTag("highway", "secondary");
        osmWay.setTag("tunnel", "yes");
        assertPriority(AVOID_AT_ALL_COSTS.getValue(), osmWay);

        osmWay.setTag("bicycle", "designated");
        assertPriority(PREFER.getValue(), osmWay);
    }

    @Test
    public void testTram()
    {
        OSMWay way = new OSMWay(1);
        // very dangerous
        way.setTag("highway", "secondary");
        way.setTag("railway", "tram");
        assertPriority(AVOID_AT_ALL_COSTS.getValue(), way);

        // should be safe now
        way.setTag("bicycle", "designated");
        assertPriority(PREFER.getValue(), way);
    }

    @Test
    public void testHandleCommonWayTags()
    {
        OSMWay way = new OSMWay(1);
        String wayType;

        way.setTag("highway", "steps");
        wayType = getWayTypeFromFlags(way);
        assertEquals("pushing section", wayType);

        way.setTag("highway", "footway");
        wayType = getWayTypeFromFlags(way);
        assertEquals("pushing section", wayType);

        way.setTag("highway", "footway");
        way.setTag("surface", "pebblestone");
        wayType = getWayTypeFromFlags(way);
        assertEquals("pushing section", wayType);

        way.setTag("highway", "residential");
        wayType = getWayTypeFromFlags(way);
        assertEquals("", wayType);
        assertPriority(PREFER.getValue(), way);

        way.clearTags();
        way.setTag("highway", "cycleway");
        wayType = getWayTypeFromFlags(way);
        assertEquals("cycleway", wayType);
        assertPriority(VERY_NICE.getValue(), way);

        way.setTag("surface", "grass");
        wayType = getWayTypeFromFlags(way);
        assertEquals("cycleway, unpaved", wayType);

        way.setTag("surface", "asphalt");
        wayType = getWayTypeFromFlags(way);
        assertEquals("cycleway", wayType);
        assertPriority(VERY_NICE.getValue(), way);

        way.setTag("highway", "footway");
        way.setTag("bicycle", "yes");
        way.setTag("surface", "grass");
        wayType = getWayTypeFromFlags(way);
        assertEquals("cycleway, unpaved", wayType);

        way.clearTags();
        way.setTag("highway", "footway");
        way.setTag("bicycle", "yes");
        way.setTag("surface", "grass");
        wayType = getWayTypeFromFlags(way);
        assertEquals("cycleway, unpaved", wayType);
    }

    @Test
    public void testService()
    {
        OSMWay way = new OSMWay(1);
        way.setTag("highway", "service");
        assertEquals(14, encoder.getSpeed(way));
        assertPriority(PREFER.getValue(), way);

        way.setTag("service", "parking_aisle");
        assertEquals(6, encoder.getSpeed(way));
        assertPriority(AVOID_IF_POSSIBLE.getValue(), way);
    }

    @Test
    public void testReduceToMaxSpeed()
    {
        OSMWay way = new OSMWay(12);
        way.setTag("maxspeed", "90");
        assertEquals(12, encoder.reduceToMaxSpeed(way, 12), 1e-2);
    }

    @Test
    public void testMaxAndMinSpeed()
    {
        OSMWay osmWay = new OSMWay(1);
        osmWay.setTag("highway", "tertiary");
        assertEquals(30, encoder.getSpeed(encoder.setSpeed(0, encoder.reduceToMaxSpeed(osmWay, 49))), 1e-1);
        assertPriority(PREFER.getValue(), osmWay);

        osmWay.setTag("highway", "tertiary");
        osmWay.setTag("maxspeed", "90");
        assertEquals(20, encoder.getSpeed(encoder.setSpeed(0, encoder.reduceToMaxSpeed(osmWay, 20))), 1e-1);
        assertPriority(REACH_DEST.getValue(), osmWay);
    }

    @Test
    public void testHandleWayTagsCallsHandlePriority()
    {
        OSMWay osmWay = new OSMWay(1);
        osmWay.setTag("highway", "cycleway");
        long encoded = encoder.handleWayTags(osmWay, encoder.acceptBit, 0);
        assertEquals((double) VERY_NICE.getValue() / BEST.getValue(), encoder.getPriority(encoded), 1e-3);
    }
}
