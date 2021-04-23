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
import com.graphhopper.routing.util.AccessFilter;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Removes nodes/edges which are not part of the 'main' network(s). I.e. mostly nodes with no edges at all but
 * also small subnetworks which could be bugs in OSM data or 'islands' or indicate otherwise disconnected areas
 * e.g. via barriers or one way problems - see #86. Subnetworks are removed by disabling access to the corresponding
 * edges for a given access encoded value. It is important to search for strongly connected components here (i.e.
 * consider that the graph is directed). For example, small areas like parking lots are sometimes connected to the whole
 * network through a single one-way road (a mapping error) and have to be removed because otherwise the routing fails
 * when starting from such a parking lot.
 *
 * @author Peter Karich
 * @author easbar
 */
public class PrepareRoutingSubnetworks {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final GraphHopperStorage ghStorage;
    private final List<PrepareJob> prepareJobs;
    private int minNetworkSize = 200;

    public PrepareRoutingSubnetworks(GraphHopperStorage ghStorage, List<PrepareJob> prepareJobs) {
        this.ghStorage = ghStorage;
        this.prepareJobs = prepareJobs;
    }

    /**
     * The subnetwork removal removes components with less than {@link #minNetworkSize} nodes from the graph if it is
     * run node-based. For edge-based subnetwork removal it removes components with less than 2*{@link #minNetworkSize}
     * (directed) edges.
     */
    public PrepareRoutingSubnetworks setMinNetworkSize(int minNetworkSize) {
        this.minNetworkSize = minNetworkSize;
        return this;
    }

    public void doWork() {
        if (minNetworkSize <= 0) {
            logger.info("Skipping subnetwork removal: prepare.min_network_size: " + minNetworkSize);
            return;
        }
        StopWatch sw = new StopWatch().start();
        logger.info("Start removing subnetworks, prepare.min_network_size: " + minNetworkSize + ", nodes: " +
                Helper.nf(ghStorage.getNodes()) + ", edges: " + Helper.nf(ghStorage.getEdges()) + ", jobs: " + prepareJobs + ", " + Helper.getMemInfo());
        for (PrepareJob job : prepareJobs) {
            removeSmallSubNetworks(job);
        }
        logger.info("Finished finding and removing subnetworks for " + prepareJobs.size() + " vehicles, took: " + sw.stop().getSeconds() + "s, " + Helper.getMemInfo());
    }

    /**
     * The biggest component is always kept regardless of its size. For edge-based routing with turn restrictions the
     * subnetwork search has to consider the turn restrictions as well to make sure components that are not reachable
     * due to turn restrictions are also removed.
     *
     * @return number of removed edges
     */
    int removeSmallSubNetworks(PrepareJob job) {
        return removeSmallSubNetworksEdgeBased(job.name, job.accessEnc, job.turnCostProvider);
    }

    private int removeSmallSubNetworksEdgeBased(String jobName, BooleanEncodedValue accessEnc, TurnCostProvider turnCostProvider) {
        // partition graph into strongly connected components using Tarjan's algorithm
        StopWatch sw = new StopWatch().start();
        EdgeBasedTarjanSCC.ConnectedComponents ccs = EdgeBasedTarjanSCC.findComponents(ghStorage, AccessFilter.outEdges(accessEnc), turnCostProvider, false);
        List<IntArrayList> components = ccs.getComponents();
        BitSet singleEdgeComponents = ccs.getSingleEdgeComponents();
        long numSingleEdgeComponents = singleEdgeComponents.cardinality();
        logger.info(jobName + " - Found " + ccs.getTotalComponents() + " subnetworks (" + numSingleEdgeComponents + " single edges and "
                + components.size() + " components with more than one edge, total nodes: " + ccs.getEdgeKeys() + "), took: " + sw.stop().getSeconds() + "s");

        final int minNetworkSizeEdgeKeys = 2 * minNetworkSize;

        // remove all small networks, but keep the biggest (even when its smaller than the given min_network_size)
        sw = new StopWatch().start();
        int removedComponents = 0;
        int removedEdgeKeys = 0;
        int smallestRemaining = ccs.getBiggestComponent().size();
        int biggestRemoved = 0;

        for (IntArrayList component : components) {
            if (component == ccs.getBiggestComponent())
                continue;

            if (component.size() < minNetworkSizeEdgeKeys) {
                for (IntCursor cursor : component) {
                    removedEdgeKeys += removeEdgeWithKey(cursor.value, accessEnc);
                }
                removedComponents++;
                biggestRemoved = Math.max(biggestRemoved, component.size());
            } else {
                smallestRemaining = Math.min(smallestRemaining, component.size());
            }
        }

        if (minNetworkSizeEdgeKeys > 0) {
            BitSetIterator iter = singleEdgeComponents.iterator();
            for (int edgeKey = iter.nextSetBit(); edgeKey >= 0; edgeKey = iter.nextSetBit()) {
                removedEdgeKeys += removeEdgeWithKey(edgeKey, accessEnc);
                removedComponents++;
                biggestRemoved = Math.max(biggestRemoved, 1);
            }
        } else if (numSingleEdgeComponents > 0) {
            smallestRemaining = Math.min(smallestRemaining, 1);
        }

        int allowedRemoved = ghStorage.getEdges() / 2;
        if (removedEdgeKeys / 2 > allowedRemoved)
            throw new IllegalStateException("Too many total (directed) edges were removed: " + removedEdgeKeys + " out of " + (2 * ghStorage.getEdges()) + "\n" +
                    "The maximum number of removed edges is: " + (2 * allowedRemoved));

        logger.info(jobName + " - Removed " + removedComponents + " subnetworks (biggest removed: " + biggestRemoved + " edges) -> " +
                (ccs.getTotalComponents() - removedComponents) + " subnetwork(s) left (smallest: " + smallestRemaining + ", biggest: " + ccs.getBiggestComponent().size() + " edges)"
                + ", total removed edges: " + removedEdgeKeys + ", took: " + sw.stop().getSeconds() + "s");
        return removedEdgeKeys;
    }

    private int removeEdgeWithKey(int edgeKey, BooleanEncodedValue accessEnc) {
        int edgeId = EdgeBasedTarjanSCC.getEdgeFromKey(edgeKey);
        EdgeIteratorState edge = ghStorage.getEdgeIteratorState(edgeId, Integer.MIN_VALUE);
        if (edgeKey % 2 == 0 && edge.get(accessEnc)) {
            edge.set(accessEnc, false);
            return 1;
        }
        if (edgeKey % 2 != 0 && edge.getReverse(accessEnc)) {
            edge.setReverse(accessEnc, false);
            return 1;
        }
        return 0;
    }

    /**
     * This method checks if the node is removed or inaccessible for ALL encoders.
     *
     * @return true if no edges are reachable from the specified nodeIndex for any flag encoder.
     */
    boolean detectNodeRemovedForAllEncoders(EdgeExplorer edgeExplorerAllEdges, int nodeIndex) {
        // we could implement a 'fast check' for several previously marked removed nodes via GHBitSet 
        // removedNodesPerVehicle. The problem is that we would need long-indices but BitSet only supports int (due to nodeIndex*numberOfEncoders)

        List<BooleanEncodedValue> accessEncList = new ArrayList<>();
        for (PrepareJob job : prepareJobs) {
            accessEncList.add(job.accessEnc);
        }
        // if no edges are reachable return true
        EdgeIterator iter = edgeExplorerAllEdges.setBaseNode(nodeIndex);
        while (iter.next()) {
            // if at least one encoder allows one direction return false
            for (BooleanEncodedValue accessEnc : accessEncList) {
                if (iter.get(accessEnc) || iter.getReverse(accessEnc))
                    return false;
            }
        }

        return true;
    }

    public static class PrepareJob {
        private final String name;
        private final BooleanEncodedValue accessEnc;
        private final TurnCostProvider turnCostProvider;

        public PrepareJob(String name, BooleanEncodedValue accessEnc, TurnCostProvider turnCostProvider) {
            this.name = name;
            this.accessEnc = accessEnc;
            this.turnCostProvider = turnCostProvider;
        }

        @Override
        public String toString() {
            return name + "|" + (turnCostProvider == null ? "node-based" : "edge-based");
        }
    }
}
