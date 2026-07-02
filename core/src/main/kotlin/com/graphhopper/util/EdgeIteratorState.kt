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
package com.graphhopper.util

import com.graphhopper.routing.ev.BooleanEncodedValue
import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.routing.ev.EncodedValue
import com.graphhopper.routing.ev.EnumEncodedValue
import com.graphhopper.routing.ev.IntEncodedValue
import com.graphhopper.routing.ev.StringEncodedValue
import com.graphhopper.search.KVStorage
import com.graphhopper.storage.IntsRef

/**
 * This interface represents an edge and is one possible state of an EdgeIterator.
 * Example:
 * <span>
 * EdgeExplorer expl = graph.createEdgeExplorer();
 * EdgeIterator iter = expl.setBaseNode(baseNode);
 * while(iter.next()) {
 * iter.getBaseBase() // equals to the specified baseNode
 * }
 * </span>
 *
 * @author Peter Karich
 * @see EdgeIterator
 * @see EdgeExplorer
 */
interface EdgeIteratorState {
    /**
     * The edge id of the current edge. Do not make any assumptions about the concrete
     * values, except that for an implementation it is recommended that they'll be contiguous.
     */
    val edge: Int

    /**
     * Returns the edge key of the current edge. The edge id can be derived from the edge key by calling
     * [GHUtility.getEdgeFromEdgeKey], but the edge key also contains information about the
     * direction of the edge. The edge key is even when the edge is oriented in storage direction and odd
     * otherwise. You can use the edge key to retrieve an edge state in the associated direction using
     * [com.graphhopper.storage.Graph.getEdgeIteratorStateForKey].
     */
    val edgeKey: Int

    /**
     * Like [edgeKey], but returns the reverse key.
     */
    val reverseEdgeKey: Int

    /**
     * Returns the node used to instantiate the EdgeIterator. Often only used for convenience reasons.
     * Do not confuse this with a *source node* of a directed edge.
     *
     * @return the requested node itself
     * @see EdgeIterator
     */
    val baseNode: Int

    /**
     * The adjacent node of baseNode for the current edge.
     *
     * @see EdgeIterator
     */
    val adjNode: Int

    /**
     * For road network data like OSM a way is often not just a straight line. The nodes between the junction nodes
     * are called pillar nodes. The junction nodes are called tower nodes and used for routing. The pillar nodes are
     * necessary to have an exact geometry. See the docs for more information
     * (docs/core/low-level-api.md#what-are-pillar-and-tower-nodes). Updates to the returned list
     * are not reflected in the graph, for that you've to use setWayGeometry.
     *
     * @param mode [FetchMode]
     * @return the pillar and/or tower nodes depending on the mode.
     */
    fun fetchWayGeometry(mode: FetchMode): PointList

    /**
     * @param list is a sorted collection of coordinates between the base node and the current adjacent node. Specify
     * the list without the adjacent and base node. This method can be called multiple times, unless the
     * given point list is longer than the first time the method was called. Also keep in
     * mind that if the distance changes the setDistance method is not called automatically.
     */
    fun setWayGeometry(list: PointList?): EdgeIteratorState

    /**
     * The distance of the current edge in meter
     */
    // todonow: check if we should replace more usages with getDistance_mm. also remove tolerances in tests, but maybe postpone
    val distance: Double

    fun setDistance(dist: Double): EdgeIteratorState

    /**
     * Returns the distance of the current edge in millimeters. This should be used wherever exact
     * distance summation is desired.
     */
    val distance_mm: Long

    /**
     * Sets the distance in mm. This should be used wherever exact distance summation is desired.
     * Distances above the storage limit will be capped!
     */
    fun setDistance_mm(distance_mm: Long): EdgeIteratorState

    /**
     * Returns edge properties stored in direction of the raw database layout. So do not use it directly, instead
     * use the appropriate set/get methods with its EncodedValue object.
     */
    val flags: IntsRef

    /**
     * Stores the specified edgeFlags down to the DataAccess
     */
    fun setFlags(edgeFlags: IntsRef): EdgeIteratorState

    fun get(property: BooleanEncodedValue): Boolean

    fun set(property: BooleanEncodedValue, value: Boolean): EdgeIteratorState

    fun getReverse(property: BooleanEncodedValue): Boolean

    fun setReverse(property: BooleanEncodedValue, value: Boolean): EdgeIteratorState

    fun set(property: BooleanEncodedValue, fwd: Boolean, bwd: Boolean): EdgeIteratorState

    fun get(property: IntEncodedValue): Int

    fun set(property: IntEncodedValue, value: Int): EdgeIteratorState

    fun getReverse(property: IntEncodedValue): Int

    fun setReverse(property: IntEncodedValue, value: Int): EdgeIteratorState

    fun set(property: IntEncodedValue, fwd: Int, bwd: Int): EdgeIteratorState

    fun get(property: DecimalEncodedValue): Double

    fun set(property: DecimalEncodedValue, value: Double): EdgeIteratorState

    fun getReverse(property: DecimalEncodedValue): Double

    fun setReverse(property: DecimalEncodedValue, value: Double): EdgeIteratorState

    fun set(property: DecimalEncodedValue, fwd: Double, bwd: Double): EdgeIteratorState

    fun <T : Enum<*>> get(property: EnumEncodedValue<T>): T

    fun <T : Enum<*>> set(property: EnumEncodedValue<T>, value: T): EdgeIteratorState

    fun <T : Enum<*>> getReverse(property: EnumEncodedValue<T>): T

    fun <T : Enum<*>> setReverse(property: EnumEncodedValue<T>, value: T): EdgeIteratorState

    fun <T : Enum<*>> set(property: EnumEncodedValue<T>, fwd: T, bwd: T): EdgeIteratorState

    fun get(property: StringEncodedValue): String?

    fun set(property: StringEncodedValue, value: String?): EdgeIteratorState

    fun getReverse(property: StringEncodedValue): String?

    fun setReverse(property: StringEncodedValue, value: String?): EdgeIteratorState

    fun set(property: StringEncodedValue, fwd: String?, bwd: String?): EdgeIteratorState

    /**
     * Identical to calling getKeyValues().get("name") if name is stored for both directions. Note that for backward
     * compatibility this method returns an empty String instead of null if there was no KeyPair with key==name stored.
     *
     * @return the stored value for the key "name" in the KeyValue list of this EdgeIteratorState.
     */
    val name: String

    /**
     * This stores the specified key-value pairs in the storage of this EdgeIteratorState. This is more flexible
     * compared to the mechanism of flags and EncodedValue and allows storing sparse key value pairs more efficient.
     * But it might be slow and more inefficient on retrieval. Call this setKeyValues method only once per
     * EdgeIteratorState as it allocates new space everytime this method is called.
     */
    fun setKeyValues(map: Map<String, KVStorage.KValue>?): EdgeIteratorState

    /**
     * This method returns KeyValue pairs for both directions in contrast to [getValue].
     *
     * @see setKeyValues
     */
    val keyValues: Map<String, KVStorage.KValue>

    /**
     * This method returns the *first* value for the specified key and only if stored for the direction of this
     * EdgeIteratorState. If you need more than one value see also [keyValues]. Avoid storing KeyPairs with
     * duplicate keys as only the first will be reachable with this method. Currently, there is no support to use this
     * method in a custom_model, and you should use EncodedValues instead.
     */
    fun getValue(key: String): Any?

    /**
     * Clones this EdgeIteratorState.
     *
     * @param reverse if true a detached edgeState with reversed properties is created where base
     * and adjacent nodes, flags and wayGeometry are in reversed order. See #162 for more details
     * about why we need the reverse parameter.
     */
    fun detach(reverse: Boolean): EdgeIteratorState

    /**
     * Copies the properties of the specified edge into this edge. Does not change nodes!
     *
     * @return the specified edge e
     */
    fun copyPropertiesFrom(e: EdgeIteratorState): EdgeIteratorState

    val isVirtual: Boolean

    companion object {
        @JvmField
        val UNFAVORED_EDGE: BooleanEncodedValue = object : BooleanEncodedValue {
            override fun init(init: EncodedValue.InitializerConfig): Int {
                throw IllegalStateException("Cannot happen for 'unfavored' BooleanEncodedValue")
            }

            override fun getBool(reverse: Boolean, edgeId: Int, edgeIntAccess: EdgeIntAccess): Boolean = false

            override fun setBool(reverse: Boolean, edgeId: Int, edgeIntAccess: EdgeIntAccess, value: Boolean) {
                throw IllegalStateException("state of 'unfavored' cannot be modified")
            }

            override val isStoreTwoDirections: Boolean
                get() = false

            override val name: String
                get() = "unfavored"
        }

        /**
         * This encoded value can be used to fetch the internal reverse state of an edge.
         */
        @JvmField
        val REVERSE_STATE: BooleanEncodedValue = object : BooleanEncodedValue {
            override fun init(init: EncodedValue.InitializerConfig): Int {
                throw IllegalStateException("Cannot happen for 'reverse' BooleanEncodedValue")
            }

            override val name: String
                get() = "reverse"

            override fun getBool(reverse: Boolean, edgeId: Int, edgeIntAccess: EdgeIntAccess): Boolean = reverse

            override fun setBool(reverse: Boolean, edgeId: Int, edgeIntAccess: EdgeIntAccess, value: Boolean) {
                throw IllegalStateException("state of 'reverse' cannot be modified")
            }

            override val isStoreTwoDirections: Boolean
                get() = false
        }
    }
}
