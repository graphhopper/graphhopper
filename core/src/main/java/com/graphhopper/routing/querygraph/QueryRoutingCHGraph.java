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

package com.graphhopper.routing.querygraph;

import com.carrotsearch.hppc.IntObjectHashMap;
import com.carrotsearch.hppc.IntObjectMap;
import com.carrotsearch.hppc.procedures.IntObjectProcedure;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.graphhopper.routing.querygraph.QueryGraph.SNAP_ADJ;
import static com.graphhopper.routing.querygraph.QueryGraph.SNAP_BASE;
import static com.graphhopper.util.EdgeIterator.NO_EDGE;

/**
 * This class is used to allow routing between virtual nodes (snapped coordinates that lie between the nodes of the
 * original graph) when using CH. To use it first create a {@link QueryGraph} just as if you were not using CH and then
 * create an instance of the present class on top of this.
 */
public class QueryRoutingCHGraph implements RoutingCHGraph {
    private final RoutingCHGraph routingCHGraph;
    private final Weighting weighting;
    private final QueryOverlay queryOverlay;
    private final QueryGraph queryGraph;
    private final Weighting queryGraphWeighting;
    private final int nodes;

    private final IntObjectMap<List<RoutingCHEdgeIteratorState>> virtualOutEdgesAtRealNodes;
    private final IntObjectMap<List<RoutingCHEdgeIteratorState>> virtualInEdgesAtRealNodes;
    private final List<List<RoutingCHEdgeIteratorState>> virtualEdgesAtVirtualNodes;

    public QueryRoutingCHGraph(RoutingCHGraph routingCHGraph, QueryGraph queryGraph) {
        this.routingCHGraph = routingCHGraph;
        this.weighting = routingCHGraph.getWeighting();
        this.queryOverlay = queryGraph.getQueryOverlay();
        this.queryGraph = queryGraph;
        this.queryGraphWeighting = queryGraph.wrapWeighting(weighting);
        virtualOutEdgesAtRealNodes = buildVirtualEdgesAtRealNodes(routingCHGraph.createOutEdgeExplorer());
        virtualInEdgesAtRealNodes = buildVirtualEdgesAtRealNodes(routingCHGraph.createInEdgeExplorer());
        virtualEdgesAtVirtualNodes = buildVirtualEdgesAtVirtualNodes();
        nodes = queryGraph.getNodes();
    }

    @Override
    public int getNodes() {
        return nodes;
    }

    @Override
    public long getEdges() {
        return routingCHGraph.getEdges() + queryOverlay.getNumVirtualEdges();
    }

    @Override
    public long getShortcuts() {
        return routingCHGraph.getShortcuts();
    }

    @Override
    public RoutingCHEdgeExplorer createInEdgeExplorer() {
        return createEdgeExplorer(routingCHGraph.createInEdgeExplorer(), virtualInEdgesAtRealNodes);
    }

    @Override
    public RoutingCHEdgeExplorer createOutEdgeExplorer() {
        return createEdgeExplorer(routingCHGraph.createOutEdgeExplorer(), virtualOutEdgesAtRealNodes);
    }

    private RoutingCHEdgeExplorer createEdgeExplorer(final RoutingCHEdgeExplorer explorer, final IntObjectMap<List<RoutingCHEdgeIteratorState>> virtualEdgesAtRealNodes) {
        final VirtualCHEdgeIterator iterator = new VirtualCHEdgeIterator();
        return new RoutingCHEdgeExplorer() {
            @Override
            public RoutingCHEdgeIterator setBaseNode(int baseNode) {
                if (isVirtualNode(baseNode)) {
                    List<RoutingCHEdgeIteratorState> virtualEdges = virtualEdgesAtVirtualNodes.get(baseNode - routingCHGraph.getNodes());
                    iterator.reset(virtualEdges);
                    return iterator;
                } else {
                    List<RoutingCHEdgeIteratorState> virtualEdges = virtualEdgesAtRealNodes.get(baseNode);
                    if (virtualEdges == null) {
                        return explorer.setBaseNode(baseNode);
                    } else {
                        iterator.reset(virtualEdges);
                        return iterator;
                    }
                }
            }
        };
    }

    @Override
    public RoutingCHEdgeIteratorState getEdgeIteratorState(long chEdge, int adjNode) {
        if (!isVirtualEdge(chEdge))
            return routingCHGraph.getEdgeIteratorState(chEdge, adjNode);
        // todo: possible optimization - instead of building a new virtual edge object use the ones we already
        // built for virtualEdgesAtReal/VirtualNodes

        // TODO NOW not good
        return buildVirtualCHEdgeState(getVirtualEdgeState((int) chEdge, adjNode));
    }

    @Override
    public int getLevel(int node) {
        if (isVirtualNode(node))
            return Integer.MAX_VALUE;
        return routingCHGraph.getLevel(node);
    }

    @Override
    public double getTurnWeight(int inEdge, int viaNode, int outEdge) {
        if (!routingCHGraph.hasTurnCosts())
            // this is important as node-based algorithms might pass in ch edge ids here
            return 0;
        return queryGraphWeighting.calcTurnWeight(inEdge, viaNode, outEdge);
    }

    @Override
    public Graph getBaseGraph() {
        return queryGraph;
    }

    @Override
    public boolean hasTurnCosts() {
        return routingCHGraph.hasTurnCosts();
    }

    @Override
    public boolean isEdgeBased() {
        return routingCHGraph.isEdgeBased();
    }

    @Override
    public Weighting getWeighting() {
        return weighting;
    }

    @Override
    public void close() {
        routingCHGraph.close();
        virtualEdgesAtVirtualNodes.clear();
        virtualInEdgesAtRealNodes.clear();
        virtualOutEdgesAtRealNodes.clear();
    }

    private VirtualEdgeIteratorState getVirtualEdgeState(int virtualEdgeId, int adjNode) {
        assert isVirtualEdge(virtualEdgeId);
        int internalVirtualEdgeId = getInternalVirtualEdgeId(virtualEdgeId);
        VirtualEdgeIteratorState virtualEdge = queryOverlay.getVirtualEdge(internalVirtualEdgeId);
        if (virtualEdge.getAdjNode() == adjNode || adjNode == Integer.MIN_VALUE)
            return virtualEdge;

        internalVirtualEdgeId = QueryGraph.getPosOfReverseEdge(internalVirtualEdgeId);
        virtualEdge = queryOverlay.getVirtualEdge(internalVirtualEdgeId);
        if (virtualEdge.getAdjNode() != adjNode)
            throw new IllegalArgumentException("The virtual edge with ID " + virtualEdgeId + " does not touch node " + adjNode);

        return virtualEdge;
    }

    private IntObjectMap<List<RoutingCHEdgeIteratorState>> buildVirtualEdgesAtRealNodes(final RoutingCHEdgeExplorer explorer) {
        final IntObjectMap<List<RoutingCHEdgeIteratorState>> virtualEdgesAtRealNodes =
                new IntObjectHashMap<>(queryOverlay.getEdgeChangesAtRealNodes().size());
        queryOverlay.getEdgeChangesAtRealNodes().forEach(new IntObjectProcedure<QueryOverlay.EdgeChanges>() {
            @Override
            public void apply(int node, QueryOverlay.EdgeChanges edgeChanges) {
                List<RoutingCHEdgeIteratorState> virtualEdges = new ArrayList<>();
                for (EdgeIteratorState v : edgeChanges.getAdditionalEdges()) {
                    assert v.getBaseNode() == node;
                    int edge = v.getEdge();
                    if (queryGraph.isVirtualEdge(edge)) {
                        edge = shiftVirtualEdgeIDForCH(edge);
                    }
                    virtualEdges.add(buildVirtualCHEdgeState(v, edge));
                }
                RoutingCHEdgeIterator iter = explorer.setBaseNode(node);
                while (iter.next()) {
                    // shortcuts cannot be in the removed edge set because this was determined on the (base) query graph
                    if (iter.isShortcut()) {
                        virtualEdges.add(new VirtualCHEdgeIteratorState(iter.getEdge(), NO_EDGE,
                                iter.getBaseNode(), iter.getAdjNode(), iter.getOrigEdgeKeyFirst(), iter.getOrigEdgeKeyLast(),
                                iter.getSkippedEdge1(), iter.getSkippedEdge2(), iter.getWeight(false), iter.getWeight(true)));
                    } else if (!edgeChanges.getRemovedEdges().contains(iter.getOrigEdge())) {
                        virtualEdges.add(new VirtualCHEdgeIteratorState(iter.getEdge(), iter.getOrigEdge(),
                                iter.getBaseNode(), iter.getAdjNode(), iter.getOrigEdgeKeyFirst(), iter.getOrigEdgeKeyLast(),
                                NO_EDGE, NO_EDGE, iter.getWeight(false), iter.getWeight(true)));
                    }
                }
                virtualEdgesAtRealNodes.put(node, virtualEdges);
            }
        });
        return virtualEdgesAtRealNodes;
    }

    private List<List<RoutingCHEdgeIteratorState>> buildVirtualEdgesAtVirtualNodes() {
        final int virtualNodes = queryOverlay.getVirtualNodes().size();
        final List<List<RoutingCHEdgeIteratorState>> virtualEdgesAtVirtualNodes = new ArrayList<>(virtualNodes);
        for (int i = 0; i < virtualNodes; i++) {
            List<RoutingCHEdgeIteratorState> virtualEdges = Arrays.<RoutingCHEdgeIteratorState>asList(
                    buildVirtualCHEdgeState(queryOverlay.getVirtualEdges().get(i * 4 + SNAP_BASE)),
                    buildVirtualCHEdgeState(queryOverlay.getVirtualEdges().get(i * 4 + SNAP_ADJ))
            );
            virtualEdgesAtVirtualNodes.add(virtualEdges);
        }
        return virtualEdgesAtVirtualNodes;
    }

    private VirtualCHEdgeIteratorState buildVirtualCHEdgeState(VirtualEdgeIteratorState virtualEdgeState) {
        int virtualCHEdge = shiftVirtualEdgeIDForCH(virtualEdgeState.getEdge());
        return buildVirtualCHEdgeState(virtualEdgeState, virtualCHEdge);
    }

    private VirtualCHEdgeIteratorState buildVirtualCHEdgeState(EdgeIteratorState edgeState, int edgeID) {
        double fwdWeight = weighting.calcEdgeWeight(edgeState, false);
        double bwdWeight = weighting.calcEdgeWeight(edgeState, true);
        return new VirtualCHEdgeIteratorState(edgeID, edgeState.getEdge(), edgeState.getBaseNode(), edgeState.getAdjNode(),
                edgeState.getEdgeKey(), edgeState.getEdgeKey(), NO_EDGE, NO_EDGE, fwdWeight, bwdWeight);
    }

    // TODO NOW shifting will make ID too big to fit into signed int
    private int shiftVirtualEdgeIDForCH(int edge) {
        long res = edge + routingCHGraph.getEdges() - routingCHGraph.getBaseGraph().getEdges();
        if (res > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Cannot shift edge to more than Integer.MAX_VALUE but was: " + res);
        return (int) res;
    }

    private int getInternalVirtualEdgeId(int edge) {
        long internalEdgeId = 2 * (edge - routingCHGraph.getEdges());
        if (internalEdgeId > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Cannot create more than " + internalEdgeId + " internal edges");
        return (int) internalEdgeId;
    }

    private boolean isVirtualNode(int node) {
        return node >= routingCHGraph.getNodes();
    }

    private boolean isVirtualEdge(long edge) {
        return edge >= routingCHGraph.getEdges();
    }

    private static class VirtualCHEdgeIteratorState implements RoutingCHEdgeIteratorState {
        private final int edge;
        private final int origEdge;
        private final int baseNode;
        private final int adjNode;
        private final int origEdgeKeyFirst;
        private final int origEdgeKeyLast;
        private final long skippedEdge1;
        private final long skippedEdge2;
        private final double weightFwd;
        private final double weightBwd;

        public VirtualCHEdgeIteratorState(int edge, int origEdge, int baseNode, int adjNode,
                                          int origEdgeKeyFirst, int origEdgeKeyLast, long skippedEdge1, long skippedEdge2, double weightFwd, double weightBwd) {
            this.edge = edge;
            this.origEdge = origEdge;
            this.baseNode = baseNode;
            this.adjNode = adjNode;
            this.origEdgeKeyFirst = origEdgeKeyFirst;
            this.origEdgeKeyLast = origEdgeKeyLast;
            this.skippedEdge1 = skippedEdge1;
            this.skippedEdge2 = skippedEdge2;
            this.weightFwd = weightFwd;
            this.weightBwd = weightBwd;
        }

        @Override
        public int getEdge() {
            return edge;
        }

        @Override
        public int getOrigEdge() {
            return origEdge;
        }

        @Override
        public int getOrigEdgeKeyFirst() {
            return origEdgeKeyFirst;
        }

        @Override
        public int getOrigEdgeKeyLast() {
            return origEdgeKeyLast;
        }

        @Override
        public int getBaseNode() {
            return baseNode;
        }

        @Override
        public int getAdjNode() {
            return adjNode;
        }

        @Override
        public boolean isShortcut() {
            return origEdge == NO_EDGE;
        }

        @Override
        public long getSkippedEdge1() {
            return skippedEdge1;
        }

        @Override
        public long getSkippedEdge2() {
            return skippedEdge2;
        }

        @Override
        public double getWeight(boolean reverse) {
            return reverse ? weightBwd : weightFwd;
        }

        @Override
        public String toString() {
            return "virtual: " + edge + ": " + baseNode + "->" + adjNode + ", orig: " + origEdge + ", weightFwd: " + Helper.round2(weightFwd) + ", weightBwd: " + Helper.round2(weightBwd);
        }

    }

    private static class VirtualCHEdgeIterator implements RoutingCHEdgeIterator {
        private List<RoutingCHEdgeIteratorState> edges;
        private int current = -1;

        @Override
        public boolean next() {
            current++;
            return current < edges.size();
        }

        void reset(List<RoutingCHEdgeIteratorState> edges) {
            this.edges = edges;
            current = -1;
        }

        @Override
        public int getEdge() {
            return getCurrent().getEdge();
        }

        @Override
        public int getOrigEdge() {
            return getCurrent().getOrigEdge();
        }

        @Override
        public int getOrigEdgeKeyFirst() {
            return getCurrent().getOrigEdgeKeyFirst();
        }

        @Override
        public int getOrigEdgeKeyLast() {
            return getCurrent().getOrigEdgeKeyLast();
        }

        @Override
        public int getBaseNode() {
            return getCurrent().getBaseNode();
        }

        @Override
        public int getAdjNode() {
            return getCurrent().getAdjNode();
        }

        @Override
        public boolean isShortcut() {
            return getCurrent().isShortcut();
        }

        @Override
        public long getSkippedEdge1() {
            if (!isShortcut())
                throw new IllegalStateException("Skipped edges are only available for shortcuts");
            return getCurrent().getSkippedEdge1();
        }

        @Override
        public long getSkippedEdge2() {
            if (!isShortcut())
                throw new IllegalStateException("Skipped edges are only available for shortcuts");
            return getCurrent().getSkippedEdge2();
        }

        @Override
        public double getWeight(boolean reverse) {
            return getCurrent().getWeight(reverse);
        }

        @Override
        public String toString() {
            if (current < 0)
                return "not started";
            return edges.get(current).toString() + ", current: " + (current + 1) + "/" + edges.size();
        }

        private RoutingCHEdgeIteratorState getCurrent() {
            return edges.get(current);
        }
    }
}
