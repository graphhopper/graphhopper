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
import com.graphhopper.util.BitUtil;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;

/**
 * @author Peter Karich
 */
abstract class EdgeAccess {
    static final int NO_NODE = -1;
    // distance of around +-1000 000 meter are ok
    private static final double INT_DIST_FACTOR = 1000d;
    static double MAX_DIST = (Integer.MAX_VALUE - 1) / INT_DIST_FACTOR;
    final DataAccess edges;
    private final BitUtil bitUtil;
    int E_NODEA, E_NODEB, E_LINKA, E_LINKB, E_DIST, E_FLAGS;
    private boolean flagsSizeIsLong;

    EdgeAccess(DataAccess edges, BitUtil bitUtil) {
        this.edges = edges;
        this.bitUtil = bitUtil;
    }

    final void init(int E_NODEA, int E_NODEB, int E_LINKA, int E_LINKB, int E_DIST, int E_FLAGS, boolean flagsSizeIsLong) {
        this.E_NODEA = E_NODEA;
        this.E_NODEB = E_NODEB;
        this.E_LINKA = E_LINKA;
        this.E_LINKB = E_LINKB;
        this.E_DIST = E_DIST;
        this.E_FLAGS = E_FLAGS;
        this.flagsSizeIsLong = flagsSizeIsLong;
    }

    abstract BaseGraph.EdgeIterable createSingleEdge(EdgeFilter edgeFilter);

    abstract long toPointer(int edgeOrShortcutId);

    abstract boolean isInBounds(int edgeOrShortcutId);

    abstract long reverseFlags(long edgePointer, long flags);

    abstract int getEdgeRef(int nodeId);

    abstract void setEdgeRef(int nodeId, int edgeId);

    abstract int getEntryBytes();

    final void invalidateEdge(long edgePointer) {
        edges.setInt(edgePointer + E_NODEA, NO_NODE);
    }

    final void setDist(long edgePointer, double distance) {
        edges.setInt(edgePointer + E_DIST, distToInt(distance));
    }

    /**
     * Translates double distance to integer in order to save it in a DataAccess object
     */
    private int distToInt(double distance) {
        int integ = (int) (distance * INT_DIST_FACTOR);
        if (integ < 0)
            throw new IllegalArgumentException("Distance cannot be negative: " + distance);
        if (integ >= Integer.MAX_VALUE)
            return Integer.MAX_VALUE;
        // throw new IllegalArgumentException("Distance too large leading to overflowed integer (#435): " + distance + " ");
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

    final long getFlags_(long edgePointer, boolean reverse) {
        int low = edges.getInt(edgePointer + E_FLAGS);
        long resFlags = low;
        if (flagsSizeIsLong) {
            int high = edges.getInt(edgePointer + E_FLAGS + 4);
            resFlags = bitUtil.combineIntsToLong(low, high);
        }
        if (reverse)
            resFlags = reverseFlags(edgePointer, resFlags);

        return resFlags;
    }

    final long setFlags_(long edgePointer, boolean reverse, long flags) {
        if (reverse)
            flags = reverseFlags(edgePointer, flags);

        edges.setInt(edgePointer + E_FLAGS, bitUtil.getIntLow(flags));

        if (flagsSizeIsLong)
            edges.setInt(edgePointer + E_FLAGS + 4, bitUtil.getIntHigh(flags));

        return flags;
    }

    /**
     * Write new edge between nodes fromNodeId, and toNodeId both to nodes index and edges index
     */
    final int internalEdgeAdd(int newEdgeId, int fromNodeId, int toNodeId) {
        writeEdge(newEdgeId, fromNodeId, toNodeId, EdgeIterator.NO_EDGE, EdgeIterator.NO_EDGE);
        connectNewEdge(fromNodeId, toNodeId, newEdgeId);
        if (fromNodeId != toNodeId)
            connectNewEdge(toNodeId, fromNodeId, newEdgeId);
        return newEdgeId;
    }

    final int getOtherNode(int nodeThis, long edgePointer) {
        int nodeA = edges.getInt(edgePointer + E_NODEA);
        if (nodeA == nodeThis)
            // return b
            return edges.getInt(edgePointer + E_NODEB);
        // return a
        return nodeA;
    }

    private long _getLinkPosInEdgeArea(int nodeThis, int nodeOther, long edgePointer) {
        return nodeThis <= nodeOther ? edgePointer + E_LINKA : edgePointer + E_LINKB;
    }

    final int getEdgeRef(int nodeThis, int nodeOther, long edgePointer) {
        return edges.getInt(_getLinkPosInEdgeArea(nodeThis, nodeOther, edgePointer));
    }

    final void connectNewEdge(int fromNode, int otherNode, int newOrExistingEdge) {
        int edge = getEdgeRef(fromNode);
        if (edge > EdgeIterator.NO_EDGE) {
            long edgePointer = toPointer(newOrExistingEdge);
            long lastLink = _getLinkPosInEdgeArea(fromNode, otherNode, edgePointer);
            edges.setInt(lastLink, edge);
        }
        setEdgeRef(fromNode, newOrExistingEdge);
    }

    final long writeEdge(int edgeId, int nodeThis, int nodeOther, int nextEdge, int nextEdgeOther) {
        if (nodeThis > nodeOther) {
            int tmp = nodeThis;
            nodeThis = nodeOther;
            nodeOther = tmp;
            tmp = nextEdge;
            nextEdge = nextEdgeOther;
            nextEdgeOther = tmp;
        }
        if (edgeId < 0 || edgeId == EdgeIterator.NO_EDGE)
            throw new IllegalStateException("Cannot write edge with illegal ID:" + edgeId + "; nodeThis:" + nodeThis + ", nodeOther:" + nodeOther);

        long edgePointer = toPointer(edgeId);
        edges.setInt(edgePointer + E_NODEA, nodeThis);
        edges.setInt(edgePointer + E_NODEB, nodeOther);
        edges.setInt(edgePointer + E_LINKA, nextEdge);
        edges.setInt(edgePointer + E_LINKB, nextEdgeOther);
        return edgePointer;
    }

    /**
     * This method disconnects the specified edge from the list of edges of the specified node. It
     * does not release the freed space to be reused.
     * <p>
     *
     * @param edgeToUpdatePointer if it is negative then the nextEdgeId will be saved to refToEdges
     *                            of nodes
     */
    final long internalEdgeDisconnect(int edgeToRemove, long edgeToUpdatePointer, int baseNode, int adjNode) {
        long edgeToRemovePointer = toPointer(edgeToRemove);
        // an edge is shared across the two nodes even if the edge is not in both directions
        // so we need to know two edge-pointers pointing to the edge before edgeToRemovePointer
        int nextEdgeId = getEdgeRef(baseNode, adjNode, edgeToRemovePointer);
        if (edgeToUpdatePointer < 0) {
            setEdgeRef(baseNode, nextEdgeId);
        } else {
            // adjNode is different for the edge we want to update with the new link
            long link = edges.getInt(edgeToUpdatePointer + E_NODEA) == baseNode
                    ? edgeToUpdatePointer + E_LINKA : edgeToUpdatePointer + E_LINKB;
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
