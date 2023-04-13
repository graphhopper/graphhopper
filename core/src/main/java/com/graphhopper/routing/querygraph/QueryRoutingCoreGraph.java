package com.graphhopper.routing.querygraph;

import com.carrotsearch.hppc.IntObjectHashMap;
import com.carrotsearch.hppc.IntObjectMap;
import com.carrotsearch.hppc.procedures.IntObjectProcedure;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.RoutingCHEdgeExplorer;
import com.graphhopper.storage.RoutingCHEdgeIterator;
import com.graphhopper.storage.RoutingCHEdgeIteratorState;
import com.graphhopper.storage.RoutingCHGraph;
import com.graphhopper.util.EdgeIteratorState;

import java.util.ArrayList;
import java.util.List;

import static com.graphhopper.util.EdgeIterator.NO_EDGE;

public class QueryRoutingCoreGraph extends QueryRoutingCHGraph {

    public QueryRoutingCoreGraph(RoutingCHGraph routingCHGraph, QueryGraph queryGraph) {
        super(routingCHGraph, queryGraph);
    }

    @Override
    protected IntObjectMap<List<RoutingCHEdgeIteratorState>> buildVirtualEdgesAtRealNodes(final RoutingCHEdgeExplorer explorer) {
        final IntObjectMap<List<RoutingCHEdgeIteratorState>> virtualEdgesAtRealNodes =
                new IntObjectHashMap<>(getQueryOverlay().getEdgeChangesAtRealNodes().size());
        getQueryOverlay().getEdgeChangesAtRealNodes().forEach(new IntObjectProcedure<QueryOverlay.EdgeChanges>() {
            @Override
            public void apply(int node, QueryOverlay.EdgeChanges edgeChanges) {
                List<RoutingCHEdgeIteratorState> virtualEdges = new ArrayList<>();
                for (EdgeIteratorState v : edgeChanges.getAdditionalEdges()) {
                    assert v.getBaseNode() == node;
                    int edge = v.getEdge();
                    if (getQueryGraph().isVirtualEdge(edge)) {
                        edge = shiftVirtualEdgeIDForCH(edge);
                    }
                    virtualEdges.add(buildVirtualCHEdgeState(v, edge));
                }
                RoutingCHEdgeIterator iter = explorer.setBaseNode(node);
                while (iter.next()) {
                    // shortcuts cannot be in the removed edge set because this was determined on the (base) query graph
                    if (iter.isShortcut()) {
                        virtualEdges.add(new VirtualCoreEdgeIteratorState(iter.getEdge(), NO_EDGE,
                                iter.getBaseNode(), iter.getAdjNode(), iter.getOrigEdgeFirst(), iter.getOrigEdgeLast(),
                                iter.getSkippedEdge1(), iter.getSkippedEdge2(), iter.getWeight(false), iter.getWeight(true),
                                iter.getTime(false), iter.getTime(true)));
                    } else if (!edgeChanges.getRemovedEdges().contains(iter.getOrigEdge())) {
                        virtualEdges.add(new VirtualCoreEdgeIteratorState(iter.getEdge(), iter.getOrigEdge(),
                                iter.getBaseNode(), iter.getAdjNode(), iter.getOrigEdgeFirst(), iter.getOrigEdgeLast(),
                                NO_EDGE, NO_EDGE, iter.getWeight(false), iter.getWeight(true),
                                iter.getTime(false), iter.getTime(true)));
                    }
                }
                virtualEdgesAtRealNodes.put(node, virtualEdges);
            }
        });
        return virtualEdgesAtRealNodes;
    }


    @Override
    protected RoutingCHEdgeIteratorState buildVirtualCHEdgeState(EdgeIteratorState edgeState, int edgeID) {
        Weighting weighting = getWeighting();
        int origEdge = edgeState.getEdge();
        double fwdWeight = weighting.calcEdgeWeightWithAccess(edgeState, false);
        double bwdWeight = weighting.calcEdgeWeightWithAccess(edgeState, true);
        int fwdTime = (int) weighting.calcEdgeMillisWithAccess(edgeState, false);
        int bwdTime = (int) weighting.calcEdgeMillisWithAccess(edgeState, true);
        return new VirtualCoreEdgeIteratorState(edgeID, origEdge, edgeState.getBaseNode(), edgeState.getAdjNode(),
                origEdge, origEdge, NO_EDGE, NO_EDGE, fwdWeight, bwdWeight, fwdTime, bwdTime);
    }


    private static class VirtualCoreEdgeIteratorState extends VirtualCHEdgeIteratorState {
        private final int timeFwd;
        private final int timeBwd;

        public VirtualCoreEdgeIteratorState(int edge, int origEdge, int baseNode, int adjNode, int origEdgeFirst, int origEdgeLast, int skippedEdge1, int skippedEdge2, double weightFwd, double weightBwd, int timeFwd, int timeBwd) {
            super( edge,  origEdge,  baseNode,  adjNode,  origEdgeFirst,  origEdgeLast,  skippedEdge1,  skippedEdge2,  weightFwd,  weightBwd);
            this.timeFwd = timeFwd;
            this.timeBwd = timeBwd;
        }

        @Override
        public int getTime(boolean reverse) {
            return reverse ? timeBwd : timeFwd;
        }

        @Override
        public String toString() {
            return super.toString() + ", timeFwd: " + timeFwd + ", timeBwd: " + timeBwd;
        }

    }

}
