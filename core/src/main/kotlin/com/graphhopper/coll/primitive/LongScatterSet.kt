/*
 * Kotlin port of com.carrotsearch.hppc.LongScatterSet from HPPC 0.8.1 (Apache License 2.0,
 * https://github.com/carrotsearch/hppc). See NOTICE.md.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.graphhopper.coll.primitive

/**
 * Same as [LongHashSet] but does not implement per-instance key mixing and uses a simpler
 * (faster) golden-ratio bit distribution — a 1:1 layout port of HPPC 0.8.1's `LongScatterSet`
 * with bit-identical iteration order.
 */
open class LongScatterSet @JvmOverloads constructor(
    expectedElements: Int = HashPort.DEFAULT_EXPECTED_ELEMENTS,
    loadFactor: Double = HashPort.DEFAULT_LOAD_FACTOR
    // the seed passed to the parent is irrelevant: hashKey ignores the key mixer,
    // like hppc's HashOrderMixing.none()
) : LongHashSet(expectedElements, loadFactor) {

    override fun hashKey(key: Long): Int = HashPort.mixPhi(key)

    companion object {
        /** Creates a set from a variable number of arguments (hppc `LongScatterSet.from`). */
        @JvmStatic
        fun from(vararg elements: Long): LongScatterSet {
            val set = LongScatterSet(elements.size)
            set.addAll(*elements)
            return set
        }
    }
}
