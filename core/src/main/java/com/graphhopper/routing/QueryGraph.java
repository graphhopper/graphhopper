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
import com.carrotsearch.hppc.predicates.IntObjectPredicate;
import com.carrotsearch.hppc.procedures.IntProcedure;
import com.graphhopper.coll.GHIntHashSet;
import com.graphhopper.coll.GHIntObjectHashMap;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;
import com.graphhopper.util.shapes.GHPoint3D;

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
    // TODO when spreading it on different threads we need multiple independent explorers
    private final Map<Integer, EdgeExplorer> cacheMap = new HashMap<>(4);

    // For every virtual node there are 4 edges: base-snap, snap-base, snap-adj, adj-snap.
    List<VirtualEdgeIteratorState> virtualEdges;
    private List<QueryResult> queryResults;
    /**
     * Store lat,lon of virtual tower nodes.
     */
    private PointList virtualNodes;
    private final NodeAccess nodeAccess = new NodeAccess() {
        @Override
        public void ensureNode(int nodeId) {
            mainNodeAccess.ensureNode(nodeId);
        }

        @Override
        public boolean is3D() {
            return mainNodeAccess.is3D();
        }

        @Override
        public int getDimension() {
            return mainNodeAccess.getDimension();
        }

        @Override
        public double getLatitude(int nodeId) {
            if (isVirtualNode(nodeId))
                return virtualNodes.getLatitude(nodeId - mainNodes);
            return mainNodeAccess.getLatitude(nodeId);
        }

        @Override
        public double getLongitude(int nodeId) {
            if (isVirtualNode(nodeId))
                return virtualNodes.getLongitude(nodeId - mainNodes);
            return mainNodeAccess.getLongitude(nodeId);
        }

        @Override
        public double getElevation(int nodeId) {
            if (isVirtualNode(nodeId))
                return virtualNodes.getElevation(nodeId - mainNodes);
            return mainNodeAccess.getElevation(nodeId);
        }

        @Override
        public int getAdditionalNodeField(int nodeId) {
            if (isVirtualNode(nodeId))
                return 0;
            return mainNodeAccess.getAdditionalNodeField(nodeId);
        }

        @Override
        public void setNode(int nodeId, double lat, double lon) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void setNode(int nodeId, double lat, double lon, double ele) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void setAdditionalNodeField(int nodeId, int additionalValue) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public double getLat(int nodeId) {
            return getLatitude(nodeId);
        }

        @Override
        public double getLon(int nodeId) {
            return getLongitude(nodeId);
        }

        @Override
        public double getEle(int nodeId) {
            return getElevation(nodeId);
        }
    };

    // Use LinkedHashSet for predictable iteration order.
    private final Set<VirtualEdgeIteratorState> unfavoredEdges = new LinkedHashSet<>(5);

    private boolean useEdgeExplorerCache = false;

    public QueryGraph(Graph graph) {
        mainGraph = graph;
        mainNodeAccess = graph.getNodeAccess();
        mainNodes = graph.getNodes();
        mainEdges = graph.getAllEdges().length();

        if (mainGraph.getExtension() instanceof TurnCostExtension)
            wrappedExtension = new QueryGraphTurnExt();
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
    }

    /**
     * Convenient method to initialize this QueryGraph with the two specified query results.
     *
     * @see #lookup(List)
     */
    public QueryGraph lookup(QueryResult fromRes, QueryResult toRes) {
        List<QueryResult> results = new ArrayList<>(2);
        results.add(fromRes);
        results.add(toRes);
        lookup(results);
        return this;
    }

    /**
     * For all specified query results calculate snapped point and if necessary set closest node
     * to a virtual one and reverse closest edge. Additionally the wayIndex can change if an edge is
     * swapped.
     *
     * @see QueryGraph
     */
    public void lookup(List<QueryResult> resList) {
        if (isInitialized())
            throw new IllegalStateException("Call lookup only once. Otherwise you'll have problems for queries sharing the same edge.");

        // initialize all none-final variables
        virtualEdges = new ArrayList<>(resList.size() * 2);
        virtualNodes = new PointList(resList.size(), mainNodeAccess.is3D());
        queryResults = new ArrayList<>(resList.size());
        baseGraph.virtualEdges = virtualEdges;
        baseGraph.virtualNodes = virtualNodes;
        baseGraph.queryResults = queryResults;

        GHIntObjectHashMap<List<QueryResult>> edge2res = new GHIntObjectHashMap<>(resList.size());

        // Phase 1
        // calculate snapped point and swap direction of closest edge if necessary
        for (QueryResult res : resList) {
            // Do not create virtual node for a query result if it is directly on a tower node or not found
            if (res.getSnappedPosition() == QueryResult.Position.TOWER)
                continue;

            EdgeIteratorState closestEdge = res.getClosestEdge();
            if (closestEdge == null)
                throw new IllegalStateException("Do not call QueryGraph.lookup with invalid QueryResult " + res);

            int base = closestEdge.getBaseNode();

            // Force the identical direction for all closest edges.
            // It is important to sort multiple results for the same edge by its wayIndex
            boolean doReverse = base > closestEdge.getAdjNode();
            if (base == closestEdge.getAdjNode()) {
                // check for special case #162 where adj == base and force direction via latitude comparison
                PointList pl = closestEdge.fetchWayGeometry(0);
                if (pl.size() > 1)
                    doReverse = pl.getLatitude(0) > pl.getLatitude(pl.size() - 1);
            }

            if (doReverse) {
                closestEdge = closestEdge.detach(true);
                PointList fullPL = closestEdge.fetchWayGeometry(3);
                res.setClosestEdge(closestEdge);
                if (res.getSnappedPosition() == QueryResult.Position.PILLAR)
                    // ON pillar node
                    res.setWayIndex(fullPL.getSize() - res.getWayIndex() - 1);
                else
                    // for case "OFF pillar node"
                    res.setWayIndex(fullPL.getSize() - res.getWayIndex() - 2);

                if (res.getWayIndex() < 0)
                    throw new IllegalStateException("Problem with wayIndex while reversing closest edge:" + closestEdge + ", " + res);
            }

            // find multiple results on same edge
            int edgeId = closestEdge.getEdge();
            List<QueryResult> list = edge2res.get(edgeId);
            if (list == null) {
                list = new ArrayList<>(5);
                edge2res.put(edgeId, list);
            }
            list.add(res);
        }

        // Phase 2 - now it is clear which points cut one edge
        // 1. create point lists
        // 2. create virtual edges between virtual nodes and its neighbor (virtual or normal nodes)
        edge2res.forEach(new IntObjectPredicate<List<QueryResult>>() {
            @Override
            public boolean apply(int edgeId, List<QueryResult> results) {
                // we can expect at least one entry in the results
                EdgeIteratorState closestEdge = results.get(0).getClosestEdge();
                final PointList fullPL = closestEdge.fetchWayGeometry(3);
                int baseNode = closestEdge.getBaseNode();
                // sort results on the same edge by the wayIndex and if equal by distance to pillar node
                Collections.sort(results, new Comparator<QueryResult>() {
                    @Override
                    public int compare(QueryResult o1, QueryResult o2) {
                        int diff = o1.getWayIndex() - o2.getWayIndex();
                        if (diff == 0) {
                            // sort by distance from snappedPoint to fullPL.get(wayIndex) if wayIndex is identical
                            GHPoint p1 = o1.getSnappedPoint();
                            GHPoint p2 = o2.getSnappedPoint();
                            if (p1.equals(p2))
                                return 0;

                            double fromLat = fullPL.getLatitude(o1.getWayIndex());
                            double fromLon = fullPL.getLongitude(o1.getWayIndex());
                            if (Helper.DIST_PLANE.calcNormalizedDist(fromLat, fromLon, p1.lat, p1.lon)
                                    > Helper.DIST_PLANE.calcNormalizedDist(fromLat, fromLon, p2.lat, p2.lon))
                                return 1;
                            return -1;
                        }
                        return diff;
                    }
                });

                GHPoint3D prevPoint = fullPL.toGHPoint(0);
                int adjNode = closestEdge.getAdjNode();
                int origEdgeKey = GHUtility.createEdgeKey(baseNode, adjNode, closestEdge.getEdge(), false);
                int origRevEdgeKey = GHUtility.createEdgeKey(baseNode, adjNode, closestEdge.getEdge(), true);
                int prevWayIndex = 1;
                int prevNodeId = baseNode;
                int virtNodeId = virtualNodes.getSize() + mainNodes;
                boolean addedEdges = false;

                // Create base and adjacent PointLists for all none-equal virtual nodes.
                // We do so via inserting them at the correct position of fullPL and cutting the
                // fullPL into the right pieces.
                for (int counter = 0; counter < results.size(); counter++) {
                    QueryResult res = results.get(counter);
                    if (res.getClosestEdge().getBaseNode() != baseNode)
                        throw new IllegalStateException("Base nodes have to be identical but were not: " + closestEdge + " vs " + res.getClosestEdge());

                    GHPoint3D currSnapped = res.getSnappedPoint();

                    // no new virtual nodes if exactly the same snapped point
                    if (prevPoint.equals(currSnapped)) {
                        res.setClosestNode(prevNodeId);
                        continue;
                    }

                    queryResults.add(res);
                    boolean isPillar = res.getSnappedPosition() == QueryResult.Position.PILLAR;
                    createEdges(origEdgeKey, origRevEdgeKey,
                            prevPoint, prevWayIndex, isPillar,
                            res.getSnappedPoint(), res.getWayIndex(),
                            fullPL, closestEdge, prevNodeId, virtNodeId);

                    virtualNodes.add(currSnapped.lat, currSnapped.lon, currSnapped.ele);

                    // add edges again to set adjacent edges for newVirtNodeId
                    if (addedEdges) {
                        virtualEdges.add(virtualEdges.get(virtualEdges.size() - 2));
                        virtualEdges.add(virtualEdges.get(virtualEdges.size() - 2));
                    }

                    addedEdges = true;
                    res.setClosestNode(virtNodeId);
                    prevNodeId = virtNodeId;
                    prevWayIndex = res.getWayIndex() + 1;
                    prevPoint = currSnapped;
                    virtNodeId++;
                }

                // two edges between last result and adjacent node are still missing if not all points skipped
                if (addedEdges)
                    createEdges(origEdgeKey, origRevEdgeKey,
                            prevPoint, prevWayIndex, false,
                            fullPL.toGHPoint(fullPL.getSize() - 1), fullPL.getSize() - 2,
                            fullPL, closestEdge, virtNodeId - 1, adjNode);

                return true;
            }
        });
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
     * locations ("hundreds") for one QueryGraph. It can make problems for custom or threaded
     * algorithms or when using custom EdgeFilters for EdgeExplorer creation. Another limitation is
     * that the same edge explorer is used even if a different vehicle/flagEncoder is chosen.
     * Currently we can cache only the ALL_EDGES filter or instances of the DefaultEdgeFilter where
     * three edge explorers will be created: forward OR backward OR both.
     */
    public QueryGraph setUseEdgeExplorerCache(boolean useEECache) {
        this.useEdgeExplorerCache = useEECache;
        this.baseGraph.setUseEdgeExplorerCache(useEECache);
        return this;
    }

    private void createEdges(int origEdgeKey, int origRevEdgeKey,
                             GHPoint3D prevSnapped, int prevWayIndex, boolean isPillar, GHPoint3D currSnapped, int wayIndex,
                             PointList fullPL, EdgeIteratorState closestEdge,
                             int prevNodeId, int nodeId) {
        int max = wayIndex + 1;
        PointList basePoints = new PointList(max - prevWayIndex + 1, mainNodeAccess.is3D());
        basePoints.add(prevSnapped.lat, prevSnapped.lon, prevSnapped.ele);
        for (int i = prevWayIndex; i < max; i++) {
            basePoints.add(fullPL, i);
        }
        if (!isPillar) {
            basePoints.add(currSnapped.lat, currSnapped.lon, currSnapped.ele);
        }
        // basePoints must have at least the size of 2 to make sure fetchWayGeometry(3) returns at least 2
        assert basePoints.size() >= 2 : "basePoints must have at least two points";

        PointList baseReversePoints = basePoints.clone(true);
        double baseDistance = basePoints.calcDistance(Helper.DIST_PLANE);
        int virtEdgeId = mainEdges + virtualEdges.size();

        boolean reverse = closestEdge.get(EdgeIteratorState.REVERSE_STATE);
        // edges between base and snapped point
        VirtualEdgeIteratorState baseEdge = new VirtualEdgeIteratorState(origEdgeKey,
                virtEdgeId, prevNodeId, nodeId, baseDistance, closestEdge.getFlags(), closestEdge.getName(), basePoints, reverse);
        VirtualEdgeIteratorState baseReverseEdge = new VirtualEdgeIteratorState(origRevEdgeKey,
                virtEdgeId, nodeId, prevNodeId, baseDistance, IntsRef.deepCopyOf(closestEdge.getFlags()), closestEdge.getName(), baseReversePoints, !reverse);

        baseEdge.setReverseEdge(baseReverseEdge);
        baseReverseEdge.setReverseEdge(baseEdge);
        virtualEdges.add(baseEdge);
        virtualEdges.add(baseReverseEdge);
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
            int counter = -1;
            if (edgeFilter instanceof DefaultEdgeFilter) {
                DefaultEdgeFilter dee = (DefaultEdgeFilter) edgeFilter;
                counter = 0;
                if (dee.acceptsBackward())
                    counter = 1;
                if (dee.acceptsForward())
                    counter += 2;

                if (counter == 0)
                    throw new IllegalStateException("You tried to use an edge filter blocking every access");

            } else if (edgeFilter == EdgeFilter.ALL_EDGES) {
                counter = 4;
            }

            if (counter >= 0) {
                EdgeExplorer cached = cacheMap.get(counter);
                if (cached == null) {
                    cached = createUncachedEdgeExplorer(edgeFilter);
                    cacheMap.put(counter, cached);
                }
                return cached;
            }
        }
        return createUncachedEdgeExplorer(edgeFilter);
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
    /**
     * @see QueryGraph
     */
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

    private UnsupportedOperationException exc() {
        return new UnsupportedOperationException("QueryGraph cannot be modified.");
    }

    class QueryGraphTurnExt extends TurnCostExtension {
        private final TurnCostExtension mainTurnExtension;

        public QueryGraphTurnExt() {
            this.mainTurnExtension = (TurnCostExtension) mainGraph.getExtension();
        }

        @Override
        public long getTurnCostFlags(int edgeFrom, int nodeVia, int edgeTo) {
            if (isVirtualNode(nodeVia)) {
                return 0;
            } else if (isVirtualEdge(edgeFrom) || isVirtualEdge(edgeTo)) {
                if (isVirtualEdge(edgeFrom)) {
                    edgeFrom = queryResults.get((edgeFrom - mainEdges) / 4).getClosestEdge().getEdge();
                }
                if (isVirtualEdge(edgeTo)) {
                    edgeTo = queryResults.get((edgeTo - mainEdges) / 4).getClosestEdge().getEdge();
                }
                return mainTurnExtension.getTurnCostFlags(edgeFrom, nodeVia, edgeTo);

            } else {
                return mainTurnExtension.getTurnCostFlags(edgeFrom, nodeVia, edgeTo);
            }
        }
    }
}
