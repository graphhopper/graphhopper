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
package com.graphhopper.routing;

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntObjectMap;
import com.carrotsearch.hppc.procedures.IntProcedure;
import com.graphhopper.coll.GHIntHashSet;
import com.graphhopper.coll.GHIntObjectHashMap;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphExtension;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.TurnCostExtension;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;

import java.util.*;

/**
 * A class which is used to query the underlying graph with real GPS points. It does so by
 * introducing virtual nodes and edges. It is lightweight in order to be created every time a new
 * query comes in, which makes the behaviour thread safe.
 * <p>
 * Calling any <tt>lookup</tt> method creates virtual edges between the tower nodes of the existing
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
    private final NodeAccess mainNodeAccess;
    private final int mainNodes;
    private final int mainEdges;
    private final QueryGraph baseGraph;
    private final GraphExtension wrappedExtension;
    private final Map<EdgeFilter, EdgeExplorer> cacheMap = new HashMap<>(4);

    // For every virtual node there are 4 edges: base-snap, snap-base, snap-adj, adj-snap.
    final List<VirtualEdgeIteratorState> virtualEdges;
    private final List<QueryResult> queryResults;
    /**
     * Store lat,lon of virtual tower nodes.
     */
    private final PointList virtualNodes;
    private final NodeAccess nodeAccess;

    // Use LinkedHashSet for predictable iteration order.
    private final Set<VirtualEdgeIteratorState> unfavoredEdges = new LinkedHashSet<>(5);

    private boolean useEdgeExplorerCache = false;

    // todonow: maybe add convenience methods for one and two query results ?
    public static QueryGraph lookup(Graph graph, List<QueryResult> queryResults) {
        return new QueryGraph(graph, queryResults);
    }

    public QueryGraph(Graph graph, List<QueryResult> queryResults) {
        mainGraph = graph;
        mainNodeAccess = graph.getNodeAccess();
        mainNodes = graph.getNodes();
        mainEdges = graph.getEdges();

        VirtualEdgeBuilder virtualEdgeBuilder = new VirtualEdgeBuilder(mainNodes, mainEdges, mainNodeAccess.is3D());
        virtualEdgeBuilder.lookup(queryResults);

        virtualEdges = virtualEdgeBuilder.getVirtualEdges();
        virtualNodes = virtualEdgeBuilder.getVirtualNodes();
        this.queryResults = virtualEdgeBuilder.getQueryResults();

        nodeAccess = new ExtendedNodeAccess(mainNodeAccess, virtualNodes, mainNodes);

        if (mainGraph.getExtension() instanceof TurnCostExtension)
            wrappedExtension = new QueryGraphTurnExt(mainGraph, this.queryResults);
        else
            wrappedExtension = mainGraph.getExtension();

        // create very lightweight QueryGraph which uses variables from this QueryGraph (same virtual edges)
        baseGraph = new QueryGraph(graph.getBaseGraph(), this) {
            // override method to avoid stackoverflow
            @Override
            public QueryGraph setUseEdgeExplorerCache(boolean useEECache) {
                baseGraph.useEdgeExplorerCache = useEECache;
                return baseGraph;
            }
        };
    }

    /**
     * See 'lookup' for further variables that are initialized
     */
    private QueryGraph(Graph graph, QueryGraph superQueryGraph) {
        mainGraph = graph;
        baseGraph = this;
        wrappedExtension = superQueryGraph.wrappedExtension;
        mainNodeAccess = graph.getNodeAccess();
        mainNodes = superQueryGraph.mainNodes;
        mainEdges = superQueryGraph.mainEdges;
        virtualEdges = superQueryGraph.virtualEdges;
        virtualNodes = superQueryGraph.virtualNodes;
        queryResults = superQueryGraph.queryResults;
        nodeAccess = superQueryGraph.nodeAccess;
    }

    @Override
    public Graph getBaseGraph() {
        // Note: if the mainGraph of this QueryGraph is a CHGraph then ignoring the shortcuts will produce a
        // huge gap of edgeIds between base and virtual edge ids. The only solution would be to move virtual edges
        // directly after normal edge ids which is ugly as we limit virtual edges to N edges and waste memory or make everything more complex.
        return baseGraph;
    }

    public EdgeIteratorState getOriginalEdgeFromVirtNode(int nodeId) {
        return queryResults.get(nodeId - mainNodes).getClosestEdge();
    }

    public boolean isVirtualEdge(int edgeId) {
        return edgeId >= mainEdges;
    }

    public boolean isVirtualNode(int nodeId) {
        return nodeId >= mainNodes;
    }

    /**
     * This method is an experimental feature to reduce memory and CPU resources if there are many
     * locations ("hundreds") for one QueryGraph. EdgeExplorer instances are cached based on the {@link EdgeFilter}
     * passed into {@link #createEdgeExplorer(EdgeFilter)}. For equal (in the java sense) {@link EdgeFilter}s always
     * the same {@link EdgeExplorer} will be returned when caching is enabled. Care has to be taken for example for
     * custom or threaded algorithms, when using custom {@link EdgeFilter}s, or when the same edge explorer is used
     * with different vehicles/encoders.
     */
    public QueryGraph setUseEdgeExplorerCache(boolean useEECache) {
        this.useEdgeExplorerCache = useEECache;
        this.baseGraph.setUseEdgeExplorerCache(useEECache);
        return this;
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
        if (!isInitialized())
            throw new IllegalStateException("QueryGraph.lookup has to be called in before heading enforcement");

        if (Double.isNaN(favoredHeading))
            return false;

        if (!isVirtualNode(nodeId))
            return false;

        int virtNodeIDintern = nodeId - mainNodes;
        favoredHeading = AC.convertAzimuth2xaxisAngle(favoredHeading);

        // either penalize incoming or outgoing edges
        List<Integer> edgePositions = incoming ? Arrays.asList(VE_BASE, VE_ADJ_REV) : Arrays.asList(VE_BASE_REV, VE_ADJ);
        boolean enforcementOccurred = false;
        for (int edgePos : edgePositions) {
            VirtualEdgeIteratorState edge = virtualEdges.get(virtNodeIDintern * 4 + edgePos);

            PointList wayGeo = edge.fetchWayGeometry(3);
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
                VirtualEdgeIteratorState reverseEdge = virtualEdges.get(virtNodeIDintern * 4 + getPosOfReverseEdge(edgePos));
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
        return virtualNodes.getSize() + mainNodes;
    }

    @Override
    public int getEdges() {
        return virtualEdges.size() + mainEdges;
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
        EdgeIteratorState eis = virtualEdges.get(edgeId);
        if (eis.getAdjNode() == adjNode || adjNode == Integer.MIN_VALUE)
            return eis;
        edgeId = getPosOfReverseEdge(edgeId);

        EdgeIteratorState eis2 = virtualEdges.get(edgeId);
        if (eis2.getAdjNode() == adjNode)
            return eis2;
        throw new IllegalStateException("Edge " + origEdgeId + " not found with adjNode:" + adjNode
                + ". found edges were:" + eis + ", " + eis2);
    }

    private int getPosOfReverseEdge(int edgeId) {
        // find reverse edge via convention. see virtualEdges comment above
        if (edgeId % 2 == 0)
            edgeId++;
        else
            edgeId--;

        return edgeId;
    }

    @Override
    public EdgeExplorer createEdgeExplorer(final EdgeFilter edgeFilter) {
        if (!isInitialized())
            throw new IllegalStateException("Call lookup before using this graph");

        if (useEdgeExplorerCache) {
            EdgeExplorer cached = cacheMap.get(edgeFilter);
            if (cached == null) {
                cached = createUncachedEdgeExplorer(edgeFilter);
                cacheMap.put(edgeFilter, cached);
            }
            return cached;
        } else {
            return createUncachedEdgeExplorer(edgeFilter);
        }
    }

    private EdgeExplorer createUncachedEdgeExplorer(EdgeFilter edgeFilter) {
        // Iteration over virtual nodes needs to be thread safe if done from different explorer
        // so we need to create the mapping on EVERY call!
        // This needs to be a HashMap (and cannot be an array) as we also need to tweak edges for some mainNodes!
        // The more query points we have the more inefficient this map could be. Hmmh.
        final IntObjectMap<VirtualEdgeIterator> node2EdgeMap
                = new GHIntObjectHashMap<>(queryResults.size() * 3);

        final EdgeExplorer mainExplorer = mainGraph.createEdgeExplorer(edgeFilter);
        final GHIntHashSet towerNodesToChange = new GHIntHashSet(queryResults.size());

        // 1. virtualEdges should also get fresh EdgeIterators on every createEdgeExplorer call!
        for (int i = 0; i < queryResults.size(); i++) {
            // create outgoing edges
            VirtualEdgeIterator virtEdgeIter = new VirtualEdgeIterator(2);
            EdgeIteratorState baseRevEdge = virtualEdges.get(i * 4 + VE_BASE_REV);
            if (edgeFilter.accept(baseRevEdge))
                virtEdgeIter.add(baseRevEdge);
            EdgeIteratorState adjEdge = virtualEdges.get(i * 4 + VE_ADJ);
            if (edgeFilter.accept(adjEdge))
                virtEdgeIter.add(adjEdge);

            int virtNode = mainNodes + i;
            node2EdgeMap.put(virtNode, virtEdgeIter);

            // replace edge list of neighboring tower nodes:
            // add virtual edges only and collect tower nodes where real edges will be added in step 2.
            //
            // base node
            int towerNode = baseRevEdge.getAdjNode();
            if (!isVirtualNode(towerNode)) {
                towerNodesToChange.add(towerNode);
                addVirtualEdges(node2EdgeMap, edgeFilter, true, towerNode, i);
            }

            // adj node
            towerNode = adjEdge.getAdjNode();
            if (!isVirtualNode(towerNode)) {
                towerNodesToChange.add(towerNode);
                addVirtualEdges(node2EdgeMap, edgeFilter, false, towerNode, i);
            }
        }

        // 2. the connected tower nodes from mainGraph need fresh EdgeIterators with possible fakes
        // where 'fresh' means independent of previous call and respecting the edgeFilter
        // -> setup fake iterators of detected tower nodes (virtual edges are already added)
        towerNodesToChange.forEach(new IntProcedure() {
            @Override
            public void apply(int value) {
                fillVirtualEdges(node2EdgeMap, value, mainExplorer);
            }
        });

        return new EdgeExplorer() {
            @Override
            public EdgeIterator setBaseNode(int baseNode) {
                VirtualEdgeIterator iter = node2EdgeMap.get(baseNode);
                if (iter != null)
                    return iter.reset();

                return mainExplorer.setBaseNode(baseNode);
            }
        };
    }

    /**
     * Creates a fake edge iterator pointing to multiple edge states.
     */
    private void addVirtualEdges(IntObjectMap<VirtualEdgeIterator> node2EdgeMap, EdgeFilter filter, boolean base,
                                 int node, int virtNode) {
        VirtualEdgeIterator existingIter = node2EdgeMap.get(node);
        if (existingIter == null) {
            existingIter = new VirtualEdgeIterator(10);
            node2EdgeMap.put(node, existingIter);
        }
        EdgeIteratorState edge = base
                ? virtualEdges.get(virtNode * 4 + VE_BASE)
                : virtualEdges.get(virtNode * 4 + VE_ADJ_REV);
        if (filter.accept(edge))
            existingIter.add(edge);
    }

    void fillVirtualEdges(IntObjectMap<VirtualEdgeIterator> node2Edge, int towerNode, EdgeExplorer mainExpl) {
        if (isVirtualNode(towerNode))
            throw new IllegalStateException("Node should not be virtual:" + towerNode + ", " + node2Edge);

        VirtualEdgeIterator vIter = node2Edge.get(towerNode);
        IntArrayList ignoreEdges = new IntArrayList(vIter.count() * 2);
        while (vIter.next()) {
            EdgeIteratorState edge = queryResults.get(vIter.getAdjNode() - mainNodes).getClosestEdge();
            ignoreEdges.add(edge.getEdge());
        }
        vIter.reset();
        EdgeIterator iter = mainExpl.setBaseNode(towerNode);
        while (iter.next()) {
            if (!ignoreEdges.contains(iter.getEdge()))
                vIter.add(iter.detach(false));
        }
    }

    private boolean isInitialized() {
        return queryResults != null;
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

    public EdgeIteratorState edge(int a, int b, double distance, int flags) {
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
    public GraphExtension getExtension() {
        return wrappedExtension;
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

    private UnsupportedOperationException exc() {
        return new UnsupportedOperationException("QueryGraph cannot be modified.");
    }
}
