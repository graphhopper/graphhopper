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
package de.jetsli.graph.coll;

import de.jetsli.lucene.DocIdSetIterator;
import de.jetsli.lucene.OpenBitSet;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public class MyOpenBitSet implements MyBitSet {

    private final ProtectedHack bitSet;

    public MyOpenBitSet(int no) {
        bitSet = new ProtectedHack(no);
    }

    static class ProtectedHack extends OpenBitSet {

        public ProtectedHack(long numBits) {
            super(numBits);
        }

        @Override
        public int expandingWordNum(long index) {
            return super.expandingWordNum(index);
        }
    }

    public MyOpenBitSet() {
        this(1000);
    }

    @Override public boolean contains(int index) {
        return bitSet.fastGet(index);
    }

    @Override public void add(int index) {
        bitSet.fastSet(index);
    }

    @Override public String toString() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            for (DocIdSetIterator iter = bitSet.iterator(); iter.nextDoc() != DocIdSetIterator.NO_MORE_DOCS;) {
                if (sb.length() > 1)
                    sb.append(", ");

                sb.append(iter.docID());
            }
            sb.append("}");
            return sb.toString();
        } catch (Exception ex) {
            throw new RuntimeException("error constructing bitset string representation", ex);
        }
    }

    @Override
    public int getCardinality() {
        return (int) bitSet.cardinality();
    }

    @Override
    public void clear() {
        bitSet.clear(0, bitSet.length());
    }

    @Override
    public void ensureCapacity(int size) {
        bitSet.expandingWordNum(size);
        // this does not update the numBits variable and failse if assertationen are on!
        // bitSet.ensureCapacity((size);
    }

    @Override
    public int next(int index) {
        return bitSet.nextSetBit(index);
    }
}
