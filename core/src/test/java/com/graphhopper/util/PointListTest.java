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
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.graphhopper.util;

import com.graphhopper.util.shapes.GHPoint;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class PointListTest
{
    @Test
    public void testEquals()
    {
        assertEquals(Helper.createPointList(), PointList.EMPTY);
        PointList list1 = Helper.createPointList(38.5, -120.2, 43.252, -126.453, 40.7, -120.95,
                50.3139, 10.612793, 50.04303, 9.497681);
        PointList list2 = Helper.createPointList(38.5, -120.2, 43.252, -126.453, 40.7, -120.95,
                50.3139, 10.612793, 50.04303, 9.497681);
        assertEquals(list1, list2);
    }

    @Test
    public void testReverse()
    {
        PointList instance = new PointList();
        instance.add(1, 1);
        instance.reverse();
        assertEquals(1, instance.getLongitude(0), 1e-7);

        instance = new PointList();
        instance.add(1, 1);
        instance.add(2, 2);
        PointList clonedList = instance.clone(false);
        instance.reverse();
        assertEquals(2, instance.getLongitude(0), 1e-7);
        assertEquals(1, instance.getLongitude(1), 1e-7);

        assertEquals(clonedList, instance.clone(true));
    }

    @Test
    public void testAddPL()
    {
        PointList instance = new PointList();
        for (int i = 0; i < 7; i++)
        {
            instance.add(0, 0);
        }
        assertEquals(7, instance.getSize());
        assertEquals(10, instance.getCapacity());

        PointList toAdd = new PointList();
        instance.add(toAdd);
        assertEquals(7, instance.getSize());
        assertEquals(10, instance.getCapacity());

        toAdd.add(1, 1);
        toAdd.add(2, 2);
        toAdd.add(3, 3);
        toAdd.add(4, 4);
        toAdd.add(5, 5);
        instance.add(toAdd);

        assertEquals(12, instance.getSize());
        assertEquals(24, instance.getCapacity());

        for (int i = 0; i < toAdd.size(); i++)
        {
            assertEquals(toAdd.getLatitude(i), instance.getLatitude(7 + i), 1e-1);
        }
    }

    @Test
    public void testIterable()
    {
        PointList toAdd = new PointList();
        toAdd.add(1, 1);
        toAdd.add(2, 2);
        toAdd.add(3, 3);
        int counter = 0;
        for (GHPoint point : toAdd)
        {
            counter++;
            assertEquals(counter, point.getLat(), 0.1);
        }
    }
}
