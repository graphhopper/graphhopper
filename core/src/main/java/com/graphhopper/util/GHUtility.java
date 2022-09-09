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

import com.bedatadriven.jackson.datatype.jts.JtsModule;
import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntIndexedContainer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.coll.GHBitSet;
import com.graphhopper.coll.GHBitSetImpl;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.AccessFilter;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.CustomArea;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.search.EdgeKVStorage;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.shapes.BBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.graphhopper.util.DistanceCalcEarth.DIST_EARTH;

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
                double lat = na.getLat(nodeIndex);
                if (lat > 90 || lat < -90)
                    problems.add("latitude is not within its bounds " + lat);

                double lon = na.getLon(nodeIndex);
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

    public static int count(RoutingCHEdgeIterator iter) {
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

    public static Set<Integer> getNeighbors(RoutingCHEdgeIterator iter) {
        // make iteration order over set static => linked
        Set<Integer> list = new LinkedHashSet<>();
        while (iter.next()) {
            list.add(iter.getAdjNode());
        }
        return list;
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

    public static void printGraphForUnitTest(Graph g, BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc) {
        printGraphForUnitTest(g, accessEnc, speedEnc, new BBox(
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY));
    }

    public static void printGraphForUnitTest(Graph g, BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc, BBox bBox) {
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
                printUnitTestEdge(accessEnc, speedEnc, iter);
            }
        }
    }

    private static void printUnitTestEdge(BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc, EdgeIteratorState edge) {
        boolean fwd = edge.get(accessEnc);
        boolean bwd = edge.getReverse(accessEnc);
        if (!fwd && !bwd) {
            return;
        }
        int from = fwd ? edge.getBaseNode() : edge.getAdjNode();
        int to = fwd ? edge.getAdjNode() : edge.getBaseNode();
        if (speedEnc != null) {
            System.out.printf(Locale.ROOT,
                    "GHUtility.setSpeed(%f, %f, encoder, graph.edge(%d, %d).setDistance(%f)); // edgeId=%s\n",
                    fwd ? edge.get(speedEnc) : 0, bwd ? edge.getReverse(speedEnc) : 0,
                    from, to, edge.getDistance(), edge.getEdge());
        } else {
            System.out.printf(Locale.ROOT,
                    "graph.edge(%d, %d).setDistance(%f).set(accessEnc, %b, %b); // edgeId=%s\n",
                    from, to, edge.getDistance(),
                    edge.get(accessEnc), edge.getReverse(accessEnc), edge.getEdge());
        }
    }

    /**
     * @param speed if null a random speed will be assign to every edge
     */
    public static void buildRandomGraph(Graph graph, Random random, int numNodes, double meanDegree, boolean allowLoops,
                                        boolean allowZeroDistance, BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc, Double speed,
                                        double pNonZeroLoop, double pBothDir, double pRandomDistanceOffset) {
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
            if (random.nextDouble() < pRandomDistanceOffset)
                distance += random.nextDouble() * distance * 0.01;
            minDist = Math.min(minDist, distance);
            maxDist = Math.max(maxDist, distance);
            // using bidirectional edges will increase mean degree of graph above given value
            boolean bothDirections = random.nextDouble() < pBothDir;
            EdgeIteratorState edge = graph.edge(from, to).setDistance(distance).set(accessEnc, true);
            if (bothDirections) edge.setReverse(accessEnc, true);
            double fwdSpeed = 10 + random.nextDouble() * 110;
            double bwdSpeed = 10 + random.nextDouble() * 110;
            // if an explicit speed is given we discard the random speeds and use the given one instead
            if (speed != null) {
                fwdSpeed = bwdSpeed = speed;
            }
            if (speedEnc != null) {
                edge.set(speedEnc, fwdSpeed);
                if (speedEnc.isStoreTwoDirections())
                    edge.setReverse(speedEnc, bwdSpeed);
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
        return DistancePlaneProjection.DIST_PLANE.calcDist(fromLat, fromLon, toLat, toLon);
    }

    public static void addRandomTurnCosts(Graph graph, long seed, BooleanEncodedValue accessEnc, DecimalEncodedValue turnCostEnc, int maxTurnCost, TurnCostStorage turnCostStorage) {
        Random random = new Random(seed);
        double pNodeHasTurnCosts = 0.3;
        double pEdgePairHasTurnCosts = 0.6;
        double pCostIsRestriction = 0.1;

        EdgeExplorer inExplorer = graph.createEdgeExplorer(AccessFilter.inEdges(accessEnc));
        EdgeExplorer outExplorer = graph.createEdgeExplorer(AccessFilter.outEdges(accessEnc));
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
                            turnCostStorage.set(turnCostEnc, inIter.getEdge(), node, outIter.getEdge(), cost);
                        }
                    }
                }
            }
        }
    }

    public static List<Snap> createRandomSnaps(BBox bbox, LocationIndex locationIndex, Random rnd, int numPoints, boolean acceptTower, EdgeFilter filter) {
        int maxTries = numPoints * 100;
        int tries = 0;
        List<Snap> snaps = new ArrayList<>(numPoints);
        while (snaps.size() < numPoints) {
            if (tries > maxTries)
                throw new IllegalArgumentException("Could not create " + numPoints + " random points. tries: " + tries + ", maxTries: " + maxTries);
            Snap snap = getRandomSnap(locationIndex, rnd, bbox, filter);
            boolean accepted = snap.isValid();
            if (!acceptTower)
                accepted = accepted && !snap.getSnappedPosition().equals(Snap.Position.TOWER);
            if (accepted)
                snaps.add(snap);
            tries++;
        }
        return snaps;
    }

    public static Snap getRandomSnap(LocationIndex locationIndex, Random rnd, BBox bbox, EdgeFilter filter) {
        return locationIndex.findClosest(
                randomDoubleInRange(rnd, bbox.minLat, bbox.maxLat),
                randomDoubleInRange(rnd, bbox.minLon, bbox.maxLon),
                filter
        );
    }

    public static double randomDoubleInRange(Random rnd, double min, double max) {
        return min + rnd.nextDouble() * (max - min);
    }

    public static Graph shuffle(Graph g, Graph sortedGraph) {
        if (g.getTurnCostStorage() != null)
            throw new IllegalArgumentException("Shuffling the graph is currently not supported in the presence of turn costs");
        IntArrayList nodes = ArrayUtil.permutation(g.getNodes(), new Random());
        IntArrayList edges = ArrayUtil.permutation(g.getEdges(), new Random());
        return createSortedGraph(g, sortedGraph, nodes, edges);
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
        final IntArrayList nodeList = ArrayUtil.constant(nodes, -1);
        final GHBitSetImpl nodeBitset = new GHBitSetImpl(nodes);
        final AtomicInteger nodeRef = new AtomicInteger(-1);

        int edges = g.getEdges();
        final IntArrayList edgeList = ArrayUtil.constant(edges, -1);
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
                sna.setNode(newIndex, na.getLat(old), na.getLon(old), na.getEle(old));
            else
                sna.setNode(newIndex, na.getLat(old), na.getLon(old));
        }
        return toSortedGraph;
    }

    static Directory guessDirectory(BaseGraph graph) {
        if (graph.getDirectory() instanceof MMapDirectory) {
            throw new IllegalStateException("not supported yet: mmap will overwrite existing storage at the same location");
        }
        String location = graph.getDirectory().getLocation();
        boolean isStoring = ((GHDirectory) graph.getDirectory()).isStoring();
        return new RAMDirectory(location, isStoring);
    }

    /**
     * Create a new storage from the specified one without copying the data. CHGraphs won't be copied.
     */
    public static BaseGraph newGraph(BaseGraph baseGraph) {
        Directory outdir = guessDirectory(baseGraph);
        return new BaseGraph.Builder(baseGraph.getIntsForFlags())
                .withTurnCosts(baseGraph.getTurnCostStorage() != null)
                .set3D(baseGraph.getNodeAccess().is3D())
                .setDir(outdir)
                .create();
    }

    public static int getAdjNode(Graph g, int edge, int adjNode) {
        if (EdgeIterator.Edge.isValid(edge)) {
            EdgeIteratorState iterTo = g.getEdgeIteratorState(edge, adjNode);
            return iterTo.getAdjNode();
        }
        return adjNode;
    }

    public static void checkDAVersion(String name, int expectedVersion, int version) {
        if (version != expectedVersion) {
            throw new IllegalStateException("Unexpected version for '" + name + "'. Got: " + version + ", " +
                    "expected: " + expectedVersion + ". "
                    + "Make sure you are using the same GraphHopper version for reading the files that was used for creating them. "
                    + "See https://discuss.graphhopper.com/t/722");
        }
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
            public <T extends Enum<?>> T get(EnumEncodedValue<T> property) {
                return property.getEnum(false, flags);
            }

            @Override
            public <T extends Enum<?>> T getReverse(EnumEncodedValue<T> property) {
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
            public PointList fetchWayGeometry(FetchMode type) {
                return Helper.createPointList(0, 2, 6, 4);
            }

        };
    }

    /**
     * @return the the edge between base and adj, or null if there is no such edge
     * @throws IllegalArgumentException when there are multiple edges
     */
    public static EdgeIteratorState getEdge(Graph graph, int base, int adj) {
        EdgeExplorer explorer = graph.createEdgeExplorer();
        int count = count(explorer.setBaseNode(base), adj);
        if (count > 1)
            throw new IllegalArgumentException("There are multiple edges between nodes " + base + " and " + adj);
        else if (count == 0)
            return null;
        EdgeIterator iter = explorer.setBaseNode(base);
        while (iter.next()) {
            if (iter.getAdjNode() == adj)
                return iter;
        }
        throw new IllegalStateException("There should be an edge");
    }

    /**
     * @return the number of edges with the given adj node
     */
    public static int count(EdgeIterator iterator, int adj) {
        int count = 0;
        while (iterator.next()) {
            if (iterator.getAdjNode() == adj)
                count++;
        }
        return count;
    }

    /**
     * Creates an edge key, i.e. an integer number that encodes an edge ID and the direction of an edge
     */
    public static int createEdgeKey(int edgeId, boolean isLoop, boolean reverse) {
        // edge state in storage direction -> edge key is even
        // edge state against storage direction -> edge key is odd
        return (edgeId << 1) + ((reverse && !isLoop) ? 1 : 0);
    }

    /**
     * Returns the edgeKey of the opposite direction, be careful not to use this for loops!
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

    public static IntsRef setSpeed(double fwdSpeed, double bwdSpeed, BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc, IntsRef edgeFlags) {
        if (fwdSpeed < 0 || bwdSpeed < 0)
            throw new IllegalArgumentException("Speed must be positive but wasn't! fwdSpeed:" + fwdSpeed + ", bwdSpeed:" + bwdSpeed);

        speedEnc.setDecimal(false, edgeFlags, fwdSpeed);
        if (fwdSpeed > 0)
            accessEnc.setBool(false, edgeFlags, true);

        if (bwdSpeed > 0 && (fwdSpeed != bwdSpeed || speedEnc.isStoreTwoDirections())) {
            if (!speedEnc.isStoreTwoDirections())
                throw new IllegalArgumentException("EncodedValue " + speedEnc.getName() + " supports only one direction " +
                        "but two different speeds were specified " + fwdSpeed + " " + bwdSpeed);
            speedEnc.setDecimal(true, edgeFlags, bwdSpeed);
        }
        if (bwdSpeed > 0)
            accessEnc.setBool(true, edgeFlags, true);
        return edgeFlags;
    }

    public static void setSpeed(double fwdSpeed, double bwdSpeed, BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc, EdgeIteratorState... edges) {
        setSpeed(fwdSpeed, bwdSpeed, accessEnc, speedEnc, Arrays.asList(edges));
    }

    public static void setSpeed(double fwdSpeed, double bwdSpeed, BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc, Collection<EdgeIteratorState> edges) {
        if (fwdSpeed < 0 || bwdSpeed < 0)
            throw new IllegalArgumentException("Speed must be positive but wasn't! fwdSpeed:" + fwdSpeed + ", bwdSpeed:" + bwdSpeed);
        for (EdgeIteratorState edge : edges) {
            edge.set(speedEnc, fwdSpeed);
            if (fwdSpeed > 0)
                edge.set(accessEnc, true);

            if (bwdSpeed > 0 && (fwdSpeed != bwdSpeed || speedEnc.isStoreTwoDirections())) {
                if (!speedEnc.isStoreTwoDirections())
                    throw new IllegalArgumentException("EncodedValue " + speedEnc.getName() + " supports only one direction " +
                            "but two different speeds were specified " + fwdSpeed + " " + bwdSpeed);
                edge.setReverse(speedEnc, bwdSpeed);
            }
            if (bwdSpeed > 0)
                edge.setReverse(accessEnc, true);
        }
    }

    public static EdgeIteratorState setSpeed(double averageSpeed, boolean fwd, boolean bwd, BooleanEncodedValue accessEnc, DecimalEncodedValue avSpeedEnc, EdgeIteratorState edge) {
        if (averageSpeed < 0.0001 && (fwd || bwd))
            throw new IllegalStateException("Zero speed is only allowed if edge will get inaccessible. Otherwise Weighting can produce inconsistent results");
        edge.set(accessEnc, fwd, bwd);
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
            iter.setDistance(DIST_EARTH.calcDistance(iter.fetchWayGeometry(FetchMode.ALL)));
            // System.out.println(node + "->" + adj + ": " + iter.getDistance());
        }
    }

    public static double calcWeightWithTurnWeightWithAccess(Weighting weighting, EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        if (edgeState.getBaseNode() == edgeState.getAdjNode()) {
            if (weighting.edgeHasNoAccess(edgeState, false) && weighting.edgeHasNoAccess(edgeState, true))
                return Double.POSITIVE_INFINITY;
        } else if (weighting.edgeHasNoAccess(edgeState, reverse)) {
            return Double.POSITIVE_INFINITY;
        }
        return calcWeightWithTurnWeight(weighting, edgeState, reverse, prevOrNextEdgeId);
    }

    /**
     * Calculates the weight of a given edge like {@link Weighting#calcEdgeWeight} and adds the transition
     * cost (the turn weight, {@link Weighting#calcTurnWeight}) associated with transitioning from/to the edge with ID prevOrNextEdgeId.
     *
     * @param prevOrNextEdgeId if reverse is false this has to be the previous edgeId, if true it
     *                         has to be the next edgeId in the direction from start to end.
     */
    public static double calcWeightWithTurnWeight(Weighting weighting, EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        final double edgeWeight = weighting.calcEdgeWeight(edgeState, reverse);
        if (!EdgeIterator.Edge.isValid(prevOrNextEdgeId)) {
            return edgeWeight;
        }
        double turnWeight = reverse
                ? weighting.calcTurnWeight(edgeState.getEdge(), edgeState.getBaseNode(), prevOrNextEdgeId)
                : weighting.calcTurnWeight(prevOrNextEdgeId, edgeState.getBaseNode(), edgeState.getEdge());
        return edgeWeight + turnWeight;
    }

    /**
     * @see #calcWeightWithTurnWeight(Weighting, EdgeIteratorState, boolean, int)
     */
    public static long calcMillisWithTurnMillis(Weighting weighting, EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        long edgeMillis = weighting.calcEdgeMillis(edgeState, reverse);
        if (!EdgeIterator.Edge.isValid(prevOrNextEdgeId)) {
            return edgeMillis;
        }
        // should we also separate weighting vs. time for turn? E.g. a fast but dangerous turn - is this common?
        // todo: why no first/last orig edge here as in calcWeight ?
//        final int origEdgeId = reverse ? edgeState.getOrigEdgeLast() : edgeState.getOrigEdgeFirst();
        final int origEdgeId = edgeState.getEdge();
        long turnMillis = reverse
                ? weighting.calcTurnMillis(origEdgeId, edgeState.getBaseNode(), prevOrNextEdgeId)
                : weighting.calcTurnMillis(prevOrNextEdgeId, edgeState.getBaseNode(), origEdgeId);
        return edgeMillis + turnMillis;
    }

    /**
     * Reads the country borders from the countries.geojson resource file
     */
    public static List<CustomArea> readCountries() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JtsModule());
        try (Reader reader = new InputStreamReader(GHUtility.class.getResourceAsStream("/com/graphhopper/countries/countries.geojson"), StandardCharsets.UTF_8)) {
            JsonFeatureCollection jsonFeatureCollection = objectMapper.readValue(reader, JsonFeatureCollection.class);
            return jsonFeatureCollection.getFeatures().stream()
                    .map(CustomArea::fromJsonFeature)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void runConcurrently(Stream<Callable<String>> callables, int threads) {
        ExecutorService executorService = Executors.newFixedThreadPool(threads);
        ExecutorCompletionService<String> completionService = new ExecutorCompletionService<>(executorService);
        AtomicInteger count = new AtomicInteger();
        callables.forEach(c -> {
            count.incrementAndGet();
            completionService.submit(c);
        });
        executorService.shutdown();
        try {
            for (int i = 0; i < count.get(); i++)
                completionService.take().get();
        } catch (Exception e) {
            executorService.shutdownNow();
            throw new RuntimeException(e);
        }
    }

    /**
     * This edge iterator can be used in tests to mock specific iterator behaviour via overloading
     * certain methods.
     */
    public static class DisabledEdgeIterator implements EdgeIterator {
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
        public int getEdgeKey() {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public int getReverseEdgeKey() {
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
        public PointList fetchWayGeometry(FetchMode type) {
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
        public EdgeIteratorState setKeyValues(List<EdgeKVStorage.KeyValue> keyValues) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public List<EdgeKVStorage.KeyValue> getKeyValues() {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public Object getValue(String key) {
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
        public EdgeIteratorState set(BooleanEncodedValue property, boolean fwd, boolean bwd) {
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
        public EdgeIteratorState set(IntEncodedValue property, int fwd, int bwd) {
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
        public EdgeIteratorState set(DecimalEncodedValue property, double fwd, double bwd) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public <T extends Enum<?>> T get(EnumEncodedValue<T> property) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public <T extends Enum<?>> EdgeIteratorState set(EnumEncodedValue<T> property, T value) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public <T extends Enum<?>> T getReverse(EnumEncodedValue<T> property) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public <T extends Enum<?>> EdgeIteratorState setReverse(EnumEncodedValue<T> property, T value) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public <T extends Enum<?>> EdgeIteratorState set(EnumEncodedValue<T> property, T fwd, T bwd) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public String get(StringEncodedValue property) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public EdgeIteratorState set(StringEncodedValue property, String value) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public String getReverse(StringEncodedValue property) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public EdgeIteratorState setReverse(StringEncodedValue property, String value) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public EdgeIteratorState set(StringEncodedValue property, String fwd, String bwd) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public EdgeIteratorState copyPropertiesFrom(EdgeIteratorState edge) {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

    }

    public static BBox createBBox(EdgeIteratorState edgeState) {
        PointList towerNodes = edgeState.fetchWayGeometry(FetchMode.TOWER_ONLY);
        int secondIndex = towerNodes.size() == 1 ? 0 : 1;
        return BBox.fromPoints(towerNodes.getLat(0), towerNodes.getLon(0),
                towerNodes.getLat(secondIndex), towerNodes.getLon(secondIndex));
    }
}
