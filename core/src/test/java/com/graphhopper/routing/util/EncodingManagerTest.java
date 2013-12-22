/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
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

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class EncodingManagerTest
{
    @Test
    public void testCompatibility()
    {
        EncodingManager manager = new EncodingManager("CAR,BIKE,FOOT");
        BikeFlagEncoder bike = (BikeFlagEncoder) manager.getEncoder("BIKE");
        CarFlagEncoder car = (CarFlagEncoder) manager.getEncoder("CAR");
        FootFlagEncoder foot = (FootFlagEncoder) manager.getEncoder("FOOT");
        assertNotEquals(car, bike);
        assertNotEquals(car, foot);
        assertNotEquals(car.hashCode(), bike.hashCode());
        assertNotEquals(car.hashCode(), foot.hashCode());

        EncodingManager manager2 = new EncodingManager();
        FootFlagEncoder foot2 = new FootFlagEncoder()
        {
        };
        manager2.registerEdgeFlagEncoder(foot2);
        assertNotEquals(foot, foot2);
        assertNotEquals(foot.hashCode(), foot2.hashCode());

        EncodingManager manager3 = new EncodingManager();
        FootFlagEncoder foot3 = new FootFlagEncoder()
        {
        };
        manager3.registerEdgeFlagEncoder(foot3);
        assertEquals(foot3, foot2);
        assertEquals(foot3.hashCode(), foot2.hashCode());

    }

    @Test
    public void testEncoderAcceptNoException()
    {
        EncodingManager manager = new EncodingManager("CAR");
        assertTrue(manager.supports("CAR"));
        assertFalse(manager.supports("FOOT"));
    }

    @Test
    public void testTooManyEncoders()
    {
        EncodingManager manager = new EncodingManager();
        for (int i = 0; i < 4; i++)
        {
            manager.registerEdgeFlagEncoder(new FootFlagEncoder()
            {
            });
        }
        try
        {
            manager.registerEdgeFlagEncoder(new FootFlagEncoder()
            {
            });
            assertTrue(false);
        } catch (Exception ex)
        {
        }
    }
}
