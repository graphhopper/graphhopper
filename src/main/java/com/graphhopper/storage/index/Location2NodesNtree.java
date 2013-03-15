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

import com.graphhopper.coll.MyBitSet;
import com.graphhopper.coll.MyTBitSet;
import com.graphhopper.geohash.SpatialKeyAlgo;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.routing.util.AllEdgesIterator;
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
import gnu.trove.TIntCollection;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.procedure.TIntProcedure;
import gnu.trove.set.hash.TIntHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This implementation implements an n-tree to get node ids from GPS location.
 * This will replace Location2IDQuadtree and Location2IDPreciseIndex.
 *
 * All leafs are at the same depth, otherwise it is quite complicated to
 * calculate the bresenham line for different resolutions, especially if a leaf
 * node could be split into a tree-node and resolution changes.
 *
 * @author Peter Karich
 */
public class Location2NodesNtree implements Location2NodesIndex, Location2IDIndex {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final static int MAGIC_INT = Integer.MAX_VALUE / 22316;
    static final EdgeFilter ALL_EDGES = new EdgeFilter() {
        @Override public boolean accept(EdgeIterator iter) {
            return true;
        }
    };
    private DistanceCalc distCalc = new DistancePlaneProjection();
    DataAccess dataAccess;
    private Graph graph;
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
    private SpatialKeyAlgo keyAlgo;
    private int minResolutionInMeter;
    private double deltaLat;
    private double deltaLon;
    private int initLeafEntries = 4;
    private boolean initialized = false;

    public Location2NodesNtree(Graph g, Directory dir) {
        this.graph = g;
        dataAccess = dir.findCreate("spatialNIndex");
        subEntries = 4;
        minResolutionInMeter = 500;
    }

    /**
     * subEntries is n and defines the branches (== tiles) per depth
     * <pre>
     * 2 * 2 tiles => n=2^2, if n=4 then this is a quadtree
     * 4 * 4 tiles => n=4^2
     * </pre>
     */
    public Location2NodesNtree subEntries(int subEntries) {
        this.subEntries = subEntries;
        return this;
    }

    public Location2NodesNtree minResolutionInMeter(int minResolutionInMeter) {
        this.minResolutionInMeter = minResolutionInMeter;
        return this;
    }

    void prepareAlgo() {
        shift = (int) Math.round(Math.sqrt(subEntries));
        bitmask = (1 << shift) - 1;

        // now calculate the necessary maxDepth d for our current bounds
        // if we assume a minimum resolution like 0.5km for a leaf-tile                
        // n^(depth/2) = toMeter(dLon) / minResolution
        BBox bounds = graph.bounds();
        double lat = Math.min(Math.abs(bounds.maxLat), Math.abs(bounds.minLat));
        double maxDistInMeter = Math.max(
                (bounds.maxLat - bounds.minLat) / 360 * DistanceCalc.C,
                (bounds.maxLon - bounds.minLon) / 360 * distCalc.calcCircumference(lat));
        int tmpDepth = (int) (2 * Math.log(maxDistInMeter / minResolutionInMeter) / Math.log(subEntries));
        maxDepth = Math.min(64 / shift, Math.max(0, tmpDepth) + 1);
        keyAlgo = new SpatialKeyAlgo(maxDepth * shift).bounds(bounds);
        long parts = Math.round(Math.pow(shift, maxDepth));
        deltaLat = (graph.bounds().maxLat - graph.bounds().minLat) / parts;
        deltaLon = (graph.bounds().maxLon - graph.bounds().minLon) / parts;
    }

    InMemConstructionIndex prepareIndex() {
        InMemConstructionIndex memIndex = new InMemConstructionIndex(subEntries);
        memIndex.prepare();
        return memIndex;
    }

    @Override
    public int findID(double lat, double lon) {
        TIntCollection list = findIDs(new GHPlace(lat, lon), ALL_EDGES);
        if (list.isEmpty())
            return -1;
        return list.iterator().next();
    }

    @Override
    public boolean loadExisting() {
        if (initialized)
            throw new IllegalStateException("Call loadExisting only once");

        if (!dataAccess.loadExisting())
            return false;

        if (dataAccess.getHeader(0) != MAGIC_INT)
            throw new IllegalStateException("incorrect location2id index version");
        if (dataAccess.getHeader(1) != calcChecksum())
            throw new IllegalStateException("location2id index was opened with incorrect graph");
        minResolutionInMeter(dataAccess.getHeader(2));
        subEntries(dataAccess.getHeader(3));
        prepareAlgo();
        initialized = true;
        return true;
    }

    @Override
    public Location2IDIndex prepareIndex(int capacity) {
        if (initialized)
            throw new IllegalStateException("Call prepareIndex only once");

        if (capacity <= 0)
            throw new IllegalStateException("Negative precision is not allowed!");

        StopWatch sw = new StopWatch().start();
        minResolutionInMeter(capacity);
        prepareAlgo();
        // in-memory preparation
        InMemConstructionIndex inMem = prepareIndex();

        // compact & store to dataAccess
        dataAccess.createNew(64 * 1024);
        inMem.store(inMem.root, 0);
        dataAccess.setHeader(0, MAGIC_INT);
        dataAccess.setHeader(1, calcChecksum());
        dataAccess.setHeader(2, minResolutionInMeter);
        dataAccess.setHeader(3, subEntries);
        dataAccess.flush();

        initialized = true;
        logger.info("location index created in " + sw.stop().getSeconds()
                + ", precision:" + minResolutionInMeter
                + ", maxDepth:" + maxDepth + ", subEntries:" + subEntries);

        return this;
    }

    int calcChecksum() {
        // do not include the edges as we could get problem with LevelGraph due to shortcuts
        // ^ graph.getAllEdges().count();
        return graph.nodes();
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
    public float calcMemInMB() {
        return (float) dataAccess.capacity() / Helper.MB;


    }

    class InMemConstructionIndex {

        InMemTreeEntry root;

        public InMemConstructionIndex(int noOfSubEntries) {
            root = new InMemTreeEntry(noOfSubEntries);
        }

        void prepare() {
            final AllEdgesIterator allIter = graph.getAllEdges();
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
        }

        void addNode(final int nodeA, final int nodeB, double lat1, double lon1, double lat2, double lon2) {
            // TODO inline bresenham?
            PointEmitter pointEmitter = new PointEmitter() {
                @Override public void set(double lat, double lon) {
                    long key = createReverseKey(lat, lon);
                    // TODO measure distance to avoid pair adding
                    // if(lat,lon more close to lat1,lon1)
                    if (NumHelper.equalsEps(lat, -1, 0.1) && NumHelper.equalsEps(lon, -1, 0.1))
                        root = root;
                    addNode(root, nodeA, 0, key);
                    addNode(root, nodeB, 0, key);
                }
            };
            BresenhamLine.calcPoints(lat1, lon1, lat2, lon2, pointEmitter, deltaLat, deltaLon);
        }

        void addNode(InMemEntry entry, int nodeId, int depth, long key) {
            if (entry.isLeaf())
                ((InMemLeafEntry) entry).addNode(nodeId, graph);
            else {
                depth++;
                int index = (int) (bitmask & key);
                key = key >>> shift;
                InMemTreeEntry treeEntry = ((InMemTreeEntry) entry);
                InMemEntry subentry = treeEntry.getSubEntry(index);
                if (subentry == null) {
                    if (depth == maxDepth)
                        subentry = new InMemLeafEntry(initLeafEntries);
                    else
                        subentry = new InMemTreeEntry(subEntries);
                    treeEntry.setSubEntry(index, subentry);
                }

                addNode(subentry, nodeId, depth, key);
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

        int store(InMemEntry entry, int pointer) {
            int refPointer = pointer;
            if (entry.isLeaf()) {
                InMemLeafEntry leaf = ((InMemLeafEntry) entry);
                leaf.doCompress(graph);
                pointer++;
                int len = leaf.size;
                dataAccess.ensureCapacity((pointer + len + 1) * 4);
                for (int index = 0; index < len; index++) {
                    int integ = leaf.subEntries[index];
                    dataAccess.setInt(pointer++, integ);
                }
                dataAccess.setInt(refPointer, pointer);
            } else {
                InMemTreeEntry treeEntry = ((InMemTreeEntry) entry);
                pointer += treeEntry.subEntries.length;
                int counter = 0;
                for (InMemEntry subEntry : treeEntry.subEntries) {
                    dataAccess.ensureCapacity((pointer + 1) * 4);
                    dataAccess.setInt(refPointer++, -pointer);
                    counter++;
                    if (subEntry == null)
                        continue;
                    pointer = store(subEntry, pointer);
                }
            }
            return pointer;
        }
    }

    int getMaxDepth() {
        return maxDepth;
    }

    // fillIDs according to how they are stored
    void fillIDs(long key, int pointer, int depth, TIntHashSet set) {
        int offset = (int) (bitmask & key);
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
            fillIDs(key >>> shift, -value, depth + 1, set);
        }
    }

    // this method returns the spatial key in reverse order for easier right-shifting
    // TODO PERFORMANCE for index creation is called very often
    final long createReverseKey(double lat, double lon) {
        return BitUtil.reverse(keyAlgo.encode(lat, lon), keyAlgo.bits());
    }

    @Override
    public TIntList findIDs(GHPlace point, final EdgeFilter edgeFilter) {
        final double queryLat = point.lat;
        final double queryLon = point.lon;
        final TIntHashSet storedNetworkEntryIds = new TIntHashSet();
        boolean simple = false;
        if (simple) {
            long key = createReverseKey(queryLat, queryLon);
            fillIDs(key, 0, 0, storedNetworkEntryIds);
        } else {
            // search all rasters around minResolutionInMeter as we did not fill empty entries
            double maxLat = queryLat + deltaLat;
            double maxLon = queryLon + deltaLon;
            for (double tmpLat = queryLat - deltaLat; tmpLat <= maxLat; tmpLat += deltaLat) {
                for (double tmpLon = queryLon - deltaLon; tmpLon <= maxLon; tmpLon += deltaLon) {
                    long key = createReverseKey(tmpLat, tmpLon);
                    fillIDs(key, 0, 0, storedNetworkEntryIds);
                }
            }
        }

        if (storedNetworkEntryIds.isEmpty())
            return new TIntArrayList(0);

        int mainId = storedNetworkEntryIds.iterator().next();
        double mainLat = graph.getLatitude(mainId);
        double mainLon = graph.getLongitude(mainId);
        final WeightedNode closestNode = new WeightedNode(mainId,
                distCalc.calcNormalizedDist(mainLat, mainLon, queryLat, queryLon));

        // clone storedIds to avoid interference with forEach
        final MyBitSet checkBitset = new MyTBitSet(new TIntHashSet(storedNetworkEntryIds));

        // find nodes from the network entries which are close to 'point'
        storedNetworkEntryIds.forEach(new TIntProcedure() {
            @Override
            public boolean execute(final int networkEntryNodeId) {
                new XFirstSearch() {
                    boolean goFurther = true;
                    double currDist;
                    double currLat;
                    double currLon;
                    int currNode;

                    @Override protected MyBitSet createBitSet(int size) {
                        return checkBitset;
                    }

                    @Override protected boolean goFurther(int baseNode) {
//                        if (baseNode == closestNode.node)
//                            return true;
                        currNode = baseNode;
                        currLat = graph.getLatitude(baseNode);
                        currLon = graph.getLongitude(baseNode);
                        currDist = distCalc.calcNormalizedDist(queryLat, queryLon, currLat, currLon);
                        // TODO do not use this as we cannot check edgeFilter
                        if (currDist < closestNode.weight) {
                            closestNode.weight = currDist;
                            closestNode.node = baseNode;
                        }

                        return goFurther; // && currDist < minResolutionInMeter * 2;
                    }

                    @Override
                    protected boolean checkConnected(EdgeIterator currEdge) {
//                        if (!edgeFilter.accept(currEdge)) {
//                            goFurther = true;
//                            return true;
//                        }

                        goFurther = false;
                        int connNode = currEdge.adjNode();
                        double connLat = graph.getLatitude(connNode);
                        double connLon = graph.getLongitude(connNode);

                        // check distance of queryLat, queryLon to connNode and to the currEdge                        
                        double connectDist = distCalc.calcNormalizedDist(connLat, connLon, queryLat, queryLon);
                        double dist = connectDist;
                        int tmpNode = connNode;
                        if (distCalc.validEdgeDistance(queryLat, queryLon,
                                currLat, currLon, connLat, connLon)) {
                            dist = distCalc.calcNormalizedEdgeDistance(queryLat, queryLon,
                                    currLat, currLon, connLat, connLon);
                            // if we use distance to edge it could be that we prefer the current node
                            if (currDist < connectDist)
                                tmpNode = currNode;
                        }

                        if (dist < closestNode.weight) {
                            closestNode.weight = dist;
                            closestNode.node = tmpNode;
                        }
                        return true;
                    }
                }.start(graph, networkEntryNodeId, false);
                return true;
            }
        });
        // TODO return start AND end node of the shortest edge       
        final TIntList result = new TIntArrayList();
        result.add(closestNode.node);
        return result;


    }

    // make entries static as otherwise we get an additional reference to this class (memory waste)
    static interface InMemEntry {

        boolean isLeaf();
    }

    static class InMemLeafEntry implements InMemEntry {

        int subEntries[];
        // TODO MEMORY avoid this variable via a negative marker in the array
        int size = 0;

        public InMemLeafEntry(int count) {
            subEntries = new int[count];
        }

        public void addNode(int nodeId, Graph graph) {
            // TODO PERFORMANCE sort subEntries or insert sorted to make this contains check faster?
            for (int i = 0; i < size; i++) {
                if (subEntries[i] == nodeId)
                    return;
            }
            if (size >= subEntries.length) {
                doCompress(graph);
                if (size >= subEntries.length)
                    subEntries = Arrays.copyOf(subEntries, (int) (1.6 * subEntries.length));
            }

            subEntries[size] = nodeId;
            size++;
        }

        void doCompress(Graph graph) {
            // TODO NOW remove node from the same network
        }

        @Override public final boolean isLeaf() {
            return true;
        }

        @Override public String toString() {
            return "LEAF " + Arrays.toString(subEntries);
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
