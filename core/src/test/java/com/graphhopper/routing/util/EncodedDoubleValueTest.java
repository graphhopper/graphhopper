/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper GmbH licenses this file to you under the Apache License, 
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

import com.graphhopper.reader.ReaderWay;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public class EncodedDoubleValueTest {
    @Test
    public void testSetDoubleValue() {
        EncodedDoubleValue instance = new EncodedDoubleValue("test", 6, 10, 0.01, 5, 10);
        assertEquals(10.12, instance.getDoubleValue(instance.setDoubleValue(0, 10.12)), 1e-4);
    }

    @Test(expected = IllegalStateException.class)
    public void testIllegalFactorMaxValueCombination() {
        new EncodedDoubleValue("illegalcombination", 6, 2, 2, 0, 3);
    }

    @Test
    public void testMaxValue() {
        EncodedDoubleValue instance1 = new EncodedDoubleValue("test1", 0, 8, 0.5, 60, 100);
        long flags = instance1.setDoubleValue(0, instance1.getMaxValue());
        assertEquals(100, instance1.getDoubleValue(flags), 1e-1);

        CarFlagEncoder carEncoder = new CarFlagEncoder(10, 0.5, 0);
        new EncodingManager(carEncoder);
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "motorway_link");
        way.setTag("maxspeed", "70 mph");
        flags = carEncoder.handleWayTags(way, 1, 0);

        // double speed = AbstractFlagEncoder.parseSpeed("70 mph");
        flags = carEncoder.reverseFlags(flags);
        assertEquals(101.5, carEncoder.getSpeed(flags), 1e-1);
    }

    @Test
    public void testUnsignedRightShift_issue417() {
        EncodedDoubleValue speedEncoder = new EncodedDoubleValue("Speed", 56, 8, 1, 30, 255);
        Long flags = -72057594037927936L;
        assertEquals(255, speedEncoder.getDoubleValue(flags), 0.01);
    }
}
