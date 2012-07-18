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

import gnu.trove.set.hash.TIntHashSet;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public class MyTBitSet implements MyBitSet {

    private final TIntHashSet tHash;

    public MyTBitSet(int no) {
        tHash = new TIntHashSet(no);
    }

    public MyTBitSet() {
        this(1000);
    }

    @Override public boolean contains(int index) {
        return tHash.contains(index);
    }

    @Override public void add(int index) {
        tHash.add(index);
    }

    @Override public String toString() {
        return tHash.toString();
    }

    @Override
    public int getCardinality() {
        return tHash.size();
    }

    @Override
    public void clear() {
        tHash.clear();
    }        

    @Override
    public void ensureCapacity(int size) {        
    }

    @Override
    public int next(int index) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
