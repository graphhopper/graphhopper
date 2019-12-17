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

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntIndexedContainer;
import com.graphhopper.coll.GHBitSet;
import com.graphhopper.coll.GHBitSetImpl;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.WeightedEdgeFilter;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.graphhopper.util.GHUtility.allowedAccess;

/**
 * Removes nodes which are not part of the large networks. Ie. mostly nodes with no edges at all but
 * also small subnetworks which could be bugs in OSM data or indicate otherwise disconnected areas
 * e.g. via barriers or one way problems - see #86.
 * <p>
 *
 * @author Peter Karich
 */
public class PrepareRoutingSubnetworks {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final GraphHopperStorage ghStorage;
    private final AtomicInteger maxEdgesPerNode = new AtomicInteger(0);
    private final List<Weighting> weightingList;
    private int minNetworkSize = 200;
    private int minOneWayNetworkSize = 0;
    private int subnetworks = -1;

    public PrepareRoutingSubnetworks(GraphHopperStorage ghStorage, List<FlagEncoder> encoders) {
        this.ghStorage = ghStorage;
        this.weightingList = new ArrayList<>();
        for (FlagEncoder encoder : encoders) {
            weightingList.add(new FastestWeighting(encoder));
        }
    }

    public PrepareRoutingSubnetworks(List<Weighting> weightingList, GraphHopperStorage ghStorage) {
        this.ghStorage = ghStorage;
        this.weightingList = new ArrayList<>(weightingList);
    }

    public PrepareRoutingSubnetworks setMinNetworkSize(int minNetworkSize) {
        this.minNetworkSize = minNetworkSize;
        return this;
    }

    public PrepareRoutingSubnetworks setMinOneWayNetworkSize(int minOnewayNetworkSize) {
        this.minOneWayNetworkSize = minOnewayNetworkSize;
        return this;
    }

    public void doWork() {
        if (minNetworkSize <= 0 && minOneWayNetworkSize <= 0)
            return;

        logger.info("start finding subnetworks (min:" + minNetworkSize + ", min one way:" + minOneWayNetworkSize + ") " + Helper.getMemInfo());
        int unvisitedDeadEnds = 0;
        for (Weighting weighting : weightingList) {
            BooleanEncodedValue accessEncToWrite = weighting.getFlagEncoder().getAccessEnc();
            if (minOneWayNetworkSize > 0)
                unvisitedDeadEnds += removeDeadEndUnvisitedNetworks(weighting, accessEncToWrite);

            // mark edges for one vehicle as inaccessible
            List<IntArrayList> components = findSubnetworks(weighting);
            keepLargeNetworks(weighting, accessEncToWrite, components);
            subnetworks = Math.max(components.size(), subnetworks);
            logger.info(components.size() + " subnetworks found for " + weighting + ", " + Helper.getMemInfo());
        }

        markNodesRemovedIfUnreachable();

        logger.info("optimize to remove subnetworks (" + subnetworks + "), "
                + "unvisited-dead-end-nodes (" + unvisitedDeadEnds + "), "
                + "maxEdges/node (" + maxEdgesPerNode.get() + ")");
        ghStorage.optimize();
    }

    public int getMaxSubnetworks() {
        return subnetworks;
    }

    /**
     * This method finds the double linked components according to the specified filter.
     */
    List<IntArrayList> findSubnetworks(final Weighting weighting) {
        final EdgeExplorer explorer = ghStorage.createEdgeExplorer();
        int locs = ghStorage.getNodes();
        List<IntArrayList> list = new ArrayList<>(100);
        final GHBitSet bs = new GHBitSetImpl(locs);
        for (int start = 0; start < locs; start++) {
            if (bs.contains(start))
                continue;

            final IntArrayList intList = new IntArrayList(20);
            list.add(intList);
            new BreadthFirstSearch() {
                int tmpCounter = 0;

                @Override
                protected GHBitSet createBitSet() {
                    return bs;
                }

                @Override
                protected final boolean goFurther(int nodeId) {
                    if (tmpCounter > maxEdgesPerNode.get())
                        maxEdgesPerNode.set(tmpCounter);

                    tmpCounter = 0;
                    intList.add(nodeId);
                    return true;
                }

                @Override
                protected final boolean checkAdjacent(EdgeIteratorState edge) {
                    if (allowedAccess(weighting, edge, false) || allowedAccess(weighting, edge, true)) {
                        tmpCounter++;
                        return true;
                    }
                    return false;
                }

            }.start(explorer, start);
            intList.trimToSize();
        }
        return list;
    }

    /**
     * Deletes all but the largest subnetworks.
     */
    int keepLargeNetworks(Weighting weighting, BooleanEncodedValue accessEncToWrite, List<IntArrayList> components) {
        if (components.size() <= 1)
            return 0;

        int maxCount = -1;
        IntIndexedContainer oldComponent = null;
        int allRemoved = 0;
        EdgeFilter filter = WeightedEdgeFilter.allEdges(weighting);
        EdgeExplorer explorer = ghStorage.createEdgeExplorer();
        for (IntArrayList component : components) {
            if (maxCount < 0) {
                maxCount = component.size();
                oldComponent = component;
                continue;
            }

            int removedEdges;
            if (maxCount < component.size()) {
                // new biggest area found. remove old
                removedEdges = removeEdges(explorer, accessEncToWrite, filter, oldComponent, minNetworkSize);

                maxCount = component.size();
                oldComponent = component;
            } else {
                removedEdges = removeEdges(explorer, accessEncToWrite, filter, component, minNetworkSize);
            }

            allRemoved += removedEdges;
        }

        if (allRemoved > ghStorage.getAllEdges().length() / 2)
            throw new IllegalStateException("Too many total edges were removed: " + allRemoved + ", all edges:" + ghStorage.getAllEdges().length());
        return allRemoved;
    }

    /**
     * This method removes networks that will be never be visited by this filter. See #235 for
     * example, small areas like parking lots are sometimes connected to the whole network through a
     * one-way road. This is clearly an error - but it causes the routing to fail when a point gets
     * connected to this small area. This routine removes all these networks from the graph.
     *
     * @return number of removed edges
     */
    int removeDeadEndUnvisitedNetworks(Weighting weighting, BooleanEncodedValue accessEncToWrite) {
        StopWatch sw = new StopWatch(weighting.toString() + " findComponents").start();
        // partition graph into strongly connected components using Tarjan's algorithm        
        TarjansSCCAlgorithm tarjan = new TarjansSCCAlgorithm(ghStorage, weighting, true);
        List<IntArrayList> components = tarjan.findComponents();
        logger.info(sw.stop() + ", size:" + components.size());

        final EdgeFilter bothFilter = WeightedEdgeFilter.allEdges(weighting);
        return removeEdges(bothFilter, accessEncToWrite, components, minOneWayNetworkSize);
    }

    /**
     * This method removes the access to edges available from the nodes contained in the components.
     * But only if a components' size is smaller then the specified min value.
     *
     * @return number of removed edges
     */
    int removeEdges(final EdgeFilter bothFilter, BooleanEncodedValue accessEncToWrite, List<IntArrayList> components, int min) {
        // remove edges determined from nodes but only if less than minimum size
        EdgeExplorer explorer = ghStorage.createEdgeExplorer();
        int removedEdges = 0;
        for (IntArrayList component : components) {
            removedEdges += removeEdges(explorer, accessEncToWrite, bothFilter, component, min);
        }
        return removedEdges;
    }

    int removeEdges(EdgeExplorer explorer, BooleanEncodedValue accessEncToWrite, EdgeFilter filter, IntIndexedContainer component, int min) {
        int removedEdges = 0;
        if (component.size() < min) {
            for (int i = 0; i < component.size(); i++) {
                EdgeIterator edge = explorer.setBaseNode(component.get(i));
                while (edge.next()) {
                    if (!filter.accept(edge))
                        continue;
                    edge.set(accessEncToWrite, false).setReverse(accessEncToWrite, false);
                    removedEdges++;
                }
            }
        }

        return removedEdges;
    }

    /**
     * Removes nodes if all edges are not accessible. I.e. removes zero degree nodes.
     */
    void markNodesRemovedIfUnreachable() {
        EdgeExplorer edgeExplorer = ghStorage.createEdgeExplorer();
        for (int nodeIndex = 0; nodeIndex < ghStorage.getNodes(); nodeIndex++) {
            if (detectNodeRemovedForAllEncoders(edgeExplorer, nodeIndex))
                ghStorage.markNodeRemoved(nodeIndex);
        }
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
            // if at least on encoder allows one direction return false
            for (Weighting weighting : weightingList) {
                if (allowedAccess(weighting, iter, false) || allowedAccess(weighting, iter, true))
                    return false;
            }
        }

        return true;
    }
}
