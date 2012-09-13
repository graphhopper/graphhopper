/*
 *  Copyright 2012 Peter Karich info@jetsli.de
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
package de.jetsli.graph.util;

import java.util.Arrays;

/**
 * push to end, pop from beginning
 *
 * @author Peter Karich, info@jetsli.de
 */
public class MyIntDeque {

    private int[] arr;
    private float factor;
    private int frontIndex;
    private int endIndexPlusOne;

    public MyIntDeque() {
        this(100, 2);
    }

    public MyIntDeque(int initSize) {
        this(initSize, 2);
    }

    public MyIntDeque(int initSize, float factor) {
        if ((int) (initSize * factor) <= initSize)
            throw new RuntimeException("initial size or increasing factor too low!");

        this.factor = factor;
        this.arr = new int[initSize];
    }

    int getCapacity() {
        return arr.length;
    }

    public void setFactor(float factor) {
        this.factor = factor;
    }

    public boolean isEmpty() {
        return frontIndex >= endIndexPlusOne;
    }

    public int pop() {
        int tmp = arr[frontIndex];
        frontIndex++;

        // removing the empty space of the front if too much is unused        
        int smallerSize = (int) (arr.length / factor);
        if (frontIndex > smallerSize) {
            endIndexPlusOne = size();
            // ensure that there are at least 10 entries
            int[] newArr = new int[endIndexPlusOne + 10];
            System.arraycopy(arr, frontIndex, newArr, 0, endIndexPlusOne);
            arr = newArr;
            frontIndex = 0;
        }

        return tmp;
    }

    public int size() {
        return endIndexPlusOne - frontIndex;
    }

    public void push(int v) {
        if (endIndexPlusOne >= arr.length)
            arr = Arrays.copyOf(arr, (int) (arr.length * factor));

        arr[endIndexPlusOne] = v;
        endIndexPlusOne++;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = frontIndex; i < endIndexPlusOne; i++) {
            if (i > frontIndex)
                sb.append(", ");
            sb.append(arr[i]);
        }
        return sb.toString();
    }
}
