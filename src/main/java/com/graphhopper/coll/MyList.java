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

import java.util.Arrays;

/**
 * Oh why can't I set the size of an ArrayList?
 *
 * @author Peter Karich
 */
public class MyList<T> {

    private T[] arr;
    private int size;

    public MyList() {
        this(10);
    }

    public MyList(int cap) {
        arr = (T[]) new Object[cap];
    }

    void ensureCapacity(int minSize) {
        if (minSize <= arr.length)
            return;

        int cap = Math.max(10, Math.round(minSize * 1.5f));
        T[] dest = (T[]) new Object[cap];
        System.arraycopy(arr, 0, dest, 0, size);
        arr = dest;
    }

    public void add(T el) {
        ensureCapacity(size + 1);
        arr[size] = el;
        size++;
    }

    public void add(int index, T el) {
        // no ensureCapacity to make sure the size variable is properly handled
        if (index >= size)
            throw new RuntimeException("When insert an element at " + index
                    + " make sure the size is at least " + (index + 1));

        System.arraycopy(arr, index, arr, index + 1, size - index);
        arr[index] = el;
        size++;
    }

    public T get(int index) {
        return arr[index];
    }

    public T remove(int index) {
        T el = arr[index];
        System.arraycopy(arr, index + 1, arr, index, size - index - 1);
        size--;
        return el;
    }

    /**
     * Sets the size accordingly and if toSize is too big then ensureCapacity. Problem: if current
     * size will be expanded the elements stay always 'null'!
     */
    private void trimTo(int toSize) {
        // TODO should we shrink the array if toSize is too low?        
        ensureCapacity(toSize);
        // let the gc do its work
        for (int i = toSize; i < size; i++) {
            arr[i] = null;
        }
        size = toSize;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void clear() {
        trimTo(0);
    }

    public int binSearch(T key) {
        return Arrays.binarySearch(arr, 0, size, key);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (; i < size; i++) {
            if (i > 0)
                sb.append(",");

            sb.append(arr[i]);
        }
        return sb.toString();

    }
}
