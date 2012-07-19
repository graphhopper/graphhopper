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
    private Logger logger = LoggerFactory.getLogger(getClass());
    private static final float FACTOR = 1.5f;
    // keep in mind that we address integers here - not bytes!
    private static final int LEN_DIST = 1;
    private static final int LEN_NODEID = 1;
    private static final int LEN_FLAGS = 1;
    private static final int LEN_LINK = 1;
    private static final int LEN_EDGE = LEN_FLAGS + LEN_DIST + LEN_NODEID + LEN_LINK;
    // or should we use int and a factor? private static double MICRO = 1000000;
    private float[] lats;
    private float[] lons;
    // private int[] priorities;
    private long creationTime = System.currentTimeMillis();
    private int size;
    private int[] refToEdges;
    // TODO use a bitset to memorize fragmented edges-area!
    private int[] edgesArea;
    private int nextEdgePointer;
//    private final ReadWriteLock lock;
//    private final Lock writeLock;
//    private final Lock readLock;
    private String storageLocation;
    private MyBitSet deletedNodes;

//    public MemoryGraphSafe() {
//        this(10);
//    }
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

    @Override public int getNodes() {
        // readLock.lock();
        return size;
    }

    public int getMaxEdges() {
        return edgesArea.length / LEN_EDGE;
    }

    @Override
    public void setNode(int index, double lat, double lon) {
        // writeLock.lock();
        ensureNodeIndex(index);
        lats[index] = (float) lat;
        lons[index] = (float) lon;
    }

    private void initEdges(int cap) {
        cap *= LEN_EDGE;
        edgesArea = new int[cap];
    }

    // Use ONLY within a writer lock area
    private void ensureEdgesCapacity(int cap) {
        ensureEdgePointer(cap * LEN_EDGE);
    }

    // Use ONLY within a writer lock area
    private void ensureEdgePointer(int pointer) {
        if (pointer + LEN_EDGE < edgesArea.length)
            return;

        // TODO instead of reallocation (memory waste, time to copy), create multiple segments for 
        // edges and use the highest bits (any number 2^x) as the segment number.
        // Choose 2^x as segment number so we can use bit operations and avoid modulo for edgeId lookup!
        pointer = Math.max(10 * LEN_EDGE, Math.round(pointer * FACTOR));
        logger.info("ensure space to " + (int) (pointer / LEN_EDGE) + " edges (" + (float) 4 * pointer / (1 << 20) + " MB)");
        int newLen = pointer;
        edgesArea = Arrays.copyOf(edgesArea, newLen);
    }

    // Use ONLY within a writer lock area
    private void ensureNodeIndex(int index) {
        if (index < size)
            return;

        size = index + 1;
        if (size <= lats.length)
            return;

        int cap = Math.max(10, Math.round(size * FACTOR));
        getDeletedNodes().ensureCapacity(cap);
        lats = Arrays.copyOf(lats, cap);
        lons = Arrays.copyOf(lons, cap);
        // priorities = Arrays.copyOf(priorities, cap);
        // int oldLen = refToEdges.length;
        refToEdges = Arrays.copyOf(refToEdges, cap);
        // Arrays.fill(refToEdges, oldLen, cap, -1);
    }

    private void initNodes(int cap) {
        lats = new float[cap];
        lons = new float[cap];
        refToEdges = new int[cap];
        // priorities = new int[cap];
        // we ensure that edgePointer always starts from 1 => no need to fill with -1
        // Arrays.fill(refToEdges, -1);
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

        int dirFlag = 3;
        if (!bothDirections)
            dirFlag = 1;
        internalAdd(a, b, distance, dirFlag);

        if (!bothDirections)
            dirFlag = 2;
        internalAdd(b, a, distance, dirFlag);
    }

    private void internalAdd(int fromNodeId, int toNodeId, double dist, int flags) {
        int edgePointer = refToEdges[fromNodeId];
        int newPos = nextEdgePointer();
        if (edgePointer > 0) {
            TIntArrayList list = readAllEdges(edgePointer);
            // TODO sort by priority but include the latest entry too!
            // Collections.sort(list, listPrioSorter);
            // int len = list.size();
            // for (int i = 0; i < len; i++) {
            //    int pointer = list.get(i);
            //    copyEdge();
            // }
            if (list.isEmpty())
                throw new IllegalStateException("list cannot be empty for positive edgePointer " + edgePointer + " node:" + fromNodeId);

            int linkPointer = getLink(list.get(list.size() - 1));
            edgesArea[linkPointer] = newPos;
        } else
            refToEdges[fromNodeId] = newPos;

        writeEdge(newPos, flags, dist, toNodeId, EMPTY_LINK);
    }

    private int getLink(int edgePointer) {
        return edgePointer + LEN_EDGE - LEN_LINK;
    }

    private void writeEdge(int edgePointer, int flags, double dist, int toNodeId, int nextEdgePointer) {
        ensureEdgePointer(edgePointer);

        edgesArea[edgePointer] = flags;
        edgePointer += LEN_FLAGS;

        edgesArea[edgePointer] = (int) (dist * DIST_UNIT);
        edgePointer += LEN_DIST;

        edgesArea[edgePointer] = toNodeId;
        edgePointer += LEN_NODEID;

        edgesArea[edgePointer] = nextEdgePointer;
        // edgePointer += LEN_LINK;
    }

    private int nextEdgePointer() {
        nextEdgePointer += LEN_EDGE;
        return nextEdgePointer;
    }

    private TIntArrayList readAllEdges(int edgePointer) {
        TIntArrayList list = new TIntArrayList(5);
        int i = 0;
        for (; i < 1000; i++) {
            list.add(edgePointer);
            edgePointer = edgesArea[getLink(edgePointer)];
            if (edgePointer == EMPTY_LINK)
                break;
        }
        if (i >= 1000)
            throw new IllegalStateException("endless loop? edge count is probably not higher than " + i);
        return list;
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

        private int pointer;
        private boolean in;
        private boolean out;
        private boolean foundNext;
        // edge properties        
        private int flags;
        private float dist;
        private int nodeId;

        public EdgeIterable(int node, boolean in, boolean out) {
            this.pointer = refToEdges[node];
            this.in = in;
            this.out = out;
        }

        void readNext() {
            // readLock.lock();
            flags = edgesArea[pointer];
            if (!in && (flags & 1) == 0 || !out && (flags & 2) == 0) {
                pointer = edgesArea[getLink(pointer)];
                return;
            }
            pointer += LEN_FLAGS;

            dist = edgesArea[pointer] / DIST_UNIT;
            pointer += LEN_DIST;

            nodeId = edgesArea[pointer];
            pointer += LEN_NODEID;
            // next edge
            pointer = edgesArea[pointer];
            foundNext = true;
        }

        @Override
        public boolean next() {
            int i = 0;
            foundNext = false;
            for (; i < 1000; i++) {
                if (pointer == EMPTY_LINK)
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
            return dist;
        }

        @Override
        public int flags() {
            return flags;
        }
    }

    @Override
    public Graph clone() {
        // readLock.lock();
        MemoryGraphSafe g = new MemoryGraphSafe(null, refToEdges.length, getMaxEdges());
        System.arraycopy(lats, 0, g.lats, 0, lats.length);
        System.arraycopy(lons, 0, g.lons, 0, lons.length);
        System.arraycopy(refToEdges, 0, g.refToEdges, 0, refToEdges.length);
        System.arraycopy(edgesArea, 0, g.edgesArea, 0, edgesArea.length);
        g.nextEdgePointer = nextEdgePointer;
        g.size = size;
        return g;
    }

    private MyBitSet getDeletedNodes() {
        if (deletedNodes == null)
            deletedNodes = new MyOpenBitSet(size);
        return deletedNodes;
    }

    @Override
    public boolean markNodeDeleted(int index) {
        // writeLock.lock();
        getDeletedNodes().add(index);
        return true;
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

//        if (deleted < size / 5) {
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
        MyBitSetImpl toUpdate = new MyBitSetImpl(deleted * 3);
        for (int delNode = deletedNodes.next(0); delNode >= 0; delNode = deletedNodes.next(delNode + 1)) {
            EdgeIdIterator delEdgesIter = getEdges(delNode);
            while (delEdgesIter.next()) {
                int currNode = delEdgesIter.nodeId();
                if (deletedNodes.contains(currNode))
                    continue;

                toUpdate.add(currNode);
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
        for (int toUpdateNode = toUpdate.next(0); toUpdateNode >= 0; toUpdateNode = toUpdate.next(toUpdateNode + 1)) {
            // remove all edges to the deleted nodes
            EdgeIdIterator nodesConnectedToDelIter = getEdges(toUpdateNode);
            // hack to remove edges ref afterwards
            boolean firstNext = nodesConnectedToDelIter.next();
            if (firstNext) {
                // this forces new edges to be created => TODO only update the edges. do not create new entries
                refToEdges[toUpdateNode] = EMPTY_LINK;
                do {
                    if (!deletedNodes.contains(nodesConnectedToDelIter.nodeId()))
                        internalAdd(toUpdateNode, nodesConnectedToDelIter.nodeId(),
                                nodesConnectedToDelIter.distance(), nodesConnectedToDelIter.flags());
                } while (nodesConnectedToDelIter.next());
            }
        }
        toUpdate.clear();

        // marks connected nodes to rewrite the edges
        for (int i = 0; i < itemsToMove; i++) {
            int oldI = oldIndices[i];
            EdgeIdIterator movedEdgeIter = getEdges(oldI);
            while (movedEdgeIter.next()) {
                if (deletedNodes.contains(movedEdgeIter.nodeId()))
                    throw new IllegalStateException("shouldn't happen the edge to the node " + movedEdgeIter.nodeId() + " should be already deleted. " + oldI);

                toUpdate.add(movedEdgeIter.nodeId());
            }
        }

        // rewrite the edges of nodes connected to moved nodes
        for (int toUpdateNode = toUpdate.next(0); toUpdateNode >= 0; toUpdateNode = toUpdate.next(toUpdateNode + 1)) {
            EdgeIdIterator connectedToMovedIter = getEdges(toUpdateNode);
            boolean firstNext = connectedToMovedIter.next();
            if (firstNext) {
                refToEdges[toUpdateNode] = EMPTY_LINK;
                do {
                    int currNode = connectedToMovedIter.nodeId();
                    int other = oldToNewIndexMap.get(currNode);
                    if (other < 0)
                        other = currNode;
                    internalAdd(toUpdateNode, other, connectedToMovedIter.distance(), connectedToMovedIter.flags());
                } while (connectedToMovedIter.next());
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

                inMemGraph.internalAdd(newNodeId, old2NewMap[iter.nodeId()], iter.distance(), iter.flags());
            }
            newNodeId++;
        }
        lats = inMemGraph.lats;
        lons = inMemGraph.lons;
        refToEdges = inMemGraph.refToEdges;
        edgesArea = inMemGraph.edgesArea;
        size = inMemGraph.size;
        deletedNodes = null;
    }

    public void save() {
        if (storageLocation == null)
            return;
        // readLock.lock();
        try {
            File tmp = new File(storageLocation);
            if (!tmp.exists())
                tmp.mkdirs();

            Helper.writeFloats(storageLocation + "/lats", lats);
            Helper.writeFloats(storageLocation + "/lons", lons);
            Helper.writeInts(storageLocation + "/refs", refToEdges);
            Helper.writeInts(storageLocation + "/edges", edgesArea);
            // Helper.writeInts(storageLocation + "/priorities", priorities);
            Helper.writeSettings(storageLocation + "/settings", size, creationTime, nextEdgePointer);
        } catch (IOException ex) {
            throw new RuntimeException("Couldn't write data to storage. location was " + storageLocation, ex);
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
            nextEdgePointer = (Integer) ob[2];
            logger.info("found graph " + storageLocation + " with size:" + size
                    + ", edges:" + nextEdgePointer / LEN_EDGE + ", created-at:" + new Date(creationTime));

            lats = Helper.readFloats(storageLocation + "/lats");
            lons = Helper.readFloats(storageLocation + "/lons");
            refToEdges = Helper.readInts(storageLocation + "/refs");
            edgesArea = Helper.readInts(storageLocation + "/edges");
            // priorities = Helper.readInts(storageLocation + "/priorities");
            deletedNodes = new MyOpenBitSet(lats.length);
            return true;
        } catch (IOException ex) {
            throw new RuntimeException("Couldn't load data to storage. location=" + storageLocation, ex);
        }
    }

    public String getStorageLocation() {
        return storageLocation;
    }
}
