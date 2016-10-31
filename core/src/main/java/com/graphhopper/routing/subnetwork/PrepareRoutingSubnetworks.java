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

import com.graphhopper.coll.GHBitSet;
import com.graphhopper.coll.GHBitSetImpl;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.*;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final List<FlagEncoder> encoders;
    private int minNetworkSize = 200;
    private int minOneWayNetworkSize = 0;
    private int subnetworks = -1;

    public PrepareRoutingSubnetworks(GraphHopperStorage ghStorage, List<FlagEncoder> encoders) {
        this.ghStorage = ghStorage;
        this.encoders = encoders;
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

        int unvisitedDeadEnds = 0;
        for (FlagEncoder encoder : encoders) {
            // mark edges for one vehicle as inaccessible
            PrepEdgeFilter filter = new PrepEdgeFilter(encoder);
            if (minOneWayNetworkSize > 0)
                unvisitedDeadEnds += removeDeadEndUnvisitedNetworks(filter);

            List<TIntArrayList> components = findSubnetworks(filter);
            keepLargeNetworks(filter, components);
            subnetworks = Math.max(components.size(), subnetworks);
            logger.info(components.size() + " subnetworks found for " + encoder + ", " + Helper.getMemInfo());
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
    List<TIntArrayList> findSubnetworks(PrepEdgeFilter filter) {
        final FlagEncoder encoder = filter.getEncoder();
        final EdgeExplorer explorer = ghStorage.createEdgeExplorer(filter);
        int locs = ghStorage.getNodes();
        List<TIntArrayList> list = new ArrayList<TIntArrayList>(100);
        final GHBitSet bs = new GHBitSetImpl(locs);
        for (int start = 0; start < locs; start++) {
            if (bs.contains(start))
                continue;

            final TIntArrayList intList = new TIntArrayList(20);
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
                    if (encoder.isForward(edge.getFlags()) || encoder.isBackward(edge.getFlags())) {
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
    int keepLargeNetworks(PrepEdgeFilter filter, List<TIntArrayList> components) {
        if (components.size() <= 1)
            return 0;

        int maxCount = -1;
        TIntList oldComponent = null;
        int allRemoved = 0;
        FlagEncoder encoder = filter.getEncoder();
        EdgeExplorer explorer = ghStorage.createEdgeExplorer(filter);
        for (TIntArrayList component : components) {
            if (maxCount < 0) {
                maxCount = component.size();
                oldComponent = component;
                continue;
            }

            int removedEdges;
            if (maxCount < component.size()) {
                // new biggest area found. remove old
                removedEdges = removeEdges(explorer, encoder, oldComponent, minNetworkSize);

                maxCount = component.size();
                oldComponent = component;
            } else {
                removedEdges = removeEdges(explorer, encoder, component, minNetworkSize);
            }

            allRemoved += removedEdges;
        }

        if (allRemoved > ghStorage.getAllEdges().getMaxId() / 2)
            throw new IllegalStateException("Too many total edges were removed: " + allRemoved + ", all edges:" + ghStorage.getAllEdges().getMaxId());
        return allRemoved;
    }

    String toString(FlagEncoder encoder, EdgeIterator iter) {
        String str = "";
        while (iter.next()) {
            int adjNode = iter.getAdjNode();
            str += adjNode + " (" + ghStorage.getNodeAccess().getLat(adjNode) + "," + ghStorage.getNodeAccess().getLon(adjNode) + "), ";
            str += "speed  (fwd:" + encoder.getSpeed(iter.getFlags()) + ", rev:" + encoder.getReverseSpeed(iter.getFlags()) + "), ";
            str += "access (fwd:" + encoder.isForward(iter.getFlags()) + ", rev:" + encoder.isBackward(iter.getFlags()) + "), ";
            str += "distance:" + iter.getDistance();
            str += ";\n ";
        }
        return str;
    }

    /**
     * This method removes networks that will be never be visited by this filter. See #235 for
     * example, small areas like parking lots are sometimes connected to the whole network through a
     * one-way road. This is clearly an error - but is causes the routing to fail when a point gets
     * connected to this small area. This routine removes all these networks from the graph.
     * <p>
     *
     * @return number of removed edges
     */
    int removeDeadEndUnvisitedNetworks(final PrepEdgeFilter bothFilter) {
        StopWatch sw = new StopWatch(bothFilter.getEncoder() + " findComponents").start();
        final EdgeFilter outFilter = new DefaultEdgeFilter(bothFilter.getEncoder(), false, true);

        // Very important special case for single entry components: we don't need them! See #520
        // But they'll be created a lot for multiple vehicles as many nodes e.g. for foot are not accessible at all for car.
        // We can ignore these single entry components as they are already set 'not accessible'
        EdgeExplorer explorer = ghStorage.createEdgeExplorer(outFilter);
        int nodes = ghStorage.getNodes();
        GHBitSet ignoreSet = new GHBitSetImpl(ghStorage.getNodes());
        for (int start = 0; start < nodes; start++) {
            if (!ghStorage.isNodeRemoved(start)) {
                EdgeIterator iter = explorer.setBaseNode(start);
                if (!iter.next())
                    ignoreSet.add(start);
            }
        }

        // partition graph into strongly connected components using Tarjan's algorithm        
        TarjansSCCAlgorithm tarjan = new TarjansSCCAlgorithm(ghStorage, ignoreSet, outFilter);
        List<TIntArrayList> components = tarjan.findComponents();
        logger.info(sw.stop() + ", size:" + components.size());

        return removeEdges(bothFilter, components, minOneWayNetworkSize);
    }

    /**
     * This method removes the access to edges available from the nodes contained in the components.
     * But only if a components' size is smaller then the specified min value.
     * <p>
     *
     * @return number of removed edges
     */
    int removeEdges(final PrepEdgeFilter bothFilter, List<TIntArrayList> components, int min) {
        // remove edges determined from nodes but only if less than minimum size
        FlagEncoder encoder = bothFilter.getEncoder();
        EdgeExplorer explorer = ghStorage.createEdgeExplorer(bothFilter);
        int removedEdges = 0;
        for (TIntArrayList component : components) {
            removedEdges += removeEdges(explorer, encoder, component, min);
        }
        return removedEdges;
    }

    int removeEdges(EdgeExplorer explorer, FlagEncoder encoder, TIntList component, int min) {
        int removedEdges = 0;
        if (component.size() < min)
            for (int i = 0; i < component.size(); i++) {
                EdgeIterator edge = explorer.setBaseNode(component.get(i));
                while (edge.next()) {
                    edge.setFlags(encoder.setAccess(edge.getFlags(), false, false));
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
        for (int nodeIndex = 0; nodeIndex < ghStorage.getNodes(); nodeIndex++) {
            if (detectNodeRemovedForAllEncoders(edgeExplorer, nodeIndex))
                ghStorage.markNodeRemoved(nodeIndex);
        }
    }

    /**
     * This method checks if the node is removed or inaccessible for ALL encoders.
     * <p>
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
            for (FlagEncoder encoder : encoders) {
                if (encoder.isBackward(iter.getFlags())
                        || encoder.isForward(iter.getFlags()))
                    return false;
            }
        }

        return true;
    }

    static class PrepEdgeFilter extends DefaultEdgeFilter {

        FlagEncoder encoder;

        public PrepEdgeFilter(FlagEncoder encoder) {
            super(encoder);
            this.encoder = encoder;
        }

        public FlagEncoder getEncoder() {
            return encoder;
        }
    }
}
