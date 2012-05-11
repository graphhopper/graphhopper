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
package de.jetsli.graph.geohash;

import de.jetsli.graph.util.BitUtil;
import java.util.Random;
import org.junit.*;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class SpatialKeyHashtableTest {
//extends QuadTreeTester {
//
//    @Override
//    protected QuadTree<Integer> createQuadTree(long items) {
//        try {
//            return new SpatialKeyTree().init(items);
//        } catch (Exception ex) {
//            throw new RuntimeException(ex);
//        }
//    }

    @Test
    public void testSize2() {
        SpatialKeyHashtable key = new SpatialKeyHashtable(0, 7).init(20);
        assertTrue(key.getEntriesPerBucket() + "", key.getEntriesPerBucket() >= 7);
        assertTrue(key.getMaxBuckets() + " " + key.getEntriesPerBucket(),
                key.getMaxBuckets() * key.getEntriesPerBucket() >= 20);
    }

    @Test
    public void testBucketIndex() {
        for (int i = 9; i < 20; i += 3) {
            SpatialKeyHashtable tree = createSKTWithoutBuffer(i);
            SpatialKeyAlgo algo = tree.getAlgo();
            Random rand = new Random();
            for (int j = 0; j < 10000; j++) {
                double lat = rand.nextDouble() * 5;
                double lon = rand.nextDouble() * 5;
                try {
                    tree.getBucketIndex(algo.encode(lat, lon));
                } catch (Exception ex) {
                    assertFalse("Problem while " + lat + "," + lon + " " + ex.getMessage(), false);
                }
            }
        }
    }

    @Test
    public void testGetPartOfKeyToStore() {
        SpatialKeyHashtable tree = new SpatialKeyHashtable(8).setCompressKey(true);
        tree.setBucketIndexBits(6);
        long part = tree.getPartOfKeyToStore(BitUtil.fromBitString2Long("01000000000110000011100000011110") << 32);
        assertEquals("00000000000000000000000000000110", BitUtil.toBitString(part, 4 * 8));
    }

    @Test
    public void testWriteNoOfEntries() {
        SpatialKeyHashtable tree = new SpatialKeyHashtable(2, 10).setCompressKey(true).init(200);
        tree.writeNoOfEntries(0, 9, true);
        assertTrue(tree.isBucketFull(0));
        assertEquals(9, tree.getNoOfEntries(0));

        tree.writeNoOfEntries(10, 10, false);
        assertFalse(tree.isBucketFull(10));
        assertEquals(10, tree.getNoOfEntries(10));

        try {
            tree.writeNoOfEntries(10, 21, false);
            assertTrue(false);
        } catch (Exception ex) {
        }
    }

    @Test
    public void testStatsNoError() {
        SpatialKeyHashtable tree = new SpatialKeyHashtable(10, 2).init(10000);
        Random rand = new Random(12);
        for (int i = 0; i < 10000; i++) {
            tree.add(Math.abs(rand.nextDouble()), Math.abs(rand.nextDouble()), (long) i * 100);
        }
        tree.getEntries("e");
        tree.getOverflowEntries("o");
        tree.getOverflowOffset("oo");
    }

    @Test
    public void testArrayIsACircle() {
        SpatialKeyHashtable tree = new SpatialKeyHashtable(0, 1).setCompressKey(false).init(2);
        assertEquals(2, tree.getEntriesPerBucket());
        tree.add(1, 1);
        tree.add(1, 2);
        tree.add(1, 3);

        try {
            assertEquals(1, tree.getNodes(0).size());
            assertFalse(true);
        } catch (Exception ex) {
            assertTrue(ex.getMessage(), ex.getMessage().contains("too small"));
        }
    }

    @Test
    public void testArrayIsACircle2() {
        SpatialKeyHashtable tree = new SpatialKeyHashtable(0, 1).setCompressKey(false).init(12);
        assertEquals(3, tree.getEntriesPerBucket());
        int bpb = tree.getBytesPerBucket();
        int bpo = tree.getBytesPerEntry() + 1;
        // fill bucket 6 and 7 (we cannot reach 7 directly as the bucketindex=key%(max-1))
        tree.add(6, 1);
        tree.add(6, 2);
        tree.add(6, 3);

        // 2*overflow to 7
        tree.add(6, 4);
        tree.add(6, 5);
        // overflow into 0
        tree.add(6, 6);

        assertEquals(2, tree.getLastOffset(0));

        assertEquals(6, tree.getNodes(6).size());
        assertEquals(6, (long) tree.getNodes(6).get(5).getValue());

        assertEquals(3, tree.getNoOfEntries(6 * bpb));
        assertEquals(0, tree.getNoOfEntries(7 * bpb));
        assertEquals(0, tree.getNoOfEntries(0 * bpb));
        assertEquals(0, tree.getNoOfEntries(1 * bpb));
        assertEquals(0, tree.getNoOfOverflowEntries(6 * bpb));
        assertEquals(2, tree.getNoOfOverflowEntries(7 * bpb));
        assertEquals(1, tree.getNoOfOverflowEntries(0 * bpb));
        assertEquals(0, tree.getNoOfOverflowEntries(1 * bpb));

        // now add some entries to check if stopbit for different offsets works
        tree.add(5, 7);
        tree.add(5, 8);
        tree.add(5, 9);
        assertEquals(1, tree.getNoOfOverflowEntries(0 * bpb));
        // overflow into 0
        tree.add(5, 10);
        assertEquals(2, tree.getNoOfOverflowEntries(0 * bpb));
        assertEquals(4, tree.getNodes(5).size());
        assertEquals(10, (long) tree.getNodes(5).get(3).getValue());
        assertEquals(6, tree.getNodes(6).size());
        assertEquals(6, (long) tree.getNodes(6).get(5).getValue());

        assertEquals(3, tree.getLastOffset(0));
        assertEquals(-1, tree.getLastOffset(6));
        assertEquals(1, tree.getLastOffset(7));
        try {
            assertEquals(1, tree.getLastOffset(8));
            assertFalse(true);
        } catch (IndexOutOfBoundsException ex) {
        }
    }

    @Test
    public void testAddAndGet() {
        SpatialKeyHashtable tree = createStorage(10, false);
        int max = tree.getEntriesPerBucket() * 2;
        long[] vals = new long[max];

        Random rand = new Random(0);
        for (int i = 0; i < max; i++) {
            vals[i] = rand.nextLong() / 123000;
        }
        for (int i = 0; i < max; i++) {
            try {
                tree.add(vals[i], i);
            } catch (Exception ex) {
                ex.printStackTrace();
                assertFalse("Problem with " + i + " " + vals[i] + " " + ex.getMessage(), true);
            }
        }

        for (int i = 0; i < max; i++) {
            assertEquals(1, tree.getNodes(vals[i]).size());
        }
    }

    @Test
    public void testWriteAndGetKey() {
        SpatialKeyHashtable tree = createStorage(0, false);
        tree.putKey(0, 123);
        assertEquals(123, tree.getKey(0));
        tree.putKey(2, Long.MAX_VALUE / 3);
        assertEquals(Long.MAX_VALUE / 3, tree.getKey(2));
        tree.putKey(0, -1);
        assertEquals(-1, tree.getKey(0));
        tree.putKey(30, 123);
        assertEquals(123, tree.getKey(30));

        tree.writeNoOfEntries(0, 3, false);
        assertFalse(tree.isBucketFull(0));
        tree.writeNoOfEntries(0, 3, true);
        assertTrue(tree.isBucketFull(0));
    }

    @Test
    public void testCountEntriesAndOverflowEntries() {
        SpatialKeyHashtable tree = createStorage(0, false);
        assertEquals(3, tree.getEntriesPerBucket());
        tree.add(0, 1);
        tree.add(0, 2);
        assertEquals(0, tree.getNoOfOverflowEntries(0));

        tree.add(0, 3);
        assertEquals(0, tree.getNoOfOverflowEntries(0));

        tree.add(0, 4);
        assertEquals(0, tree.getNoOfOverflowEntries(0));
        assertEquals(1, tree.getNoOfOverflowEntries(tree.getBytesPerBucket()));

        tree.add(1, 5);
        tree.add(1, 6);
        assertEquals(1, tree.getNoOfOverflowEntries(tree.getBytesPerBucket()));
        assertEquals(1, tree.getNoOfEntries(tree.getBytesPerBucket()));
    }

    @Test
    public void testKeyDuplicatesForceOverflow() {
        // 0 => force that it is a bad hash creation algo
        // false => do not compress key
        SpatialKeyHashtable tree = createStorage(0, false);
        assertEquals(3, tree.getEntriesPerBucket());
        int max = 6;
        int bytesPerBucket = tree.getBytesPerBucket();
        int bytesPerEntry = tree.getBytesPerEntry();
        for (int i = 0; i < max; i++) {
            tree.add(0, i);
            tree.add(1, i);
            tree.add(2, i);
        }

        assertEquals(max, tree.getNodes(0).size());
        assertEquals(max, tree.getNodes(1).size());
        assertEquals(max, tree.getNodes(2).size());
        assertEquals(0, tree.getNodes(3).size());

        // no overflow stuff
        assertEquals(0, tree.getNoOfOverflowEntries(0));
        assertEquals(0, tree.getNoOfOverflowEntries(bytesPerBucket));
        assertEquals(0, tree.getNoOfOverflowEntries(2 * bytesPerBucket));

        // one overflow entry only
        assertEquals(2, tree.getNoOfOverflowEntries(3 * bytesPerBucket));
        assertEquals(2, tree.getNoOfOverflowEntries(4 * bytesPerBucket));
    }

    SpatialKeyHashtable createStorage(final int skipLeft, boolean compress) {
        try {
            return new SpatialKeyHashtable(skipLeft, 1).setCompressKey(compress).init(120);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
    // faster - but not functional

    SpatialKeyHashtable createSKTWithoutBuffer(int i) {
        return new SpatialKeyHashtable(i) {

            @Override protected void initBuffers() {
            }
        };
    }
}
