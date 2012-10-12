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
package com.graphhopper.storage;

import com.graphhopper.util.EdgeSkipIterator;

/**
 * @author Peter Karich
 */
public class LevelGraphStorage extends GraphStorage implements LevelGraph {

    private final int I_SC_NODE, I_ORIG_EDGES;
    private final int I_LEVEL;

    public LevelGraphStorage(Directory dir) {
        super(dir);
        I_SC_NODE = nextEdgeEntryIndex();
        I_ORIG_EDGES = nextEdgeEntryIndex();
        I_LEVEL = nextNodeEntryIndex();
        initNodeAndEdgeEntrySize();
    }

    @Override public void setLevel(int index, int level) {
        ensureNodeIndex(index);
        nodes.setInt((long) index * nodeEntrySize + I_LEVEL, level);
    }

    @Override public int getLevel(int index) {
        ensureNodeIndex(index);
        return nodes.getInt((long) index * nodeEntrySize + I_LEVEL);
    }

    @Override protected GraphStorage newThis(Directory dir) {
        return new LevelGraphStorage(dir);
    }

    @Override public void edge(int a, int b, double distance, int flags) {
        shortcut(a, b, distance, flags, -1);
    }

    @Override public EdgeSkipIterator shortcut(int a, int b, double distance, int flags, int shortcutNode) {
        ensureNodeIndex(a);
        ensureNodeIndex(b);
        return internalEdgeAdd(a, b, distance, flags, shortcutNode);
    }

    protected EdgeSkipIterator internalEdgeAdd(int fromNodeId, int toNodeId, double dist, int flags, int shortcutNode) {
        int newOrExistingEdge = nextEdge();
        connectNewEdge(fromNodeId, newOrExistingEdge);
        connectNewEdge(toNodeId, newOrExistingEdge);
        writeEdge(newOrExistingEdge, fromNodeId, toNodeId, EMPTY_LINK, EMPTY_LINK, flags, dist, shortcutNode);
        return new EdgeSkipIteratorImpl(newOrExistingEdge);
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

        public EdgeSkipIteratorImpl(int edge) {
            super(edge);
        }

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
            writeEdge((int) (edgePointer / edgeEntrySize), fromNode, nodeId, nep, neop, flags, distance);
        }

        @Override public int originalEdges() {
            return edges.getInt(edgePointer + I_ORIG_EDGES);
        }

        @Override public void originalEdges(int no) {
            edges.setInt(edgePointer + I_ORIG_EDGES, no);
        }
    }

    @Override
    public EdgeSkipIterator getAllEdges() {
        return new AllEdgeSkipIterator();
    }

    public class AllEdgeSkipIterator extends AllEdgeIterator implements EdgeSkipIterator {

        @Override public void skippedNode(int node) {
            edges.setInt(edgePointer + I_SC_NODE, node);
        }

        @Override public int skippedNode() {
            return edges.getInt(edgePointer + I_SC_NODE);
        }

        @Override public void distance(double dist) {
            edges.setInt(edgePointer + I_DIST, distToInt(dist));
        }

        @Override public void flags(int flags) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override public int originalEdges() {
            return edges.getInt(edgePointer + I_ORIG_EDGES);
        }

        @Override public void originalEdges(int no) {
            edges.setInt(edgePointer + I_ORIG_EDGES, no);
        }
    }
}
