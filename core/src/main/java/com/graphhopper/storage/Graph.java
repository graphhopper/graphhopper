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

import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.shapes.BBox;

/**
 * An interface to represent a (geo) graph - suited for efficient storage as it can be requested via
 * indices called node IDs. To get the lat,lon point you need to set up a LocationIndex instance.
 * <p>
 *
 * @author Peter Karich
 */
public interface Graph {
    /**
     * @return a graph which behaves like an unprepared graph and e.g. the normal unidirectional
     * Dijkstra or any graph traversal algorithm can be executed.
     */
    Graph getBaseGraph();

    /**
     * @return the number of created locations - via setNode() or edge()
     */
    int getNodes();

    /**
     * Creates a node explorer to access node properties.
     */
    NodeAccess getNodeAccess();

    /**
     * Returns the implicit bounds of this graph calculated from the lat,lon input of setNode
     */
    BBox getBounds();

    /**
     * Creates an edge between the nodes a and b. To set distance or access use the returned edge
     * and e.g. edgeState.setDistance
     * <p>
     *
     * @param a the index of the starting (tower) node of the edge
     * @param b the index of the ending (tower) node of the edge
     * @return the newly created edge
     */
    EdgeIteratorState edge(int a, int b);

    /**
     * Use edge(a,b).setDistance().setFlags instead
     */
    EdgeIteratorState edge(int a, int b, double distance, boolean bothDirections);

    /**
     * Returns a wrapper over the specified edgeId.
     *
     * @param adjNode is the node that will be returned via adjNode(). If adjNode is
     *                Integer.MIN_VALUE then the edge with uncertain values for adjNode and baseNode (two
     *                possibilities) will be returned.
     * @return an edge iterator state or potentially null if adjNode does not match
     * @throws IllegalStateException if edgeId is not valid
     */
    EdgeIteratorState getEdgeIteratorState(int edgeId, int adjNode);

    /**
     * @return all edges in this graph, where baseNode will be the smaller node.
     */
    AllEdgesIterator getAllEdges();

    /**
     * Returns an EdgeExplorer which makes it possible to traverse all filtered edges of a specific
     * node. Reduce calling this method as much as possible, e.g. create an explorer before a for
     * loop!
     * <p>
     *
     * @see EdgeExplorer
     * @see Graph#createEdgeExplorer()
     */
    EdgeExplorer createEdgeExplorer(EdgeFilter filter);

    /**
     * @see Graph#createEdgeExplorer(com.graphhopper.routing.util.EdgeFilter)
     */
    EdgeExplorer createEdgeExplorer();

    /**
     * Copy this Graph into the specified Graph g.
     * <p>
     *
     * @return the specified Graph g
     */
    Graph copyTo(Graph g);

    /**
     * @return the graph extension like a TurnCostExtension
     */
    GraphExtension getExtension();
}
