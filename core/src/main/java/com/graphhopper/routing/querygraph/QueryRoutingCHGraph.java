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
    public int getEdges() {
        return routingCHGraph.getEdges() + queryOverlay.getNumVirtualEdges();
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
    public RoutingCHEdgeIteratorState getEdgeIteratorState(int chEdge, int adjNode) {
        if (!isVirtualEdge(chEdge))
            return routingCHGraph.getEdgeIteratorState(chEdge, adjNode);
        // todo: possible optimization - instead of building a new virtual edge object use the ones we already
        // built for virtualEdgesAtReal/VirtualNodes
        return buildVirtualCHEdgeState(getVirtualEdgeState(chEdge, adjNode));
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

// ORS-GH MOD START add getters
    public QueryOverlay getQueryOverlay() {
        return queryOverlay;
    }

    public QueryGraph getQueryGraph() {
        return queryGraph;
    }
// ORS-GH MOD END

    @Override
    public Weighting getWeighting() {
        return weighting;
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

// ORS-GH MOD change access from private to protected
    protected IntObjectMap<List<RoutingCHEdgeIteratorState>> buildVirtualEdgesAtRealNodes(final RoutingCHEdgeExplorer explorer) {
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
                                iter.getBaseNode(), iter.getAdjNode(), iter.getOrigEdgeFirst(), iter.getOrigEdgeLast(),
                                iter.getSkippedEdge1(), iter.getSkippedEdge2(), iter.getWeight(false), iter.getWeight(true)));
                    } else if (!edgeChanges.getRemovedEdges().contains(iter.getOrigEdge())) {
                        virtualEdges.add(new VirtualCHEdgeIteratorState(iter.getEdge(), iter.getOrigEdge(),
                                iter.getBaseNode(), iter.getAdjNode(), iter.getOrigEdgeFirst(), iter.getOrigEdgeLast(),
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

// ORS-GH MOD START change return type from a specific class to its interface
    private RoutingCHEdgeIteratorState buildVirtualCHEdgeState(VirtualEdgeIteratorState virtualEdgeState) {
// ORS-GH MOD END
        int virtualCHEdge = shiftVirtualEdgeIDForCH(virtualEdgeState.getEdge());
        return buildVirtualCHEdgeState(virtualEdgeState, virtualCHEdge);
    }

// ORS-GH MOD START change access from private to protected and return type from a specific class to its interface
    protected RoutingCHEdgeIteratorState buildVirtualCHEdgeState(EdgeIteratorState edgeState, int edgeID) {
// ORS-GH MOD END
        int origEdge = edgeState.getEdge();
        double fwdWeight = weighting.calcEdgeWeightWithAccess(edgeState, false);
        double bwdWeight = weighting.calcEdgeWeightWithAccess(edgeState, true);
        return new VirtualCHEdgeIteratorState(edgeID, origEdge, edgeState.getBaseNode(), edgeState.getAdjNode(),
                origEdge, origEdge, NO_EDGE, NO_EDGE, fwdWeight, bwdWeight);
    }

// ORS-GH MOD change access from private to protected
    protected int shiftVirtualEdgeIDForCH(int edge) {
        return edge + routingCHGraph.getEdges() - routingCHGraph.getBaseGraph().getEdges();
    }

    private int getInternalVirtualEdgeId(int edge) {
        return 2 * (edge - routingCHGraph.getEdges());
    }

    private boolean isVirtualNode(int node) {
        return node >= routingCHGraph.getNodes();
    }

    private boolean isVirtualEdge(int edge) {
        return edge >= routingCHGraph.getEdges();
    }

// ORS-GH MOD change access from private to protected
    protected static class VirtualCHEdgeIteratorState implements RoutingCHEdgeIteratorState {
        private final int edge;
        private final int origEdge;
        private final int baseNode;
        private final int adjNode;
        private final int origEdgeFirst;
        private final int origEdgeLast;
        private final int skippedEdge1;
        private final int skippedEdge2;
        private final double weightFwd;
        private final double weightBwd;

        public VirtualCHEdgeIteratorState(int edge, int origEdge, int baseNode, int adjNode, int origEdgeFirst, int origEdgeLast, int skippedEdge1, int skippedEdge2, double weightFwd, double weightBwd) {
            this.edge = edge;
            this.origEdge = origEdge;
            this.baseNode = baseNode;
            this.adjNode = adjNode;
            this.origEdgeFirst = origEdgeFirst;
            this.origEdgeLast = origEdgeLast;
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
        public int getOrigEdgeFirst() {
            return origEdgeFirst;
        }

        @Override
        public int getOrigEdgeLast() {
            return origEdgeLast;
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
        public int getSkippedEdge1() {
            return skippedEdge1;
        }

        @Override
        public int getSkippedEdge2() {
            return skippedEdge2;
        }

        @Override
        public double getWeight(boolean reverse) {
            return reverse ? weightBwd : weightFwd;
        }

// ORS-GH MOD START add method for TD core routing
        @Override
        public int getTime(boolean reverse) {
            throw new UnsupportedOperationException("Not supported.");
        }
// ORS-GH MOD END

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
        public int getOrigEdgeFirst() {
            return getCurrent().getOrigEdgeFirst();
        }

        @Override
        public int getOrigEdgeLast() {
            return getCurrent().getOrigEdgeLast();
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
        public int getSkippedEdge1() {
            if (!isShortcut())
                throw new IllegalStateException("Skipped edges are only available for shortcuts");
            return getCurrent().getSkippedEdge1();
        }

        @Override
        public int getSkippedEdge2() {
            if (!isShortcut())
                throw new IllegalStateException("Skipped edges are only available for shortcuts");
            return getCurrent().getSkippedEdge2();
        }

        @Override
        public double getWeight(boolean reverse) {
            return getCurrent().getWeight(reverse);
        }

// ORS-GH MOD START add method for TD core routing
        @Override
        public int getTime(boolean reverse) {
            return getCurrent().getTime(reverse);
        }
// ORS-GH MOD END

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