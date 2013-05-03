/*
 *  Licensed to Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  Peter Karich licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the
 *  License at
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

import com.graphhopper.coll.GHBitSet;
import com.graphhopper.coll.GHTBitSet;
import com.graphhopper.geohash.SpatialKeyAlgo;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.BitUtil;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistancePlaneProjection;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.Helper;
import com.graphhopper.util.NumHelper;
import com.graphhopper.util.PointList;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.XFirstSearch;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPlace;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.procedure.TIntProcedure;
import gnu.trove.set.hash.TIntHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This implementation implements an n-tree to get node ids from GPS location.
 * This replaces Location2IDQuadtree except for cases when you only need rough
 * precision or when you need better support for out-of-bounds queries.
 *
 * All leafs are at the same depth, otherwise it is quite complicated to
 * calculate the bresenham line for different resolutions, especially if a leaf
 * node could be split into a tree-node and resolution changes.
 *
 * @author Peter Karich
 */
public class Location2NodesNtree implements Location2NodesIndex, Location2IDIndex {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final int MAGIC_INT;
    private DistanceCalc distCalc = new DistancePlaneProjection();
    final DataAccess dataAccess;
    private final Graph graph;
    /**
     * With maximum depth you control precision versus memory usage. The higher
     * the more memory wasted due to references but more precise value selection
     * can happen.
     */
    private int maxDepth;
    private int subEntries;
    private int shift;
    // convert spatial key to index for subentry of current depth
    private long bitmask;
    SpatialKeyAlgo keyAlgo;
    private int minResolutionInMeter;
    private double deltaLat;
    private double deltaLon;
    private int initLeafEntries = 4;
    private boolean initialized = false;
    // do not start with 0 as a positive value means leaf and a negative means "entry with subentries"
    static final int START_POINTER = 1;
    private boolean edgeDistCalcOnSearch = true;
    private boolean regionSearch = true;

    public Location2NodesNtree(Graph g, Directory dir) {
        MAGIC_INT = Integer.MAX_VALUE / 22316;
        this.graph = g;
        dataAccess = dir.findCreate("spatialNIndex");
        subEntries(4);
        minResolutionInMeter(500);
    }

    /**
     * subEntries is n and defines the branches (== tiles) per depth
     * <pre>
     * 2 * 2 tiles => n=2^2, if n=4 then this is a quadtree
     * 4 * 4 tiles => n=4^2
     * </pre>
     */
    Location2NodesNtree subEntries(int subEntries) {
        this.subEntries = subEntries;
        return this;
    }

    public int minResolutionInMeter() {
        return minResolutionInMeter;
    }

    public Location2NodesNtree minResolutionInMeter(int minResolutionInMeter) {
        this.minResolutionInMeter = minResolutionInMeter;
        return this;
    }

    /**
     * Calculate edge distance to increase map matching precision.
     */
    public Location2NodesNtree edgeCalcOnFind(boolean edgeCalcOnSearch) {
        this.edgeDistCalcOnSearch = edgeCalcOnSearch;
        return this;
    }

    /**
     * Searches also neighbouring quadtree entries to increase map matching
     * precision.
     */
    public Location2NodesNtree searchRegion(boolean regionAround) {
        this.regionSearch = regionAround;
        return this;
    }

    void prepareAlgo() {
        shift = (int) Math.round(Math.log(subEntries) / Math.log(2));
        // Math.log(1) == 0
        if (shift < 2)
            throw new IllegalStateException("Too few subEntries:" + subEntries + ", shift:" + shift);
        bitmask = (1 << shift) - 1;

        // now calculate the necessary maxDepth d for our current bounds
        // if we assume a minimum resolution like 0.5km for a leaf-tile                
        // n^(depth/2) = toMeter(dLon) / minResolution
        BBox bounds = graph.bounds();
        double lat = Math.min(Math.abs(bounds.maxLat), Math.abs(bounds.minLat));
        double maxDistInMeter = Math.max(
                (bounds.maxLat - bounds.minLat) / 360 * DistanceCalc.C,
                (bounds.maxLon - bounds.minLon) / 360 * distCalc.calcCircumference(lat));
        int tmpDepth = (int) (Math.log(maxDistInMeter / minResolutionInMeter) / Math.log(shift));
        maxDepth = Math.min(64 / shift, Math.max(0, tmpDepth) + 1);
        if (maxDepth <= 0 || maxDepth * shift > 64)
            throw new IllegalStateException("Bounds or minimal resolution wrong:"
                    + bounds + ", resolution:" + minResolutionInMeter
                    + ", shift:" + shift + ", maxDepth:" + maxDepth + ", tmpDepth:" + tmpDepth);
        keyAlgo = new SpatialKeyAlgo(maxDepth * shift).bounds(bounds);
        long parts = Math.round(Math.pow(shift, maxDepth));
        deltaLat = (graph.bounds().maxLat - graph.bounds().minLat) / parts;
        deltaLon = (graph.bounds().maxLon - graph.bounds().minLon) / parts;
    }

    InMemConstructionIndex prepareInMemIndex() {
        InMemConstructionIndex memIndex = new InMemConstructionIndex(subEntries);
        memIndex.prepare();
        return memIndex;
    }

    @Override
    public int findID(double lat, double lon) {
        LocationIDResult res = findClosest(new GHPlace(lat, lon), EdgeFilter.ALL_EDGES);
        if (res == null)
            return -1;
        return res.closestNode();
    }

    @Override
    public boolean loadExisting() {
        if (initialized)
            throw new IllegalStateException("Call loadExisting only once");

        if (!dataAccess.loadExisting())
            return false;

        if (dataAccess.getHeader(0) != MAGIC_INT)
            throw new IllegalStateException("incorrect location2id index version, expected:" + MAGIC_INT);
        if (dataAccess.getHeader(1) != calcChecksum())
            throw new IllegalStateException("location2id index was opened with incorrect graph");
        minResolutionInMeter(dataAccess.getHeader(2));
        subEntries(dataAccess.getHeader(3));
        prepareAlgo();
        initialized = true;
        return true;
    }

    @Override
    public Location2IDIndex resolution(int minResolutionInMeter) {
        if (minResolutionInMeter <= 0)
            throw new IllegalStateException("Negative precision is not allowed!");

        minResolutionInMeter(minResolutionInMeter);
        return this;
    }

    @Override
    public Location2IDIndex precision(boolean approx) {
        if (approx)
            distCalc = new DistancePlaneProjection();
        else
            distCalc = new DistanceCalc();
        return this;
    }

    @Override
    public Location2NodesNtree create(long size) {
        throw new UnsupportedOperationException("Not supported. Use prepareIndex instead.");
    }

    @Override
    public void flush() {
        dataAccess.setHeader(0, MAGIC_INT);
        dataAccess.setHeader(1, calcChecksum());
        dataAccess.setHeader(2, minResolutionInMeter);
        dataAccess.setHeader(3, subEntries);
        // save a bit space not necessary dataAccess.trimTo((lastPointer + 1) * 4);
        dataAccess.flush();
    }

    @Override
    public Location2IDIndex prepareIndex() {
        if (initialized)
            throw new IllegalStateException("Call prepareIndex only once");

        StopWatch sw = new StopWatch().start();
        prepareAlgo();
        // in-memory preparation
        InMemConstructionIndex inMem = prepareInMemIndex();

        // compact & store to dataAccess
        dataAccess.create(64 * 1024);
        int lastPointer = inMem.store(inMem.root, START_POINTER);
        flush();
        float entriesPerLeaf = (float) inMem.size / inMem.leafs;
        initialized = true;
        logger.info("location index created in " + sw.stop().getSeconds()
                + "s, size:" + Helper.nf(inMem.size)
                + ", leafs:" + Helper.nf(inMem.leafs)
                + ", precision:" + minResolutionInMeter
                + ", maxDepth:" + maxDepth + ", subEntries:" + subEntries
                + ", entriesPerLeaf:" + entriesPerLeaf);

        return this;
    }

    int calcChecksum() {
        // do not include the edges as we could get problem with LevelGraph due to shortcuts
        // ^ graph.getAllEdges().count();
        return graph.nodes();
    }

    protected void sortNodes(TIntList nodes) {
    }

    @Override
    public void close() {
        dataAccess.close();
    }

    @Override
    public long capacity() {
        return dataAccess.capacity();
    }

    class InMemConstructionIndex {

        int size;
        int leafs;
        InMemTreeEntry root;

        public InMemConstructionIndex(int noOfSubEntries) {
            root = new InMemTreeEntry(noOfSubEntries);
        }

        void prepare() {
            final EdgeIterator allIter = getAllEdges();
            try {
                while (allIter.next()) {
                    int nodeA = allIter.baseNode();
                    int nodeB = allIter.adjNode();
                    double lat1 = graph.getLatitude(nodeA);
                    double lon1 = graph.getLongitude(nodeA);
                    double lat2;
                    double lon2;
                    PointList points = allIter.wayGeometry();
                    int len = points.size();
                    for (int i = 0; i < len; i++) {
                        lat2 = points.latitude(i);
                        lon2 = points.longitude(i);
                        addNode(nodeA, nodeB, lat1, lon1, lat2, lon2);
                        lat1 = lat2;
                        lon1 = lon2;
                    }
                    lat2 = graph.getLatitude(nodeB);
                    lon2 = graph.getLongitude(nodeB);
                    addNode(nodeA, nodeB, lat1, lon1, lat2, lon2);
                }
            } catch (Exception ex) {
//                logger.error("Problem!", ex);
                logger.error("Problem! base:" + allIter.baseNode() + ", adj:" + allIter.adjNode()
                        + ", edge:" + allIter.edge(), ex);
            }
        }

        void addNode(final int nodeA, final int nodeB,
                final double lat1, final double lon1,
                final double lat2, final double lon2) {
            PointEmitter pointEmitter = new PointEmitter() {
                @Override public void set(double lat, double lon) {
                    long key = keyAlgo.encode(lat, lon);
                    long keyPart = createReverseKey(key);
                    // no need to feed both nodes as we search neighbors in findIDs
                    addNode(root, pickBestNode(nodeA, nodeB), 0, keyPart, key);
                }
            };
            BresenhamLine.calcPoints(lat1, lon1, lat2, lon2, pointEmitter,
                    graph.bounds().minLat, graph.bounds().minLon,
                    deltaLat, deltaLon);
        }

        void addNode(InMemEntry entry, int nodeId, int depth, long keyPart, long key) {
            if (entry.isLeaf()) {
                InMemLeafEntry leafEntry = (InMemLeafEntry) entry;
                leafEntry.addNode(nodeId);
            } else {
                depth++;
                int index = (int) (bitmask & keyPart);
                keyPart = keyPart >>> shift;
                InMemTreeEntry treeEntry = ((InMemTreeEntry) entry);
                InMemEntry subentry = treeEntry.getSubEntry(index);
                if (subentry == null) {
                    if (depth == maxDepth)
                        subentry = new InMemLeafEntry(initLeafEntries, key);
                    else
                        subentry = new InMemTreeEntry(subEntries);
                    treeEntry.setSubEntry(index, subentry);
                }

                addNode(subentry, nodeId, depth, keyPart, key);
            }
        }

        Collection<InMemEntry> getLayer(int selectDepth) {
            List<InMemEntry> list = new ArrayList<InMemEntry>();
            fillLayer(list, selectDepth, 0, Collections.singleton((InMemEntry) root));
            return list;
        }

        void fillLayer(Collection<InMemEntry> list, int selectDepth, int depth, Collection<InMemEntry> entries) {
            for (InMemEntry entry : entries) {
                if (selectDepth == depth)
                    list.add(entry);
                else if (entry instanceof InMemTreeEntry)
                    fillLayer(list, selectDepth, depth + 1, ((InMemTreeEntry) entry).getSubEntriesForDebug());
            }
        }

        String print() {
            StringBuilder sb = new StringBuilder();
            print(root, sb, 0);
            return sb.toString();
        }

        void print(InMemEntry e, StringBuilder sb, long key) {
            if (e.isLeaf()) {
                InMemLeafEntry leaf = (InMemLeafEntry) e;
                int bits = keyAlgo.bits();
                // print reverse keys
                sb.append(BitUtil.toBitString(BitUtil.reverse(key, bits), bits)).append("  ");
                TIntArrayList entries = leaf.getResults();
                for (int i = 0; i < entries.size(); i++) {
                    sb.append(leaf.get(i)).append(',');
                }
                sb.append('\n');
            } else {
                InMemTreeEntry tree = (InMemTreeEntry) e;
                key = key << shift;
                for (int counter = 0; counter < tree.subEntries.length; counter++) {
                    InMemEntry sube = tree.subEntries[counter];
                    if (sube != null)
                        print(sube, sb, key | counter);
                }
            }
        }

        int store(InMemEntry entry, int pointer) {
            int refPointer = pointer;
            if (entry.isLeaf()) {
                InMemLeafEntry leaf = ((InMemLeafEntry) entry);
                TIntArrayList entries = leaf.getResults();
                int len = entries.size();
//                if (old > len)
//                    System.out.println("shrink:" + old + " to " + len);
                // special case for empty list
                if (len == 0)
                    return pointer;
                size += len;
                pointer++;
                leafs++;
                dataAccess.ensureCapacity((pointer + len + 1) * 4);
                for (int index = 0; index < len; index++) {
                    int integ = entries.get(index);
                    dataAccess.setInt(pointer++, integ);
                }
                dataAccess.setInt(refPointer, pointer);
            } else {
                InMemTreeEntry treeEntry = ((InMemTreeEntry) entry);
                int len = treeEntry.subEntries.length;
                pointer += len;
                for (int subCounter = 0; subCounter < len; subCounter++, refPointer++) {
                    InMemEntry subEntry = treeEntry.subEntries[subCounter];
                    if (subEntry == null)
                        continue;
                    dataAccess.ensureCapacity((pointer + 1) * 4);
                    int beforePointer = pointer;
                    pointer = store(subEntry, beforePointer);
                    if (pointer == beforePointer)
                        dataAccess.setInt(refPointer, 0);
                    else
                        dataAccess.setInt(refPointer, -beforePointer);
                }
            }
            return pointer;
        }
    }

    int getMaxDepth() {
        return maxDepth;
    }

    // fillIDs according to how they are stored
    void fillIDs(long keyPart, int pointer, TIntHashSet set) {
        int offset = (int) (bitmask & keyPart);
        int value = dataAccess.getInt(pointer + offset);
        if (value == 0) {
            // empty entry
        } else if (value > 0) {
            // leaf entry => value is maxPointer
            for (int leafIndex = pointer + 1; leafIndex < value; leafIndex++) {
                set.add(dataAccess.getInt(leafIndex));
            }
        } else if (value < 0) {
            // tree entry => negative value points to subentries
            fillIDs(keyPart >>> shift, -value, set);
        }
    }

    // this method returns the spatial key in reverse order for easier right-shifting
    final long createReverseKey(double lat, double lon) {
        return BitUtil.reverse(keyAlgo.encode(lat, lon), keyAlgo.bits());
    }

    final long createReverseKey(long key) {
        return BitUtil.reverse(key, keyAlgo.bits());
    }

    TIntHashSet findNetworkEntries(double queryLat, double queryLon) {
        TIntHashSet storedNetworkEntryIds = new TIntHashSet();
        if (regionSearch) {
            // search all rasters around minResolutionInMeter as we did not fill empty entries
            double maxLat = queryLat + deltaLat;
            double maxLon = queryLon + deltaLon;
            for (double tmpLat = queryLat - deltaLat; tmpLat <= maxLat; tmpLat += deltaLat) {
                for (double tmpLon = queryLon - deltaLon; tmpLon <= maxLon; tmpLon += deltaLon) {
                    long keyPart = createReverseKey(tmpLat, tmpLon);
                    // System.out.println(BitUtil.toBitString(key, keyAlgo.bits()));
                    fillIDs(keyPart, START_POINTER, storedNetworkEntryIds);
                }
            }
        } else {
            long keyPart = createReverseKey(queryLat, queryLon);
            fillIDs(keyPart, START_POINTER, storedNetworkEntryIds);
        }
        return storedNetworkEntryIds;
    }

    @Override
    public LocationIDResult findClosest(GHPlace point, final EdgeFilter edgeFilter) {
        final double queryLat = point.lat;
        final double queryLon = point.lon;
        final TIntHashSet storedNetworkEntryIds = findNetworkEntries(queryLat, queryLon);
        if (storedNetworkEntryIds.isEmpty())
            return null;

        final LocationIDResult closestNode = new LocationIDResult();
        // clone storedIds to avoid interference with forEach
        final GHBitSet checkBitset = new GHTBitSet(new TIntHashSet(storedNetworkEntryIds));
        // find nodes from the network entries which are close to 'point'
        storedNetworkEntryIds.forEach(new TIntProcedure() {
            @Override public boolean execute(final int networkEntryNodeId) {
                new XFirstSearch() {
                    boolean goFurther = true;
                    double currDist;
                    double currLat;
                    double currLon;
                    int currNode;

                    @Override protected GHBitSet createBitSet(int size) {
                        return checkBitset;
                    }

                    @Override protected EdgeIterator getEdges(Graph g, int current) {
                        return Location2NodesNtree.this.getEdges(current);
                    }

                    @Override protected boolean goFurther(int baseNode) {
                        currNode = baseNode;
                        currLat = graph.getLatitude(baseNode);
                        currLon = graph.getLongitude(baseNode);
                        currDist = distCalc.calcNormalizedDist(queryLat, queryLon, currLat, currLon);
                        return goFurther;
                    }

                    @Override
                    protected boolean checkAdjacent(EdgeIterator currEdge) {
                        // TODO
//                        if (!edgeFilter.accept(currEdge)) {
//                            // only limit the adjNode to a certain radius as currNode could be the wrong side of a valid edge
//                            goFurther = currDist < minResolutionInMeterNormed * 2;
//                            return true;
//                        }

                        goFurther = false;
                        int tmpNode = currNode;
                        double tmpLat = currLat;
                        double tmpLon = currLon;
                        int adjNode = currEdge.adjNode();
                        double adjLat = graph.getLatitude(adjNode);
                        double adjLon = graph.getLongitude(adjNode);

                        check(tmpNode, currDist, -adjNode - 2);

                        double tmpDist;
                        double adjDist = distCalc.calcNormalizedDist(adjLat, adjLon, queryLat, queryLon);
                        // if there are wayPoints this is only an approximation
                        if (edgeDistCalcOnSearch && adjDist < currDist)
                            tmpNode = adjNode;

                        PointList pointList = currEdge.wayGeometry();
                        int len = pointList.size();
                        for (int pointIndex = 0; pointIndex < len; pointIndex++) {
                            double wayLat = pointList.latitude(pointIndex);
                            double wayLon = pointList.longitude(pointIndex);
                            if (NumHelper.equalsEps(queryLat, wayLat, 1e-6)
                                    && NumHelper.equalsEps(queryLon, wayLon, 1e-6)) {
                                // equal point found
                                check(tmpNode, 0d, pointIndex);
                                break;
                            } else if (edgeDistCalcOnSearch
                                    && distCalc.validEdgeDistance(queryLat, queryLon,
                                    tmpLat, tmpLon, wayLat, wayLon)) {
                                tmpDist = distCalc.calcNormalizedEdgeDistance(queryLat, queryLon,
                                        tmpLat, tmpLon, wayLat, wayLon);
                                check(tmpNode, tmpDist, pointIndex);
                            }

                            tmpLat = wayLat;
                            tmpLon = wayLon;
                        }

                        if (edgeDistCalcOnSearch
                                && distCalc.validEdgeDistance(queryLat, queryLon,
                                tmpLat, tmpLon, adjLat, adjLon))
                            tmpDist = distCalc.calcNormalizedEdgeDistance(queryLat, queryLon,
                                    tmpLat, tmpLon, adjLat, adjLon);
                        else
                            tmpDist = adjDist;

                        check(tmpNode, tmpDist, -currNode - 2);
                        return closestNode.weight >= 0;
                    }

                    void check(int node, double dist, int wayIndex) {
                        if (dist < closestNode.weight) {
                            closestNode.weight = dist;
                            closestNode.closestNode(node);
                            closestNode.wayIndex = wayIndex;
                        }
                    }
                }.start(graph, networkEntryNodeId, false);
                return true;
            }
        });

        return closestNode;
    }

    protected int pickBestNode(int nodeA, int nodeB) {
        // For normal graph the node does not matter because if nodeA is conntected to nodeB
        // then nodeB is also connect to nodeA, but for a LevelGraph this does not apply.
        return nodeA;
    }

    protected EdgeIterator getEdges(int node) {
        return graph.getEdges(node);
    }

    protected AllEdgesIterator getAllEdges() {
        return graph.getAllEdges();
    }

    // make entries static as otherwise we get an additional reference to this class (memory waste)
    static interface InMemEntry {

        boolean isLeaf();
    }

    static class InMemLeafEntry extends SortedIntSet implements InMemEntry {

        private long key;

        public InMemLeafEntry(int count, long key) {
            super(count);
            this.key = key;
        }

        public boolean addNode(int nodeId) {
            return addOnce(nodeId);
        }

        @Override public final boolean isLeaf() {
            return true;
        }

        @Override public String toString() {
            return "LEAF " + key + " " + super.toString();
        }

        TIntArrayList getResults() {
            return this;
        }
    }

    // Space efficient sorted integer set. Suited for only a few entries.
    static class SortedIntSet extends TIntArrayList {

        public SortedIntSet() {
        }

        public SortedIntSet(int capacity) {
            super(capacity);
        }

        /**
         * Allow adding a value only once
         */
        public boolean addOnce(int value) {
            int foundIndex = binarySearch(value);
            if (foundIndex >= 0)
                return false;
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
            List<InMemEntry> list = new ArrayList<InMemEntry>();
            for (InMemEntry e : subEntries) {
                if (e != null)
                    list.add(e);
            }
            return list;
        }

        @Override public final boolean isLeaf() {
            return false;
        }

        @Override public String toString() {
            return "TREE";
        }
    }
}
