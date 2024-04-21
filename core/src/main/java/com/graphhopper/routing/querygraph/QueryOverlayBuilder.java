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

import com.carrotsearch.hppc.predicates.IntObjectPredicate;
import com.graphhopper.coll.GHIntObjectHashMap;
import com.graphhopper.search.KVStorage;
import com.graphhopper.storage.BytesRef;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;
import com.graphhopper.util.shapes.GHPoint3D;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.graphhopper.util.DistancePlaneProjection.DIST_PLANE;

class QueryOverlayBuilder {
    private final int firstVirtualNodeId;
    private final int firstVirtualEdgeId;
    private final boolean is3D;
    private QueryOverlay queryOverlay;

    public static QueryOverlay build(Graph graph, List<Snap> snaps) {
        return build(graph.getNodes(), graph.getEdges(), graph.getNodeAccess().is3D(), snaps);
    }

    public static QueryOverlay build(int firstVirtualNodeId, int firstVirtualEdgeId, boolean is3D, List<Snap> snaps) {
        return new QueryOverlayBuilder(firstVirtualNodeId, firstVirtualEdgeId, is3D).build(snaps);
    }

    private QueryOverlayBuilder(int firstVirtualNodeId, int firstVirtualEdgeId, boolean is3D) {
        this.firstVirtualNodeId = firstVirtualNodeId;
        this.firstVirtualEdgeId = firstVirtualEdgeId;
        this.is3D = is3D;
    }

    private QueryOverlay build(List<Snap> resList) {
        queryOverlay = new QueryOverlay(resList.size(), is3D);
        buildVirtualEdges(resList);
        buildEdgeChangesAtRealNodes();
        return queryOverlay;
    }

    /**
     * For all specified snaps calculate the snapped point and if necessary set the closest node
     * to a virtual one and reverse the closest edge. Additionally the wayIndex can change if an edge is
     * swapped.
     */
    private void buildVirtualEdges(List<Snap> snaps) {
        GHIntObjectHashMap<List<Snap>> edge2res = new GHIntObjectHashMap<>(snaps.size());

        // Phase 1
        // calculate snapped point and swap direction of closest edge if necessary
        for (Snap snap : snaps) {
            // Do not create virtual node for a snap if it is directly on a tower node or not found
            if (snap.getSnappedPosition() == Snap.Position.TOWER)
                continue;

            EdgeIteratorState closestEdge = snap.getClosestEdge();
            if (closestEdge == null)
                throw new IllegalStateException("Do not call QueryGraph.create with invalid Snap " + snap);

            int base = closestEdge.getBaseNode();

            // Force the identical direction for all closest edges.
            // It is important to sort multiple results for the same edge by its wayIndex
            boolean doReverse = base > closestEdge.getAdjNode();
            if (base == closestEdge.getAdjNode()) {
                // check for special case #162 where adj == base and force direction via latitude comparison
                PointList pl = closestEdge.fetchWayGeometry(FetchMode.PILLAR_ONLY);
                if (pl.size() > 1)
                    doReverse = pl.getLat(0) > pl.getLat(pl.size() - 1);
            }

            if (doReverse) {
                closestEdge = closestEdge.detach(true);
                PointList fullPL = closestEdge.fetchWayGeometry(FetchMode.ALL);
                snap.setClosestEdge(closestEdge);
                if (snap.getSnappedPosition() == Snap.Position.PILLAR)
                    // ON pillar node
                    snap.setWayIndex(fullPL.size() - snap.getWayIndex() - 1);
                else
                    // for case "OFF pillar node"
                    snap.setWayIndex(fullPL.size() - snap.getWayIndex() - 2);

                if (snap.getWayIndex() < 0)
                    throw new IllegalStateException("Problem with wayIndex while reversing closest edge:" + closestEdge + ", " + snap);
            }

            // find multiple results on same edge
            int edgeId = closestEdge.getEdge();
            List<Snap> list = edge2res.get(edgeId);
            if (list == null) {
                list = new ArrayList<>(5);
                edge2res.put(edgeId, list);
            }
            list.add(snap);
        }

        // Phase 2 - now it is clear which points cut one edge
        // 1. create point lists
        // 2. create virtual edges between virtual nodes and its neighbor (virtual or normal nodes)
        edge2res.forEach(new IntObjectPredicate<List<Snap>>() {
            @Override
            public boolean apply(int edgeId, List<Snap> results) {
                // we can expect at least one entry in the results
                EdgeIteratorState closestEdge = results.get(0).getClosestEdge();
                final PointList fullPL = closestEdge.fetchWayGeometry(FetchMode.ALL);
                int baseNode = closestEdge.getBaseNode();
                Collections.sort(results, new Comparator<Snap>() {
                    @Override
                    public int compare(Snap o1, Snap o2) {
                        int diff = Integer.compare(o1.getWayIndex(), o2.getWayIndex());
                        if (diff == 0) {
                            return Double.compare(distanceOfSnappedPointToPillarNode(o1), distanceOfSnappedPointToPillarNode(o2));
                        } else {
                            return diff;
                        }
                    }

                    private double distanceOfSnappedPointToPillarNode(Snap o) {
                        GHPoint snappedPoint = o.getSnappedPoint();
                        double fromLat = fullPL.getLat(o.getWayIndex());
                        double fromLon = fullPL.getLon(o.getWayIndex());
                        return DistancePlaneProjection.DIST_PLANE.calcNormalizedDist(fromLat, fromLon, snappedPoint.lat, snappedPoint.lon);
                    }
                });

                GHPoint3D prevPoint = fullPL.get(0);
                int adjNode = closestEdge.getAdjNode();
                int origEdgeKey = closestEdge.getEdgeKey();
                int origRevEdgeKey = closestEdge.getReverseEdgeKey();
                int prevWayIndex = 1;
                int prevNodeId = baseNode;
                int virtNodeId = queryOverlay.getVirtualNodes().size() + firstVirtualNodeId;
                boolean addedEdges = false;

                // Create base and adjacent PointLists for all non-equal virtual nodes.
                // We do so via inserting them at the correct position of fullPL and cutting the
                // fullPL into the right pieces.
                for (int i = 0; i < results.size(); i++) {
                    Snap res = results.get(i);
                    if (res.getClosestEdge().getBaseNode() != baseNode)
                        throw new IllegalStateException("Base nodes have to be identical but were not: " + closestEdge + " vs " + res.getClosestEdge());

                    GHPoint3D currSnapped = res.getSnappedPoint();

                    // no new virtual nodes if very close ("snap" together)
                    if (Snap.considerEqual(prevPoint.lat, prevPoint.lon, currSnapped.lat, currSnapped.lon)) {
                        res.setClosestNode(prevNodeId);
                        res.setSnappedPoint(prevPoint);
                        res.setWayIndex(i == 0 ? 0 : results.get(i - 1).getWayIndex());
                        res.setSnappedPosition(i == 0 ? Snap.Position.TOWER : results.get(i - 1).getSnappedPosition());
                        res.setQueryDistance(DIST_PLANE.calcDist(prevPoint.lat, prevPoint.lon, res.getQueryPoint().lat, res.getQueryPoint().lon));
                        continue;
                    }

                    queryOverlay.getClosestEdges().add(res.getClosestEdge().getEdge());
                    boolean isPillar = res.getSnappedPosition() == Snap.Position.PILLAR;
                    createEdges(origEdgeKey, origRevEdgeKey,
                            prevPoint, prevWayIndex, isPillar,
                            res.getSnappedPoint(), res.getWayIndex(),
                            fullPL, closestEdge, prevNodeId, virtNodeId);

                    queryOverlay.getVirtualNodes().add(currSnapped.lat, currSnapped.lon, currSnapped.ele);

                    // add edges again to set adjacent edges for newVirtNodeId
                    if (addedEdges) {
                        queryOverlay.addVirtualEdge(queryOverlay.getVirtualEdge(queryOverlay.getNumVirtualEdges() - 2));
                        queryOverlay.addVirtualEdge(queryOverlay.getVirtualEdge(queryOverlay.getNumVirtualEdges() - 2));
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
                            fullPL.get(fullPL.size() - 1), fullPL.size() - 2,
                            fullPL, closestEdge, virtNodeId - 1, adjNode);

                return true;
            }
        });
    }

    private void createEdges(int origEdgeKey, int origRevEdgeKey,
                             GHPoint3D prevSnapped, int prevWayIndex, boolean isPillar, GHPoint3D currSnapped, int wayIndex,
                             PointList fullPL, EdgeIteratorState closestEdge,
                             int prevNodeId, int nodeId) {
        int max = wayIndex + 1;
        PointList basePoints = new PointList(max - prevWayIndex + 1, is3D);
        basePoints.add(prevSnapped.lat, prevSnapped.lon, prevSnapped.ele);
        for (int i = prevWayIndex; i < max; i++) {
            basePoints.add(fullPL, i);
        }
        if (!isPillar) {
            basePoints.add(currSnapped.lat, currSnapped.lon, currSnapped.ele);
        }
        // basePoints must have at least the size of 2 to make sure fetchWayGeometry(FetchMode.ALL) returns at least 2
        assert basePoints.size() >= 2 : "basePoints must have at least two points";

        PointList baseReversePoints = basePoints.clone(true);
        double baseDistance = DistancePlaneProjection.DIST_PLANE.calcDistance(basePoints);
        int virtEdgeId = firstVirtualEdgeId + queryOverlay.getNumVirtualEdges() / 2;

        boolean reverse = closestEdge.get(EdgeIteratorState.REVERSE_STATE);
        // edges between base and snapped point
        List<KVStorage.KeyValue> keyValues = closestEdge.getKeyValues();
        VirtualEdgeIteratorState baseEdge = new VirtualEdgeIteratorState(origEdgeKey, GHUtility.createEdgeKey(virtEdgeId, false),
                prevNodeId, nodeId, baseDistance, closestEdge.getFlags(), keyValues, basePoints, reverse);
        VirtualEdgeIteratorState baseReverseEdge = new VirtualEdgeIteratorState(origRevEdgeKey, GHUtility.createEdgeKey(virtEdgeId, true),
                nodeId, prevNodeId, baseDistance, BytesRef.deepCopyOf(closestEdge.getFlags()), keyValues, baseReversePoints, !reverse);

        baseEdge.setReverseEdge(baseReverseEdge);
        baseReverseEdge.setReverseEdge(baseEdge);
        queryOverlay.addVirtualEdge(baseEdge);
        queryOverlay.addVirtualEdge(baseReverseEdge);
    }

    private void buildEdgeChangesAtRealNodes() {
        EdgeChangeBuilder.build(queryOverlay.getClosestEdges(), queryOverlay.getVirtualEdges(), firstVirtualNodeId, queryOverlay.getEdgeChangesAtRealNodes());
    }
}
