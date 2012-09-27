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
package de.jetsli.graph.storage;

import de.jetsli.graph.util.EdgeSkipIterator;

/**
 * @author Peter Karich
 */
public class PriorityGraphStorage extends GraphStorage implements PriorityGraph {

    private static final int I_SC_NODE = 6;
    private static final int I_PRIO = 3;

    public PriorityGraphStorage(Directory dir) {
        super(dir);
        edgeEntrySize = 7;
        nodeEntrySize = 4;
    }

    @Override
    public void setPriority(int index, int prio) {
        ensureNodeIndex(index);
        nodes.setInt((long) index * nodeEntrySize + I_PRIO, prio);
    }

    @Override
    public int getPriority(int index) {
        ensureNodeIndex(index);
        return nodes.getInt((long) index * nodeEntrySize + I_PRIO);
    }

    @Override
    protected GraphStorage createThis(Directory dir) {
        return new PriorityGraphStorage(dir);
    }

    @Override public void edge(int a, int b, double distance, int flags) {
        shortcut(a, b, distance, flags, -1);
    }

    @Override public void shortcut(int a, int b, double distance, int flags, int shortcutNode) {        
        ensureNodeIndex(a);
        ensureNodeIndex(b);
        internalEdgeAdd(a, b, distance, flags, shortcutNode);
    }

    protected void internalEdgeAdd(int fromNodeId, int toNodeId, double dist, int flags, int shortcutNode) {
        int newOrExistingEdge = nextEdge();
        connectNewEdge(fromNodeId, newOrExistingEdge);
        connectNewEdge(toNodeId, newOrExistingEdge);
        writeEdge(newOrExistingEdge, fromNodeId, toNodeId, EMPTY_LINK, EMPTY_LINK, flags, dist, shortcutNode);
    }

    protected void writeEdge(int edge, int nodeThis, int nodeOther, int nextEdge,
            int nextEdgeOther, int flags, double dist, int shortcutNode) {

        super.writeEdge(edge, nodeThis, nodeOther, nextEdge,
                nextEdgeOther, flags, dist);
        edges.setInt((long) edge * edgeEntrySize + I_SC_NODE, shortcutNode);
    }

    @Override public EdgeSkipIterator getEdges(int nodeId) {
        return new EdgeSkipIteratorImpl(nodeId, true, true);
    }

    @Override public EdgeSkipIterator getIncoming(int nodeId) {
        return new EdgeSkipIteratorImpl(nodeId, true, false);
    }

    @Override public EdgeSkipIterator getOutgoing(int nodeId) {
        return new EdgeSkipIteratorImpl(nodeId, false, true);
    }

    public class EdgeSkipIteratorImpl extends EdgeIterable implements EdgeSkipIterator {

        public EdgeSkipIteratorImpl(int node, boolean in, boolean out) {
            super(node, in, out);
        }

        @Override public void skippedNode(int node) {
            edges.setInt(edgePointer + I_SC_NODE, node);
        }

        @Override public int skippedNode() {
            return edges.getInt(edgePointer + I_SC_NODE);
        }

        @Override public void distance(double dist) {
            distance = dist;
            edges.setInt(edgePointer + I_DIST, distToInt(dist));
        }

        @Override public void flags(int fl) {
            flags = fl;
            int nep = edges.getInt(getLinkPosInEdgeArea(fromNode, nodeId, edgePointer));
            int neop = edges.getInt(getLinkPosInEdgeArea(nodeId, fromNode, edgePointer));
            writeEdge((int) (edgePointer / 4), fromNode, nodeId, nep, neop, flags, distance);
        }
    }
}
