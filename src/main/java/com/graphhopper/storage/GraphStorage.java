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
import com.graphhopper.routing.util.CarStreetType;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeWriteIterator;
import com.graphhopper.util.GraphUtility;
import com.graphhopper.util.shapes.BBox;
import gnu.trove.map.hash.TIntIntHashMap;

/**
 * The main implementation which handles nodes and edges file format. It can be used with different
 * Directory implementations like RAMDirectory for fast and read-thread safe usage which can be
 * flushed to disc or via MMapDirectory for virtual-memory and not thread safe usage.
 *
 * @author Peter Karich
 */
public class GraphStorage implements WritableGraph, Storable {

    protected static final int EMPTY_LINK = 0;
    private static final float INC_FACTOR = 1.5f;
    // +- 180 and +-90 => let use use 400
    private static final float INT_FACTOR = Integer.MAX_VALUE / 400f;
    // distance of around +-1000 000 meter are ok
    private static final float INT_DIST_FACTOR = 1000f;
    private Directory dir;
    // edge memory layout: nodeA,nodeB,linkA,linkB,dist,flags
    protected final int I_NODEA, I_NODEB, I_LINKA, I_LINKB, I_FLAGS, I_DIST;
    protected int edgeEntrySize;
    protected DataAccess edges;
    protected int edgeCount;
    // node memory layout: edgeRef,lat,lon
    protected final int I_EDGE_REF, I_LAT, I_LON;
    protected int nodeEntrySize;
    protected DataAccess nodes;
    protected int nodeCount;
    private BBox bounds;
    // delete marker is not persistent!
    private MyBitSet deletedNodes;
    private int edgeEntryIndex = -1, nodeEntryIndex = -1;

    public GraphStorage(Directory dir) {
        this.dir = dir;
        edges = dir.createDataAccess("edges");
        nodes = dir.createDataAccess("nodes");
        this.bounds = BBox.INVERSE.clone();

        I_NODEA = nextEdgeEntryIndex();
        I_NODEB = nextEdgeEntryIndex();
        I_LINKA = nextEdgeEntryIndex();
        I_LINKB = nextEdgeEntryIndex();
        I_FLAGS = nextEdgeEntryIndex();
        I_DIST = nextEdgeEntryIndex();

        I_EDGE_REF = nextNodeEntryIndex();
        I_LAT = nextNodeEntryIndex();
        I_LON = nextNodeEntryIndex();
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
        return intToDouble(nodes.getInt((long) index * nodeEntrySize + I_LAT));
    }

    @Override
    public double getLongitude(int index) {
        return intToDouble(nodes.getInt((long) index * nodeEntrySize + I_LON));
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
        return (double) edges.getInt(pointer + I_DIST) / INT_DIST_FACTOR;
    }

    @Override
    public BBox getBounds() {
        return bounds;
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
        ensureEdgeIndex(edgeCount);
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
        edges.setInt(edgePointer + I_DIST, distToInt(dist));
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

    @Override
    public EdgeWriteIterator getEdgeProps(int edgeId, final int endNode) {
        if (edgeId < 1 || edgeId > edgeCount)
            throw new IllegalStateException("edgeId " + edgeId + " out of bounds [0," + edgeCount + "]");
        long edgePointer = (long) edgeId * edgeEntrySize;
        // a bit complex but faster
        int nodeA = edges.getInt(edgePointer + I_NODEA);
        int nodeB = edges.getInt(edgePointer + I_NODEB);
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
            edges.setInt(edgePointer + I_DIST, distToInt(dist));
        }

        @Override public int flags() {
            int flags = edges.getInt(edgePointer + I_FLAGS);
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
            return edges.getInt(edgePointer + I_NODEA);
        }

        @Override public int node() {
            return edges.getInt(edgePointer + I_NODEB);
        }

        @Override public double distance() {
            return getDist(edgePointer);
        }

        @Override public void distance(double dist) {
            edges.setInt(edgePointer + I_DIST, distToInt(dist));
        }

        @Override public int flags() {
            return edges.getInt(edgePointer + I_FLAGS);
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
            this.fromNode = edges.getInt(nextEdge * edgeEntrySize + I_NODEA);
            this.in = true;
            this.out = true;
            next();
        }

        public EdgeIterable(int node, boolean in, boolean out) {
            this.fromNode = node;
            this.nextEdge = nodes.getInt((long) node * nodeEntrySize);
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
            edges.setInt(edgePointer + I_DIST, distToInt(dist));
        }

        @Override public void flags(int fl) {
            flags = fl;
            int nep = edges.getInt(getLinkPosInEdgeArea(fromNode, nodeId, edgePointer));
            int neop = edges.getInt(getLinkPosInEdgeArea(nodeId, fromNode, edgePointer));
            writeEdge((int) (edgePointer / edgeEntrySize), fromNode, nodeId, nep, neop, flags, distance);
        }

        @Override public boolean isEmpty() {
            return false;
        }
    }

    protected GraphStorage newThis(Directory dir) {
        // no create here        
        return new GraphStorage(dir);
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

        return _copyTo(newThis(dir));
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
        return clonedG;
    }

    /**
     * @param edgeToUpdatePointer if it is negative then it will be saved to refToEdges
     */
    void internalEdgeRemove(long edgeToDeletePointer, long edgeToUpdatePointer, int node) {
        // an edge is shared across the two node even if the edge is not in both directions
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

    private MyBitSet getDeletedNodes() {
        if (deletedNodes == null)
            deletedNodes = new MyBitSetImpl((int) (nodes.capacity() / 4));
        return deletedNodes;
    }

    @Override
    public void markNodeDeleted(int index) {
        getDeletedNodes().add(index);
    }

    @Override
    public boolean isDeleted(int index) {
        return getDeletedNodes().contains(index);
    }

    /**
     * This methods creates a new in-memory graph without the specified deleted nodes.
     */
    void replacingDeleteTodo(int deleted) {
        GraphStorage inMemGraph = new GraphStorage(new RAMDirectory()).createNew(nodeCount);

        // see MMapGraph for a near duplicate         
        int locs = this.getNodes();
        int newNodeId = 0;
        int[] old2NewMap = new int[locs];
        for (int oldNodeId = 0; oldNodeId < locs; oldNodeId++) {
            if (deletedNodes.contains(oldNodeId))
                continue;

            old2NewMap[oldNodeId] = newNodeId;
            newNodeId++;
        }

        newNodeId = 0;
        // create new graph with new mapped ids
        for (int oldNodeId = 0; oldNodeId < locs; oldNodeId++) {
            if (deletedNodes.contains(oldNodeId))
                continue;
            double lat = this.getLatitude(oldNodeId);
            double lon = this.getLongitude(oldNodeId);
            inMemGraph.setNode(newNodeId, lat, lon);
            EdgeIterator iter = this.getEdges(oldNodeId);
            while (iter.next()) {
                if (deletedNodes.contains(iter.node()))
                    continue;

                // TODO duplicate edges will be created!
                inMemGraph.internalEdgeAdd(newNodeId, old2NewMap[iter.node()], iter.distance(), iter.flags());
            }
            newNodeId++;
        }
        // keep in mind that this graph storage could be in-memory OR mmap
        inMemGraph.edges.copyTo(edges);
        edgeCount = inMemGraph.edgeCount;
        edgeEntrySize = inMemGraph.edgeEntrySize;

        inMemGraph.nodes.copyTo(nodes);
        nodeCount = inMemGraph.nodeCount;
        nodeEntrySize = inMemGraph.nodeEntrySize;
        bounds = inMemGraph.bounds;
        deletedNodes = null;
    }

    /**
     * This methods moves the last nodes into the deleted nodes, which is much more memory friendly
     * for only a few deletes but probably not for many deletes.
     */
    void inPlaceDelete(int deleted) {
        // Alternative to this method: use canBeOverwritten segments for nodes and not one big fat java array?
        //
        // Prepare edge-update of nodes which are connected to deleted nodes        
        int toMoveNode = getNodes();
        int itemsToMove = 0;
        int maxMoves = Math.min(deleted, Math.max(0, toMoveNode - deleted));
        int newIndices[] = new int[maxMoves];
        int oldIndices[] = new int[maxMoves];

        final TIntIntHashMap oldToNewIndexMap = new TIntIntHashMap(deleted, 1.5f, -1, -1);
        MyBitSetImpl toUpdatedSet = new MyBitSetImpl(deleted * 3);
        for (int delNode = deletedNodes.next(0); delNode >= 0; delNode = deletedNodes.next(delNode + 1)) {
            EdgeIterator delEdgesIter = getEdges(delNode);
            while (delEdgesIter.next()) {
                int currNode = delEdgesIter.node();
                if (deletedNodes.contains(currNode))
                    continue;

                toUpdatedSet.add(currNode);
            }

            toMoveNode--;
            for (; toMoveNode >= 0; toMoveNode--) {
                if (!deletedNodes.contains(toMoveNode))
                    break;
            }

            if (toMoveNode < delNode)
                break;

            // create sorted old- to new-index map
            newIndices[itemsToMove] = delNode;
            oldIndices[itemsToMove] = toMoveNode;
            oldToNewIndexMap.put(toMoveNode, delNode);
            itemsToMove++;
        }

        // all deleted nodes could be connected to existing. remove the connections
        for (int toUpdateNode = toUpdatedSet.next(0); toUpdateNode >= 0; toUpdateNode = toUpdatedSet.next(toUpdateNode + 1)) {
            // remove all edges connected to the deleted nodes
            EdgeIterable nodesConnectedToDelIter = (EdgeIterable) getEdges(toUpdateNode);
            long prev = -1;
            while (nodesConnectedToDelIter.next()) {
                int nodeId = nodesConnectedToDelIter.node();
                if (deletedNodes.contains(nodeId))
                    internalEdgeRemove(nodesConnectedToDelIter.edgePointer(), prev, toUpdateNode);
                else
                    prev = nodesConnectedToDelIter.edgePointer();
            }
        }
        toUpdatedSet.clear();

        // marks connected nodes to rewrite the edges
        for (int i = 0; i < itemsToMove; i++) {
            int oldI = oldIndices[i];
            EdgeIterator movedEdgeIter = getEdges(oldI);
            while (movedEdgeIter.next()) {
                if (deletedNodes.contains(movedEdgeIter.node()))
                    throw new IllegalStateException("shouldn't happen the edge to the node " + movedEdgeIter.node() + " should be already deleted. " + oldI);

                toUpdatedSet.add(movedEdgeIter.node());
            }
        }

        // move nodes into deleted nodes
        for (int i = 0; i < itemsToMove; i++) {
            int oldI = oldIndices[i];
            int newI = newIndices[i];
            for (int j = 0; j < nodeEntrySize; j++) {
                nodes.setInt((long) newI * nodeEntrySize + j, nodes.getInt((long) oldI * nodeEntrySize + j));
            }
        }

        // rewrite the edges of nodes connected to moved nodes
        // go through all edges and pick the necessary ... <- this is easier to implement then
        // a more efficient (?) breadth-first search
        for (int edge = 1; edge < edgeCount + 1; edge++) {
            long edgePointer = (long) edge * edgeEntrySize;
            // nodeId could be wrong - see tests            
            int nodeA = edges.getInt(edgePointer + I_NODEA);
            int nodeB = edges.getInt(edgePointer + I_NODEB);
            if (!toUpdatedSet.contains(nodeA) && !toUpdatedSet.contains(nodeB))
                continue;

            // now overwrite exiting edge with new node ids 
            // also flags and links could have changed due to different node order
            int updatedA = oldToNewIndexMap.get(nodeA);
            if (updatedA < 0)
                updatedA = nodeA;

            int updatedB = oldToNewIndexMap.get(nodeB);
            if (updatedB < 0)
                updatedB = nodeB;

            int linkA = edges.getInt(getLinkPosInEdgeArea(nodeA, nodeB, edgePointer));
            int linkB = edges.getInt(getLinkPosInEdgeArea(nodeB, nodeA, edgePointer));
            int flags = edges.getInt(edgePointer + I_FLAGS);
            double distance = getDist(edgePointer);
            writeEdge(edge, updatedA, updatedB, linkA, linkB, flags, distance);
        }

        // edgeCount stays!
        nodeCount -= deleted;
        deletedNodes = null;
    }

    @Override
    public void optimize() {
        int deleted = getDeletedNodes().getCardinality();
        if (deleted == 0)
            return;

        inPlaceDelete(deleted);
        // replacingDelete(deleted);
        trimToSize();
    }

    void trimToSize() {
        long nodeCap = (long) nodeCount * 4 * nodeEntrySize;
        nodes.trimTo(nodeCap);
        // TODO delete empty edges too
    }

    @Override
    public boolean loadExisting() {
        if (edges.loadExisting()) {
            if (!nodes.loadExisting())
                throw new IllegalStateException("corrupt file?");
            if (nodes.getVersion() != edges.getVersion())
                throw new IllegalStateException("nodes and edge file have different versions!?");
            // nodes
            int hash = nodes.getHeader(0);
            if (hash != getClass().getName().hashCode())
                throw new IllegalStateException("The graph file wasn't create via "
                        + getClass().getName() + "! Location:" + dir);

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
        optimize();

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
        flush();
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
}
