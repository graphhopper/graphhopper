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
import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntIndexedContainer;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.Helper;
import com.graphhopper.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Removes nodes/edges which are not part of the 'main' network(s). I.e. mostly nodes with no edges at all but
 * also small subnetworks which could be bugs in OSM data or 'islands' or indicate otherwise disconnected areas
 * e.g. via barriers or one way problems - see #86.
 * <p>
 *
 * @author Peter Karich
 * @author easbar
 */
public class PrepareRoutingSubnetworks {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final GraphHopperStorage ghStorage;
    private final List<FlagEncoder> encoders;
    private final List<BooleanEncodedValue> accessEncList;
    private int minNetworkSize = 200;
    private int subnetworks = -1;

    public PrepareRoutingSubnetworks(GraphHopperStorage ghStorage, List<FlagEncoder> encoders) {
        this.ghStorage = ghStorage;
        this.encoders = encoders;
        this.accessEncList = new ArrayList<>();
        for (FlagEncoder flagEncoder : encoders) {
            accessEncList.add(flagEncoder.getAccessEnc());
        }
    }

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
        logger.info("Graph nodes: " + Helper.nf(ghStorage.getNodes()));
        logger.info("Graph edges: " + Helper.nf(ghStorage.getEdges()));
        for (FlagEncoder encoder : encoders) {
            logger.info("--- vehicle: '" + encoder.toString() + "'");
            removeSmallSubNetworks(encoder.getAccessEnc());
        }
        markNodesRemovedIfUnreachable();
        optimize();
        logger.info("Finished finding and removing subnetworks for " + encoders.size() + " vehicles, took: " + sw.stop().getSeconds() + "s, " + Helper.getMemInfo());
    }

    private void optimize() {
        StopWatch sw = new StopWatch().start();
        ghStorage.optimize();
        logger.info("Optimized storage after subnetwork removal, took: " + sw.stop().getSeconds() + "s," + Helper.getMemInfo());
    }

    public int getMaxSubnetworks() {
        return subnetworks;
    }

    /**
     * Removes components with less than {@link #minNetworkSize} nodes from the graph by disabling access to the nodes
     * of the removed components (for the given access encoded value). It is important to search for strongly connected
     * components here (i.e. consider that the graph is directed). For example, small areas like parking lots are
     * sometimes connected to the whole network through a single one-way road. This is clearly a (mapping) error - but
     * it causes the routing to fail when starting from the parking lot (and there is no way out from it).
     * The biggest component is always kept regardless of its size.
     *
     * @return number of removed edges
     */
    int removeSmallSubNetworks(BooleanEncodedValue accessEnc) {
        // partition graph into strongly connected components using Tarjan's algorithm
        StopWatch sw = new StopWatch().start();
        TarjanSCC tarjan = new TarjanSCC(ghStorage, accessEnc, false);
        TarjanSCC.ConnectedComponents ccs = tarjan.findComponents();
        List<IntArrayList> components = ccs.getComponents();
        BitSet singleNodeComponents = ccs.getSingleNodeComponents();
        logger.info("Found " + ccs.getTotalComponents() + " subnetworks (" + singleNodeComponents.cardinality() + " single nodes and "
                + components.size() + " components with more than one node, total nodes: " + ccs.getNodes() + "), took: " + sw.stop().getSeconds() + "s");

        // remove all small networks except the biggest (even when its smaller than the given min_network_size)
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

        int allowedRemoved = ghStorage.getEdges() / 2;
        if (removedEdges > allowedRemoved)
            throw new IllegalStateException("Too many total edges were removed: " + removedEdges + " out of " + ghStorage.getEdges() + "\n" +
                    "The maximum number of removed edges is: " + allowedRemoved);

        subnetworks = ccs.getTotalComponents() - removedComponents;
        logger.info("Removed " + removedComponents + " subnetworks (biggest removed: " + biggestRemoved + " nodes) -> " +
                subnetworks + " subnetwork(s) left (smallest: " + smallestRemaining + ", biggest: " + ccs.getBiggestComponent().size() + " nodes)"
                + ", total removed edges: " + removedEdges + ", took: " + sw.stop().getSeconds() + "s");
        return removedEdges;
    }

    /**
     * Makes all edges of the given component (the given set of node ids) inaccessible for the given access encoded value.
     * So far we are not removing the edges entirely from the graph (we could probably do this for edges that are blocked
     * for *all* vehicles similar to {@link #markNodesRemovedIfUnreachable})
     */
    int blockEdgesForComponent(EdgeExplorer explorer, BooleanEncodedValue accessEnc, IntIndexedContainer component) {
        int removedEdges = 0;
        for (int i = 0; i < component.size(); i++) {
            EdgeIterator edge = explorer.setBaseNode(component.get(i));
            while (edge.next()) {
                if (!edge.get(accessEnc) && !edge.getReverse(accessEnc))
                    continue;
                edge.set(accessEnc, false).setReverse(accessEnc, false);
                removedEdges++;
            }
        }
        return removedEdges;
    }

    /**
     * Removes nodes if all edges are not accessible. I.e. removes zero degree nodes.
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
}
