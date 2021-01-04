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
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.*;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;
import org.locationtech.jts.geom.Coordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.IntConsumer;

import static com.graphhopper.util.DistanceCalcEarth.C;
import static com.graphhopper.util.DistanceCalcEarth.DIST_EARTH;
import static com.graphhopper.util.DistancePlaneProjection.DIST_PLANE;

/**
 * This class implements a Quadtree to get the closest node or edge from GPS coordinates.
 * The following properties are different to an ordinary implementation:
 * <ol>
 * <li>To reduce overall size it can use 16 instead of just 4 cell if required</li>
 * <li>Still all leafs are at the same depth, otherwise it is too complicated to calculate the Bresenham line for different
 * resolutions, especially if a leaf node could be split into a tree-node and resolution changes.</li>
 * <li>To further reduce size this Quadtree avoids storing the bounding box of every cell and calculates this per request instead.</li>
 * <li>To simplify this querying and avoid a slow down for the most frequent queries ala "lat,lon" it encodes the point
 * into a spatial key {@see SpatialKeyAlgo} and can the use the resulting raw bits as cell index to recurse
 * into the subtrees. E.g. if there are 3 layers with 16, 4 and 4 cells each, then the spatial key has
 * three parts: 4 bits for the cellIndex into the 16 cells, 2 bits for the next layer and 2 bits for the last layer.</li>
 * <li>An array structure (DataAccess) is internally used and stores the offset to the next cell.
 * E.g. in case of 4 cells, the offset is 0,1,2 or 3. Except when the leaf-depth is reached, then the value
 * is the number of node IDs stored in the cell or, if negative, just a single node ID.</li>
 * </ol>
 *
 * @author Peter Karich
 */
public class LocationIndexTree implements LocationIndex {
    // do not start with 0 as a positive value means leaf and a negative means "entry with subentries"
    static final int START_POINTER = 1;
    protected final Graph graph;
    final DataAccess dataAccess;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final int MAGIC_INT = Integer.MAX_VALUE / 22318;
    private final NodeAccess nodeAccess;
    SpatialKeyAlgo keyAlgo;
    private int maxRegionSearch = 4;
    private int[] entries;
    private byte[] shifts;
    // convert spatial key to index for subentry of current depth
    private long[] bitmasks;
    private int minResolutionInMeter = 300;
    private double deltaLat;
    private double deltaLon;
    private boolean initialized = false;

    /**
     * If normed distance is smaller than this value the node or edge is 'identical' and the
     * algorithm can stop search.
     */
    private final double equalNormedDelta = DIST_PLANE.calcNormalizedDist(0.1); // 0.1 meters
    private PixelGridTraversal pixelGridTraversal;

    /**
     * @param g the graph for which this index should do the lookup based on latitude,longitude.
     */
    public LocationIndexTree(Graph g, Directory dir) {
        this.graph = g;
        this.nodeAccess = g.getNodeAccess();
        this.dataAccess = dir.find("location_index", DAType.getPreferredInt(dir.getDefaultType()));
    }

    public int getMinResolutionInMeter() {
        return minResolutionInMeter;
    }

    /**
     * Minimum width in meter of one tile. Decrease this if you need faster queries, but keep in
     * mind that then queries with different coordinates are more likely to fail.
     */
    public LocationIndexTree setMinResolutionInMeter(int minResolutionInMeter) {
        this.minResolutionInMeter = minResolutionInMeter;
        return this;
    }

    /**
     * Searches also neighbouring tiles until the maximum distance from the query point is reached
     * (minResolutionInMeter*regionAround). Set to 1 to only search one tile. Good if you
     * have strict performance requirements and want the search to terminate early, and you can tolerate
     * that edges that may be in neighboring tiles are not found. Default is 4, which means approximately
     * that a square of three tiles upwards, downwards, leftwards and rightwards from the tile the query tile
     * is in is searched.
     */
    public LocationIndexTree setMaxRegionSearch(int numTiles) {
        if (numTiles < 1)
            throw new IllegalArgumentException("Region of location index must be at least 1 but was " + numTiles);
        this.maxRegionSearch = numTiles;
        return this;
    }

    void prepareAlgo() {
        // now calculate the necessary maxDepth d for our current bounds
        // if we assume a minimum resolution like 0.5km for a leaf-tile
        // n^(depth/2) = toMeter(dLon) / minResolution
        BBox bounds = graph.getBounds().clone();

        // I want to be able to create a location index for the empty graph without error, but for that
        // I need valid bounds so that the initialization logic works.
        if (!bounds.isValid())
            bounds = new BBox(-10.0,10.0,-10.0,10.0);

        double lat = Math.min(Math.abs(bounds.maxLat), Math.abs(bounds.minLat));
        double maxDistInMeter = Math.max(
                (bounds.maxLat - bounds.minLat) / 360 * C,
                (bounds.maxLon - bounds.minLon) / 360 * DIST_EARTH.calcCircumference(lat));
        double tmp = maxDistInMeter / minResolutionInMeter;
        tmp = tmp * tmp;
        IntArrayList tmpEntries = new IntArrayList();
        // the last one is always 4 to reduce costs if only a single entry
        tmp /= 4;
        while (tmp > 1) {
            int tmpNo;
            if (tmp >= 16) {
                tmpNo = 16;
            } else if (tmp >= 4) {
                tmpNo = 4;
            } else {
                break;
            }
            tmpEntries.add(tmpNo);
            tmp /= tmpNo;
        }
        tmpEntries.add(4);
        initEntries(tmpEntries.toArray());
        int shiftSum = 0;
        long parts = 1;
        for (int i = 0; i < shifts.length; i++) {
            shiftSum += shifts[i];
            parts *= entries[i];
        }
        if (shiftSum > 64)
            throw new IllegalStateException("sum of all shifts does not fit into a long variable");

        keyAlgo = new SpatialKeyAlgo(shiftSum, bounds);
        parts = Math.round(Math.sqrt(parts));
        deltaLat = (bounds.maxLat - bounds.minLat) / parts;
        deltaLon = (bounds.maxLon - bounds.minLon) / parts;
        pixelGridTraversal = new PixelGridTraversal((int) parts, bounds);
    }

    private LocationIndexTree initEntries(int[] entries) {
        if (entries.length < 1) {
            // at least one depth should have been specified
            throw new IllegalStateException("depth needs to be at least 1");
        }
        this.entries = entries;
        int depth = entries.length;
        shifts = new byte[depth];
        bitmasks = new long[depth];
        int lastEntry = entries[0];
        for (int i = 0; i < depth; i++) {
            if (lastEntry < entries[i]) {
                throw new IllegalStateException("entries should decrease or stay but was:"
                        + Arrays.toString(entries));
            }
            lastEntry = entries[i];
            shifts[i] = getShift(entries[i]);
            bitmasks[i] = getBitmask(shifts[i]);
        }
        return this;
    }

    private byte getShift(int entries) {
        byte b = (byte) Math.round(Math.log(entries) / Math.log(2));
        if (b <= 0)
            throw new IllegalStateException("invalid shift:" + b);

        return b;
    }

    private long getBitmask(int shift) {
        long bm = (1L << shift) - 1;
        if (bm <= 0) {
            throw new IllegalStateException("invalid bitmask:" + bm);
        }
        return bm;
    }

    InMemConstructionIndex getPrepareInMemIndex(EdgeFilter edgeFilter) {
        InMemConstructionIndex memIndex = new InMemConstructionIndex(entries[0]);
        memIndex.prepare(edgeFilter);
        return memIndex;
    }

    public LocationIndex setResolution(int minResolutionInMeter) {
        if (minResolutionInMeter <= 0)
            throw new IllegalStateException("Negative precision is not allowed!");

        setMinResolutionInMeter(minResolutionInMeter);
        return this;
    }

    @Override
    public LocationIndexTree create(long size) {
        throw new UnsupportedOperationException("Not supported. Use prepareIndex instead.");
    }

    @Override
    public boolean loadExisting() {
        if (initialized)
            throw new IllegalStateException("Call loadExisting only once");

        if (!dataAccess.loadExisting())
            return false;

        if (dataAccess.getHeader(0) != MAGIC_INT)
            throw new IllegalStateException("incorrect location index version, expected:" + MAGIC_INT);

        if (dataAccess.getHeader(1 * 4) != calcChecksum())
            throw new IllegalStateException("location index was opened with incorrect graph: "
                    + dataAccess.getHeader(1 * 4) + " vs. " + calcChecksum());

        setMinResolutionInMeter(dataAccess.getHeader(2 * 4));
        prepareAlgo();
        initialized = true;
        return true;
    }

    @Override
    public void flush() {
        dataAccess.setHeader(0, MAGIC_INT);
        dataAccess.setHeader(1 * 4, calcChecksum());
        dataAccess.setHeader(2 * 4, minResolutionInMeter);

        // saving space not necessary: dataAccess.trimTo((lastPointer + 1) * 4);
        dataAccess.flush();
    }

    public LocationIndex prepareIndex() {
        return prepareIndex(EdgeFilter.ALL_EDGES);
    }

    public LocationIndex prepareIndex(EdgeFilter edgeFilter) {
        if (initialized)
            throw new IllegalStateException("Call prepareIndex only once");

        StopWatch sw = new StopWatch().start();
        prepareAlgo();
        // in-memory preparation
        InMemConstructionIndex inMem = getPrepareInMemIndex(edgeFilter);

        // compact & store to dataAccess
        dataAccess.create(64 * 1024);
        try {
            inMem.store(inMem.root, START_POINTER);
            flush();
        } catch (Exception ex) {
            throw new IllegalStateException("Problem while storing location index. " + Helper.getMemInfo(), ex);
        }
        float entriesPerLeaf = (float) inMem.size / inMem.leafs;
        initialized = true;
        logger.info("location index created in " + sw.stop().getSeconds()
                + "s, size:" + Helper.nf(inMem.size)
                + ", leafs:" + Helper.nf(inMem.leafs)
                + ", precision:" + minResolutionInMeter
                + ", depth:" + entries.length
                + ", checksum:" + calcChecksum()
                + ", entries:" + Arrays.toString(entries)
                + ", entriesPerLeaf:" + entriesPerLeaf);

        return this;
    }

    int calcChecksum() {
        return graph.getNodes() ^ graph.getAllEdges().length();
    }

    @Override
    public void close() {
        dataAccess.close();
    }

    @Override
    public boolean isClosed() {
        return dataAccess.isClosed();
    }

    @Override
    public long getCapacity() {
        return dataAccess.getCapacity();
    }

    /**
     * This method fills the set with stored edge IDs from the given spatial key
     */
    final void fillIDs(long keyPart, IntConsumer consumer) {
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

    /**
     * Calculates the distance to the nearest tile border, where the tile border is the rectangular
     * region with dimension 2*paddingTiles + 1 and where the center tile contains the given lat/lon
     * coordinate
     */
    final double calculateRMin(double lat, double lon, int paddingTiles) {
        int x = keyAlgo.x(lon);
        int y = keyAlgo.y(lat);

        // deltaLat and deltaLon comes from the LocationIndex:
        double minLat = graph.getBounds().minLat + (y - paddingTiles) * deltaLat;
        double maxLat = graph.getBounds().minLat + (y + paddingTiles + 1) * deltaLat;
        double minLon = graph.getBounds().minLon + (x - paddingTiles) * deltaLon;
        double maxLon = graph.getBounds().minLon + (x + paddingTiles + 1) * deltaLon;

        double dSouthernLat = lat - minLat;
        double dNorthernLat = maxLat - lat;
        double dWesternLon = lon - minLon;
        double dEasternLon = maxLon - lon;

        // convert degree deltas into a radius in meter
        double dMinLat, dMinLon;
        if (dSouthernLat < dNorthernLat) {
            dMinLat = DIST_PLANE.calcDist(lat, lon, minLat, lon);
        } else {
            dMinLat = DIST_PLANE.calcDist(lat, lon, maxLat, lon);
        }

        if (dWesternLon < dEasternLon) {
            dMinLon = DIST_PLANE.calcDist(lat, lon, lat, minLon);
        } else {
            dMinLon = DIST_PLANE.calcDist(lat, lon, lat, maxLon);
        }

        return Math.min(dMinLat, dMinLon);
    }

    public void query(BBox queryShape, final Visitor function) {
        BBox bbox = graph.getBounds();
        final IntHashSet set = new IntHashSet();
        query(START_POINTER, queryShape,
                bbox.minLat, bbox.minLon, bbox.maxLat - bbox.minLat, bbox.maxLon - bbox.minLon,
                new Visitor() {
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

    final void query(int intPointer, BBox queryBBox,
                     double minLat, double minLon,
                     double deltaLatPerDepth, double deltaLonPerDepth,
                     Visitor function, int depth) {
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
     *
     * If it is called with iteration = 0, it just looks in the tile the query point is in.
     * If it is called with iteration = 0,1,2,.., it will look in additional tiles further and further
     * from the start tile. (In a square that grows by one pixel in all four directions per iteration).
     *
     * See discussion at issue #221.
     * <p>
     */
    final void findEdgeIdsInNeighborhood(double queryLat, double queryLon, int iteration, IntConsumer foundEntries) {
        int x = keyAlgo.x(queryLon);
        int y = keyAlgo.y(queryLat);
        for (int yreg = -iteration; yreg <= iteration; yreg++) {
            int subqueryY = y + yreg;
            int subqueryXA = x - iteration;
            int subqueryXB = x + iteration;
            long keyPart1 = keyAlgo.encode(subqueryXA, subqueryY) << (64 - keyAlgo.getBits());
            fillIDs(keyPart1, foundEntries);

            // When iteration == 0, I just check one tile (the center)
            if (iteration > 0) {
                long keyPart = keyAlgo.encode(subqueryXB, subqueryY) << (64 - keyAlgo.getBits());
                fillIDs(keyPart, foundEntries);
            }
        }

        for (int xreg = -iteration + 1; xreg <= iteration - 1; xreg++) {
            int subqueryX = x + xreg;
            int subqueryYA = y - iteration;
            int subqueryYB = y + iteration;
            long keyPart1 = keyAlgo.encode(subqueryX, subqueryYA) << (64 - keyAlgo.getBits());
            fillIDs(keyPart1, foundEntries);
            long keyPart = keyAlgo.encode(subqueryX, subqueryYB) << (64 - keyAlgo.getBits());
            fillIDs(keyPart, foundEntries);
        }
    }

    @Override
    public Snap findClosest(final double queryLat, final double queryLon, final EdgeFilter edgeFilter) {
        if (isClosed())
            throw new IllegalStateException("You need to create a new LocationIndex instance as it is already closed");

        final Snap closestMatch = new Snap(queryLat, queryLon);
        IntHashSet seenEdges = new IntHashSet();
        for (int iteration = 0; iteration < maxRegionSearch; iteration++) {
            findEdgeIdsInNeighborhood(queryLat, queryLon, iteration, edgeId -> {
                EdgeIteratorState edgeIteratorState = graph.getEdgeIteratorStateForKey(edgeId * 2);
                if (seenEdges.add(edgeId) && edgeFilter.accept(edgeIteratorState)) { // TODO: or reverse?
                    traverseEdge(queryLat, queryLon, edgeIteratorState, (node, normedDist, wayIndex, pos) -> {
                        if (normedDist < closestMatch.getQueryDistance()) {
                            closestMatch.setQueryDistance(normedDist);
                            closestMatch.setClosestNode(node);
                            closestMatch.setClosestEdge(edgeIteratorState.detach(false));
                            closestMatch.setWayIndex(wayIndex);
                            closestMatch.setSnappedPosition(pos);
                        }
                    });
                }
            });
            if (closestMatch.isValid()) {
                // Check if we can stop...
                double rMin = calculateRMin(queryLat, queryLon, iteration);
                double minDistance = DIST_PLANE.calcDenormalizedDist(closestMatch.getQueryDistance());
                if (minDistance < rMin) {
                    break; // We can (approximately?) guarantee that no closer edges are anywhere else
                }
            }
        }

        if (closestMatch.isValid()) {
            closestMatch.setQueryDistance(DIST_PLANE.calcDenormalizedDist(closestMatch.getQueryDistance()));
            closestMatch.calcSnappedPoint(DIST_PLANE);
        }
        return closestMatch;
    }

    // make entries static as otherwise we get an additional reference to this class (memory waste)
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

        public Collection<InMemEntry> getSubEntriesForDebug() {
            List<InMemEntry> list = new ArrayList<>();
            for (InMemEntry e : subEntries) {
                if (e != null) {
                    list.add(e);
                }
            }
            return list;
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

    class InMemConstructionIndex {
        int size;
        int leafs;
        InMemTreeEntry root;

        public InMemConstructionIndex(int noOfSubEntries) {
            root = new InMemTreeEntry(noOfSubEntries);
        }

        void prepare(EdgeFilter edgeFilter) {
            AllEdgesIterator allIter = graph.getAllEdges();
            try {
                while (allIter.next()) {
                    if (!edgeFilter.accept(allIter))
                        continue;
                    int edge = allIter.getEdge();
                    int nodeA = allIter.getBaseNode();
                    int nodeB = allIter.getAdjNode();
                    double lat1 = nodeAccess.getLat(nodeA);
                    double lon1 = nodeAccess.getLon(nodeA);
                    double lat2;
                    double lon2;
                    PointList points = allIter.fetchWayGeometry(FetchMode.PILLAR_ONLY);
                    int len = points.getSize();
                    for (int i = 0; i < len; i++) {
                        lat2 = points.getLat(i);
                        lon2 = points.getLon(i);
                        addEdgeToAllTilesOnLine(edge, lat1, lon1, lat2, lon2);
                        lat1 = lat2;
                        lon1 = lon2;
                    }
                    lat2 = nodeAccess.getLat(nodeB);
                    lon2 = nodeAccess.getLon(nodeB);
                    addEdgeToAllTilesOnLine(edge, lat1, lon1, lat2, lon2);
                }
            } catch (Exception ex) {
                logger.error("Problem! base:" + allIter.getBaseNode() + ", adj:" + allIter.getAdjNode()
                        + ", edge:" + allIter.getEdge(), ex);
            }
        }

        void addEdgeToAllTilesOnLine(final int edgeId, final double lat1, final double lon1, final double lat2, final double lon2) {
            if (!DIST_PLANE.isCrossBoundary(lon1, lon2)) {
                // Find all the tiles on the line from (y1, x1) to (y2, y2) in tile coordinates (y, x)
                pixelGridTraversal.traverse(new Coordinate(lon1, lat1), new Coordinate(lon2, lat2), p -> {
                    long key = keyAlgo.encode((int) p.x, (int) p.y);
                    addEdgeToOneTile(root, edgeId, 0, key << (64 - keyAlgo.getBits()));
                });
            }
        }

        void addEdgeToOneTile(InMemEntry entry, int value, int depth, long keyPart) {
            if (entry.isLeaf()) {
                InMemLeafEntry leafEntry = (InMemLeafEntry) entry;
                // Avoid adding the same edge id multiple times.
                // Since each edge id is handled only once, this can only happen when
                // this method is called several times in a row with the same edge id,
                // so it is enough to check the last entry.
                // (It happens when one edge has several segments. Every segment is traversed
                // on its own, without de-duplicating the tiles that are touched.)
                if (leafEntry.isEmpty() || leafEntry.get(leafEntry.size()-1) != value) {
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
                addEdgeToOneTile(subentry, value, depth, keyPart);
            }
        }

        // store and freezes tree
        int store(InMemEntry entry, int intPointer) {
            long pointer = (long) intPointer * 4;
            if (entry.isLeaf()) {
                InMemLeafEntry leaf = ((InMemLeafEntry) entry);
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
                InMemTreeEntry treeEntry = ((InMemTreeEntry) entry);
                int len = treeEntry.subEntries.length;
                intPointer += len;
                for (int subCounter = 0; subCounter < len; subCounter++, pointer += 4) {
                    InMemEntry subEntry = treeEntry.subEntries[subCounter];
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
    }

    public interface EdgeCheck {
        void check(int node, double normedDist, int wayIndex, Snap.Position pos);
    }

    public void traverseEdge(double queryLat, double queryLon, EdgeIteratorState currEdge, EdgeCheck edgeCheck) {
        int baseNode = currEdge.getBaseNode();
        double currLat = nodeAccess.getLat(baseNode);
        double currLon = nodeAccess.getLon(baseNode);
        double currNormedDist = DIST_PLANE.calcNormalizedDist(queryLat, queryLon, currLat, currLon);

        int tmpClosestNode = baseNode;
        edgeCheck.check(tmpClosestNode, currNormedDist, 0, Snap.Position.TOWER);
        if (currNormedDist <= equalNormedDelta)
            return;

        int adjNode = currEdge.getAdjNode();
        double adjLat = nodeAccess.getLat(adjNode);
        double adjLon = nodeAccess.getLon(adjNode);
        double adjDist = DIST_PLANE.calcNormalizedDist(adjLat, adjLon, queryLat, queryLon);
        // if there are wayPoints this is only an approximation
        if (adjDist < currNormedDist)
            tmpClosestNode = adjNode;

        double tmpLat = currLat;
        double tmpLon = currLon;
        double tmpNormedDist;
        PointList pointList = currEdge.fetchWayGeometry(FetchMode.PILLAR_AND_ADJ);
        int len = pointList.getSize();
        for (int pointIndex = 0; pointIndex < len; pointIndex++) {
            double wayLat = pointList.getLat(pointIndex);
            double wayLon = pointList.getLon(pointIndex);
            Snap.Position pos = Snap.Position.EDGE;
            if (DIST_PLANE.isCrossBoundary(tmpLon, wayLon)) {
                tmpLat = wayLat;
                tmpLon = wayLon;
                continue;
            }

            if (DIST_PLANE.validEdgeDistance(queryLat, queryLon, tmpLat, tmpLon, wayLat, wayLon)) {
                tmpNormedDist = DIST_PLANE.calcNormalizedEdgeDistance(queryLat, queryLon,
                        tmpLat, tmpLon, wayLat, wayLon);
                edgeCheck.check(tmpClosestNode, tmpNormedDist, pointIndex, pos);
            } else {
                if (pointIndex + 1 == len) {
                    tmpNormedDist = adjDist;
                    pos = Snap.Position.TOWER;
                } else {
                    tmpNormedDist = DIST_PLANE.calcNormalizedDist(queryLat, queryLon, wayLat, wayLon);
                    pos = Snap.Position.PILLAR;
                }
                edgeCheck.check(tmpClosestNode, tmpNormedDist, pointIndex + 1, pos);
            }

            if (tmpNormedDist <= equalNormedDelta)
                return;

            tmpLat = wayLat;
            tmpLon = wayLon;
        }
    }

}
