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

import static com.graphhopper.routing.util.PriorityCode.*;

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
        return getWayTypeFromFlags(way, 0);
    }

    protected String getWayTypeFromFlags( OSMWay way, long relationFlags )
    {
        long allowed = encoder.acceptBit;
        long flags = encoder.handleWayTags(way, allowed, relationFlags);
        Translation enMap = SINGLETON.getWithFallBack(Locale.UK);
        return encoder.getAnnotation(flags, enMap).getMessage();
    }

    @Test
    public void testAccess()
    {
        OSMWay way = new OSMWay(1);

        way.setTag("highway", "motorway");
        assertFalse(encoder.acceptWay(way) > 0);

        way.setTag("highway", "motorway");
        way.setTag("bicycle", "yes");
        assertTrue(encoder.acceptWay(way) > 0);

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
        assertNotEquals(0, encoder.acceptWay(way));

        way = new OSMWay(1);
        way.setTag("highway", "secondary");
        way.setTag("railway", "station");
        way.setTag("bicycle", "no");
        // disallow
        assertEquals(0, encoder.acceptWay(way));

        way = new OSMWay(1);
        way.setTag("railway", "platform");
        long flags = encoder.handleWayTags(way, encoder.acceptWay(way), 0);
        assertNotEquals(0, flags);

        way = new OSMWay(1);
        way.setTag("highway", "track");
        way.setTag("railway", "platform");
        flags = encoder.handleWayTags(way, encoder.acceptWay(way), 0);
        assertNotEquals(0, flags);

        way = new OSMWay(1);
        way.setTag("highway", "track");
        way.setTag("railway", "platform");
        way.setTag("bicycle", "no");
        flags = encoder.handleWayTags(way, encoder.acceptWay(way), 0);
        assertEquals(0, flags);
    }

    @Test
    public void testAvoidTunnel()
    {
        OSMWay osmWay = new OSMWay(1);
        osmWay.setTag("highway", "residential");
        assertPriority(PREFER.getValue(), osmWay);

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
        assertEquals("get off the bike", wayType);

        way.setTag("highway", "footway");
        wayType = getWayTypeFromFlags(way);
        assertEquals("get off the bike", wayType);

        way.setTag("highway", "footway");
        way.setTag("surface", "pebblestone");
        wayType = getWayTypeFromFlags(way);
        assertEquals("get off the bike", wayType);

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
        assertEquals("way, unpaved", wayType);

        way.setTag("bicycle", "designated");
        wayType = getWayTypeFromFlags(way);
        assertEquals("cycleway, unpaved", wayType);

        way.clearTags();
        way.setTag("highway", "footway");
        way.setTag("bicycle", "yes");
        way.setTag("surface", "grass");
        wayType = getWayTypeFromFlags(way);
        assertEquals("way, unpaved", wayType);

        way.clearTags();
        way.setTag("railway", "platform");
        wayType = getWayTypeFromFlags(way);
        assertEquals("get off the bike", wayType);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("railway", "platform");
        wayType = getWayTypeFromFlags(way);
        assertEquals("get off the bike, unpaved", wayType);
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
    public void testSacScale()
    {
        OSMWay way = new OSMWay(1);
        way.setTag("highway", "service");
        way.setTag("sac_scale", "hiking");
        // allow
        assertTrue(encoder.acceptWay(way) > 0);

        way.setTag("sac_scale", "alpine_hiking");
        assertTrue(encoder.acceptWay(way) == 0);
    }

    @Test
    public void testReduceToMaxSpeed()
    {
        OSMWay way = new OSMWay(12);
        way.setTag("maxspeed", "90");
        assertEquals(12, encoder.applyMaxSpeed(way, 12, false), 1e-2);
    }

    @Test
    public void testPreferenceForSlowSpeed()
    {
        OSMWay osmWay = new OSMWay(1);
        osmWay.setTag("highway", "tertiary");
        assertEquals(30, encoder.getSpeed(encoder.setSpeed(0, encoder.applyMaxSpeed(osmWay, 49, false))), 1e-1);
        assertPriority(PREFER.getValue(), osmWay);
    }

    @Test
    public void testHandleWayTagsCallsHandlePriority()
    {
        OSMWay osmWay = new OSMWay(1);
        osmWay.setTag("highway", "cycleway");
        long encoded = encoder.handleWayTags(osmWay, encoder.acceptBit, 0);
        assertEquals((double) VERY_NICE.getValue() / BEST.getValue(), encoder.getDouble(encoded, PriorityWeighting.KEY), 1e-3);
    }

    @Test
    public void testAvoidMotorway()
    {
        OSMWay osmWay = new OSMWay(1);
        osmWay.setTag("highway", "motorway");
        osmWay.setTag("bicycle", "yes");
        assertPriority(REACH_DEST.getValue(), osmWay);
    }

    @Test
    public void testPriority()
    {
        long flags = encoder.setLong(0L, PriorityWeighting.KEY, PriorityCode.BEST.getValue());
        assertEquals(1, encoder.getDouble(flags, PriorityWeighting.KEY), 1e-3);

        flags = encoder.setLong(0L, PriorityWeighting.KEY, PriorityCode.AVOID_IF_POSSIBLE.getValue());
        assertEquals(3d / 7d, encoder.getDouble(flags, PriorityWeighting.KEY), 1e-3);
    }

    @Test
    public void testBarrierAccess()
    {
        // by default allow access through the gate for bike & foot!
        OSMNode node = new OSMNode(1, -1, -1);
        node.setTag("barrier", "gate");
        // no barrier!
        assertTrue(encoder.handleNodeTags(node) == 0);

        node.setTag("bicycle", "yes");
        // no barrier!
        assertTrue(encoder.handleNodeTags(node) == 0);

        node = new OSMNode(1, -1, -1);
        node.setTag("barrier", "gate");
        node.setTag("access", "no");
        // barrier!
        assertTrue(encoder.handleNodeTags(node) > 0);

        node = new OSMNode(1, -1, -1);
        node.setTag("barrier", "gate");
        node.setTag("access", "yes");
        node.setTag("bicycle", "no");
        // barrier!
        assertTrue(encoder.handleNodeTags(node) > 0);

        node = new OSMNode(1, -1, -1);
        node.setTag("barrier", "gate");
        node.setTag("access", "no");
        node.setTag("foot", "yes");
        // barrier!
        assertTrue(encoder.handleNodeTags(node) > 0);

        node = new OSMNode(1, -1, -1);
        node.setTag("barrier", "gate");
        node.setTag("access", "no");
        node.setTag("bicycle", "yes");
        // no barrier!
        assertTrue(encoder.handleNodeTags(node) == 0);
    }
}
