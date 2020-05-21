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
import com.carrotsearch.hppc.IntIndexedContainer;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.util.DefaultEdgeFilter;
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
        logger.info("Start removing subnetworks (prepare.min_network_size:" + minNetworkSize + ") " + Helper.getMemInfo());
        logger.info("Subnetwork removal jobs: " + prepareJobs);
        logger.info("Graph nodes: " + Helper.nf(ghStorage.getNodes()));
        logger.info("Graph edges: " + Helper.nf(ghStorage.getEdges()));
        for (PrepareJob job : prepareJobs) {
            logger.info("--- vehicle: '" + job.name + "'");
            removeSmallSubNetworks(job.accessEnc, job.turnCostProvider);
        }
        markNodesRemovedIfUnreachable();
        optimize();
        logger.info("Finished finding and removing subnetworks for " + prepareJobs.size() + " vehicles, took: " + sw.stop().getSeconds() + "s, " + Helper.getMemInfo());
    }

    private void optimize() {
        StopWatch sw = new StopWatch().start();
        ghStorage.optimize();
        logger.info("Optimized storage after subnetwork removal, took: " + sw.stop().getSeconds() + "s," + Helper.getMemInfo());
    }

    /**
     * The biggest component is always kept regardless of its size. For edge-based routing with turn restrictions the
     * subnetwork search has to consider the turn restrictions as well to make sure components that are not reachable
     * due to turn restrictions are also removed.
     *
     * @return number of removed edges
     */
    int removeSmallSubNetworks(BooleanEncodedValue accessEnc, TurnCostProvider turnCostProvider) {
        if (turnCostProvider == null)
            return removeSmallSubNetworksNodeBased(accessEnc);
        else
            return removeSmallSubNetworksEdgeBased(accessEnc, turnCostProvider);
    }

    private int removeSmallSubNetworksNodeBased(BooleanEncodedValue accessEnc) {
        // partition graph into strongly connected components using Tarjan's algorithm
        StopWatch sw = new StopWatch().start();
        TarjanSCC tarjan = new TarjanSCC(ghStorage, accessEnc, false);
        TarjanSCC.ConnectedComponents ccs = tarjan.findComponents();
        List<IntArrayList> components = ccs.getComponents();
        BitSet singleNodeComponents = ccs.getSingleNodeComponents();
        long numSingleNodeComponents = singleNodeComponents.cardinality();
        logger.info("Found " + ccs.getTotalComponents() + " subnetworks (" + numSingleNodeComponents + " single nodes and "
                + components.size() + " components with more than one node, total nodes: " + ccs.getNodes() + "), took: " + sw.stop().getSeconds() + "s");

        // remove all small networks, but keep the biggest (even when its smaller than the given min_network_size)
        sw = new StopWatch().start();
        int removedComponents = 0;
        int removedEdges = 0;
        int smallestRemaining = ccs.getBiggestComponent().size();
        int biggestRemoved = 0;
        EdgeExplorer explorer = ghStorage.createEdgeExplorer(DefaultEdgeFilter.allEdges(accessEnc));
        for (IntArrayList component : components) {
            if (component == ccs.getBiggestComponent())
                continue;

            if (component.size() < minNetworkSize) {
                removedEdges += blockEdgesForComponent(explorer, accessEnc, component);
                removedComponents++;
                biggestRemoved = Math.max(biggestRemoved, component.size());
            } else {
                smallestRemaining = Math.min(smallestRemaining, component.size());
            }
        }

        if (minNetworkSize > 0) {
            BitSetIterator iter = singleNodeComponents.iterator();
            for (int node = iter.nextSetBit(); node >= 0; node = iter.nextSetBit()) {
                removedEdges += blockEdgesForNode(explorer, accessEnc, node);
                removedComponents++;
                biggestRemoved = Math.max(biggestRemoved, 1);
            }
        } else if (numSingleNodeComponents > 0) {
            smallestRemaining = Math.min(smallestRemaining, 1);
        }

        int allowedRemoved = ghStorage.getEdges() / 2;
        if (removedEdges > allowedRemoved)
            throw new IllegalStateException("Too many total edges were removed: " + removedEdges + " out of " + ghStorage.getEdges() + "\n" +
                    "The maximum number of removed edges is: " + allowedRemoved);

        logger.info("Removed " + removedComponents + " subnetworks (biggest removed: " + biggestRemoved + " nodes) -> " +
                (ccs.getTotalComponents() - removedComponents) + " subnetwork(s) left (smallest: " + smallestRemaining + ", biggest: " + ccs.getBiggestComponent().size() + " nodes)"
                + ", total removed edges: " + removedEdges + ", took: " + sw.stop().getSeconds() + "s");
        return removedEdges;
    }

    /**
     * Makes all edges of the given component (the given set of node ids) inaccessible for the given access encoded value.
     */
    int blockEdgesForComponent(EdgeExplorer explorer, BooleanEncodedValue accessEnc, IntIndexedContainer component) {
        int removedEdges = 0;
        for (int i = 0; i < component.size(); i++) {
            removedEdges += blockEdgesForNode(explorer, accessEnc, component.get(i));
        }
        return removedEdges;
    }

    private int blockEdgesForNode(EdgeExplorer explorer, BooleanEncodedValue accessEnc, int node) {
        int removedEdges = 0;
        EdgeIterator edge = explorer.setBaseNode(node);
        while (edge.next()) {
            if (!edge.get(accessEnc) && !edge.getReverse(accessEnc))
                continue;
            edge.set(accessEnc, false).setReverse(accessEnc, false);
            removedEdges++;
        }
        return removedEdges;
    }

    private int removeSmallSubNetworksEdgeBased(BooleanEncodedValue accessEnc, TurnCostProvider turnCostProvider) {
        // partition graph into strongly connected components using Tarjan's algorithm
        StopWatch sw = new StopWatch().start();
        EdgeBasedTarjanSCC tarjan = new EdgeBasedTarjanSCC(ghStorage, accessEnc, turnCostProvider, false);
        EdgeBasedTarjanSCC.ConnectedComponents ccs = tarjan.findComponents();
        List<IntArrayList> components = ccs.getComponents();
        BitSet singleEdgeComponents = ccs.getSingleEdgeComponents();
        long numSingleEdgeComponents = singleEdgeComponents.cardinality();
        logger.info("Found " + ccs.getTotalComponents() + " subnetworks (" + numSingleEdgeComponents + " single edges and "
                + components.size() + " components with more than one edge, total nodes: " + ccs.getEdgeKeys() + "), took: " + sw.stop().getSeconds() + "s");

        // n edge-keys roughly equal n/2 edges and components with n/2 edges approximately have n/2 nodes
        // we could actually count the nodes to make this more consistent, but is it really needed?
        final int minNetworkSizeEdges = 2 * minNetworkSize;

        // remove all small networks, but keep the biggest (even when its smaller than the given min_network_size)
        sw = new StopWatch().start();
        int removedComponents = 0;
        int removedEdgeKeys = 0;
        int smallestRemaining = ccs.getBiggestComponent().size();
        int biggestRemoved = 0;

        for (IntArrayList component : components) {
            if (component == ccs.getBiggestComponent())
                continue;

            if (component.size() < minNetworkSizeEdges) {
                for (IntCursor cursor : component) {
                    removedEdgeKeys += removeEdgeWithKey(cursor.value, accessEnc);
                }
                removedComponents++;
                biggestRemoved = Math.max(biggestRemoved, component.size());
            } else {
                smallestRemaining = Math.min(smallestRemaining, component.size());
            }
        }

        if (minNetworkSizeEdges > 0) {
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

        logger.info("Removed " + removedComponents + " subnetworks (biggest removed: " + biggestRemoved + " edges) -> " +
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
     * Removes nodes if all edges are not accessible. I.e. removes zero degree nodes. Note that so far we are not
     * removing any edges entirely from the graph (we could probably do this for edges that are blocked for *all*
     * vehicles.
     */
    void markNodesRemovedIfUnreachable() {
        EdgeExplorer edgeExplorer = ghStorage.createEdgeExplorer();
        int removedNodes = 0;
        for (int nodeIndex = 0; nodeIndex < ghStorage.getNodes(); nodeIndex++) {
            if (detectNodeRemovedForAllEncoders(edgeExplorer, nodeIndex)) {
                ghStorage.markNodeRemoved(nodeIndex);
                removedNodes++;
            }
        }
        logger.info("Removed " + removedNodes + " nodes from the graph as they aren't used by any vehicle after removing subnetworks");
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
