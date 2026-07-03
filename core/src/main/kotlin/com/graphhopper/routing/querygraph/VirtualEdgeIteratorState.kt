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
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.routing.ev.EnumEncodedValue
import com.graphhopper.routing.ev.IntEncodedValue
import com.graphhopper.routing.ev.IntsRefEdgeIntAccess
import com.graphhopper.routing.ev.StringEncodedValue
import com.graphhopper.search.KVStorage
import com.graphhopper.storage.BaseGraph.Companion.MAX_DIST_METERS
import com.graphhopper.storage.IntsRef
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.util.FetchMode
import com.graphhopper.util.GHUtility
import com.graphhopper.util.Parameters.Details.STREET_NAME
import com.graphhopper.util.PointList

/**
 * Creates an edge state decoupled from a graph where nodes, pointList, etc are kept in memory.
 *
 * Note, this class is not suited for public use and can change with minor releases unexpectedly or
 * even gets removed.
 */
class VirtualEdgeIteratorState(
    originalEdgeKey: Int, edgeKey: Int, baseNode: Int, adjNode: Int, distance: Double,
    edgeFlags: IntsRef, keyValues: Map<String, KVStorage.KValue>, pointList: PointList, reverse: Boolean
) : EdgeIteratorState {
    private val pointList: PointList = pointList

    override val edgeKey: Int = edgeKey

    override val baseNode: Int = baseNode

    override val adjNode: Int = adjNode

    /**
     * This method returns the original (not virtual!) edge key. I.e. also the direction is
     * already correctly encoded.
     *
     * @see EdgeIteratorState.edgeKey
     */
    val originalEdgeKey: Int = originalEdgeKey

    private var distanceMm: Long = 0
    private var edgeFlags: IntsRef = edgeFlags
    private var edgeIntAccess: EdgeIntAccess = IntsRefEdgeIntAccess(edgeFlags)
    private var keyValuesMap: Map<String, KVStorage.KValue>? = keyValues

    // true if edge should be avoided as start/stop
    private var unfavored = false
    private var reverseEdge: EdgeIteratorState? = null
    private val reverse: Boolean = reverse

    init {
        setDistance(distance)
    }

    override val edge: Int
        get() = GHUtility.getEdgeFromEdgeKey(edgeKey)

    override val reverseEdgeKey: Int
        get() = GHUtility.reverseEdgeKey(edgeKey)

    override fun fetchWayGeometry(mode: FetchMode): PointList {
        if (pointList.isEmpty)
            return PointList.EMPTY
        // due to API we need to create a new instance per call!
        if (mode == FetchMode.TOWER_ONLY) {
            if (pointList.size() < 3)
                return pointList.clone(false)
            val towerNodes = PointList(2, pointList.is3D())
            towerNodes.add(pointList, 0)
            towerNodes.add(pointList, pointList.size() - 1)
            return towerNodes
        } else if (mode == FetchMode.ALL)
            return pointList.clone(false)
        else if (mode == FetchMode.BASE_AND_PILLAR)
            return pointList.copy(0, pointList.size() - 1)
        else if (mode == FetchMode.PILLAR_AND_ADJ)
            return pointList.copy(1, pointList.size())
        else if (mode == FetchMode.PILLAR_ONLY) {
            if (pointList.size() == 1)
                return PointList.EMPTY
            return pointList.copy(1, pointList.size() - 1)
        }
        throw UnsupportedOperationException("Illegal mode:$mode")
    }

    override fun setWayGeometry(list: PointList?): EdgeIteratorState {
        throw UnsupportedOperationException("Not supported for virtual edge. Set when creating it.")
    }

    override val distance: Double
        get() = distanceMm / 1000.0

    override fun setDistance(dist: Double): EdgeIteratorState {
        var distance = dist
        if (distance < 0)
            throw IllegalArgumentException("distances must be non-negative, got: $distance")
        if (distance > MAX_DIST_METERS)
            distance = MAX_DIST_METERS
        val distance_mm = Math.round(distance * 1000)
        setDistance_mm(distance_mm)
        return this
    }

    override val distance_mm: Long
        get() = distanceMm

    override fun setDistance_mm(distance_mm: Long): EdgeIteratorState {
        var mm = distance_mm
        if (mm < 0)
            throw IllegalArgumentException("distances must be non-negative, got: $mm")
        if (mm > Int.MAX_VALUE)
            mm = Int.MAX_VALUE.toLong()
        this.distanceMm = mm
        return this
    }

    override val flags: IntsRef
        get() = edgeFlags

    override fun setFlags(edgeFlags: IntsRef): EdgeIteratorState {
        this.edgeFlags = edgeFlags
        this.edgeIntAccess = IntsRefEdgeIntAccess(edgeFlags)
        return this
    }

    override fun get(property: BooleanEncodedValue): Boolean {
        if (property === EdgeIteratorState.UNFAVORED_EDGE)
            return unfavored

        return property.getBool(reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess)
    }

    override fun set(property: BooleanEncodedValue, value: Boolean): EdgeIteratorState {
        property.setBool(reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, value)
        return this
    }

    override fun getReverse(property: BooleanEncodedValue): Boolean {
        if (property === EdgeIteratorState.UNFAVORED_EDGE)
            return unfavored
        return property.getBool(!reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess)
    }

    override fun setReverse(property: BooleanEncodedValue, value: Boolean): EdgeIteratorState {
        property.setBool(!reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, value)
        return this
    }

    override fun set(property: BooleanEncodedValue, fwd: Boolean, bwd: Boolean): EdgeIteratorState {
        if (!property.isStoreTwoDirections)
            throw IllegalArgumentException("EncodedValue " + property.name + " supports only one direction")
        property.setBool(reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, fwd)
        property.setBool(!reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, bwd)
        return this
    }

    override fun get(property: IntEncodedValue): Int =
        property.getInt(reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess)

    override fun set(property: IntEncodedValue, value: Int): EdgeIteratorState {
        property.setInt(reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, value)
        return this
    }

    override fun getReverse(property: IntEncodedValue): Int =
        property.getInt(!reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess)

    override fun setReverse(property: IntEncodedValue, value: Int): EdgeIteratorState {
        property.setInt(!reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, value)
        return this
    }

    override fun set(property: IntEncodedValue, fwd: Int, bwd: Int): EdgeIteratorState {
        if (!property.isStoreTwoDirections)
            throw IllegalArgumentException("EncodedValue " + property.name + " supports only one direction")
        property.setInt(reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, fwd)
        property.setInt(!reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, bwd)
        return this
    }

    override fun get(property: DecimalEncodedValue): Double =
        property.getDecimal(reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess)

    override fun set(property: DecimalEncodedValue, value: Double): EdgeIteratorState {
        property.setDecimal(reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, value)
        return this
    }

    override fun getReverse(property: DecimalEncodedValue): Double =
        property.getDecimal(!reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess)

    override fun setReverse(property: DecimalEncodedValue, value: Double): EdgeIteratorState {
        property.setDecimal(!reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, value)
        return this
    }

    override fun set(property: DecimalEncodedValue, fwd: Double, bwd: Double): EdgeIteratorState {
        if (!property.isStoreTwoDirections)
            throw IllegalArgumentException("EncodedValue " + property.name + " supports only one direction")
        property.setDecimal(reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, fwd)
        property.setDecimal(!reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, bwd)
        return this
    }

    override fun <T : Enum<*>> get(property: EnumEncodedValue<T>): T =
        property.getEnum(reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess)

    override fun <T : Enum<*>> set(property: EnumEncodedValue<T>, value: T): EdgeIteratorState {
        property.setEnum(reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, value)
        return this
    }

    override fun <T : Enum<*>> getReverse(property: EnumEncodedValue<T>): T =
        property.getEnum(!reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess)

    override fun <T : Enum<*>> setReverse(property: EnumEncodedValue<T>, value: T): EdgeIteratorState {
        property.setEnum(!reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, value)
        return this
    }

    override fun <T : Enum<*>> set(property: EnumEncodedValue<T>, fwd: T, bwd: T): EdgeIteratorState {
        if (!property.isStoreTwoDirections)
            throw IllegalArgumentException("EncodedValue " + property.name + " supports only one direction")
        property.setEnum(reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, fwd)
        property.setEnum(!reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, bwd)
        return this
    }

    override fun get(property: StringEncodedValue): String? =
        property.getString(reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess)

    override fun set(property: StringEncodedValue, value: String?): EdgeIteratorState {
        property.setString(reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, value)
        return this
    }

    override fun getReverse(property: StringEncodedValue): String? =
        property.getString(!reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess)

    override fun setReverse(property: StringEncodedValue, value: String?): EdgeIteratorState {
        property.setString(!reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, value)
        return this
    }

    override fun set(property: StringEncodedValue, fwd: String?, bwd: String?): EdgeIteratorState {
        if (!property.isStoreTwoDirections)
            throw IllegalArgumentException("EncodedValue " + property.name + " supports only one direction")
        property.setString(reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, fwd)
        property.setString(!reverse, GHUtility.getEdgeFromEdgeKey(originalEdgeKey), edgeIntAccess, bwd)
        return this
    }

    override val name: String
        get() {
            val name = getValue(STREET_NAME) as String?
            // preserve backward compatibility (returns empty string if name tag missing)
            return name ?: ""
        }

    override fun setKeyValues(map: Map<String, KVStorage.KValue>?): EdgeIteratorState {
        this.keyValuesMap = map
        return this
    }

    override val keyValues: Map<String, KVStorage.KValue>
        get() = keyValuesMap!!

    override fun getValue(key: String): Any? {
        val value = keyValuesMap!![key]
        if (value != null) {
            if (!reverse && value.fwd != null) return value.fwd
            if (reverse && value.bwd != null) return value.bwd
        }
        return null
    }

    /**
     * This method sets edge to unfavored status for routing from the start or to the stop location.
     */
    fun setUnfavored(unfavored: Boolean) {
        this.unfavored = unfavored
    }

    override fun toString(): String = "$baseNode->$adjNode"

    override fun detach(reverse: Boolean): EdgeIteratorState {
        return if (reverse) {
            // update properties of reverse edge
            // TODO copy pointList (geometry) too
            val reverseEdge = this.reverseEdge!!
            reverseEdge.setFlags(flags)
            reverseEdge.setKeyValues(keyValues)
            reverseEdge.setDistance_mm(distance_mm)
            reverseEdge
        } else {
            this
        }
    }

    override fun copyPropertiesFrom(e: EdgeIteratorState): EdgeIteratorState {
        throw RuntimeException("Not supported.")
    }

    override val isVirtual: Boolean
        get() = true

    fun setReverseEdge(reverseEdge: EdgeIteratorState?) {
        this.reverseEdge = reverseEdge
    }
}
