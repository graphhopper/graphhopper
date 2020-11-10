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

/**
 * Graph structure used for Contraction Hierarchies. It allows storing and retrieving the
 * levels for a node and creating shortcuts, which are additional 'artificial' edges to speedup
 * traversal in certain cases.
 *
 * @author Peter Karich
 */
public interface CHGraph {

    /**
     * This methods sets the level of the specified node.
     */
    void setLevel(int nodeId, int level);

    /**
     * @return the level of the specified node. The higher the more important the node is. Virtual nodes have the
     * biggest level associated.
     */
    int getLevel(int nodeId);

    /**
     * Returns the profile of this CH graph. This is used to identify the CH graph.
     */
    CHConfig getCHConfig();

    boolean isShortcut(int edgeId);

    /**
     * This method creates a shortcut between a to b which is nearly identical to creating an edge
     * except that it can be excluded or included for certain traversals or algorithms.
     */
    int shortcut(int a, int b, int accessFlags, double weight, int skippedEdge1, int skippedEdge2);

    /**
     * like shortcut(), but for edge-based CH
     *
     * @param origFirst The first original edge that is skipped by this shortcut. For example for the following shortcut
     *                  edge from x to y, which itself skips the shortcuts x->v and v->y the first original edge would
     *                  be x->u: x->u->v->w->y
     * @param origLast  like origFirst, but the last orig edge, i.e w->y in above example
     */
    int shortcutEdgeBased(int a, int b, int accessFlags, double weight, int skippedEdge1, int skippedEdge2, int origFirst, int origLast);

    CHEdgeIteratorState getEdgeIteratorState(int edgeId, int endNode);

    CHEdgeExplorer createEdgeExplorer();

    CHEdgeExplorer createEdgeExplorer(EdgeFilter filter);

    EdgeExplorer createOriginalEdgeExplorer();

    EdgeExplorer createOriginalEdgeExplorer(EdgeFilter filter);

    AllCHEdgesIterator getAllEdges();

    /**
     * @return the number of original edges in this graph (without shortcuts)
     */
    int getOriginalEdges();

    NodeOrderingProvider getNodeOrderingProvider();

    /**
     * @return true if contraction can be started (add shortcuts and set levels), false otherwise
     */
    boolean isReadyForContraction();

    Graph getBaseGraph();

    int getNodes();

    /**
     * @return the number of edges (including shortcuts) in this graph. Equivalent to getAllEdges().length().
     */
    int getEdges();

    /**
     * @return the 'opposite' node of a given edge, so if there is an edge 3-2 and node =2 this returns 3
     */
    int getOtherNode(int edge, int node);

    /**
     * @return true if the edge with id edge is adjacent to node, false otherwise
     */
    boolean isAdjacentToNode(int edge, int node);

    void debugPrint();
}
