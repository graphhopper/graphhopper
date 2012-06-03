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
    private LinkedDistEntryWithFlags[] edges;

    public MemoryGraph() {
        this(1000);
    }

    public MemoryGraph(int capacity) {
        lons = new float[capacity];
        lats = new float[capacity];
        edges = new LinkedDistEntryWithFlags[capacity];
    }

    @Override
    public void ensureCapacity(int numberOfLocation) {
        edges = growArray(edges, numberOfLocation);
        lons = growArray(lons, numberOfLocation);
        lats = growArray(lats, numberOfLocation);
    }

    @Override
    public int addLocation(float lat, float lon) {
        int tmp = lonLatSize++;
        lons = growArray(lons, lonLatSize);
        lats = growArray(lats, lonLatSize);
        lons[tmp] = lon;
        lats[tmp] = lat;
        return tmp;
    }

    @Override
    public void edge(int a, int b, float distance, boolean bothDirections) {
        if (distance < 0)
            throw new UnsupportedOperationException("negative distance not supported");

        maxRecognizedNodeIndex = Math.max(maxRecognizedNodeIndex, Math.max(a, b));
        // required only: edges = growArrayList(edges, Math.max(a, b) + 1);
        edges = growArray(edges, maxRecognizedNodeIndex + 1);

        byte dirFlag = 3;
        if (!bothDirections)
            dirFlag = 1;

        LinkedDistEntryWithFlags currentEdges = edges[a];
        if (currentEdges == null)
            edges[a] = new LinkedDistEntryWithFlags(distance, b, dirFlag);
        else
            addIfAbsent(currentEdges, b, distance, dirFlag);

        if (!bothDirections)
            dirFlag = 2;

        currentEdges = edges[b];
        if (currentEdges == null)
            edges[b] = new LinkedDistEntryWithFlags(distance, a, dirFlag);
        else
            addIfAbsent(currentEdges, a, distance, dirFlag);
    }

    /**
     * @return either the number of added locations. Or if the addLocation-method(s) was unused to
     * save memory, this method will return the maximum index plus 1 beeing specified via the
     * edge-method(s).
     */
    @Override
    public int getLocations() {
        return Math.max(lonLatSize, maxRecognizedNodeIndex + 1);
    }

    /**
     * if distance entry with location already exists => overwrite distance. if it does not exist =>
     * append
     */
    private void addIfAbsent(LinkedDistEntryWithFlags currEntry, int index, float distance, byte dirFlag) {
        LinkedDistEntryWithFlags de = null;
        while (true) {
            if (currEntry.node == index) {
                de = currEntry;
                break;
            }
            if (currEntry.prevEntry == null)
                break;
            currEntry = (LinkedDistEntryWithFlags) currEntry.prevEntry;
        }

        if (de == null) {
            de = new LinkedDistEntryWithFlags(distance, index, dirFlag);
            currEntry.prevEntry = de;
        } else {
            de.distance = distance;
            de.directionFlag |= dirFlag;
        }
    }

    @Override
    public MyIteratorable<DistEntry> getEdges(int index) {
        if (index >= edges.length)
            return DistEntry.EMPTY_ITER;

        final LinkedDistEntryWithFlags d = edges[index];
        if (d == null)
            return DistEntry.EMPTY_ITER;

        return new EdgesIteratorable(d);
    }

    @Override
    public MyIteratorable<DistEntry> getOutgoing(int index) {
        if (index >= edges.length)
            return DistEntry.EMPTY_ITER;

        final LinkedDistEntryWithFlags d = edges[index];
        if (d == null)
            return DistEntry.EMPTY_ITER;

        return new EdgesIteratorable(d) {

            @Override public boolean hasNext() {
                for (;;) {
                    if (curr == null || (curr.directionFlag & 1) != 0)
                        break;
                    curr = (LinkedDistEntryWithFlags) curr.prevEntry;
                }

                return curr != null;
            }
        };
    }

    @Override
    public MyIteratorable<DistEntry> getIncoming(int index) {
        if (index >= edges.length)
            return DistEntry.EMPTY_ITER;

        final LinkedDistEntryWithFlags d = edges[index];
        if (d == null)
            return DistEntry.EMPTY_ITER;

        return new EdgesIteratorable(d) {

            @Override public boolean hasNext() {
                for (;;) {
                    if (curr == null || (curr.directionFlag & 2) != 0)
                        break;
                    curr = (LinkedDistEntryWithFlags) curr.prevEntry;
                }

                return curr != null;
            }
        };
    }
    
    private static class EdgesIteratorable extends MyIteratorable<DistEntry> {

        LinkedDistEntryWithFlags curr;

        EdgesIteratorable(LinkedDistEntryWithFlags lde) {
            curr = lde;
        }

        @Override public boolean hasNext() {
            return curr != null;
        }

        @Override public DistEntry next() {
            if (!hasNext())
                throw new IllegalStateException("No next element");

            DistEntry tmp = curr;
            curr = (LinkedDistEntryWithFlags) curr.prevEntry;
            return tmp;
        }

        @Override public void remove() {
            throw new UnsupportedOperationException("Not supported. We would bi-linked nextEntries or O(n) processing");
        }
    }

    @Override
    public float getLongitude(int index) {
        return lons[index];
    }

    @Override
    public float getLatitude(int index) {
        return lats[index];
    }

    private static final Field sizeField;

    static {
        try {
            sizeField = ArrayList.class.getDeclaredField("size");
            sizeField.setAccessible(true);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
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
            throw new RuntimeException("Problem while setting private size field of ArrayList", ex);
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
