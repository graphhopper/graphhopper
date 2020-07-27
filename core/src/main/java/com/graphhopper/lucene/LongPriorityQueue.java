/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.graphhopper.lucene;

import java.util.Arrays;

/**
 * A native long priority queue.
 */
public class LongPriorityQueue {
    protected int size;             // number of elements currently in the queue
    protected int currentCapacity;  // number of elements the queue can hold w/o expanding
    protected int maxSize;          // max number of elements allowed in the queue
    protected long[] heap;          // the first element is empty, so this collection is 1-based!?
    protected final long sentinel;  // represents a null return value

    public LongPriorityQueue(int initialSize, int maxSize, long sentinel) {
        this.maxSize = maxSize;
        this.sentinel = sentinel;
        initialize(initialSize);
    }


    protected void initialize(int sz) {
        int heapSize;
        if (0 == sz)
            // We allocate 1 extra to avoid if statement in top()
            heapSize = 2;
        else {
            // NOTE: we add +1 because all access to heap is
            // 1-based not 0-based.  heap[0] is unused.
            heapSize = Math.max(sz, sz + 1); // handle overflow
        }
        heap = new long[heapSize];
        currentCapacity = sz;
    }

    public int getCurrentCapacity() {
        return currentCapacity;
    }

    public void resize(int sz) {
        int heapSize;
        if (sz > maxSize) {
            maxSize = sz;
        }
        if (0 == sz)
            // We allocate 1 extra to avoid if statement in top()
            heapSize = 2;
        else {
            heapSize = Math.max(sz, sz + 1); // handle overflow
        }
        heap = Arrays.copyOf(heap, heapSize);
        currentCapacity = sz;
    }

    /**
     * Adds an object to a PriorityQueue in log(size) time. If one tries to add
     * more objects than maxSize from initialize an
     * {@link ArrayIndexOutOfBoundsException} is thrown.
     *
     * @return the new 'top' element in the queue.
     */
    public long add(long element) {
        if (size >= currentCapacity) {
            int newSize = Math.min(currentCapacity << 1, maxSize);
            if (newSize < currentCapacity) newSize = Integer.MAX_VALUE;  // handle overflow
            resize(newSize);
        }
        size++;
        heap[size] = element;
        upHeap();
        return heap[1];
    }

    /**
     * Instead of remove(oldElement) and add(newElement) this method is slightly optimized and should preferred.
     */
    public boolean update(long oldElement, long newElement) {
        for (int i = 1; i <= size; i++) {
            if (heap[i] == oldElement) {
                if (i < size) {
                    // fill gap with the new element
                    downHeap(i, newElement);
                    // if it wasn't moved downwards try moving it upwards
                    if (heap[i] == newElement)
                        upHeap(i, newElement);

                } else {
                    // i == size
                    heap[size] = newElement;
                    upHeap();
                }
                return true;
            }
        }
        return false;
    }

    public boolean remove(long element) {
        for (int i = 1; i <= size; i++) {
            if (heap[i] == element) {
                if (i < size) {
                    // fill gap with last element (move downwards from i to end if necessary)
                    long lastElement = heap[size];
                    size--;
                    downHeap(i, lastElement);
                    // if lastElement wasn't moved downwards try moving it upwards
                    if (heap[i] == lastElement)
                        upHeap(i, lastElement);

                } else {
                    // it was the last element so no need to correct heap
                    // heap[size] = sentinel;
                    size--;
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Adds an object to a PriorityQueue in log(size) time. If one tries to add
     * more objects than the current capacity, an
     * {@link ArrayIndexOutOfBoundsException} is thrown.
     */
    public void addNoCheck(long element) {
        ++size;
        heap[size] = element;
        upHeap();
    }

    /**
     * Adds an object to a PriorityQueue in log(size) time.
     * It returns the smallest object (if any) that was
     * dropped off the heap because it was full, or
     * the sentinel value.
     * <p>
     * This can be
     * the given parameter (in case it is smaller than the
     * full heap's minimum, and couldn't be added), or another
     * object that was previously the smallest value in the
     * heap and now has been replaced by a larger one, or null
     * if the queue wasn't yet full with maxSize elements.
     */
    public long insertWithOverflow(long element) {
        if (size < maxSize) {
            add(element);
            return sentinel;
        } else if (element > heap[1]) {
            long ret = heap[1];
            heap[1] = element;
            updateTop();
            return ret;
        } else {
            return element;
        }
    }

    /**
     * inserts the element and returns true if this element caused another element
     * to be dropped from the queue.
     */
    public boolean insert(long element) {
        if (size < maxSize) {
            add(element);
            return false;
        } else if (element > heap[1]) {
            // long ret = heap[1];
            heap[1] = element;
            updateTop();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Returns the least element of the PriorityQueue in constant time.
     */
    public long top() {
        return heap[1];
    }

    /**
     * Removes and returns the least element of the PriorityQueue in log(size)
     * time.  Only valid if size() &gt; 0.
     */
    public long pop() {
        long result = heap[1];            // save first value
        heap[1] = heap[size];            // move last to first
        size--;
        downHeap();          // adjust heap
        return result;
    }

    /**
     * Should be called when the Object at top changes values.
     *
     * @return the new 'top' element.
     */
    public long updateTop() {
        downHeap();
        return heap[1];
    }

    /**
     * Returns the number of elements currently stored in the PriorityQueue.
     */
    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Pops the smallest n items from the heap, placing them in the internal array at
     * arr[size] through arr[size-(n-1)] with the smallest (first element popped)
     * being at arr[size].  The internal array is returned.
     */
    public long[] sort(int n) {
        while (--n >= 0) {
            long result = heap[1];            // save first value
            heap[1] = heap[size];            // move last to first
            heap[size] = result;                  // place it last
            size--;
            downHeap();          // adjust heap
        }
        return heap;
    }

    /**
     * Removes all entries from the PriorityQueue.
     */
    public void clear() {
        size = 0;
    }

    private void upHeap() {
        upHeap(size, heap[size]);
    }

    private void upHeap(int i, long node) {
        int j = i >>> 1;
        while (j > 0 && node < heap[j]) {
            heap[i] = heap[j];        // shift parents down
            i = j;
            j = j >>> 1;
        }
        heap[i] = node;          // install saved node
    }

    private void downHeap() {
        downHeap(1, heap[1]);
    }

    private void downHeap(int i, long node) {
        int j = i << 1;          // find smaller child
        int k = j + 1;
        if (k <= size && heap[k] < heap[j]) {
            j = k;
        }
        while (j <= size && heap[j] < node) {
            heap[i] = heap[j];        // shift up child
            i = j;
            j = i << 1;
            k = j + 1;
            if (k <= size && heap[k] < heap[j]) {
                j = k;
            }
        }
        heap[i] = node;          // install saved node
    }
}
