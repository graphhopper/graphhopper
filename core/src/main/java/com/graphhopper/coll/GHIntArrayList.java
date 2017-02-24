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

import com.carrotsearch.hppc.IntArrayList;

import java.util.Random;

/**
 * @author Peter Karich
 */
public class GHIntArrayList extends IntArrayList {
    public GHIntArrayList() {
        super(10);
    }

    public GHIntArrayList(int capacity) {
        super(capacity);
    }

    public GHIntArrayList(GHIntArrayList list) {
        super(list);
    }

    public final GHIntArrayList reverse() {
        final int[] buffer = this.buffer;
        int tmp;
        for (int start = 0, end = size() - 1; start < end; start++, end--) {
            // swap the values
            tmp = buffer[start];
            buffer[start] = buffer[end];
            buffer[end] = tmp;
        }
        return this;
    }

    public final GHIntArrayList fill(final int max, final int value) {
        // TODO fill 100 then copy using System.arraycopy, then duplicate this via copying again etc
        for (int i = 0; i < max; i++) {
            add(value);
        }
        return this;
    }

    public final GHIntArrayList shuffle(Random random) {
        int[] buffer = this.buffer;
        int max = size();
        int maxHalf = max / 2;
        for (int x1 = 0; x1 < maxHalf; x1++) {
            int x2 = random.nextInt(maxHalf) + maxHalf;
            int tmp = buffer[x1];
            buffer[x1] = buffer[x2];
            buffer[x2] = tmp;
        }
        return this;
    }

    public static GHIntArrayList from(int... elements) {
        final GHIntArrayList list = new GHIntArrayList(elements.length);
        list.add(elements);
        return list;
    }
}
