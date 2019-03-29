package com.graphhopper.routing;

import com.graphhopper.RepeatRule;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PMap;
import com.graphhopper.util.shapes.BBox;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class RandomCHRoutingTest {
    private final TraversalMode traversalMode = TraversalMode.NODE_BASED;
    private Directory dir;
    private CarFlagEncoder encoder;
    private Weighting weighting;
    private GraphHopperStorage graph;
    private LocationIndexTree locationIndex;
    private CHGraph chGraph;

    @Rule
    public RepeatRule repeatRule = new RepeatRule();

    @Before
    public void init() {
        dir = new RAMDirectory();
        encoder = new CarFlagEncoder();
        EncodingManager em = EncodingManager.create(encoder);
        weighting = new FastestWeighting(encoder);
        graph = new GraphBuilder(em).setCHGraph(weighting).create();
        chGraph = graph.getGraph(CHGraph.class);
    }


    /**
     * Runs random routing queries on a random query/CH graph with random speeds and adding random virtual edges and
     * nodes.
     */
    @Test
    public void issue1574_random() {
        // you might have to keep this test running in an infinite loop for several minutes to find potential routing
        // bugs (e.g. use intellij 'run until stop/failure').
        int numNodes = 50;
        long seed = System.nanoTime();
        // for example these used to fail before fixing #1574.
//        seed = 9348906923700L;
//        seed = 9376976930825L;
//        seed = 9436934744695L;
//        seed = 10093639220394L;

        System.out.println("seed: " + seed);
        Random rnd = new Random(seed);
        // todo: allowing loops also produces another error (may not read speed in wrong direction...), e.g. with this seed: 10785899964423
        boolean allowLoops = false;
        buildRandomGraph(rnd, numNodes, 2.5, allowLoops, true, 0.9);
        locationIndex = new LocationIndexTree(graph, dir);
        locationIndex.prepareIndex();

        graph.freeze();
        PrepareContractionHierarchies pch = new PrepareContractionHierarchies(chGraph, weighting, traversalMode);
        pch.doWork();

        int numQueryGraph = 50;
        for (int j = 0; j < numQueryGraph; j++) {
            QueryGraph queryGraph = new QueryGraph(graph);
            QueryGraph chQueryGraph = new QueryGraph(chGraph);
            addVirtualNodesAndEdges(rnd, queryGraph, chQueryGraph);

            int numQueries = 100;
            int numPathsNotFound = 0;
            for (int i = 0; i < numQueries; i++) {
                assertEquals("queryGraph and chQueryGraph should have equal number of nodes", queryGraph.getNodes(), chQueryGraph.getNodes());
                int from = rnd.nextInt(queryGraph.getNodes());
                int to = rnd.nextInt(queryGraph.getNodes());
                DijkstraBidirectionRef refAlgo = new DijkstraBidirectionRef(queryGraph, weighting, TraversalMode.NODE_BASED);
                Path refPath = refAlgo.calcPath(from, to);
                if (!refPath.isFound()) {
                    numPathsNotFound++;
                    continue;
                }

                RoutingAlgorithm algo = pch.createAlgo(chQueryGraph, AlgorithmOptions.start().hints(new PMap().put("stall_on_demand", true)).build());
                Path path = algo.calcPath(from, to);
                if (!path.isFound()) {
                    fail("path not found for for " + from + "->" + to + ", expected weight: " + path.getWeight());
                }

                double weight = path.getWeight();
                double refWeight = refPath.getWeight();
                if (Math.abs(refWeight - weight) > 1) {
                    fail("wrong weight: " + from + "->" + to + ", dijkstra: " + refWeight + " vs. ch: " + path.getWeight());
                }
            }
            if (numPathsNotFound > 0.9 * numQueries) {
                fail("Too many paths not found: " + numPathsNotFound + "/" + numQueries);
            }
        }
    }

    private void addVirtualNodesAndEdges(Random rnd, QueryGraph queryGraph, QueryGraph chQueryGraph) {
        BBox bbox = graph.getBounds();
        int numVirtualNodes = 20;
        int count = 0;
        List<QueryResult> qrs = new ArrayList<>(numVirtualNodes);
        while (qrs.size() < numVirtualNodes) {
            if (count > numVirtualNodes * 100) {
                throw new IllegalArgumentException("Could not create enough virtual edges");
            }
            QueryResult qr = findQueryResult(rnd, bbox);
            if (qr.getSnappedPosition().equals(QueryResult.Position.EDGE)) {
                qrs.add(qr);
            }
            count++;
        }
        queryGraph.lookup(qrs);
        chQueryGraph.lookup(qrs);
    }

    private QueryResult findQueryResult(Random rnd, BBox bbox) {
        return locationIndex.findClosest(
                randomDoubleInRange(rnd, bbox.minLat, bbox.maxLat),
                randomDoubleInRange(rnd, bbox.minLon, bbox.maxLon),
                EdgeFilter.ALL_EDGES
        );
    }

    private double randomDoubleInRange(Random rnd, double min, double max) {
        return min + rnd.nextDouble() * (max - min);
    }

    private void buildRandomGraph(Random random, int numNodes, double meanDegree, boolean allowLoops, boolean allowZeroDistance, double pBothDir) {
        for (int i = 0; i < numNodes; ++i) {
            double lat = 49.4 + (random.nextDouble() * 0.0001);
            double lon = 9.7 + (random.nextDouble() * 0.0001);
            graph.getNodeAccess().setNode(i, lat, lon);
        }
        double minDist = Double.MAX_VALUE;
        double maxDist = Double.MIN_VALUE;
        int numEdges = (int) (0.5 * meanDegree * numNodes);
        for (int i = 0; i < numEdges; ++i) {
            int from = random.nextInt(numNodes);
            int to = random.nextInt(numNodes);
            if (!allowLoops && from == to) {
                continue;
            }
            double distance = GHUtility.getDistance(from, to, graph.getNodeAccess());
            if (!allowZeroDistance) {
                distance = Math.max(0.001, distance);
            }
            // add some random offset for most cases, but also allow duplicate edges with same weight
            if (random.nextDouble() < 0.8)
                distance += random.nextDouble() * distance * 0.01;
            minDist = Math.min(minDist, distance);
            maxDist = Math.max(maxDist, distance);
            // using bidirectional edges will increase mean degree of graph above given value
            boolean bothDirections = random.nextDouble() < pBothDir;
            EdgeIteratorState edge = graph.edge(from, to, distance, bothDirections);
            double fwdSpeed = 10 + random.nextDouble() * 120;
            double bwdSpeed = 10 + random.nextDouble() * 120;
            DecimalEncodedValue speedEnc = encoder.getAverageSpeedEnc();
            edge.set(speedEnc, fwdSpeed);
            edge.setReverse(speedEnc, bwdSpeed);
        }
    }
}

