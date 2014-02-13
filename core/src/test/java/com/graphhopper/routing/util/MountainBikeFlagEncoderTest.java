/*
 * Copyright 2013 User.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.graphhopper.routing.util;

import com.graphhopper.reader.OSMRelation;
import com.graphhopper.reader.OSMWay;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class MountainBikeFlagEncoderTest extends AbstractBikeFlagEncoderTester
{
    @Override
    BikeFlagCommonEncoder createBikeEncoder()
    {
        return (BikeFlagCommonEncoder) new EncodingManager("BIKE,MTB").getEncoder("MTB");
    }

    @Test
    public void testGetSpeed()
    {
        long result = encoder.setProperties(10, true, true);
        assertEquals(10, encoder.getSpeed(result), 1e-1);
        OSMWay way = new OSMWay(1);
        way.setTag("highway", "primary");
        assertEquals(10, encoder.getSpeed(way));

        way.setTag("highway", "residential");
        assertEquals(15, encoder.getSpeed(way));
        // Test pushing section speeds
        way.setTag("highway", "footway");
        assertEquals(4, encoder.getSpeed(way));
        way.setTag("highway", "track");
        assertEquals(24, encoder.getSpeed(way));

        way.setTag("highway", "steps");
        assertEquals(2, encoder.getSpeed(way));

        way.setTag("highway", "service");
        assertEquals(15, encoder.getSpeed(way));
        way.setTag("service", "parking_aisle");
        assertEquals(15, encoder.getSpeed(way));
        way.clearTags();

        // test speed for allowed pushing section types
        way.setTag("highway", "track");
        way.setTag("bicycle", "yes");
        assertEquals(24, encoder.getSpeed(way));

        way.setTag("highway", "track");
        way.setTag("bicycle", "yes");
        way.setTag("tracktype", "grade3");
        assertEquals(20, encoder.getSpeed(way));

        way.setTag("surface", "paved");
        assertEquals(12, encoder.getSpeed(way));

        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("surface", "ground");
        assertEquals(20, encoder.getSpeed(way));
    }

    @Test
    public void testHandleWayTags()
    {
        Map<String, String> wayMap = new HashMap<String, String>();
        OSMWay way = new OSMWay(1, wayMap);
        String wayType;

        wayMap.put("highway", "track");
        wayType = encodeDecodeWayType("", way);
        assertEquals("way, unpaved", wayType);

        wayMap.clear();
        wayMap.put("highway", "path");
        wayType = encodeDecodeWayType("", way);
        assertEquals("way, unpaved", wayType);

        wayMap.clear();
        wayMap.put("highway", "path");
        wayMap.put("surface", "grass");
        wayType = encodeDecodeWayType("", way);
        assertEquals("way, unpaved", wayType);

        wayMap.clear();
        wayMap.put("highway", "path");
        wayMap.put("surface", "concrete");
        wayType = encodeDecodeWayType("", way);
        assertEquals("way", wayType);

        wayMap.clear();
        wayMap.put("highway", "track");
        wayMap.put("foot", "yes");
        wayMap.put("surface", "paved");
        wayMap.put("tracktype", "grade1");
        wayType = encodeDecodeWayType("", way);
        assertEquals("way", wayType);

        wayMap.clear();
        wayMap.put("highway", "track");
        wayMap.put("foot", "yes");
        wayMap.put("surface", "paved");
        wayMap.put("tracktype", "grade2");
        wayType = encodeDecodeWayType("", way);
        assertEquals("way, unpaved", wayType);

    }

    @Test
    public void testHandleWayTagsInfluencedByRelation()
    {
        Map<String, String> wayMap = new HashMap<String, String>();
        OSMWay osmWay = new OSMWay(1, wayMap);
        wayMap.put("highway", "track");
        long allowed = encoder.acceptBit;

        Map<String, String> relMap = new HashMap<String, String>();
        OSMRelation osmRel = new OSMRelation(1, relMap);

        long relFlags = encoder.handleRelationTags(osmRel, 0);
        // unchanged
        long flags = encoder.handleWayTags(osmWay, allowed, relFlags);
        assertEquals(24, encoder.getSpeed(flags), 1e-1);
        assertEquals(3, encoder.getWayTypeCode(flags));
        assertEquals(1, encoder.getPavementCode(flags));

        // relation code is PREFER
        relMap.put("route", "bicycle");
        relMap.put("network", "lcn");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        flags = encoder.handleWayTags(osmWay, allowed, relFlags);
        assertEquals(28, encoder.getSpeed(flags), 1e-1);
        assertEquals(3, encoder.getWayTypeCode(flags));
        assertEquals(1, encoder.getPavementCode(flags));

        // relation code is PREFER
        relMap.put("network", "rcn");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        flags = encoder.handleWayTags(osmWay, allowed, relFlags);
        assertEquals(28, encoder.getSpeed(flags), 1e-1);

        // relation code is PREFER
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
        assertEquals(20, encoder.getSpeed(flags), 1e-1);
        assertEquals(0, encoder.getWayTypeCode(flags));

        // test max and min speed
        final AtomicInteger fakeSpeed = new AtomicInteger(40);
        MountainBikeFlagEncoder fakeEncoder = new MountainBikeFlagEncoder()
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
}
