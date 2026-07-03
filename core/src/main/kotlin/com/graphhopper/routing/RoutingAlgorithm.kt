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

/**
 * Calculates the shortest path from the specified node ids. Can be used only once.
 *
 * @author Peter Karich
 */
interface RoutingAlgorithm {
    /**
     * Calculates the best path between the specified nodes.
     *
     * @return the path. Call the method found() to make sure that the path is valid.
     */
    fun calcPath(from: Int, to: Int): Path

    /**
     * Calculates multiple possibilities for a path.
     *
     * @see calcPath
     */
    fun calcPaths(from: Int, to: Int): List<Path>

    /**
     * Limit the search to numberOfNodes. See #681
     */
    fun setMaxVisitedNodes(numberOfNodes: Int)

    /**
     * Limit the search to the given time in milliseconds
     */
    fun setTimeoutMillis(timeoutMillis: Long)

    /**
     * @return name of this algorithm
     */
    fun getName(): String

    /**
     * Returns the visited nodes after searching. Useful for debugging.
     */
    fun getVisitedNodes(): Int
}
