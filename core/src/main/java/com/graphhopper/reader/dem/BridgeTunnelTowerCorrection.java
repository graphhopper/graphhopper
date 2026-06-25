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
package com.graphhopper.reader.dem;

import com.carrotsearch.hppc.DoubleArrayList;
import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntDoubleHashMap;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.carrotsearch.hppc.cursors.IntDoubleCursor;
import com.graphhopper.apache.commons.collections.IntFloatBinaryHeap;
import com.graphhopper.coll.GHBitSet;
import com.graphhopper.coll.GHTBitSet;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadEnvironment;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.BitSet;

/**
 * Fixes the DEM elevation of bridge/tunnel/ferry tower-end nodes from the surrounding road,
 * then re-interpolates the pillars on the adjacent ramp edges.
 * <p>
 * The DEM at a bridge end often hits the valley/river below the deck (too low), at a tunnel end
 * the surface above (too high); the surrounding road is the correct anchor. For each
 * structure-touching tower we run Dijkstra (≤ {@link #MAX_DIST_M}) over non-structure edges,
 * sampling only pure-ground nodes — walking past other structure nodes so shared/parallel bridges
 * don't stop the search — and set the tower to the inverse-distance-weighted mean (IDW) of those
 * samples. An edge that overshoots the budget is sampled at the cutoff.
 * <p>
 * An IDW mean cannot leave the sample range, so a tower at the bottom (or top) of a real gradient
 * would be dragged towards its one-sided samples and spike. We therefore apply a correction only
 * if it does not steepen the road or the deck (see {@link #steepensIncidentEdges}).
 * <p>
 * {@link EdgeElevationInterpolator} runs after this and fills the structure interior (pillars,
 * inner towers) from the corrected outer towers.
 */
public class BridgeTunnelTowerCorrection {

    private static final Logger LOGGER = LoggerFactory.getLogger(BridgeTunnelTowerCorrection.class);

    // How far outward to search via Dijkstra (in meter).
    private static final double MAX_DIST_M = 50.0;
    // Minimum number of pure-ground samples required before we trust the inferred elevation.
    private static final int MIN_ROAD_SAMPLES = 1;

    private final BaseGraph graph;
    private final EnumEncodedValue<RoadEnvironment> roadEnvEnc;

    public BridgeTunnelTowerCorrection(BaseGraph graph, EnumEncodedValue<RoadEnvironment> roadEnvEnc) {
        this.graph = graph;
        this.roadEnvEnc = roadEnvEnc;
    }

    public void execute() {
        final NodeAccess nodeAccess = graph.getNodeAccess();
        final EdgeExplorer explorer = graph.createEdgeExplorer();
        final int numNodes = graph.getNodes();

        StopWatch sw = new StopWatch().start();

        // pendingNodes: structure-touching towers = correction candidates. Bits are cleared for the
        // ones we don't change (skipped, IDW ≈ DEM, or rejected by the guard), so afterwards a set
        // bit means "elevation changed" — which is what the pillar loop needs.
        // groundTouching: nodes with >=1 non-structure edge. Inner towers of a multi-edge structure
        // have none; their DEM is meaningless (re-interpolated later), so the guard ignores them.
        BitSet pendingNodes = new BitSet(numNodes);
        BitSet groundTouching = new BitSet(numNodes);
        AllEdgesIterator allIter = graph.getAllEdges();
        while (allIter.next()) {
            if (isStructureEdge(allIter)) {
                pendingNodes.set(allIter.getBaseNode());
                pendingNodes.set(allIter.getAdjNode());
            } else {
                groundTouching.set(allIter.getBaseNode());
                groundTouching.set(allIter.getAdjNode());
            }
        }

        float initTime = sw.stop().getSeconds();
        sw = new StopWatch().start();

        // For each tower, collect ground samples via Dijkstra and compute the IDW elevation.
        // Corrections are staged in newEles (not applied in place) so the guard below can judge
        // every tower against the same full set of proposals, independent of node order.
        // Scratch buffers are allocated once and clear()ed per tower to avoid GC churn.
        DoubleArrayList sampleEles = new DoubleArrayList();
        DoubleArrayList sampleDists = new DoubleArrayList();
        GHBitSet settled = new GHTBitSet();
        IntDoubleHashMap distFromStart = new IntDoubleHashMap();
        IntFloatBinaryHeap heap = new IntFloatBinaryHeap();
        IntDoubleHashMap newEles = new IntDoubleHashMap();
        int skipped = 0;
        for (int n = 0; n < numNodes; n++) {
            if (!pendingNodes.get(n)) continue;
            sampleEles.clear();
            sampleDists.clear();
            settled.clear();
            distFromStart.clear();
            heap.clear();
            dijkstraCollectRoadSamples(n, nodeAccess, explorer, sampleEles, sampleDists,
                    settled, distFromStart, heap);
            if (sampleEles.size() < MIN_ROAD_SAMPLES) {
                pendingNodes.clear(n);
                skipped++;
                continue;
            }
            // Replace the DEM value outright (no dampening): if it is already consistent IDW ≈ obs,
            // and the threshold below leaves it untouched.
            double newZ = inverseDistanceWeightedMean(sampleEles, sampleDists);
            double obs = nodeAccess.getEle(n);
            if (Math.abs(newZ - obs) > 1e-6)
                newEles.put(n, newZ);
            else
                pendingNodes.clear(n);
        }

        // Guard: drop any correction that would steepen the road/deck (see steepensIncidentEdges).
        // Decisions use the full set of proposals (collect first, then remove) so they don't depend
        // on node order — a valley viaduct's two towers are still lifted together.
        IntArrayList rejected = new IntArrayList();
        for (IntDoubleCursor c : newEles)
            if (steepensIncidentEdges(c.key, c.value, nodeAccess, explorer, newEles, groundTouching))
                rejected.add(c.key);
        for (IntCursor c : rejected) {
            newEles.remove(c.value);
            pendingNodes.clear(c.value);
        }
        for (IntDoubleCursor c : newEles)
            nodeAccess.setNode(c.key, nodeAccess.getLat(c.key), nodeAccess.getLon(c.key), c.value);
        int corrected = newEles.size();

        float dijkstraTime = sw.stop().getSeconds();
        sw = new StopWatch().start();

        // Re-interpolate pillars only on ramp edges whose tower endpoints actually moved (very important filter for performance).
        ElevationInterpolator elevationInterpolator = new ElevationInterpolator();
        AllEdgesIterator edgeIter = graph.getAllEdges();
        while (edgeIter.next()) {
            if (isStructureEdge(edgeIter)) continue;
            if (pendingNodes.get(edgeIter.getBaseNode())
                    || pendingNodes.get(edgeIter.getAdjNode()))
                reinterpolatePillars(edgeIter, nodeAccess, elevationInterpolator);
        }

        LOGGER.info("BridgeTunnelTowerCorrection: corrected {} towers, skipped {} (insufficient road samples), " +
                        "rejected {} (would steepen the road network). init {}s, dijkstra {}s, interpolate {}s",
                corrected, skipped, rejected.size(), (int) initTime, (int) dijkstraTime, (int) sw.stop().getSeconds());
    }

    /**
     * True if setting the tower to {@code newZ} would make the road steeper than the raw DEM — i.e.
     * the "correction" would create a spike rather than remove one — so it is rejected and the DEM
     * kept. This happens at a tower on a real gradient: the structure blocks one side, all samples
     * are uphill, and the IDW pulls a self-consistent tower up.
     * <p>
     * Rejected if the lift increases the steepest slope over either (a) all incident edges, or
     * (b) the deck edges alone. Test (b) is needed because the lift flattens the steep ramp under
     * such a tower, which would hide the deck steepening if only (a) were checked (the Gsollstraße
     * bridges near the B115). Both compare the steepest edge before/after, so a deck whose DEM is
     * already spiky can still be smoothed (a Monaco hillside tunnel), and (a) still keeps a tower
     * whose lift would steepen the uphill ramp.
     * <p>
     * A neighbour with its own proposed correction is judged at that value; inner towers (no ground
     * contact) carry a meaningless DEM and are skipped — they are filled later by
     * {@link EdgeElevationInterpolator}.
     */
    private boolean steepensIncidentEdges(int node, double newZ, NodeAccess nodeAccess, EdgeExplorer explorer,
                                          IntDoubleHashMap newEles, BitSet groundTouching) {
        double obs = nodeAccess.getEle(node);
        double maxBefore = 0, maxAfter = 0, maxDeckBefore = 0, maxDeckAfter = 0;
        EdgeIterator it = explorer.setBaseNode(node);
        while (it.next()) {
            double dist = it.getDistance();
            if (dist < 1) continue;
            int adj = it.getAdjNode();
            if (!groundTouching.get(adj)) continue;
            double adjObs = nodeAccess.getEle(adj);
            double adjNew = newEles.getOrDefault(adj, adjObs);
            double slopeBefore = Math.abs(adjObs - obs) / dist;
            double slopeAfter = Math.abs(adjNew - newZ) / dist;
            maxBefore = Math.max(maxBefore, slopeBefore);
            maxAfter = Math.max(maxAfter, slopeAfter);
            if (isStructureEdge(it)) {
                maxDeckBefore = Math.max(maxDeckBefore, slopeBefore);
                maxDeckAfter = Math.max(maxDeckAfter, slopeAfter);
            }
        }
        return maxAfter > maxBefore + 1e-9 || maxDeckAfter > maxDeckBefore + 1e-9;
    }

    /**
     * Collect pure-ground node elevations within {@link #MAX_DIST_M} via Dijkstra, so the IDW weights
     * and the distance cutoff use each node's true shortest-path distance from the tower. Whether a
     * node touches a structure is checked inline at settle time.
     */
    private void dijkstraCollectRoadSamples(int startTower,
                                            NodeAccess nodeAccess, EdgeExplorer explorer,
                                            DoubleArrayList sampleEles, DoubleArrayList sampleDists,
                                            GHBitSet settled, IntDoubleHashMap distFromStart,
                                            IntFloatBinaryHeap heap) {
        distFromStart.put(startTower, 0.0);
        heap.insert(0.0, startTower);

        while (!heap.isEmpty()) {
            int n = heap.poll();
            double dN = distFromStart.get(n); // full-precision settled distance (heap key is only a float ordering hint)

            if (settled.contains(n)) continue; // stale entry — n was already settled via a shorter path
            settled.add(n);

            EdgeIterator it = explorer.setBaseNode(n);
            boolean nodeTouchesStructure = false;
            while (it.next()) {
                if (isStructureEdge(it)) {
                    nodeTouchesStructure = true;
                    continue;
                }
                int adj = it.getAdjNode();
                double edgeDist = it.getDistance();
                double newDist = dN + edgeDist;
                if (newDist > MAX_DIST_M) {
                    // Edge overshoots: sample along way-geometry at the budget cutoff.
                    double remaining = MAX_DIST_M - dN;
                    if (remaining > 0) {
                        double virtualEle = sampleEleAlongEdge(it, remaining);
                        if (!Double.isNaN(virtualEle)) {
                            sampleEles.add(virtualEle);
                            sampleDists.add(dN + remaining);
                        }
                    }
                    continue;
                }
                if (!settled.contains(adj)
                        && (!distFromStart.containsKey(adj) || newDist < distFromStart.get(adj))) {
                    distFromStart.put(adj, newDist);
                    // Enqueue structure nodes too (walk past them) so parallel bridges sharing a tower
                    // (e.g. the Albertbrücke) don't stop the search; sampling is decided per settled node.
                    heap.insert(newDist, adj);
                }
            }
            // Sample n only if it is pure ground (the start tower touches a structure, so it's excluded).
            if (!nodeTouchesStructure) {
                sampleEles.add(nodeAccess.getEle(n));
                sampleDists.add(dN);
            }
        }
    }

    /**
     * Inverse-distance-squared weighting: closer samples dominate, so far ones don't drag the result
     * away from the road right next to the bridge end on steadily climbing/descending terrain.
     */
    private static double inverseDistanceWeightedMean(DoubleArrayList eles, DoubleArrayList dists) {
        double weightedSum = 0, totalWeight = 0;
        for (int i = 0; i < eles.size(); i++) {
            double d = Math.max(dists.get(i), 1.0);
            double w = 1.0 / (d * d);
            weightedSum += w * eles.get(i);
            totalWeight += w;
        }
        return weightedSum / totalWeight;
    }

    /**
     * Sample the elevation at a given distance along an edge's way-geometry,
     * linearly interpolating between the two surrounding pillar/tower points.
     * Returns NaN if the edge geometry is unusable.
     */
    private double sampleEleAlongEdge(EdgeIteratorState edge, double distAlongEdge) {
        PointList pl = edge.fetchWayGeometry(FetchMode.ALL);
        if (pl.size() < 2) return Double.NaN;
        double cum = 0;
        for (int i = 0; i < pl.size() - 1; i++) {
            double segLen = DistancePlaneProjection.DIST_PLANE.calcDist(
                    pl.getLat(i), pl.getLon(i), pl.getLat(i + 1), pl.getLon(i + 1));
            if (cum + segLen >= distAlongEdge) {
                double frac = segLen > 0 ? (distAlongEdge - cum) / segLen : 0;
                return pl.getEle(i) + frac * (pl.getEle(i + 1) - pl.getEle(i));
            }
            cum += segLen;
        }
        return pl.getEle(pl.size() - 1);
    }

    /**
     * Re-interpolate pillar nodes on a non-structure edge linearly between its two
     * tower endpoints. Mirrors {@link EdgeElevationInterpolator}'s Phase 2 for
     * bridge/tunnel edges. Also recomputes the edge distance.
     */
    private void reinterpolatePillars(EdgeIteratorState edge, NodeAccess nodeAccess,
                                      ElevationInterpolator elevationInterpolator) {
        int firstNodeId = edge.getBaseNode();
        int secondNodeId = edge.getAdjNode();
        double lat0 = nodeAccess.getLat(firstNodeId);
        double lon0 = nodeAccess.getLon(firstNodeId);
        double ele0 = nodeAccess.getEle(firstNodeId);
        double lat1 = nodeAccess.getLat(secondNodeId);
        double lon1 = nodeAccess.getLon(secondNodeId);
        double ele1 = nodeAccess.getEle(secondNodeId);

        // Mutate the fetched PointList in place (mirrors EdgeElevationInterpolator). Always recompute
        // the distance — even with no pillars a tower endpoint's elevation may have changed.
        PointList pointList = edge.fetchWayGeometry(FetchMode.ALL);
        int count = pointList.size();
        for (int index = 1; index < count - 1; index++) {
            double lat = pointList.getLat(index);
            double lon = pointList.getLon(index);
            double ele = elevationInterpolator.calculateElevationBasedOnTwoPoints(lat, lon,
                    lat0, lon0, ele0, lat1, lon1, ele1);
            pointList.set(index, lat, lon, ele);
        }
        if (count > 2)
            edge.setWayGeometry(pointList.shallowCopy(1, count - 1, false));
        edge.setDistance(DistanceCalcEarth.DIST_EARTH.calcDistance(pointList));
    }

    private boolean isStructureEdge(EdgeIteratorState edge) {
        RoadEnvironment re = edge.get(roadEnvEnc);
        return re == RoadEnvironment.BRIDGE || re == RoadEnvironment.TUNNEL || re == RoadEnvironment.FERRY;
    }

}
