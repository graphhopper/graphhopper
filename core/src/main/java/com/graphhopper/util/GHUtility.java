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
package com.graphhopper.util;

import com.carrotsearch.hppc.IntIndexedContainer;
import com.carrotsearch.hppc.LongArrayList;
import com.carrotsearch.hppc.LongIndexedContainer;
import com.graphhopper.coll.GHBitSet;
import com.graphhopper.coll.GHBitSetImpl;
import com.graphhopper.coll.GHIntArrayList;
import com.graphhopper.routing.util.AllCHEdgesIterator;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A helper class to avoid cluttering the Graph interface with all the common methods. Most of the
 * methods are useful for unit tests or debugging only.
 * <p>
 *
 * @author Peter Karich
 */
public class GHUtility {
    /**
     * This method could throw exception if uncatched problems like index out of bounds etc
     */
    public static List<String> getProblems(Graph g) {
        List<String> problems = new ArrayList<String>();
        int nodes = g.getNodes();
        int nodeIndex = 0;
        NodeAccess na = g.getNodeAccess();
        try {
            EdgeExplorer explorer = g.createEdgeExplorer();
            for (; nodeIndex < nodes; nodeIndex++) {
                double lat = na.getLatitude(nodeIndex);
                if (lat > 90 || lat < -90)
                    problems.add("latitude is not within its bounds " + lat);

                double lon = na.getLongitude(nodeIndex);
                if (lon > 180 || lon < -180)
                    problems.add("longitude is not within its bounds " + lon);

                EdgeIterator iter = explorer.setBaseNode(nodeIndex);
                while (iter.next()) {
                    if (iter.getAdjNode() >= nodes) {
                        problems.add("edge of " + nodeIndex + " has a node " + iter.getAdjNode() + " greater or equal to getNodes");
                    }
                    if (iter.getAdjNode() < 0) {
                        problems.add("edge of " + nodeIndex + " has a negative node " + iter.getAdjNode());
                    }
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException("problem with node " + nodeIndex, ex);
        }

//        for (int i = 0; i < nodes; i++) {
//            new BreadthFirstSearch().start(g, i);
//        }
        return problems;
    }

    /**
     * Counts reachable edges.
     */
    public static int count(EdgeIterator iter) {
        int counter = 0;
        while (iter.next()) {
            counter++;
        }
        return counter;
    }

    public static Set<Integer> asSet(int... values) {
        Set<Integer> s = new HashSet<Integer>();
        for (int v : values) {
            s.add(v);
        }
        return s;
    }

    public static Set<Integer> getNeighbors(EdgeIterator iter) {
        // make iteration order over set static => linked
        Set<Integer> list = new LinkedHashSet<Integer>();
        while (iter.next()) {
            list.add(iter.getAdjNode());
        }
        return list;
    }

    public static List<Integer> getEdgeIds(EdgeIterator iter) {
        List<Integer> list = new ArrayList<Integer>();
        while (iter.next()) {
            list.add(iter.getEdge());
        }
        return list;
    }

    public static void printEdgeInfo(final Graph g, FlagEncoder encoder) {
        System.out.println("-- Graph n:" + g.getNodes() + " e:" + g.getAllEdges().getMaxId() + " ---");
        AllEdgesIterator iter = g.getAllEdges();
        while (iter.next()) {
            String sc = "";
            if (iter instanceof AllCHEdgesIterator) {
                AllCHEdgesIterator aeSkip = (AllCHEdgesIterator) iter;
                sc = aeSkip.isShortcut() ? "sc" : "  ";
            }
            String fwdStr = iter.isForward(encoder) ? "fwd" : "   ";
            String bckStr = iter.isBackward(encoder) ? "bckwd" : "";
            System.out.println(sc + " " + iter + " " + fwdStr + " " + bckStr);
        }
    }

    public static void printInfo(final Graph g, int startNode, final int counts, final EdgeFilter filter) {
        new BreadthFirstSearch() {
            int counter = 0;

            @Override
            protected boolean goFurther(int nodeId) {
                System.out.println(getNodeInfo(g, nodeId, filter));
                if (counter++ > counts) {
                    return false;
                }
                return true;
            }
        }.start(g.createEdgeExplorer(), startNode);
    }

    public static String getNodeInfo(CHGraph g, int nodeId, EdgeFilter filter) {
        CHEdgeExplorer ex = g.createEdgeExplorer(filter);
        CHEdgeIterator iter = ex.setBaseNode(nodeId);
        NodeAccess na = g.getNodeAccess();
        String str = nodeId + ":" + na.getLatitude(nodeId) + "," + na.getLongitude(nodeId) + "\n";
        while (iter.next()) {
            str += "  ->" + iter.getAdjNode() + "(" + iter.getSkippedEdge1() + "," + iter.getSkippedEdge2() + ") "
                    + iter.getEdge() + " \t" + BitUtil.BIG.toBitString(iter.getFlags(), 8) + "\n";
        }
        return str;
    }

    public static String getNodeInfo(Graph g, int nodeId, EdgeFilter filter) {
        EdgeIterator iter = g.createEdgeExplorer(filter).setBaseNode(nodeId);
        NodeAccess na = g.getNodeAccess();
        String str = nodeId + ":" + na.getLatitude(nodeId) + "," + na.getLongitude(nodeId) + "\n";
        while (iter.next()) {
            str += "  ->" + iter.getAdjNode() + " (" + iter.getDistance() + ") pillars:"
                    + iter.fetchWayGeometry(0).getSize() + ", edgeId:" + iter.getEdge()
                    + "\t" + BitUtil.BIG.toBitString(iter.getFlags(), 8) + "\n";
        }
        return str;
    }

    public static Graph shuffle(Graph g, Graph sortedGraph) {
        int nodes = g.getNodes();
        GHIntArrayList list = new GHIntArrayList(nodes);
        list.fill(nodes, -1);
        for (int i = 0; i < nodes; i++) {
            list.set(i, i);
        }
        list.shuffle(new Random());
        return createSortedGraph(g, sortedGraph, list);
    }

    /**
     * Sorts the graph according to depth-first search traversal. Other traversals have either no
     * significant difference (bfs) for querying or are worse (z-curve).
     */
    public static Graph sortDFS(Graph g, Graph sortedGraph) {
        int nodes = g.getNodes();
        final GHIntArrayList list = new GHIntArrayList(nodes);
        list.fill(nodes, -1);
        final GHBitSetImpl bitset = new GHBitSetImpl(nodes);
        final AtomicInteger ref = new AtomicInteger(-1);
        EdgeExplorer explorer = g.createEdgeExplorer();
        for (int startNode = 0; startNode >= 0 && startNode < nodes;
             startNode = bitset.nextClear(startNode + 1)) {
            new DepthFirstSearch() {
                @Override
                protected GHBitSet createBitSet() {
                    return bitset;
                }

                @Override
                protected boolean goFurther(int nodeId) {
                    list.set(nodeId, ref.incrementAndGet());
                    return super.goFurther(nodeId);
                }
            }.start(explorer, startNode);
        }
        return createSortedGraph(g, sortedGraph, list);
    }

    static Graph createSortedGraph(Graph fromGraph, Graph toSortedGraph, final IntIndexedContainer oldToNewNodeList) {
        AllEdgesIterator eIter = fromGraph.getAllEdges();
        while (eIter.next()) {
            int base = eIter.getBaseNode();
            int newBaseIndex = oldToNewNodeList.get(base);
            int adj = eIter.getAdjNode();
            int newAdjIndex = oldToNewNodeList.get(adj);

            // ignore empty entries
            if (newBaseIndex < 0 || newAdjIndex < 0)
                continue;

            eIter.copyPropertiesTo(toSortedGraph.edge(newBaseIndex, newAdjIndex));
        }

        int nodes = fromGraph.getNodes();
        NodeAccess na = fromGraph.getNodeAccess();
        NodeAccess sna = toSortedGraph.getNodeAccess();
        for (int old = 0; old < nodes; old++) {
            int newIndex = oldToNewNodeList.get(old);
            if (sna.is3D())
                sna.setNode(newIndex, na.getLatitude(old), na.getLongitude(old), na.getElevation(old));
            else
                sna.setNode(newIndex, na.getLatitude(old), na.getLongitude(old));
        }
        return toSortedGraph;
    }

    /**
     * @return the specified toGraph which is now filled with data from fromGraph
     */
    // TODO very similar to createSortedGraph -> use a 'int map(int)' interface
    public static Graph copyTo(Graph fromGraph, Graph toGraph) {
        AllEdgesIterator eIter = fromGraph.getAllEdges();
        while (eIter.next()) {
            int base = eIter.getBaseNode();
            int adj = eIter.getAdjNode();
            eIter.copyPropertiesTo(toGraph.edge(base, adj));
        }

        NodeAccess fna = fromGraph.getNodeAccess();
        NodeAccess tna = toGraph.getNodeAccess();
        int nodes = fromGraph.getNodes();
        for (int node = 0; node < nodes; node++) {
            if (tna.is3D())
                tna.setNode(node, fna.getLatitude(node), fna.getLongitude(node), fna.getElevation(node));
            else
                tna.setNode(node, fna.getLatitude(node), fna.getLongitude(node));
        }
        return toGraph;
    }

    static Directory guessDirectory(GraphStorage store) {
        String location = store.getDirectory().getLocation();
        Directory outdir;
        if (store.getDirectory() instanceof MMapDirectory) {
            throw new IllegalStateException("not supported yet: mmap will overwrite existing storage at the same location");
        } else {
            boolean isStoring = ((GHDirectory) store.getDirectory()).isStoring();
            outdir = new RAMDirectory(location, isStoring);
        }
        return outdir;
    }

    /**
     * Create a new storage from the specified one without copying the data.
     */
    public static GraphHopperStorage newStorage(GraphHopperStorage store) {
        Directory outdir = guessDirectory(store);
        boolean is3D = store.getNodeAccess().is3D();

        return new GraphHopperStorage(store.getCHWeightings(), outdir, store.getEncodingManager(),
                is3D, store.getExtension()).
                create(store.getNodes());
    }

    public static int getAdjNode(Graph g, int edge, int adjNode) {
        if (EdgeIterator.Edge.isValid(edge)) {
            EdgeIteratorState iterTo = g.getEdgeIteratorState(edge, adjNode);
            return iterTo.getAdjNode();
        }
        return adjNode;
    }

    public static EdgeIteratorState createMockedEdgeIteratorState(final double distance, final long flags) {
        return new GHUtility.DisabledEdgeIterator() {
            @Override
            public double getDistance() {
                return distance;
            }

            @Override
            public long getFlags() {
                return flags;
            }

            @Override
            public boolean getBool(int key, boolean _default) {
                return _default;
            }
        };
    }

    ;

    /**
     * @return the <b>first</b> edge containing the specified nodes base and adj. Returns null if
     * not found.
     */
    public static EdgeIteratorState getEdge(Graph graph, int base, int adj) {
        EdgeIterator iter = graph.createEdgeExplorer().setBaseNode(base);
        while (iter.next()) {
            if (iter.getAdjNode() == adj)
                return iter;
        }
        return null;
    }

    /**
     * Creates unique positive number for specified edgeId taking into account the direction defined
     * by nodeA, nodeB and reverse.
     */
    public static int createEdgeKey(int nodeA, int nodeB, int edgeId, boolean reverse) {
        edgeId = edgeId << 1;
        if (reverse)
            return (nodeA > nodeB) ? edgeId : edgeId + 1;
        return (nodeA > nodeB) ? edgeId + 1 : edgeId;
    }

    /**
     * Returns if the specified edgeKeys (created by createEdgeKey) are identical regardless of the
     * direction.
     */
    public static boolean isSameEdgeKeys(int edgeKey1, int edgeKey2) {
        return edgeKey1 / 2 == edgeKey2 / 2;
    }

    /**
     * Returns the edgeKey of the opposite direction
     */
    public static int reverseEdgeKey(int edgeKey) {
        return edgeKey % 2 == 0 ? edgeKey + 1 : edgeKey - 1;
    }

    /**
     * @return edge ID for edgeKey
     */
    public static int getEdgeFromEdgeKey(int edgeKey) {
        return edgeKey / 2;
    }

    /**
     * This edge iterator can be used in tests to mock specific iterator behaviour via overloading
     * certain methods.
     */
    public static class DisabledEdgeIterator implements CHEdgeIterator {
        @Override
        public EdgeIterator detach(boolean reverse) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public EdgeIteratorState setDistance(double dist) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public EdgeIteratorState setFlags(long flags) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public boolean next() {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public int getEdge() {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public int getBaseNode() {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public int getAdjNode() {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public double getDistance() {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public long getFlags() {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public PointList fetchWayGeometry(int type) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public EdgeIteratorState setWayGeometry(PointList list) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public String getName() {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public EdgeIteratorState setName(String name) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public boolean getBool(int key, boolean _default) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public boolean isBackward(FlagEncoder encoder) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public boolean isForward(FlagEncoder encoder) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public int getAdditionalField() {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public EdgeIteratorState setAdditionalField(int value) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public EdgeIteratorState copyPropertiesTo(EdgeIteratorState edge) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public boolean isShortcut() {
            return false;
        }

        @Override
        public int getSkippedEdge1() {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public int getSkippedEdge2() {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public void setSkippedEdges(int edge1, int edge2) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public double getWeight() {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public CHEdgeIteratorState setWeight(double weight) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public int getMergeStatus(long flags) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }
    }
}
