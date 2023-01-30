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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.graphhopper.util.GHUtility.getEdgeFromEdgeKey;

/**
 * Detects and marks 'subnetworks' with a dedicated subnetwork encoded value. Subnetworks are parts of the road network
 * that are not connected to the rest of the network and that are below a certain size. These can be isolated nodes with
 * no edges at all, but also small subnetworks which could be bugs in OSM data or 'islands' that are separated from
 * the rest of the network because of some missing link, barrier or some closed road for example.
 * <p>
 * Sometimes there are also subnetworks that can be reached from the main network but not the other way around (or the
 * opposite). For example this can be parking lots that can only be accessed by a single one-way road (a mapping error).
 * These are called 'one-way subnetworks' and are marked using the same subnetwork encoded value, see #86. To find such
 * one-way subnetworks it is important to search for strongly connected components on the directed graph and not do a
 * simple connectivity check for one direction.
 * <p>
 * Note that it depends on the weighting whether or not edges belong to a subnetwork or not. For example if a weighting
 * 'closes' a bridge to an island the island might become a subnetwork, but if the bridge was open it would belong to
 * the main network. There can even be subnetworks that are due to turn restrictions.
 * <p>
 * We always run an edge-based connected component search, because this way we retrieve the edges (not the nodes) that
 * belong to each component and can include turn restrictions as well. Node-based component search is faster, but since
 * the subnetwork search goes relatively fast anyway using it has no real benefit.
 *
 * @author Peter Karich
 * @author easbar
 */
public class PrepareRoutingSubnetworks {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final BaseGraph graph;
    private final List<PrepareJob> prepareJobs;
    private int minNetworkSize = 200;

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
        logger.info("Start marking subnetworks, prepare.min_network_size: " + minNetworkSize + ", nodes: " +
                Helper.nf(graph.getNodes()) + ", edges: " + Helper.nf(graph.getEdges()) + ", jobs: " + prepareJobs + ", " + Helper.getMemInfo());
        int total = 0;
        List<BitSet> flags = Stream.generate(() -> new BitSet(graph.getEdges())).limit(prepareJobs.size()).collect(Collectors.toList());
        for (int i = 0; i < prepareJobs.size(); i++) {
            PrepareJob job = prepareJobs.get(i);
            total += setSubnetworks(job.weighting, job.subnetworkEnc.getName().replaceAll("_subnetwork", ""), flags.get(i));
        }
        AllEdgesIterator iter = graph.getAllEdges();
        while (iter.next()) {
            for (int i = 0; i < prepareJobs.size(); i++) {
                PrepareJob prepareJob = prepareJobs.get(i);
                iter.set(prepareJob.subnetworkEnc, flags.get(i).get(iter.getEdge()));
            }
        }
        logger.info("Finished finding and marking subnetworks for " + prepareJobs.size() + " jobs, took: " + sw.stop().getSeconds() + "s, " + Helper.getMemInfo());
        return total;
    }

    private int setSubnetworks(Weighting weighting, String jobName, BitSet subnetworkFlags) {
        // partition graph into strongly connected components using Tarjan's algorithm
        StopWatch sw = new StopWatch().start();
        EdgeBasedTarjanSCC.ConnectedComponents ccs = EdgeBasedTarjanSCC.findComponents(graph,
                (prev, edge) -> Double.isFinite(GHUtility.calcWeightWithTurnWeightWithAccess(weighting, edge, false, prev)),
                false);
        List<IntArrayList> components = ccs.getComponents();
        BitSet singleEdgeComponents = ccs.getSingleEdgeComponents();
        long numSingleEdgeComponents = singleEdgeComponents.cardinality();
        logger.info(jobName + " - Found " + ccs.getTotalComponents() + " subnetworks (" + numSingleEdgeComponents + " single edges and "
                + components.size() + " components with more than one edge, total nodes: " + ccs.getEdgeKeys() + "), took: " + sw.stop().getSeconds() + "s");

        final int minNetworkSizeEdgeKeys = 2 * minNetworkSize;

        // make all small components subnetworks, but keep the biggest (even when its smaller than the given min_network_size)
        sw = new StopWatch().start();
        int subnetworks = 0;
        int markedEdges = 0;
        int smallestNonSubnetwork = ccs.getBiggestComponent().size();
        int biggestSubnetwork = 0;

        for (IntArrayList component : components) {
            if (component == ccs.getBiggestComponent())
                continue;

            if (component.size() < minNetworkSizeEdgeKeys) {
                for (IntCursor cursor : component)
                    markedEdges += setSubnetworkEdge(cursor.value, weighting, subnetworkFlags);
                subnetworks++;
                biggestSubnetwork = Math.max(biggestSubnetwork, component.size());
            } else {
                smallestNonSubnetwork = Math.min(smallestNonSubnetwork, component.size());
            }
        }

        if (minNetworkSizeEdgeKeys > 0) {
            BitSetIterator iter = singleEdgeComponents.iterator();
            for (int edgeKey = iter.nextSetBit(); edgeKey >= 0; edgeKey = iter.nextSetBit()) {
                markedEdges += setSubnetworkEdge(edgeKey, weighting, subnetworkFlags);
                subnetworks++;
                biggestSubnetwork = Math.max(biggestSubnetwork, 1);
            }
        } else if (numSingleEdgeComponents > 0) {
            smallestNonSubnetwork = Math.min(smallestNonSubnetwork, 1);
        }

        int allowedMarked = graph.getEdges() / 2;
        if (markedEdges / 2 > allowedMarked)
            throw new IllegalStateException("Too many total (directed) edges were marked as subnetwork edges: " + markedEdges + " out of " + (2 * graph.getEdges()) + "\n" +
                    "The maximum number of subnetwork edges is: " + (2 * allowedMarked));

        logger.info(jobName + " - Marked " + subnetworks + " subnetworks (biggest: " + biggestSubnetwork + " edges) -> " +
                (ccs.getTotalComponents() - subnetworks) + " components(s) remain (smallest: " + smallestNonSubnetwork + ", biggest: " + ccs.getBiggestComponent().size() + " edges)"
                + ", total marked edges: " + markedEdges + ", took: " + sw.stop().getSeconds() + "s");
        return markedEdges;
    }

    private int setSubnetworkEdge(int edgeKey, Weighting weighting, BitSet subnetworkFlags) {
        // edges that are not accessible anyway are not marked as subnetworks additionally
        if (!Double.isFinite(weighting.calcEdgeWeightWithAccess(graph.getEdgeIteratorStateForKey(edgeKey), false)))
            return 0;

        // now get edge again but in stored direction so that subnetwork EV is not overwritten (as it is unidirectional)
        int edge = getEdgeFromEdgeKey(edgeKey);
        if (!subnetworkFlags.get(edge)) {
            subnetworkFlags.set(edge);
            return 1;
        } else {
            return 0;
        }
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
