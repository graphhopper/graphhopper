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
package com.graphhopper.routing.querygraph

import com.graphhopper.routing.ev.BooleanEncodedValue
import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.routing.ev.EnumEncodedValue
import com.graphhopper.routing.ev.IntEncodedValue
import com.graphhopper.routing.ev.StringEncodedValue
import com.graphhopper.routing.util.EdgeFilter
import com.graphhopper.search.KVStorage
import com.graphhopper.storage.IntsRef
import com.graphhopper.util.EdgeIterator
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.util.FetchMode
import com.graphhopper.util.PointList

/**
 * @author Peter Karich
 */
class VirtualEdgeIterator internal constructor(
    private val edgeFilter: EdgeFilter,
    private var edges: List<EdgeIteratorState>?
) : EdgeIterator {
    private var current = -1

    internal fun reset(edges: List<EdgeIteratorState>): EdgeIterator {
        this.edges = edges
        current = -1
        return this
    }

    override fun next(): Boolean {
        val edges = this.edges!!
        current++
        while (current < edges.size && !edgeFilter.accept(edges[current])) {
            current++
        }
        return current < edges.size
    }

    override fun detach(reverse: Boolean): EdgeIteratorState {
        if (reverse)
            throw IllegalStateException("Not yet supported")
        return currentEdge
    }

    override val edge: Int
        get() = currentEdge.edge

    override val edgeKey: Int
        get() = currentEdge.edgeKey

    override val reverseEdgeKey: Int
        get() = currentEdge.reverseEdgeKey

    override val baseNode: Int
        get() = currentEdge.baseNode

    override val adjNode: Int
        get() = currentEdge.adjNode

    override fun fetchWayGeometry(mode: FetchMode): PointList = currentEdge.fetchWayGeometry(mode)

    override fun setWayGeometry(list: PointList?): EdgeIteratorState = currentEdge.setWayGeometry(list)

    override val distance: Double
        get() = currentEdge.distance

    override fun setDistance(dist: Double): EdgeIteratorState = currentEdge.setDistance(dist)

    override val distance_mm: Long
        get() = currentEdge.distance_mm

    override fun setDistance_mm(distance_mm: Long): EdgeIteratorState = currentEdge.setDistance_mm(distance_mm)

    override val flags: IntsRef
        get() = currentEdge.flags

    override fun setFlags(edgeFlags: IntsRef): EdgeIteratorState = currentEdge.setFlags(edgeFlags)

    override fun set(property: BooleanEncodedValue, value: Boolean): EdgeIteratorState {
        currentEdge.set(property, value)
        return this
    }

    override fun get(property: BooleanEncodedValue): Boolean = currentEdge.get(property)

    override fun setReverse(property: BooleanEncodedValue, value: Boolean): EdgeIteratorState {
        currentEdge.setReverse(property, value)
        return this
    }

    override fun getReverse(property: BooleanEncodedValue): Boolean = currentEdge.getReverse(property)

    override fun set(property: BooleanEncodedValue, fwd: Boolean, bwd: Boolean): EdgeIteratorState {
        currentEdge.set(property, fwd, bwd)
        return this
    }

    override fun set(property: IntEncodedValue, value: Int): EdgeIteratorState {
        currentEdge.set(property, value)
        return this
    }

    override fun get(property: IntEncodedValue): Int = currentEdge.get(property)

    override fun setReverse(property: IntEncodedValue, value: Int): EdgeIteratorState {
        currentEdge.setReverse(property, value)
        return this
    }

    override fun getReverse(property: IntEncodedValue): Int = currentEdge.getReverse(property)

    override fun set(property: IntEncodedValue, fwd: Int, bwd: Int): EdgeIteratorState {
        currentEdge.set(property, fwd, bwd)
        return this
    }

    override fun set(property: DecimalEncodedValue, value: Double): EdgeIteratorState {
        currentEdge.set(property, value)
        return this
    }

    override fun get(property: DecimalEncodedValue): Double = currentEdge.get(property)

    override fun setReverse(property: DecimalEncodedValue, value: Double): EdgeIteratorState {
        currentEdge.setReverse(property, value)
        return this
    }

    override fun getReverse(property: DecimalEncodedValue): Double = currentEdge.getReverse(property)

    override fun set(property: DecimalEncodedValue, fwd: Double, bwd: Double): EdgeIteratorState {
        currentEdge.set(property, fwd, bwd)
        return this
    }

    override fun <T : Enum<*>> set(property: EnumEncodedValue<T>, value: T): EdgeIteratorState {
        currentEdge.set(property, value)
        return this
    }

    override fun <T : Enum<*>> get(property: EnumEncodedValue<T>): T = currentEdge.get(property)

    override fun <T : Enum<*>> setReverse(property: EnumEncodedValue<T>, value: T): EdgeIteratorState {
        currentEdge.setReverse(property, value)
        return this
    }

    override fun <T : Enum<*>> getReverse(property: EnumEncodedValue<T>): T = currentEdge.getReverse(property)

    override fun <T : Enum<*>> set(property: EnumEncodedValue<T>, fwd: T, bwd: T): EdgeIteratorState {
        currentEdge.set(property, fwd, bwd)
        return this
    }

    override fun get(property: StringEncodedValue): String? = currentEdge.get(property)

    override fun set(property: StringEncodedValue, value: String?): EdgeIteratorState = currentEdge.set(property, value)

    override fun getReverse(property: StringEncodedValue): String? = currentEdge.getReverse(property)

    override fun setReverse(property: StringEncodedValue, value: String?): EdgeIteratorState = currentEdge.setReverse(property, value)

    override fun set(property: StringEncodedValue, fwd: String?, bwd: String?): EdgeIteratorState = currentEdge.set(property, fwd, bwd)

    override val name: String
        get() = currentEdge.name

    override val keyValues: Map<String, KVStorage.KValue>
        get() = currentEdge.keyValues

    override fun setKeyValues(map: Map<String, KVStorage.KValue>?): EdgeIteratorState = currentEdge.setKeyValues(map)

    override fun getValue(key: String): Any? = currentEdge.getValue(key)

    override val isVirtual: Boolean
        get() = currentEdge.isVirtual

    override fun toString(): String {
        val edges = this.edges!!
        return if (current >= 0 && current < edges.size) {
            "virtual edge: " + currentEdge + ", all: " + edges.toString()
        } else {
            "virtual edge: (invalid)" + ", all: " + edges.toString()
        }
    }

    override fun copyPropertiesFrom(e: EdgeIteratorState): EdgeIteratorState = currentEdge.copyPropertiesFrom(e)

    private val currentEdge: EdgeIteratorState
        get() = edges!![current]

    fun getEdges(): List<EdgeIteratorState> = edges!!
}
