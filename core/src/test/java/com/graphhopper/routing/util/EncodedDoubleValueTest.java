/*
 *  Licensed to Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  Peter Karich licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the
 *  License at
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
 *
 * @author Peter Karich
 */
public class EncodedDoubleValueTest
{
    @Test
    public void testSetDoubleValue()
    {
        EncodedDoubleValue instance = new EncodedDoubleValue("test", 6, 10, 0.01, 5, 10);
        assertEquals(10.12, instance.getDoubleValue(instance.setDoubleValue(0, 10.12)), 1e-4);
    }

    @Test
    public void testMaxValue()
    {
        EncodedDoubleValue instance1 = new EncodedDoubleValue("test1", 0, 8, 0.5, 60, 100);
        long flags = instance1.setDoubleValue(0, instance1.getMaxValue());
        assertEquals(100, instance1.getDoubleValue(flags), 1e-1);
    }

    @Test
    public void testMaxValueAndSwap()
    {
        EncodedDoubleValue instance1 = new EncodedDoubleValue("test1", 0, 8, 0.5, 60, 100);
        EncodedDoubleValue instance2 = new EncodedDoubleValue("test2", 8, 8, 0.5, 60, 100);
        long flags = instance2.setDoubleValue(instance1.setDoubleValue(0, 100), 90);
        long expectedFlags = instance2.setDoubleValue(instance1.setDoubleValue(0, 90), 100);
        long swappedFlags = instance1.swap(flags, instance2);
        assertEquals(expectedFlags, swappedFlags);

        CarFlagEncoder carEncoder = new CarFlagEncoder(8, 0.5, 0);
        new EncodingManager(carEncoder);
        OSMWay way = new OSMWay(1);
        way.setTag("highway", "motorway_link");
        way.setTag("maxspeed", "70 mph");
        flags = carEncoder.handleWayTags(way, 1, 0);

        // double speed = AbstractFlagEncoder.parseSpeed("70 mph");
        flags = carEncoder.reverseFlags(flags);
        assertEquals(100, carEncoder.getSpeed(flags), 1e-1);
    }
}
