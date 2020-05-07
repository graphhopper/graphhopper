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

import com.carrotsearch.hppc.IntObjectMap;
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
import com.graphhopper.util.*;
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
    static final int VE_BASE = 0, VE_BASE_REV = 1, VE_ADJ = 2, VE_ADJ_REV = 3;
    private static final AngleCalc AC = Helper.ANGLE_CALC;
    private final Graph mainGraph;
    private final int mainNodes;
    private final int mainEdges;
    // todo: why do we need this and do we still need it when we stop wrapping CHGraph with QueryGraph ?
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

    /**
     * Set those edges at the virtual node (nodeId) to 'unfavored' that require at least a turn of
     * 100° from favoredHeading.
     * <p>
     *
     * @param nodeId         VirtualNode at which edges get unfavored
     * @param favoredHeading north based azimuth of favored heading between 0 and 360
     * @param incoming       if true, incoming edges are unfavored, else outgoing edges
     * @return boolean indicating if enforcement took place
     */
    public boolean enforceHeading(int nodeId, double favoredHeading, boolean incoming) {
        if (Double.isNaN(favoredHeading))
            return false;

        if (!isVirtualNode(nodeId))
            return false;

        int virtNodeIDintern = nodeId - mainNodes;
        favoredHeading = AC.convertAzimuth2xaxisAngle(favoredHeading);

        // either penalize incoming or outgoing edges
        int[] edgePositions = incoming ? new int[]{VE_BASE, VE_ADJ_REV} : new int[]{VE_BASE_REV, VE_ADJ};
        boolean enforcementOccurred = false;
        for (int edgePos : edgePositions) {
            VirtualEdgeIteratorState edge = getVirtualEdge(virtNodeIDintern * 4 + edgePos);

            PointList wayGeo = edge.fetchWayGeometry(FetchMode.ALL);
            double edgeOrientation;
            if (incoming) {
                int numWayPoints = wayGeo.getSize();
                edgeOrientation = AC.calcOrientation(wayGeo.getLat(numWayPoints - 2), wayGeo.getLon(numWayPoints - 2),
                        wayGeo.getLat(numWayPoints - 1), wayGeo.getLon(numWayPoints - 1));
            } else {
                edgeOrientation = AC.calcOrientation(wayGeo.getLat(0), wayGeo.getLon(0),
                        wayGeo.getLat(1), wayGeo.getLon(1));
            }

            edgeOrientation = AC.alignOrientation(favoredHeading, edgeOrientation);
            double delta = (edgeOrientation - favoredHeading);

            if (Math.abs(delta) > 1.74) // penalize if a turn of more than 100°
            {
                edge.setUnfavored(true);
                unfavoredEdges.add(edge);
                //also apply to opposite edge for reverse routing
                VirtualEdgeIteratorState reverseEdge = getVirtualEdge(virtNodeIDintern * 4 + getPosOfReverseEdge(edgePos));
                reverseEdge.setUnfavored(true);
                unfavoredEdges.add(reverseEdge);
                enforcementOccurred = true;
            }

        }
        return enforcementOccurred;
    }

    /**
     * Sets the virtual edge with virtualEdgeId and its reverse edge to 'unfavored', which
     * effectively penalizes both virtual edges towards an adjacent node of virtualNodeId.
     * This makes it more likely (but does not guarantee) that the router chooses a route towards
     * the other adjacent node of virtualNodeId.
     * <p>
     *
     * @param virtualNodeId virtual node at which edges get unfavored
     * @param virtualEdgeId this edge and the reverse virtual edge become unfavored
     */
    public void unfavorVirtualEdgePair(int virtualNodeId, int virtualEdgeId) {
        if (!isVirtualNode(virtualNodeId)) {
            throw new IllegalArgumentException("Node id " + virtualNodeId
                    + " must be a virtual node.");
        }

        VirtualEdgeIteratorState incomingEdge =
                (VirtualEdgeIteratorState) getEdgeIteratorState(virtualEdgeId, virtualNodeId);
        VirtualEdgeIteratorState reverseEdge = (VirtualEdgeIteratorState) getEdgeIteratorState(
                virtualEdgeId, incomingEdge.getBaseNode());
        incomingEdge.setUnfavored(true);
        unfavoredEdges.add(incomingEdge);
        reverseEdge.setUnfavored(true);
        unfavoredEdges.add(reverseEdge);
    }

    /**
     * Returns all virtual edges that have been unfavored via
     * {@link #enforceHeading(int, double, boolean)} or {@link #unfavorVirtualEdgePair(int, int)}.
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

        int edgeId = origEdgeId - mainEdges;
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
        final int[] vEdges = {VE_BASE_REV, VE_ADJ};
        for (int i = 0; i < queryOverlay.getVirtualNodes().size(); i++) {
            List<EdgeIteratorState> filteredEdges = new ArrayList<>(2);
            for (int vEdge : vEdges) {
                filteredEdges.add(queryOverlay.getVirtualEdge(i * 4 + vEdge));
            }
            virtualEdgesAtVirtualNodes.add(filteredEdges);
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
}