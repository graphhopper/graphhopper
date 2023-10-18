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

import com.graphhopper.apache.commons.collections.IntFloatBinaryHeap;

import java.util.Arrays;

/**
 * A minimum heap implemented using a binary tree (https://en.wikipedia.org/wiki/Binary_heap). Besides the tree and the
 * elements' values this heap also keeps track of the positions of the elements in the tree.
 * This requires additional book-keeping when doing pushes/polls, but allows for an efficient update operation.
 * For the same reason the heap has a fixed memory size that is determined in the constructor and the inserted element
 * may not exceed a certain range.
 * todo: strictly speaking the heap could automatically grow/shrink as long as the range of legal ids stays fixed, but
 * for simplicity the heap has a fixed size for now.
 * <p>
 * This class is very similar to {@link IntFloatBinaryHeap}, but compared to this has an efficient update operation.
 * In turn it is (much) less memory-efficient when the heap is used for a small number of elements from a large range.
 */
public class MinHeapWithUpdate {
    private static final int NOT_PRESENT = -1;
    private final int[] tree;
    private final int[] positions;
    private final float[] vals;
    private final int max;
    private int size;

    /**
     * @param elements the number of elements that can be stored in this heap. Currently the heap cannot be resized or
     *                 shrunk/trimmed after initial creation. elements-1 is the maximum id that can be stored in this
     *                 heap
     */
    public MinHeapWithUpdate(int elements) {
        // we use an offset of one to make the arithmetic a bit simpler/more efficient, the 0th elements are not used!
        tree = new int[elements + 1];
        positions = new int[elements + 1];
        Arrays.fill(positions, NOT_PRESENT);
        vals = new float[elements + 1];
        vals[0] = Float.NEGATIVE_INFINITY;
        this.max = elements;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Adds an element to the heap, the given id must not exceed the size specified in the constructor. Its illegal
     * to push the same id twice (unless it was polled/removed before). To update the value of an id contained in the
     * heap use the {@link #update} method.
     */
    public void push(int id, float value) {
        checkIdInRange(id);
        if (size == max)
            throw new IllegalStateException("Cannot push anymore, the heap is already full. size: " + size);
        if (contains(id))
            throw new IllegalStateException("Element with id: " + id + " was pushed already, you need to use the update method if you want to change its value");
        size++;
        tree[size] = id;
        positions[id] = size;
        vals[size] = value;
        percolateUp(size);
    }

    /**
     * @return true if the heap contains an element with the given id
     */
    public boolean contains(int id) {
        checkIdInRange(id);
        return positions[id] != NOT_PRESENT;
    }

    /**
     * Updates the element with the given id. The complexity of this method is O(log(N)), just like push/poll.
     * Its illegal to update elements that are not contained in the heap. Use {@link #contains} to check the existence
     * of an id.
     */
    public void update(int id, float value) {
        checkIdInRange(id);
        int index = positions[id];
        if (index < 0)
            throw new IllegalStateException("The heap does not contain: " + id + ". Use the contains method to check this before calling update");
        float prev = vals[index];
        vals[index] = value;
        if (value > prev)
            percolateDown(index);
        else if (value < prev)
            percolateUp(index);
    }

    /**
     * @return the id of the next element to be polled, i.e. the same as calling poll() without removing the element
     */
    public int peekId() {
        return tree[1];
    }

    /**
     * @return the value of the next element to be polled
     */
    public float peekValue() {
        return vals[1];
    }

    /**
     * Extracts the element with minimum value from the heap
     */
    public int poll() {
        int id = peekId();
        tree[1] = tree[size];
        vals[1] = vals[size];
        positions[tree[1]] = 1;
        positions[id] = NOT_PRESENT;
        size--;
        percolateDown(1);
        return id;
    }

    public void clear() {
        for (int i = 1; i <= size; i++)
            positions[tree[i]] = NOT_PRESENT;
        size = 0;
    }

    private void percolateUp(int index) {
        assert index != 0;
        if (index == 1)
            return;
        final int el = tree[index];
        final float val = vals[index];
        // the finish condition (index==0) is covered here automatically because we set vals[0]=-inf
        while (val < vals[index >> 1]) {
            int parent = index >> 1;
            tree[index] = tree[parent];
            vals[index] = vals[parent];
            positions[tree[index]] = index;
            index = parent;
        }
        tree[index] = el;
        vals[index] = val;
        positions[tree[index]] = index;
    }

    private void percolateDown(int index) {
        if (size == 0)
            return;
        assert index > 0;
        assert index <= size;
        final int el = tree[index];
        final float val = vals[index];
        while (index << 1 <= size) {
            int child = index << 1;
            if (child != size && vals[child + 1] < vals[child])
                // use the second child if it exists and has a smaller value
                child++;
            if (vals[child] >= val)
                break;
            tree[index] = tree[child];
            vals[index] = vals[child];
            positions[tree[index]] = index;
            index = child;
        }
        tree[index] = el;
        vals[index] = val;
        positions[tree[index]] = index;
    }

    private void checkIdInRange(int id) {
        if (id < 0 || id >= max)
            throw new IllegalArgumentException("Illegal id: " + id + ", legal range: [0, " + max + "[");
    }

}
