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
package com.graphhopper.coll;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class StripedLongLongBTreeTest {

    @Test
    void testBasicOperations() {
        StripedLongLongBTree map = new StripedLongLongBTree(8, 200, 5, -1);

        assertEquals(-1, map.get(100));
        assertEquals(-1, map.put(100, 42));
        assertEquals(42, map.get(100));
        assertEquals(42, map.put(100, 43));
        assertEquals(43, map.get(100));

        assertEquals(1, map.getSize());
    }

    @Test
    void testPutOrCompute() {
        StripedLongLongBTree map = new StripedLongLongBTree(8, 200, 5, -1);

        // Insert when absent
        long result = map.putOrCompute(100, 10, old -> old + 100);
        assertEquals(-1, result);
        assertEquals(10, map.get(100));

        // Update when present
        result = map.putOrCompute(100, 10, old -> old + 100);
        assertEquals(10, result);
        assertEquals(110, map.get(100));
    }

    @Test
    void testNumStripesRoundedToPowerOfTwo() {
        assertEquals(1, new StripedLongLongBTree(1, 200, 5, -1).getNumStripes());
        assertEquals(2, new StripedLongLongBTree(2, 200, 5, -1).getNumStripes());
        assertEquals(4, new StripedLongLongBTree(3, 200, 5, -1).getNumStripes());
        assertEquals(4, new StripedLongLongBTree(4, 200, 5, -1).getNumStripes());
        assertEquals(8, new StripedLongLongBTree(5, 200, 5, -1).getNumStripes());
        assertEquals(16, new StripedLongLongBTree(16, 200, 5, -1).getNumStripes());
        assertEquals(32, new StripedLongLongBTree(17, 200, 5, -1).getNumStripes());
    }

    @Test
    void testManyKeys() {
        StripedLongLongBTree map = new StripedLongLongBTree(8, 200, 5, -1);

        int count = 100_000;
        for (int i = 0; i < count; i++) {
            map.put(i, i * 2);
        }

        assertEquals(count, map.getSize());

        for (int i = 0; i < count; i++) {
            assertEquals(i * 2, map.get(i));
        }
    }

    @Test
    void testConcurrentPut() throws InterruptedException {
        StripedLongLongBTree map = new StripedLongLongBTree(16, 200, 5, -1);

        int numThreads = 8;
        int keysPerThread = 10_000;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);

        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < keysPerThread; i++) {
                        // Each thread writes to its own key range
                        long key = threadId * keysPerThread + i;
                        map.put(key, key * 2);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(numThreads * keysPerThread, map.getSize());

        // Verify all values
        for (int t = 0; t < numThreads; t++) {
            for (int i = 0; i < keysPerThread; i++) {
                long key = t * keysPerThread + i;
                assertEquals(key * 2, map.get(key), "Key " + key);
            }
        }
    }

    @Test
    void testConcurrentPutOrCompute() throws InterruptedException {
        StripedLongLongBTree map = new StripedLongLongBTree(16, 200, 5, -1);

        int numThreads = 8;
        int numKeys = 1000;
        int incrementsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);

        // All threads increment the same keys concurrently
        for (int t = 0; t < numThreads; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int round = 0; round < incrementsPerThread; round++) {
                        for (int key = 0; key < numKeys; key++) {
                            map.putOrCompute(key, 1, old -> old + 1);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(numKeys, map.getSize());

        // Each key should have been incremented numThreads * incrementsPerThread times
        long expectedValue = numThreads * incrementsPerThread;
        for (int key = 0; key < numKeys; key++) {
            assertEquals(expectedValue, map.get(key), "Key " + key);
        }
    }

    @Test
    void testConcurrentMixedOperations() throws InterruptedException {
        StripedLongLongBTree map = new StripedLongLongBTree(16, 200, 5, -1);

        int numThreads = 8;
        int opsPerThread = 50_000;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        AtomicInteger errors = new AtomicInteger(0);

        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            final Random random = new Random(threadId);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < opsPerThread; i++) {
                        long key = random.nextInt(10_000);
                        int op = random.nextInt(3);
                        switch (op) {
                            case 0:
                                map.put(key, key);
                                break;
                            case 1:
                                map.get(key);
                                break;
                            case 2:
                                map.putOrCompute(key, 1, old -> old + 1);
                                break;
                        }
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(60, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(0, errors.get(), "There should be no errors");
        assertTrue(map.getSize() > 0, "Map should have entries");
    }

    @Test
    void testClearAndOptimize() {
        StripedLongLongBTree map = new StripedLongLongBTree(8, 200, 5, -1);

        for (int i = 0; i < 10_000; i++) {
            map.put(i, i);
        }
        assertEquals(10_000, map.getSize());

        map.optimize();
        assertEquals(10_000, map.getSize());

        map.clear();
        assertEquals(0, map.getSize());
        assertEquals(-1, map.get(100));
    }

    @Test
    void testKeysDistributedAcrossStripes() {
        // This is a rough test to ensure sequential keys don't all land in the same stripe
        // We can't directly inspect stripes, but we can verify the map works with sequential keys
        StripedLongLongBTree map = new StripedLongLongBTree(16, 10, 5, -1);

        // Insert many sequential keys - if they all went to one stripe,
        // we'd see lots of tree splits in that stripe. The map should still work.
        for (long i = 0; i < 10_000; i++) {
            map.put(i, i);
        }

        assertEquals(10_000, map.getSize());

        for (long i = 0; i < 10_000; i++) {
            assertEquals(i, map.get(i));
        }
    }
}
