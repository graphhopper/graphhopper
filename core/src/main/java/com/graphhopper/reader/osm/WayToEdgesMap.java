/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.graphhopper.reader.osm;

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.LongIntMap;
import com.carrotsearch.hppc.LongIntScatterMap;
import com.carrotsearch.hppc.cursors.IntCursor;

import java.util.Iterator;

import static java.util.Collections.emptyIterator;

/**
 * This map can store multiple edges (int) for each way ID (long). All way-edge pairs with the same way must be inserted
 * consecutively. This allows us to simply store all edges in an array along with a mapping between the ways and the
 * position of the associated edges in this array.
 */
public class WayToEdgesMap {
    private static final int RESERVED = -1;
    private final LongIntMap offsetIndexByWay = new LongIntScatterMap();
    private final IntArrayList offsets = new IntArrayList();
    private final IntArrayList edges = new IntArrayList();
    private long lastWay = -1;

    /**
     * We need to reserve a way before we can put the associated edges into the map.
     * This way we can define a set of keys/ways for which we shall add edges later.
     */
    public void reserve(long way) {
        offsetIndexByWay.put(way, RESERVED);
    }

    public void putIfReserved(long way, int edge) {
        if (edge < 0)
            throw new IllegalArgumentException("edge must be >= 0, but was: " + edge);
        if (way != lastWay) {
            int idx = offsetIndexByWay.indexOf(way);
            if (idx < 0)
                // not reserved yet
                return;
            if (offsetIndexByWay.indexGet(idx) != RESERVED)
                // already taken
                throw new IllegalArgumentException("You need to add all edges for way: " + way + " consecutively");
            offsetIndexByWay.indexReplace(idx, offsets.size());
            offsets.add(this.edges.size());
            lastWay = way;
        }
        this.edges.add(edge);
    }

    public Iterator<IntCursor> getEdges(long way) {
        int idx = offsetIndexByWay.indexOf(way);
        if (idx < 0)
            return emptyIterator();
        int offsetIndex = offsetIndexByWay.indexGet(idx);
        if (offsetIndex == RESERVED)
            // we reserved this, but did not put a value later
            return emptyIterator();
        int offsetBegin = offsets.get(offsetIndex);
        int offsetEnd = offsetIndex + 1 < offsets.size() ? offsets.get(offsetIndex + 1) : edges.size();
        IntCursor cursor = new IntCursor();
        cursor.index = -1;
        return new Iterator<IntCursor>() {
            @Override
            public boolean hasNext() {
                return offsetBegin + cursor.index + 1 < offsetEnd;
            }

            @Override
            public IntCursor next() {
                cursor.index++;
                cursor.value = edges.get(offsetBegin + cursor.index);
                return cursor;
            }
        };
    }
}
