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
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class MotorcycleFlagEncoderTest
{
    private final EncodingManager em = new EncodingManager("motorcycle,foot");
    private final MotorcycleFlagEncoder encoder = (MotorcycleFlagEncoder) em.getEncoder("motorcycle");

    @Test
    public void testHandleWayTags()
    {
        OSMWay way = new OSMWay(1);
        way.setTag("highway", "service");
        long flags = encoder.acceptWay(way);
        assertTrue(flags > 0);
        long result = encoder.handleWayTags(way, flags, 0);
        assertEquals(20, encoder.getSpeed(result), .1);
        assertEquals(20, encoder.getReverseSpeed(result), .1);
    }

    @Test
    public void testSetSpeed0_issue367()
    {
        long flags = encoder.setProperties(10, true, true);
        flags = encoder.setSpeed(flags, 0);

        assertEquals(0, encoder.getSpeed(flags), .1);
        assertEquals(10, encoder.getReverseSpeed(flags), .1);
        assertFalse(encoder.isForward(flags));
        assertTrue(encoder.isBackward(flags));
    }
}
