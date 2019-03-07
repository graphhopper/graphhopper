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
package com.graphhopper.storage;

import com.graphhopper.routing.ch.NodeOrderingProvider;
import com.graphhopper.routing.util.AllCHEdgesIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.util.CHEdgeExplorer;
import com.graphhopper.util.CHEdgeIteratorState;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIteratorState;

/**
 * Extended graph interface which supports Contraction Hierarchies. Ie. storing and retrieving the
 * levels for a node and creating shortcuts, which are additional 'artificial' edges to speedup
 * traversal in certain cases.
 *
 * @author Peter Karich
 */
public interface CHGraph extends Graph {

    /**
     * This methods sets the level of the specified node.
     */
    void setLevel(int nodeId, int level);

    /**
     * @return the level of the specified node. The higher the more important the node is. Virtual nodes have the
     * biggest level associated.
     */
    int getLevel(int nodeId);

    boolean isShortcut(int edgeId);

    /**
     * This method creates a shortcut between a to b which is nearly identical to creating an edge
     * except that it can be excluded or included for certain traversals or algorithms.
     */
    CHEdgeIteratorState shortcut(int a, int b);

    int shortcut(int a, int b, int accessFlags, double weight, double distance, int skippedEdge1, int skippedEdge2);

    int shortcutEdgeBased(int a, int b, int accessFlags, double weight, double distance, int skippedEdge1, int skippedEdge2, int origFirst, int origLast);

    @Override
    CHEdgeIteratorState getEdgeIteratorState(int edgeId, int endNode);

    @Override
    CHEdgeExplorer createEdgeExplorer();

    @Override
    CHEdgeExplorer createEdgeExplorer(EdgeFilter filter);

    EdgeExplorer createOriginalEdgeExplorer();

    EdgeExplorer createOriginalEdgeExplorer(EdgeFilter filter);

    @Override
    AllCHEdgesIterator getAllEdges();

    /**
     * Disconnects the edges (higher to lower node) via the specified edgeState pointing from lower to
     * higher node.
     * <p>
     *
     * @param edgeState the edge from lower to higher
     */
    void disconnect(CHEdgeExplorer edgeExplorer, EdgeIteratorState edgeState);

    /**
     * @return the number of original edges in this graph (without shortcuts)
     */
    int getOriginalEdges();

    NodeOrderingProvider getNodeOrderingProvider();

    /**
     * @return true if contraction can be started (add shortcuts and set levels), false otherwise
     */
    boolean isReadyForContraction();

}
