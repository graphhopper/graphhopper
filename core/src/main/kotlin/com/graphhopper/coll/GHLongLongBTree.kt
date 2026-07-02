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

import com.graphhopper.util.Helper
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.Arrays
import java.util.function.LongUnaryOperator

/**
 * An in-memory B-Tree with configurable value size (1-8 bytes). Delete not supported.
 * (Later we could use DataAccess to allow on-disc storage for very large data sets.)
 *
 * @author Peter Karich
 */
class GHLongLongBTree(maxLeafEntries: Int, bytesPerValue: Int, emptyValue: Long) : LongLongMap {
    val emptyValue: Long
    private val maxLeafEntries: Int
    private val initLeafSize: Int
    private val splitIndex: Int
    private val factor: Float
    private var _size: Long = 0
    private var height: Int = 0
    private lateinit var root: BTreeEntry
    private val bytesPerValue: Int
    override val maxValue: Long

    init {
        this.maxLeafEntries = maxLeafEntries
        this.bytesPerValue = bytesPerValue
        if (bytesPerValue > 8)
            throw IllegalArgumentException("Values can have 8 bytes maximum but requested was $bytesPerValue")
        this.emptyValue = emptyValue

        // reserve one bit for negative values
        this.maxValue = (1L shl (bytesPerValue * 8 - 1)) - 1
        if (maxLeafEntries < 1)
            throw IllegalArgumentException("illegal maxLeafEntries:$maxLeafEntries")

        var tmpMaxLeafEntries = maxLeafEntries
        if (tmpMaxLeafEntries % 2 == 0)
            tmpMaxLeafEntries++

        splitIndex = tmpMaxLeafEntries / 2
        if (tmpMaxLeafEntries < 10) {
            factor = 2f
            initLeafSize = 1
        } else if (tmpMaxLeafEntries < 20) {
            factor = 2f
            initLeafSize = 4
        } else {
            factor = 1.7f
            initLeafSize = tmpMaxLeafEntries / 10
        }
        clear()
    }

    override fun put(key: Long, value: Long): Long {
        if (value > maxValue)
            throw IllegalArgumentException("Value $value exceeded max value: $maxValue" +
                    ". Increase bytesPerValue ($bytesPerValue)")
        if (value == emptyValue)
            throw IllegalArgumentException("Value cannot be the 'empty value' $emptyValue")

        val rv = root.put(key, value)
        val tree = rv.tree
        if (tree != null) {
            height++
            root = tree
        }
        val oldValue = rv.oldValue
        if (oldValue == null) {
            // successfully inserted
            _size++
            if (_size % 1000000 == 0L)
                optimize()
            return emptyValue
        }
        return toLong(oldValue)
    }

    override fun putOrCompute(key: Long, valueIfAbsent: Long, computeIfPresent: LongUnaryOperator): Long {
        if (valueIfAbsent > maxValue)
            throw IllegalArgumentException("Value $valueIfAbsent exceeded max value: $maxValue" +
                    ". Increase bytesPerValue ($bytesPerValue)")
        if (valueIfAbsent == emptyValue)
            throw IllegalArgumentException("Value cannot be the 'empty value' $emptyValue")

        val rv = root.putOrCompute(key, valueIfAbsent, computeIfPresent)
        val tree = rv.tree
        if (tree != null) {
            height++
            root = tree
        }
        val oldValue = rv.oldValue
        if (oldValue == null) {
            // successfully inserted (was absent)
            _size++
            if (_size % 1000000 == 0L)
                optimize()
            return emptyValue
        }
        return toLong(oldValue)
    }

    override fun get(key: Long): Long {
        return root.get(key)
    }

    @JvmName("height")
    internal fun height(): Int {
        return height
    }

    override val size: Long
        get() = _size

    /**
     * @return memory usage in MB
     */
    override val memoryUsage: Int
        get() = Math.round((root.getCapacity() / Helper.MB).toFloat())

    override fun clear() {
        _size = 0
        height = 1
        root = BTreeEntry(initLeafSize, true)
    }

    private fun getEntries(): Int {
        return root.getEntries()
    }

    override fun optimize() {
        if (size > 10000) {
//            StopWatch sw = new StopWatch().start();
//            int old = memoryUsage();
            root.compact()
//            logger.info(size + "| osmIdMap.optimize took: " + sw.stop().getSeconds()
//                    + " => freed: " + (old - memoryUsage()) + "MB");
        }
    }

    override fun toString(): String {
        return "Height:${height()}, entries:${getEntries()}"
    }

    internal fun print() {
        logger.info(root.toString(1))
    }

    internal class ReturnValue(var oldValue: ByteArray?) {
        var tree: BTreeEntry? = null
    }

    @JvmName("toLong")
    internal fun toLong(b: ByteArray): Long {
        return toLong(b, 0)
    }

    @JvmName("toLong")
    internal fun toLong(bytes: ByteArray, offset: Int): Long {
        var res: Long = 0
        if (bytesPerValue == 8) res = res or (bytes[offset + 7].toLong() shl 56)
        else if (bytesPerValue > 7) res = res or (bytes[offset + 7].toLong() shl 56)

        if (bytesPerValue == 7) res = res or (bytes[offset + 6].toLong() shl 48)
        else if (bytesPerValue > 6) res = res or ((bytes[offset + 6].toLong() and 0xFF) shl 48)

        if (bytesPerValue == 6) res = res or (bytes[offset + 5].toLong() shl 40)
        else if (bytesPerValue > 5) res = res or ((bytes[offset + 5].toLong() and 0xFF) shl 40)

        if (bytesPerValue == 5) res = res or (bytes[offset + 4].toLong() shl 32)
        else if (bytesPerValue > 4) res = res or ((bytes[offset + 4].toLong() and 0xFF) shl 32)

        if (bytesPerValue == 4) res = res or (bytes[offset + 3].toLong() shl 24)
        else if (bytesPerValue > 3) res = res or ((bytes[offset + 3].toLong() and 0xFF) shl 24)

        if (bytesPerValue == 3) res = res or (bytes[offset + 2].toLong() shl 16)
        else if (bytesPerValue > 2) res = res or ((bytes[offset + 2].toLong() and 0xFF) shl 16)

        if (bytesPerValue == 2) res = res or (bytes[offset + 1].toLong() shl 8)
        else if (bytesPerValue > 1) res = res or ((bytes[offset + 1].toLong() and 0xFF) shl 8)

        res = res or (bytes[offset].toLong() and 0xff)
        return res
    }

    @JvmName("fromLong")
    internal fun fromLong(value: Long): ByteArray {
        val bytes = ByteArray(bytesPerValue)
        fromLong(bytes, value, 0)
        return bytes
    }

    @JvmName("fromLong")
    internal fun fromLong(bytes: ByteArray, value: Long, offset: Int) {
        if (bytesPerValue > 7) bytes[offset + 7] = (value shr 56).toByte()
        if (bytesPerValue > 6) bytes[offset + 6] = (value shr 48).toByte()
        if (bytesPerValue > 5) bytes[offset + 5] = (value shr 40).toByte()
        if (bytesPerValue > 4) bytes[offset + 4] = (value shr 32).toByte()
        if (bytesPerValue > 3) bytes[offset + 3] = (value shr 24).toByte()
        if (bytesPerValue > 2) bytes[offset + 2] = (value shr 16).toByte()
        if (bytesPerValue > 1) bytes[offset + 1] = (value shr 8).toByte()
        bytes[offset] = value.toByte()
    }

    internal inner class BTreeEntry(tmpSize: Int, val isLeaf: Boolean) {
        var entrySize: Int = 0
        var keys: LongArray
        var values: ByteArray
        var children: Array<BTreeEntry?>? = null

        init {
            keys = LongArray(tmpSize)
            values = ByteArray(tmpSize * bytesPerValue)

            if (!isLeaf) {
                // in a b-tree we need one more entry to point to all children!
                children = arrayOfNulls(tmpSize + 1)
            }
        }

        /**
         * @return the old value which was associated with the specified key or if no update it
         * returns noNumberValue
         */
        fun put(key: Long, newValue: Long): ReturnValue {
            var index = binarySearch(keys, 0, entrySize, key)
            if (index >= 0) {
                // update
                val oldValue = ByteArray(bytesPerValue)
                System.arraycopy(values, index * bytesPerValue, oldValue, 0, bytesPerValue)
                // copy newValue to values
                fromLong(values, newValue, index * bytesPerValue)
                return ReturnValue(oldValue)
            }

            index = index.inv()
            if (isLeaf || children!![index] == null) {
                // insert
                val downTreeRV = ReturnValue(null)
                downTreeRV.tree = checkSplitEntry()
                val tree = downTreeRV.tree
                if (tree == null) {
                    insertKeyValue(index, key, fromLong(newValue))
                } else if (index <= splitIndex) {
                    tree.children!![0]!!.insertKeyValue(index, key, fromLong(newValue))
                } else {
                    tree.children!![1]!!.insertKeyValue(index - splitIndex - 1, key, fromLong(newValue))
                }
                return downTreeRV
            }

            val downTreeRV = children!![index]!!.put(key, newValue)
            if (downTreeRV.oldValue != null) // only update
                return downTreeRV

            if (downTreeRV.tree != null) {
                // split this treeEntry if it is too big
                val downTree = checkSplitEntry()
                if (downTree == null) {
                    insertTree(index, downTreeRV.tree!!)
                } else if (index <= splitIndex) {
                    downTree.children!![0]!!.insertTree(index, downTreeRV.tree!!)
                } else {
                    downTree.children!![1]!!.insertTree(index - splitIndex - 1, downTreeRV.tree!!)
                }

                downTreeRV.tree = downTree
            }
            return downTreeRV
        }

        /**
         * Like put, but uses valueIfAbsent when key is not found, and computeIfPresent when key is found.
         * This avoids a separate get+put traversal.
         */
        fun putOrCompute(key: Long, valueIfAbsent: Long, computeIfPresent: LongUnaryOperator): ReturnValue {
            var index = binarySearch(keys, 0, entrySize, key)
            if (index >= 0) {
                // key exists: compute new value from old value
                val oldValue = ByteArray(bytesPerValue)
                System.arraycopy(values, index * bytesPerValue, oldValue, 0, bytesPerValue)
                val oldLong = toLong(oldValue)
                val newValue = computeIfPresent.applyAsLong(oldLong)
                if (newValue > maxValue)
                    throw IllegalArgumentException("Computed value $newValue exceeded max value: $maxValue" +
                            ". Increase bytesPerValue ($bytesPerValue)")
                if (newValue == emptyValue)
                    throw IllegalArgumentException("Computed value cannot be the 'empty value' $emptyValue")
                fromLong(values, newValue, index * bytesPerValue)
                return ReturnValue(oldValue)
            }

            // key does not exist: insert valueIfAbsent
            index = index.inv()
            if (isLeaf || children!![index] == null) {
                // insert
                val downTreeRV = ReturnValue(null)
                downTreeRV.tree = checkSplitEntry()
                val tree = downTreeRV.tree
                if (tree == null) {
                    insertKeyValue(index, key, fromLong(valueIfAbsent))
                } else if (index <= splitIndex) {
                    tree.children!![0]!!.insertKeyValue(index, key, fromLong(valueIfAbsent))
                } else {
                    tree.children!![1]!!.insertKeyValue(index - splitIndex - 1, key, fromLong(valueIfAbsent))
                }
                return downTreeRV
            }

            val downTreeRV = children!![index]!!.putOrCompute(key, valueIfAbsent, computeIfPresent)
            if (downTreeRV.oldValue != null) // only update
                return downTreeRV

            if (downTreeRV.tree != null) {
                // split this treeEntry if it is too big
                val downTree = checkSplitEntry()
                if (downTree == null) {
                    insertTree(index, downTreeRV.tree!!)
                } else if (index <= splitIndex) {
                    downTree.children!![0]!!.insertTree(index, downTreeRV.tree!!)
                } else {
                    downTree.children!![1]!!.insertTree(index - splitIndex - 1, downTreeRV.tree!!)
                }

                downTreeRV.tree = downTree
            }
            return downTreeRV
        }

        /**
         * @return null if nothing to do or a new sub tree if this tree capacity is no longer
         * sufficient.
         */
        fun checkSplitEntry(): BTreeEntry? {
            if (entrySize < maxLeafEntries) {
                return null
            }

            // right child: copy from this
            val count = entrySize - splitIndex - 1
            val newRightChild = BTreeEntry(Math.max(initLeafSize, count), isLeaf)
            copy(this, newRightChild, splitIndex + 1, count)

            // left child: copy from this
            // avoid: http://stackoverflow.com/q/15897869/194609
            val newLeftChild = BTreeEntry(Math.max(initLeafSize, splitIndex), isLeaf)
            copy(this, newLeftChild, 0, splitIndex)

            // new tree pointing to left + right tree only
            val newTree = BTreeEntry(1, false)
            newTree.entrySize = 1
            newTree.keys[0] = this.keys[splitIndex]

            System.arraycopy(this.values, splitIndex * bytesPerValue, newTree.values, 0, bytesPerValue)
            newTree.children!![0] = newLeftChild
            newTree.children!![1] = newRightChild
            return newTree
        }

        fun copy(fromChild: BTreeEntry, toChild: BTreeEntry, from: Int, count: Int) {
            System.arraycopy(fromChild.keys, from, toChild.keys, 0, count)
            System.arraycopy(fromChild.values, from * bytesPerValue, toChild.values, 0, count * bytesPerValue)
            if (!fromChild.isLeaf) {
                System.arraycopy(fromChild.children!!, from, toChild.children!!, 0, count + 1)
            }

            toChild.entrySize = count
        }

        fun insertKeyValue(index: Int, key: Long, newValueFromIdx0: ByteArray) {
            ensureSize(entrySize + 1)
            val count = entrySize - index
            if (count > 0) {
                System.arraycopy(keys, index, keys, index + 1, count)
                System.arraycopy(values, index * bytesPerValue, values, index * bytesPerValue + bytesPerValue, count * bytesPerValue)
                if (!isLeaf) {
                    System.arraycopy(children!!, index + 1, children!!, index + 2, count)
                }
            }

            keys[index] = key
            System.arraycopy(newValueFromIdx0, 0, values, index * bytesPerValue, bytesPerValue)
            entrySize++
        }

        fun insertTree(index: Int, tree: BTreeEntry) {
            insertKeyValue(index, tree.keys[0], tree.values)
            if (!isLeaf) {
                // overwrite children
                children!![index] = tree.children!![0]
                // set
                children!![index + 1] = tree.children!![1]
            }
        }

        fun get(key: Long): Long {
            val index = binarySearch(keys, 0, entrySize, key)
            if (index >= 0) {
                return toLong(values, index * bytesPerValue)
            }
            val childIndex = index.inv()
            if (isLeaf || children!![childIndex] == null) {
                return emptyValue
            }
            return children!![childIndex]!!.get(key)
        }

        /**
         * @return used bytes
         */
        fun getCapacity(): Long {
            var cap = (keys.size * (8 + 4) + 3 * 12 + 4 + 1).toLong()
            if (!isLeaf) {
                val children = children!!
                cap += (children.size * 4).toLong()
                for (i in children.indices) {
                    val child = children[i]
                    if (child != null) {
                        cap += child.getCapacity()
                    }
                }
            }
            return cap
        }

        fun getEntries(): Int {
            var entries = 1
            if (!isLeaf) {
                val children = children!!
                for (i in children.indices) {
                    val child = children[i]
                    if (child != null) {
                        entries += child.getEntries()
                    }
                }
            }
            return entries
        }

        fun ensureSize(size: Int) {
            if (size <= keys.size) {
                return
            }
            val newSize = Math.min(maxLeafEntries, Math.max(size + 1, Math.round(size * factor)))
            keys = Arrays.copyOf(keys, newSize)
            values = Arrays.copyOf(values, newSize * bytesPerValue)
            if (!isLeaf) {
                children = Arrays.copyOf(children!!, newSize + 1)
            }
        }

        fun compact() {
            val tolerance = 1
            if (entrySize + tolerance < keys.size) {
                keys = Arrays.copyOf(keys, entrySize)
                values = Arrays.copyOf(values, entrySize * bytesPerValue)
                if (!isLeaf) {
                    children = Arrays.copyOf(children!!, entrySize + 1)
                }
            }

            if (!isLeaf) {
                val children = children!!
                for (i in children.indices) {
                    val child = children[i]
                    if (child != null) {
                        child.compact()
                    }
                }
            }
        }

        fun toString(height: Int): String {
            var str = "$height: "
            for (i in 0 until entrySize) {
                if (i > 0) {
                    str += ","
                }
                str += keys[i]
            }
            str += "\n"
            if (!isLeaf) {
                val children = children!!
                for (i in 0 until entrySize + 1) {
                    val child = children[i]
                    if (child != null) {
                        str += child.toString(height + 1) + "| "
                    }
                }
            }
            return str
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(GHLongLongBTree::class.java)

        @JvmStatic
        @JvmName("binarySearch")
        internal fun binarySearch(keys: LongArray, start: Int, len: Int, key: Long): Int {
            var high = start + len
            var low = start - 1
            var guess: Int
            while (high - low > 1) {
                // use >>> for average or we could get an integer overflow.
                guess = (high + low) ushr 1
                val guessedKey = keys[guess]
                if (guessedKey < key) {
                    low = guess
                } else {
                    high = guess
                }
            }

            if (high == start + len) {
                return (start + len).inv()
            }

            val highKey = keys[high]
            return if (highKey == key) high else high.inv()
        }
    }
}
