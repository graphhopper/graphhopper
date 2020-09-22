package com.graphhopper.apache.commons.collections;

import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.coll.BinaryHeapTestInterface;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Peter Karich
 * @author easbar
 */
public class IntFloatBinaryHeapTest implements BinaryHeapTestInterface {

    private IntFloatBinaryHeap heap;

    @Override
    public void create(int capacity) {
        heap = new IntFloatBinaryHeap(capacity);
    }

    @Override
    public int size() {
        return heap.getSize();
    }

    @Override
    public boolean isEmpty() {
        return heap.isEmpty();
    }

    @Override
    public void push(int id, float val) {
        heap.insert(val, id);
    }

    @Override
    public int peekId() {
        return heap.peekElement();
    }

    @Override
    public float peekVal() {
        return heap.peekKey();
    }

    @Override
    public void update(int id, float val) {
        heap.update(val, id);
    }

    @Override
    public int poll() {
        return heap.poll();
    }

    @Override
    public void clear() {
        heap.clear();
    }

    @Test
    public void growIfNeeded() {
        create(3);
        push(4, 1.6f);
        push(8, 1.8f);
        push(12, 0.7f);
        push(5, 1.2f);
        assertEquals(4, size());
        IntArrayList elements = new IntArrayList();
        while (!isEmpty()) {
            elements.add(poll());
        }
        assertEquals(IntArrayList.from(12, 5, 4, 8), elements);
    }
}
