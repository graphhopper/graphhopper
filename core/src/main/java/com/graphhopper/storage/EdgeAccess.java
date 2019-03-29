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

import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;

/**
 * @author Peter Karich
 */
abstract class EdgeAccess {
    private static final int NO_NODE = -1;
    // distance of around +-1000 000 meter are ok
    private static final double INT_DIST_FACTOR = 1000d;
    static double MAX_DIST = Integer.MAX_VALUE / INT_DIST_FACTOR;
    final DataAccess edges;
    int E_NODEA, E_NODEB, E_LINKA, E_LINKB, E_DIST, E_FLAGS;

    EdgeAccess(DataAccess edges) {
        this.edges = edges;
    }

    final void init(int E_NODEA, int E_NODEB, int E_LINKA, int E_LINKB, int E_DIST, int E_FLAGS) {
        this.E_NODEA = E_NODEA;
        this.E_NODEB = E_NODEB;
        this.E_LINKA = E_LINKA;
        this.E_LINKB = E_LINKB;
        this.E_DIST = E_DIST;
        this.E_FLAGS = E_FLAGS;
    }

    abstract BaseGraph.EdgeIterable createSingleEdge(EdgeFilter edgeFilter);

    abstract long toPointer(int edgeOrShortcutId);

    abstract boolean isInBounds(int edgeOrShortcutId);

    abstract int getEdgeRef(int nodeId);

    abstract void setEdgeRef(int nodeId, int edgeId);

    abstract int getEntryBytes();

    final void invalidateEdge(long edgePointer) {
        edges.setInt(edgePointer + E_NODEB, NO_NODE);
    }

    static final boolean isInvalidNodeB(int node) {
        return node == EdgeAccess.NO_NODE;
    }

    final void setDist(long edgePointer, double distance) {
        edges.setInt(edgePointer + E_DIST, distToInt(distance));
    }

    /**
     * Translates double distance to integer in order to save it in a DataAccess object
     */
    private int distToInt(double distance) {
        if (distance < 0)
            throw new IllegalArgumentException("Distance cannot be negative: " + distance);
        if (distance > MAX_DIST) {
            distance = MAX_DIST;
        }
        int integ = (int) Math.round(distance * INT_DIST_FACTOR);
        assert integ >= 0 : "distance out of range";
        return integ;
    }

    /**
     * returns distance (already translated from integer to double)
     */
    final double getDist(long pointer) {
        int val = edges.getInt(pointer + E_DIST);
        // do never return infinity even if INT MAX, see #435
        return val / INT_DIST_FACTOR;
    }

    final void readFlags(long edgePointer, IntsRef edgeFlags) {
        int size = edgeFlags.ints.length;
        for (int i = 0; i < size; i++) {
            edgeFlags.ints[i] = edges.getInt(edgePointer + E_FLAGS + i * 4);
        }
    }

    final void writeFlags(long edgePointer, IntsRef edgeFlags) {
        int size = edgeFlags.ints.length;
        for (int i = 0; i < size; i++) {
            edges.setInt(edgePointer + E_FLAGS + i * 4, edgeFlags.ints[i]);
        }
    }

    /**
     * Writes a new edge to the array of edges and adds it to the linked list of edges at nodeA and nodeB
     */
    final int internalEdgeAdd(int newEdgeId, int nodeA, int nodeB) {
        writeEdge(newEdgeId, nodeA, nodeB, EdgeIterator.NO_EDGE, EdgeIterator.NO_EDGE);
        long edgePointer = toPointer(newEdgeId);

        int edge = getEdgeRef(nodeA);
        if (edge > EdgeIterator.NO_EDGE)
            edges.setInt(E_LINKA + edgePointer, edge);
        setEdgeRef(nodeA, newEdgeId);

        if (nodeA != nodeB) {
            edge = getEdgeRef(nodeB);
            if (edge > EdgeIterator.NO_EDGE)
                edges.setInt(E_LINKB + edgePointer, edge);
            setEdgeRef(nodeB, newEdgeId);
        }
        return newEdgeId;
    }

    final int getNodeA(long edgePointer) {
        return edges.getInt(edgePointer + E_NODEA);
    }

    final int getNodeB(long edgePointer) {
        return edges.getInt(edgePointer + E_NODEB);
    }

    final int getLinkA(long edgePointer) {
        return edges.getInt(edgePointer + E_LINKA);
    }

    final int getLinkB(long edgePointer) {
        return edges.getInt(edgePointer + E_LINKB);
    }

    final int getOtherNode(int nodeThis, long edgePointer) {
        int nodeA = getNodeA(edgePointer);
        return nodeThis == nodeA ? getNodeB(edgePointer) : nodeA;
    }

    /**
     * Writes plain edge information to the edges index
     */
    final long writeEdge(int edgeId, int nodeA, int nodeB, int nextEdgeA, int nextEdgeB) {
        if (edgeId < 0 || edgeId == EdgeIterator.NO_EDGE)
            throw new IllegalStateException("Cannot write edge with illegal ID:" + edgeId + "; nodeA:" + nodeA + ", nodeB:" + nodeB);

        long edgePointer = toPointer(edgeId);
        edges.setInt(edgePointer + E_NODEA, nodeA);
        edges.setInt(edgePointer + E_NODEB, nodeB);
        edges.setInt(edgePointer + E_LINKA, nextEdgeA);
        edges.setInt(edgePointer + E_LINKB, nextEdgeB);
        return edgePointer;
    }

    /**
     * This method disconnects the specified edge from the list of edges of the specified node. It
     * does not release the freed space to be reused.
     *
     * @param edgeToUpdatePointer if it is negative then the nextEdgeId will be saved to refToEdges of nodes
     */
    final long internalEdgeDisconnect(int edgeToRemove, long edgeToUpdatePointer, int baseNode) {
        long edgeToRemovePointer = toPointer(edgeToRemove);
        // an edge is shared across the two nodes even if the edge is not in both directions
        // so we need to know two edge-pointers pointing to the edge before edgeToRemovePointer
        int nextEdgeId = getNodeA(edgeToRemovePointer) == baseNode ? getLinkA(edgeToRemovePointer) : getLinkB(edgeToRemovePointer);
        if (edgeToUpdatePointer < 0) {
            setEdgeRef(baseNode, nextEdgeId);
        } else {
            // adjNode is different for the edge we want to update with the new link
            long link = getNodeA(edgeToUpdatePointer) == baseNode ? edgeToUpdatePointer + E_LINKA : edgeToUpdatePointer + E_LINKB;
            edges.setInt(link, nextEdgeId);
        }
        return edgeToRemovePointer;
    }

    final EdgeIteratorState getEdgeProps(int edgeId, int adjNode) {
        if (edgeId <= EdgeIterator.NO_EDGE)
            throw new IllegalStateException("edgeId invalid " + edgeId + ", " + this);

        BaseGraph.EdgeIterable edge = createSingleEdge(EdgeFilter.ALL_EDGES);
        if (edge.init(edgeId, adjNode))
            return edge;

        // if edgeId exists but adjacent nodes do not match
        return null;
    }
}
