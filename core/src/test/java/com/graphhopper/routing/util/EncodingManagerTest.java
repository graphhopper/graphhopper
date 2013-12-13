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

import com.graphhopper.reader.OSMNode;
import com.graphhopper.reader.OSMWay;
import com.graphhopper.util.BitUtil;
import java.util.HashMap;
import java.util.Map;
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
        FootFlagEncoder foot2 = new FootFlagEncoder();
        manager2.register(foot2);
        assertNotEquals(foot, foot2);
        assertNotEquals(foot.hashCode(), foot2.hashCode());

        EncodingManager manager3 = new EncodingManager();
        FootFlagEncoder foot3 = new FootFlagEncoder();
        manager3.register(foot3);
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
        for (int i = 0; i < 9; i++)
        {
            manager.register(new FootFlagEncoder());
        }
        try
        {
            manager.register(new FootFlagEncoder());
            assertTrue(false);
        } catch (Exception ex)
        {
        }
    }

    @Test
    public void testFullBitMask()
    {
        BitUtil bitUtil = BitUtil.LITTLE;
        EncodingManager manager = new EncodingManager("CAR,FOOT");
        AbstractFlagEncoder carr = (AbstractFlagEncoder) manager.getEncoder("CAR");
        assertTrue(bitUtil.toBitString(carr.getBitMask()).endsWith("00000000001111111"));

        AbstractFlagEncoder foot = (AbstractFlagEncoder) manager.getEncoder("FOOT");
        assertTrue(bitUtil.toBitString(foot.getBitMask()).endsWith("00011111110000000"));
    }

    @Test
    public void testApplyNodeTags()
    {
        EncodingManager manager = new EncodingManager();
        CarFlagEncoder car = new CarFlagEncoder();
        manager.register(car);
        CarFlagEncoder car2 = new CarFlagEncoder(7, 1)
        {
            protected EncodedValue nodeEncoder;

            @Override
            public int defineBits( int index, int shift )
            {
                shift = super.defineBits(index, shift);
                nodeEncoder = new EncodedValue("nodeEnc", shift, 2, 1, 0, 3);
                return shift + 2;
            }

            @Override
            public long analyzeNodeTags( OSMNode node )
            {
                String tmp = node.getTags().get("test");
                if (tmp == null)
                    return -nodeEncoder.setValue(0, 1);
                return -nodeEncoder.setValue(0, 2);
            }

            @Override
            public long applyNodeFlags( long wayFlags, long nodeFlags )
            {
                int speed = (int) speedEncoder.getValue(wayFlags);
                int speedDecrease = (int) nodeEncoder.getValue(nodeFlags);
                return speedEncoder.setValue(wayFlags, speed - speedDecrease);
            }
        };
        manager.register(car2);

        Map<String, String> nodeMap = new HashMap<String, String>();
        OSMNode node = new OSMNode(1, nodeMap, Double.NaN, Double.NaN);
        Map<String, String> wayMap = new HashMap<String, String>();
        wayMap.put("highway", "secondary");
        OSMWay way = new OSMWay(2, wayMap);

        long wayFlags = manager.handleWayTags(manager.acceptWay(way), way);
        long nodeFlags = manager.analyzeNode(node);
        wayFlags = manager.applyNodeFlags(wayFlags, -nodeFlags);
        assertEquals(60, car.getSpeed(wayFlags));
        assertEquals(59, car2.getSpeed(wayFlags));

        nodeMap.put("test", "something");
        wayFlags = manager.handleWayTags(manager.acceptWay(way), way);
        nodeFlags = manager.analyzeNode(node);
        wayFlags = manager.applyNodeFlags(wayFlags, -nodeFlags);
        assertEquals(58, car2.getSpeed(wayFlags));
        assertEquals(60, car.getSpeed(wayFlags));
    }
}
