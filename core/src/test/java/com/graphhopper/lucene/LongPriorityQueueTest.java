package com.graphhopper.lucene;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LongPriorityQueueTest {

    @Test
    public void testAddAndPoll() {
        int N = 2000;
        LongPriorityQueue queue = new LongPriorityQueue(10, N, -1);
        List<Long> list = new ArrayList<>();
        long seed = System.currentTimeMillis();
        Random rand = new Random(seed);
        for (int i = 0; i < N; i++) {
            long value = rand.nextLong();
            queue.add(value);
            list.add(value);
        }
        Collections.sort(list);
        int counter = 0;
        while (!queue.isEmpty()) {
            long value = queue.pop();
            assertEquals(list.get(counter), value, "seed: " + seed);
            counter++;
        }
    }

    @Test
    public void testAddAndRemoveAndPoll() {
        int N = 2000;
        LongPriorityQueue queue = new LongPriorityQueue(10, N, Long.MAX_VALUE);
        List<Long> list = new ArrayList<>();
        long seed = System.currentTimeMillis();
        Random rand = new Random(seed);
        for (int i = 0; i < N; i++) {
            long value = rand.nextInt();
            queue.add(value);
            list.add(value);
        }
        Collections.sort(list);

        for (int i = 0; i < N / 20; i++) {
            int removeAtIndex = rand.nextInt(list.size());
            long value = list.remove(removeAtIndex);
            assertTrue(queue.remove(value), "seed : " + seed);
            assertEquals(queue.size(), list.size(), "seed: " + seed);
        }

        int counter = 0;
        while (!queue.isEmpty()) {
            long value = queue.pop();
            assertEquals(list.get(counter), value, "seed: " + seed);
            counter++;
        }
    }
}