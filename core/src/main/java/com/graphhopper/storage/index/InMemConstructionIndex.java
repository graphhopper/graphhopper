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

package com.graphhopper.storage.index;

import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.geohash.SpatialKeyAlgo;
import org.locationtech.jts.geom.Coordinate;

import static com.graphhopper.core.util.DistancePlaneProjection.DIST_PLANE;

public class InMemConstructionIndex {

    interface InMemEntry {
        boolean isLeaf();
    }

    static class InMemLeafEntry extends IntArrayList implements InMemEntry {

        public InMemLeafEntry(int count) {
            super(count);
        }

        @Override
        public final boolean isLeaf() {
            return true;
        }

        @Override
        public String toString() {
            return "LEAF " + /*key +*/ " " + super.toString();
        }

        IntArrayList getResults() {
            return this;
        }
    }

    static class InMemTreeEntry implements InMemEntry {
        InMemEntry[] subEntries;

        public InMemTreeEntry(int subEntryNo) {
            subEntries = new InMemEntry[subEntryNo];
        }

        public InMemEntry getSubEntry(int index) {
            return subEntries[index];
        }

        public void setSubEntry(int index, InMemEntry subEntry) {
            this.subEntries[index] = subEntry;
        }

        @Override
        public final boolean isLeaf() {
            return false;
        }

        @Override
        public String toString() {
            return "TREE";
        }
    }

    final PixelGridTraversal pixelGridTraversal;
    final SpatialKeyAlgo keyAlgo;
    final int[] entries;
    final byte[] shifts;
    final InMemTreeEntry root;

    public InMemConstructionIndex(IndexStructureInfo indexStructureInfo) {
        this.root = new InMemTreeEntry(indexStructureInfo.getEntries()[0]);
        this.entries = indexStructureInfo.getEntries();
        this.shifts = indexStructureInfo.getShifts();
        this.pixelGridTraversal = indexStructureInfo.getPixelGridTraversal();
        this.keyAlgo = indexStructureInfo.getKeyAlgo();
    }

    public void addToAllTilesOnLine(final int value, final double lat1, final double lon1, final double lat2, final double lon2) {
        if (!DIST_PLANE.isCrossBoundary(lon1, lon2)) {
            // Find all the tiles on the line from (y1, x1) to (y2, y2) in tile coordinates (y, x)
            pixelGridTraversal.traverse(new Coordinate(lon1, lat1), new Coordinate(lon2, lat2), p -> {
                long key = keyAlgo.encode((int) p.x, (int) p.y);
                put(key, value);
            });
        }
    }

    void put(long key, int value) {
        put(key << (64 - keyAlgo.getBits()), root, 0, value);
    }

    private void put(long keyPart, InMemEntry entry, int depth, int value) {
        if (entry.isLeaf()) {
            InMemLeafEntry leafEntry = (InMemLeafEntry) entry;
            // Avoid adding the same edge id multiple times.
            // Since each edge id is handled only once, this can only happen when
            // this method is called several times in a row with the same edge id,
            // so it is enough to check the last entry.
            // (It happens when one edge has several segments. Every segment is traversed
            // on its own, without de-duplicating the tiles that are touched.)
            if (leafEntry.isEmpty() || leafEntry.get(leafEntry.size() - 1) != value) {
                leafEntry.add(value);
            }
        } else {
            int index = (int) (keyPart >>> (64 - shifts[depth]));
            keyPart = keyPart << shifts[depth];
            InMemTreeEntry treeEntry = ((InMemTreeEntry) entry);
            InMemEntry subentry = treeEntry.getSubEntry(index);
            depth++;
            if (subentry == null) {
                if (depth == entries.length) {
                    subentry = new InMemLeafEntry(4);
                } else {
                    subentry = new InMemTreeEntry(entries[depth]);
                }
                treeEntry.setSubEntry(index, subentry);
            }
            put(keyPart, subentry, depth, value);
        }
    }
}
