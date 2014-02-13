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
import java.util.Collections;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class BikeFlagEncoderTest extends AbstractBikeFlagEncoderTester
{
    @Override
    BikeFlagCommonEncoder createBikeEncoder()
    {
        return (BikeFlagCommonEncoder) new EncodingManager("BIKE,MTB,RACINGBIKE").getEncoder("BIKE");
    }

    @Test
    public void testGetSpeed()
    {
        long result = encoder.setProperties(10, true, true);
        assertEquals(10, encoder.getSpeed(result), 1e-1);
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
        
        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("surface", "ground");
        assertEquals(4, encoder.getSpeed(way));
        
        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("bicycle", "yes");
        way.setTag("surface", "fine_gravel");
        assertEquals(18, encoder.getSpeed(way));
        
        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("bicycle", "yes");
        way.setTag("surface", "unknown_surface");
        assertEquals(4, encoder.getSpeed(way));
        
    }

    @Test
    public void testHandleWayTags()
    {
        Map<String, String> wayMap = new HashMap<String, String>();
        OSMWay way = new OSMWay(1, wayMap);
        String wayType;
        
        wayMap.put("highway", "track");
        wayType = encodeDecodeWayType("", way);
        assertEquals("pushing section, unpaved", wayType);
        
        wayMap.clear();
        wayMap.put("highway", "path");
        wayType = encodeDecodeWayType("", way);
        assertEquals("pushing section, unpaved", wayType);

        wayMap.clear();
        wayMap.put("highway", "path");
        wayMap.put("surface", "grass");
        wayType = encodeDecodeWayType("", way);
        assertEquals("pushing section, unpaved", wayType);

        wayMap.clear();
        wayMap.put("highway", "path");
        wayMap.put("surface", "concrete");
        wayType = encodeDecodeWayType("", way);
        assertEquals("pushing section", wayType);

        wayMap.clear();
        wayMap.put("highway", "track");
        wayMap.put("foot", "yes");
        wayMap.put("surface", "paved");
        wayMap.put("tracktype", "grade1");
        wayType = encodeDecodeWayType("", way);
        assertEquals("pushing section", wayType);

        wayMap.clear();
        wayMap.put("highway", "track");
        wayMap.put("foot", "yes");
        wayMap.put("surface", "paved");
        wayMap.put("tracktype", "grade2");
        wayType = encodeDecodeWayType("", way);
        assertEquals("pushing section, unpaved", wayType);
        
    }

    @Test
    public void testHandleWayTagsInfluencedByRelation()
    {
        Map<String, String> wayMap = new HashMap<String, String>();
        OSMWay osmWay = new OSMWay(1, wayMap);
        wayMap.put("highway", "track");
        long allowed=encoder.acceptBit;

        Map<String, String> relMap = new HashMap<String, String>();
        OSMRelation osmRel = new OSMRelation(1, relMap);

        long relFlags = encoder.handleRelationTags(osmRel, 0);
        // unchanged
        long flags = encoder.handleWayTags(osmWay, allowed, relFlags);
        assertEquals(16, encoder.getSpeed(flags), 1e-1);
        assertEquals(1, encoder.getWayTypeCode(flags));
        assertEquals(1, encoder.getPavementCode(flags));

        // relation code is PREFER
        relMap.put("route", "bicycle");
        relMap.put("network", "lcn");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        flags = encoder.handleWayTags(osmWay, allowed, relFlags);
        assertEquals(20, encoder.getSpeed(flags), 1e-1);
        assertEquals(1, encoder.getWayTypeCode(flags));
        assertEquals(1, encoder.getPavementCode(flags));

        // relation code is VERY_NICE
        relMap.put("network", "rcn");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        flags = encoder.handleWayTags(osmWay, allowed, relFlags);
        assertEquals(24, encoder.getSpeed(flags), 1e-1);

        // relation code is OUTSTANDING_NICE
        relMap.put("network", "ncn");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        flags = encoder.handleWayTags(osmWay, allowed, relFlags);
        assertEquals(28, encoder.getSpeed(flags), 1e-1);

        // PREFER relation, but tertiary road
        // => no pushing section but road wayTypeCode and faster
        wayMap.clear();
        wayMap.put("highway", "tertiary");

        relMap.put("route", "bicycle");
        relMap.put("network", "lcn");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        flags = encoder.handleWayTags(osmWay, allowed, relFlags);
        assertEquals(22, encoder.getSpeed(flags), 1e-1);
        assertEquals(0, encoder.getWayTypeCode(flags));
        
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
        new EncodingManager(fakeEncoder);
        allowed = fakeEncoder.acceptBit;

        flags = fakeEncoder.handleWayTags(osmWay, allowed, 1);
        assertEquals(30, fakeEncoder.getSpeed(flags), 1e-1);

        fakeSpeed.set(-2);
        flags = fakeEncoder.handleWayTags(osmWay, allowed, 1);
        assertEquals(0, fakeEncoder.getSpeed(flags), 1e-1);
        
    }
    
    @Test
    public void testTurnFlagEncoding_noCosts() {
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
    public void testTurnFlagEncoding_withCosts() {
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
        
        assertEquals(127, encoder.getTurnCosts(flags_r220));
        assertEquals(127, encoder.getTurnCosts(flags_220));
        
        assertTrue(encoder.isTurnRestricted(flags_r0));
        assertFalse(encoder.isTurnRestricted(flags_0));
        
        assertTrue(encoder.isTurnRestricted(flags_r20));
        assertFalse(encoder.isTurnRestricted(flags_20));
        
        assertTrue(encoder.isTurnRestricted(flags_r220));
        assertFalse(encoder.isTurnRestricted(flags_220));
    }
}
