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

import com.graphhopper.routing.util.CarStreetType;
import com.graphhopper.util.EdgeSkipIterator;

/**
 * @author Peter Karich
 */
public class LevelGraphStorage extends GraphStorage implements LevelGraph {

    private final int I_SKIP_EDGE;
    private final int I_LEVEL;

    public LevelGraphStorage(Directory dir) {
        this(dir, dir.findCreate("nodes"), dir.findCreate("edges"));
    }

    LevelGraphStorage(Directory dir, DataAccess nodes, DataAccess edges) {
        super(dir, nodes, edges);
        I_SKIP_EDGE = nextEdgeEntryIndex();
        I_LEVEL = nextNodeEntryIndex();
        initNodeAndEdgeEntrySize();
    }

    @Override public final void setLevel(int index, int level) {
        ensureNodeIndex(index);
        nodes.setInt((long) index * nodeEntrySize + I_LEVEL, level);
    }

    @Override public final int getLevel(int index) {
        ensureNodeIndex(index);
        return nodes.getInt((long) index * nodeEntrySize + I_LEVEL);
    }

    @Override protected GraphStorage newThis(Directory dir, DataAccess nodes, DataAccess edges) {
        return new LevelGraphStorage(dir, nodes, edges);
    }

    public EdgeSkipIterator newEdge(int a, int b, double distance, boolean bothDir) {
        return newEdge(a, b, distance, CarStreetType.flagsDefault(bothDir));
    }

    public EdgeSkipIterator newEdge(int a, int b, double distance, int flags) {
        return shortcut(a, b, distance, flags, -1);
    }

    @Override public void edge(int a, int b, double distance, int flags) {
        shortcut(a, b, distance, flags, -1);
    }

    @Override public EdgeSkipIterator shortcut(int a, int b, double distance, int flags, int skippedEdge) {
        ensureNodeIndex(a);
        ensureNodeIndex(b);
        return internalEdgeAdd(a, b, distance, flags, skippedEdge);
    }

    protected EdgeSkipIterator internalEdgeAdd(int fromNodeId, int toNodeId, double dist, int flags, int skippedEdge) {
        int newOrExistingEdge = nextEdge();
        connectNewEdge(fromNodeId, newOrExistingEdge);
        connectNewEdge(toNodeId, newOrExistingEdge);
        writeEdge(newOrExistingEdge, fromNodeId, toNodeId, EMPTY_LINK, EMPTY_LINK, dist, flags);
        edges.setInt((long) newOrExistingEdge * edgeEntrySize + I_SKIP_EDGE, skippedEdge);
        return new EdgeSkipIteratorImpl(newOrExistingEdge);
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

        @Override public void skippedEdge(int edgeId) {
            edges.setInt(edgePointer + I_SKIP_EDGE, edgeId);
        }

        @Override public int skippedEdge() {
            return edges.getInt(edgePointer + I_SKIP_EDGE);
        }
    }

    @Override
    public EdgeSkipIterator getEdgeProps(int edgeId, int endNode) {
        return (EdgeSkipIterator) super.getEdgeProps(edgeId, endNode);
    }

    @Override
    protected SingleEdge createSingleEdge(long edgePointer) {
        return new SingleLevelEdge(edgePointer);
    }

    protected class SingleLevelEdge extends SingleEdge implements EdgeSkipIterator {

        public SingleLevelEdge(long edgePointer) {
            super(edgePointer);
        }

        @Override public void skippedEdge(int node) {
            edges.setInt(edgePointer + I_SKIP_EDGE, node);
        }

        @Override public int skippedEdge() {
            return edges.getInt(edgePointer + I_SKIP_EDGE);
        }
    }

    @Override
    public EdgeSkipIterator getAllEdges() {
        return new AllEdgeSkipIterator();
    }

    public class AllEdgeSkipIterator extends AllEdgeIterator implements EdgeSkipIterator {

        @Override public void skippedEdge(int node) {
            edges.setInt(edgePointer + I_SKIP_EDGE, node);
        }

        @Override public int skippedEdge() {
            return edges.getInt(edgePointer + I_SKIP_EDGE);
        }
    }
}
