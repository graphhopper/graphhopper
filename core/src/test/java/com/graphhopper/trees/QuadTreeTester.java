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
package com.graphhopper.trees;

import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.CoordTrig;
import java.util.Collection;
import java.util.Iterator;
import org.junit.*;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public abstract class QuadTreeTester
{
    protected abstract QuadTree<Long> createQuadTree( long items );

    @Test
    public void testSize()
    {
        QuadTree instance = createQuadTree(100);
        assertEquals(0, instance.getSize());
    }

    @Test
    public void testPutAndGet()
    {
        QuadTree<Long> instance = createQuadTree(100);
        assertEquals(0, instance.getNodesFromValue(12, 10, null).size());
        instance.add(12, 10, 111L);
        assertEquals(1, instance.getSize());
        assertEquals(111, (long) instance.getNodesFromValue(12, 10, null).iterator().next().getValue());

        instance.add(12, 10, 222L);
        assertEquals(2, instance.getSize());

        instance.add(12, 12, 333L);
        assertEquals(3, instance.getSize());

        assertEquals(2, instance.getNodesFromValue(12, 10, null).size());
        instance.add(12.0001, 12.0001, 444L);
        assertEquals(4, instance.getSize());
        assertEquals(444, (long) instance.getNodesFromValue(12.0001, 12.0001, null).iterator().next().getValue());
        assertEquals(0, instance.getNodesFromValue(12.0001, 12.0001, 445L).size());

        instance.add(10, 10, 1010L);
        assertEquals(5, instance.getSize());
        assertEquals(1010, (long) instance.getNodesFromValue(10, 10, null).iterator().next().getValue());

        instance.add(10, 12, 1012L);

        assertEquals(6, instance.getSize());
        assertEquals(444, (long) instance.getNodesFromValue(12.0001, 12.0001, null).iterator().next().getValue());
        assertEquals(1010, (long) instance.getNodesFromValue(10, 10, null).iterator().next().getValue());
        assertEquals(1012, (long) instance.getNodesFromValue(10, 12, null).iterator().next().getValue());

        instance.clear();
        assertEquals(0, instance.getSize());
    }

    @Test
    public void testPutBatch()
    {
        int max = 100;
        QuadTree<Long> instance = createQuadTree(max);
        for (long i = 0; i < max; i++)
        {
            instance.add(i / 100.0, i / 100.0, i);
            assertEquals(i + 1, instance.getSize());
        }

        assertEquals(max, instance.getSize());
        for (int i = 0; i < max; i++)
        {
            Collection<CoordTrig<Long>> coll = instance.getNodesFromValue(i / 100.0, i / 100.0, null);
            assertEquals(i + "/" + max, 1, coll.size());
            Long val = (Long) coll.iterator().next().getValue();
            assertNotNull(i + "/" + max, val);
            assertEquals(i + "/" + max, i, (long) val);
        }

        for (int i = 0; i < max; i++)
        {
            assertEquals(max - i, instance.getSize());
            Collection<CoordTrig<Long>> res = instance.getNodes(i / 100.0, i / 100.0, 1d);
            CoordTrig<Long> coord = res.iterator().next();
            assertEquals("couldn't remove " + i / 100.0 + " -> " + coord, 1, instance.remove(coord.lat, coord.lon));
        }
    }

    @Test
    public void testRemove()
    {
        QuadTree<Long> instance = createQuadTree(100);

        assertEquals(0, instance.remove(7.002f, 7.001f));
        instance.add(12, 10, 111L);
        assertEquals(1, instance.getSize());

        assertEquals(1, instance.remove(12, 10));
        assertEquals(0, instance.getNodesFromValue(12, 10, null).size());
        assertEquals(0, instance.getSize());

        instance.add(12, 10, 111L);
        instance.add(12, 10.1, 222L);
        assertEquals(2, instance.getSize());
        // System.out.println(instance.toDetailString());
        assertEquals(1, instance.remove(12, 10));
        // System.out.println(instance.toDetailString());
        assertEquals(1, instance.getSize());
        assertEquals(0, instance.getNodesFromValue(12, 10, null).size());
        assertEquals(222, (long) instance.getNodesFromValue(12, 10.1, null).iterator().next().getValue());
    }

    @Test
    public void testGetNeighboursExactHit()
    {
        QuadTree<Long> instance = createQuadTree(100);
        Collection<CoordTrig<Long>> coll = instance.getNodes(8.124, 8.123, 10000);
        assertTrue(coll.isEmpty());

        instance.add(8.124, 8.123, 1L);
        instance.add(8.123, 8.123, 2L);
        instance.add(9.124, 8.123, 3l);
        assertEquals(3, instance.getSize());

        // search in 10km - exact hit
        coll = instance.getNodes(8.124, 8.123, 10000);
        assertEquals(2, coll.size());

        coll = instance.getNodes(8.124, 8.123, 120000);
        assertEquals(3, coll.size());
    }

    @Test
    public void testGetNeighboursSearch()
    {
        QuadTree<Long> instance = createQuadTree(100);
        Collection<CoordTrig<Long>> coll = instance.getNodes(8.124, 8.123, 10000);
        assertTrue(coll.isEmpty());

        instance.add(8.124, 8.123, 1L);
        instance.add(8.123, 8.123, 2L);
        instance.add(9.124, 8.123, 3L);
        instance.add(8, 9, 4L);
        instance.add(9, 9, 5L);
        instance.add(7, 7, 6L);
        instance.add(7, 8, 7L);
        instance.add(7, 9, 8L);
        instance.add(8, 7, 9L);
        instance.add(9, 7, 10L);

        // distances from 8.12, 8.12
        //
        // 9,7: 157.29199    9.124,8.123: 111.64019   9,9: 137.61365
        //
        // 8,7: 124.02789    distance:0               8,9: 97.79944
        //
        // 7,7: 175.3585     7,8: 125.23878           7,9: 157.85646
        //
//        CalcDistance c = new CalcDistance();
//        System.out.println("9.124, 8.123:" + c.calcDistKm(9.124, 8.123, 8.12, 8.12));
//        System.out.println("8,9:" + c.calcDistKm(8, 9, 8.12, 8.12));
//        System.out.println("9,9:" + c.calcDistKm(9, 9, 8.12, 8.12));
//        System.out.println("7,7:" + c.calcDistKm(7, 7, 8.12, 8.12));
//        System.out.println("7,8:" + c.calcDistKm(7, 8, 8.12, 8.12));
//        System.out.println("7,9:" + c.calcDistKm(7, 9, 8.12, 8.12));
//        System.out.println("8,7:" + c.calcDistKm(8, 7, 8.12, 8.12));
//        System.out.println("9,7:" + c.calcDistKm(9, 7, 8.12, 8.12));
        assertEquals(10, instance.getSize());

        assertEquals(2, instance.getNodes(8.12, 8.12, 10000).size());
        assertEquals(2, instance.getNodes(8.12, 8.12, 50000).size());
        assertEquals(1, instance.getNodes(8.12, 8.12, 500).size());
        Iterator<CoordTrig<Long>> iter = instance.getNodes(8.12, 8.12, 500).iterator();
        assertEquals(2, (long) iter.next().getValue());
        assertEquals(3, instance.getNodes(8.12, 8.12, 100000).size());
//        System.out.println(instance.getNodes(8.12, 8.12, 130));
        assertEquals(6, instance.getNodes(8.12, 8.12, 130000).size());
        assertEquals(9, instance.getNodes(8.12, 8.12, 175000).size());
        assertEquals(10, instance.getNodes(8.12, 8.12, 176000).size());
    }

    public static void assertOrder( Collection<CoordTrig> coll, double... latitudes )
    {
        Iterator<CoordTrig> iter = coll.iterator();
        for (int i = 0; i < latitudes.length; i++)
        {
            double f = latitudes[i];
            CoordTrig f2d = iter.next();
            assertEquals(f, f2d.lat, 1e-8);
        }
        assertFalse("There are more than " + latitudes.length + " items", iter.hasNext());
    }

    public static void assertCount( int c, Iterator iter )
    {
        for (int i = c; i >= 0; i--)
        {
            iter.next();
        }
        assertFalse("There are more than " + c + " items", iter.hasNext());
    }
}
