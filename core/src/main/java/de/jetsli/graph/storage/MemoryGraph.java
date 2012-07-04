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
import de.jetsli.graph.util.MyIteratorable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * A simple in-memory graph represenation
 *
 * @author Peter Karich, info@jetsli.de
 */
public class MemoryGraph implements Graph {

    private float[] lons;
    private float[] lats;
    private int lonLatSize = 0;
    private int maxRecognizedNodeIndex = -1;
    private EdgeWithFlags[] edges;
    private MyBitSet deletedNodes;

    public MemoryGraph() {
        this(100);
    }

    public MemoryGraph(int capacity) {
        lons = new float[capacity];
        lats = new float[capacity];
        edges = new EdgeWithFlags[capacity];
    }

    private MyBitSet getDeletedNodes() {
        if (deletedNodes == null)
            deletedNodes = new MyOpenBitSet(lons.length);
        return deletedNodes;
    }

    @Override
    public void ensureCapacity(int numberOfLocation) {
        edges = growArray(edges, numberOfLocation);
        lons = growArray(lons, numberOfLocation);
        lats = growArray(lats, numberOfLocation);
    }

    @Override
    public int addNode(double lat, double lon) {
        int tmp = lonLatSize++;
        lons = growArray(lons, lonLatSize);
        lats = growArray(lats, lonLatSize);
        lons[tmp] = (float) lon;
        lats[tmp] = (float) lat;
        return tmp;
    }

    @Override
    public void edge(int a, int b, double distance, boolean bothDirections) {
        if (distance < 0)
            throw new UnsupportedOperationException("negative distance not supported");

        maxRecognizedNodeIndex = Math.max(maxRecognizedNodeIndex, Math.max(a, b));
        // required only: edges = growArrayList(edges, Math.max(a, b) + 1);
        edges = growArray(edges, maxRecognizedNodeIndex + 1);

        byte dirFlag = 3;
        if (!bothDirections)
            dirFlag = 1;

        addIfAbsent(a, b, (float) distance, dirFlag);

        if (!bothDirections)
            dirFlag = 2;

        addIfAbsent(b, a, (float) distance, dirFlag);
    }

    /**
     * @return either the number of added locations. Or if the addLocation-method(s) was unused to
     * save memory, this method will return the maximum index plus 1 beeing specified via the
     * edge-method(s).
     */
    @Override
    public int getNodes() {
        return Math.max(lonLatSize, maxRecognizedNodeIndex + 1);
    }

    /**
     * if distance entry with location already exists => overwrite distance. if it does not exist =>
     * append
     */
    private void addIfAbsent(int from, int to, float distance, byte dirFlag) {
        EdgeWithFlags currEntry = edges[from];
        if (currEntry == null) {
            edges[from] = new EdgeWithFlags(to, distance, dirFlag);
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
    public MyIteratorable<EdgeWithFlags> getEdges(int index) {
        if (index >= edges.length)
            throw new IllegalStateException("Cannot accept indices higher then maxNode");

        final EdgeWithFlags d = edges[index];
        if (d == null)
            return EdgeWithFlags.EMPTY_ITER;

        return new EdgesIteratorable(d);
    }

    @Override
    public MyIteratorable<EdgeWithFlags> getOutgoing(int index) {
        if (index >= edges.length)
            throw new IllegalStateException("Cannot accept indices higher then maxNode");

        final EdgeWithFlags d = edges[index];
        if (d == null)
            return EdgeWithFlags.EMPTY_ITER;

        return new EdgesIteratorable(d) {

            @Override public boolean hasNext() {
                for (;;) {
                    if (curr == null || (curr.flags & 1) != 0)
                        break;
                    curr = (EdgeWithFlags) curr.prevEntry;
                }

                return curr != null;
            }
        };
    }

    @Override
    public MyIteratorable<EdgeWithFlags> getIncoming(int index) {
        if (index >= edges.length)
            throw new IllegalStateException("Cannot accept indices higher then maxNode");

        final EdgeWithFlags d = edges[index];
        if (d == null)
            return EdgeWithFlags.EMPTY_ITER;

        return new EdgesIteratorable(d) {

            @Override public boolean hasNext() {
                for (;;) {
                    if (curr == null || (curr.flags & 2) != 0)
                        break;
                    curr = (EdgeWithFlags) curr.prevEntry;
                }

                return curr != null;
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
        MemoryGraph inMemGraph = new MemoryGraph(getNodes() - getDeletedNodes().getCardinality());

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

                inMemGraph.addIfAbsent(newNodeId, old2NewMap[de.node], (float) de.distance, de.flags);
            }
            newNodeId++;
        }
        lats = inMemGraph.lats;
        lons = inMemGraph.lons;
        edges = inMemGraph.edges;
        lonLatSize = inMemGraph.lonLatSize;
        maxRecognizedNodeIndex = inMemGraph.maxRecognizedNodeIndex;
        deletedNodes = null;
    }

    private static class EdgesIteratorable extends MyIteratorable<EdgeWithFlags> {

        EdgeWithFlags curr;

        EdgesIteratorable(EdgeWithFlags lde) {
            curr = lde;
        }

        @Override public boolean hasNext() {
            return curr != null;
        }

        @Override public EdgeWithFlags next() {
            if (!hasNext())
                throw new IllegalStateException("No next element");

            EdgeWithFlags tmp = curr;
            curr = (EdgeWithFlags) curr.prevEntry;
            return tmp;
        }

        @Override public void remove() {
            throw new UnsupportedOperationException("Not supported. We would bi-linked nextEntries or O(n) processing");
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

    public static <T> T[] growArray(final T[] arr, final int maxSize) {
        if (maxSize <= arr.length)
            return arr;

        return Arrays.copyOf(arr, maxSize);
    }

    public static float[] growArray(final float[] arr, final int maxSize) {
        if (maxSize <= arr.length)
            return arr;

        return Arrays.copyOf(arr, maxSize);
    }

    @Override
    public MemoryGraph clone() {
        MemoryGraph ret = new MemoryGraph(0);
        ret.lons = Arrays.copyOf(lons, lons.length);
        ret.lats = Arrays.copyOf(lats, lats.length);
        ret.lonLatSize = lonLatSize;
        ret.maxRecognizedNodeIndex = maxRecognizedNodeIndex;
        ret.edges = Arrays.copyOf(edges, edges.length);
        for (int i = 0; i < edges.length; i++) {
            if (edges[i] != null)
                ret.edges[i] = edges[i].cloneFull();
        }
        return ret;
    }
}
