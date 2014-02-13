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
import java.util.HashMap;
import java.util.Map;
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
        return (BikeFlagCommonEncoder) new EncodingManager("BIKE,MTB,RACINGBIKE").getEncoder("RACINGBIKE");
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
        assertEquals(PUSHING_SECTION_SPEED / 2, getEncodedDecodedSpeed(way), 1e-1);

        // Even if it is part of a cycle way PUSHING_SECTION_SPEED/2
        way.setTag("bicycle", "yes");
        assertEquals(PUSHING_SECTION_SPEED / 2, getEncodedDecodedSpeed(way), 1e-1);

        way.clearTags();
        way.setTag("highway", "steps");
        assertEquals(2, getEncodedDecodedSpeed(way), 1e-1);

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

        assertEquals(PUSHING_SECTION_SPEED / 2, getEncodedDecodedSpeed(osmWay), 1e-1);

        // relation code is PREFER
        relMap.put("route", "bicycle");
        relMap.put("network", "lcn");
        long relFlags = encoder.handleRelationTags(osmRel, 0);
        long flags = encoder.handleWayTags(osmWay, allowed, relFlags);
        assertEquals(2, encoder.getSpeed(flags), 1e-1);
        assertEquals(1, encoder.getWayTypeCode(flags)); // Pushing section
        assertEquals(1, encoder.getPavementCode(flags)); //  Unpaved

        // relation code is OUTSTANDING NICE but as unpaved, the speed is still PUSHING_SECTION_SPEED/2
        relMap.put("network", "icn");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        flags = encoder.handleWayTags(osmWay, allowed, relFlags);
        assertEquals(2, encoder.getSpeed(flags), 1e-1);

        // Now we assume bicycle=yes, anyhow still unpaved
        wayMap.put("bicycle", "yes");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        flags = encoder.handleWayTags(osmWay, allowed, relFlags);
        assertEquals(2, encoder.getSpeed(flags), 1e-1);

        // Now we assume bicycle=yes, and paved -> The speed is pushed!
        wayMap.put("tracktype", "grade1");
        relFlags = encoder.handleRelationTags(osmRel, 0);
        flags = encoder.handleWayTags(osmWay, allowed, relFlags);
        assertEquals(30, encoder.getSpeed(flags), 1e-1);
    }
}
