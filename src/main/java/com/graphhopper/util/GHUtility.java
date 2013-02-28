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
package com.graphhopper.util;

import com.graphhopper.coll.MyBitSet;
import com.graphhopper.coll.MyBitSetImpl;
import com.graphhopper.geohash.KeyAlgo;
import com.graphhopper.geohash.SpatialKeyAlgo;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.VehicleEncoder;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.storage.Location2IDPreciseIndex;
import com.graphhopper.storage.LevelGraphStorage;
import com.graphhopper.storage.MMapDirectory;
import com.graphhopper.storage.RAMDirectory;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A helper class to avoid cluttering the Graph interface with all the common
 * methods. Most of the methods are useful for unit tests or debugging only.
 *
 * @author Peter Karich,
 */
public class GHUtility {

    private static Logger logger = LoggerFactory.getLogger(GHUtility.class);
    private static final VehicleEncoder carEncoder = new CarFlagEncoder();
    private static final EdgeFilter edgesOutFilter = new DefaultEdgeFilter(carEncoder, false, true);
    private static final EdgeFilter edgesInFilter = new DefaultEdgeFilter(carEncoder, true, false);

    public static EdgeIterator getCarOutgoing(Graph g, int index) {
        return g.getEdges(index, edgesOutFilter);
    }

    public static EdgeIterator getCarIncoming(Graph g, int index) {
        return g.getEdges(index, edgesInFilter);
    }

    /**
     * @throws could throw exception if uncatched problems like index out of
     * bounds etc
     */
    public static List<String> getProblems(Graph g) {
        List<String> problems = new ArrayList<String>();
        int nodes = g.nodes();
        int nodeIndex = 0;
        try {
            for (; nodeIndex < nodes; nodeIndex++) {
                double lat = g.getLatitude(nodeIndex);
                if (lat > 90 || lat < -90)
                    problems.add("latitude is not within its bounds " + lat);
                double lon = g.getLongitude(nodeIndex);
                if (lon > 180 || lon < -180)
                    problems.add("longitude is not within its bounds " + lon);

                EdgeIterator iter = g.getEdges(nodeIndex);
                while (iter.next()) {
                    if (iter.node() >= nodes)
                        problems.add("edge of " + nodeIndex + " has a node " + iter.node() + " greater or equal to getNodes");
                    if (iter.node() < 0)
                        problems.add("edge of " + nodeIndex + " has a negative node " + iter.node());
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException("problem with node " + nodeIndex, ex);
        }

//        for (int i = 0; i < nodes; i++) {
//            new XFirstSearch().start(g, i, false);
//        }

        return problems;
    }

    public static int count(EdgeIterator iter) {
        int counter = 0;
        while (iter.next()) {
            counter++;
        }
        return counter;
    }

    public static List<Integer> neighbors(EdgeIterator iter) {
        List<Integer> list = new ArrayList<Integer>();
        while (iter.next()) {
            list.add(iter.node());
        }
        return list;
    }

    public static int count(RawEdgeIterator iter) {
        int counter = 0;
        while (iter.next()) {
            ++counter;
        }
        return counter;
    }

    public static void printInfo(final Graph g, int startNode, final int counts, final EdgeFilter filter) {
        new XFirstSearch() {
            int counter = 0;

            @Override protected boolean goFurther(int nodeId) {
                System.out.println(getNodeInfo(g, nodeId, filter));
                if (counter++ > counts)
                    return false;
                return true;
            }
        }.start(g, startNode, false);
    }

    public static String getNodeInfo(LevelGraph g, int nodeId, EdgeFilter filter) {
        EdgeSkipIterator iter = g.getEdges(nodeId, filter);
        String str = nodeId + ":" + g.getLatitude(nodeId) + "," + g.getLongitude(nodeId) + "\n";
        while (iter.next()) {
            str += "  ->" + iter.node() + "(" + iter.skippedEdge1() + "," + iter.skippedEdge2() + ") "
                    + iter.edge() + " \t" + BitUtil.toBitString(iter.flags(), 8) + "\n";
        }
        return str;
    }

    public static String getNodeInfo(Graph g, int nodeId, EdgeFilter filter) {
        EdgeIterator iter = g.getEdges(nodeId, filter);
        String str = nodeId + ":" + g.getLatitude(nodeId) + "," + g.getLongitude(nodeId) + "\n";
        while (iter.next()) {
            str += "  ->" + iter.node() + " (" + iter.distance() + ") pillars:"
                    + iter.wayGeometry().size() + ", edgeId:" + iter.edge()
                    + "\t" + BitUtil.toBitString(iter.flags(), 8) + "\n";
        }
        return str;
    }

    public static Graph shuffle(Graph g, Graph sortedGraph) {
        int len = g.nodes();
        TIntList list = new TIntArrayList(len, -1);
        list.fill(0, len, -1);
        for (int i = 0; i < len; i++) {
            list.set(i, i);
        }
        list.shuffle(new Random());
        return createSortedGraph(g, sortedGraph, list);
    }

    /**
     * Sorts the graph according to depth-first search traversal. Other
     * traversals have either no significant difference (bfs) for querying or
     * are worse (see sort).
     */
    public static Graph sortDFS(Graph g, Graph sortedGraph) {
        final TIntList list = new TIntArrayList(g.nodes(), -1);
        list.fill(0, g.nodes(), -1);
        new XFirstSearch() {
            int counter = 0;

            @Override
            protected boolean goFurther(int nodeId) {
                list.set(nodeId, counter);
                counter++;
                return super.goFurther(nodeId);
            }
        }.start(g, 0, false);
        return createSortedGraph(g, sortedGraph, list);
    }

    /**
     * Sorts the graph according to the z-curve. Better use sortDFS as a lot
     * memory is necessary.
     */
    public static Graph sort(Graph g, Graph sortedGraph, int capacity) {
        // make sure it is a square rootable number -> necessary for spatialkeyalgo
//        capacity = (int) Math.sqrt(capacity);
//        capacity *= capacity;

        int bits = (int) (Math.log(capacity) / Math.log(2));
        final KeyAlgo algo = new SpatialKeyAlgo(bits);
        Location2IDPreciseIndex index = new Location2IDPreciseIndex(g, new RAMDirectory()) {
            @Override protected KeyAlgo createKeyAlgo(int latS, int lonS) {
                return algo;
            }
        };
        index.setCalcEdgeDistance(false);
        Location2IDPreciseIndex.InMemConstructionIndex idx = index.prepareInMemoryIndex(capacity);
        final TIntList nodeMappingList = new TIntArrayList(g.nodes(), -1);
        nodeMappingList.fill(0, g.nodes(), -1);
        int counter = 0;
        int tmp = 0;
        for (int ti = 0; ti < idx.length(); ti++) {
            TIntArrayList list = idx.getNodes(ti);
            if (list == null)
                continue;
            tmp++;
            int s = list.size();
            for (int ii = 0; ii < s; ii++) {
                nodeMappingList.set(list.get(ii), counter);
                counter++;
            }
        }

        return createSortedGraph(g, sortedGraph, nodeMappingList);
    }

    static Graph createSortedGraph(Graph g, Graph sortedGraph, final TIntList oldToNewNodeList) {
        int len = oldToNewNodeList.size();
        // important to avoid creating two edges for edges with both directions
        MyBitSet bitset = new MyBitSetImpl(len);
        for (int old = 0; old < len; old++) {
            int newIndex = oldToNewNodeList.get(old);
            // ignore empty entries
            if (newIndex < 0)
                continue;
            bitset.add(newIndex);
            sortedGraph.setNode(newIndex, g.getLatitude(old), g.getLongitude(old));
            EdgeIterator eIter = g.getEdges(old);
            while (eIter.next()) {
                int newNodeIndex = oldToNewNodeList.get(eIter.node());
                if (newNodeIndex < 0)
                    throw new IllegalStateException("empty entries should be connected to the others");
                if (bitset.contains(newNodeIndex))
                    continue;
                sortedGraph.edge(newIndex, newNodeIndex, eIter.distance(), eIter.flags()).
                        wayGeometry(eIter.wayGeometry());
            }
        }
        return sortedGraph;
    }

    static Directory guessDirectory(GraphStorage store) {
        String location = store.directory().location();
        Directory outdir;
        if (store.directory() instanceof MMapDirectory) {
            // TODO mmap will overwrite existing storage at the same location!                
            throw new IllegalStateException("not supported yet");
            // outdir = new MMapDirectory(location);                
        } else {
            boolean isStoring = ((RAMDirectory) store.directory()).isStoring();
            outdir = new RAMDirectory(location, isStoring);
        }
        return outdir;
    }

    static GraphStorage guessStorage(Graph g, Directory outdir) {
        GraphStorage store;
        if (g instanceof LevelGraphStorage)
            store = new LevelGraphStorage(outdir);
        else
            store = new GraphStorage(outdir);
        return store;
    }

    /**
     * Create a new storage from the specified one without copying the data.
     */
    public static GraphStorage newStorage(GraphStorage store) {
        return guessStorage(store, guessDirectory(store)).createNew(store.nodes());
    }

    /**
     * Create a new in-memory storage from the specified one with copying the
     * data.
     *
     * @return the new storage
     */
    public static Graph clone(Graph graph) {
        return clone(graph, guessStorage(graph, new RAMDirectory()));
    }

    /**
     * @return the graph outGraph
     */
    public static Graph clone(Graph g, GraphStorage outGraph) {
        return g.copyTo(outGraph.createNew(g.nodes()));
    }

    /**
     * @return the graph 'to'
     */
    // TODO very similar to createSortedGraph -> use a 'int map(int)' interface
    public static Graph copyTo(Graph from, Graph to) {
        int len = from.nodes();
        // important to avoid creating two edges for edges with both directions        
        MyBitSet bitset = new MyBitSetImpl(len);
        for (int oldNode = 0; oldNode < len; oldNode++) {
            bitset.add(oldNode);
            to.setNode(oldNode, from.getLatitude(oldNode), from.getLongitude(oldNode));
            EdgeIterator eIter = from.getEdges(oldNode);
            while (eIter.next()) {
                int adjacentNodeIndex = eIter.node();
                if (bitset.contains(adjacentNodeIndex))
                    continue;
                to.edge(oldNode, adjacentNodeIndex, eIter.distance(), eIter.flags()).wayGeometry(eIter.wayGeometry());
            }
        }
        return to;
    }

    public static int getToNode(Graph g, int edge, int endNode) {
        if (EdgeIterator.Edge.isValid(edge)) {
            EdgeIterator iterTo = g.getEdgeProps(edge, endNode);
            return iterTo.node();
        }
        return endNode;
    }
    public static final EdgeSkipIterator EMPTY = new EdgeSkipIterator() {
        @Override public boolean isShortcut() {
            return false;
        }

        @Override public int skippedEdge1() {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override public int skippedEdge2() {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override public void skippedEdges(int edge1, int edge2) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override public void distance(double dist) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override public void flags(int flags) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override public boolean next() {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override public int edge() {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override public int baseNode() {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override public int node() {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override public double distance() {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override public int flags() {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override public PointList wayGeometry() {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override public void wayGeometry(PointList list) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override public boolean isEmpty() {
            return true;
        }
    };
}