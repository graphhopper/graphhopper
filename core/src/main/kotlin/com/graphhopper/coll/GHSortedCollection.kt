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

import java.util.TreeMap

/**
 * A priority queue implemented by a treemap to allow fast key update. Or should we use a standard
 * b-tree?
 *
 * @author Peter Karich
 */
class GHSortedCollection {
    val slidingMeanValue = 20

    // use size as indicator for maxEntries => try radix sort?
    private val map = TreeMap<Int, GHIntHashSet>()

    var size = 0
        private set

    fun clear() {
        size = 0
        map.clear()
    }

    private fun remove(key: Int, value: Int) {
        val set = map[value]
        if (set == null || !set.remove(key)) {
            throw IllegalStateException("cannot remove key " + key + " with value " + value
                    + " - did you insert " + key + "," + value + " before?")
        }
        size--
        if (set.isEmpty()) {
            map.remove(value)
        }
    }

    fun update(key: Int, oldValue: Int, value: Int) {
        remove(key, oldValue)
        insert(key, value)
    }

    fun insert(key: Int, value: Int) {
        var set = map[value]
        if (set == null) {
            set = GHIntHashSet(slidingMeanValue)
            map[value] = set
        }
//        else
//            slidingMeanValue = Math.max(5, (slidingMeanValue + set.size()) / 2);
        if (!set.add(key)) {
            throw IllegalStateException("use update if you want to update $key")
        }
        size++
    }

    fun peekValue(): Int {
        if (size == 0) {
            throw IllegalStateException("collection is already empty!?")
        }
        val e = map.firstEntry()
        if (e.value.isEmpty()) {
            throw IllegalStateException("internal set is already empty!?")
        }
        return map.firstEntry().key
    }

    fun peekKey(): Int {
        if (size == 0) {
            throw IllegalStateException("collection is already empty!?")
        }
        val set = map.firstEntry().value
        if (set.isEmpty()) {
            throw IllegalStateException("internal set is already empty!?")
        }
        return set.firstInIteratorOrder()
    }

    /**
     * @return removes the smallest entry (key and value) from this collection
     */
    fun pollKey(): Int {
        size--
        if (size < 0) {
            throw IllegalStateException("collection is already empty!?")
        }

        val e = map.firstEntry()
        val set = e.value
        if (set.isEmpty()) {
            throw IllegalStateException("internal set is already empty!?")
        }

        val value = set.firstInIteratorOrder()
        set.remove(value)
        if (set.isEmpty()) {
            map.remove(e.key)
        }
        return value
    }

    fun isEmpty(): Boolean = size == 0

    override fun toString(): String {
        var min = Int.MAX_VALUE
        var max = Int.MIN_VALUE
        for (e in map.entries) {
            val tmpSize = e.value.size()
            if (min > tmpSize) {
                min = tmpSize
            }
            if (max < tmpSize) {
                max = tmpSize
            }
        }
        var str = ""
        if (!isEmpty()) {
            str = ", minEntry=(" + peekKey() + "=>" + peekValue() + ")"
        }
        return "size=" + size + ", treeMap.size=" + map.size +
                ", averageNo=" + size * 1f / map.size +
                ", minNo=" + min + ", maxNo=" + max + str
    }
}
