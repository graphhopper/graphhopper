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
import com.carrotsearch.hppc.cursors.IntCursor;
import com.carrotsearch.hppc.predicates.IntPredicate;
import com.graphhopper.coll.GHBitSet;
import com.graphhopper.coll.GHIntHashSet;
import com.graphhopper.coll.GHTBitSet;
import com.graphhopper.geohash.SpatialKeyAlgo;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.*;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * This implementation implements an n-tree to get the closest node or edge from GPS coordinates.
 * <p>
 * All leafs are at the same depth, otherwise it is quite complicated to calculate the Bresenham
 * line for different resolutions, especially if a leaf node could be split into a tree-node and
 * resolution changes.
 * <p>
 *
 * @author Peter Karich
 */
public class LocationIndexTree implements LocationIndex {
    // do not start with 0 as a positive value means leaf and a negative means "entry with subentries"
    static final int START_POINTER = 1;
    protected final Graph graph;
    final DataAccess dataAccess;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final int MAGIC_INT;
    private final NodeAccess nodeAccess;
    protected DistanceCalc distCalc = Helper.DIST_PLANE;
    protected SpatialKeyAlgo keyAlgo;
    int maxRegionSearch = 4;
    private DistanceCalc preciseDistCalc = Helper.DIST_EARTH;
    private int[] entries;
    private byte[] shifts;
    // convert spatial key to index for subentry of current depth
    private long[] bitmasks;
    private int minResolutionInMeter = 300;
    private double deltaLat;
    private double deltaLon;
    private int initSizeLeafEntries = 4;
    private boolean initialized = false;
    private static final Comparator<QueryResult> QR_COMPARATOR = new Comparator<QueryResult>() {
        @Override
        public int compare(QueryResult o1, QueryResult o2) {
            return Double.compare(o1.getQueryDistance(), o2.getQueryDistance());
        }
    };
    /**
     * If normed distance is smaller than this value the node or edge is 'identical' and the
     * algorithm can stop search.
     */
    private double equalNormedDelta;

    /**
     * @param g the graph for which this index should do the lookup based on latitude,longitude.
     */
    public LocationIndexTree(Graph g, Directory dir) {
        if (g instanceof CHGraph)
            throw new IllegalArgumentException("Use base graph for LocationIndexTree instead of CHGraph");

        MAGIC_INT = Integer.MAX_VALUE / 22316;
        this.graph = g;
        this.nodeAccess = g.getNodeAccess();
        dataAccess = dir.find("location_index", DAType.getPreferredInt(dir.getDefaultType()));
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
     * (minResolutionInMeter*regionAround). Set to 1 for to force avoiding a fall back, good if you
     * have strict performance and lookup-quality requirements. Default is 4.
     */
    public LocationIndexTree setMaxRegionSearch(int numTiles) {
        if (numTiles < 1)
            throw new IllegalArgumentException("Region of location index must be at least 1 but was " + numTiles);

        // see #232
        if (numTiles % 2 == 1)
            numTiles++;

        this.maxRegionSearch = numTiles;
        return this;
    }

    void prepareAlgo() {
        // 0.1 meter should count as 'equal'
        equalNormedDelta = distCalc.calcNormalizedDist(0.1);

        // now calculate the necessary maxDepth d for our current bounds
        // if we assume a minimum resolution like 0.5km for a leaf-tile                
        // n^(depth/2) = toMeter(dLon) / minResolution
        BBox bounds = graph.getBounds();
        if (graph.getNodes() == 0)
            throw new IllegalStateException("Cannot create location index of empty graph!");

        if (!bounds.isValid())
            throw new IllegalStateException("Cannot create location index when graph has invalid bounds: " + bounds);

        double lat = Math.min(Math.abs(bounds.maxLat), Math.abs(bounds.minLat));
        double maxDistInMeter = Math.max(
                (bounds.maxLat - bounds.minLat) / 360 * DistanceCalcEarth.C,
                (bounds.maxLon - bounds.minLon) / 360 * preciseDistCalc.calcCircumference(lat));
        double tmp = maxDistInMeter / minResolutionInMeter;
        tmp = tmp * tmp;
        IntArrayList tmpEntries = new IntArrayList();
        // the last one is always 4 to reduce costs if only a single entry
        tmp /= 4;
        while (tmp > 1) {
            int tmpNo;
            if (tmp >= 64) {
                tmpNo = 64;
            } else if (tmp >= 16) {
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

        keyAlgo = new SpatialKeyAlgo(shiftSum).bounds(bounds);
        parts = Math.round(Math.sqrt(parts));
        deltaLat = (bounds.maxLat - bounds.minLat) / parts;
        deltaLon = (bounds.maxLon - bounds.minLon) / parts;
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

    InMemConstructionIndex getPrepareInMemIndex() {
        InMemConstructionIndex memIndex = new InMemConstructionIndex(entries[0]);
        memIndex.prepare();
        return memIndex;
    }

    @Override
    public LocationIndex setResolution(int minResolutionInMeter) {
        if (minResolutionInMeter <= 0)
            throw new IllegalStateException("Negative precision is not allowed!");

        setMinResolutionInMeter(minResolutionInMeter);
        return this;
    }

    @Override
    public LocationIndex setApproximation(boolean approx) {
        if (approx)
            distCalc = Helper.DIST_PLANE;
        else
            distCalc = Helper.DIST_EARTH;
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

    @Override
    public LocationIndex prepareIndex() {
        if (initialized)
            throw new IllegalStateException("Call prepareIndex only once");

        StopWatch sw = new StopWatch().start();
        prepareAlgo();
        // in-memory preparation
        InMemConstructionIndex inMem = getPrepareInMemIndex();

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
        // do not include the edges as we could get problem with CHGraph due to shortcuts
        // ^ graph.getAllEdges().count();
        return graph.getNodes();
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

    @Override
    public void setSegmentSize(int bytes) {
        dataAccess.setSegmentSize(bytes);
    }

    // just for test
    IntArrayList getEntries() {
        return IntArrayList.from(entries);
    }

    // fill node IDs according to how they are stored
    final void fillIDs(long keyPart, int intIndex, GHIntHashSet set, int depth) {
        long pointer = (long) intIndex << 2;
        if (depth == entries.length) {
            int value = dataAccess.getInt(pointer);
            if (value < 0) {
                // single data entries (less disc space)
                set.add(-(value + 1));
            } else {
                long max = (long) value * 4;
                // leaf entry => value is maxPointer
                for (long leafIndex = pointer + 4; leafIndex < max; leafIndex += 4) {
                    set.add(dataAccess.getInt(leafIndex));
                }
            }
            return;
        }
        int offset = (int) (bitmasks[depth] & keyPart) << 2;
        int value = dataAccess.getInt(pointer + offset);
        if (value > 0) {
            // tree entry => negative value points to subentries
            fillIDs(keyPart >>> shifts[depth], value, set, depth + 1);
        }
    }

    // this method returns the spatial key in reverse order for easier right-shifting
    final long createReverseKey(double lat, double lon) {
        return BitUtil.BIG.reverse(keyAlgo.encode(lat, lon), keyAlgo.getBits());
    }

    final long createReverseKey(long key) {
        return BitUtil.BIG.reverse(key, keyAlgo.getBits());
    }

    /**
     * calculate the distance to the nearest tile border for a given lat/lon coordinate in the
     * context of a spatial key tile.
     * <p>
     */
    final double calculateRMin(double lat, double lon) {
        return calculateRMin(lat, lon, 0);
    }

    /**
     * Calculates the distance to the nearest tile border, where the tile border is the rectangular
     * region with dimension 2*paddingTiles + 1 and where the center tile contains the given lat/lon
     * coordinate
     */
    final double calculateRMin(double lat, double lon, int paddingTiles) {
        GHPoint query = new GHPoint(lat, lon);
        long key = keyAlgo.encode(query);
        GHPoint center = new GHPoint();
        keyAlgo.decode(key, center);

        // deltaLat and deltaLon comes from the LocationIndex:
        double minLat = center.lat - (0.5 + paddingTiles) * deltaLat;
        double maxLat = center.lat + (0.5 + paddingTiles) * deltaLat;
        double minLon = center.lon - (0.5 + paddingTiles) * deltaLon;
        double maxLon = center.lon + (0.5 + paddingTiles) * deltaLon;

        double dSouthernLat = query.lat - minLat;
        double dNorthernLat = maxLat - query.lat;
        double dWesternLon = query.lon - minLon;
        double dEasternLon = maxLon - query.lon;

        // convert degree deltas into a radius in meter
        double dMinLat, dMinLon;
        if (dSouthernLat < dNorthernLat) {
            dMinLat = distCalc.calcDist(query.lat, query.lon, minLat, query.lon);
        } else {
            dMinLat = distCalc.calcDist(query.lat, query.lon, maxLat, query.lon);
        }

        if (dWesternLon < dEasternLon) {
            dMinLon = distCalc.calcDist(query.lat, query.lon, query.lat, minLon);
        } else {
            dMinLon = distCalc.calcDist(query.lat, query.lon, query.lat, maxLon);
        }

        double rMin = Math.min(dMinLat, dMinLon);
        return rMin;
    }

    /**
     * Provide info about tilesize for testing / visualization
     */
    public double getDeltaLat() {
        return deltaLat;
    }

    public double getDeltaLon() {
        return deltaLon;
    }

    GHPoint getCenter(double lat, double lon) {
        GHPoint query = new GHPoint(lat, lon);
        long key = keyAlgo.encode(query);
        GHPoint center = new GHPoint();
        keyAlgo.decode(key, center);
        return center;
    }

    /**
     * This method collects the node indices from the quad tree data structure in a certain order
     * which makes sure not too many nodes are collected as well as no nodes will be missing. See
     * discussion at issue #221.
     * <p>
     *
     * @return true if no further call of this method is required. False otherwise, ie. a next
     * iteration is necessary and no early finish possible.
     */
    public final boolean findNetworkEntries(double queryLat, double queryLon,
                                            GHIntHashSet foundEntries, int iteration) {
        // find entries in border of searchbox
        for (int yreg = -iteration; yreg <= iteration; yreg++) {
            double subqueryLat = queryLat + yreg * deltaLat;
            double subqueryLonA = queryLon - iteration * deltaLon;
            double subqueryLonB = queryLon + iteration * deltaLon;
            findNetworkEntriesSingleRegion(foundEntries, subqueryLat, subqueryLonA);

            // minor optimization for iteration == 0
            if (iteration > 0)
                findNetworkEntriesSingleRegion(foundEntries, subqueryLat, subqueryLonB);
        }

        for (int xreg = -iteration + 1; xreg <= iteration - 1; xreg++) {
            double subqueryLon = queryLon + xreg * deltaLon;
            double subqueryLatA = queryLat - iteration * deltaLat;
            double subqueryLatB = queryLat + iteration * deltaLat;
            findNetworkEntriesSingleRegion(foundEntries, subqueryLatA, subqueryLon);
            findNetworkEntriesSingleRegion(foundEntries, subqueryLatB, subqueryLon);
        }

        if (iteration % 2 != 0) {
            // Check if something was found already...
            if (!foundEntries.isEmpty()) {
                double rMin = calculateRMin(queryLat, queryLon, iteration);
                double minDistance = calcMinDistance(queryLat, queryLon, foundEntries);

                if (minDistance < rMin)
                    // early finish => foundEntries contains a nearest node for sure
                    return true;
                // else: continue as an undetected nearer node may sit in a neighbouring tile.
                // Now calculate how far we have to look outside to find any hidden nearest nodes
                // and repeat whole process with wider search area until this distance is covered.
            }
        }

        // no early finish possible
        return false;
    }

    final double calcMinDistance(double queryLat, double queryLon, GHIntHashSet pointset) {
        double min = Double.MAX_VALUE;
        Iterator<IntCursor> itr = pointset.iterator();
        while (itr.hasNext()) {
            int node = itr.next().value;
            double lat = nodeAccess.getLat(node);
            double lon = nodeAccess.getLon(node);
            double dist = distCalc.calcDist(queryLat, queryLon, lat, lon);
            if (dist < min) {
                min = dist;
            }
        }
        return min;
    }

    public final void findNetworkEntriesSingleRegion(GHIntHashSet storedNetworkEntryIds, double queryLat, double queryLon) {
        long keyPart = createReverseKey(queryLat, queryLon);
        fillIDs(keyPart, START_POINTER, storedNetworkEntryIds, 0);
    }

    @Override
    public QueryResult findClosest(final double queryLat, final double queryLon, final EdgeFilter edgeFilter) {
        if (isClosed())
            throw new IllegalStateException("You need to create a new LocationIndex instance as it is already closed");

        GHIntHashSet allCollectedEntryIds = new GHIntHashSet();
        final QueryResult closestMatch = new QueryResult(queryLat, queryLon);
        for (int iteration = 0; iteration < maxRegionSearch; iteration++) {
            GHIntHashSet storedNetworkEntryIds = new GHIntHashSet();
            boolean earlyFinish = findNetworkEntries(queryLat, queryLon, storedNetworkEntryIds, iteration);
            storedNetworkEntryIds.removeAll(allCollectedEntryIds);
            allCollectedEntryIds.addAll(storedNetworkEntryIds);

            // clone storedIds to avoid interference with forEach
            final GHBitSet checkBitset = new GHTBitSet(new GHIntHashSet(storedNetworkEntryIds));
            // find nodes from the network entries which are close to 'point'
            final EdgeExplorer explorer = graph.createEdgeExplorer();
            storedNetworkEntryIds.forEach(new IntPredicate() {
                @Override
                public boolean apply(int networkEntryNodeId) {
                    new XFirstSearchCheck(queryLat, queryLon, checkBitset, edgeFilter) {
                        @Override
                        protected double getQueryDistance() {
                            return closestMatch.getQueryDistance();
                        }

                        @Override
                        protected boolean check(int node, double normedDist, int wayIndex, EdgeIteratorState edge, QueryResult.Position pos) {
                            if (normedDist < closestMatch.getQueryDistance()) {
                                closestMatch.setQueryDistance(normedDist);
                                closestMatch.setClosestNode(node);
                                closestMatch.setClosestEdge(edge.detach(false));
                                closestMatch.setWayIndex(wayIndex);
                                closestMatch.setSnappedPosition(pos);
                                return true;
                            }
                            return false;
                        }
                    }.start(explorer, networkEntryNodeId);
                    return true;
                }
            });

            // do early finish only if something was found (#318)
            if (earlyFinish && closestMatch.isValid())
                break;
        }

        // denormalize distance and calculate snapping point only if closed match was found
        if (closestMatch.isValid()) {
            closestMatch.setQueryDistance(distCalc.calcDenormalizedDist(closestMatch.getQueryDistance()));
            closestMatch.calcSnappedPoint(distCalc);
        }

        return closestMatch;
    }

    /**
     * Returns all edges that are within the specified radius around the queried position.
     * Searches at most 9 cells to avoid performance problems. Hence, if the radius is larger than
     * the cell width then not all edges might be returned.
     * <p>
     * TODO: either clarify the method name and description (to only search e.g. 9 tiles) or
     * refactor so it can handle a radius larger than 9 tiles. Also remove reference to 'NClosest',
     * which is misleading, and don't always return at least one value. See map-matching #65.
     * TODO: tidy up logic - see comments in graphhopper #994.
     *
     * @param radius in meters
     */
    public List<QueryResult> findNClosest(final double queryLat, final double queryLon,
                                          final EdgeFilter edgeFilter, double radius) {
        // Return ALL results which are very close and e.g. within the GPS signal accuracy.
        // Also important to get all edges if GPS point is close to a junction.
        final double returnAllResultsWithin = distCalc.calcNormalizedDist(radius);

        // implement a cheap priority queue via List, sublist and Collections.sort
        final List<QueryResult> queryResults = new ArrayList<>();
        GHIntHashSet set = new GHIntHashSet();

        // Doing 2 iterations means searching 9 tiles.
        for (int iteration = 0; iteration < 2; iteration++) {
            // should we use the return value of earlyFinish?
            findNetworkEntries(queryLat, queryLon, set, iteration);

            final GHBitSet exploredNodes = new GHTBitSet(new GHIntHashSet(set));
            final EdgeExplorer explorer = graph.createEdgeExplorer(edgeFilter);

            set.forEach(new IntPredicate() {

                @Override
                public boolean apply(int node) {
                    new XFirstSearchCheck(queryLat, queryLon, exploredNodes, edgeFilter) {
                        @Override
                        protected double getQueryDistance() {
                            // do not skip search if distance is 0 or near zero (equalNormedDelta)
                            return Double.MAX_VALUE;
                        }

                        @Override
                        protected boolean check(int node, double normedDist, int wayIndex, EdgeIteratorState edge, QueryResult.Position pos) {
                            if (normedDist < returnAllResultsWithin
                                    || queryResults.isEmpty()
                                    || queryResults.get(0).getQueryDistance() > normedDist) {
                                // TODO: refactor below:
                                // - should only add edges within search radius (below allows the
                                // returning of a candidate outside search radius if it's the only
                                // one). Removing this test would simplify it a lot and probably
                                // match the expected behaviour (see method description)
                                // - create QueryResult first and the add/set as required - clean up
                                // the index tracking business.

                                int index = -1;
                                for (int qrIndex = 0; qrIndex < queryResults.size(); qrIndex++) {
                                    QueryResult qr = queryResults.get(qrIndex);
                                    // overwrite older queryResults which are potentially more far away than returnAllResultsWithin
                                    if (qr.getQueryDistance() > returnAllResultsWithin) {
                                        index = qrIndex;
                                        break;
                                    }

                                    // avoid duplicate edges
                                    if (qr.getClosestEdge().getEdge() == edge.getEdge()) {
                                        if (qr.getQueryDistance() < normedDist) {
                                            // do not add current edge
                                            return true;
                                        } else {
                                            // overwrite old edge with current
                                            index = qrIndex;
                                            break;
                                        }
                                    }
                                }

                                QueryResult qr = new QueryResult(queryLat, queryLon);
                                qr.setQueryDistance(normedDist);
                                qr.setClosestNode(node);
                                qr.setClosestEdge(edge.detach(false));
                                qr.setWayIndex(wayIndex);
                                qr.setSnappedPosition(pos);

                                if (index < 0) {
                                    queryResults.add(qr);
                                } else {
                                    queryResults.set(index, qr);
                                }
                            }
                            return true;
                        }
                    }.start(explorer, node);
                    return true;
                }
            });
        }

        // TODO: pass boolean argument for whether or not to sort? Can be expensive if not required.
        Collections.sort(queryResults, QR_COMPARATOR);

        for (QueryResult qr : queryResults) {
            if (qr.isValid()) {
                // denormalize distance
                qr.setQueryDistance(distCalc.calcDenormalizedDist(qr.getQueryDistance()));
                qr.calcSnappedPoint(distCalc);
            } else {
                throw new IllegalStateException("Invalid QueryResult should not happen here: " + qr);
            }
        }

        return queryResults;
    }

    // make entries static as otherwise we get an additional reference to this class (memory waste)
    interface InMemEntry {
        boolean isLeaf();
    }

    static class InMemLeafEntry extends SortedIntSet implements InMemEntry {
        // private long key;

        public InMemLeafEntry(int count, long key) {
            super(count);
            // this.key = key;
        }

        public boolean addNode(int nodeId) {
            return addOnce(nodeId);
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

    // Space efficient sorted integer set. Suited for only a few entries.
    static class SortedIntSet extends IntArrayList {
        public SortedIntSet() {
        }

        public SortedIntSet(int capacity) {
            super(capacity);
        }

        /**
         * Allow adding a value only once
         */
        public boolean addOnce(int value) {
            int foundIndex = Arrays.binarySearch(buffer, 0, size(), value);
            if (foundIndex >= 0) {
                return false;
            }
            foundIndex = -foundIndex - 1;
            insert(foundIndex, value);
            return true;
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

        void prepare() {
            final EdgeIterator allIter = graph.getAllEdges();
            try {
                while (allIter.next()) {
                    int nodeA = allIter.getBaseNode();
                    int nodeB = allIter.getAdjNode();
                    double lat1 = nodeAccess.getLatitude(nodeA);
                    double lon1 = nodeAccess.getLongitude(nodeA);
                    double lat2;
                    double lon2;
                    PointList points = allIter.fetchWayGeometry(0);
                    int len = points.getSize();
                    for (int i = 0; i < len; i++) {
                        lat2 = points.getLatitude(i);
                        lon2 = points.getLongitude(i);
                        addNode(nodeA, nodeB, lat1, lon1, lat2, lon2);
                        lat1 = lat2;
                        lon1 = lon2;
                    }
                    lat2 = nodeAccess.getLatitude(nodeB);
                    lon2 = nodeAccess.getLongitude(nodeB);
                    addNode(nodeA, nodeB, lat1, lon1, lat2, lon2);
                }
            } catch (Exception ex) {
                logger.error("Problem! base:" + allIter.getBaseNode() + ", adj:" + allIter.getAdjNode()
                        + ", edge:" + allIter.getEdge(), ex);
            }
        }

        void addNode(final int nodeA, final int nodeB,
                     final double lat1, final double lon1,
                     final double lat2, final double lon2) {
            PointEmitter pointEmitter = new PointEmitter() {
                @Override
                public void set(double lat, double lon) {
                    long key = keyAlgo.encode(lat, lon);
                    long keyPart = createReverseKey(key);
                    // no need to feed both nodes as we search neighbors in fillIDs
                    addNode(root, nodeA, 0, keyPart, key);
                }
            };

            if (!distCalc.isCrossBoundary(lon1, lon2)) {
                BresenhamLine.calcPoints(lat1, lon1, lat2, lon2, pointEmitter,
                        graph.getBounds().minLat, graph.getBounds().minLon,
                        deltaLat, deltaLon);
            }
        }

        void addNode(InMemEntry entry, int nodeId, int depth, long keyPart, long key) {
            if (entry.isLeaf()) {
                InMemLeafEntry leafEntry = (InMemLeafEntry) entry;
                leafEntry.addNode(nodeId);
            } else {
                int index = (int) (bitmasks[depth] & keyPart);
                keyPart = keyPart >>> shifts[depth];
                InMemTreeEntry treeEntry = ((InMemTreeEntry) entry);
                InMemEntry subentry = treeEntry.getSubEntry(index);
                depth++;
                if (subentry == null) {
                    if (depth == entries.length) {
                        subentry = new InMemLeafEntry(initSizeLeafEntries, key);
                    } else {
                        subentry = new InMemTreeEntry(entries[depth]);
                    }
                    treeEntry.setSubEntry(index, subentry);
                }

                addNode(subentry, nodeId, depth, keyPart, key);
            }
        }

        Collection<InMemEntry> getEntriesOf(int selectDepth) {
            List<InMemEntry> list = new ArrayList<>();
            fillLayer(list, selectDepth, 0, ((InMemTreeEntry) root).getSubEntriesForDebug());
            return list;
        }

        void fillLayer(Collection<InMemEntry> list, int selectDepth, int depth, Collection<InMemEntry> entries) {
            for (InMemEntry entry : entries) {
                if (selectDepth == depth) {
                    list.add(entry);
                } else if (entry instanceof InMemTreeEntry) {
                    fillLayer(list, selectDepth, depth + 1, ((InMemTreeEntry) entry).getSubEntriesForDebug());
                }
            }
        }

        String print() {
            StringBuilder sb = new StringBuilder();
            print(root, sb, 0, 0);
            return sb.toString();
        }

        void print(InMemEntry e, StringBuilder sb, long key, int depth) {
            if (e.isLeaf()) {
                InMemLeafEntry leaf = (InMemLeafEntry) e;
                int bits = keyAlgo.getBits();
                // print reverse keys
                sb.append(BitUtil.BIG.toBitString(BitUtil.BIG.reverse(key, bits), bits)).append("  ");
                IntArrayList entries = leaf.getResults();
                for (int i = 0; i < entries.size(); i++) {
                    sb.append(leaf.get(i)).append(',');
                }
                sb.append('\n');
            } else {
                InMemTreeEntry tree = (InMemTreeEntry) e;
                key = key << shifts[depth];
                for (int counter = 0; counter < tree.subEntries.length; counter++) {
                    InMemEntry sube = tree.subEntries[counter];
                    if (sube != null) {
                        print(sube, sb, key | counter, depth + 1);
                    }
                }
            }
        }

        // store and freezes tree
        int store(InMemEntry entry, int intIndex) {
            long refPointer = (long) intIndex * 4;
            if (entry.isLeaf()) {
                InMemLeafEntry leaf = ((InMemLeafEntry) entry);
                IntArrayList entries = leaf.getResults();
                int len = entries.size();
                if (len == 0) {
                    return intIndex;
                }
                size += len;
                intIndex++;
                leafs++;
                dataAccess.ensureCapacity((long) (intIndex + len + 1) * 4);
                if (len == 1) {
                    // less disc space for single entries
                    dataAccess.setInt(refPointer, -entries.get(0) - 1);
                } else {
                    for (int index = 0; index < len; index++, intIndex++) {
                        dataAccess.setInt((long) intIndex * 4, entries.get(index));
                    }
                    dataAccess.setInt(refPointer, intIndex);
                }
            } else {
                InMemTreeEntry treeEntry = ((InMemTreeEntry) entry);
                int len = treeEntry.subEntries.length;
                intIndex += len;
                for (int subCounter = 0; subCounter < len; subCounter++, refPointer += 4) {
                    InMemEntry subEntry = treeEntry.subEntries[subCounter];
                    if (subEntry == null) {
                        continue;
                    }
                    dataAccess.ensureCapacity((long) (intIndex + 1) * 4);
                    int beforeIntIndex = intIndex;
                    intIndex = store(subEntry, beforeIntIndex);
                    if (intIndex == beforeIntIndex) {
                        dataAccess.setInt(refPointer, 0);
                    } else {
                        dataAccess.setInt(refPointer, beforeIntIndex);
                    }
                }
            }
            return intIndex;
        }
    }

    /**
     * Make it possible to collect nearby location also for other purposes.
     */
    protected abstract class XFirstSearchCheck extends BreadthFirstSearch {
        final double queryLat;
        final double queryLon;
        final GHBitSet checkBitset;
        final EdgeFilter edgeFilter;
        boolean goFurther = true;
        double currNormedDist;
        double currLat;
        double currLon;
        int currNode;

        public XFirstSearchCheck(double queryLat, double queryLon, GHBitSet checkBitset, EdgeFilter edgeFilter) {
            this.queryLat = queryLat;
            this.queryLon = queryLon;
            this.checkBitset = checkBitset;
            this.edgeFilter = edgeFilter;
        }

        @Override
        protected GHBitSet createBitSet() {
            return checkBitset;
        }

        @Override
        protected boolean goFurther(int baseNode) {
            currNode = baseNode;
            currLat = nodeAccess.getLatitude(baseNode);
            currLon = nodeAccess.getLongitude(baseNode);
            currNormedDist = distCalc.calcNormalizedDist(queryLat, queryLon, currLat, currLon);
            return goFurther;
        }

        @Override
        protected boolean checkAdjacent(EdgeIteratorState currEdge) {
            goFurther = false;
            if (!edgeFilter.accept(currEdge)) {
                // only limit the adjNode to a certain radius as currNode could be the wrong side of a valid edge
                // goFurther = currDist < minResolution2InMeterNormed;
                return true;
            }

            int tmpClosestNode = currNode;
            if (check(tmpClosestNode, currNormedDist, 0, currEdge, QueryResult.Position.TOWER)) {
                if (currNormedDist <= equalNormedDelta)
                    return false;
            }

            int adjNode = currEdge.getAdjNode();
            double adjLat = nodeAccess.getLatitude(adjNode);
            double adjLon = nodeAccess.getLongitude(adjNode);
            double adjDist = distCalc.calcNormalizedDist(adjLat, adjLon, queryLat, queryLon);
            // if there are wayPoints this is only an approximation
            if (adjDist < currNormedDist)
                tmpClosestNode = adjNode;

            double tmpLat = currLat;
            double tmpLon = currLon;
            double tmpNormedDist;
            PointList pointList = currEdge.fetchWayGeometry(2);
            int len = pointList.getSize();
            for (int pointIndex = 0; pointIndex < len; pointIndex++) {
                double wayLat = pointList.getLatitude(pointIndex);
                double wayLon = pointList.getLongitude(pointIndex);
                QueryResult.Position pos = QueryResult.Position.EDGE;
                if (distCalc.isCrossBoundary(tmpLon, wayLon)) {
                    tmpLat = wayLat;
                    tmpLon = wayLon;
                    continue;
                }

                if (distCalc.validEdgeDistance(queryLat, queryLon, tmpLat, tmpLon, wayLat, wayLon)) {
                    tmpNormedDist = distCalc.calcNormalizedEdgeDistance(queryLat, queryLon,
                            tmpLat, tmpLon, wayLat, wayLon);
                    check(tmpClosestNode, tmpNormedDist, pointIndex, currEdge, pos);
                } else {
                    if (pointIndex + 1 == len) {
                        tmpNormedDist = adjDist;
                        pos = QueryResult.Position.TOWER;
                    } else {
                        tmpNormedDist = distCalc.calcNormalizedDist(queryLat, queryLon, wayLat, wayLon);
                        pos = QueryResult.Position.PILLAR;
                    }
                    check(tmpClosestNode, tmpNormedDist, pointIndex + 1, currEdge, pos);
                }

                if (tmpNormedDist <= equalNormedDelta)
                    return false;

                tmpLat = wayLat;
                tmpLon = wayLon;
            }
            return getQueryDistance() > equalNormedDelta;
        }

        protected abstract double getQueryDistance();

        protected abstract boolean check(int node, double normedDist, int wayIndex, EdgeIteratorState iter, QueryResult.Position pos);
    }
}
