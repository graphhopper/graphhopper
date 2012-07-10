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
import de.jetsli.graph.util.EdgeIdIterator;
import de.jetsli.graph.util.MyIteratorable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple in-memory graph represenation
 *
 * @author Peter Karich, info@jetsli.de
 */
public class MemoryGraph implements Graph, Cloneable {

    private static final float FACTOR = 1.5f;
    private Logger logger = LoggerFactory.getLogger(getClass());
    private float[] lons;
    private float[] lats;
    private int size = 0;
    private EdgeWithFlags[] refToEdges;
    private MyBitSet deletedNodes;

    public MemoryGraph() {
        this(100);
    }

    public MemoryGraph(int capacity) {
        lons = new float[capacity];
        lats = new float[capacity];
        refToEdges = new EdgeWithFlags[capacity];
    }

    private MyBitSet getDeletedNodes() {
        if (deletedNodes == null)
            deletedNodes = new MyOpenBitSet(lons.length);
        return deletedNodes;
    }

    @Override
    public void ensureCapacity(int numberOfLocation) {
        refToEdges = growArray(refToEdges, numberOfLocation, 1);
        lons = growArray(lons, numberOfLocation, 1);
        lats = growArray(lats, numberOfLocation, 1);
    }

    @Override
    public void setNode(int index, double lat, double lon) {
        size = Math.max(index + 1, size);
        lons = growArray(lons, index + 1, FACTOR);
        lats = growArray(lats, index + 1, FACTOR);
        lons[index] = (float) lon;
        lats[index] = (float) lat;
    }

    @Override
    public void edge(int a, int b, double distance, boolean bothDirections) {
        if (distance < 0)
            throw new UnsupportedOperationException("negative distance not supported");

        size = Math.max(size, Math.max(a, b) + 1);
        if (size > refToEdges.length)
            logger.info("ensure edges to " + (float) size * 29 / (1 << 20) + " MB");
        // required only: edges = growArrayList(edges, Math.max(a, b) + 1);
        refToEdges = growArray(refToEdges, size, FACTOR);

        byte dirFlag = 3;
        if (!bothDirections)
            dirFlag = 1;

        addIfAbsent(a, b, (float) distance, dirFlag);

        if (!bothDirections)
            dirFlag = 2;

        addIfAbsent(b, a, (float) distance, dirFlag);
    }

    @Override
    public int getNodes() {
        return size;
    }

    /**
     * if distance entry with location already exists => overwrite distance. if it does not exist =>
     * append
     */
    private void addIfAbsent(int from, int to, float distance, byte dirFlag) {
        EdgeWithFlags currEntry = refToEdges[from];
        if (currEntry == null) {
            refToEdges[from] = new EdgeWithFlags(to, distance, dirFlag);
            return;
        }

        EdgeWithFlags de = null;
        while (true) {
            if (currEntry.node == to) {
                de = currEntry;
                break;
            }
            if (currEntry.prevEntry == null)
                break;
            currEntry = (EdgeWithFlags) currEntry.prevEntry;
        }

        if (de == null) {
            de = new EdgeWithFlags(to, distance, dirFlag);
            currEntry.prevEntry = de;
        } else {
            de.distance = distance;
            de.flags |= dirFlag;
        }
    }

    @Override
    public EdgeIdIterator getEdges(int index) {
        if (index >= refToEdges.length)
            throw new IllegalStateException("Cannot accept indices higher then maxNode");

        final EdgeWithFlags d = refToEdges[index];
        if (d == null)
            return EdgeIdIterator.EMPTY;

        return new EdgesIteratorable(d);
    }

    @Override
    public EdgeIdIterator getOutgoing(int index) {
        if (index >= refToEdges.length)
            throw new IllegalStateException("Cannot accept indices higher then maxNode");

        final EdgeWithFlags d = refToEdges[index];
        if (d == null)
            return EdgeIdIterator.EMPTY;

        return new EdgesIteratorable(d) {
            @Override public boolean next() {
                for (;;) {
                    if (!super.next())
                        return false;

                    if ((curr.flags & 1) != 0)
                        return true;
                }
            }
        };
    }

    @Override
    public EdgeIdIterator getIncoming(int index) {
        if (index >= refToEdges.length)
            throw new IllegalStateException("Cannot accept indices higher then maxNode");

        final EdgeWithFlags d = refToEdges[index];
        if (d == null)
            return EdgeIdIterator.EMPTY;

        return new EdgesIteratorable(d) {
            @Override public boolean next() {
                for (;;) {
                    if (!super.next())
                        return false;

                    if ((curr.flags & 2) != 0)
                        return true;
                }
            }
        };
    }

    @Override
    public boolean markNodeDeleted(int index) {
        getDeletedNodes().add(index);
        return true;
    }

    @Override public boolean isDeleted(int index) {
        return getDeletedNodes().contains(index);
    }

    @Override
    public void optimize() {
        int deleted = deletedNodes.getCardinality();
        if (deleted == 0)
            return;
        MemoryGraph inMemGraph = new MemoryGraph(getNodes() - deleted);

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
            inMemGraph.setNode(newNodeId, lat, lon);
            EdgeIdIterator iter = this.getEdges(oldNodeId);
            while (iter.next()) {
                if (deletedNodes.contains(iter.nodeId()))
                    continue;

                inMemGraph.addIfAbsent(newNodeId, old2NewMap[iter.nodeId()],
                        (float) iter.distance(), (byte) iter.flags());
            }
            newNodeId++;
        }
        lats = inMemGraph.lats;
        lons = inMemGraph.lons;
        refToEdges = inMemGraph.refToEdges;
        size = inMemGraph.size;
        deletedNodes = null;
    }

    private static class EdgesIteratorable implements EdgeIdIterator {

        EdgeWithFlags first;
        EdgeWithFlags curr;

        EdgesIteratorable(EdgeWithFlags lde) {
            first = lde;
        }

        @Override public boolean next() {
            if (curr == null) {
                curr = first;
                first = null;
            } else
                curr = (EdgeWithFlags) curr.prevEntry;
            return curr != null;
        }

        @Override public int nodeId() {
            return curr.node;
        }

        @Override public double distance() {
            return curr.distance;
        }

        @Override public int flags() {
            return curr.flags;
        }
    }

    @Override
    public double getLongitude(int index) {
        return lons[index];
    }

    @Override
    public double getLatitude(int index) {
        return lats[index];
    }
    private static final Field sizeField;

    static {
        try {
            sizeField = ArrayList.class.getDeclaredField("size");
            sizeField.setAccessible(true);
        } catch (Exception ex) {
            throw new RuntimeException("Couldn't make field 'size' accessible", ex);
        }
    }

    // 'setSize'
    public static <T> ArrayList<T> growArrayList(final ArrayList<T> list, final int maxSize) {
        if (maxSize <= list.size())
            return list;

        list.ensureCapacity(maxSize);
        try {
            sizeField.setInt(list, maxSize);
        } catch (Exception ex) {
            throw new RuntimeException("Problem while setting private field 'size' of ArrayList", ex);
        }
        return list;
    }

    public static <T> T[] growArray(final T[] arr, final int maxSize, float factor) {
        if (maxSize <= arr.length)
            return arr;

        return Arrays.copyOf(arr, (int) (maxSize * factor));
    }

    public static float[] growArray(final float[] arr, final int maxSize, float factor) {
        if (maxSize <= arr.length)
            return arr;

        return Arrays.copyOf(arr, (int) (maxSize * factor));
    }

    @Override
    public MemoryGraph clone() {
        MemoryGraph ret = new MemoryGraph(0);
        ret.lons = Arrays.copyOf(lons, lons.length);
        ret.lats = Arrays.copyOf(lats, lats.length);
        ret.size = size;
        ret.refToEdges = Arrays.copyOf(refToEdges, refToEdges.length);
        for (int i = 0; i < refToEdges.length; i++) {
            if (refToEdges[i] != null)
                ret.refToEdges[i] = refToEdges[i].cloneFull();
        }
        return ret;
    }
}
