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

import com.carrotsearch.hppc.IntContainer
import com.graphhopper.coll.primitive.IntHashSet

/**
 * Prefer GHTBitSet or GHBitSetImpl over this class.
 *
 * Since the HPPC switch this extends the hppc-layout port in [com.graphhopper.coll.primitive]
 * (default seed = the historic GH constant) — iteration order is bit-identical to the old
 * hppc-based implementation.
 *
 * @author Peter Karich
 */
class GHIntHashSet @JvmOverloads constructor(
    capacity: Int = 10,
    loadFactor: Double = 0.75
) : IntHashSet(capacity, loadFactor) {

    /**
     * Bridge constructor for hppc containers (mirrors the old hppc `IntHashSet(IntContainer)`:
     * capacity from `container.size()`, then adds in the container's iterator order).
     * Dies with the hppc dependency in batch H5/H8 of the collection switch.
     */
    constructor(container: IntContainer) : this(container.size()) {
        for (cursor in container)
            add(cursor.value)
    }

    companion object {
        // The Java original declared the return type as IntHashSet, hiding IntHashSet.from(int...);
        // the return type stays covariantly narrowed to GHIntHashSet - Java call sites see
        // GHIntHashSet.from(int...) exactly as before.
        @JvmStatic
        fun from(vararg elements: Int): GHIntHashSet {
            val set = GHIntHashSet(elements.size)
            set.addAll(*elements)
            return set
        }
    }
}
