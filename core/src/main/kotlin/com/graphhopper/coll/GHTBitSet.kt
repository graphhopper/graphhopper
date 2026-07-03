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
package com.graphhopper.coll

/**
 * Implements the bitset interface via a hash set. It is more efficient for only a few entries.
 *
 * @author Peter Karich
 */
class GHTBitSet(private val tHash: GHIntHashSet) : GHBitSet {

    // NOTE: the Java original passed the float literal 0.7f into a double parameter; the widened
    // value 0.7f.toDouble() (= 0.699999988...) is kept instead of 0.7 to preserve identical
    // hash-container resize thresholds.
    constructor(no: Int) : this(GHIntHashSet(no, 0.7f.toDouble()))

    constructor() : this(1000)

    override fun contains(index: Int): Boolean = tHash.contains(index)

    override fun add(index: Int) {
        tHash.add(index)
    }

    override fun toString(): String = tHash.toString()

    override val cardinality: Int
        get() = tHash.size()

    override fun clear() {
        tHash.clear()
    }

    override fun remove(index: Int) {
        tHash.remove(index)
    }

    override fun copyTo(bs: GHBitSet): GHBitSet {
        bs.clear()
        if (bs is GHTBitSet) {
            bs.tHash.addAll(this.tHash)
        } else {
            // hppc cursor-iterator order (slots ascending, empty key last)
            tHash.forEachInIteratorOrder { value ->
                bs.add(value)
            }
        }
        return bs
    }

    override fun next(index: Int): Int {
        throw UnsupportedOperationException("Not supported yet.")
    }
}
