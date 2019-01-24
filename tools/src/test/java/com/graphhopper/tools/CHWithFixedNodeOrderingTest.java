package com.graphhopper.tools;

import com.graphhopper.reader.osm.OSMReader;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.ch.NodeOrderingProvider;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.*;
import com.graphhopper.storage.*;
import com.graphhopper.util.MiniPerfTest;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import static java.lang.System.nanoTime;

// todo: this class is purely experimental at the moment, should not go to master!
public class CHWithFixedNodeOrderingTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(CHWithFixedNodeOrderingTest.class);

    private CarFlagEncoder encoder = new CarFlagEncoder();
    private EncodingManager em = new EncodingManager(encoder);
    private Weighting otherWeighting = new FastestWeighting(encoder);
    private Weighting weighting = new ShortFastestWeighting(encoder, 0.1);
    private Directory dir = new RAMDirectory("my_ch-gh", true);
    private GraphHopperStorage ghStorage = new GraphHopperStorage(Arrays.asList(weighting, otherWeighting), dir, em, false, new GraphExtension.NoOpExtension());
    // we can also get the node ordering with node-based preparation and use it for edge-based CH -> faster preparation but also slower queries
    // private GraphHopperStorage ghStorage = new GraphHopperStorage(Arrays.asList(weighting), Arrays.asList(otherWeighting), dir, em, false, new TurnCostExtension());
    private CHGraphImpl chGraph = ghStorage.getGraph(CHGraphImpl.class);
    private long seed = 123;
    private int iterations = 50_000;

    /**
     * run this first ...
     */
    @Test
    public void createCHStorage() throws IOException {
        // you probably want to adjust JVM memory settings in pom.xml maven-surefire-plugin argline, it overwrites intellij settings!
        parseOSMPrepareCHAndBenchmark("../local/maps/unterfranken-140101.osm.pbf");
    }

    /**
     * ... then this
     */
    @Test
    public void runPreparationWithOtherWeighting() {
        ghStorage.loadExisting();
        CHGraphImpl otherChGraph = ghStorage.getGraph(CHGraphImpl.class, otherWeighting);
        NodeOrderingProvider nodeOrderingProvider = chGraph.getNodeOrderingProvider();
        boolean edgeBased = false;
        TraversalMode traversalMode = edgeBased ? TraversalMode.EDGE_BASED_2DIR : TraversalMode.NODE_BASED;
        PrepareContractionHierarchies pch = new PrepareContractionHierarchies(otherChGraph, otherWeighting, traversalMode)
                .useFixedNodeOrdering(nodeOrderingProvider);
        pch.doWork();
        runPerformanceTest(ghStorage, otherChGraph, pch, seed, iterations);
    }

    private void parseOSMPrepareCHAndBenchmark(String osmFile) throws IOException {
        // read osm
        OSMReader reader = new OSMReader(ghStorage);
        reader.setFile(new File(osmFile));
        reader.setCreateStorage(true);
        reader.readGraph();

        // create CH
        CHGraphImpl chGraph = ghStorage.getGraph(CHGraphImpl.class, weighting);
        PrepareContractionHierarchies pch = new PrepareContractionHierarchies(chGraph, weighting, TraversalMode.NODE_BASED);
        pch.doWork();

        // check performance
        runPerformanceTest(ghStorage, chGraph, pch, seed, iterations);

        // store on disk
        ghStorage.flush();
    }

    private void runPerformanceTest(final GraphHopperStorage ghStorage, final CHGraphImpl chGraph, final PrepareContractionHierarchies pch,
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
                    LOGGER.info("Finished {} of {} runs. {}", run, iterations,
                            run > 0 ? String.format(" Time: %6.2fms", queryTime * 1.e-6 / run) : "");
                }
                if (run == iterations - 1) {
                    String avg = fmt(queryTime * 1.e-6 / run);
                    LOGGER.info("Finished all ({}) runs, avg time: {}ms", iterations, avg);
                }
                int from = random.nextInt(numNodes);
                int to = random.nextInt(numNodes);
                long start = nanoTime();
                RoutingAlgorithm algo = pch.createAlgo(chGraph, AlgorithmOptions.start().weighting(weighting).build());
                Path path = algo.calcPath(from, to);
                if (!warmup && !path.isFound())
                    return 1;
                double distance = path.getDistance();
                double weight = path.getWeight();
                distAndWeight[0] += distance;
                distAndWeight[0] += weight;
                if (!warmup)
                    queryTime += nanoTime() - start;
                return 0;
            }
        };
        performanceTest.setIterations(iterations).start();
        if (performanceTest.getDummySum() > 0.5 * iterations) {
            throw new IllegalStateException("too many errors, probably something is wrong");
        }
        LOGGER.info("Average query time: {}ms", performanceTest.getMean());
    }

    private static String fmt(double number) {
        return String.format("%.2f", number);
    }
}