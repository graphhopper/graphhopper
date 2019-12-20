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
import com.graphhopper.coll.GHBitSet;
import com.graphhopper.coll.GHBitSetImpl;
import com.graphhopper.coll.GHIntArrayList;
import com.graphhopper.coll.GHTBitSet;
import com.graphhopper.routing.profiles.*;
import com.graphhopper.routing.util.*;
import com.graphhopper.storage.*;
import com.graphhopper.util.shapes.BBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.graphhopper.routing.profiles.TurnCost.EV_SUFFIX;
import static com.graphhopper.routing.util.EncodingManager.getKey;
import static com.graphhopper.util.Helper.DIST_EARTH;

/**
 * A helper class to avoid cluttering the Graph interface with all the common methods. Most of the
 * methods are useful for unit tests or debugging only.
 *
 * @author Peter Karich
 */
public class GHUtility {
    private static final Logger LOGGER = LoggerFactory.getLogger(GHUtility.class);

    /**
     * This method could throw an exception if problems like index out of bounds etc
     */
    public static List<String> getProblems(Graph g) {
        List<String> problems = new ArrayList<>();
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
        Set<Integer> s = new HashSet<>();
        for (int v : values) {
            s.add(v);
        }
        return s;
    }

    public static Set<Integer> getNeighbors(EdgeIterator iter) {
        // make iteration order over set static => linked
        Set<Integer> list = new LinkedHashSet<>();
        while (iter.next()) {
            list.add(iter.getAdjNode());
        }
        return list;
    }

    public static List<Integer> getEdgeIds(EdgeIterator iter) {
        List<Integer> list = new ArrayList<>();
        while (iter.next()) {
            list.add(iter.getEdge());
        }
        return list;
    }

    public static void printEdgeInfo(final Graph g, FlagEncoder encoder) {
        System.out.println("-- Graph nodes:" + g.getNodes() + " edges:" + g.getEdges() + " ---");
        AllEdgesIterator iter = g.getAllEdges();
        BooleanEncodedValue accessEnc = encoder.getAccessEnc();
        while (iter.next()) {
            String prefix = (iter instanceof AllCHEdgesIterator && ((AllCHEdgesIterator) iter).isShortcut()) ? "sc" : "  ";
            String fwdStr = iter.get(accessEnc) ? "fwd" : "   ";
            String bwdStr = iter.getReverse(accessEnc) ? "bwd" : "   ";
            System.out.println(prefix + " " + iter + " " + fwdStr + " " + bwdStr + " " + iter.getDistance());
        }
    }

    public static void printGraphForUnitTest(Graph g, FlagEncoder encoder) {
        printGraphForUnitTest(g, encoder, new BBox(
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY));
    }

    public static void printGraphForUnitTest(Graph g, FlagEncoder encoder, BBox bBox) {
        System.out.println("WARNING: printGraphForUnitTest does not pay attention to custom edge speeds at the moment");
        NodeAccess na = g.getNodeAccess();
        for (int node = 0; node < g.getNodes(); ++node) {
            if (bBox.contains(na.getLat(node), na.getLon(node))) {
                System.out.printf(Locale.ROOT, "na.setNode(%d, %f, %f);\n", node, na.getLat(node), na.getLon(node));
            }
        }
        AllEdgesIterator iter = g.getAllEdges();
        while (iter.next()) {
            if (bBox.contains(na.getLat(iter.getBaseNode()), na.getLon(iter.getBaseNode())) &&
                    bBox.contains(na.getLat(iter.getAdjNode()), na.getLon(iter.getAdjNode()))) {
                printUnitTestEdge(encoder, iter);
            }
        }
    }

    private static void printUnitTestEdge(FlagEncoder encoder, EdgeIteratorState edge) {
        BooleanEncodedValue accessEnc = encoder.getAccessEnc();
        boolean fwd = edge.get(accessEnc);
        boolean bwd = edge.getReverse(accessEnc);
        if (!fwd && !bwd) {
            return;
        }
        int from = fwd ? edge.getBaseNode() : edge.getAdjNode();
        int to = fwd ? edge.getAdjNode() : edge.getBaseNode();
        System.out.printf(Locale.ROOT,
                "graph.edge(%d, %d, %f, %s); // edgeId=%s\n", from, to, edge.getDistance(), fwd && bwd ? "true" : "false", edge.getEdge());
    }

    public static void buildRandomGraph(Graph graph, Random random, int numNodes, double meanDegree, boolean allowLoops,
                                        boolean allowZeroDistance, DecimalEncodedValue randomSpeedEnc,
                                        double pNonZeroLoop, double pBothDir, double pRandomOffset) {
        if (numNodes < 2 || meanDegree < 1) {
            throw new IllegalArgumentException("numNodes must be >= 2, meanDegree >= 1");
        }
        for (int i = 0; i < numNodes; ++i) {
            double lat = 49.4 + (random.nextDouble() * 0.01);
            double lon = 9.7 + (random.nextDouble() * 0.01);
            graph.getNodeAccess().setNode(i, lat, lon);
        }
        double minDist = Double.MAX_VALUE;
        double maxDist = Double.MIN_VALUE;
        int totalNumEdges = (int) (0.5 * meanDegree * numNodes);
        int numEdges = 0;
        while (numEdges < totalNumEdges) {
            int from = random.nextInt(numNodes);
            int to = random.nextInt(numNodes);
            if (!allowLoops && from == to) {
                continue;
            }
            double distance = GHUtility.getDistance(from, to, graph.getNodeAccess());
            // allow loops with non-zero distance
            if (from == to && random.nextDouble() < pNonZeroLoop) {
                distance = random.nextDouble() * 1000;
            }
            if (!allowZeroDistance) {
                distance = Math.max(0.001, distance);
            }
            // add some random offset, but also allow duplicate edges with same weight
            if (random.nextDouble() < pRandomOffset)
                distance += random.nextDouble() * distance * 0.01;
            minDist = Math.min(minDist, distance);
            maxDist = Math.max(maxDist, distance);
            // using bidirectional edges will increase mean degree of graph above given value
            boolean bothDirections = random.nextDouble() < pBothDir;
            EdgeIteratorState edge = graph.edge(from, to, distance, bothDirections);
            double fwdSpeed = 10 + random.nextDouble() * 120;
            double bwdSpeed = 10 + random.nextDouble() * 120;
            if (randomSpeedEnc != null) {
                edge.set(randomSpeedEnc, fwdSpeed);
                if (randomSpeedEnc.isStoreTwoDirections())
                    edge.setReverse(randomSpeedEnc, bwdSpeed);
            }
            numEdges++;
        }
        LOGGER.debug(String.format(Locale.ROOT, "Finished building random graph" +
                        ", nodes: %d, edges: %d , min distance: %.2f, max distance: %.2f\n",
                graph.getNodes(), graph.getEdges(), minDist, maxDist));
    }

    public static double getDistance(int from, int to, NodeAccess nodeAccess) {
        double fromLat = nodeAccess.getLat(from);
        double fromLon = nodeAccess.getLon(from);
        double toLat = nodeAccess.getLat(to);
        double toLon = nodeAccess.getLon(to);
        return Helper.DIST_PLANE.calcDist(fromLat, fromLon, toLat, toLon);
    }

    public static void addRandomTurnCosts(Graph graph, long seed, EncodingManager em, FlagEncoder encoder, int maxTurnCost, TurnCostStorage turnCostStorage) {
        Random random = new Random(seed);
        double pNodeHasTurnCosts = 0.3;
        double pEdgePairHasTurnCosts = 0.6;
        double pCostIsRestriction = 0.1;

        DecimalEncodedValue turnCostEnc = em.getDecimalEncodedValue(getKey(encoder.toString(), EV_SUFFIX));
        EdgeExplorer inExplorer = graph.createEdgeExplorer(DefaultEdgeFilter.inEdges(encoder));
        EdgeExplorer outExplorer = graph.createEdgeExplorer(DefaultEdgeFilter.outEdges(encoder));
        IntsRef tcFlags = TurnCost.createFlags();
        for (int node = 0; node < graph.getNodes(); ++node) {
            if (random.nextDouble() < pNodeHasTurnCosts) {
                EdgeIterator inIter = inExplorer.setBaseNode(node);
                while (inIter.next()) {
                    EdgeIterator outIter = outExplorer.setBaseNode(node);
                    while (outIter.next()) {
                        if (inIter.getEdge() == outIter.getEdge()) {
                            // leave u-turns as they are
                            continue;
                        }
                        if (random.nextDouble() < pEdgePairHasTurnCosts) {
                            double cost = random.nextDouble() < pCostIsRestriction ? Double.POSITIVE_INFINITY : random.nextDouble() * maxTurnCost;
                            turnCostStorage.set(turnCostEnc, tcFlags, inIter.getEdge(), node, outIter.getEdge(), cost);
                        }
                    }
                }
            }
        }
    }

    public static void printInfo(final Graph g, int startNode, final int counts, final EdgeFilter filter) {
        new BreadthFirstSearch() {
            int counter = 0;

            @Override
            protected GHBitSet createBitSet() {
                return new GHTBitSet();
            }

            @Override
            protected boolean goFurther(int nodeId) {
                System.out.println(getNodeInfo(g, nodeId, filter));
                return counter++ <= counts;
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
                    + iter.getEdge() + " \t" + BitUtil.BIG.toBitString(iter.getFlags().ints[0], 8) + "\n";
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
                    + "\t" + BitUtil.BIG.toBitString(iter.getFlags().ints[0], 8) + "\n";
        }
        return str;
    }

    public static Graph shuffle(Graph g, Graph sortedGraph) {
        if (g.getTurnCostStorage() != null) {
            throw new IllegalArgumentException("Shuffling the graph is currently not supported in the presence of turn costs");
        }
        int nodes = g.getNodes();
        GHIntArrayList list = new GHIntArrayList(nodes);
        list.fill(nodes, -1);
        for (int i = 0; i < nodes; i++) {
            list.set(i, i);
        }
        list.shuffle(new Random());

        int edges = g.getEdges();
        GHIntArrayList edgesList = new GHIntArrayList(edges);
        edgesList.fill(edges, -1);
        for (int i = 0; i < edges; i++) {
            edgesList.set(i, i);
        }
        edgesList.shuffle(new Random());

        return createSortedGraph(g, sortedGraph, list, edgesList);
    }

    /**
     * Sorts the graph according to depth-first search traversal. Other traversals have either no
     * significant difference (bfs) for querying or are worse (z-curve).
     */
    public static Graph sortDFS(Graph g, Graph sortedGraph) {
        if (g.getTurnCostStorage() != null) {
            throw new IllegalArgumentException("Sorting the graph is currently not supported in the presence of turn costs");
        }
        int nodes = g.getNodes();
        final GHIntArrayList nodeList = new GHIntArrayList(nodes);
        nodeList.fill(nodes, -1);
        final GHBitSetImpl nodeBitset = new GHBitSetImpl(nodes);
        final AtomicInteger nodeRef = new AtomicInteger(-1);

        int edges = g.getEdges();
        final GHIntArrayList edgeList = new GHIntArrayList(edges);
        edgeList.fill(edges, -1);
        final GHBitSetImpl edgeBitset = new GHBitSetImpl(edges);
        final AtomicInteger edgeRef = new AtomicInteger(-1);

        EdgeExplorer explorer = g.createEdgeExplorer();
        for (int startNode = 0; startNode >= 0 && startNode < nodes;
             startNode = nodeBitset.nextClear(startNode + 1)) {
            new DepthFirstSearch() {
                @Override
                protected GHBitSet createBitSet() {
                    return nodeBitset;
                }

                @Override
                protected boolean checkAdjacent(EdgeIteratorState edge) {
                    int edgeId = edge.getEdge();
                    if (!edgeBitset.contains(edgeId)) {
                        edgeBitset.add(edgeId);
                        edgeList.set(edgeRef.incrementAndGet(), edgeId);
                    }
                    return super.checkAdjacent(edge);
                }

                @Override
                protected boolean goFurther(int nodeId) {
                    nodeList.set(nodeId, nodeRef.incrementAndGet());
                    return super.goFurther(nodeId);
                }
            }.start(explorer, startNode);
        }
        return createSortedGraph(g, sortedGraph, nodeList, edgeList);
    }

    static Graph createSortedGraph(Graph fromGraph, Graph toSortedGraph, final IntIndexedContainer oldToNewNodeList, final IntIndexedContainer newToOldEdgeList) {
        if (fromGraph.getTurnCostStorage() != null) {
            throw new IllegalArgumentException("Sorting the graph is currently not supported in the presence of turn costs");
        }
        int edges = fromGraph.getEdges();
        for (int i = 0; i < edges; i++) {
            int edgeId = newToOldEdgeList.get(i);
            if (edgeId < 0)
                continue;

            EdgeIteratorState eIter = fromGraph.getEdgeIteratorState(edgeId, Integer.MIN_VALUE);

            int base = eIter.getBaseNode();
            int newBaseIndex = oldToNewNodeList.get(base);
            int adj = eIter.getAdjNode();
            int newAdjIndex = oldToNewNodeList.get(adj);

            // ignore empty entries
            if (newBaseIndex < 0 || newAdjIndex < 0)
                continue;

            toSortedGraph.edge(newBaseIndex, newAdjIndex).copyPropertiesFrom(eIter);
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
        if (fromGraph.getTurnCostStorage() != null) {
            throw new IllegalArgumentException("Copying a graph is currently not supported in the presence of turn costs");
        }
        AllEdgesIterator eIter = fromGraph.getAllEdges();
        while (eIter.next()) {
            int base = eIter.getBaseNode();
            int adj = eIter.getAdjNode();
            toGraph.edge(base, adj).copyPropertiesFrom(eIter);
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
        if (store.getDirectory() instanceof MMapDirectory) {
            throw new IllegalStateException("not supported yet: mmap will overwrite existing storage at the same location");
        }
        String location = store.getDirectory().getLocation();
        boolean isStoring = ((GHDirectory) store.getDirectory()).isStoring();
        return new RAMDirectory(location, isStoring);
    }

    /**
     * Create a new storage from the specified one without copying the data.
     */
    public static GraphHopperStorage newStorage(GraphHopperStorage store) {
        Directory outdir = guessDirectory(store);
        boolean is3D = store.getNodeAccess().is3D();
        return new GraphBuilder(store.getEncodingManager())
                .withTurnCosts(store.getTurnCostStorage() != null)
                .set3D(is3D)
                .setDir(outdir)
                .setCHProfiles(store.getCHProfiles())
                .setBytes(store.getNodes())
                .create();
    }

    public static int getAdjNode(Graph g, int edge, int adjNode) {
        if (EdgeIterator.Edge.isValid(edge)) {
            EdgeIteratorState iterTo = g.getEdgeIteratorState(edge, adjNode);
            return iterTo.getAdjNode();
        }
        return adjNode;
    }

    public static EdgeIteratorState createMockedEdgeIteratorState(final double distance, final IntsRef flags) {
        return createMockedEdgeIteratorState(distance, flags, 0, 1, 2, 3, 4);
    }

    public static EdgeIteratorState createMockedEdgeIteratorState(final double distance, final IntsRef flags,
                                                                  final int base, final int adj, final int edge, final int origFirst, final int origLast) {
        return new GHUtility.DisabledEdgeIterator() {
            @Override
            public double getDistance() {
                return distance;
            }

            @Override
            public IntsRef getFlags() {
                return flags;
            }

            @Override
            public boolean get(BooleanEncodedValue property) {
                return property.getBool(false, flags);
            }

            @Override
            public boolean getReverse(BooleanEncodedValue property) {
                return property.getBool(true, flags);
            }

            @Override
            public double get(DecimalEncodedValue property) {
                return property.getDecimal(false, flags);
            }

            @Override
            public double getReverse(DecimalEncodedValue property) {
                return property.getDecimal(true, flags);
            }

            @Override
            public <T extends Enum> T get(EnumEncodedValue<T> property) {
                return property.getEnum(false, flags);
            }

            @Override
            public <T extends Enum> T getReverse(EnumEncodedValue<T> property) {
                return property.getEnum(true, flags);
            }

            @Override
            public int getEdge() {
                return edge;
            }

            @Override
            public int getBaseNode() {
                return base;
            }

            @Override
            public int getAdjNode() {
                return adj;
            }

            @Override
            public PointList fetchWayGeometry(int type) {
                return Helper.createPointList(0, 2, 6, 4);
            }

            @Override
            public int getOrigEdgeFirst() {
                return origFirst;
            }

            @Override
            public int getOrigEdgeLast() {
                return origLast;
            }
        };
    }

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
            return (nodeA >= nodeB) ? edgeId : edgeId + 1;
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

    public static IntsRef setProperties(IntsRef edgeFlags, FlagEncoder encoder, double averageSpeed, boolean fwd, boolean bwd) {
        if (averageSpeed < 0.0001 && (fwd || bwd))
            throw new IllegalStateException("Zero speed is only allowed if edge will get inaccessible. Otherwise Weighting can produce inconsistent results");

        BooleanEncodedValue accessEnc = encoder.getAccessEnc();
        DecimalEncodedValue avSpeedEnc = encoder.getAverageSpeedEnc();
        accessEnc.setBool(false, edgeFlags, fwd);
        accessEnc.setBool(true, edgeFlags, bwd);
        if (fwd)
            avSpeedEnc.setDecimal(false, edgeFlags, averageSpeed);
        if (bwd)
            avSpeedEnc.setDecimal(true, edgeFlags, averageSpeed);
        return edgeFlags;
    }

    public static EdgeIteratorState setProperties(EdgeIteratorState edge, FlagEncoder encoder, double fwdSpeed, double bwdSpeed) {
        BooleanEncodedValue accessEnc = encoder.getAccessEnc();
        edge.set(accessEnc, true).setReverse(accessEnc, true);
        DecimalEncodedValue avSpeedEnc = encoder.getAverageSpeedEnc();
        return edge.set(avSpeedEnc, fwdSpeed).setReverse(avSpeedEnc, bwdSpeed);
    }

    public static IntsRef setProperties(IntsRef edgeFlags, FlagEncoder encoder, double fwdSpeed, double bwdSpeed) {
        BooleanEncodedValue accessEnc = encoder.getAccessEnc();
        accessEnc.setBool(false, edgeFlags, true);
        accessEnc.setBool(true, edgeFlags, true);
        DecimalEncodedValue avSpeedEnc = encoder.getAverageSpeedEnc();
        avSpeedEnc.setDecimal(false, edgeFlags, fwdSpeed);
        avSpeedEnc.setDecimal(true, edgeFlags, bwdSpeed);
        return edgeFlags;
    }

    public static EdgeIteratorState setProperties(EdgeIteratorState edge, FlagEncoder encoder, double averageSpeed, boolean fwd, boolean bwd) {
        if (averageSpeed < 0.0001 && (fwd || bwd))
            throw new IllegalStateException("Zero speed is only allowed if edge will get inaccessible. Otherwise Weighting can produce inconsistent results");

        BooleanEncodedValue accessEnc = encoder.getAccessEnc();
        DecimalEncodedValue avSpeedEnc = encoder.getAverageSpeedEnc();
        edge.set(accessEnc, fwd).setReverse(accessEnc, bwd);
        if (fwd)
            edge.set(avSpeedEnc, averageSpeed);
        if (bwd && avSpeedEnc.isStoreTwoDirections())
            edge.setReverse(avSpeedEnc, averageSpeed);
        return edge;
    }

    public static void updateDistancesFor(Graph g, int node, double lat, double lon) {
        NodeAccess na = g.getNodeAccess();
        na.setNode(node, lat, lon);
        EdgeIterator iter = g.createEdgeExplorer().setBaseNode(node);
        while (iter.next()) {
            iter.setDistance(iter.fetchWayGeometry(3).calcDistance(DIST_EARTH));
            // System.out.println(node + "->" + adj + ": " + iter.getDistance());
        }
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
        public EdgeIteratorState setFlags(IntsRef flags) {
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
        public IntsRef getFlags() {
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
        public boolean get(BooleanEncodedValue property) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public EdgeIteratorState set(BooleanEncodedValue property, boolean value) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public boolean getReverse(BooleanEncodedValue property) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public EdgeIteratorState setReverse(BooleanEncodedValue property, boolean value) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public int get(IntEncodedValue property) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public EdgeIteratorState set(IntEncodedValue property, int value) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public int getReverse(IntEncodedValue property) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public EdgeIteratorState setReverse(IntEncodedValue property, int value) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public double get(DecimalEncodedValue property) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public EdgeIteratorState set(DecimalEncodedValue property, double value) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public double getReverse(DecimalEncodedValue property) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public EdgeIteratorState setReverse(DecimalEncodedValue property, double value) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public <T extends Enum> T get(EnumEncodedValue<T> property) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public <T extends Enum> EdgeIteratorState set(EnumEncodedValue<T> property, T value) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public <T extends Enum> T getReverse(EnumEncodedValue<T> property) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public <T extends Enum> EdgeIteratorState setReverse(EnumEncodedValue<T> property, T value) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public EdgeIteratorState copyPropertiesFrom(EdgeIteratorState edge) {
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
        public CHEdgeIteratorState setSkippedEdges(int edge1, int edge2) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public int getOrigEdgeFirst() {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public int getOrigEdgeLast() {
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
        public void setFlagsAndWeight(int flags, double weight) {
            throw new UnsupportedOperationException("Not supported. Edge is empty");
        }

        @Override
        public int getMergeStatus(int flags) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }
    }
}
