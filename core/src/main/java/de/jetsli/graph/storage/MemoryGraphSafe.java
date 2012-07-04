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

import de.jetsli.graph.util.Helper;
import de.jetsli.graph.util.MyIteratorable;
import gnu.trove.list.array.TIntArrayList;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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
    private static final int LEN_DIST = 1;
    private static final int LEN_NODEID = 1;
    private static final int LEN_FLAGS = 1;
    private static final int LEN_PRIO = 1;
    private static final int LEN_LINK = 1;
    private static final int LEN_EDGE = LEN_PRIO + LEN_FLAGS + LEN_DIST + LEN_NODEID + LEN_LINK;
    // or should we use int and a factor? private static double MICRO = 1000000;
    private float[] lats;
    private float[] lons;
    private long creationTime = System.currentTimeMillis();
    private int size;
    private int[] refToEdges;
    // TODO use a bitset to memorize fragmented edges-area!
    private int[] edgesArea;
    private int nextEdgePointer;
    private Lock writeLock;
    private Lock readLock;
    private String storageLocation;

    public MemoryGraphSafe() {
        this(10);
    }

    public MemoryGraphSafe(int cap) {
        this(null, cap, cap / 4);
    }

    public MemoryGraphSafe(String storageDir, int cap, int capEdge) {
        this.storageLocation = storageDir;
        ReadWriteLock lock = new ReentrantReadWriteLock();
        writeLock = lock.writeLock();
        readLock = lock.readLock();
        if (!loadExisting(storageDir)) {
            ensureNodesCapacity(cap);
            ensureEdgesCapacity(capEdge);
        }
    }

    @Override public void ensureCapacity(int cap) {
        writeLock.lock();
        try {
            ensureNodesCapacity(cap);
            ensureEdgesCapacity(cap / 4);
        } finally {
            writeLock.unlock();
        }
    }

    @Override public int getLocations() {
        readLock.lock();
        try {
            return size;
        } finally {
            readLock.unlock();
        }
    }

    @Deprecated
    public int addLocation(double lat, double lon) {
        int tmp = size;
        size++;
        setLocation(tmp, lat, lon);
        return tmp;
    }
    //
    // TODO @Override

    public void setLocation(int index, double lat, double lon) {
        writeLock.lock();
        try {
            ensureNodeIndex(index);
            lats[index] = (float) lat;
            lons[index] = (float) lon;
        } finally {
            writeLock.unlock();
        }
    }

     // Use ONLY within a writer lock area
    private void ensureEdgePointer(int pointer) {
        if (edgesArea != null && pointer < edgesArea.length)
            return;
        
        pointer = Math.max(10 * LEN_EDGE, Math.round(pointer * 1.5f));
        if (edgesArea == null) {
            edgesArea = new int[pointer];
            Arrays.fill(edgesArea, -1);
        } else {
            int oldLen = edgesArea.length;
            int newLen = pointer;
            edgesArea = Arrays.copyOf(edgesArea, newLen);
            Arrays.fill(edgesArea, oldLen, newLen, -1);
        }
    }
    // Use ONLY within a writer lock area
    private void ensureEdgesCapacity(int cap) {        
        ensureEdgePointer(cap * LEN_EDGE);
    }

    // Use ONLY within a writer lock area
    private void ensureNodeIndex(int index) {
        size = index + 1;
        ensureNodesCapacity(size);
    }
    

    private void ensureNodesCapacity(int cap) {
        if (refToEdges != null && cap < refToEdges.length)
            return;

        cap = Math.max(10, Math.round(cap * 1.5f));
        if (lats == null) {
            lats = new float[cap];
            lons = new float[cap];
            refToEdges = new int[cap];
            // TODO we can easily avoid filling with -1 -> ensure that edgePointer always starts from 1
            Arrays.fill(refToEdges, -1);
        } else {
            lats = Arrays.copyOf(lats, cap);
            lons = Arrays.copyOf(lons, cap);
            int oldLen = refToEdges.length;
            refToEdges = Arrays.copyOf(refToEdges, cap);
            Arrays.fill(refToEdges, oldLen, cap, -1);
        }
    }

    @Override
    public double getLatitude(int index) {
        return lats[index];
    }

    @Override
    public double getLongitude(int index) {
        return lons[index];
    }

    @Override
    public void edge(int a, int b, double distance, boolean bothDirections) {
        writeLock.lock();
        try {
            ensureNodeIndex(a);
            ensureNodeIndex(b);

            byte dirFlag = 3;
            if (!bothDirections)
                dirFlag = 1;
            internalAdd(a, b, (float) distance, dirFlag);

            if (!bothDirections)
                dirFlag = 2;
            internalAdd(b, a, (float) distance, dirFlag);
        } finally {
            writeLock.unlock();
        }
    }

    private void internalAdd(int fromNodeId, int toNodeId, float dist, byte flags) {
        int edgePointer = refToEdges[fromNodeId];
        int newPos = nextEdgePointer();
        if (edgePointer >= 0) {
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

        writeEdge(newPos, 0, flags, dist, toNodeId, -1);
    }

    private int getLink(int edgePointer) {
        return edgePointer + LEN_EDGE - LEN_LINK;
    }

    private void writeEdge(int edgePointer, int nodePrio, int flags, float dist, int toNodeId, int nextEdgePointer) {
        ensureEdgePointer(edgePointer);
        // write node priority
        edgesArea[edgePointer] = nodePrio;
        edgePointer += LEN_PRIO;

        edgesArea[edgePointer] = flags;
        edgePointer += LEN_FLAGS;

        edgesArea[edgePointer] = Float.floatToIntBits(dist);
        edgePointer += LEN_DIST;

        edgesArea[edgePointer] = toNodeId;
        edgePointer += LEN_NODEID;

        edgesArea[edgePointer] = nextEdgePointer;
        // edgePointer += LEN_LINK;
    }

    public int nextEdgePointer() {
        nextEdgePointer += LEN_EDGE;
        return nextEdgePointer;
    }

    public TIntArrayList readAllEdges(int edgePointer) {
        TIntArrayList list = new TIntArrayList(5);
        int i = 0;
        for (; i < 1000; i++) {
            list.add(edgePointer);
            edgePointer = edgesArea[getLink(edgePointer)];
            if (edgePointer < 0)
                break;
        }
        if (i >= 1000)
            throw new IllegalStateException("endless loop? edge count is probably not higher than " + i);
        return list;
    }

    @Override
    public MyIteratorable<EdgeWithFlags> getEdges(int nodeId) {
        return new EdgeIterable(nodeId, true, true);
    }

    @Override
    public MyIteratorable<EdgeWithFlags> getIncoming(int nodeId) {
        return new EdgeIterable(nodeId, true, false);
    }

    @Override
    public MyIteratorable<EdgeWithFlags> getOutgoing(int nodeId) {
        return new EdgeIterable(nodeId, false, true);
    }

    @Override
    public void close() {
        flush();
    }

    private class EdgeIterable extends MyIteratorable<EdgeWithFlags> {

        private int pointer;
        private boolean in;
        private boolean out;
        private EdgeWithFlags next;

        public EdgeIterable(int node, boolean in, boolean out) {
            this.pointer = refToEdges[node];
            this.in = in;
            this.out = out;
            next();
        }

        @Override public boolean hasNext() {
            return next != null;
        }

        EdgeWithFlags readNext() {
            readLock.lock();
            try {
                if (pointer < 0)
                    return null;

                int origPointer = pointer;
                // skip node priority for now
                // int priority = edgesArea[pointer];
                pointer += LEN_PRIO;

                byte flags = (byte) edgesArea[pointer];
                if (!in && (flags & 1) == 0 || !out && (flags & 2) == 0) {
                    pointer = edgesArea[getLink(origPointer)];
                    return null;
                }

                pointer += LEN_FLAGS;

                float dist = Float.intBitsToFloat(edgesArea[pointer]);
                pointer += LEN_DIST;
                int nodeId = edgesArea[pointer];
                pointer += LEN_NODEID;
                // next edge
                pointer = edgesArea[pointer];
                return new EdgeWithFlags(nodeId, (double) dist, flags);
            } finally {
                readLock.unlock();
            }
        }

        @Override public EdgeWithFlags next() {
            EdgeWithFlags tmp = next;
            int i = 0;
            next = null;
            for (; i < 1000; i++) {
                next = readNext();
                if (next != null || pointer < 0)
                    break;
            }
            if (i > 1000)
                throw new IllegalStateException("something went wrong: no end of edge-list found");

            return tmp;
        }

        @Override public void remove() {
            // markDeleted(firstPointer);
            throw new IllegalStateException("not implemented yet");
        }
    }

    @Override
    public Graph clone() {
        MemoryGraphSafe g = new MemoryGraphSafe(null, refToEdges.length, edgesArea.length / LEN_EDGE);
        System.arraycopy(lats, 0, g.lats, 0, lats.length);
        System.arraycopy(lons, 0, g.lons, 0, lons.length);
        System.arraycopy(refToEdges, 0, g.refToEdges, 0, refToEdges.length);
        System.arraycopy(edgesArea, 0, g.edgesArea, 0, edgesArea.length);
        g.size = size;
        return g;
    }

    @Override
    public boolean markDeleted(int index) {
        writeLock.lock();
        try {
            refToEdges[index] = -1;
        } finally {
            writeLock.unlock();
        }
        return true;
    }

    @Override
    public boolean isDeleted(int index) {
        return refToEdges[index] == -1;
    }

    @Override
    public void optimize() {
        // TODO defragmentate
    }

    /**
     * Saves this graph to disc
     */
    @Override
    public void flush() {
        // we should avoid storing the defragmentation bitset => defragmentate before saving!
        optimize();
        save();
    }

    public void save() {
        if (storageLocation == null)
            return;
        readLock.lock();
        try {
            File tmp = new File(storageLocation);
            if (!tmp.exists())
                tmp.mkdirs();

            Helper.writeFloats(storageLocation + "/lats", lats);
            Helper.writeFloats(storageLocation + "/lons", lons);
            Helper.writeInts(storageLocation + "/edges", edgesArea);
            Helper.writeInts(storageLocation + "/refs", refToEdges);
            Helper.writeSettings(storageLocation + "/settings", size, creationTime);
        } catch (IOException ex) {
            throw new RuntimeException("Couldn't write data to storage. location was " + storageLocation, ex);
        } finally {
            readLock.unlock();
        }
    }

    public boolean loadExisting(String storageDir) {
        if (storageDir == null || !new File(storageDir).exists())
            return false;

        writeLock.lock();
        try {
            lats = Helper.readFloats(storageLocation + "/lats");
            lons = Helper.readFloats(storageLocation + "/lons");
            edgesArea = Helper.readInts(storageLocation + "/edges");
            refToEdges = Helper.readInts(storageLocation + "/refs");
            Object[] ob = Helper.readSettings(storageLocation + "/settings");
            size = (Integer) ob[0];
            creationTime = (Long) ob[1];
            return true;
        } catch (IOException ex) {
            throw new RuntimeException("Couldn't load data to storage. location=" + storageLocation, ex);
            // return false;
        } finally {
            writeLock.unlock();
        }
    }

    public String getStorageLocation() {
        return storageLocation;
    }
}
