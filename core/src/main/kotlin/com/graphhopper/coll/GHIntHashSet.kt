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

import com.carrotsearch.hppc.HashOrderMixingStrategy
import com.carrotsearch.hppc.IntContainer
import com.carrotsearch.hppc.IntHashSet

/**
 * Prefer GHTBitSet or GHBitSetImpl over this class.
 *
 * @author Peter Karich
 */
class GHIntHashSet @JvmOverloads constructor(
    capacity: Int = 10,
    loadFactor: Double = 0.75,
    hashOrderMixer: HashOrderMixingStrategy = GHIntObjectHashMap.DETERMINISTIC
) : IntHashSet(capacity, loadFactor, hashOrderMixer) {

    constructor(container: IntContainer) : this(container.size()) {
        addAll(container)
    }

    companion object {
        // The Java original declared the return type as IntHashSet, hiding IntHashSet.from(int...).
        // Kotlin rejects a static with the identical JVM signature ("accidental override"), so the
        // return type is covariantly narrowed to GHIntHashSet - Java call sites are unaffected and
        // IntHashSet.from(int...) remains hidden.
        @JvmStatic
        fun from(vararg elements: Int): GHIntHashSet {
            val set = GHIntHashSet(elements.size)
            set.addAll(*elements)
            return set
        }
    }
}
