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

import com.carrotsearch.hppc.cursors.IntCursor;

import java.util.Iterator;

/**
 * Implements the bitset interface via a hash set. It is more efficient for only a few entries.
 * <p>
 *
 * @author Peter Karich
 */
public class GHTBitSet implements GHBitSet {
    private final GHIntHashSet tHash;

    public GHTBitSet(GHIntHashSet set) {
        tHash = set;
    }

    public GHTBitSet(int no) {
        tHash = new GHIntHashSet(no, 0.7f);
    }

    public GHTBitSet() {
        this(1000);
    }

    @Override
    public final boolean contains(int index) {
        return tHash.contains(index);
    }

    @Override
    public final void add(int index) {
        tHash.add(index);
    }

    @Override
    public final String toString() {
        return tHash.toString();
    }

    @Override
    public final int getCardinality() {
        return tHash.size();
    }

    @Override
    public final void clear() {
        tHash.clear();
    }

    @Override
    public void remove(int index) {
        tHash.remove(index);
    }

    @Override
    public final GHBitSet copyTo(GHBitSet bs) {
        bs.clear();
        if (bs instanceof GHTBitSet) {
            ((GHTBitSet) bs).tHash.addAll(this.tHash);
        } else {
            Iterator<IntCursor> iter = tHash.iterator();
            while (iter.hasNext()) {
                bs.add(iter.next().value);
            }
        }
        return bs;
    }

    @Override
    public int next(int index) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
