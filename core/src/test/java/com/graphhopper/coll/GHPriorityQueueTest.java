package com.graphhopper.coll;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

}