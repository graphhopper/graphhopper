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
import de.jetsli.graph.coll.MyOpenBitSet;
import de.jetsli.graph.util.EdgesWrapper;
import de.jetsli.graph.util.Helper;
import de.jetsli.graph.util.MyIteratorable;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * This graph implementation is memory efficient and fast (in that order), and thread safe. Also it
 * is easier to maintain compared to the MMapGraph. It allows direct deletes (without optimize),
 * storage via flush and loads from storage if existing. Allow duplicate edges if flags (highway and
 * bothDir flags) are identical
 *
 * @author Peter Karich
 */
public class MemoryGraphSafe implements SaveableGraph {

    // keep in mind that we address integers here - not bytes!
    private static final float FACTOR = 1.5f;
    // or should we use int and a factor? private static double MICRO = 1000000;
    private float[] lats;
    private float[] lons;
    // private int[] priorities;
    private long creationTime = System.currentTimeMillis();
    private int size;
    private EdgesWrapper edges = new EdgesWrapper();
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
        this(storageDir, cap, cap);
    }

    public MemoryGraphSafe(String storageDir, int cap, int capEdge) {
        this.storageLocation = storageDir;
        if (!loadExisting(storageDir)) {
            initNodes(cap);
            edges.initEdges(capEdge);
        }
        edges.setFactor(FACTOR);
    }

    @Override public void ensureCapacity(int nodeCount) {
        // writeLock.lock();
        ensureNodesCapacity(nodeCount);
        edges.ensureEdges(nodeCount);
    }

    @Override public int getNodes() {
        // readLock.lock();
        return size;

    }

    @Override public int addNode(double lat, double lon) {
        int tmp = size;
        size++;
        setNode(tmp, lat, lon);
        return tmp;
    }
    //
    // TODO @Override

    public void setNode(int index, double lat, double lon) {
        // writeLock.lock();
        ensureNodeIndex(index);
        lats[index] = (float) lat;
        lons[index] = (float) lon;
    }

    // Use ONLY within a writer lock area
    private void ensureNodeIndex(int index) {
        size = index + 1;
        ensureNodesCapacity(size);
    }

    private void initNodes(int cap) {
        lats = new float[cap];
        lons = new float[cap];
        edges.initNodes(cap);
        // priorities = new int[cap];
        // we ensure that edgePointer always starts from 1 => no need to fill with -1
        // Arrays.fill(refToEdges, -1);
        deletedNodes = new MyOpenBitSet(cap);
    }

    private void ensureNodesCapacity(int cap) {
        if (cap < lats.length)
            return;

        cap = Math.max(10, Math.round(cap * FACTOR));
        // TODO deletedNodes = copy(deletedNodes, cap);
        lats = Arrays.copyOf(lats, cap);
        lons = Arrays.copyOf(lons, cap);
        // priorities = Arrays.copyOf(priorities, cap);
        // int oldLen = refToEdges.length;
        edges.ensureNodes(cap);
        // Arrays.fill(refToEdges, oldLen, cap, -1);
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

        byte dirFlag = 3;
        if (!bothDirections)
            dirFlag = 1;
        edges.add(a, b, (float) distance, dirFlag);

        if (!bothDirections)
            dirFlag = 2;
        edges.add(b, a, (float) distance, dirFlag);
    }

    @Override
    public MyIteratorable<EdgeWithFlags> getEdges(int nodeId) {
        return edges.createEdgeIterable(nodeId, true, true);
    }

    @Override
    public MyIteratorable<EdgeWithFlags> getIncoming(int nodeId) {
        return edges.createEdgeIterable(nodeId, true, false);
    }

    @Override
    public MyIteratorable<EdgeWithFlags> getOutgoing(int nodeId) {
        return edges.createEdgeIterable(nodeId, false, true);
    }

    @Override
    public void close() {
        flush();
    }

    @Override
    public Graph clone() {
        // readLock.lock();
        MemoryGraphSafe g = new MemoryGraphSafe(null, lats.length, edges.size());
        System.arraycopy(lats, 0, g.lats, 0, lats.length);
        System.arraycopy(lons, 0, g.lons, 0, lons.length);
        EdgesWrapper cloneEdges = (EdgesWrapper) edges.clone();
        g.edges = cloneEdges;
        g.size = size;
        return g;

    }

    @Override
    public boolean markNodeDeleted(int index) {
        // writeLock.lock();
        deletedNodes.add(index);
        return true;
    }

    @Override
    public boolean isDeleted(int index) {
        // readLock.lock();
        return deletedNodes.contains(index);
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
        int deleted = deletedNodes.getCardinality();
        if (deleted == 0)
            return;
        MemoryGraphSafe inMemGraph = new MemoryGraphSafe(null, getNodes() - deleted, edges.size());

        /**
         * This methods creates a new in-memory graph without the specified deleted nodes. see
         * MMapGraph for a near duplicate
         */
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
            inMemGraph.addNode(lat, lon);
            for (EdgeWithFlags de : this.getEdges(oldNodeId)) {
                if (deletedNodes.contains(de.node))
                    continue;

                inMemGraph.edges.add(newNodeId, old2NewMap[de.node], (float) de.distance, de.flags);
            }
            newNodeId++;
        }
        lats = inMemGraph.lats;
        lons = inMemGraph.lons;
        edges = inMemGraph.edges;
        size = inMemGraph.size;
        deletedNodes = null;
    }

    public void save() {
        if (storageLocation == null)
            return;
        // readLock.lock();
        try {
            File tmp = new File(storageLocation);
            if (!tmp.exists()) {
                tmp.mkdirs();
            }

            Helper.writeFloats(storageLocation + "/lats", lats);
            Helper.writeFloats(storageLocation + "/lons", lons);
            edges.save(storageLocation);
            // Helper.writeInts(storageLocation + "/priorities", priorities);
            Helper.writeSettings(storageLocation + "/settings", size, creationTime);
        } catch (IOException ex) {
            throw new RuntimeException("Couldn't write data to storage. location was " + storageLocation, ex);
        }
    }

    public boolean loadExisting(String storageDir) {
        if (storageDir == null || !new File(storageDir).exists())
            return false;

        //writeLock.lock();
        try {
            lats = Helper.readFloats(storageLocation + "/lats");
            lons = Helper.readFloats(storageLocation + "/lons");
            edges.read(storageLocation);
            // priorities = Helper.readInts(storageLocation + "/priorities");
            Object[] ob = Helper.readSettings(storageLocation + "/settings");
            size = (Integer) ob[0];
            creationTime = (Long) ob[1];
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
