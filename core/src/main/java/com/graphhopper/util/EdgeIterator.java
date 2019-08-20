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
package com.graphhopper.util;

/**
 * Iterates through all edges of one node. Avoids object creation in-between via direct access
 * methods. If you want to access some properties of an 'edge' (i.e. the current state) for later
 * usage store it via edgeIterator.detach() or edgeIterator.getEdge() instead of the iterator
 * itself. Usage:
 * <pre>
 * EdgeExplorer explorer = graph.createEdgeExplorer();
 * EdgeIterator iter = explorer.setBaseNode(nodeId);
 * // calls to iter.getAdjNode(), getDistance() without calling next() will cause undefined behaviour!
 * while(iter.next()) {
 *   int baseNodeId = iter.getBaseNode(); // equal to nodeId
 *   int adjacentNodeId = iter.getAdjNode(); // this is the node where this edge state is "pointing to"
 *   ...
 * }
 * </pre>
 * <p>
 *
 * @author Peter Karich
 * @see EdgeIteratorState
 * @see EdgeExplorer
 */
public interface EdgeIterator extends EdgeIteratorState {
    /**
     * Integer value used in places where normally an edge would be expected, but none is given. For example in the
     * shortest path tree of route calculations every child element should have an incoming edge, but for the root item
     * there is no parent so we would use this value instead.
     */
    int NO_EDGE = -1;

    /**
     * Integer value used in places where normally an edge would be expected, but no specific edge shall be specified.
     */
    int ANY_EDGE = -2;

    /**
     * To be called to go to the next edge state.
     * <p>
     *
     * @return true if an edge state is available
     */
    boolean next();

    class Edge {
        /**
         * Checks if a given integer edge ID is valid or not. Edge IDs >= 0 are considered valid, while negative
         * values are considered as invalid. However, some negative values are used as special values, e.g. {@link
         * #NO_EDGE}.
         */
        public static boolean isValid(int edgeId) {
            return edgeId >= 0;
        }
    }
}
