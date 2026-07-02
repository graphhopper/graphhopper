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

import com.graphhopper.routing.weighting.Weighting

interface RoutingCHGraph {
    val nodes: Int

    val edges: Int

    val shortcuts: Int

    /**
     * Traverses the base edges and shortcuts at a given node. This will only include shortcuts coming from higher
     * level nodes, but *all* base edges with finite weight.
     */
    fun createInEdgeExplorer(): RoutingCHEdgeExplorer

    /**
     * @see createInEdgeExplorer but here the shortcuts/edges are going out of the given node.
     */
    fun createOutEdgeExplorer(): RoutingCHEdgeExplorer

    fun getEdgeIteratorState(chEdge: Int, adjNode: Int): RoutingCHEdgeIteratorState?

    fun getLevel(node: Int): Int

    fun getTurnWeight(inEdge: Int, viaNode: Int, outEdge: Int): Double

    /**
     * @return the graph this CH graph is based on, i.e. a the base [Graph] or a [com.graphhopper.routing.querygraph.QueryGraph]
     * on top of the base graph
     * todo: maybe it would be better to remove this method and use a direct reference to the base graph when it is
     * needed
     */
    val baseGraph: Graph

    fun hasTurnCosts(): Boolean

    val isEdgeBased: Boolean

    val weighting: Weighting

    // todo: would like to get rid of this
    fun close()
}
