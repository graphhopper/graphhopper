/*
 *  Copyright 2012 Peter Karich
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
package com.graphhopper.coll;

/**
 * Several papers regarding queues are interesting:
 *
 * "Priority Queues and Dijkstra’s Algorithm" http://www.cs.utexas.edu/~shaikat/papers/TR-07-54.pdf
 * -> 2007, Chen et al, Auxiliary Buffer Heap, also the idea of a dijkstra with no decrement key is
 * shown
 *
 * "Fast Priority Queues for Cached Memory" http://algo2.iti.kit.edu/sanders/papers/falenex.ps.gz ->
 * 1998, Peter Sanders, sequence heap and tuned other heaps
 *
 * "Revisiting priority queues for image analysis"
 * http://www.cb.uu.se/~cris/Documents/j.patcog.2010.04.002_preprint.pdf -> 2010, Hendriks, ladder
 * queue
 *
 * @author Peter Karich
 */
public class MyDijkstraHeap implements BinHeapWrapper<Number, Integer> {

    private IntDoubleBinHeap smallHeap;
    private IntDoubleBinHeap midHeap;
    private IntDoubleBinHeap largeHeap;
    private double noKey = Double.MAX_VALUE;
    private double midMin = noKey, largeMin = noKey;
    private int smallCapacity, midCapacity;
    private int size;
    private int underflows = 0;
    private int overflows = 0;

    public MyDijkstraHeap() {
        this(128);
    }

    public MyDijkstraHeap(int cap) {
        if (cap < 100)
            cap = 100;
        smallHeap = new IntDoubleBinHeap(smallCapacity = cap / 16);
        midHeap = new IntDoubleBinHeap(midCapacity = cap / 4);
        largeHeap = new IntDoubleBinHeap(cap);
    }

    @Override
    public void update(Number key, Integer element) {
        update_(key.doubleValue(), element);
    }

    public void update_(double key, int element) {
        if (key >= midMin) {
            if (key >= largeMin) {
                largeHeap.update_(key, element);
            } else {
                midHeap.update_(key, element);
            }
        } else {
            smallHeap.update_(key, element);
        }
    }

    @Override
    public void insert(Number key, Integer element) {
        insert_(key.doubleValue(), element);
    }
//    int tmp = 0;

    public void insert_(double key, int element) {
//        tmp++;
//        if (tmp % 10000 == 0)
//            System.out.println(tmp + " " + stats());
        // 1. find out the correct heap
        if (key >= midMin) {
            if (key >= largeMin) {
                largeHeap.insert_(key, element);
            } else {
                if (handleMidOverflow()) {
                    insert_(key, element);
                    return;
                }
                midHeap.insert(key, element);
            }
        } else {
            if (handleSmallOverflow()) {
                insert_(key, element);
                return;
            }

            smallHeap.insert_(key, element);
        }

        size++;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public Integer peekElement() {
        return peek_element();
    }

    public int peek_element() {
        handleSmallUnderflow();
        return smallHeap.peek_element();
    }

    @Override
    public Number peekKey() {
        return peek_key();
    }

    public double peek_key() {
        handleSmallUnderflow();
        return smallHeap.peek_key();
    }

    @Override
    public Integer pollElement() {
        return poll_element();
    }

    public int poll_element() {
        handleSmallUnderflow();
        int el = smallHeap.poll_element();
        size--;
        return el;
    }

    @Override
    public void clear() {
        smallHeap.clear();
        midHeap.clear();
        largeHeap.clear();
        size = 0;
    }

    @Override
    public void ensureCapacity(int size) {
        size = size - (smallHeap.getCapacity() + midHeap.getCapacity());
        if (size < 0)
            return;
        largeHeap.ensureCapacity(size);
    }

    private boolean handleSmallUnderflow() {
        if (!smallHeap.isEmpty())
            return false;

        handleMidUnderflow();
        for (int i = 0; !midHeap.isEmpty() && i < smallCapacity; i++) {
            double key = midHeap.peek_key();
            int el = midHeap.poll_element();
            smallHeap.insert_(key, el);
        }
        if (midHeap.isEmpty())
            midMin = noKey;
        else
            midMin = midHeap.peek_key();
        underflows++;
        return true;
    }

    private boolean handleSmallOverflow() {
        if (smallHeap.size() < smallCapacity)
            return false;

        handleMidOverflow();
        smallHeap = move(smallCapacity, smallHeap, midHeap);
        if (midHeap.isEmpty())
            throw new IllegalStateException("mid heap wasn't filled with data from small heap!?");
        midMin = midHeap.peek_key();
        overflows++;
        return true;
    }

    private boolean handleMidUnderflow() {
        if (!midHeap.isEmpty())
            return false;

        for (int i = 0; !largeHeap.isEmpty() && i < midCapacity; i++) {
            double key = largeHeap.peek_key();
            int el = largeHeap.poll_element();
            midHeap.insert_(key, el);
        }
        if (midHeap.isEmpty())
            midMin = noKey;
        else
            midMin = midHeap.peek_key();
        if (largeHeap.isEmpty())
            largeMin = noKey;
        else
            largeMin = largeHeap.peek_key();
        underflows++;
        return true;
    }

    private boolean handleMidOverflow() {
        if (midHeap.size() < midCapacity)
            return false;

        midHeap = move(midCapacity, midHeap, largeHeap);
        if (largeHeap.isEmpty())
            throw new IllegalStateException("large heap wasn't filled with data from large heap!?");
        midMin = midHeap.peek_key();
        largeMin = largeHeap.peek_key();
        overflows++;
        return true;
    }

    static IntDoubleBinHeap move(int capacity, IntDoubleBinHeap from, IntDoubleBinHeap to) {
        // TODO can we improve performance?

        // put the smaller values into the 'newFrom' heap
        IntDoubleBinHeap newFrom = new IntDoubleBinHeap(capacity);
        int max = capacity / 2;
        for (int i = 0; i < max; i++) {
            double key = from.peek_key();
            int el = from.poll_element();
            newFrom.insert_(key, el);
        }

        // ... and the remaining one into the 'to' heap
        while (!from.isEmpty()) {
            double key = from.peek_key();
            int el = from.poll_element();
            to.insert_(key, el);
        }

        return newFrom;
    }

    public String stats() {
        return "size:" + size()
                + ", smallSize: " + smallHeap.size() + "(" + smallHeap.getCapacity() + ")"
                + ", midSize: " + midHeap.size() + "(" + midHeap.getCapacity() + ")"
                + ", largeSize: " + largeHeap.size() + "(" + largeHeap.getCapacity() + ")"
                + ", overflows:" + overflows + ", underflows:" + underflows;
    }
}
