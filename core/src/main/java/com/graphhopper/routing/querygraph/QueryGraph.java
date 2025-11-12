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

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntObjectMap;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.carrotsearch.hppc.procedures.IntObjectProcedure;
import com.graphhopper.coll.GHIntObjectHashMap;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.weighting.QueryGraphWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.shapes.BBox;

import java.util.*;

/**
 * A class which is used to query the underlying graph with real GPS points. It does so by
 * introducing virtual nodes and edges. It is lightweight in order to be created every time a new
 * query comes in, which makes the behaviour thread safe.
 * <p>
 * Calling any <code>create</code> method creates virtual edges between the tower nodes of the existing
 * graph and new virtual tower nodes. Every virtual node has two adjacent nodes and is connected
 * to each adjacent nodes via 2 virtual edges with opposite base node / adjacent node encoding.
 * However, the edge explorer returned by {@link #createEdgeExplorer()} only returns two
 * virtual edges per virtual node (the ones with correct base node).
 *
 * @author Peter Karich
 */
public class QueryGraph implements Graph {
    static final int BASE_SNAP = 0, SNAP_BASE = 1, SNAP_ADJ = 2, ADJ_SNAP = 3;
    private final BaseGraph baseGraph;
    private final int baseNodes;
    private final int baseEdges;
    private final TurnCostStorage turnCostStorage;
    private final NodeAccess nodeAccess;
    private final QueryOverlay queryOverlay;

    // Use LinkedHashSet for predictable iteration order.
    private final Set<VirtualEdgeIteratorState> unfavoredEdges = new LinkedHashSet<>(5);
    private final IntObjectMap<List<EdgeIteratorState>> virtualEdgesAtRealNodes;
    private final List<List<EdgeIteratorState>> virtualEdgesAtVirtualNodes;

    public static QueryGraph create(BaseGraph graph, Snap snap) {
        return QueryGraph.create(graph, Collections.singletonList(snap));
    }

    public static QueryGraph create(BaseGraph graph, Snap fromSnap, Snap toSnap) {
        return QueryGraph.create(graph.getBaseGraph(), Arrays.asList(fromSnap, toSnap));
    }

    public static QueryGraph create(BaseGraph graph, List<Snap> snaps) {
        return new QueryGraph(graph, snaps);
    }

    private QueryGraph(BaseGraph graph, List<Snap> snaps) {
        baseGraph = graph;
        baseNodes = graph.getNodes();
        baseEdges = graph.getEdges();

        queryOverlay = QueryOverlayBuilder.build(graph, snaps);
        nodeAccess = new ExtendedNodeAccess(graph.getNodeAccess(), queryOverlay.getVirtualNodes(), baseNodes);
        turnCostStorage = baseGraph.getTurnCostStorage();

        // build data structures holding the virtual edges at all real/virtual nodes that are modified compared to the
        // mainGraph.
        final EdgeExplorer mainExplorer = baseGraph.createEdgeExplorer();
        virtualEdgesAtRealNodes = buildVirtualEdgesAtRealNodes(mainExplorer);
        virtualEdgesAtVirtualNodes = buildVirtualEdgesAtVirtualNodes();
    }

    public QueryOverlay getQueryOverlay() {
        return queryOverlay;
    }

    @Override
    public BaseGraph getBaseGraph() {
        return baseGraph;
    }

    public boolean isVirtualEdge(int edgeId) {
        return edgeId >= baseEdges;
    }

    public boolean isVirtualNode(int nodeId) {
        return nodeId >= baseNodes;
    }

    /**
     * Assigns the 'unfavored' flag to the given virtual edges (for both directions)
     */
    public void unfavorVirtualEdges(IntArrayList edgeIds) {
        for (IntCursor c : edgeIds) {
            int virtualEdgeId = c.value;
            if (!isVirtualEdge(virtualEdgeId))
                return;
            VirtualEdgeIteratorState edge = getVirtualEdge(getInternalVirtualEdgeId(virtualEdgeId));
            edge.setUnfavored(true);
            unfavoredEdges.add(edge);
            // we have to set the unfavored flag also for the virtual edge state that is used when we discover the same edge
            // from the adjacent node. note that the unfavored flag will be set for both 'directions' of the same edge state.
            VirtualEdgeIteratorState reverseEdge = getVirtualEdge(getPosOfReverseEdge(getInternalVirtualEdgeId(virtualEdgeId)));
            reverseEdge.setUnfavored(true);
            unfavoredEdges.add(reverseEdge);
        }
    }

    /**
     * Returns all virtual edges that have been unfavored via {@link #unfavorVirtualEdges(IntArrayList)}
     */
    public Set<EdgeIteratorState> getUnfavoredVirtualEdges() {
        // Need to create a new set to convert Set<VirtualEdgeIteratorState> to
        // Set<EdgeIteratorState>.
        return new LinkedHashSet<>(unfavoredEdges);
    }

    /**
     * Removes the 'unfavored' status of all virtual edges.
     */
    public void clearUnfavoredStatus() {
        for (VirtualEdgeIteratorState edge : unfavoredEdges) {
            edge.setUnfavored(false);
        }
        unfavoredEdges.clear();
    }

    @Override
    public int getNodes() {
        return queryOverlay.getVirtualNodes().size() + baseNodes;
    }

    @Override
    public int getEdges() {
        return queryOverlay.getNumVirtualEdges() / 2 + baseEdges;
    }

    @Override
    public NodeAccess getNodeAccess() {
        return nodeAccess;
    }

    @Override
    public BBox getBounds() {
        return baseGraph.getBounds();
    }

    @Override
    public EdgeIteratorState getEdgeIteratorState(int origEdgeId, int adjNode) {
        if (!isVirtualEdge(origEdgeId))
            return baseGraph.getEdgeIteratorState(origEdgeId, adjNode);

        int edgeId = getInternalVirtualEdgeId(origEdgeId);
        EdgeIteratorState eis = getVirtualEdge(edgeId);
        if (eis.getAdjNode() == adjNode || adjNode == Integer.MIN_VALUE)
            return eis;
        edgeId = getPosOfReverseEdge(edgeId);

        EdgeIteratorState eis2 = getVirtualEdge(edgeId);
        if (eis2.getAdjNode() == adjNode)
            return eis2;
        throw new IllegalStateException("Edge " + origEdgeId + " not found with adjNode:" + adjNode
                + ". found edges were:" + eis + ", " + eis2);
    }

    @Override
    public EdgeIteratorState getEdgeIteratorStateForKey(int edgeKey) {
        int edge = GHUtility.getEdgeFromEdgeKey(edgeKey);
        if (!isVirtualEdge(edge))
            return baseGraph.getEdgeIteratorStateForKey(edgeKey);
        return getVirtualEdge(edgeKey - 2 * baseEdges);
    }

    private VirtualEdgeIteratorState getVirtualEdge(int edgeId) {
        return queryOverlay.getVirtualEdge(edgeId);
    }

    static int getPosOfReverseEdge(int edgeId) {
        // find reverse edge via convention. see virtualEdges comment above
        return edgeId % 2 == 0 ? edgeId + 1 : edgeId - 1;
    }

    private int getInternalVirtualEdgeId(int origEdgeId) {
        return 2 * (origEdgeId - baseEdges);
    }

    @Override
    public EdgeExplorer createEdgeExplorer(final EdgeFilter edgeFilter) {
        // re-use these objects between setBaseNode calls to prevent GC
        final EdgeExplorer mainExplorer = baseGraph.createEdgeExplorer(edgeFilter);
        final VirtualEdgeIterator virtualEdgeIterator = new VirtualEdgeIterator(edgeFilter, null);
        return new EdgeExplorer() {
            @Override
            public EdgeIterator setBaseNode(int baseNode) {
                if (isVirtualNode(baseNode)) {
                    List<EdgeIteratorState> virtualEdges = virtualEdgesAtVirtualNodes.get(baseNode - baseNodes);
                    return virtualEdgeIterator.reset(virtualEdges);
                } else {
                    List<EdgeIteratorState> virtualEdges = virtualEdgesAtRealNodes.get(baseNode);
                    if (virtualEdges == null) {
                        return mainExplorer.setBaseNode(baseNode);
                    } else {
                        return virtualEdgeIterator.reset(virtualEdges);
                    }
                }
            }
        };
    }

    private IntObjectMap<List<EdgeIteratorState>> buildVirtualEdgesAtRealNodes(final EdgeExplorer mainExplorer) {
        final IntObjectMap<List<EdgeIteratorState>> virtualEdgesAtRealNodes =
                new GHIntObjectHashMap<>(queryOverlay.getEdgeChangesAtRealNodes().size());
        queryOverlay.getEdgeChangesAtRealNodes().forEach(new IntObjectProcedure<QueryOverlay.EdgeChanges>() {
            @Override
            public void apply(int node, QueryOverlay.EdgeChanges edgeChanges) {
                List<EdgeIteratorState> virtualEdges = new ArrayList<>(edgeChanges.getAdditionalEdges());
                EdgeIterator mainIter = mainExplorer.setBaseNode(node);
                while (mainIter.next()) {
                    if (!edgeChanges.getRemovedEdges().contains(mainIter.getEdge())) {
                        virtualEdges.add(mainIter.detach(false));
                    }
                }
                virtualEdgesAtRealNodes.put(node, virtualEdges);
            }
        });
        return virtualEdgesAtRealNodes;
    }

    private List<List<EdgeIteratorState>> buildVirtualEdgesAtVirtualNodes() {
        final List<List<EdgeIteratorState>> virtualEdgesAtVirtualNodes = new ArrayList<>();
        for (int i = 0; i < queryOverlay.getVirtualNodes().size(); i++) {
            List<EdgeIteratorState> virtualEdges = Arrays.<EdgeIteratorState>asList(
                    queryOverlay.getVirtualEdge(i * 4 + SNAP_BASE),
                    queryOverlay.getVirtualEdge(i * 4 + SNAP_ADJ)
            );
            virtualEdgesAtVirtualNodes.add(virtualEdges);
        }
        return virtualEdgesAtVirtualNodes;
    }

    @Override
    public AllEdgesIterator getAllEdges() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public EdgeIteratorState edge(int a, int b) {
        throw exc();
    }

    @Override
    public TurnCostStorage getTurnCostStorage() {
        return turnCostStorage;
    }

    @Override
    public Weighting wrapWeighting(Weighting weighting) {
        if (weighting instanceof QueryGraphWeighting)
            return weighting;
        queryOverlay.adjustVirtualWeights(baseGraph, weighting);
        return new QueryGraphWeighting(baseGraph, weighting, queryOverlay.getClosestEdges());
    }

    @Override
    public int getOtherNode(int edge, int node) {
        if (isVirtualEdge(edge)) {
            return getEdgeIteratorState(edge, node).getBaseNode();
        }
        return baseGraph.getOtherNode(edge, node);
    }

    @Override
    public boolean isAdjacentToNode(int edge, int node) {
        if (isVirtualEdge(edge)) {
            EdgeIteratorState virtualEdge = getEdgeIteratorState(edge, node);
            return virtualEdge.getBaseNode() == node || virtualEdge.getAdjNode() == node;
        }
        return baseGraph.isAdjacentToNode(edge, node);
    }

    List<VirtualEdgeIteratorState> getVirtualEdges() {
        return queryOverlay.getVirtualEdges();
    }

    private UnsupportedOperationException exc() {
        return new UnsupportedOperationException("QueryGraph cannot be modified.");
    }

}
