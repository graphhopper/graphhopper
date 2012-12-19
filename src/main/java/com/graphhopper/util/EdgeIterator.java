/*
 *  Copyright 2012 Peter Karich 
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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
 * methods.
 *
 * Usage:
 * <pre>
 * // calls to iter.node(), distance() without next() will cause undefined behaviour
 * EdgeIterator iter = graph.getOutgoing(nodeId);
 * // or similar
 * EdgeIterator iter = graph.getIncoming(nodeId);
 * while(iter.next()) {
 *   int baseNodeId = iter.baseNode(); // equal to nodeId
 *   int outerNodeId = iter.node();
 *   ...
 * }
 *
 * @author Peter Karich
 */
public interface EdgeIterator {

    /**
     * To be called to go to the next edge
     */
    boolean next();

    /**
     * @return the edge id of the current edge. Although the current implementation uses an index
     * starting from 1, do not make any assumptions about it.
     */
    int edge();

    /**
     * If you retrieve edges via "edgeIterator = graph.getXY(nodeId)" then the returned node is
     * identical to nodeId. Often only used instead of nodeId for convenience reasons. Do not
     * confuse this with <i>source</i> node of a directed edge.
     *
     * @return the node id of the 'base' node
     * @see EdgeIterator
     */
    int baseNode();

    /**
     * @return the node id of the 'outer' node (connected to baseNode) for the current edge.
     * @see EdgeIterator
     */
    int node();

    /**
     * @return the distance of the current edge edge
     */
    double distance();

    int flags();

    boolean isEmpty();
    public static final int NO_EDGE = -1;
}
