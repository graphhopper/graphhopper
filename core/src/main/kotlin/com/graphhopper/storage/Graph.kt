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
package com.graphhopper.storage

import com.graphhopper.routing.util.AllEdgesIterator
import com.graphhopper.routing.util.EdgeFilter
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.util.EdgeExplorer
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.util.shapes.BBox

/**
 * An interface to represent a (geo) graph - suited for efficient storage as it can be requested via
 * indices called node IDs. To get the lat,lon point you need to set up a LocationIndex instance.
 *
 * @author Peter Karich
 */
interface Graph {
    /**
     * @return a graph which behaves like an unprepared graph and e.g. the normal unidirectional
     * Dijkstra or any graph traversal algorithm can be executed.
     */
    val baseGraph: BaseGraph

    /**
     * @return the number of created locations - via setNode() or edge()
     */
    val nodes: Int

    /**
     * @return the number of edges in this graph. Equivalent to getAllEdges().length().
     */
    val edges: Int

    /**
     * Creates an object to access node properties.
     */
    val nodeAccess: NodeAccess

    /**
     * Returns the implicit bounds of this graph calculated from the lat,lon input of setNode
     */
    val bounds: BBox

    /**
     * Creates an edge between the nodes a and b. To set distance or access use the returned edge
     * and e.g. edgeState.setDistance
     *
     * @param a the index of the starting (tower) node of the edge
     * @param b the index of the ending (tower) node of the edge
     * @return the newly created edge
     */
    fun edge(a: Int, b: Int): EdgeIteratorState

    /**
     * Returns a wrapper over the specified edgeId.
     *
     * @param adjNode is the node that will be returned via getAdjNode(). If adjNode is
     * Integer.MIN_VALUE then the edge will be returned in the direction of how it is stored
     * @return a new EdgeIteratorState object or potentially null if adjNode does not match
     * @throws IllegalStateException if edgeId is not valid
     */
    fun getEdgeIteratorState(edgeId: Int, adjNode: Int): EdgeIteratorState?

    /**
     * Returns the edge state for the given edge key
     *
     * @see EdgeIteratorState.edgeKey
     */
    fun getEdgeIteratorStateForKey(edgeKey: Int): EdgeIteratorState

    /**
     * @return the 'opposite' node of a given edge, so if there is an edge 3-2 and node =2 this returns 3
     */
    fun getOtherNode(edge: Int, node: Int): Int

    /**
     * @return true if the edge with id edge is adjacent to node, false otherwise
     */
    fun isAdjacentToNode(edge: Int, node: Int): Boolean

    /**
     * @return all edges in this graph, where baseNode will be the smaller node.
     */
    val allEdges: AllEdgesIterator

    /**
     * Returns an EdgeExplorer which makes it possible to traverse all filtered edges of a specific
     * node. Calling this method might be expensive, so e.g. create an explorer before a for loop!
     */
    fun createEdgeExplorer(filter: EdgeFilter): EdgeExplorer

    /**
     * Creates an EdgeExplorer that accepts all edges
     *
     * @see createEdgeExplorer
     */
    fun createEdgeExplorer(): EdgeExplorer = createEdgeExplorer(EdgeFilter.ALL_EDGES)

    /**
     * @return the [TurnCostStorage] or null if not supported
     */
    val turnCostStorage: TurnCostStorage?

    /**
     * Wraps the given weighting into a weighting that can be used by this graph
     */
    fun wrapWeighting(weighting: Weighting): Weighting
}
