/*
 * Kotlin port of internals of com.carrotsearch.hppc (BitMixer, HashContainers, Containers,
 * BitUtil, HashOrderMixing) from HPPC 0.8.1 (Apache License 2.0,
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

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Shared internals for the hash-container ports in this package: HPPC 0.8.1's bit mixers and
 * buffer-size math, replicated EXACTLY. Any change here changes hash-iteration order, which is
 * part of GraphHopper's stored-graph reproducibility (see the "HPPC → androidx.collection switch
 * plan" in KOTLIN_MIGRATION.md and docs/pinned-behavior.md) — do not "improve" these functions.
 */
object HashPort {
    /** The default number of expected elements for containers (hppc Containers). */
    const val DEFAULT_EXPECTED_ELEMENTS = 4

    /** Default load factor (hppc HashContainers). */
    const val DEFAULT_LOAD_FACTOR = 0.75

    /**
     * The constant hash-order-mixing seed used by the GH* container family since GraphHopper 0.x
     * (see com.graphhopper.coll.GHIntObjectHashMap): iteration order of the deterministic GH
     * containers is `HashOrderMixing.constant(DETERMINISTIC_SEED)` order.
     */
    const val DETERMINISTIC_SEED = 123321123321123312L

    /** Maximum array size for hash containers (power-of-two). */
    internal const val MAX_HASH_ARRAY_LENGTH = 0x40000000

    /** Minimum hash buffer size. */
    internal const val MIN_HASH_ARRAY_LENGTH = 4

    /** Minimal sane load factor (as double, converted from hppc's 1/100f float). */
    internal val MIN_LOAD_FACTOR = (1f / 100.0f).toDouble()

    /** Maximum sane load factor (as double, converted from hppc's 99/100f float). */
    internal val MAX_LOAD_FACTOR = (99f / 100.0f).toDouble()

    // MurmurHash3 finalization constants
    private val MH3_C1 = 0x85ebca6b.toInt()
    private val MH3_C2 = 0xc2b2ae35.toInt()

    // David Stafford variant 9 constants
    private const val STAFFORD_C1 = 0x4cd6944c5cc20b6dL
    private val STAFFORD_C2 = 0xfc12c5b19d3259e9uL.toLong()

    // Golden ratio constants
    private val PHI_C32 = 0x9e3779b9.toInt()
    private val PHI_C64 = 0x9e3779b97f4a7c15uL.toLong()

    /** MH3's plain finalization step (hppc BitMixer.mix32). */
    @JvmStatic
    fun mix32(k: Int): Int {
        var v = (k xor (k ushr 16)) * MH3_C1
        v = (v xor (v ushr 13)) * MH3_C2
        return v xor (v ushr 16)
    }

    /** David Stafford variant 9 of the 64bit mix function (hppc BitMixer.mix64). */
    @JvmStatic
    fun mix64(z: Long): Long {
        var v = (z xor (z ushr 32)) * STAFFORD_C1
        v = (v xor (v ushr 29)) * STAFFORD_C2
        return v xor (v ushr 32)
    }

    /** hppc BitMixer.mix(int key, int seed). */
    @JvmStatic
    fun mix(key: Int, seed: Int): Int = mix32(key xor seed)

    /** hppc BitMixer.mix(long key, int seed) — the int seed is sign-extended like in Java. */
    @JvmStatic
    fun mix(key: Long, seed: Int): Int = mix64(key xor seed.toLong()).toInt()

    /** hppc BitMixer.mix(Object key, int seed). */
    @JvmStatic
    fun mix(key: Any?, seed: Int): Int = if (key == null) 0 else mix32(key.hashCode() xor seed)

    /** hppc BitMixer.mix(int). */
    @JvmStatic
    fun mix(key: Int): Int = mix32(key)

    /** hppc BitMixer.mix(long). */
    @JvmStatic
    fun mix(key: Long): Int = mix64(key).toInt()

    /** hppc BitMixer.mix(Object). */
    @JvmStatic
    fun mix(key: Any?): Int = if (key == null) 0 else mix32(key.hashCode())

    /** Golden-ratio bit mixer (hppc BitMixer.mixPhi(int)) — used by the scatter containers. */
    @JvmStatic
    fun mixPhi(k: Int): Int {
        val h = k * PHI_C32
        return h xor (h ushr 16)
    }

    /** Golden-ratio bit mixer (hppc BitMixer.mixPhi(long)) — used by the scatter containers. */
    @JvmStatic
    fun mixPhi(k: Long): Int {
        val h = k * PHI_C64
        return (h xor (h ushr 32)).toInt()
    }

    /**
     * The per-buffer-size key mixer of `HashOrderMixing.constant(seed)`: recomputed on every
     * buffer (re)allocation from the new array size.
     */
    internal fun constantKeyMixer(seed: Long, newContainerBufferSize: Int): Int =
        mix64(newContainerBufferSize.toLong() xor seed).toInt()

    /** hppc HashContainers.minBufferSize. */
    internal fun minBufferSize(elements: Int, loadFactor: Double): Int {
        if (elements < 0)
            throw IllegalArgumentException("Number of elements must be >= 0: $elements")
        var length = ceil(elements / loadFactor).toLong()
        if (length == elements.toLong()) length++
        length = max(MIN_HASH_ARRAY_LENGTH.toLong(), nextHighestPowerOfTwo(length))
        if (length > MAX_HASH_ARRAY_LENGTH)
            throw RuntimeException(
                "Maximum array size exceeded for this load factor (elements: $elements, load factor: $loadFactor)")
        return length.toInt()
    }

    /** hppc HashContainers.nextBufferSize. */
    internal fun nextBufferSize(arraySize: Int, elements: Int, loadFactor: Double): Int {
        if (arraySize == MAX_HASH_ARRAY_LENGTH)
            throw RuntimeException(
                "Maximum array size exceeded for this load factor (elements: $elements, load factor: $loadFactor)")
        return arraySize shl 1
    }

    /** hppc HashContainers.expandAtCount: there has to be at least one empty slot. */
    internal fun expandAtCount(arraySize: Int, loadFactor: Double): Int =
        min(arraySize - 1, ceil(arraySize * loadFactor).toInt())

    /** hppc HashContainers.checkLoadFactor. */
    internal fun checkLoadFactor(loadFactor: Double, minAllowedInclusive: Double, maxAllowedInclusive: Double): Double {
        if (loadFactor < minAllowedInclusive || loadFactor > maxAllowedInclusive)
            throw RuntimeException(
                "The load factor should be in range [$minAllowedInclusive, $maxAllowedInclusive]: $loadFactor")
        return loadFactor
    }

    /** hppc BitUtil.nextHighestPowerOfTwo(long). */
    internal fun nextHighestPowerOfTwo(v0: Long): Long {
        var v = v0
        v--
        v = v or (v shr 1)
        v = v or (v shr 2)
        v = v or (v shr 4)
        v = v or (v shr 8)
        v = v or (v shr 16)
        v = v or (v shr 32)
        v++
        return v
    }
}
