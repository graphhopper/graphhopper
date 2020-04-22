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

/**
 * A priority queue that uses double for comparison (and not Comparable or Comparator). A ~20% faster poll leads to 10% faster A*.
 */
public class GHPriorityQueue<T> {
    double[] priorities;
    Object[] objects;
    int size;

    public GHPriorityQueue(int capacity) {
        int cap = Math.max(capacity, 64);
        priorities = new double[cap];
        objects = new Object[cap];
    }

    public void add(T object, double priority) {
        grow(++size);

        if (size > 1) {
            upHeap(size - 1, object, priority);
        } else {
            objects[0] = object;
            priorities[0] = priority;
        }
    }

    private void grow(int capacity) {
        if (capacity < objects.length)
            return;

        int newCap = (int) Math.round(capacity * 1.4);
        double[] oldPriorities = priorities;
        Object[] oldObjects = objects;
        priorities = new double[newCap];
        objects = new Object[newCap];

        System.arraycopy(oldPriorities, 0, priorities, 0, oldPriorities.length);
        System.arraycopy(oldObjects, 0, objects, 0, oldObjects.length);
    }

    private void upHeap(int index, Object object, double priority) {
        while (index > 0) {
            int parentIndex = index - 1 >>> 1;
            if (priorities[parentIndex] <= priority)
                break;

            objects[index] = objects[parentIndex];
            priorities[index] = priorities[parentIndex];
            index = parentIndex;
        }

        // we moved the other entries so we can place the new here
        objects[index] = object;
        priorities[index] = priority;
    }

    void downHeap(int index, Object object, double priority) {
        int half = size >>> 1;
        while (index < half) {
            int childIndex = (index << 1) + 1;
            Object childObject = objects[childIndex];
            double childPrio = priorities[childIndex];
            int right = childIndex + 1;
            if (right < size && childPrio > priorities[right]) {
                childObject = objects[childIndex = right];
                childPrio = priorities[childIndex];
            }

            if (priority <= childPrio)
                break;

            objects[index] = childObject;
            priorities[index] = childPrio;
            index = childIndex;
        }

        objects[index] = object;
        priorities[index] = priority;
    }

    public T poll() {
        if (this.size == 0)
            throw new IllegalArgumentException("Cannot pop from empty queue");

        size--;
        Object oldObject = objects[0];
        if (size != 0)
            // pick the last / large enough element and swap entries until it heap property is satisfied
            downHeap(0, objects[size], priorities[size]);

        return (T) oldObject;
    }

    public void remove(T object, double priority) {
        // TODO for large queues it could be beneficial to somehow use the priority and the heap property
        int index = -1;
        for (int i = 0; i < objects.length; i++) {
            if (objects[i] == object) {
                index = i;
                break;
            }
        }
        if (index < 0)
            throw new IllegalArgumentException("Cannot find element with object " + object + " in queue");

        if (--size == index) {
            // last element => nothing to do
        } else {
            Object swappedObject = objects[size];
            double movedPrio = priorities[size];
            downHeap(index, swappedObject, movedPrio);
            if (objects[index] == swappedObject)
                upHeap(index, swappedObject, movedPrio);
        }
    }

    public T peek() {
        return (T) objects[0];
    }

    public double peekPriority() {
        return priorities[0];
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int size() {
        return size;
    }
}
