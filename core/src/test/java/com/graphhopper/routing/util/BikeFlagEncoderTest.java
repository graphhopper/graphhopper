/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License,
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
public class BikeFlagEncoderTest {

    @Test
    public void testGetSpeed() {
        BikeFlagEncoder instance = (BikeFlagEncoder) new EncodingManager("CAR,BIKE").getEncoder("BIKE");
        int result = instance.flags(10, true);
        assertEquals(10, instance.getSpeed(result));
        OSMWay way = new OSMWay();
        way.setTag("highway", "primary");
        assertEquals(18, instance.getSpeed(way));

        way.setTag("surface", "paved");
        assertEquals(16, instance.getSpeed(way));
    }
}
