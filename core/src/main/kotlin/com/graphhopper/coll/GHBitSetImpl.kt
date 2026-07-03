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

import java.util.BitSet

/**
 * This implementation stores the bits inside the values of a long-array. Be aware that the size of this array grows
 * depending on the values you pass into this set. If you only want to add a few (possibly large) integers you should
 * use [GHTBitSet] instead.
 *
 * @author Peter Karich
 */
class GHBitSetImpl : BitSet, GHBitSet {
    constructor() : super()

    constructor(nbits: Int) : super(nbits)

    override fun contains(index: Int): Boolean = get(index)

    override fun add(index: Int) {
        set(index)
    }

    override val cardinality: Int
        get() = super.cardinality()

    override fun next(index: Int): Int = nextSetBit(index)

    fun nextClear(index: Int): Int = nextClearBit(index)

    override fun remove(index: Int) {
        clear(index)
    }

    override fun copyTo(bs: GHBitSet): GHBitSet {
        bs.clear()
        if (bs is GHBitSetImpl) {
            bs.or(this)
        } else {
            var index = nextSetBit(0)
            while (index >= 0) {
                bs.add(index)
                index = nextSetBit(index + 1)
            }
        }
        return bs
    }
}
