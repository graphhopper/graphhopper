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

import de.jetsli.graph.coll.MyBitSet;
import de.jetsli.graph.coll.MyBitSetImpl;
import de.jetsli.graph.coll.MyOpenBitSet;
import de.jetsli.graph.util.EdgeIdIterator;
import de.jetsli.graph.util.Helper;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntIntHashMap;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This graph implementation is memory efficient and fast (in that order), and thread safe. Also it
 * is easier to maintain compared to the MMapGraph. It allows storage via flush and loads from
 * storage if existing. It allows duplicate edges if flags (highway and bothDir flags) are identical
 *
 * @author Peter Karich
 */
public class MemoryGraphSafe implements SaveableGraph {

    private static final int EMPTY_LINK = 0;
    private static final float DIST_UNIT = 10000f;
    // number of integers not edges
    private static final int MIN_SEGMENT_SIZE = 1 << 13;
    private static final float FACTOR = 1.5f;
    // EDGES LAYOUT - keep in mind that we address integers here - not bytes!
    // one edge is referenced by two nodes A and B, where it is id(A) < id(B). flags are relative to A
    private static final int LEN_DIST = 1;
    private static final int LEN_NODEA_ID = 1;
    private static final int LEN_NODEB_ID = 1;
    private static final int LEN_FLAGS = 1;
    private static final int LEN_LINKA = 1;
    private static final int LEN_LINKB = 1;
    private static final int LEN_EDGE = LEN_NODEA_ID + LEN_NODEB_ID + LEN_LINKA + LEN_LINKB + LEN_FLAGS + LEN_DIST;
    protected Logger logger = LoggerFactory.getLogger(getClass());
    // nodes
    private float[] lats;
    private float[] lons;
    private int[] refToEdges;
    // edges
    private int[][] edgesSegments;
    private int edgesSegmentSize;
    private int edgeCurrentSegment;
    private int edgeNextGlobalPointer;
    // some properties
    private long creationTime = System.currentTimeMillis();
    private int size;
    private String storageLocation;
    private MyBitSet deletedNodes;

    public MemoryGraphSafe(int cap) {
        this(null, cap);
    }

    public MemoryGraphSafe(String storageDir, int cap) {
        this(storageDir, cap, 2 * cap);
    }

    public MemoryGraphSafe(String storageDir, int cap, int capEdge) {
        this.storageLocation = storageDir;
        if (!loadExisting(storageDir)) {
            initNodes(cap);
            initEdges(capEdge);
        }
    }

    int getSegmentSize() {
        return edgesSegmentSize;
    }

    int getSegments() {
        return edgesSegments.length;
    }

    @Override public int getNodes() {
        // readLock.lock();
        return size;
    }

    private int getMaxEdges() {
        return edgesSegments.length * edgesSegmentSize / LEN_EDGE;
    }

    @Override
    public void setNode(int index, double lat, double lon) {
        // writeLock.lock();
        ensureNodeIndex(index);
        lats[index] = (float) lat;
        lons[index] = (float) lon;
    }

    private void initEdges(int cap) {
        int tmp = (int) (Math.log(cap * LEN_EDGE) / Math.log(2));
        edgesSegmentSize = Math.max((int) Math.pow(2, tmp), MIN_SEGMENT_SIZE);
        edgesSegments = new int[1][edgesSegmentSize];
    }

    // Use ONLY within a writer lock area
    private void ensureEdgePointer(int pointer) {
        if (pointer + LEN_EDGE < edgesSegmentSize * getSegments())
            return;

        logger.info("Creating new edge segment " + edgesSegmentSize * 4f / (1 << 20) + " MB");
        edgeCurrentSegment++;
        int[][] tmp = new int[edgeCurrentSegment + 1][];
        for (int i = 0; i < edgesSegments.length; i++) {
            tmp[i] = edgesSegments[i];
        }
        tmp[edgeCurrentSegment] = new int[edgesSegmentSize];
        edgesSegments = tmp;
        // nextEdgePointer = 0;
    }

    // Use ONLY within a writer lock area
    protected int ensureNodeIndex(int index) {
        if (index < size)
            return -1;

        size = index + 1;
        if (size <= lats.length)
            return -1;

        int cap = Math.max(10, Math.round(size * FACTOR));
        getDeletedNodes().ensureCapacity(cap);
        lats = Arrays.copyOf(lats, cap);
        lons = Arrays.copyOf(lons, cap);
        refToEdges = Arrays.copyOf(refToEdges, cap);
        return cap;
    }

    protected void initNodes(int cap) {
        lats = new float[cap];
        lons = new float[cap];
        // we ensure that edgePointer always starts from 1 => no need to fill with -1
        refToEdges = new int[cap];
        deletedNodes = new MyOpenBitSet(cap);
    }

    @Override
    public double getLatitude(int index) {
        // readLock.lock();
        return lats[index];
    }

    @Override
    public double getLongitude(int index) {
        // readLock.lock();       
        return lons[index];
    }

    @Override
    public void edge(int a, int b, double distance, boolean bothDirections) {
        // writeLock.lock();
        ensureNodeIndex(a);
        ensureNodeIndex(b);

        if (bothDirections)
            internalEdgeAdd(a, b, distance, 3);
        else
            internalEdgeAdd(a, b, distance, 1);
    }

    private void saveToEdgeArea(int pointer, int data) {
        // uh, speed won't be improved via bit operations!
        int segNumber = pointer / edgesSegmentSize;
        int segPointer = pointer % edgesSegmentSize;
        edgesSegments[segNumber][segPointer] = data;
    }

    private int getFromEdgeArea(int pointer) {
        // uh, speed won't be improved via bit operations!
        int segNumber = pointer / edgesSegmentSize;
        int segPointer = pointer % edgesSegmentSize;
        return edgesSegments[segNumber][segPointer];
    }

    private int getOtherNode(int nodeThis, int edgePointer) {
        int nodeA = getFromEdgeArea(edgePointer);
        if (nodeA == nodeThis)
            // return b
            return getFromEdgeArea(edgePointer + LEN_NODEA_ID);
        // return a
        return nodeA;
    }

    private int getLinkPosInEdgeArea(int nodeThis, int nodeOther, int edgePointer) {
        if (nodeThis < nodeOther)
            // get link to next a
            return edgePointer + LEN_NODEA_ID + LEN_NODEB_ID;

        // b
        return edgePointer + LEN_NODEA_ID + LEN_NODEB_ID + LEN_LINKA;
    }

    protected void internalEdgeAdd(int fromNodeId, int toNodeId, double dist, int flags) {
        int newOrExistingEdgePointer = nextEdgePointer();
        connectNewEdge(fromNodeId, newOrExistingEdgePointer);
        connectNewEdge(toNodeId, newOrExistingEdgePointer);
        writeEdge(newOrExistingEdgePointer, flags, dist, fromNodeId, toNodeId, EMPTY_LINK, EMPTY_LINK);
    }

    private void connectNewEdge(int fromNodeId, int newOrExistingEdgePointer) {
        int edgePointer = refToEdges[fromNodeId];
        if (edgePointer > 0) {
            // append edge and overwrite EMPTY_LINK
            int lastEdgePointer = getLastEdgePointer(fromNodeId, edgePointer);
            saveToEdgeArea(lastEdgePointer, newOrExistingEdgePointer);
        } else {
            refToEdges[fromNodeId] = newOrExistingEdgePointer;
        }
    }

    // writes distance, flags, nodeThis, *nodeOther* and nextEdgePointer
    private void writeEdge(int edgePointer, int flags, double dist, int nodeThis, int nodeOther,
            int nextEdgePointer, int nextEdgeOtherPointer) {
        ensureEdgePointer(edgePointer);

        if (nodeThis > nodeOther) {
            int tmp = nodeThis;
            nodeThis = nodeOther;
            nodeOther = tmp;

            tmp = nextEdgePointer;
            nextEdgePointer = nextEdgeOtherPointer;
            nextEdgeOtherPointer = tmp;

            // switch direction flag
            if ((flags & 0x3) == 1)
                flags = 2;
        }

        writeA(edgePointer, nodeThis);
        writeB(edgePointer, nodeOther);
        edgePointer += LEN_NODEA_ID + LEN_NODEB_ID;

        saveToEdgeArea(edgePointer, nextEdgePointer);
        edgePointer += LEN_LINKA;

        saveToEdgeArea(edgePointer, nextEdgeOtherPointer);
        edgePointer += LEN_LINKB;

        saveToEdgeArea(edgePointer, flags);
        edgePointer += LEN_FLAGS;

        saveToEdgeArea(edgePointer, (int) (dist * DIST_UNIT));
        //edgePointer += LEN_DIST;
    }

    void writeA(int edgePointer, int node) {
        saveToEdgeArea(edgePointer, node);
    }

    void writeB(int edgePointer, int node) {
        saveToEdgeArea(edgePointer + LEN_NODEA_ID, node);
    }

    void internalEdgeRemove(int node, int otherNode) {
        EdgeIterable iter = (EdgeIterable) getEdges(node);
        int prev = -1;
        while (iter.next()) {
            if (iter.nodeId() == otherNode) {
                internalEdgeRemove(node, prev, iter.edgePointer());
                return;
            }

            prev = iter.edgePointer();
        }
        throw new IllegalStateException("edge to be removed not found node:" + node + ", otherNode:" + otherNode);
    }

    /**
     * @param edgeToUpdatePointer if it is negative then it will be saved to refToEdges
     */
    void internalEdgeRemove(int node, int edgeToUpdatePointer, int edgeToDeletePointer) {
        // an edge is shared across the two node even if the edge is not in both directions
        // so we need to know two edge-pointers pointing to the edge before edgeToDeletePointer
        int otherNode = getOtherNode(node, edgeToDeletePointer);
        int linkPos = getLinkPosInEdgeArea(node, otherNode, edgeToDeletePointer);
        int nextEdge = getFromEdgeArea(linkPos);
        if (edgeToUpdatePointer < 0)
            refToEdges[node] = nextEdge;
        else {
            int link = getLinkPosInEdgeArea(node, otherNode, edgeToUpdatePointer);
            saveToEdgeArea(link, nextEdge);
        }
    }

    private int nextEdgePointer() {
        edgeNextGlobalPointer += LEN_EDGE;
        return edgeNextGlobalPointer;
    }

    private int getLastEdgePointer(int nodeThis, int edgePointer) {
        int lastLink = -1;
        int i = 0;
        int otherNode;
        for (; i < 1000; i++) {
            otherNode = getOtherNode(nodeThis, edgePointer);
            lastLink = getLinkPosInEdgeArea(nodeThis, otherNode, edgePointer);
            edgePointer = getFromEdgeArea(lastLink);
            if (edgePointer == EMPTY_LINK)
                break;
        }

        if (i >= 1000)
            throw new IllegalStateException("endless loop? edge count is probably not higher than " + i);
        return lastLink;
    }

    @Override
    public EdgeIdIterator getEdges(int nodeId) {
        return new EdgeIterable(nodeId, true, true);
    }

    @Override
    public EdgeIdIterator getIncoming(int nodeId) {
        return new EdgeIterable(nodeId, true, false);
    }

    @Override
    public EdgeIdIterator getOutgoing(int nodeId) {
        return new EdgeIterable(nodeId, false, true);
    }

    @Override
    public void close() {
        flush();
    }

    private class EdgeIterable implements EdgeIdIterator {

        private int lastEdgePointer;
        private boolean in;
        private boolean out;
        private boolean foundNext;
        // edge properties        
        private int flags;
        private float distance;
        private int nodeId;
        private int fromNode;
        private int nextEdgePointer;

        public EdgeIterable(int node, boolean in, boolean out) {
            this.fromNode = node;
            this.nextEdgePointer = refToEdges[node];
            this.in = in;
            this.out = out;
        }

        void readNext() {
            // readLock.lock();
            int pointer = lastEdgePointer = nextEdgePointer;
            nodeId = getOtherNode(fromNode, pointer);

            // position to next edge
            nextEdgePointer = getFromEdgeArea(getLinkPosInEdgeArea(fromNode, nodeId, pointer));

            // position to flags
            pointer += LEN_EDGE - LEN_FLAGS - LEN_DIST;
            flags = getFromEdgeArea(pointer);

            // switch direction flags if necessary
            if (fromNode > nodeId && (flags & 0x3) < 3)
                flags = ~(flags & 0x3);

            if (!in && (flags & 1) == 0 || !out && (flags & 2) == 0) {
                // skip this edge as it does not fit to defined filter
            } else {
                // position to distance
                pointer += LEN_FLAGS;
                distance = getFromEdgeArea(pointer) / DIST_UNIT;
                foundNext = true;
            }
        }

        int edgePointer() {
            return lastEdgePointer;
        }

        @Override
        public boolean next() {
            int i = 0;
            foundNext = false;
            for (; i < 1000; i++) {
                if (nextEdgePointer == EMPTY_LINK)
                    break;
                readNext();
                if (foundNext)
                    break;
            }
            if (i > 1000)
                throw new IllegalStateException("something went wrong: no end of edge-list found");
            return foundNext;
        }

        @Override
        public int nodeId() {
            return nodeId;
        }

        @Override
        public double distance() {
            return distance;
        }

        @Override
        public int flags() {
            return flags;
        }
    }

    protected MemoryGraphSafe creatThis(String storage, int nodes, int edges) {
        return new MemoryGraphSafe(storage, nodes, edges);
    }

    @Override
    public Graph clone() {
        // readLock.lock();
        MemoryGraphSafe clonedGraph = creatThis(null, refToEdges.length, getMaxEdges());

        System.arraycopy(lats, 0, clonedGraph.lats, 0, lats.length);
        System.arraycopy(lons, 0, clonedGraph.lons, 0, lons.length);
        System.arraycopy(refToEdges, 0, clonedGraph.refToEdges, 0, refToEdges.length);

        clonedGraph.edgesSegments = new int[edgeCurrentSegment + 1][];
        for (int i = 0; i < clonedGraph.edgesSegments.length; i++) {
            clonedGraph.edgesSegments[i] = new int[edgesSegmentSize];
        }

        for (int i = 0; i < edgesSegments.length; i++) {
            System.arraycopy(edgesSegments[i], 0, clonedGraph.edgesSegments[i], 0, edgesSegmentSize);
        }
        clonedGraph.edgeCurrentSegment = edgeCurrentSegment;
        clonedGraph.edgesSegmentSize = edgesSegmentSize;
        clonedGraph.edgeNextGlobalPointer = edgeNextGlobalPointer;
        clonedGraph.size = size;
        return clonedGraph;
    }

    private MyBitSet getDeletedNodes() {
        if (deletedNodes == null)
            deletedNodes = new MyOpenBitSet(size);
        return deletedNodes;
    }

    @Override
    public void markNodeDeleted(int index) {
        // writeLock.lock();
        getDeletedNodes().add(index);
    }

    @Override
    public boolean isDeleted(int index) {
        // readLock.lock();
        return getDeletedNodes().contains(index);
    }

    /**
     * Saves this graph to disc
     */
    @Override
    public void flush() {
        // we can avoid storing the deletedNodes bitset but we need to defragmentate before saving!
        // writeLock.lock();        
        optimize();
        save();
    }

    @Override
    public void optimize() {
        // writeLock.lock();
        int deleted = getDeletedNodes().getCardinality();
        if (deleted == 0)
            return;

//        if (deleted < size / 4) {
        inPlaceDelete(deleted);
//        } else
//            optimizeIfLotsOfDeletes(deleted);
    }

    /**
     * This methods moves the last nodes into the deleted nodes, which is much more memory friendly
     * for only a few deletes but probably not for many deletes.
     */
    void inPlaceDelete(int deleted) {
        // Alternative to this method: use smaller segments for nodes and not one big fat java array?
        //
        // Prepare edge-update of nodes which are connected to deleted nodes        
        int toMoveNode = getNodes();
        int itemsToMove = 0;
        int maxMoves = Math.min(deleted, Math.max(0, toMoveNode - deleted));
        int newIndices[] = new int[maxMoves];
        int oldIndices[] = new int[maxMoves];

        TIntIntHashMap oldToNewIndexMap = new TIntIntHashMap(deleted, 1.5f, -1, -1);
        MyBitSetImpl toUpdated = new MyBitSetImpl(deleted * 3);
        for (int delNode = deletedNodes.next(0); delNode >= 0; delNode = deletedNodes.next(delNode + 1)) {
            EdgeIdIterator delEdgesIter = getEdges(delNode);
            while (delEdgesIter.next()) {
                int currNode = delEdgesIter.nodeId();
                if (deletedNodes.contains(currNode))
                    continue;

                toUpdated.add(currNode);
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
        for (int toUpdateNode = toUpdated.next(0); toUpdateNode >= 0; toUpdateNode = toUpdated.next(toUpdateNode + 1)) {
            // remove all edges connected to the deleted nodes
            EdgeIterable nodesConnectedToDelIter = (EdgeIterable) getEdges(toUpdateNode);
            int prev = -1;
            while (nodesConnectedToDelIter.next()) {
                int nodeId = nodesConnectedToDelIter.nodeId();
                if (deletedNodes.contains(nodeId))
                    internalEdgeRemove(toUpdateNode, prev, nodesConnectedToDelIter.edgePointer());
                else
                    prev = nodesConnectedToDelIter.edgePointer();
            }
        }
        toUpdated.clear();

        // marks connected nodes to rewrite the edges
        for (int i = 0; i < itemsToMove; i++) {
            int oldI = oldIndices[i];
            EdgeIdIterator movedEdgeIter = getEdges(oldI);
            while (movedEdgeIter.next()) {
                if (deletedNodes.contains(movedEdgeIter.nodeId()))
                    throw new IllegalStateException("shouldn't happen the edge to the node " + movedEdgeIter.nodeId() + " should be already deleted. " + oldI);

                toUpdated.add(movedEdgeIter.nodeId());
            }
        }

        // rewrite the edges of nodes connected to moved nodes
        for (int toUpdateNode = toUpdated.next(0); toUpdateNode >= 0; toUpdateNode = toUpdated.next(toUpdateNode + 1)) {
            EdgeIterable connectedToMovedIter = (EdgeIterable) getEdges(toUpdateNode);
            int prev = -1;
            while (connectedToMovedIter.next()) {
                int currNode = connectedToMovedIter.nodeId();
                int edgePointer = connectedToMovedIter.edgePointer();
                // node order could have changed, so we need to remove the edge from the nodes.
                internalEdgeRemove(toUpdateNode, prev, edgePointer);
                if (currNode != toUpdateNode)
                    internalEdgeRemove(currNode, toUpdateNode);
                prev = edgePointer;

                // now edge with new node ids
                int otherNodeNewIndex = oldToNewIndexMap.get(currNode);
                if (otherNodeNewIndex < 0)
                    otherNodeNewIndex = currNode;
                int newToUpdateIndex = oldToNewIndexMap.get(toUpdateNode);
                if (newToUpdateIndex < 0)
                    newToUpdateIndex = toUpdateNode;
                internalEdgeAdd(newToUpdateIndex, otherNodeNewIndex, connectedToMovedIter.distance(), connectedToMovedIter.flags());
            }
        }

        // move nodes into deleted nodes
        for (int i = 0; i < itemsToMove; i++) {
            int oldI = oldIndices[i];
            int newI = newIndices[i];
            refToEdges[newI] = refToEdges[oldI];
            lats[newI] = lats[oldI];
            lons[newI] = lons[oldI];
        }

        size -= deleted;
        deletedNodes = null;
    }

    /**
     * This methods creates a new in-memory graph without the specified deleted nodes.
     */
    void replacingDelete(int deleted) {
        MemoryGraphSafe inMemGraph = new MemoryGraphSafe(null, getNodes() - deleted, getMaxEdges());

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
            EdgeIdIterator iter = this.getEdges(oldNodeId);
            while (iter.next()) {
                if (deletedNodes.contains(iter.nodeId()))
                    continue;

                inMemGraph.internalEdgeAdd(newNodeId, old2NewMap[iter.nodeId()], iter.distance(), iter.flags());
            }
            newNodeId++;
        }
        lats = inMemGraph.lats;
        lons = inMemGraph.lons;
        refToEdges = inMemGraph.refToEdges;
        for (int i = 0; i < edgesSegments.length; i++) {
            edgesSegments[i] = inMemGraph.edgesSegments[i];
        }

        size = inMemGraph.size;
        deletedNodes = null;
    }

    public boolean save() {
        if (storageLocation == null)
            return false;
        // readLock.lock();
        try {
            File tmp = new File(storageLocation);
            if (!tmp.exists())
                tmp.mkdirs();

            Helper.writeFloats(storageLocation + "/lats", lats);
            Helper.writeFloats(storageLocation + "/lons", lons);
            Helper.writeInts(storageLocation + "/refs", refToEdges);
            for (int i = 0; i < edgesSegments.length; i++) {
                Helper.writeInts(storageLocation + "/edges" + i, edgesSegments[i]);
            }
            Helper.writeSettings(storageLocation + "/settings", size, creationTime, edgeNextGlobalPointer, edgeCurrentSegment, edgesSegmentSize);
            return true;
        } catch (IOException ex) {
            throw new RuntimeException("Couldn't write data to disc. location=" + storageLocation, ex);
        }
    }

    public boolean loadExisting(String storageDir) {
        if (storageDir == null || !new File(storageDir).exists())
            return false;

        //writeLock.lock();
        try {
            Object[] ob = Helper.readSettings(storageLocation + "/settings");
            if (ob.length < 3)
                throw new IllegalStateException("invalid file format");

            size = (Integer) ob[0];
            creationTime = (Long) ob[1];
            edgeNextGlobalPointer = (Integer) ob[2];
            edgeCurrentSegment = (Integer) ob[3];
            edgesSegmentSize = (Integer) ob[4];
            logger.info("found graph " + storageLocation + " with nodes:" + size
                    + ", edges:" + edgeNextGlobalPointer / LEN_EDGE
                    + ", edges segments:" + (edgeCurrentSegment + 1)
                    + ", edges segmentSize:" + edgesSegmentSize
                    + ", created-at:" + new Date(creationTime));

            lats = Helper.readFloats(storageLocation + "/lats");
            lons = Helper.readFloats(storageLocation + "/lons");
            refToEdges = Helper.readInts(storageLocation + "/refs");
            edgesSegments = new int[edgeCurrentSegment + 1][];
            for (int i = 0; i <= edgeCurrentSegment; i++) {
                edgesSegments[i] = Helper.readInts(storageLocation + "/edges" + i);
            }
            deletedNodes = new MyOpenBitSet(lats.length);
            return true;
        } catch (IOException ex) {
            throw new RuntimeException("Couldn't load data from disc. location=" + storageLocation, ex);
        }
    }

    public String getStorageLocation() {
        return storageLocation;
    }
}
