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
package com.graphhopper.util;

import com.graphhopper.util.shapes.GHPoint;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Peter Karich
 */
public class PointListTest {
    @Test
    public void testEquals() {
        assertEquals(Helper.createPointList(), PointList.EMPTY);
        PointList list1 = Helper.createPointList(38.5, -120.2, 43.252, -126.453, 40.7, -120.95,
                50.3139, 10.612793, 50.04303, 9.497681);
        PointList list2 = Helper.createPointList(38.5, -120.2, 43.252, -126.453, 40.7, -120.95,
                50.3139, 10.612793, 50.04303, 9.497681);
        assertEquals(list1, list2);
    }

    @Test
    public void testReverse() {
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
    public void testAddPL() {
        PointList instance = new PointList();
        for (int i = 0; i < 7; i++) {
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

        for (int i = 0; i < toAdd.size(); i++) {
            assertEquals(toAdd.getLatitude(i), instance.getLatitude(7 + i), 1e-1);
        }
    }

    @Test
    public void testIterable() {
        PointList toAdd = new PointList();
        toAdd.add(1, 1);
        toAdd.add(2, 2);
        toAdd.add(3, 3);
        int counter = 0;
        for (GHPoint point : toAdd) {
            counter++;
            assertEquals(counter, point.getLat(), 0.1);
        }
    }

    @Test
    public void testCopy_issue1166() {
        PointList list = new PointList(20, false);
        for (int i = 0; i < 10; i++) {
            list.add(1, i);
        }
        assertEquals(10, list.getSize());
        assertEquals(20, list.getCapacity());

        PointList copy = list.copy(9, 10);
        assertEquals(1, copy.getSize());
        assertEquals(1, copy.getCapacity());
        assertEquals(9, copy.getLongitude(0), .1);
    }

    @Test
    public void testShallowCopy() {
        PointList pl1 = new PointList(100, true);
        for (int i = 0; i < 1000; i++) {
            pl1.add(i, i, 0);
        }

        PointList pl2 = pl1.shallowCopy(100, 600, false);
        assertEquals(500, pl2.size());
        for (int i = 0; i < pl2.size(); i++) {
            assertEquals(pl1.getLat(i + 100), pl2.getLat(i), .01);
        }

        // If you change the original PointList the shallow copy changes as well
        pl1.set(100, 0, 0, 0);
        assertEquals(0, pl2.getLat(0), .01);

        // Create a shallow copy of the shallow copy
        PointList pl3 = pl2.shallowCopy(0, 100, true);
        // If we create a safe shallow copy of pl2, we have to make pl1 immutable
        assertTrue(pl1.isImmutable());
        assertEquals(100, pl3.size());
        for (int i = 0; i < pl3.size(); i++) {
            assertEquals(pl2.getLon(i), pl3.getLon(i), .01);
        }

        PointList pl4 = pl1.shallowCopy(0, pl1.size(), false);
        assertTrue(pl1.equals(pl4));

        PointList pl5 = pl1.shallowCopy(100, 600, false);
        assertTrue(pl2.equals(pl5));

    }

    @Test(expected = IllegalStateException.class)
    public void testImmutable() {
        PointList pl = new PointList();
        pl.makeImmutable();
        pl.add(0, 0, 0);
    }

    @Test()
    public void testToString() {
        PointList pl = new PointList(3, true);
        pl.add(0, 0, 0);
        pl.add(1, 1, 1);
        pl.add(2, 2, 2);

        assertEquals("(0.0,0.0,0.0), (1.0,1.0,1.0), (2.0,2.0,2.0)", pl.toString());
        assertEquals("(1.0,1.0,1.0), (2.0,2.0,2.0)", pl.shallowCopy(1, 3, false).toString());
    }

    @Test()
    public void testClone() {
        PointList pl = new PointList(3, true);
        pl.add(0, 0, 0);
        pl.add(1, 1, 1);
        pl.add(2, 2, 2);

        PointList shallowPl = pl.shallowCopy(1, 3, false);
        PointList clonedPl = shallowPl.clone(false);

        assertEquals(shallowPl, clonedPl);
        clonedPl.setNode(0, 5, 5, 5);
        assertNotEquals(shallowPl, clonedPl);
    }

    @Test()
    public void testCopyOfShallowCopy() {
        PointList pl = new PointList(3, true);
        pl.add(0, 0, 0);
        pl.add(1, 1, 1);
        pl.add(2, 2, 2);

        PointList shallowPl = pl.shallowCopy(1, 3, false);
        PointList copiedPl = shallowPl.copy(0, 2);

        assertEquals(shallowPl, copiedPl);
        copiedPl.setNode(0, 5, 5, 5);
        assertNotEquals(shallowPl, copiedPl);
    }

    @Test()
    public void testCalcDistanceOfShallowCopy() {
        PointList pl = new PointList(3, true);
        pl.add(0, 0, 0);
        pl.add(1, 1, 1);
        pl.add(2, 2, 2);

        PointList shallowPl = pl.shallowCopy(1, 3, false);
        PointList clonedPl = shallowPl.clone(false);
        assertEquals(clonedPl.calcDistance(Helper.DIST_EARTH), shallowPl.calcDistance(Helper.DIST_EARTH), .01);
    }

    @Test()
    public void testToGeoJson() {
        PointList pl = new PointList(3, true);
        pl.add(0, 0, 0);
        pl.add(1, 1, 1);
        pl.add(2, 2, 2);

        assertEquals(3, pl.toGeoJson(true).size());
        assertEquals(2, pl.shallowCopy(1, 3, false).toGeoJson(true).size());
    }


}
