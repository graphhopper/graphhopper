/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */
package de.jetsli.graph.coll;

import java.util.Arrays;

/**
 * taken from opentripplanner. Note: LGPL 3 is compatible with Apache, where as LGPL 2 was not!
 */
public class IntBinHeap {

    private static final double GROW_FACTOR = 2.0;
    private float[] prio;
    private int[] elem;
    private int size;
    private int capacity;

    public IntBinHeap() {
        this(1000);
    }

    public IntBinHeap(int capacity) {
        if (capacity < 10)
            capacity = 10;
        this.capacity = capacity;
        size = 0;
        elem = new int[capacity + 1];
        // 1-based indexing
        prio = new float[capacity + 1];
        // set sentinel
        prio[0] = Float.NEGATIVE_INFINITY;
    }

    public int size() {
        return size;
    }
    
    public boolean isEmpty() {
        return size == 0;
    }

    public boolean empty() {
        return size <= 0;
    }

    public double peekMinPriority() {
        if (size > 0)
            return prio[1];
        else
            throw new IllegalStateException("An empty queue does not have a minimum key.");
    }

    public int peekMinElement() {
        if (size > 0)
            return elem[1];
        else
            throw new IllegalStateException("An empty queue does not have a minimum value.");
    }

    public void rekey(int e, double p) {
        // Perform "inefficient" but straightforward linear search 
        // for an element then change its key by sifting up or down
        int i;
        for (i = 1; i <= size; i++) {
            if (elem[i] == e)
                break;
        }
        if (i > size) {
            //System.out.printf("did not find element %s\n", e);
            return;
        }
        //System.out.printf("found element %s with key %f at %d\n", e, prio[i], i);
        if (p > prio[i]) {
            // sift up (as in extract)
            while (i * 2 <= size) {
                int child = i * 2;
                if (child != size && prio[child + 1] < prio[child])
                    child++;
                if (p > prio[child]) {
                    elem[i] = elem[child];
                    prio[i] = prio[child];
                    i = child;
                } else
                    break;
            }
            elem[i] = e;
            prio[i] = (float) p;
        } else {
            // sift down (as in insert)
            while (prio[i / 2] > p) {
                elem[i] = elem[i / 2];
                prio[i] = prio[i / 2];
                i /= 2;
            }
            elem[i] = e;
            prio[i] = (float) p;
        }
    }

    public void dump() {
        for (int i = 0; i <= capacity; i++) {
            String topMarker = (i > size) ? "(UNUSED)" : "";
            System.out.printf("%d\t%f\t%s\t%s\n", i, prio[i], elem[i], topMarker);
        }
        System.out.printf("-----------------------\n");
    }

    public void reset() {
        // empties the queue in one operation
        size = 0;
    }

    public void insert(int e, double p) {
        int i;
        size += 1;
        if (size > capacity)
            resize((int) (capacity * GROW_FACTOR));
        for (i = size; prio[i / 2] > p; i /= 2) {
            elem[i] = elem[i / 2];
            prio[i] = prio[i / 2];
        }
        elem[i] = e;
        prio[i] = (float) p;
    }

    public int extractMin() {
        int i, child;
        int minElem = elem[1];
        int lastElem = elem[size];
        double lastPrio = prio[size];
        if (size <= 0)
            throw new IllegalStateException("An empty queue does not have a minimum value.");
        size -= 1;
        for (i = 1; i * 2 <= size; i = child) {
            child = i * 2;
            if (child != size && prio[child + 1] < prio[child])
                child++;
            if (lastPrio > prio[child]) {
                elem[i] = elem[child];
                prio[i] = prio[child];
            } else
                break;
        }
        elem[i] = lastElem;
        prio[i] = (float) lastPrio;
        return minElem;
    }

    public void resize(int capacity) {
        // System.out.println("Growing queue to " + capacity);
        if (capacity < size)
            throw new IllegalStateException("BinHeap contains too many elements to fit in new capacity.");
        this.capacity = capacity;
        prio = Arrays.copyOf(prio, capacity + 1);
        elem = Arrays.copyOf(elem, capacity + 1);
    }
    
    public void clear() {
        this.size = 0;
        Arrays.fill(prio, 0f);
        Arrays.fill(elem, 0);
    }
}