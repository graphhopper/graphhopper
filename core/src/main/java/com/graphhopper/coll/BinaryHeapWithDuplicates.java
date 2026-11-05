package com.graphhopper.coll;

import java.util.Arrays;

/**
 * A binary min-heap that stores items with parallel double[] weights for cache-friendly comparisons.
 * Unlike java.util.PriorityQueue which dereferences Object pointers during sift operations,
 * this heap compares weights from a contiguous double[] array, dramatically reducing cache misses.
 */
public class BinaryHeapWithDuplicates<E> {
    private double[] weights;
    private Object[] items;
    private int size;

    public BinaryHeapWithDuplicates() { this(64); }
    public BinaryHeapWithDuplicates(int initialCapacity) {
        initialCapacity = Math.max(1, initialCapacity);
        weights = new double[initialCapacity];
        items = new Object[initialCapacity];
    }

    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }

    public void add(E item, double weight) {
        if (size == weights.length) grow();
        weights[size] = weight;
        items[size] = item;
        siftUp(size);
        size++;
    }

    @SuppressWarnings("unchecked")
    public E poll() {
        if (size == 0) return null;
        E result = (E) items[0];
        size--;
        if (size > 0) {
            weights[0] = weights[size];
            items[0] = items[size];
            items[size] = null;
            siftDown(0);
        } else {
            items[0] = null;
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public E peek() { return size > 0 ? (E) items[0] : null; }

    public void clear() { Arrays.fill(items, 0, size, null); size = 0; }

    private void siftUp(int pos) {
        double w = weights[pos]; Object item = items[pos];
        while (pos > 0) {
            int parent = (pos - 1) >>> 1;
            if (w >= weights[parent]) break;
            weights[pos] = weights[parent]; items[pos] = items[parent];
            pos = parent;
        }
        weights[pos] = w; items[pos] = item;
    }

    private void siftDown(int pos) {
        double w = weights[pos]; Object item = items[pos];
        int half = size >>> 1;
        while (pos < half) {
            int child = (pos << 1) + 1, right = child + 1;
            if (right < size && weights[right] < weights[child]) child = right;
            if (w <= weights[child]) break;
            weights[pos] = weights[child]; items[pos] = items[child];
            pos = child;
        }
        weights[pos] = w; items[pos] = item;
    }

    private void grow() {
        int nc = weights.length + (weights.length >> 1) + 1;
        weights = Arrays.copyOf(weights, nc);
        items = Arrays.copyOf(items, nc);
    }
}
