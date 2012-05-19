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

import de.jetsli.graph.trees.QuadTree;
import de.jetsli.graph.trees.QuadTreeTester;
import de.jetsli.graph.util.BitUtil;
import de.jetsli.graph.util.CoordTrig;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import org.junit.*;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class SpatialHashtableTest extends QuadTreeTester {

    @Override
    protected QuadTree<Long> createQuadTree(long items) {
        return new SpatialHashtable().init(items);
    }

    @Test 
    @Override public void testRemove() {
        // TODO not yet implemented
    }

    @Test
    public void testSize2() {
        SpatialHashtable key = new SpatialHashtable(0, 7).init(20);
        assertTrue(key.getEntriesPerBucket() + "", key.getEntriesPerBucket() >= 7);
        assertTrue(key.getMaxBuckets() + " " + key.getEntriesPerBucket(),
                key.getMaxBuckets() * key.getEntriesPerBucket() >= 20);
    }

    @Test
    public void testBucketIndex() {
        for (int i = 9; i < 20; i += 3) {
            SpatialHashtable tree = createSKTWithoutBuffer(i);
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
    public void testGetStoredKey() {
        SpatialHashtable tree = new SpatialHashtable(6).setCompressKey(true).init(2000);
        assertEquals(9, tree.getBucketIndexBits());
        assertEquals(14, tree.getSkipKeyBeginningBits());
        long spatialKey = BitUtil.fromBitString2Long("00000000000110000011100000011110" + "00000110000001110000000111100000");
        // int bucketIndex = tree.getBucketIndex(spatialKey);
        long part = tree.getStoredKey(spatialKey);
        assertEquals("00000000000000000000110000011100" + "00000110000001110000000111100000", BitUtil.toBitString(part));
    }

    @Test
    public void testWriteNoOfEntries() {
        SpatialHashtable tree = new SpatialHashtable(2, 10).setCompressKey(true).init(200);
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
        SpatialHashtable tree = new SpatialHashtable(10, 2).init(10000);
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
        SpatialHashtable tree = new SpatialHashtable(0, 1).setCompressKey(false).init(2);
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
        SpatialHashtable tree = new SpatialHashtable(0, 1).setCompressKey(false).init(12);
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
        SpatialHashtable tree = createStorage(10, false);
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
        SpatialHashtable tree = createStorage(0, false);
        int bucketIndex = tree.getBucketIndex(123);
        tree.putKey(0, 123);
        assertEquals(123, tree.getKey(0, bucketIndex));
        bucketIndex = tree.getBucketIndex(Long.MAX_VALUE / 3);
        tree.putKey(2, Long.MAX_VALUE / 3);
        assertEquals(Long.MAX_VALUE / 3, tree.getKey(2, bucketIndex));
        bucketIndex = tree.getBucketIndex(-1);
        tree.putKey(0, -1);
        assertEquals(-1, tree.getKey(0, bucketIndex));
        bucketIndex = tree.getBucketIndex(123);
        tree.putKey(30, 123);
        assertEquals(123, tree.getKey(30, bucketIndex));

        tree.writeNoOfEntries(0, 3, false);
        assertFalse(tree.isBucketFull(0));
        tree.writeNoOfEntries(0, 3, true);
        assertTrue(tree.isBucketFull(0));
    }

    @Test
    public void testKeyCompression() {
        SpatialHashtable tree = createStorage(10, true);
        assertEquals(6, tree.getBucketIndexBits());

        long key = tree.getAlgo().encode(10.0001, 10.02);
        int bucketIndex = tree.getBucketIndex(key);
        tree.putKey(0, tree.getStoredKey(key));
        long keyTmp = tree.getKey(0, bucketIndex);
        assertEquals(BitUtil.toBitString(key), BitUtil.toBitString(keyTmp));
        assertEquals(key, keyTmp);

        key = tree.getAlgo().encode(12, 10);
        bucketIndex = tree.getBucketIndex(key);
        tree.putKey(0, tree.getStoredKey(key));
        keyTmp = tree.getKey(0, bucketIndex);
        assertEquals(BitUtil.toBitString(key), BitUtil.toBitString(keyTmp));
        assertEquals(key, keyTmp);
    }

    @Test
    public void testCountEntriesAndOverflowEntries() {
        SpatialHashtable tree = createStorage(0, false);
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
    public void testRead_IfSameBucketIndexDifferentKey() {
        SpatialHashtable tree = createStorage(0, true);
        tree.add(1, 1);
        tree.add(2, 2);
        List<CoordTrig<Long>> res = tree.getNodes(1);
        assertEquals(1, res.size());
        assertEquals(1, (long) res.get(0).getValue());

        res = tree.getNodes(2);
        assertEquals(1, res.size());
        assertEquals(2, (long) res.get(0).getValue());

        tree = createStorage(8, true);
        tree.add(12, 10, 1L);
        tree.add(12.00001, 10.0001, 2L);
        Collection<CoordTrig<Long>> res2 = tree.getNodes(12, 10, 0.0001);
        assertEquals(1, res2.size());
        assertEquals(1L, (long) res2.iterator().next().getValue());

        res2 = tree.getNodes(12.00001, 10.0001, 0.001);
        assertEquals(1, res2.size());
        assertEquals(2L, (long) res2.iterator().next().getValue());
    }

    @Test
    public void testKeyDuplicatesForceOverflow() {
        // 0 => force that it is a bad hash creation algo
        // false => do not compress key
        SpatialHashtable tree = createStorage(0, false);
        assertEquals(3, tree.getEntriesPerBucket());
        int max = 6;
        int bytesPerBucket = tree.getBytesPerBucket();
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

    SpatialHashtable createStorage(final int skipLeft, boolean compress) {
        try {
            return new SpatialHashtable(skipLeft, 1).setCompressKey(compress).init(120);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
    // faster - but not functional

    SpatialHashtable createSKTWithoutBuffer(int i) {
        return new SpatialHashtable(i) {

            @Override protected void initBuffers() {
            }
        };
    }
}
