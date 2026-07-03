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

package com.graphhopper.reader.osm

import androidx.collection.MutableLongIntMap
import com.graphhopper.coll.primitive.IntArrayList
import com.graphhopper.coll.primitive.IntCursor
import java.util.Collections.emptyIterator

/**
 * This map can store multiple edges (int) for each way ID (long). All way-edge pairs with the same way must be inserted
 * consecutively. This allows us to simply store all edges in an array along with a mapping between the ways and the
 * position of the associated edges in this array.
 */
class WayToEdgesMap {
    private val offsetIndexByWay: MutableLongIntMap = MutableLongIntMap()
    private val offsets = IntArrayList()
    private val edges = IntArrayList()
    private var lastWay = -1L

    /**
     * We need to reserve a way before we can put the associated edges into the map.
     * This way we can define a set of keys/ways for which we shall add edges later.
     */
    fun reserve(way: Long) {
        offsetIndexByWay.put(way, RESERVED)
    }

    fun putIfReserved(way: Long, edge: Int) {
        if (edge < 0)
            throw IllegalArgumentException("edge must be >= 0, but was: $edge")
        if (way != lastWay) {
            if (!offsetIndexByWay.containsKey(way))
                // not reserved yet
                return
            if (offsetIndexByWay.get(way) != RESERVED)
                // already taken
                throw IllegalArgumentException("You need to add all edges for way: $way consecutively")
            offsetIndexByWay.put(way, offsets.size())
            offsets.add(this.edges.size())
            lastWay = way
        }
        this.edges.add(edge)
    }

    fun getEdges(way: Long): Iterator<IntCursor> {
        val offsetIndex = offsetIndexByWay.getOrDefault(way, RESERVED)
        if (offsetIndex == RESERVED)
            // we reserved this, but did not put a value later
            return emptyIterator()
        val offsetBegin = offsets.get(offsetIndex)
        val offsetEnd = if (offsetIndex + 1 < offsets.size()) offsets.get(offsetIndex + 1) else edges.size()
        val cursor = IntCursor()
        cursor.index = -1
        return object : Iterator<IntCursor> {
            override fun hasNext(): Boolean {
                return offsetBegin + cursor.index + 1 < offsetEnd
            }

            override fun next(): IntCursor {
                cursor.index++
                cursor.value = edges.get(offsetBegin + cursor.index)
                return cursor
            }
        }
    }

    companion object {
        private const val RESERVED = -1
    }
}
