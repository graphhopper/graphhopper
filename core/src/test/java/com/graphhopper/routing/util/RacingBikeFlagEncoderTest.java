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
import static com.graphhopper.routing.util.BikeFlagCommonEncoder.PUSHING_SECTION_SPEED;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author ratrun
 */
public class RacingBikeFlagEncoderTest extends AbstractBikeFlagEncoderTester
{
    @Override
    BikeFlagCommonEncoder createBikeEncoder()
    {
        return (BikeFlagCommonEncoder) new EncodingManager("BIKE,RACINGBIKE").getEncoder("RACINGBIKE");
    }

    @Test
    public void testGetSpeed()
    {
        long result = encoder.setProperties(10, true, true);
        assertEquals(10, encoder.getSpeed(result), 1e-1);
        OSMWay way = new OSMWay(1);
        way.setTag("highway", "track");
        way.setTag("tracktype", "grade3");
        // Pushing section speed/2
        assertEquals(PUSHING_SECTION_SPEED / 2, getSpeedFromFlags(way), 1e-1);

        // Even if it is part of a cycle way PUSHING_SECTION_SPEED/2
        way.setTag("bicycle", "yes");
        assertEquals(PUSHING_SECTION_SPEED / 2, getSpeedFromFlags(way), 1e-1);

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
        assertEquals("pushing section, unpaved", getWayTypeFromFlags(osmWay));        

        // relation code is OUTSTANDING NICE but as unpaved, the speed is still PUSHING_SECTION_SPEED/2
        osmRel.setTag("network", "icn");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        flags = encoder.handleWayTags(osmWay, allowed, relFlags);
        assertEquals(2, encoder.getSpeed(flags), 1e-1);

        // Now we assume bicycle=yes, anyhow still unpaved
        osmWay.setTag("bicycle", "yes");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        flags = encoder.handleWayTags(osmWay, allowed, relFlags);
        assertEquals(2, encoder.getSpeed(flags), 1e-1);

        // Now we assume bicycle=yes, and paved -> The speed is pushed!
        osmWay.setTag("tracktype", "grade1");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        flags = encoder.handleWayTags(osmWay, allowed, relFlags);
        assertEquals(30, encoder.getSpeed(flags), 1e-1);
    }
}
