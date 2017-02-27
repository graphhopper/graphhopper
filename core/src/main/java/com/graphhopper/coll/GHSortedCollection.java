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

import com.carrotsearch.hppc.LongArrayList;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.carrotsearch.hppc.predicates.IntPredicate;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * A priority queue implemented by a treemap to allow fast key update. Or should we use a standard
 * b-tree?
 * <p>
 *
 * @author Peter Karich
 */
public class GHSortedCollection {
    private final int slidingMeanValue = 20;
    private final TreeMap<Integer, GHIntHashSet> map;
    private int size;

    public GHSortedCollection() {
        // use size as indicator for maxEntries => try radix sort?
        map = new TreeMap<Integer, GHIntHashSet>();
    }

    public void clear() {
        size = 0;
        map.clear();
    }

    void remove(int key, int value) {
        GHIntHashSet set = map.get(value);
        if (set == null || !set.remove(key)) {
            throw new IllegalStateException("cannot remove key " + key + " with value " + value
                    + " - did you insert " + key + "," + value + " before?");
        }
        size--;
        if (set.isEmpty()) {
            map.remove(value);
        }
    }

    public void update(int key, int oldValue, int value) {
        remove(key, oldValue);
        insert(key, value);
    }

    public void insert(int key, int value) {
        GHIntHashSet set = map.get(value);
        if (set == null) {
            map.put(value, set = new GHIntHashSet(slidingMeanValue));
        }
//        else
//            slidingMeanValue = Math.max(5, (slidingMeanValue + set.size()) / 2);
        if (!set.add(key)) {
            throw new IllegalStateException("use update if you want to update " + key);
        }
        size++;
    }

    public int peekValue() {
        if (size == 0) {
            throw new IllegalStateException("collection is already empty!?");
        }
        Entry<Integer, GHIntHashSet> e = map.firstEntry();
        if (e.getValue().isEmpty()) {
            throw new IllegalStateException("internal set is already empty!?");
        }
        return map.firstEntry().getKey();
    }

    public int peekKey() {
        if (size == 0) {
            throw new IllegalStateException("collection is already empty!?");
        }
        GHIntHashSet set = map.firstEntry().getValue();
        if (set.isEmpty()) {
            throw new IllegalStateException("internal set is already empty!?");
        }
        return set.iterator().next().value;
    }

    /**
     * @return removes the smallest entry (key and value) from this collection
     */
    public int pollKey() {
        size--;
        if (size < 0) {
            throw new IllegalStateException("collection is already empty!?");
        }

        Entry<Integer, GHIntHashSet> e = map.firstEntry();
        GHIntHashSet set = e.getValue();
        if (set.isEmpty()) {
            throw new IllegalStateException("internal set is already empty!?");
        }

        Iterator<IntCursor> iter = set.iterator();
        final int val = iter.next().value;
        set.remove(val);
        if (set.isEmpty()) {
            map.remove(e.getKey());
        }
        return val;
    }

    public int getSize() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int getSlidingMeanValue() {
        return slidingMeanValue;
    }

    @Override
    public String toString() {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (Entry<Integer, GHIntHashSet> e : map.entrySet()) {
            int tmpSize = e.getValue().size();
            if (min > tmpSize) {
                min = tmpSize;
            }
            if (max < tmpSize) {
                max = tmpSize;
            }
        }
        String str = "";
        if (!isEmpty()) {
            str = ", minEntry=(" + peekKey() + "=>" + peekValue() + ")";
        }
        return "size=" + size + ", treeMap.size=" + map.size()
                + ", averageNo=" + size * 1f / map.size()
                + ", minNo=" + min + ", maxNo=" + max + str;
    }
}
