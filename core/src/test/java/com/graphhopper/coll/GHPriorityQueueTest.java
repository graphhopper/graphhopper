package com.graphhopper.coll;

import com.graphhopper.storage.SPTEntry;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GHPriorityQueueTest {

    @Test
    public void add() {
        GHPriorityQueue<Integer> queue = new GHPriorityQueue<>(10);
        queue.add(0, 10);
        queue.add(1, 5);
        queue.add(2, 20);
        queue.add(3, 7);
        queue.add(4, 17);

        assertEquals(5, queue.peekPriority(), .1);
        assertEquals(1, queue.poll());

        assertEquals(7, queue.peekPriority(), .1);
        assertEquals(3, queue.poll());

        assertEquals(10, queue.peekPriority(), .1);
        assertEquals(0, queue.poll());

        assertEquals(17, queue.peekPriority(), .1);
        assertEquals(4, queue.poll());

        assertEquals(20, queue.peekPriority(), .1);
        assertEquals(2, queue.poll());
    }

    @Test
    public void addDuplicate() {
        GHPriorityQueue<Integer> queue = new GHPriorityQueue<>(10);
        queue.add(0, 10);
        queue.add(1, 5);
        queue.add(2, 5);
        queue.add(2, 7);

        // same priority is allowed of course
        // TODO is it good that same key is allowed (with same or different priority)?
        assertEquals(1, queue.poll());
        assertEquals(2, queue.poll());
        assertEquals(2, queue.poll());
        assertEquals(0, queue.poll());
    }

    @Test
    public void addMultiple() {
        GHPriorityQueue<Integer> queue = new GHPriorityQueue<>(10);
        Random random = new Random(0);
        int MAX_VAL = 1000;
        int MAX_SIZE = 10000;
        List<Double> list = new ArrayList<>();
        for (int i = 0; i < MAX_SIZE; i++) {
            double value = random.nextInt(MAX_VAL);
            queue.add(i, value);
            list.add(value);
        }
        Collections.sort(list);

        for (int i = 0; i < MAX_SIZE; i++) {
            double val = queue.peekPriority();
            assertEquals(list.get(i), val);
            queue.poll();
        }
    }

    @Test
    public void testAddAndPoll() {
        int N = 3000;
        GHPriorityQueue<SPTEntry> queue = new GHPriorityQueue<>(10);
        List<Double> list = new ArrayList<>();
        long seed = System.currentTimeMillis();
        Random rand = new Random(seed);
        for (int i = 0; i < N; i++) {
            double value = Math.abs(rand.nextDouble());
            queue.add(new SPTEntry(i, value), value);
            list.add(value);
        }
        Collections.sort(list);
        int counter = 0;
        while (!queue.isEmpty()) {
            double value = queue.poll().weight;
            assertEquals(list.get(counter), value, 0.0001, counter + ", seed: " + seed);
            counter++;
        }
    }

    @Test
    public void testAddAndRemoveAndPoll() {
        int N = 2000;
        GHPriorityQueue<SPTEntry> queue = new GHPriorityQueue<>(10);
        List<SPTEntry> list = new ArrayList<>();
        long seed = System.currentTimeMillis();
        Random rand = new Random(seed);
        for (int i = 0; i < N; i++) {
            long value = rand.nextInt();
            SPTEntry entry = new SPTEntry(i, value);
            queue.add(entry, value);
            list.add(entry);
        }
        Collections.sort(list);

        for (int i = 0; i < N / 20; i++) {
            int removeAtIndex = rand.nextInt(list.size());
            SPTEntry value = list.remove(removeAtIndex);
            assertTrue(queue.remove(value, value.weight), "seed : " + seed);
            assertEquals(queue.size(), list.size(), "seed: " + seed);
        }

        int counter = 0;
        while (!queue.isEmpty()) {
            SPTEntry value = queue.poll();
            assertEquals(list.get(counter), value, "seed: " + seed);
            counter++;
        }
    }

    @Test
    public void duplicateObjects() {
        PriorityQueue<SPTEntry> entry = new PriorityQueue<>();


    }

    // TODO NOW
//    @Test
//    public void testAddAndUpdateAndPoll() {
//        int N = 2000;
//        GHPriorityQueue queue = new GHPriorityQueue(10, N, Long.MAX_VALUE);
//        List<Long> list = new ArrayList<>();
//        long seed = System.currentTimeMillis();
//        Random rand = new Random(seed);
//        for (int i = 0; i < N; i++) {
//            long value = rand.nextInt();
//            queue.add(value);
//            list.add(value);
//        }
//        Collections.sort(list);
//        for (int i = 0; i < N / 20; i++) {
//            int removeAtIndex = rand.nextInt(list.size());
//            long value = list.remove(removeAtIndex);
//            long newElement = rand.nextInt();
//            list.add(newElement);
//            assertTrue(queue.update(value, newElement), "seed : " + seed);
//            assertEquals(queue.size(), list.size(), "seed: " + seed);
//        }
//        Collections.sort(list);
//
//        int counter = 0;
//        while (!queue.isEmpty()) {
//            long value = queue.pop();
//            assertEquals(list.get(counter), value, "seed: " + seed);
//            counter++;
//        }
//    }

}