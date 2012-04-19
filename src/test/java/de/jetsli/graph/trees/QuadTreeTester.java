/*
 *  Copyright 2012 Peter Karich info@jetsli.de
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.jetsli.graph.trees;

import de.jetsli.graph.util.shapes.BBox;
import de.jetsli.graph.util.CoordTrig;
import java.util.Collection;
import java.util.Iterator;
import org.junit.*;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public abstract class QuadTreeTester {

    protected abstract QuadTree<Integer> createQuadTree(int items);

    @Test
    public void testSize() {
        QuadTree instance = createQuadTree(100);
        assertEquals(0, instance.size());
    }

//    @Test
//    public void testPrint() {
//        int maxbits = 62;
//        GeoHashAlgo algo = new GeoHashAlgo().init(maxbits);
//        System.out.println("12,10:" + BitUtil.toBitString(algo.encode(12, 10), maxbits));
//        System.out.println("12,12:" + BitUtil.toBitString(algo.encode(12, 12), maxbits));
//        System.out.println("10,10:" + BitUtil.toBitString(algo.encode(10, 10), maxbits));
//        System.out.println("10,12:" + BitUtil.toBitString(algo.encode(10, 12), maxbits));
//        System.out.println("12,0.:" + BitUtil.toBitString(algo.encode(12.0001f, 12.0001f), maxbits));
//    }
    @Test
    public void testPutAndGet() {
        QuadTree instance = createQuadTree(100);
        assertEquals(null, instance.get(12, 10));
        assertEquals(null, instance.put(12, 10, 111));
        assertEquals(1, instance.size());
        assertEquals(111, instance.get(12, 10));

        assertEquals(111, instance.put(12, 10, 222));
        assertEquals(1, instance.size());

        assertEquals(null, instance.put(12, 12, 333));
        // System.out.println(instance.toDetailString() + "\n");

        assertEquals(333, instance.get(12, 12));
        assertEquals(2, instance.size());

        assertEquals(null, instance.put(12.0001f, 12.0001f, 444));
        // System.out.println(instance.toDetailString() + "\n");
        assertEquals(3, instance.size());
        assertEquals(444, instance.get(12.0001f, 12.0001f));

        assertEquals(null, instance.put(10, 10, 1010));
        // System.out.println(instance.toDetailString() + "\n");
        assertEquals(4, instance.size());
        assertEquals(1010, instance.get(10, 10));

        assertEquals(null, instance.put(10, 12, 1012));
        // System.out.println(instance.toDetailString() + "\n");

        assertEquals(5, instance.size());
        assertEquals(444, instance.get(12.0001f, 12.0001f));
        assertEquals(1010, instance.get(10, 10));
        assertEquals(1012, instance.get(10, 12));

        instance.clear();
        assertEquals(0, instance.size());
    }

    @Test
    public void testPutBatch() {
        QuadTree instance = createQuadTree(100);
        int max = 1000;
        for (int i = 0; i < max; i++) {
            instance.put(i / 100.0, i / 100.0, i);
            assertEquals(i + 1, instance.size());
        }

        assertEquals(max, instance.size());
        for (int i = 0; i < max; i++) {
            Integer integ = (Integer) instance.get(i / 100.0, i / 100.0);
            assertNotNull("i/100 ", integ);
            assertEquals(i + "/100", i, (int) integ);
        }

        for (int i = 0; i < max; i++) {
            assertEquals(max - i, instance.size());
            Collection<CoordTrig> res = instance.getNeighbours(i / 100.0, i / 100.0, 0.001d);
            CoordTrig coord = res.iterator().next();
            assertTrue("couldn't remove " + i / 100.0 + " -> " + coord, instance.remove(coord.lat, coord.lon));
        }
    }

    @Test
    public void testRemove() {
        QuadTree instance = createQuadTree(100);

        assertFalse(instance.remove(7.002f, 7.001f));
        assertEquals(null, instance.put(12, 10, 111));
        assertEquals(1, instance.size());

        assertTrue(instance.remove(12, 10));
        assertEquals(null, instance.get(12, 10));
        assertEquals(0, instance.size());

        assertEquals(null, instance.put(12, 10, 111));
        assertEquals(null, instance.put(12, 10.1f, 222));
        assertEquals(2, instance.size());
        // System.out.println(instance.toDetailString());
        assertTrue(instance.remove(12, 10));
        // System.out.println(instance.toDetailString());
        assertEquals(1, instance.size());
        assertEquals(null, instance.get(12, 10));
        assertEquals(222, instance.get(12, 10.1f));
    }

    @Test
    public void testGetNeighboursExactHit() {
        QuadTree<Integer> instance = createQuadTree(100);
        Collection<CoordTrig<Integer>> coll = instance.getNeighbours(8.124, 8.123, 10);
        assertTrue(coll.isEmpty());

        assertEquals(null, instance.put(8.124, 8.123, 1));
        assertEquals(null, instance.put(8.123, 8.123, 2));
        assertEquals(null, instance.put(9.124, 8.123, 3));
        assertEquals(3, instance.size());

        // search in 10km - exact hit
        coll = instance.getNeighbours(8.124, 8.123, 10);
        assertEquals(2, coll.size());

        coll = instance.getNeighbours(8.124, 8.123, 120);
        assertEquals(3, coll.size());
    }

    @Test
    public void testGetNeighboursRectangleSearch() {
        QuadTree<Integer> instance = createQuadTree(100);
        Collection<CoordTrig<Integer>> coll = instance.getNeighbours(new BBox(10, 12, 9.5f, 12.5f));
        // TODO
    }

    @Test
    public void testGetNeighboursSearch() {
        QuadTree<Integer> instance = createQuadTree(100);
        Collection<CoordTrig<Integer>> coll = instance.getNeighbours(8.124, 8.123, 10);
        assertTrue(coll.isEmpty());

        assertEquals(null, instance.put(8.124, 8.123, 1));
        assertEquals(null, instance.put(8.123, 8.123, 2));
        assertEquals(null, instance.put(9.124, 8.123, 3));
        assertEquals(null, instance.put(8, 9, 4));
        assertEquals(null, instance.put(9, 9, 5));
        assertEquals(null, instance.put(7, 7, 6));
        assertEquals(null, instance.put(7, 8, 7));
        assertEquals(null, instance.put(7, 9, 8));
        assertEquals(null, instance.put(8, 7, 9));
        assertEquals(null, instance.put(9, 7, 10));

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

        assertEquals(10, instance.count());
        assertEquals(10, instance.size());

        assertEquals(2, instance.getNeighbours(8.12, 8.12, 10).size());
        assertEquals(2, instance.getNeighbours(8.12, 8.12, 50).size());
        assertEquals(1, instance.getNeighbours(8.12, 8.12, 0.5).size());
        Iterator<CoordTrig<Integer>> iter = instance.getNeighbours(8.12, 8.12, 0.5).iterator();
        assertEquals(2, (int) iter.next().getValue());
        assertEquals(3, instance.getNeighbours(8.12, 8.12, 100).size());
        assertEquals(6, instance.getNeighbours(8.12, 8.12, 130).size());
        assertEquals(9, instance.getNeighbours(8.12, 8.12, 175).size());
        assertEquals(10, instance.getNeighbours(8.12, 8.12, 176).size());
    }

    public static void assertOrder(Collection<CoordTrig> coll, double... latitudes) {
        Iterator<CoordTrig> iter = coll.iterator();
        for (int i = 0; i < latitudes.length; i++) {
            double f = latitudes[i];
            CoordTrig f2d = iter.next();
            assertEquals(f, f2d.lat, 1e-8);
        }
        assertFalse("There are more than " + latitudes.length + " items", iter.hasNext());
    }

    public static void assertCount(int c, Iterator iter) {
        for (int i = c; i >= 0; i--) {
            iter.next();
        }
        assertFalse("There are more than " + c + " items", iter.hasNext());
    }
}
