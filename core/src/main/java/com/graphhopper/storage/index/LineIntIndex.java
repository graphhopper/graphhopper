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
import com.carrotsearch.hppc.IntHashSet;
import com.graphhopper.geohash.SpatialKeyAlgo;
import com.graphhopper.storage.DAType;
import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.Directory;
import com.graphhopper.util.Constants;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.BBox;

import java.util.function.IntConsumer;

public class LineIntIndex {
    // do not start with 0 as a positive value means leaf and a negative means "entry with subentries"
    static final int START_POINTER = 1;

    final DataAccess dataAccess;
    private final BBox bounds;
    private int minResolutionInMeter = 300;
    private int size;
    private int leafs;
    private int checksum;
    private IndexStructureInfo indexStructureInfo;
    private int[] entries;
    private byte[] shifts;
    private boolean initialized = false;
    private SpatialKeyAlgo keyAlgo;

    public LineIntIndex(BBox bBox, Directory dir, String name) {
        this(bBox, dir, name, dir.getDefaultType(name, true));
    }

    public LineIntIndex(BBox bBox, Directory dir, String name, DAType daType) {
        this.bounds = bBox;
        this.dataAccess = dir.create(name, daType);
    }

    public boolean loadExisting() {
        if (initialized)
            throw new IllegalStateException("Call loadExisting only once");

        if (!dataAccess.loadExisting())
            return false;

        GHUtility.checkDAVersion("location_index", Constants.VERSION_LOCATION_IDX, dataAccess.getHeader(0));
        checksum = dataAccess.getHeader(1 * 4);
        minResolutionInMeter = dataAccess.getHeader(2 * 4);
        indexStructureInfo = IndexStructureInfo.create(bounds, minResolutionInMeter);
        keyAlgo = indexStructureInfo.getKeyAlgo();
        entries = indexStructureInfo.getEntries();
        shifts = indexStructureInfo.getShifts();
        initialized = true;
        return true;
    }

    public void store(InMemConstructionIndex inMem) {
        indexStructureInfo = IndexStructureInfo.create(bounds, minResolutionInMeter);
        keyAlgo = indexStructureInfo.getKeyAlgo();
        entries = indexStructureInfo.getEntries();
        shifts = indexStructureInfo.getShifts();
        dataAccess.create(64 * 1024);
        try {
            store(inMem.root, START_POINTER);
        } catch (Exception ex) {
            throw new IllegalStateException("Problem while storing location index. " + Helper.getMemInfo(), ex);
        }
        initialized = true;
    }

    private int store(InMemConstructionIndex.InMemEntry entry, int intPointer) {
        long pointer = (long) intPointer * 4;
        if (entry.isLeaf()) {
            InMemConstructionIndex.InMemLeafEntry leaf = ((InMemConstructionIndex.InMemLeafEntry) entry);
            IntArrayList entries = leaf.getResults();
            int len = entries.size();
            if (len == 0) {
                return intPointer;
            }
            size += len;
            intPointer++;
            leafs++;
            dataAccess.ensureCapacity((long) (intPointer + len + 1) * 4);
            if (len == 1) {
                // less disc space for single entries
                dataAccess.setInt(pointer, -entries.get(0) - 1);
            } else {
                for (int index = 0; index < len; index++, intPointer++) {
                    dataAccess.setInt((long) intPointer * 4, entries.get(index));
                }
                dataAccess.setInt(pointer, intPointer);
            }
        } else {
            InMemConstructionIndex.InMemTreeEntry treeEntry = ((InMemConstructionIndex.InMemTreeEntry) entry);
            int len = treeEntry.subEntries.length;
            intPointer += len;
            for (int subCounter = 0; subCounter < len; subCounter++, pointer += 4) {
                InMemConstructionIndex.InMemEntry subEntry = treeEntry.subEntries[subCounter];
                if (subEntry == null) {
                    continue;
                }
                dataAccess.ensureCapacity((long) (intPointer + 1) * 4);
                int prevIntPointer = intPointer;
                intPointer = store(subEntry, prevIntPointer);
                if (intPointer == prevIntPointer) {
                    dataAccess.setInt(pointer, 0);
                } else {
                    dataAccess.setInt(pointer, prevIntPointer);
                }
            }
        }
        return intPointer;
    }

    private void fillIDs(long keyPart, IntConsumer consumer) {
        int intPointer = START_POINTER;
        for (int depth = 0; depth < entries.length; depth++) {
            int offset = (int) (keyPart >>> (64 - shifts[depth]));
            int nextIntPointer = dataAccess.getInt((long) (intPointer + offset) * 4);
            if (nextIntPointer <= 0) {
                // empty cell
                return;
            }
            keyPart = keyPart << shifts[depth];
            intPointer = nextIntPointer;
        }
        int data = dataAccess.getInt((long) intPointer * 4);
        if (data < 0) {
            // single data entries (less disc space)
            int edgeId = -(data + 1);
            consumer.accept(edgeId);
        } else {
            // "data" is index of last data item
            for (int leafIndex = intPointer + 1; leafIndex < data; leafIndex++) {
                int edgeId = dataAccess.getInt((long) leafIndex * 4);
                consumer.accept(edgeId);
            }
        }
    }

    public void query(BBox queryShape, final LocationIndex.Visitor function) {
        final IntHashSet set = new IntHashSet();
        query(START_POINTER, queryShape,
                bounds.minLat, bounds.minLon, bounds.maxLat - bounds.minLat, bounds.maxLon - bounds.minLon,
                new LocationIndex.Visitor() {
                    @Override
                    public boolean isTileInfo() {
                        return function.isTileInfo();
                    }

                    @Override
                    public void onTile(BBox bbox, int width) {
                        function.onTile(bbox, width);
                    }

                    @Override
                    public void onEdge(int edgeId) {
                        if (set.add(edgeId))
                            function.onEdge(edgeId);
                    }
                }, 0);
    }

    private void query(int intPointer, BBox queryBBox,
                       double minLat, double minLon,
                       double deltaLatPerDepth, double deltaLonPerDepth,
                       LocationIndex.Visitor function, int depth) {
        long pointer = (long) intPointer * 4;
        if (depth == entries.length) {
            int nextIntPointer = dataAccess.getInt(pointer);
            if (nextIntPointer < 0) {
                // single data entries (less disc space)
                function.onEdge(-(nextIntPointer + 1));
            } else {
                long maxPointer = (long) nextIntPointer * 4;
                // loop through every leaf entry => nextIntPointer is maxPointer
                for (long leafPointer = pointer + 4; leafPointer < maxPointer; leafPointer += 4) {
                    // we could read the whole info at once via getBytes instead of getInt
                    function.onEdge(dataAccess.getInt(leafPointer));
                }
            }
            return;
        }
        int max = (1 << shifts[depth]);
        int factor = max == 4 ? 2 : 4;
        deltaLonPerDepth /= factor;
        deltaLatPerDepth /= factor;
        for (int cellIndex = 0; cellIndex < max; cellIndex++) {
            int nextIntPointer = dataAccess.getInt(pointer + cellIndex * 4);
            if (nextIntPointer <= 0)
                continue;
            int[] pixelXY = keyAlgo.decode(cellIndex);
            double tmpMinLon = minLon + deltaLonPerDepth * pixelXY[0];
            double tmpMinLat = minLat + deltaLatPerDepth * pixelXY[1];

            BBox bbox = (queryBBox != null || function.isTileInfo()) ? new BBox(tmpMinLon, tmpMinLon + deltaLonPerDepth, tmpMinLat, tmpMinLat + deltaLatPerDepth) : null;
            if (function.isTileInfo())
                function.onTile(bbox, depth);
            if (queryBBox == null || queryBBox.contains(bbox)) {
                // fill without a restriction!
                query(nextIntPointer, null, tmpMinLat, tmpMinLon, deltaLatPerDepth, deltaLonPerDepth, function, depth + 1);
            } else if (queryBBox.intersects(bbox)) {
                query(nextIntPointer, queryBBox, tmpMinLat, tmpMinLon, deltaLatPerDepth, deltaLonPerDepth, function, depth + 1);
            }
        }
    }

    /**
     * This method collects edge ids from the neighborhood of a point and puts them into foundEntries.
     * <p>
     * If it is called with iteration = 0, it just looks in the tile the query point is in.
     * If it is called with iteration = 0,1,2,.., it will look in additional tiles further and further
     * from the start tile. (In a square that grows by one pixel in all four directions per iteration).
     * <p>
     * See discussion at issue #221.
     * <p>
     */
    public void findEdgeIdsInNeighborhood(double queryLat, double queryLon, int iteration, IntConsumer foundEntries) {
        int x = keyAlgo.x(queryLon);
        int y = keyAlgo.y(queryLat);
        for (int yreg = -iteration; yreg <= iteration; yreg++) {
            int subqueryY = y + yreg;
            int subqueryXA = x - iteration;
            int subqueryXB = x + iteration;
            if (subqueryXA >= 0 && subqueryY >= 0 && subqueryXA < indexStructureInfo.getParts() && subqueryY < indexStructureInfo.getParts()) {
                long keyPart = keyAlgo.encode(subqueryXA, subqueryY) << (64 - keyAlgo.getBits());
                fillIDs(keyPart, foundEntries);
            }
            if (iteration > 0 && subqueryXB >= 0 && subqueryY >= 0 && subqueryXB < indexStructureInfo.getParts() && subqueryY < indexStructureInfo.getParts()) {
                long keyPart = keyAlgo.encode(subqueryXB, subqueryY) << (64 - keyAlgo.getBits());
                fillIDs(keyPart, foundEntries);
            }
        }

        for (int xreg = -iteration + 1; xreg <= iteration - 1; xreg++) {
            int subqueryX = x + xreg;
            int subqueryYA = y - iteration;
            int subqueryYB = y + iteration;
            if (subqueryX >= 0 && subqueryYA >= 0 && subqueryX < indexStructureInfo.getParts() && subqueryYA < indexStructureInfo.getParts()) {
                long keyPart = keyAlgo.encode(subqueryX, subqueryYA) << (64 - keyAlgo.getBits());
                fillIDs(keyPart, foundEntries);
            }
            if (subqueryX >= 0 && subqueryYB >= 0 && subqueryX < indexStructureInfo.getParts() && subqueryYB < indexStructureInfo.getParts()) {
                long keyPart = keyAlgo.encode(subqueryX, subqueryYB) << (64 - keyAlgo.getBits());
                fillIDs(keyPart, foundEntries);
            }
        }
    }

    public int getChecksum() {
        return checksum;
    }

    public int getMinResolutionInMeter() {
        return minResolutionInMeter;
    }

    public void setMinResolutionInMeter(int minResolutionInMeter) {
        this.minResolutionInMeter = minResolutionInMeter;
    }

    public void flush() {
        dataAccess.setHeader(0, Constants.VERSION_LOCATION_IDX);
        dataAccess.setHeader(1 * 4, checksum);
        dataAccess.setHeader(2 * 4, minResolutionInMeter);

        // saving space not necessary: dataAccess.trimTo((lastPointer + 1) * 4);
        dataAccess.flush();
    }

    public void close() {
        dataAccess.close();
    }

    public boolean isClosed() {
        return dataAccess.isClosed();
    }

    public long getCapacity() {
        return dataAccess.getCapacity();
    }

    public void setChecksum(int checksum) {
        this.checksum = checksum;
    }

    public int getSize() {
        return size;
    }

    public int getLeafs() {
        return leafs;
    }
}
