package com.graphhopper.coll;

import java.util.Arrays;

/**
 * A priority queue that uses a double array for comparison and not via Comparable where we need to jump to the
 * object (using the reference in the array) before every comparison. Although there is an overhead due to two
 * arrays this leads to a ~20% faster poll which leads to ~10% faster A*.
 */
public class GHPriorityQueue<T> {
    private double[] priorities;
    private Object[] objects;
    private int size;

    public GHPriorityQueue(int capacity) {
        int cap = Math.max(capacity, 64);
        priorities = new double[cap];
        objects = new Object[cap];
    }

    public void add(T object, double priority) {
        grow(++size);
        upHeap(size - 1, object, priority);
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

    private void check() {
        if (size < objects.length && objects[size] != null)
            throw new IllegalStateException("Forgot to remove element. Possible memory leak");
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
            throw new IllegalArgumentException("Cannot poll from empty queue");

        size--;
        Object oldObject = objects[0];
        if (size != 0) {
            // pick the last / large enough element and swap entries until it heap property is satisfied
            downHeap(0, objects[size], priorities[size]);
        }
        objects[size] = null;
        check();
        return (T) oldObject;
    }

    /**
     * This method is slightly more efficient compared to priorityQueue.remove(value) and
     * priorityQueue.add(value, newPriority) but does the same.
     *
     * @return true if old entry was found
     * @param skipInsertIfNotFound the default behaviour should be true, but sometimes it is necessary to always add
     *                             the newPriority even if values wasn't found. See #2104
     */
    public boolean update(T value, double newPriority, boolean skipInsertIfNotFound) {
        // TODO for large queues it could be beneficial to somehow use the priority and the heap property
        int index = -1;
        for (int i = 0; i < objects.length; i++) {
            if (objects[i] == value) {
                index = i;
                break;
            }
        }

        if (index < 0) {
            if (!skipInsertIfNotFound)
                add(value, newPriority);
            return false;
        }

        if (index - 1 == size) {
            // now we can just insert and let the last element being overwritten from upHeap
            upHeap(index, value, newPriority);
        } else {
            downHeap(index, value, newPriority);
            // if it couldn't move down try to move it up
            if (objects[index] == value)
                upHeap(index, value, newPriority);
        }

        return true;
    }

    /**
     * This method removes the specified object based on its reference equality i.e. == is used.
     */
    public boolean remove(T object) {
        int index = -1;
        for (int i = 0; i < objects.length; i++) {
            if (objects[i] == object) {
                index = i;
                break;
            }
        }

        if (index < 0)
            return false;

        if (--size == index) {
            // last element => nothing to do
        } else {
            Object swappedObject = objects[size];
            double movedPrio = priorities[size];
            downHeap(index, swappedObject, movedPrio);
            // if it couldn't move down try to move it up
            if (objects[index] == swappedObject)
                upHeap(index, swappedObject, movedPrio);
        }
        objects[size] = null;
        check();
        return true;
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

    @Override
    public String toString() {
        return "size=" + size + ", objects=" + Arrays.toString(Arrays.copyOf(objects, size));
    }
}
