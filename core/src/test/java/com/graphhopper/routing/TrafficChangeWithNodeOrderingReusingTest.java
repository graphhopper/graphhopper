package com.graphhopper.routing;

import com.graphhopper.json.LeafStatement;
import com.graphhopper.json.Statement;
import com.graphhopper.reader.osm.OSMReader;
import com.graphhopper.routing.ch.CHRoutingAlgorithmFactory;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.VehicleAccess;
import com.graphhopper.routing.ev.VehicleSpeed;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.OSMParsers;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.util.parsers.CarAverageSpeedParser;
import com.graphhopper.routing.weighting.AbstractWeighting;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.routing.weighting.custom.CustomModelParser;
import com.graphhopper.storage.*;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.MiniPerfTest;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Random;
import java.util.stream.Stream;

import static java.lang.System.nanoTime;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests CH contraction and query performance when re-using the node ordering after random changes
 * have been applied to the edge weights (like when considering traffic).
 */
@Disabled("for performance testing only")
public class TrafficChangeWithNodeOrderingReusingTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TrafficChangeWithNodeOrderingReusingTest.class);
    // make sure to increase xmx/xms for the JVM created by the surefire plugin in parent pom.xml when using bigger maps
    private static final String OSM_FILE = "../core/files/monaco.osm.gz";

    private static class Fixture {
        private final int maxDeviationPercentage;
        private final BaseGraph graph;
        private final EncodingManager em;
        private final OSMParsers osmParsers;
        private final CHConfig baseCHConfig;
        private final CHConfig trafficCHConfig;

        public Fixture(int maxDeviationPercentage) {
            this.maxDeviationPercentage = maxDeviationPercentage;
            BooleanEncodedValue accessEnc = VehicleAccess.create("car");
            DecimalEncodedValue speedEnc = VehicleSpeed.create("car", 5, 5, false);
            em = EncodingManager.start().add(accessEnc).add(speedEnc).build();
            CarAverageSpeedParser carParser = new CarAverageSpeedParser(em);
            osmParsers = new OSMParsers()
                    .addWayTagParser(carParser);
            baseCHConfig = CHConfig.nodeBased("base", CustomModelParser.createWeighting(em, TurnCostProvider.NO_TURN_COST_PROVIDER,
                    new CustomModel()
                            .addToPriority(LeafStatement.If("!car_access", Statement.Op.MULTIPLY, "0"))
                            .addToSpeed(LeafStatement.If("true", Statement.Op.LIMIT, "car_average_speed")
                    )
            ));
            trafficCHConfig = CHConfig.nodeBased("traffic", new RandomDeviationWeighting(baseCHConfig.getWeighting(), accessEnc, speedEnc, maxDeviationPercentage));
            graph = new BaseGraph.Builder(em).create();
        }

        @Override
        public String toString() {
            return "maxDeviationPercentage=" + maxDeviationPercentage;
        }
    }

    private static class FixtureProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                    new Fixture(0),
                    new Fixture(1),
                    new Fixture(5),
                    new Fixture(10),
                    new Fixture(50)
            ).map(Arguments::of);
        }
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testPerformanceForRandomTrafficChange(Fixture f) throws IOException {
        final long seed = 2139960664L;
        final int numQueries = 50_000;

        LOGGER.info("Running performance test, max deviation percentage: " + f.maxDeviationPercentage);
        // read osm
        OSMReader reader = new OSMReader(f.graph, f.osmParsers, new OSMReaderConfig());
        reader.setFile(new File(OSM_FILE));
        reader.readGraph();
        f.graph.freeze();

        // create CH
        PrepareContractionHierarchies basePch = PrepareContractionHierarchies.fromGraph(f.graph, f.baseCHConfig);
        PrepareContractionHierarchies.Result res = basePch.doWork();

        // check correctness & performance
        checkCorrectness(f.graph, res.getCHStorage(), f.baseCHConfig, seed, 100);
        runPerformanceTest(f.graph, res.getCHStorage(), f.baseCHConfig, seed, numQueries);

        // now we re-use the contraction order from the previous contraction and re-run it with the traffic weighting
        PrepareContractionHierarchies trafficPch = PrepareContractionHierarchies.fromGraph(f.graph, f.trafficCHConfig)
                .useFixedNodeOrdering(res.getCHStorage().getNodeOrderingProvider());
        res = trafficPch.doWork();

        // check correctness & performance
        checkCorrectness(f.graph, res.getCHStorage(), f.trafficCHConfig, seed, 100);
        runPerformanceTest(f.graph, res.getCHStorage(), f.trafficCHConfig, seed, numQueries);
    }

    private static void checkCorrectness(BaseGraph graph, CHStorage chStorage, CHConfig chConfig, long seed, long numQueries) {
        LOGGER.info("checking correctness");
        RoutingCHGraph chGraph = RoutingCHGraphImpl.fromGraph(graph, chStorage, chConfig);
        Random rnd = new Random(seed);
        int numFails = 0;
        for (int i = 0; i < numQueries; ++i) {
            Dijkstra dijkstra = new Dijkstra(graph, chConfig.getWeighting(), TraversalMode.NODE_BASED);
            RoutingAlgorithm chAlgo = new CHRoutingAlgorithmFactory(chGraph).createAlgo(new PMap());

            int from = rnd.nextInt(graph.getNodes());
            int to = rnd.nextInt(graph.getNodes());
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

    private static void runPerformanceTest(final BaseGraph graph, CHStorage chStorage, CHConfig chConfig, long seed, final int iterations) {
        final int numNodes = graph.getNodes();
        RoutingCHGraph chGraph = RoutingCHGraphImpl.fromGraph(graph, chStorage, chConfig);
        final Random random = new Random(seed);

        LOGGER.info("Running performance test, seed = {}", seed);
        final double[] distAndWeight = {0.0, 0.0};
        MiniPerfTest performanceTest = new MiniPerfTest();
        performanceTest.setIterations(iterations).start(new MiniPerfTest.Task() {
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
                RoutingAlgorithm algo = new CHRoutingAlgorithmFactory(chGraph).createAlgo(new PMap());
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
        });
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

        public RandomDeviationWeighting(Weighting baseWeighting, BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc, double maxDeviationPercentage) {
            super(accessEnc, speedEnc, TurnCostProvider.NO_TURN_COST_PROVIDER);
            this.baseWeighting = baseWeighting;
            this.maxDeviationPercentage = maxDeviationPercentage;
        }

        @Override
        public double calcMinWeightPerDistance() {
            // left as is, ok for now, but do not use with astar, at least as long as deviations can be negative!!
            return this.baseWeighting.calcMinWeightPerDistance();
        }

        @Override
        public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
            double baseWeight = baseWeighting.calcEdgeWeight(edgeState, reverse);
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
