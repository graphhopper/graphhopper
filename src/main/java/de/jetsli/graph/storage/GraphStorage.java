/*
 *  Copyright 2012 Peter Karich info@jetsli.de
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

import de.jetsli.graph.routing.util.CarStreetType;
import de.jetsli.graph.util.EdgeIterator;
import de.jetsli.graph.util.shapes.BBox;

/**
 * The main implementation which handles nodes and edges file format. It can be used with different
 * Directory implementations like RAMDirectory for fast and read-thread safe usage which can be even
 * flushed to disc or via MMapDirectory for unbounded heap-memory (and not thread safe) usage.
 *
 * @author Peter Karich
 */
public class GraphStorage implements Graph, Storable {

    private static final int EMPTY_LINK = 0;
    private static final float INC_FACTOR = 1.5f;
    private static final float INT_FACTOR = 1000000f;
    private Directory dir;
    // TODO make the necessary variables persistent
    // nodeA,nodeB,linkA,linkB,dist,flags,(shortcutnode)
    private static final int I_NODEA = 0, I_NODEB = 1, I_LINKA = 2, I_LINKB = 3, I_FLAGS = 4, I_DIST = 5;
    private int edgeEntrySize = 6;
    private DataAccess edges;
    private int edgeCount;
    // edgeRef,deleted?,lat,lon,(prio),
    private static final int I_EDGE_REF = 0, I_LAT = 1, I_LON = 2;
    private int nodeEntrySize = 3;
    private DataAccess nodes;
    private int nodeCount;
    private BBox bounds;

    // TODO
    // clone => how to create new Graph? => new Directory!
    // in place delete ? 
    // optimize
    // flush/close
    public GraphStorage(Directory dir) {
        this.dir = dir;
        edges = dir.createDataAccess("edges");
        nodes = dir.createDataAccess("nodes");
        this.bounds = BBox.INVERSE.clone();
    }

    @Override
    public boolean loadExisting() {
        if (edges.loadExisting()) {
            if (!nodes.loadExisting())
                throw new IllegalStateException("corrupt file?");

            return true;
        }
        return false;
    }

    public GraphStorage createNew(int nodeCount) {
        nodes.createNew((long) nodeCount * 4 * nodeCount);
        edges.createNew((long) nodeCount * 4 * 2 * edgeCount);
        return this;
    }

    public void ensureNodeIndex(int nodeIndex) {
        if (nodeIndex < nodeCount)
            return;

        nodeCount = nodeIndex + 1;
        if (nodeCount <= nodes.capacity() / 4 / nodeEntrySize)
            return;

        long cap = Math.max(10, Math.round((long) nodeCount * INC_FACTOR * nodeEntrySize));
        nodes.ensureCapacity(cap * 4);
    }

    public void ensureEdgeIndex(int edgeIndex) {
        if (edgeIndex < edgeCount)
            return;
        edgeCount = edgeIndex + 1;
        if (edgeCount <= edges.capacity() / 4 / edgeEntrySize)
            return;

        long cap = Math.max(10, Math.round((long) edgeCount * INC_FACTOR * edgeEntrySize));
        edges.ensureCapacity(cap * 4);
    }

    @Override
    public int getNodes() {
        return nodeCount;
    }

    /**
     * @return the number of edges where a two direction edge is counted only once.
     */
    public int getEdges() {
        return edgeCount;
    }

    @Override
    public void setNode(int index, double lat, double lon) {
        ensureNodeIndex(index);
        nodes.setInt((long) index * nodeEntrySize + I_LAT, doubleToInt(lat));
        nodes.setInt((long) index * nodeEntrySize + I_LON, doubleToInt(lon));
        if (lat > bounds.maxLat)
            bounds.maxLat = lat;
        if (lat < bounds.minLat)
            bounds.minLat = lat;
        if (lon > bounds.maxLon)
            bounds.maxLon = lon;
        if (lon < bounds.minLon)
            bounds.minLon = lon;
    }

    // TODO really use the same factor for latitude and distance?
    double intToDouble(int i) {
        return (double) i / INT_FACTOR;
    }

    int doubleToInt(double f) {
        return (int) (f * INT_FACTOR);
    }

    private double getDist(long pointer) {
        return intToDouble(edges.getInt(pointer + I_DIST));
    }

    @Override
    public double getLatitude(int index) {
        return intToDouble(nodes.getInt((long) index * nodeEntrySize + I_LAT));
    }

    @Override
    public double getLongitude(int index) {
        return intToDouble(nodes.getInt((long) index * nodeEntrySize + I_LON));
    }

    @Override
    public BBox getBounds() {
        return bounds;
    }

    @Override
    public void edge(int a, int b, double distance, boolean bothDirections) {
        edge(a, b, distance, CarStreetType.flagsDefault(bothDirections));
    }

    @Override
    public void edge(int a, int b, double distance, int flags) {
        ensureNodeIndex(Math.max(a, b));
        internalEdgeAdd(a, b, distance, flags);
    }

    protected void internalEdgeAdd(int fromNodeId, int toNodeId, double dist, int flags) {
        int newOrExistingEdge = nextEdge();
        connectNewEdge(fromNodeId, newOrExistingEdge);
        connectNewEdge(toNodeId, newOrExistingEdge);
        writeEdge(newOrExistingEdge, fromNodeId, toNodeId, EMPTY_LINK, EMPTY_LINK, flags, dist);
    }

    protected int nextEdge() {
        edgeCount++;
        if (edgeCount < 0)
            throw new IllegalStateException("too many edges. new edge pointer would be negative.");
        return edgeCount;
    }

    protected void connectNewEdge(int fromNodeId, int newOrExistingEdge) {
        long nodePointer = (long) fromNodeId * nodeEntrySize;
        int edge = nodes.getInt(nodePointer + I_EDGE_REF);
        if (edge > 0) {
            // append edge and overwrite EMPTY_LINK
            long lastEdge = getLastEdge(fromNodeId, edge);
            edges.setInt(lastEdge, newOrExistingEdge);
        } else {
            nodes.setInt(nodePointer + I_EDGE_REF, newOrExistingEdge);
        }
    }

    // writes distance, flags, nodeThis, *nodeOther* and nextEdgePointer
    protected void writeEdge(int edge, int nodeThis, int nodeOther,
            int nextEdge, int nextEdgeOther, int flags, double dist) {
        ensureEdgeIndex(edge);

        if (nodeThis > nodeOther) {
            int tmp = nodeThis;
            nodeThis = nodeOther;
            nodeOther = tmp;

            tmp = nextEdge;
            nextEdge = nextEdgeOther;
            nextEdgeOther = tmp;

            flags = CarStreetType.swapDirection(flags);
        }

        long edgePointer = (long) edge * edgeEntrySize;
        edges.setInt(edgePointer + I_NODEA, nodeThis);
        edges.setInt(edgePointer + I_NODEB, nodeOther);
        edges.setInt(edgePointer + I_LINKA, nextEdge);
        edges.setInt(edgePointer + I_LINKB, nextEdgeOther);
        edges.setInt(edgePointer + I_LINKB, nextEdgeOther);
        edges.setInt(edgePointer + I_FLAGS, flags);
        edges.setInt(edgePointer + I_DIST, doubleToInt(dist));
    }

    protected long getLinkPosInEdgeArea(int nodeThis, int nodeOther, long edgePointer) {
        if (nodeThis <= nodeOther)
            return edgePointer + I_LINKA;
        return edgePointer + I_LINKB;
    }

    private long getLastEdge(int nodeThis, long edgePointer) {
        long lastLink = -1;
        int i = 0;
        int otherNode;
        for (; i < 1000; i++) {
            edgePointer *= edgeEntrySize;
            otherNode = getOtherNode(nodeThis, edgePointer);
            lastLink = getLinkPosInEdgeArea(nodeThis, otherNode, edgePointer);
            edgePointer = edges.getInt(lastLink);
            if (edgePointer == EMPTY_LINK)
                break;
        }

        if (i >= 1000)
            throw new IllegalStateException("endless loop? edge count is probably not higher than " + i);
        return lastLink;
    }

    private int getOtherNode(int nodeThis, long edgePointer) {
        int nodeA = edges.getInt(edgePointer + I_NODEA);
        if (nodeA == nodeThis)
            // return b
            return edges.getInt(edgePointer + I_NODEB);
        // return a
        return nodeA;
    }

    public EdgeIterator getAllEdges() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public EdgeIterator getEdges(int index) {
        return new EdgeIterable(index, true, true);
    }

    @Override
    public EdgeIterator getIncoming(int index) {
        return new EdgeIterable(index, true, false);
    }

    @Override
    public EdgeIterator getOutgoing(int index) {
        return new EdgeIterable(index, false, true);
    }

    protected class EdgeIterable implements EdgeIterator {

        int edgePointer;
        boolean in;
        boolean out;
        boolean foundNext;
        // edge properties        
        int flags;
        double distance;
        int nodeId;
        final int fromNode;
        int nextEdge;

        public EdgeIterable(int node, boolean in, boolean out) {
            this.fromNode = node;
            this.nextEdge = nodes.getInt(node * nodeEntrySize);
            this.in = in;
            this.out = out;
        }

        void readNext() {
            // readLock.lock();
            edgePointer = nextEdge * edgeEntrySize;
            nodeId = getOtherNode(fromNode, edgePointer);
            if (fromNode != getOtherNode(nodeId, edgePointer))
                throw new IllegalStateException("requested node " + fromNode + " not stored in edge. "
                        + "was:" + nodeId + "," + getOtherNode(nodeId, edgePointer));

            // position to next edge
            nextEdge = edges.getInt(getLinkPosInEdgeArea(fromNode, nodeId, edgePointer));
            flags = edges.getInt(edgePointer + I_FLAGS);

            // switch direction flags if necessary
            if (fromNode > nodeId)
                flags = CarStreetType.swapDirection(flags);

            if (!in && !CarStreetType.isForward(flags) || !out && !CarStreetType.isBackward(flags)) {
                // skip this edge as it does not fit to defined filter
            } else {
                distance = getDist(edgePointer);
                foundNext = true;
            }
        }

        int edgePointer() {
            return edgePointer;
        }

        int nextEdgePointer() {
            return nextEdge;
        }

        @Override public boolean next() {
            int i = 0;
            foundNext = false;
            for (; i < 1000; i++) {
                if (nextEdge == EMPTY_LINK)
                    break;
                readNext();
                if (foundNext)
                    break;
            }
            if (i > 1000)
                throw new IllegalStateException("something went wrong: no end of edge-list found");
            return foundNext;
        }

        @Override public int node() {
            return nodeId;
        }

        @Override public double distance() {
            return distance;
        }

        @Override public int flags() {
            return flags;
        }

        @Override public int fromNode() {
            return fromNode;
        }
    }

    @Override
    public Graph clone() {
        // TODO hhmmh how can we create the graph in a different location if on-disc?
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public Graph clone(Directory dir) {
        if (this.dir == dir)
            throw new IllegalStateException("cannot create graph into the same directory!");

        GraphStorage clonedG = new GraphStorage(dir);
        // TODO GraphUtility.copy(this, clonedG);
        return clonedG;
    }

    @Override
    public void markNodeDeleted(int index) {
        // nodes.setInt(index, index);
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean isDeleted(int index) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void optimize() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void flush() {
        optimize();

        // TODO save
    }

    @Override
    public void close() {
        // TODO flush is called under the hood :/
        edges.close();
        nodes.close();
    }

    @Override
    public long capacity() {
        return edges.capacity() + nodes.capacity();
    }
}
