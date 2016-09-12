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

import com.graphhopper.util.BitUtil;

import java.util.TreeMap;

/**
 * A priority queue implemented by a TreeMap. As the tree map does not allow duplicated we compose
 * the key via priority | nodeId.
 * <p>
 *
 * @author Peter Karich
 */
public class GHTreeMapComposed {
    private static final Integer NOT_EMPTY = new Integer(-3);
    private final BitUtil bitUtil = BitUtil.BIG;
    private final TreeMap<Long, Integer> map;

    public GHTreeMapComposed() {
        map = new TreeMap<Long, Integer>();
    }

    public void clear() {
        map.clear();
    }

    void remove(int key, int value) {
        long v = bitUtil.toLong(value, key);
        if (!map.remove(v).equals(NOT_EMPTY)) {
            throw new IllegalStateException("cannot remove key " + key + " with value " + value
                    + " - did you insert " + key + "," + value + " before?");
        }
    }

    public void update(int key, int oldValue, int value) {
        remove(key, oldValue);
        insert(key, value);
    }

    public void insert(int key, int value) {
        long v = bitUtil.toLong(value, key);
        map.put(v, NOT_EMPTY);
    }

    public int peekValue() {
        long key = map.firstEntry().getKey();
        return (int) (key >> 32);
    }

    public int peekKey() {
        long key = map.firstEntry().getKey();
        return (int) (key & 0xFFFFFFFFL);
    }

    /**
     * @return removes the smallest entry (key and value) from this collection
     */
    public int pollKey() {
        if (map.isEmpty())
            throw new IllegalStateException("Cannot poll collection is empty!");

        long key = map.pollFirstEntry().getKey();
        return (int) (key & 0xFFFFFFFFL);
    }

    public int getSize() {
        return map.size();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public String toString() {
        return map.toString();
    }
}
