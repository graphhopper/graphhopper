package com.graphhopper.routing;

import com.graphhopper.reader.osm.OSMReader;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.AbstractWeighting;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.util.CHEdgeIteratorState;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.MiniPerfTest;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

import static java.lang.System.nanoTime;
import static org.junit.Assert.assertEquals;
import static org.junit.runners.Parameterized.Parameters;

/**
 * Tests CH contraction and query performance when re-using the node ordering after random changes
 * have been applied to the edge weights (like when considering traffic).
 */
@Ignore("for performance testing only")
@RunWith(Parameterized.class)
public class TrafficChangeWithNodeOrderingReusingTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TrafficChangeWithNodeOrderingReusingTest.class);
    // make sure to increase xmx/xms for the JVM created by the surefire plugin in parent pom.xml
    private static final String OSM_FILE = "../local/maps/berlin-latest.osm.pbf";

    private final Weighting baseWeighting;
    private final Weighting trafficWeighting;
    private final GraphHopperStorage ghStorage;
    private final CHGraphImpl baseCHGraph;
    private final CHGraphImpl trafficCHGraph;
    private int maxDeviationPercentage;

    @Parameters(name = "maxDeviationPercentage = {0}")
    public static Object[] data() {
        return new Object[]{0, 1, 5, 10, 50};
    }

    public TrafficChangeWithNodeOrderingReusingTest(int maxDeviationPercentage) {
        this.maxDeviationPercentage = maxDeviationPercentage;
        FlagEncoder encoder = new CarFlagEncoder();
        EncodingManager em = EncodingManager.create(encoder);
        baseWeighting = new FastestWeighting(encoder);
        trafficWeighting = new RandomDeviationWeighting(baseWeighting, maxDeviationPercentage);
        Directory dir = new RAMDirectory("traffic-change-test");
        ghStorage = new GraphHopperStorage(Arrays.asList(baseWeighting, trafficWeighting), dir, em, false, new GraphExtension.NoOpExtension());
        baseCHGraph = ghStorage.getGraph(CHGraphImpl.class, baseWeighting);
        trafficCHGraph = ghStorage.getGraph(CHGraphImpl.class, trafficWeighting);
    }

    @Test
    public void testPerformanceForRandomTrafficChange() throws IOException {
        final long seed = 2139960664L;
        final int numQueries = 50_000;

        LOGGER.info("Running performance test, max deviation percentage: " + this.maxDeviationPercentage);
        // read osm
        OSMReader reader = new OSMReader(ghStorage);
        reader.setFile(new File(OSM_FILE));
        reader.setCreateStorage(true);
        reader.readGraph();
        ghStorage.freeze();

        // create CH
        PrepareContractionHierarchies basePch = new PrepareContractionHierarchies(baseCHGraph, baseWeighting, TraversalMode.NODE_BASED);
        basePch.doWork();

        // check correctness & performance
        checkCorrectness(ghStorage, baseCHGraph, basePch, seed, 100);
        runPerformanceTest(ghStorage, baseCHGraph, basePch, seed, numQueries);

        // now we re-use the contraction order from the previous contraction and re-run it with the traffic weighting
        PrepareContractionHierarchies trafficPch = new PrepareContractionHierarchies(trafficCHGraph, trafficWeighting, TraversalMode.NODE_BASED)
                .useFixedNodeOrdering(baseCHGraph.getNodeOrderingProvider());
        trafficPch.doWork();

        // check correctness & performance
        checkCorrectness(ghStorage, trafficCHGraph, trafficPch, seed, 100);
        runPerformanceTest(ghStorage, trafficCHGraph, trafficPch, seed, numQueries);
    }

    private static void checkCorrectness(GraphHopperStorage ghStorage, CHGraph chGraph, PrepareContractionHierarchies pch, long seed, long numQueries) {
        LOGGER.info("checking correctness");
        Random rnd = new Random(seed);
        int numFails = 0;
        for (int i = 0; i < numQueries; ++i) {
            Dijkstra dijkstra = new Dijkstra(ghStorage, pch.getWeighting(), TraversalMode.NODE_BASED);
            RoutingAlgorithm chAlgo = pch.createAlgo(chGraph, AlgorithmOptions.start().weighting(pch.getWeighting()).build());

            int from = rnd.nextInt(ghStorage.getNodes());
            int to = rnd.nextInt(ghStorage.getNodes());
            double dijkstraWeight = dijkstra.calcPath(from, to).getWeight();
            double chWeight = chAlgo.calcPath(from, to).getWeight();
            double error = Math.abs(dijkstraWeight - chWeight);
            if (error > 1) {
                System.out.println("failure from " + from + " to " + to + " dijkstra: " + dijkstraWeight + " ch: " + chWeight);
                numFails++;
            }
        }
        LOGGER.info("number of failed queries: " + numFails);
        assertEquals(0, numFails);
    }

    private static void runPerformanceTest(final GraphHopperStorage ghStorage, final CHGraph chGraph, final PrepareContractionHierarchies pch,
                                           long seed, final int iterations) {
        final int numNodes = ghStorage.getNodes();
        final Random random = new Random(seed);

        LOGGER.info("Running performance test, seed = {}", seed);
        final double[] distAndWeight = {0.0, 0.0};
        MiniPerfTest performanceTest = new MiniPerfTest() {
            private long queryTime;

            @Override
            public int doCalc(boolean warmup, int run) {
                if (!warmup && run % 1000 == 0) {
                    LOGGER.debug("Finished {} of {} runs. {}", run, iterations,
                            run > 0 ? String.format(Locale.ROOT, " Time: %6.2fms", queryTime * 1.e-6 / run) : "");
                }
                if (run == iterations - 1) {
                    String avg = fmt(queryTime * 1.e-6 / run);
                    LOGGER.debug("Finished all ({}) runs, avg time: {}ms", iterations, avg);
                }
                int from = random.nextInt(numNodes);
                int to = random.nextInt(numNodes);
                long start = nanoTime();
                RoutingAlgorithm algo = pch.createAlgo(chGraph, AlgorithmOptions.start().weighting(pch.getWeighting()).build());
                Path path = algo.calcPath(from, to);
                if (!warmup && !path.isFound())
                    return 1;

                if (!warmup) {
                    queryTime += nanoTime() - start;
                    double distance = path.getDistance();
                    double weight = path.getWeight();
                    distAndWeight[0] += distance;
                    distAndWeight[1] += weight;
                }
                return 0;
            }
        };
        performanceTest.setIterations(iterations).start();
        if (performanceTest.getDummySum() > 0.5 * iterations) {
            throw new IllegalStateException("too many errors, probably something is wrong");
        }
        LOGGER.info("Total distance: {}, total weight: {}", distAndWeight[0], distAndWeight[1]);
        LOGGER.info("Average query time: {}ms", performanceTest.getMean());
    }

    private static String fmt(double number) {
        return String.format(Locale.ROOT, "%.2f", number);
    }

    /**
     * Wraps another weighting and applies random weight deviations to it.
     * Do not use with AStar/Landmarks!
     */
    private static class RandomDeviationWeighting extends AbstractWeighting {
        private final Weighting baseWeighting;
        private final double maxDeviationPercentage;

        public RandomDeviationWeighting(Weighting baseWeighting, double maxDeviationPercentage) {
            super(baseWeighting.getFlagEncoder());
            this.baseWeighting = baseWeighting;
            this.maxDeviationPercentage = maxDeviationPercentage;
        }

        @Override
        public double getMinWeight(double distance) {
            // left as is, ok for now, but do not use with astar, at least as long as deviations can be negative!!
            return this.baseWeighting.getMinWeight(distance);
        }

        @Override
        public double calcWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
            double baseWeight = this.baseWeighting.calcWeight(edgeState, reverse, prevOrNextEdgeId);
            if (edgeState instanceof CHEdgeIteratorState) {
                // important! we may not change weights of shortcuts (the deviations are already included in their weight)
                if (((CHEdgeIteratorState) edgeState).isShortcut()) {
                    return baseWeight;
                }
            }
            if (Double.isInfinite(baseWeight)) {
                // we are not touching this, might happen when speed is 0 ?
                return baseWeight;
            }
            // apply a random (but deterministic) weight deviation - deviation may not depend on reverse flag!
            long seed = edgeState.getEdge();
            Random rnd = new Random(seed);
            double deviation = 2 * (rnd.nextDouble() - 0.5) * baseWeight * maxDeviationPercentage / 100;
            double result = baseWeight + deviation;
            if (result < 0) {
                throw new IllegalStateException("negative weights are not allowed: " + result);
            }
            return result;
        }

        @Override
        public String getName() {
            return "random_deviation";
        }
    }
}
