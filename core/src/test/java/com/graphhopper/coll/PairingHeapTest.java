package com.graphhopper.coll;

import com.graphhopper.storage.SPTEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PairingHeapTest {


    @Test
    public void add() {
        PairingHeap queue = new PairingHeap();
        queue.add(new SPTEntry(0, 10));
        queue.add(new SPTEntry(1, 5));
        queue.add(new SPTEntry(2, 20));
        queue.add(new SPTEntry(3, 7));
        queue.add(new SPTEntry(4, 17));

        assertEquals(5, queue.poll().weight, .1);
        assertEquals(7, queue.poll().weight, .1);
        assertEquals(10, queue.poll().weight, .1);
        assertEquals(17, queue.poll().weight, .1);
        assertEquals(20, queue.poll().weight, .1);
    }

    @Test
    public void addDuplicate() {
        PairingHeap queue = new PairingHeap();
        queue.add(new SPTEntry(0, 10));
        queue.add(new SPTEntry(1, 5));
        queue.add(new SPTEntry(2, 5));
        queue.add(new SPTEntry(3, 7));

        assertEquals(5, queue.poll().weight, .1);
        assertEquals(5, queue.poll().weight, .1);
        assertEquals(7, queue.poll().weight, .1);
        assertEquals(10, queue.poll().weight, .1);
    }

    @Test
    public void addMultiple() {
        PairingHeap queue = new PairingHeap();
        Random random = new Random(0);
        int MAX_VAL = 1000;
        int MAX_SIZE = 10000;
        List<Double> list = new ArrayList<>();
        for (int i = 0; i < MAX_SIZE; i++) {
            double value = random.nextInt(MAX_VAL);
            queue.add(new SPTEntry(i, value));
            list.add(value);
        }
        Collections.sort(list);

        for (int i = 0; i < MAX_SIZE; i++) {
            SPTEntry val = queue.poll();
            assertEquals(list.get(i), val.weight, .1);
        }
    }
}