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

import com.graphhopper.coll.MyBitSet;
import com.graphhopper.coll.MyBitSetImpl;
import com.graphhopper.coll.SparseIntIntArray;
import com.graphhopper.routing.util.CarStreetType;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeWriteIterator;
import com.graphhopper.util.GraphUtility;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.BBox;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The main implementation which handles nodes and edges file format. It can be used with different
 * Directory implementations like RAMDirectory for fast and read-thread safe usage which can be
 * flushed to disc or via MMapDirectory for virtual-memory and not thread safe usage.
 *
 * @author Peter Karich
 */
public class GraphStorage implements Graph, Storable {

    protected static final int EMPTY_LINK = 0;
    private static final float INC_FACTOR = 1.5f;
    // +- 180 and +-90 => let use use 400
    private static final float INT_FACTOR = Integer.MAX_VALUE / 400f;
    // distance of around +-1000 000 meter are ok
    private static final float INT_DIST_FACTOR = 1000f;
    private Directory dir;
    // edge memory layout: nodeA,nodeB,linkA,linkB,dist,flags
    private final int E_NODEA, E_NODEB, E_LINKA, E_LINKB, E_DIST, E_FLAGS;
    protected int edgeEntrySize;
    protected DataAccess edges;
    /**
     * specified how many entries (integers) are used per edge. starting from 1 => fresh int arrays
     * do not need to be initialized with -1
     */
    private int edgeCount;
    // node memory layout: edgeRef,lat,lon
    private final int N_EDGE_REF, N_LAT, N_LON;
    /**
     * specified how many entries (integers) are used per node
     */
    protected int nodeEntrySize;
    protected DataAccess nodes;
    // starting from 0 (inconsistent :/) => normal iteration and no internal correction is necessary.
    // problem: we exported this to external API => or should we change the edge count in order to 
    // have [0,n) based edge indices in outside API?
    private int nodeCount;
    private BBox bounds;
    // delete markers are not yet persistent!
    private MyBitSet deletedNodes;
    private MyBitSet deletedEdges;
    private int edgeEntryIndex = -1, nodeEntryIndex = -1;

    public GraphStorage(Directory dir) {
        this(dir, dir.findAttach("nodes"), dir.findAttach("edges"));
    }

    GraphStorage(Directory dir, DataAccess nodes, DataAccess edges) {
        this.dir = dir;
        this.nodes = nodes;
        this.edges = edges;
        this.bounds = BBox.INVERSE.clone();
        E_NODEA = nextEdgeEntryIndex();
        E_NODEB = nextEdgeEntryIndex();
        E_LINKA = nextEdgeEntryIndex();
        E_LINKB = nextEdgeEntryIndex();
        E_DIST = nextEdgeEntryIndex();
        E_FLAGS = nextEdgeEntryIndex();

        N_EDGE_REF = nextNodeEntryIndex();
        N_LAT = nextNodeEntryIndex();
        N_LON = nextNodeEntryIndex();
        initNodeAndEdgeEntrySize();
    }

    protected final int nextEdgeEntryIndex() {
        edgeEntryIndex++;
        return edgeEntryIndex;
    }

    protected final int nextNodeEntryIndex() {
        nodeEntryIndex++;
        return nodeEntryIndex;
    }

    protected final void initNodeAndEdgeEntrySize() {
        nodeEntrySize = nodeEntryIndex + 1;
        edgeEntrySize = edgeEntryIndex + 1;
    }

    public Directory getDirectory() {
        return dir;
    }

    public GraphStorage setSegmentSize(int bytes) {
        nodes.setSegmentSize(bytes);
        edges.setSegmentSize(bytes);
        return this;
    }

    public GraphStorage createNew(int nodeCount) {
        nodes.createNew((long) nodeCount * 4 * nodeEntrySize);
        edges.createNew((long) nodeCount * 4 * edgeEntrySize);
        return this;
    }

    @Override
    public int getNodes() {
        return nodeCount;
    }

    @Override
    public double getLatitude(int index) {
        return intToDouble(nodes.getInt((long) index * nodeEntrySize + N_LAT));
    }

    @Override
    public double getLongitude(int index) {
        return intToDouble(nodes.getInt((long) index * nodeEntrySize + N_LON));
    }

    protected double intToDouble(int i) {
        return (double) i / INT_FACTOR;
    }

    protected int doubleToInt(double f) {
        return (int) (f * INT_FACTOR);
    }

    protected int distToInt(double f) {
        return (int) (f * INT_DIST_FACTOR);
    }

    protected double getDist(long pointer) {
        return (double) edges.getInt(pointer + E_DIST) / INT_DIST_FACTOR;
    }

    @Override
    public BBox getBounds() {
        return bounds;
    }

    @Override
    public void setNode(int index, double lat, double lon) {
        ensureNodeIndex(index);
        nodes.setInt((long) index * nodeEntrySize + N_LAT, doubleToInt(lat));
        nodes.setInt((long) index * nodeEntrySize + N_LON, doubleToInt(lon));
        if (lat > bounds.maxLat)
            bounds.maxLat = lat;
        if (lat < bounds.minLat)
            bounds.minLat = lat;
        if (lon > bounds.maxLon)
            bounds.maxLon = lon;
        if (lon < bounds.minLon)
            bounds.minLon = lon;
    }

    public void ensureNodeIndex(int nodeIndex) {
        if (nodeIndex < nodeCount)
            return;

        nodeCount = nodeIndex + 1;
        long tmp = nodeCount * nodeEntrySize;
        if (tmp <= nodes.capacity() / 4)
            return;

        long cap = Math.max(10, (long) (tmp * INC_FACTOR));
        nodes.ensureCapacity(cap * 4);
        if (deletedNodes != null)
            getDeletedNodes().ensureCapacity((int) (nodeCount * INC_FACTOR));
    }

    private void ensureEdgeIndex(int edgeIndex) {
        // the beginning edge is unused i.e. edgeCount == maximum edge index
        edgeIndex++;
        long tmp = (long) edgeIndex * edgeEntrySize;
        if (tmp <= edges.capacity() / 4)
            return;

        long cap = Math.max(10, Math.round(tmp * INC_FACTOR));
        edges.ensureCapacity(cap * 4);
        if (deletedEdges != null)
            getDeletedEdges().ensureCapacity((int) (edgeCount * INC_FACTOR));
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
        writeEdge(newOrExistingEdge, fromNodeId, toNodeId, EMPTY_LINK, EMPTY_LINK, dist, flags);
    }

    protected int nextEdge() {
        edgeCount++;
        if (edgeCount < 0)
            throw new IllegalStateException("too many edges. new edge pointer would be negative.");
        ensureEdgeIndex(edgeCount);
        return edgeCount;
    }

    protected void connectNewEdge(int fromNodeId, int newOrExistingEdge) {
        long nodePointer = (long) fromNodeId * nodeEntrySize;
        int edge = nodes.getInt(nodePointer + N_EDGE_REF);
        if (edge > 0) {
            // append edge and overwrite EMPTY_LINK
            long lastEdge = getLastEdge(fromNodeId, edge);
            edges.setInt(lastEdge, newOrExistingEdge);
        } else {
            nodes.setInt(nodePointer + N_EDGE_REF, newOrExistingEdge);
        }
    }

    // writes distance, flags, nodeThis, *nodeOther* and nextEdgePointer
    protected void writeEdge(int edge, int nodeThis, int nodeOther, int nextEdge, int nextEdgeOther,
            double dist, int flags) {
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
        edges.setInt(edgePointer + E_NODEA, nodeThis);
        edges.setInt(edgePointer + E_NODEB, nodeOther);
        edges.setInt(edgePointer + E_LINKA, nextEdge);
        edges.setInt(edgePointer + E_LINKB, nextEdgeOther);
        edges.setInt(edgePointer + E_LINKB, nextEdgeOther);
        edges.setInt(edgePointer + E_DIST, distToInt(dist));
        edges.setInt(edgePointer + E_FLAGS, flags);
    }

    protected long getLinkPosInEdgeArea(int nodeThis, int nodeOther, long edgePointer) {
        if (nodeThis <= nodeOther)
            return edgePointer + E_LINKA;
        return edgePointer + E_LINKB;
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
        int nodeA = edges.getInt(edgePointer + E_NODEA);
        if (nodeA == nodeThis)
            // return b
            return edges.getInt(edgePointer + E_NODEB);
        // return a
        return nodeA;
    }

    @Override
    public EdgeWriteIterator getEdgeProps(int edgeId, final int endNode) {
        if (edgeId < 1 || edgeId > edgeCount)
            throw new IllegalStateException("edgeId " + edgeId + " out of bounds [0," + edgeCount + "]");
        long edgePointer = (long) edgeId * edgeEntrySize;
        // a bit complex but faster
        int nodeA = edges.getInt(edgePointer + E_NODEA);
        int nodeB = edges.getInt(edgePointer + E_NODEB);
        SingleEdge edge = createSingleEdge(edgePointer);
        if (endNode < 0 || endNode == nodeB) {
            edge.fromNode = nodeA;
            edge.node = nodeB;
            return edge;
        } else if (endNode == nodeA) {
            edge.fromNode = nodeB;
            edge.node = nodeA;
            edge.switchFlags = true;
            return edge;
        } else
            return GraphUtility.EMPTY;
    }

    protected SingleEdge createSingleEdge(long edgePointer) {
        return new SingleEdge(edgePointer);
    }

    // TODO create a new constructor and reuse EdgeIterable -> new EdgeIterable(edgeId, END node)
    protected class SingleEdge implements EdgeWriteIterator {

        protected long edgePointer;
        protected int fromNode;
        protected int node;
        protected boolean switchFlags;

        public SingleEdge(long edgePointer) {
            this.edgePointer = edgePointer;
        }

        @Override public boolean next() {
            return false;
        }

        @Override public int edge() {
            return (int) (edgePointer / edgeEntrySize);
        }

        @Override
        public int fromNode() {
            return fromNode;
        }

        @Override
        public int node() {
            return node;
        }

        @Override public double distance() {
            return getDist(edgePointer);
        }

        @Override public void distance(double dist) {
            edges.setInt(edgePointer + E_DIST, distToInt(dist));
        }

        @Override public int flags() {
            int flags = edges.getInt(edgePointer + E_FLAGS);
            if (switchFlags)
                return CarStreetType.swapDirection(flags);
            return flags;
        }

        @Override public void flags(int flags) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override public boolean isEmpty() {
            return false;
        }
    }

    @Override
    public EdgeWriteIterator getAllEdges() {
        return new AllEdgeIterator();
    }

    protected class AllEdgeIterator implements EdgeWriteIterator {

        protected long edgePointer = 0;
        private int maxEdges = (edgeCount + 1) * edgeEntrySize;

        @Override public boolean next() {
            edgePointer += edgeEntrySize;
            return edgePointer < maxEdges;
        }

        @Override public int fromNode() {
            return edges.getInt(edgePointer + E_NODEA);
        }

        @Override public int node() {
            return edges.getInt(edgePointer + E_NODEB);
        }

        @Override public double distance() {
            return getDist(edgePointer);
        }

        @Override public void distance(double dist) {
            edges.setInt(edgePointer + E_DIST, distToInt(dist));
        }

        @Override public int flags() {
            return edges.getInt(edgePointer + E_FLAGS);
        }

        @Override public void flags(int flags) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override public int edge() {
            return (int) (edgePointer / edgeEntrySize);
        }

        @Override public boolean isEmpty() {
            return false;
        }
    }

    @Override
    public EdgeWriteIterator getEdges(int node) {
        return new EdgeIterable(node, true, true);
    }

    @Override
    public EdgeWriteIterator getIncoming(int node) {
        return new EdgeIterable(node, true, false);
    }

    @Override
    public EdgeWriteIterator getOutgoing(int node) {
        return new EdgeIterable(node, false, true);
    }

    protected class EdgeIterable implements EdgeWriteIterator {

        long edgePointer;
        boolean in;
        boolean out;
        boolean foundNext;
        // edge properties        
        int flags;
        double distance;
        int nodeId;
        final int fromNode;
        int edgeId;
        int nextEdge;

        public EdgeIterable(int edge) {
            this.nextEdge = edge;
            this.fromNode = edges.getInt(nextEdge * edgeEntrySize + E_NODEA);
            this.in = true;
            this.out = true;
            next();
        }

        public EdgeIterable(int node, boolean in, boolean out) {
            this.fromNode = node;
            this.nextEdge = nodes.getInt((long) node * nodeEntrySize + N_EDGE_REF);
            this.in = in;
            this.out = out;
        }

        void readNext() {
            // readLock.lock();                       
            edgePointer = nextEdge * edgeEntrySize;
            edgeId = nextEdge;
            nodeId = getOtherNode(fromNode, edgePointer);

            // position to next edge
            nextEdge = edges.getInt(getLinkPosInEdgeArea(fromNode, nodeId, edgePointer));
            flags = edges.getInt(edgePointer + E_FLAGS);

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

        long edgePointer() {
            return edgePointer;
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
            // road networks typically do not have nodes with plenty of edges!
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

        @Override public int edge() {
            return edgeId;
        }

        @Override public void distance(double dist) {
            distance = dist;
            edges.setInt(edgePointer + E_DIST, distToInt(dist));
        }

        @Override public void flags(int fl) {
            flags = fl;
            int nep = edges.getInt(getLinkPosInEdgeArea(fromNode, nodeId, edgePointer));
            int neop = edges.getInt(getLinkPosInEdgeArea(nodeId, fromNode, edgePointer));
            writeEdge((int) (edgePointer / edgeEntrySize), fromNode, nodeId, nep, neop, distance, flags);
        }

        @Override public boolean isEmpty() {
            return false;
        }
    }

    protected GraphStorage newThis(Directory dir, DataAccess nodes, DataAccess edges) {
        // no storage.create here!
        return new GraphStorage(dir, nodes, edges);
    }

    @Override
    public Graph copyTo(Graph g) {
        if (g.getClass().equals(getClass())) {
            return _copyTo((GraphStorage) g);
        } else
            return GraphUtility.copyTo(this, g);
    }

    public Graph copyTo(Directory dir) {
        if (this.dir == dir)
            throw new IllegalStateException("cannot copy graph into the same directory!");

        return _copyTo(newThis(dir, dir.create("nodes"), dir.create("edges")));
    }

    Graph _copyTo(GraphStorage clonedG) {
        if (clonedG.edgeEntrySize != edgeEntrySize)
            throw new IllegalStateException("edgeEntrySize cannot be different for cloned graph");
        if (clonedG.nodeEntrySize != nodeEntrySize)
            throw new IllegalStateException("nodeEntrySize cannot be different for cloned graph");

        edges.copyTo(clonedG.edges);
        clonedG.edgeCount = edgeCount;
        nodes.copyTo(clonedG.nodes);
        clonedG.nodeCount = nodeCount;
        clonedG.bounds = bounds;
        if (deletedNodes == null)
            clonedG.deletedNodes = null;
        else
            clonedG.deletedNodes = deletedNodes.copyTo(new MyBitSetImpl());
        if (deletedEdges == null)
            clonedG.deletedEdges = null;
        else
            clonedG.deletedEdges = deletedEdges.copyTo(new MyBitSetImpl());
        return clonedG;
    }

    private MyBitSet getDeletedNodes() {
        if (deletedNodes == null)
            deletedNodes = new MyBitSetImpl((int) (nodes.capacity() / 4));
        return deletedNodes;
    }

    private MyBitSet getDeletedEdges() {
        if (deletedEdges == null)
            deletedEdges = new MyBitSetImpl((int) (edges.capacity() / 4));
        return deletedEdges;
    }

    @Override
    public void markNodeDeleted(int index) {
        getDeletedNodes().add(index);
    }

    @Override
    public boolean isNodeDeleted(int index) {
        return getDeletedNodes().contains(index);
    }

    @Override
    public void optimize() {
        // 1. disconnect all marked edges from nodes
        disconnectEdges(getDeletedEdges().getCardinality());
        // 2. delete all marked nodes and move remaining nodes into gaps to do compaction.
        // also disconnect edges, then copy into new area while clearing out old area (to reduce memory usage)
        inPlaceDelete();
        trimToSize();
    }

    private void trimToSize() {
        long nodeCap = (long) nodeCount * nodeEntrySize;
        nodes.trimTo(nodeCap * 4);
        long edgeCap = (long) (edgeCount + 1) * edgeEntrySize;
        edges.trimTo(edgeCap * 4);
    }

    /**
     * This method removes all edges marked in deletedEdges from the edge list of every node
     */
    void disconnectEdges(int deletedEdgeCount) {
        if (deletedEdgeCount <= 0)
            return;

        for (int toUpdateNode = 0; toUpdateNode < nodeCount; toUpdateNode++) {
            EdgeIterable nodeIter = (EdgeIterable) getEdges(toUpdateNode);
            long prev = -1;
            while (nodeIter.next()) {
                int edgeId = nodeIter.edge();
                if (deletedEdges.contains(edgeId))
                    internalEdgeDisconnect(edgeId, prev, toUpdateNode);
                else
                    prev = nodeIter.edgePointer();
            }
        }
    }

    /**
     * This method disconnects the specified edge from the list of edges of the specified node. It
     * does not release the freed space to be reused.
     *
     * @param edgeToUpdatePointer if it is negative then it will be saved to refToEdges
     */
    void internalEdgeDisconnect(int edge, long edgeToUpdatePointer, int node) {
        long edgeToDeletePointer = (long) edge * edgeEntrySize;
        // an edge is shared across the two nodes even if the edge is not in both directions
        // so we need to know two edge-pointers pointing to the edge before edgeToDeletePointer
        int otherNode = getOtherNode(node, edgeToDeletePointer);
        long linkPos = getLinkPosInEdgeArea(node, otherNode, edgeToDeletePointer);
        int nextEdge = edges.getInt(linkPos);
        if (edgeToUpdatePointer < 0) {
            nodes.setInt((long) node * nodeEntrySize, nextEdge);
        } else {
            long link = getLinkPosInEdgeArea(node, otherNode, edgeToUpdatePointer);
            edges.setInt(link, nextEdge);
        }
    }

    /**
     * Removes nodes and edges via efficiently copying into a new graph. Efficiently means it will
     * release resources for edges and nodes while copying in order to reduce memory usage. So that
     * one is able to copy even GB-sized graphs.
     */
    void inPlaceDelete() {
        int deletedNodeCount = getDeletedNodes().getCardinality();
        if (deletedNodeCount <= 0)
            return;

        // fill sparse array efficiently via append which requires increasing key!
        final SparseIntIntArray oldToNewMap = new SparseIntIntArray(deletedNodeCount);
        for (int newNode = 0, oldNode = 0; oldNode < nodeCount; oldNode++) {
            if (deletedNodes.contains(oldNode))
                continue;

            oldToNewMap.append(oldNode, newNode);
            newNode++;
        }

        DataAccess tmpNodes = dir.create("nodes");
        DataAccess tmpEdges = dir.create("edges");
        int newNodeCount = nodeCount - deletedNodeCount;
        MyBitSet avoidDuplicateEdges = new MyBitSetImpl(newNodeCount);
        GraphStorage tmpGraph = newThis(dir, tmpNodes, tmpEdges);
        // TODO only aquire two node segments in order to remove memory usage
        tmpGraph.createNew(newNodeCount);
        int nodesPerSegment = nodes.getSegmentSize() / (nodeEntrySize * 4);
        if (nodes.getSegmentSize() % (nodeEntrySize * 4) != 0)
            nodesPerSegment++;

        // TODO nearly identical to GraphUtility.createSortedGraph 
        for (int oldNode = 0; oldNode < nodeCount; oldNode++) {
            int newNode = oldToNewMap.get(oldNode);
            if (newNode < 0)
                continue;
            avoidDuplicateEdges.add(newNode);
            long oldNodePointer = (long) oldNode * nodeEntrySize;
            long newNodePointer = (long) newNode * nodeEntrySize;
            // copy all node properties except the edge ref!
            for (int j = 0; j < nodeEntrySize; j++) {
                int value = nodes.getInt(oldNodePointer + j);
                if (j != N_EDGE_REF)
                    tmpNodes.setInt(newNodePointer + j, value);
            }

            EdgeIterator oldIter = getEdges(oldNode);
            while (oldIter.next()) {
                int connectedOldNode = oldIter.node();
                deletedEdges.add(oldIter.edge());
                int connectedNewNode = oldToNewMap.get(connectedOldNode);
                if (connectedNewNode < 0 || avoidDuplicateEdges.contains(connectedNewNode))
                    continue;

                int fromNodeId = newNode;
                int toNodeId = connectedNewNode;
                int newOrExistingEdge = tmpGraph.nextEdge();
                tmpGraph.connectNewEdge(fromNodeId, newOrExistingEdge);
                tmpGraph.connectNewEdge(toNodeId, newOrExistingEdge);
                tmpGraph.writeEdge(newOrExistingEdge, fromNodeId, toNodeId, EMPTY_LINK, EMPTY_LINK, oldIter.distance(), oldIter.flags());

                // copy values of extended graphs is currently NOT supported/needed
                // edge ids are changing too, so we need an edgeOldToNewMap too!
//                long oldEdgePointer = (long) iter.edge() * edgeEntrySize;
//                long newEdgePointer = (long) newOrExistingEdge * edgeEntrySize;
//                for (int j = maxStandardEdgePosition; j < edgeEntrySize; j++) {
//                    int value = edges.getInt(oldEdgePointer + j);
//                    if (j != N_EDGE_REF)
//                        tmpEdges.setInt(newEdgePointer + j, value);
//                }
            }

            if (oldNode > 0 && oldNode % nodesPerSegment == 0) {
                int segmentNumber = oldNode / nodesPerSegment - 1;
                nodes.releaseSegment(segmentNumber);
                
                // TODO release segments for edges too
                // deletedEdges.next();
                // edges.releaseSegment(edgeSegmentNumber);
            }
        }

        dir.delete(nodes);
        nodes = tmpNodes;
        nodeCount = newNodeCount;
        deletedNodes = null;

        dir.delete(edges);
        edges = tmpEdges;
        edgeCount = tmpGraph.edgeCount;
        deletedEdges = null;
    }

    @Override
    public boolean loadExisting() {
        if (edges.loadExisting()) {
            if (!nodes.loadExisting())
                throw new IllegalStateException("corrupt file or directory? " + dir);
            if (nodes.getVersion() != edges.getVersion())
                throw new IllegalStateException("nodes and edges files have different versions!? " + dir);
            // nodes
            int hash = nodes.getHeader(0);
            if (hash != getClass().getName().hashCode())
                throw new IllegalStateException("Cannot load the graph - it wasn't create via "
                        + getClass().getName() + "! " + dir);

            nodeEntrySize = nodes.getHeader(1);
            nodeCount = nodes.getHeader(2);
            bounds.minLon = intToDouble(nodes.getHeader(3));
            bounds.maxLon = intToDouble(nodes.getHeader(4));
            bounds.minLat = intToDouble(nodes.getHeader(5));
            bounds.maxLat = intToDouble(nodes.getHeader(6));

            // edges
            edgeEntrySize = edges.getHeader(0);
            edgeCount = edges.getHeader(1);
            return true;
        }
        return false;
    }

    @Override
    public void flush() {
        // nodes
        nodes.setHeader(0, getClass().getName().hashCode());
        nodes.setHeader(1, nodeEntrySize);
        nodes.setHeader(2, nodeCount);
        nodes.setHeader(3, doubleToInt(bounds.minLon));
        nodes.setHeader(4, doubleToInt(bounds.maxLon));
        nodes.setHeader(5, doubleToInt(bounds.minLat));
        nodes.setHeader(6, doubleToInt(bounds.maxLat));

        // edges
        edges.setHeader(0, edgeEntrySize);
        edges.setHeader(1, edgeCount);

        edges.flush();
        nodes.flush();
    }

    @Override
    public void close() {
        edges.close();
        nodes.close();
    }

    @Override
    public long capacity() {
        return edges.capacity() + nodes.capacity();
    }

    public int getVersion() {
        return nodes.getVersion();
    }

    @Override public String toString() {
        return "edges:" + edgeCount + "(" + edges.capacity() / Helper.MB + "), "
                + "nodes:" + nodeCount + "(" + nodes.capacity() / Helper.MB + ")"
                + ", bounds:" + bounds;
    }
}
