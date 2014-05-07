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
import static com.graphhopper.routing.util.BikeCommonFlagEncoder.PUSHING_SECTION_SPEED;
import static com.graphhopper.routing.util.BikeCommonFlagEncoder.PriorityCode.*;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author ratrun
 */
public class RacingBikeFlagEncoderTest extends AbstractBikeFlagEncoderTester
{
    @Override
    protected BikeCommonFlagEncoder createBikeEncoder()
    {
        return (BikeCommonFlagEncoder) new EncodingManager("BIKE,RACINGBIKE").getEncoder("RACINGBIKE");
    }

    @Test
    @Override
    public void testAvoidTunnel()
    {
        // tunnel is not that bad for racing bike
        OSMWay osmWay = new OSMWay(1);
        osmWay.setTag("highway", "residential");
        osmWay.setTag("tunnel", "yes");
        assertPriority(UNCHANGED.getValue(), osmWay);

        osmWay.setTag("highway", "secondary");
        osmWay.setTag("tunnel", "yes");
        assertPriority(UNCHANGED.getValue(), osmWay);

        osmWay.setTag("bicycle", "designated");
        assertPriority(PREFER.getValue(), osmWay);
    }

    @Test
    @Override
    public void testService()
    {
        OSMWay way = new OSMWay(1);
        way.setTag("highway", "service");
        assertEquals(12, encoder.getSpeed(way));
        assertPriority(UNCHANGED.getValue(), way);

        way.setTag("service", "parking_aisle");
        assertEquals(6, encoder.getSpeed(way));
        assertPriority(AVOID_IF_POSSIBLE.getValue(), way);
    }

    @Test
    public void testGetSpeed()
    {
        long result = encoder.setProperties(10, true, true);
        assertEquals(10, encoder.getSpeed(result), 1e-1);
        OSMWay way = new OSMWay(1);
        way.setTag("highway", "track");
        way.setTag("tracktype", "grade3");
        // Pushing section
        assertEquals(PUSHING_SECTION_SPEED, getSpeedFromFlags(way), 1e-1);

        // Even if it is part of a cycle way
        way.setTag("bicycle", "yes");
        assertEquals(PUSHING_SECTION_SPEED, getSpeedFromFlags(way), 1e-1);

        way.clearTags();
        way.setTag("highway", "steps");
        assertEquals(2, getSpeedFromFlags(way), 1e-1);
    }

    @Test
    public void testHandleWayTagsInfluencedByRelation()
    {
        OSMWay osmWay = new OSMWay(1);
        osmWay.setTag("highway", "track");
        long allowed = encoder.acceptBit;

        OSMRelation osmRel = new OSMRelation(1);

        assertEquals(PUSHING_SECTION_SPEED / 2, getSpeedFromFlags(osmWay), 1e-1);

        // relation code is PREFER
        osmRel.setTag("route", "bicycle");
        osmRel.setTag("network", "lcn");
        long relFlags = encoder.handleRelationTags(osmRel, 0);
        long flags = encoder.handleWayTags(osmWay, allowed, relFlags);
        assertEquals(2, encoder.getSpeed(flags), 1e-1);
        assertPriority(AVOID_AT_ALL_COSTS.getValue(), osmWay, relFlags);
        // TODO unpaved if tracktype == null?
        assertEquals("pushing section", getWayTypeFromFlags(osmWay));

        // relation code is OUTSTANDING NICE but as unpaved, the speed is still PUSHING_SECTION_SPEED/2
        osmRel.setTag("network", "icn");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        flags = encoder.handleWayTags(osmWay, allowed, relFlags);
        assertEquals(2, encoder.getSpeed(flags), 1e-1);
        assertPriority(AVOID_AT_ALL_COSTS.getValue(), osmWay, relFlags);

        // Now we assume bicycle=yes, anyhow still unpaved
        osmWay.setTag("bicycle", "yes");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        flags = encoder.handleWayTags(osmWay, allowed, relFlags);
        assertEquals(2, encoder.getSpeed(flags), 1e-1);
        assertPriority(AVOID_AT_ALL_COSTS.getValue(), osmWay, relFlags);

        // Now we assume bicycle=yes, and paved
        osmWay.setTag("tracktype", "grade1");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        flags = encoder.handleWayTags(osmWay, allowed, relFlags);
        assertEquals(20, encoder.getSpeed(flags), 1e-1);
        assertPriority(PREFER.getValue(), osmWay, relFlags);
    }
}
