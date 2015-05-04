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
import static com.graphhopper.routing.util.PriorityCode.*;
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
    @Override
    public void testSacScale()
    {
        OSMWay way = new OSMWay(1);
        way.setTag("highway", "service");
        way.setTag("sac_scale", "mountain_hiking");
        // disallow
        assertEquals(0, encoder.acceptWay(way));
        
        way.setTag("highway", "path");
        way.setTag("sac_scale", "hiking");
        // disallow
        assertEquals(0, encoder.acceptWay(way));
        
        way.setTag("highway", "cycleway");
        way.setTag("sac_scale", "hiking");
        // but allow this as there is no reason for not allowing it
        assertTrue(encoder.acceptWay(way) > 0);

        // This looks to be tagging error:
        way.setTag("highway", "cycleway");
        way.setTag("sac_scale", "mountain_hiking");
        // we are coutious and disallow this
        assertEquals(0, encoder.acceptWay(way));
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
        assertEquals(PUSHING_SECTION_SPEED / 2, getSpeedFromFlags(osmWay), 1e-1);
        assertEquals("get off the bike, unpaved", getWayTypeFromFlags(osmWay, 0));

        // relation code is PREFER
        long allowed = encoder.acceptBit;
        OSMRelation osmRel = new OSMRelation(1);
        osmRel.setTag("route", "bicycle");
        osmRel.setTag("network", "lcn");
        long relFlags = encoder.handleRelationTags(osmRel, 0);
        long flags = encoder.handleWayTags(osmWay, allowed, relFlags);
        assertEquals(2, encoder.getSpeed(flags), 1e-1);
        assertPriority(AVOID_AT_ALL_COSTS.getValue(), osmWay, relFlags);
        assertEquals("way, unpaved", getWayTypeFromFlags(osmWay, relFlags));

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
        assertEquals("", getWayTypeFromFlags(osmWay, relFlags));

        // Now we assume bicycle=yes, and unpaved as part of a cycle relation
        osmWay.setTag("tracktype", "grade2");
        osmWay.setTag("bicycle", "yes");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        flags = encoder.handleWayTags(osmWay, allowed, relFlags);
        assertEquals(10, encoder.getSpeed(flags), 1e-1);
        assertPriority(AVOID_AT_ALL_COSTS.getValue(), osmWay, relFlags);
        assertEquals("way, unpaved", getWayTypeFromFlags(osmWay, relFlags));

        // Now we assume bicycle=yes, and unpaved not part of a cycle relation
        osmRel.clearTags();
        osmWay.clearTags();
        osmWay.setTag("highway", "track");
        osmWay.setTag("tracktype", "grade3");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        flags = encoder.handleWayTags(osmWay, allowed, relFlags);
        assertEquals(4, encoder.getSpeed(flags), 1e-1);
        assertPriority(AVOID_AT_ALL_COSTS.getValue(), osmWay, relFlags);
        assertEquals("get off the bike, unpaved", getWayTypeFromFlags(osmWay, relFlags));

        // Now we assume bicycle=yes, and tracktype = null
        osmRel.clearTags();
        osmWay.clearTags();
        osmWay.setTag("highway", "track");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        flags = encoder.handleWayTags(osmWay, allowed, relFlags);
        assertEquals(2, encoder.getSpeed(flags), 1e-1);
        assertPriority(AVOID_AT_ALL_COSTS.getValue(), osmWay, relFlags);
        assertEquals("get off the bike, unpaved", getWayTypeFromFlags(osmWay, relFlags));
    }
}
