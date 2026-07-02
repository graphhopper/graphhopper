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
package com.graphhopper.search

import com.graphhopper.storage.DataAccess
import com.graphhopper.storage.Directory
import com.graphhopper.util.BitUtil
import com.graphhopper.util.Constants
import com.graphhopper.util.GHUtility
import com.graphhopper.util.Helper
import java.util.Arrays
import java.util.Collections
import java.util.Objects
import java.util.concurrent.atomic.AtomicInteger

/**
 * This class stores key-value pairs in an append-only manner.
 *
 * @author Peter Karich
 */
class KVStorage(private val dir: Directory, edge: Boolean) {

    private val keys: DataAccess

    // The storage layout in the vals DataAccess for one Map of key-value pairs. For example the map:
    // map = new HashMap(); map.put("some", "value"); map.put("some2", "value2"); is added via the method add, then we store:
    // 2 (the size of the Map, 1 byte)
    // --- now the first key-value pair:
    // 1 (the keys index for "some", 2 byte)
    // 4 (the length of the bytes from "some")
    // "some" (the bytes from "some")
    // --- second key-value pair:
    // 2 (the keys index for "some2")
    // 5 (the length of the bytes from "some2")
    // "some2" (the bytes from "some2")

    // So more generic: the values could be of dynamic length, fixed length like int or be duplicates:
    // vals count      (1 byte)
    // --- 1. key-value pair (store String or byte[] with dynamic length)
    // key_idx_0       (2 byte, of which the first 2bits are to know if this is valid for fwd and/or bwd direction)
    // val_length_0    (1 byte)
    // val_0 (x bytes)
    // --- 2. key-value pair (store int with fixed length)
    // key_idx_1       (2 byte)
    // int             (4 byte)
    //
    // Notes:
    // 1. The key strings are limited MAX_UNIQUE_KEYS. A dynamic value has a maximum byte length of 255.
    // 2. Every key can store values only of the same type
    // 3. We need to loop through X entries to get the start val_x.
    // 4. The key index (14 bits) is stored along with the availability (2 bits), i.e. whether they KeyValue is available in forward and/or backward directions
    private val vals: DataAccess
    private val keyToIndex = HashMap<String, Int>()
    private val indexToClass = ArrayList<Class<*>>()
    private val indexToKey = ArrayList<String>()
    private val bitUtil = BitUtil.LITTLE
    private var bytePointer = START_POINTER
    private var lastEntryPointer = -1L
    private var lastEntries: Map<String, KValue>? = null

    /**
     * Specify a larger cacheSize to reduce disk usage. Note that this increases the memory usage of this object.
     */
    init {
        // It stores the mapping of "key to index" in the keys DataAccess. E.g. if your first key is "some" then we will
        // store the mapping "1->some" there (the 0th index is skipped on purpose). As this map is 'small' the keys
        // DataAccess is only used for long term storage, i.e. only in loadExisting and flush. For add and getAll we use
        // keyToIndex, indexToClass and indexToClass.
        if (edge) {
            this.keys = dir.create("edgekv_keys", 10 * 1024)
            this.vals = dir.create("edgekv_vals")
        } else {
            this.keys = dir.create("nodekv_keys", 10 * 1024)
            this.vals = dir.create("nodekv_vals")
        }
    }

    fun create(initBytes: Long): KVStorage {
        keys.create(initBytes)
        vals.create(initBytes)
        // add special empty case to have a reliable duplicate detection via negative keyIndex
        keyToIndex[""] = 0
        indexToKey.add("")
        indexToClass.add(String::class.java)
        return this
    }

    fun loadExisting(): Boolean {
        if (vals.loadExisting()) {
            if (!keys.loadExisting())
                throw IllegalStateException("Loaded values but cannot load keys")
            bytePointer = bitUtil.toLong(vals.getHeader(0), vals.getHeader(4))
            GHUtility.checkDAVersion(vals.name, Constants.VERSION_KV_STORAGE, vals.getHeader(8))
            GHUtility.checkDAVersion(keys.name, Constants.VERSION_KV_STORAGE, keys.getHeader(0))

            // load keys into memory
            val count = keys.getShort(0).toInt()
            var keyBytePointer = 2L
            for (i in 0 until count) {
                val keyLength = keys.getShort(keyBytePointer).toInt()
                keyBytePointer += 2
                val keyBytes = ByteArray(keyLength)
                keys.getBytes(keyBytePointer, keyBytes, keyLength)
                val valueStr = String(keyBytes, Helper.UTF_CS)
                keyBytePointer += keyLength

                keyToIndex[valueStr] = keyToIndex.size
                indexToKey.add(valueStr)

                val shortClassNameLength = 1
                val classBytes = ByteArray(shortClassNameLength)
                keys.getBytes(keyBytePointer, classBytes, shortClassNameLength)
                keyBytePointer += shortClassNameLength
                indexToClass.add(shortNameToClass(String(classBytes, Helper.UTF_CS)))
            }
            return true
        }

        return false
    }

    @JvmName("getKeys")
    internal fun getKeys(): Collection<String> = indexToKey

    private fun setKVList(currentPointer: Long, entries: Map<String, KValue>): Long {
        if (currentPointer == EMPTY_POINTER) return currentPointer
        var pointer = currentPointer + 1 // skip stored count
        for (entry in entries.entries) {
            if (entry.value.fwdBwdEqual) {
                pointer = add(pointer, entry.key, entry.value.fwd, true, true)
            } else {
                // potentially add two internal values
                if (entry.value.fwd != null)
                    pointer = add(pointer, entry.key, entry.value.fwd, true, false)
                if (entry.value.bwd != null)
                    pointer = add(pointer, entry.key, entry.value.bwd, false, true)
            }
        }
        return pointer
    }

    internal fun add(currentPointer: Long, key: String?, value: Any?, fwd: Boolean, bwd: Boolean): Long {
        if (key == null) throw IllegalArgumentException("key cannot be null")
        if (value == null)
            throw IllegalArgumentException("value for key $key cannot be null")

        var pointer = currentPointer
        var keyIndex = keyToIndex[key]
        val clazz: Class<*>
        if (keyIndex == null) {
            keyIndex = keyToIndex.size
            if (keyIndex >= MAX_UNIQUE_KEYS)
                throw IllegalArgumentException("Cannot store more than $MAX_UNIQUE_KEYS unique keys")
            keyToIndex[key] = keyIndex
            indexToKey.add(key)
            clazz = value.javaClass
            indexToClass.add(clazz)
        } else {
            clazz = indexToClass[keyIndex]
            if (clazz != value.javaClass)
                throw IllegalArgumentException("Class of value for key " + key + " must be " + clazz.simpleName + " but was " + value.javaClass.simpleName)
        }

        val hasDynLength = hasDynLength(clazz)
        if (hasDynLength) {
            // optimization for empty string or empty byte array
            if (clazz == String::class.java && (value as String).isEmpty()
                    || clazz == ByteArray::class.java && (value as ByteArray).isEmpty()) {
                vals.ensureCapacity(pointer + 3)
                vals.setShort(pointer, keyIndex.toShort())
                // ensure that also in case of MMap value is set to 0
                vals.setByte(pointer + 2, 0.toByte())
                return pointer + 3
            }
        }

        val valueBytes = getBytesForValue(clazz, value)
        vals.ensureCapacity(pointer + 2 + 1 + valueBytes.size)
        vals.setShort(pointer, ((keyIndex shl 2) or (if (fwd) 2 else 0) or (if (bwd) 1 else 0)).toShort())
        pointer += 2
        if (hasDynLength) {
            vals.setByte(pointer, valueBytes.size.toByte())
            pointer++
        }
        vals.setBytes(pointer, valueBytes, valueBytes.size)
        return pointer + valueBytes.size
    }

    /**
     * This method writes the specified entryMap (key-value pairs) into the storage. Please note that null keys or null
     * values are rejected. The Class of a value can be only: byte[], String, int, long, float or double
     * (or more precisely, their wrapper equivalent). For all other types an exception is thrown. The first call of add
     * assigns a Class to every key in the Map and future calls of add will throw an exception if this Class differs.
     *
     * @return entryPointer with which you can later fetch the entryMap via the get or getAll method
     */
    fun add(entries: Map<String, KValue>?): Long {
        if (entries == null) throw IllegalArgumentException("specified List must not be null")
        if (entries.isEmpty()) return EMPTY_POINTER
        else if (entries.size > 200)
            throw IllegalArgumentException("Cannot store more than 200 entries per entry")

        // This is a very important "compression" mechanism because one OSM way is split into multiple edges and so we
        // can often re-use the serialized key-value pairs of the previous edge.
        if (entries == lastEntries) return lastEntryPointer

        var entryCount = 0
        for (kv in entries.entries) {

            if (kv.value.fwdBwdEqual) {
                entryCount++
            } else {
                // note, if fwd and bwd are different we create two internal entries!
                if (kv.value.fwd != null) entryCount++
                if (kv.value.bwd != null) entryCount++
            }

            // If the Class of a value is unknown it should already fail here, before we modify internal data. (see #2597#discussion_r896469840)
            val keyIndex = keyToIndex[kv.key]
            if (keyIndex != null) {
                if (kv.value.fwd != null)
                    getBytesForValue(indexToClass[keyIndex], kv.value.fwd!!)
                if (kv.value.bwd != null)
                    getBytesForValue(indexToClass[keyIndex], kv.value.bwd!!)
            }
        }

        lastEntries = entries
        lastEntryPointer = bytePointer
        vals.ensureCapacity(bytePointer + 1)
        vals.setByte(bytePointer, entryCount.toByte())
        bytePointer = setKVList(bytePointer, entries)
        if (bytePointer < 0)
            throw IllegalStateException("Negative bytePointer in KVStorage")
        // Pad to next alignment boundary
        val remainder = bytePointer % ALIGNMENT
        if (remainder != 0L)
            bytePointer += ALIGNMENT - remainder
        return lastEntryPointer
    }

    fun getAll(entryPointer: Long): Map<String, KValue> {
        if (entryPointer < 0)
            throw IllegalStateException("Pointer to access KVStorage cannot be negative:$entryPointer")

        if (entryPointer == EMPTY_POINTER) return Collections.emptyMap()

        val keyCount = vals.getByte(entryPointer).toInt() and 0xFF
        if (keyCount == 0) return Collections.emptyMap()

        val map = LinkedHashMap<String, KValue>()
        var tmpPointer = entryPointer + 1
        val sizeOfObject = AtomicInteger()
        for (i in 0 until keyCount) {
            val currentKeyIndexRaw = vals.getShort(tmpPointer).toInt() and 0xFFFF
            val bwd = (currentKeyIndexRaw and 1) == 1
            val fwd = (currentKeyIndexRaw and 2) == 2
            val currentKeyIndex = currentKeyIndexRaw ushr 2
            tmpPointer += 2

            val obj = deserializeObj(sizeOfObject, tmpPointer, indexToClass[currentKeyIndex])
            tmpPointer += sizeOfObject.get()
            val key = indexToKey[currentKeyIndex]
            val oldValue = map[key]
            if (oldValue != null)
                map[key] = KValue(if (fwd) obj else oldValue.fwd, if (bwd) obj else oldValue.bwd)
            else if (fwd && bwd)
                map[key] = KValue(obj)
            else
                map[key] = KValue(if (fwd) obj else null, if (bwd) obj else null)
        }

        return map
    }

    /**
     * Please note that this method ignores potentially different tags for forward and backward direction. To avoid this
     * use [getAll] instead.
     */
    fun getMap(entryPointer: Long): Map<String, Any> {
        if (entryPointer < 0)
            throw IllegalStateException("Pointer to access KVStorage cannot be negative:$entryPointer")

        if (entryPointer == EMPTY_POINTER) return Collections.emptyMap()

        val keyCount = vals.getByte(entryPointer).toInt() and 0xFF
        if (keyCount == 0) return Collections.emptyMap()

        val map = HashMap<String, Any>(keyCount)
        var tmpPointer = entryPointer + 1
        val sizeOfObject = AtomicInteger()
        for (i in 0 until keyCount) {
            val currentKeyIndexRaw = vals.getShort(tmpPointer).toInt() and 0xFFFF
            val currentKeyIndex = currentKeyIndexRaw ushr 2
            tmpPointer += 2

            val obj = deserializeObj(sizeOfObject, tmpPointer, indexToClass[currentKeyIndex])
            tmpPointer += sizeOfObject.get()
            val key = indexToKey[currentKeyIndex]
            map[key] = obj
        }

        return map
    }

    private fun hasDynLength(clazz: Class<*>): Boolean {
        return clazz == String::class.java || clazz == ByteArray::class.java
    }

    private fun getFixLength(clazz: Class<*>): Int {
        return if (clazz == INTEGER_CLASS || clazz == FLOAT_CLASS) 4
        else if (clazz == LONG_CLASS || clazz == DOUBLE_CLASS) 8
        else throw IllegalArgumentException("unknown class $clazz")
    }

    private fun getBytesForValue(clazz: Class<*>, value: Any): ByteArray {
        val bytes: ByteArray
        if (clazz == String::class.java) {
            bytes = (value as String).toByteArray(Helper.UTF_CS)
            if (bytes.size > MAX_LENGTH)
                throw IllegalArgumentException("bytes.length cannot be > " + MAX_LENGTH + " but was " + bytes.size + ". String:" + value)
        } else if (clazz == ByteArray::class.java) {
            bytes = value as ByteArray
            if (bytes.size > MAX_LENGTH)
                throw IllegalArgumentException("bytes.length cannot be > " + MAX_LENGTH + " but was " + bytes.size)
        } else if (clazz == INTEGER_CLASS) {
            return bitUtil.fromInt(value as Int)
        } else if (clazz == LONG_CLASS) {
            return bitUtil.fromLong(value as Long)
        } else if (clazz == FLOAT_CLASS) {
            return bitUtil.fromFloat(value as Float)
        } else if (clazz == DOUBLE_CLASS) {
            return bitUtil.fromDouble(value as Double)
        } else
            throw IllegalArgumentException("The Class of a value was " + clazz.simpleName + ", currently supported: byte[], String, int, long, float and double")
        return bytes
    }

    private fun classToShortName(clazz: Class<*>): String {
        return if (clazz == String::class.java) "S"
        else if (clazz == INTEGER_CLASS) "i"
        else if (clazz == LONG_CLASS) "l"
        else if (clazz == FLOAT_CLASS) "f"
        else if (clazz == DOUBLE_CLASS) "d"
        else if (clazz == ByteArray::class.java) "["
        else throw IllegalArgumentException("Cannot find short name. Unknown class $clazz")
    }

    private fun shortNameToClass(name: String): Class<*> {
        return if (name == "S") String::class.java
        else if (name == "i") INTEGER_CLASS
        else if (name == "l") LONG_CLASS
        else if (name == "f") FLOAT_CLASS
        else if (name == "d") DOUBLE_CLASS
        else if (name == "[") ByteArray::class.java
        else throw IllegalArgumentException("Cannot find class. Unknown short name $name")
    }

    /**
     * This method creates an Object (type Class) which is located at the specified pointer
     */
    private fun deserializeObj(sizeOfObject: AtomicInteger?, pointer: Long, clazz: Class<*>): Any {
        var p = pointer
        if (hasDynLength(clazz)) {
            val valueLength = vals.getByte(p).toInt() and 0xFF
            p++
            val valueBytes = ByteArray(valueLength)
            vals.getBytes(p, valueBytes, valueBytes.size)
            if (sizeOfObject != null)
                sizeOfObject.set(1 + valueLength) // For String and byte[] we store the length and the value
            if (clazz == String::class.java) return String(valueBytes, Helper.UTF_CS)
            else if (clazz == ByteArray::class.java) return valueBytes
            throw IllegalArgumentException()
        } else {
            val valueBytes = ByteArray(getFixLength(clazz))
            vals.getBytes(p, valueBytes, valueBytes.size)
            if (clazz == INTEGER_CLASS) {
                sizeOfObject?.set(4)
                return bitUtil.toInt(valueBytes, 0)
            } else if (clazz == LONG_CLASS) {
                sizeOfObject?.set(8)
                return bitUtil.toLong(valueBytes, 0)
            } else if (clazz == FLOAT_CLASS) {
                sizeOfObject?.set(4)
                return bitUtil.toFloat(valueBytes, 0)
            } else if (clazz == DOUBLE_CLASS) {
                sizeOfObject?.set(8)
                return bitUtil.toDouble(valueBytes, 0)
            } else {
                throw IllegalArgumentException("unknown class $clazz")
            }
        }
    }

    fun get(entryPointer: Long, key: String, reverse: Boolean): Any? {
        if (entryPointer < 0)
            throw IllegalStateException("Pointer to access KVStorage cannot be negative:$entryPointer")

        if (entryPointer == EMPTY_POINTER) return null

        val keyIndex = keyToIndex[key] ?: return null // key wasn't stored before

        val keyCount = vals.getByte(entryPointer).toInt() and 0xFF
        if (keyCount == 0) return null // no entries

        var tmpPointer = entryPointer + 1
        for (i in 0 until keyCount) {
            val currentKeyIndexRaw = vals.getShort(tmpPointer).toInt() and 0xFFFF
            val bwd = (currentKeyIndexRaw and 1) == 1
            val fwd = (currentKeyIndexRaw and 2) == 2
            val currentKeyIndex = currentKeyIndexRaw ushr 2

            assert(currentKeyIndex < indexToKey.size) { "invalid key index " + currentKeyIndex + ">=" + indexToKey.size + ", entryPointer=" + entryPointer + ", max=" + bytePointer }
            tmpPointer += 2
            if ((!reverse && fwd || reverse && bwd) && currentKeyIndex == keyIndex) {
                return deserializeObj(null, tmpPointer, indexToClass[keyIndex])
            }

            // skip to next entry of same edge via skipping the real value
            val clazz = indexToClass[currentKeyIndex]
            // NOTE: intentionally kept identical to the original Java code where '+' binds stronger
            // than '&', i.e. this is (1 + byte) & 0xFF and thus 0 for a byte value of 255!
            val valueLength = if (hasDynLength(clazz)) (1 + vals.getByte(tmpPointer)) and 0xFF else getFixLength(clazz)
            tmpPointer += valueLength
        }

        // value for specified key does not exist for the specified pointer
        return null
    }

    fun flush() {
        keys.ensureCapacity(2)
        keys.setShort(0, keyToIndex.size.toShort())
        var keyBytePointer = 2L
        for (i in 0 until indexToKey.size) {
            val key = indexToKey[i]
            val keyBytes = getBytesForValue(String::class.java, key)
            keys.ensureCapacity(keyBytePointer + 2 + keyBytes.size)
            keys.setShort(keyBytePointer, keyBytes.size.toShort())
            keyBytePointer += 2

            keys.setBytes(keyBytePointer, keyBytes, keyBytes.size)
            keyBytePointer += keyBytes.size

            val clazz = indexToClass[i]
            val clazzBytes = getBytesForValue(String::class.java, classToShortName(clazz))
            if (clazzBytes.size != 1)
                throw IllegalArgumentException("class name byte length must be 1 but was " + clazzBytes.size)
            keys.ensureCapacity(keyBytePointer + 1)
            keys.setBytes(keyBytePointer, clazzBytes, 1)
            keyBytePointer += 1
        }
        keys.setHeader(0, Constants.VERSION_KV_STORAGE)
        keys.flush()

        vals.setHeader(0, bitUtil.getIntLow(bytePointer))
        vals.setHeader(4, bitUtil.getIntHigh(bytePointer))
        vals.setHeader(8, Constants.VERSION_KV_STORAGE)
        vals.flush()
    }

    fun clear() {
        dir.remove(keys.name)
        dir.remove(vals.name)
    }

    fun close() {
        keys.close()
        vals.close()
    }

    val isClosed: Boolean
        get() = vals.isClosed && keys.isClosed

    fun getCapacity(): Long = vals.capacity + keys.capacity

    class KValue {
        val fwd: Any?
        val bwd: Any?

        @JvmField
        internal val fwdBwdEqual: Boolean

        constructor(obj: Any?) {
            if (obj == null)
                throw IllegalArgumentException("Object cannot be null if forward and backward is both true")
            fwd = obj
            bwd = obj
            fwdBwdEqual = true
        }

        constructor(fwd: Any?, bwd: Any?) {
            this.fwd = fwd
            this.bwd = bwd
            if (fwd != null && bwd != null && fwd.javaClass != bwd.javaClass)
                throw IllegalArgumentException("If both values are not null they have to be they same class but was: "
                        + fwd.javaClass + " vs " + bwd.javaClass)
            if (fwd == null && bwd == null)
                throw IllegalArgumentException("If both values are null just do not store them")
            fwdBwdEqual = false
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || javaClass != other.javaClass) return false
            val value = other as KValue
            // due to check in constructor we can assume that fwdValue and bwdValue are of same type.
            // I.e. if one is a byte array the other is too.
            if (fwd is ByteArray || bwd is ByteArray)
                return fwdBwdEqual == value.fwdBwdEqual && (Arrays.equals(fwd as ByteArray?, value.fwd as ByteArray?) || Arrays.equals(bwd as ByteArray?, value.bwd as ByteArray?))

            return fwdBwdEqual == value.fwdBwdEqual && Objects.equals(fwd, value.fwd) && Objects.equals(bwd, value.bwd)
        }

        override fun hashCode(): Int {
            return Objects.hash(fwd, bwd, fwdBwdEqual)
        }

        override fun toString(): String {
            return if (fwdBwdEqual) fwd!!.toString() else "$fwd | $bwd"
        }
    }

    companion object {
        private const val EMPTY_POINTER = 0L

        // Align entries to 4-byte boundaries. This allows callers to store pointer >> 2 externally,
        // giving 4x the addressable space when storing pointers as unsigned int (~16GB instead of ~4GB).
        // Callers are expected to shift the pointer themselves: >> 2 when storing, << 2 when retrieving.
        private const val ALIGNMENT = 4

        /**
         * The alignment shift for pointers returned by [add]. Callers should use
         * `pointer >> ALIGNMENT_SHIFT` when storing and `pointer << ALIGNMENT_SHIFT` when retrieving.
         */
        const val ALIGNMENT_SHIFT = 2

        private const val START_POINTER = ALIGNMENT.toLong()

        // Store the key index in 2 bytes. Use first 2 bits for marking fwd+bwd existence.
        internal const val MAX_UNIQUE_KEYS = 1 shl 14

        // Store string value as byte array and store the length into 1 byte
        private const val MAX_LENGTH = (1 shl 8) - 1

        private val INTEGER_CLASS: Class<Int> = Int::class.javaObjectType
        private val LONG_CLASS: Class<Long> = Long::class.javaObjectType
        private val FLOAT_CLASS: Class<Float> = Float::class.javaObjectType
        private val DOUBLE_CLASS: Class<Double> = Double::class.javaObjectType

        /**
         * This method limits the specified String value to the length currently accepted for values in the KVStorage.
         */
        @JvmStatic
        fun cutString(value: String): String {
            val bytes = value.toByteArray(Helper.UTF_CS)
            // See #2609 and test why we use a value < 255
            return if (bytes.size > 250) String(bytes, 0, 250, Helper.UTF_CS) else value
        }
    }
}
