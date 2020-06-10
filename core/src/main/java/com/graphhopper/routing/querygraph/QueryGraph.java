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
import com.graphhopper.storage.ExtendedNodeAccess;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.TurnCostStorage;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.shapes.BBox;

import java.util.*;

/**
 * A class which is used to query the underlying graph with real GPS points. It does so by
 * introducing virtual nodes and edges. It is lightweight in order to be created every time a new
 * query comes in, which makes the behaviour thread safe.
 * <p>
 * Calling any <tt>create</tt> method creates virtual edges between the tower nodes of the existing
 * graph and new virtual tower nodes. Every virtual node has two adjacent nodes and is connected
 * to each adjacent nodes via 2 virtual edges with opposite base node / adjacent node encoding.
 * However, the edge explorer returned by {@link #createEdgeExplorer()} only returns two
 * virtual edges per virtual node (the ones with correct base node).
 *
 * @author Peter Karich
 */
public class QueryGraph implements Graph {
    static final int BASE_SNAP = 0, SNAP_BASE = 1, SNAP_ADJ = 2, ADJ_SNAP = 3;
    private final Graph mainGraph;
    private final int mainNodes;
    private final int mainEdges;
    // todo: why do we need this and do we still need it when we stop wrapping CHGraph with QueryGraph?
    private final QueryGraph baseGraph;
    private final TurnCostStorage turnCostStorage;
    private final NodeAccess nodeAccess;
    private final QueryOverlay queryOverlay;

    // Use LinkedHashSet for predictable iteration order.
    private final Set<VirtualEdgeIteratorState> unfavoredEdges = new LinkedHashSet<>(5);
    private final IntObjectMap<List<EdgeIteratorState>> virtualEdgesAtRealNodes;
    private final List<List<EdgeIteratorState>> virtualEdgesAtVirtualNodes;

    public static QueryGraph create(Graph graph, QueryResult qr) {
        return QueryGraph.create(graph, Collections.singletonList(qr));
    }

    public static QueryGraph create(Graph graph, QueryResult fromQR, QueryResult toQR) {
        return QueryGraph.create(graph, Arrays.asList(fromQR, toQR));
    }

    public static QueryGraph create(Graph graph, List<QueryResult> queryResults) {
        return new QueryGraph(graph, queryResults);
    }

    private QueryGraph(Graph graph, List<QueryResult> queryResults) {
        mainGraph = graph;
        mainNodes = graph.getNodes();
        mainEdges = graph.getEdges();

        queryOverlay = QueryOverlayBuilder.build(graph, queryResults);
        nodeAccess = new ExtendedNodeAccess(graph.getNodeAccess(), queryOverlay.getVirtualNodes(), mainNodes);
        turnCostStorage = mainGraph.getTurnCostStorage();

        // build data structures holding the virtual edges at all real/virtual nodes that are modified compared to the
        // mainGraph.
        final EdgeExplorer mainExplorer = mainGraph.createEdgeExplorer();
        virtualEdgesAtRealNodes = buildVirtualEdgesAtRealNodes(mainExplorer);
        virtualEdgesAtVirtualNodes = buildVirtualEdgesAtVirtualNodes();

        // create very lightweight QueryGraph which uses variables from this QueryGraph (same virtual edges)
        baseGraph = new QueryGraph(graph.getBaseGraph(), this);
    }

    /**
     * See {@link #create} for further variables that are initialized
     */
    private QueryGraph(Graph graph, QueryGraph superQueryGraph) {
        mainGraph = graph;
        baseGraph = this;
        turnCostStorage = superQueryGraph.turnCostStorage;
        mainNodes = superQueryGraph.mainNodes;
        mainEdges = superQueryGraph.mainEdges;
        queryOverlay = superQueryGraph.queryOverlay;
        nodeAccess = superQueryGraph.nodeAccess;
        virtualEdgesAtRealNodes = buildVirtualEdgesAtRealNodes(graph.createEdgeExplorer());
        virtualEdgesAtVirtualNodes = buildVirtualEdgesAtVirtualNodes();
    }

    @Override
    public Graph getBaseGraph() {
        // Note: if the mainGraph of this QueryGraph is a CHGraph then ignoring the shortcuts will produce a
        // huge gap of edgeIds between base and virtual edge ids. The only solution would be to move virtual edges
        // directly after normal edge ids which is ugly as we limit virtual edges to N edges and waste memory or make everything more complex.
        return baseGraph;
    }

    public boolean isVirtualEdge(int edgeId) {
        return edgeId >= mainEdges;
    }

    public boolean isVirtualNode(int nodeId) {
        return nodeId >= mainNodes;
    }

    public void unfavorVirtualEdges(IntArrayList edgeIds) {
        for (IntCursor c : edgeIds) {
            unfavorVirtualEdge(c.value);
        }
    }

    /**
     * Assigns the 'unfavored' flag to a virtual edge (for both directions)
     */
    public void unfavorVirtualEdge(int virtualEdgeId) {
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

    /**
     * Returns all virtual edges that have been unfavored via
     * {@link #unfavorVirtualEdge(int)} or {@link #unfavorVirtualEdges(IntArrayList)}
     */
    public Set<EdgeIteratorState> getUnfavoredVirtualEdges() {
        // Need to create a new set to convert Set<VirtualEdgeIteratorState> to
        // Set<EdgeIteratorState>.
        return new LinkedHashSet<EdgeIteratorState>(unfavoredEdges);
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
        return queryOverlay.getVirtualNodes().getSize() + mainNodes;
    }

    @Override
    public int getEdges() {
        return queryOverlay.getNumVirtualEdges() + mainEdges;
    }

    @Override
    public NodeAccess getNodeAccess() {
        return nodeAccess;
    }

    @Override
    public BBox getBounds() {
        return mainGraph.getBounds();
    }

    @Override
    public EdgeIteratorState getEdgeIteratorState(int origEdgeId, int adjNode) {
        if (!isVirtualEdge(origEdgeId))
            return mainGraph.getEdgeIteratorState(origEdgeId, adjNode);

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

    private VirtualEdgeIteratorState getVirtualEdge(int edgeId) {
        return queryOverlay.getVirtualEdge(edgeId);
    }

    private int getPosOfReverseEdge(int edgeId) {
        // find reverse edge via convention. see virtualEdges comment above
        return edgeId % 2 == 0 ? edgeId + 1 : edgeId - 1;
    }

    private int getInternalVirtualEdgeId(int origEdgeId) {
        return 2 * (origEdgeId - mainEdges);
    }

    @Override
    public EdgeExplorer createEdgeExplorer(final EdgeFilter edgeFilter) {
        // re-use these objects between setBaseNode calls to prevent GC
        final EdgeExplorer mainExplorer = mainGraph.createEdgeExplorer(edgeFilter);
        final VirtualEdgeIterator virtualEdgeIterator = new VirtualEdgeIterator(edgeFilter, null);
        return new EdgeExplorer() {
            @Override
            public EdgeIterator setBaseNode(int baseNode) {
                if (isVirtualNode(baseNode)) {
                    List<EdgeIteratorState> virtualEdges = virtualEdgesAtVirtualNodes.get(baseNode - mainNodes);
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
    public EdgeExplorer createEdgeExplorer() {
        return createEdgeExplorer(EdgeFilter.ALL_EDGES);
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
    public EdgeIteratorState edge(int a, int b, double distance, boolean bothDirections) {
        throw exc();
    }

    @Override
    public Graph copyTo(Graph g) {
        throw exc();
    }

    @Override
    public TurnCostStorage getTurnCostStorage() {
        return turnCostStorage;
    }

    @Override
    public Weighting wrapWeighting(Weighting weighting) {
        return new QueryGraphWeighting(weighting, mainGraph.getNodes(), mainGraph.getEdges(), queryOverlay.getClosestEdges());
    }

    @Override
    public int getOtherNode(int edge, int node) {
        if (isVirtualEdge(edge)) {
            return getEdgeIteratorState(edge, node).getBaseNode();
        }
        return mainGraph.getOtherNode(edge, node);
    }

    @Override
    public boolean isAdjacentToNode(int edge, int node) {
        if (isVirtualEdge(edge)) {
            EdgeIteratorState virtualEdge = getEdgeIteratorState(edge, node);
            return virtualEdge.getBaseNode() == node || virtualEdge.getAdjNode() == node;
        }
        return mainGraph.isAdjacentToNode(edge, node);
    }

    List<VirtualEdgeIteratorState> getVirtualEdges() {
        return queryOverlay.getVirtualEdges();
    }

    private UnsupportedOperationException exc() {
        return new UnsupportedOperationException("QueryGraph cannot be modified.");
    }

    public Graph getMainGraph() {
        return mainGraph;
    }

    public int shiftEdgeId(int edgeId) {
        // the restricted edge ids are determined on the (base) query graph and to use them with CH we need to shift
        // them if they are virtual (the virtual edge ids start after the shortcut ids).
        if (edgeId < 0)
            return edgeId;
        if (edgeId >= mainGraph.getBaseGraph().getEdges()) {
            // this is a virtual edge on the base graph
            return edgeId + (mainGraph.getEdges() - mainGraph.getBaseGraph().getEdges());
        }
        return edgeId;
    }
}