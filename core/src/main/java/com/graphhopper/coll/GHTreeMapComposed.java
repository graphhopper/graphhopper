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

import java.util.Comparator;
import java.util.TreeMap;

/**
 * A priority queue for integer-float key-value pairs implemented by a TreeMap. As the tree map does not allow multiple
 * values for the same key we store the value inside the key which is composed as value | key.
 * <p>
 *
 * @author Peter Karich
 */
public class GHTreeMapComposed {
    private static final Integer NOT_EMPTY = -3;
    private final BitUtil bitUtil = BitUtil.BIG;
    private final TreeMap<Long, Integer> map;

    public GHTreeMapComposed() {
        map = new TreeMap<>(new Comparator<Long>() {
            //  we cannot just use the long sorting because the values are floats
            @Override
            public int compare(Long o1, Long o2) {
                // for two entries to be equal both value and key must be equal
                if (o1.equals(o2)) return 0;
                int value1 = bitUtil.getIntHigh(o1);
                int value2 = bitUtil.getIntHigh(o2);
                if (value1 == value2) {
                    // we enforce a deterministic order by looking at the size of the key (although there is no real
                    // reason to prefer one entry over the other)
                    int key1 = bitUtil.getIntLow(o1);
                    int key2 = bitUtil.getIntLow(o2);
                    if (key1 == key2) return 0;
                    return key1 < key2 ? -1 : 1;
                }
                float f1 = Float.intBitsToFloat(value1);
                float f2 = Float.intBitsToFloat(value2);
                return Float.compare(f1, f2);
            }
        });
    }

    public void clear() {
        map.clear();
    }

    void remove(int key, float value) {
        long v = bitUtil.toLong(Float.floatToRawIntBits(value), key);
        Integer prev = map.remove(v);
        if (prev == null) {
            throw new IllegalStateException("cannot remove key " + key + " with value " + value
                    + " - did you insert this key with this value before ?");
        }
    }

    public void update(int key, float oldValue, float value) {
        remove(key, oldValue);
        insert(key, value);
    }

    public void insert(int key, float value) {
        long v = bitUtil.toLong(Float.floatToRawIntBits(value), key);
        map.put(v, NOT_EMPTY);
    }

    public float peekValue() {
        long key = map.firstEntry().getKey();
        return Float.intBitsToFloat(bitUtil.getIntHigh(key));
    }

    public int peekKey() {
        long key = map.firstEntry().getKey();
        return bitUtil.getIntLow(key);
    }

    /**
     * @return removes the smallest entry (key and value) from this collection
     */
    public int pollKey() {
        if (map.isEmpty())
            throw new IllegalStateException("Cannot poll collection is empty!");

        long key = map.pollFirstEntry().getKey();
        return bitUtil.getIntLow(key);
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
