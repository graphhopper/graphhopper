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
package com.graphhopper.routing

import com.graphhopper.routing.util.TraversalMode
import com.graphhopper.util.PMap
import com.graphhopper.util.Parameters

/**
 * @author Peter Karich
 */
class AlgorithmOptions {
    private var hints = PMap()
    private var algorithm: String = Parameters.Algorithms.DIJKSTRA_BI
    private var traversalMode = TraversalMode.NODE_BASED
    private var maxVisitedNodes = Int.MAX_VALUE
    private var timeoutMillis = Long.MAX_VALUE

    constructor()

    // note: the timeout is deliberately NOT copied, just like in the original Java implementation
    constructor(b: AlgorithmOptions) {
        setAlgorithm(b.getAlgorithm())
        setTraversalMode(b.getTraversalMode())
        setMaxVisitedNodes(b.getMaxVisitedNodes())
        setHints(b.getHints())
    }

    fun setAlgorithm(algorithm: String): AlgorithmOptions {
        this.algorithm = algorithm
        return this
    }

    fun setTraversalMode(traversalMode: TraversalMode): AlgorithmOptions {
        this.traversalMode = traversalMode
        return this
    }

    fun setMaxVisitedNodes(maxVisitedNodes: Int): AlgorithmOptions {
        this.maxVisitedNodes = maxVisitedNodes
        return this
    }

    fun setTimeoutMillis(timeoutMillis: Long): AlgorithmOptions {
        this.timeoutMillis = timeoutMillis
        return this
    }

    fun setHints(pMap: PMap): AlgorithmOptions {
        this.hints = PMap(pMap)
        return this
    }

    fun getTraversalMode(): TraversalMode = traversalMode

    fun getAlgorithm(): String = algorithm

    fun getMaxVisitedNodes(): Int = maxVisitedNodes

    fun getTimeoutMillis(): Long = timeoutMillis

    fun getHints(): PMap = hints

    override fun toString(): String = "$algorithm, $traversalMode"
}
