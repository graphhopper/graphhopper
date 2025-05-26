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
package com.graphhopper.routing.subnetwork;

import com.carrotsearch.hppc.BitSet;
import com.carrotsearch.hppc.BitSetIterator;
import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import com.graphhopper.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.graphhopper.util.GHUtility.getEdgeFromEdgeKey;

/**
 * Detects and marks 'subnetworks', i.e., parts of the road network that (for the given weighting) aren't
 * strongly connected to the rest of the network. This excludes subnetworks of a certain size (min_network_size).
 *
 * @author Peter Karich
 * @author easbar
 */
public class PrepareRoutingSubnetworks {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final BaseGraph graph;
    private final List<PrepareJob> prepareJobs;
    private int minNetworkSize = 200;
    private int threads = 1;

    public PrepareRoutingSubnetworks(BaseGraph graph, List<PrepareJob> prepareJobs) {
        this.graph = graph;
        this.prepareJobs = prepareJobs;
    }

    /**
     * All components of the graph with less than 2*{@link #minNetworkSize} directed edges (edge keys) will be marked
     * as subnetworks. The biggest component will never be marked as subnetwork, even when it is below this size.
     */
    public PrepareRoutingSubnetworks setMinNetworkSize(int minNetworkSize) {
        this.minNetworkSize = minNetworkSize;
        return this;
    }

    public PrepareRoutingSubnetworks setThreads(int threads) {
        this.threads = threads;
        return this;
    }

    /**
     * Finds and marks all subnetworks according to {@link #setMinNetworkSize(int)}
     *
     * @return the total number of marked edges
     */
    public int doWork() {
        if (minNetworkSize <= 0) {
            logger.info("Skipping subnetwork search: prepare.min_network_size: " + minNetworkSize);
            return 0;
        }
        StopWatch sw = new StopWatch().start();
        logger.info("Start marking subnetworks, prepare.min_network_size: " + minNetworkSize + ", threads: " + threads + ", nodes: " +
                Helper.nf(graph.getNodes()) + ", edges: " + Helper.nf(graph.getEdges()) + ", jobs: " + prepareJobs + ", " + Helper.getMemInfo());
        AtomicInteger total = new AtomicInteger(0);
        List<BitSet> flags = Stream.generate(() -> new BitSet(graph.getEdges())).limit(prepareJobs.size()).toList();
        Stream<Runnable> runnables = IntStream.range(0, prepareJobs.size()).mapToObj(i -> () -> {
            PrepareJob job = prepareJobs.get(i);
            total.addAndGet(setSubnetworks(job.weighting, job.subnetworkEnc.getName().replaceAll("_subnetwork", ""), flags.get(i)));
        });
        GHUtility.runConcurrently(runnables, threads);
        AllEdgesIterator iter = graph.getAllEdges();
        while (iter.next()) {
            for (int i = 0; i < prepareJobs.size(); i++) {
                PrepareJob prepareJob = prepareJobs.get(i);
                iter.set(prepareJob.subnetworkEnc, flags.get(i).get(iter.getEdge()));
            }
        }
        logger.info("Finished finding and marking subnetworks for " + prepareJobs.size() + " jobs, took: " + sw.stop().getSeconds() + "s, " + Helper.getMemInfo());
        return total.get();
    }

    private int setSubnetworks(Weighting weighting, String jobName, BitSet subnetworkFlags) {
        if (minNetworkSize <= 0)
            throw new IllegalStateException("minNetworkSize: " + minNetworkSize);
        if (!subnetworkFlags.isEmpty())
            throw new IllegalArgumentException("Expected an empty set");
        // partition graph into strongly connected components using Tarjan's algorithm
        StopWatch sw = new StopWatch().start();
        EdgeBasedTarjanSCC.ConnectedComponents ccs = EdgeBasedTarjanSCC.findComponents(graph,
                (prev, edge) -> Double.isFinite(GHUtility.calcWeightWithTurnWeight(weighting, edge, false, prev)),
                false);
        List<IntArrayList> components = ccs.getComponents();
        BitSet subnetworkEdgeKeys = ccs.getSingleEdgeComponents();
        long numSingleEdgeComponents = subnetworkEdgeKeys.cardinality();
        logger.info(jobName + " - Found " + ccs.getTotalComponents() + " subnetworks (" + numSingleEdgeComponents + " single edges and "
                + components.size() + " components with more than one edge, total nodes: " + ccs.getEdgeKeys() + "), took: " + sw.stop().getSeconds() + "s");

        final int minNetworkSizeEdgeKeys = 2 * minNetworkSize;

        // make all small components subnetworks, but keep the biggest (even when its smaller than the given min_network_size)
        sw = new StopWatch().start();
        int smallestNonSubnetwork = ccs.getBiggestComponent().size();
        int biggestSubnetwork = 0;
        int subnetworkComponents = 0;

        for (IntArrayList component : components) {
            if (component == ccs.getBiggestComponent())
                continue;
            if (component.size() < minNetworkSizeEdgeKeys) {
                subnetworkComponents++;
                biggestSubnetwork = Math.max(biggestSubnetwork, component.size());
                for (IntCursor cursor : component)
                    // add all edge keys of subnetwork components into the same set
                    subnetworkEdgeKeys.set(cursor.value);
            } else
                smallestNonSubnetwork = Math.min(smallestNonSubnetwork, component.size());
        }

        BitSetIterator iter = subnetworkEdgeKeys.iterator();
        for (int edgeKey = iter.nextSetBit(); edgeKey >= 0; edgeKey = iter.nextSetBit())
            // Only flag edges as subnetworks if both of the two edge keys belong to subnetworks.
            // If only one belongs to the main network(s) we accept it for our undirected snapping.
            if (subnetworkEdgeKeys.get(GHUtility.reverseEdgeKey(edgeKey)))
                subnetworkFlags.set(getEdgeFromEdgeKey(edgeKey));

        int markedEdges = Math.toIntExact(subnetworkFlags.cardinality());
        logger.info(jobName + " - Marked " + subnetworkComponents + " subnetworks (biggest: " + biggestSubnetwork + " edge keys) and " + numSingleEdgeComponents + " single edge keys -> " +
                (components.size() - subnetworkComponents) + " components(s) remain (smallest: " + smallestNonSubnetwork + ", biggest: " + ccs.getBiggestComponent().size() + " edge keys)"
                + ", total marked edges: " + markedEdges + ", took: " + sw.stop().getSeconds() + "s");
        return markedEdges;
    }

    public static class PrepareJob {
        private final BooleanEncodedValue subnetworkEnc;
        private final Weighting weighting;

        public PrepareJob(BooleanEncodedValue subnetworkEnc, Weighting weighting) {
            this.weighting = weighting;
            this.subnetworkEnc = subnetworkEnc;
        }

        @Override
        public String toString() {
            return subnetworkEnc.getName() + "|" + weighting;
        }
    }
}
