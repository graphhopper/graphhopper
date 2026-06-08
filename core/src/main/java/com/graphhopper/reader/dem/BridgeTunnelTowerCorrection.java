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
import com.carrotsearch.hppc.IntDoubleHashMap;
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
import java.util.PriorityQueue;

/**
 * Corrects elevations of bridge/tunnel/ferry tower-end nodes from the surrounding road
 * network, then re-interpolates pillars on the adjacent ramp edges so the slope at the
 * structure end is smoothed.
 * <p>
 * DEM at a bridge end often samples the valley/river below the deck (too low); at a tunnel
 * end the surface above (too high). The surrounding road is on solid ground at the right
 * level, so its elevations are the correct anchor. For each structure-touching tower we
 * run Dijkstra outward (≤ {@link #MAX_DIST_M}) over non-structure edges, sample only
 * pure-ground nodes (no structure incidence) — walking *past* other structure-touching
 * nodes so that parallel bridges/tunnels sharing the tower don't terminate the traversal —
 * and replace the tower's elevation with the inverse-distance-weighted mean of the samples
 * (Shepard's IDW). Using shortest-path distances ensures the distance budget and IDW weights
 * are always correct regardless of traversal order.
 * If a single edge would overshoot the budget, we sample along its way-geometry at the
 * cutoff so sparse graphs still produce a representative sample.
 * <p>
 * After tower correction, we re-interpolate the pillar nodes on each adjacent ramp edge
 * linearly between the two endpoints — this kills the slope artifact at the approach when
 * ramp pillars had bad DEM.
 * <p>
 * {@link EdgeElevationInterpolator} should run AFTER this class — it interpolates the
 * structure interior (bridge/tunnel pillars, inner tower nodes) from the now-correct
 * outer values.
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

        // pendingNodes starts as the set of structure-touching towers, i.e. candidates where we
        // might correct the elevation. During the correction loop, we clear() any candidate that
        // ended up not actually corrected (skipped or IDW ≈ obs). After the loop, a set bit means
        // "this node's elevation was changed", which is what the pillar loop needs.
        BitSet pendingNodes = new BitSet(numNodes);
        AllEdgesIterator allIter = graph.getAllEdges();
        while (allIter.next()) {
            if (isStructureEdge(allIter)) {
                pendingNodes.set(allIter.getBaseNode());
                pendingNodes.set(allIter.getAdjNode());
            }
        }

        float initTime = sw.stop().getSeconds();
        sw = new StopWatch().start();

        // For every structure-touching tower, collect elevation samples via Dijkstra and decide
        // the corrected elevation. Apply in place — the traversal only samples pure-ground nodes
        // (those that do not touch any structure edge), so already-corrected neighbouring
        // towers cannot contaminate later results.
        // Scratch buffers allocated once and clear()ed per run — saves ~5 allocations per
        // candidate tower (hundreds of thousands on a country graph).
        DoubleArrayList sampleEles = new DoubleArrayList();
        DoubleArrayList sampleDists = new DoubleArrayList();
        GHBitSet settled = new GHTBitSet();
        IntDoubleHashMap distFromStart = new IntDoubleHashMap();
        PriorityQueue<double[]> pq = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        int corrected = 0, skipped = 0;
        for (int n = 0; n < numNodes; n++) {
            if (!pendingNodes.get(n)) continue;
            sampleEles.clear();
            sampleDists.clear();
            settled.clear();
            distFromStart.clear();
            pq.clear();
            dijkstraCollectRoadSamples(n, nodeAccess, explorer, sampleEles, sampleDists,
                    settled, distFromStart, pq);
            if (sampleEles.size() < MIN_ROAD_SAMPLES) {
                pendingNodes.clear(n);
                skipped++;
                continue;
            }
            // Fully replace the DEM value (no soft-trust dampening). If DEM is already
            // consistent with surroundings, IDW ≈ obs and nothing visibly moves anyway;
            // a smaller threshold previously let ~1 m underbias slip through.
            double newZ = inverseDistanceWeightedMean(sampleEles, sampleDists);
            double obs = nodeAccess.getEle(n);
            if (Math.abs(newZ - obs) > 1e-6) {
                nodeAccess.setNode(n, nodeAccess.getLat(n), nodeAccess.getLon(n), newZ);
                corrected++;
            } else {
                pendingNodes.clear(n);
            }
        }

        float dijkstraTime = sw.stop().getSeconds();
        sw = new StopWatch().start();

        // Re-interpolate pillars only on ramp edges whose tower endpoints actually moved.
        // If neither endpoint changed, the stored geometry is still consistent — re-running
        // linear interpolation would just destroy any real DEM variation along the ramp.
        ElevationInterpolator elevationInterpolator = new ElevationInterpolator();
        AllEdgesIterator edgeIter = graph.getAllEdges();
        while (edgeIter.next()) {
            if (isStructureEdge(edgeIter)) continue;
            if (pendingNodes.get(edgeIter.getBaseNode())
                    || pendingNodes.get(edgeIter.getAdjNode()))
                reinterpolatePillars(edgeIter, nodeAccess, elevationInterpolator);
        }

        LOGGER.info("BridgeTunnelTowerCorrection: corrected {} towers, skipped {} (insufficient road samples). " +
                        "init {}s, dijkstra {}s, interpolate {}s",
                corrected, skipped, (int) initTime, (int) dijkstraTime, (int) sw.stop().getSeconds());
    }

    /**
     * Sample pure-ground node elevations via Dijkstra. Using a
     * priority queue ordered by accumulated distance guarantees that {@code distFromStart}
     * holds the true shortest-path distances, so the IDW weights and the
     * {@link #MAX_DIST_M} cutoff are always based on the nearest approach to each node.
     * The "n touches structure" check is done inline at settle time, so no
     * precomputed array is needed.
     */
    private void dijkstraCollectRoadSamples(int startTower,
                                            NodeAccess nodeAccess, EdgeExplorer explorer,
                                            DoubleArrayList sampleEles, DoubleArrayList sampleDists,
                                            GHBitSet settled, IntDoubleHashMap distFromStart,
                                            PriorityQueue<double[]> pq) {
        distFromStart.put(startTower, 0.0);
        pq.offer(new double[]{0.0, startTower});

        while (!pq.isEmpty()) {
            double[] top = pq.poll();
            double dN = top[0];
            int n = (int) top[1];

            if (settled.contains(n)) continue; // stale entry — a shorter path was found later
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
                    // Edge overshoots — sample along way-geometry at the budget cutoff.
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
                    // Always enqueue (= walk past), so that structure-touching nodes — outers of
                    // PARALLEL bridges/tunnels sharing this node, common on multi-way bridges
                    // like the Albertbrücke — don't terminate the traversal. Whether they get
                    // sampled is decided when they themselves are settled.
                    pq.offer(new double[]{newDist, adj});
                }
            }
            // Sample the elevation of current node n only if it's pure ground. The start tower is itself
            // structure-touching by construction, so it's naturally excluded.
            if (!nodeTouchesStructure) {
                sampleEles.add(nodeAccess.getEle(n));
                sampleDists.add(dN);
            }
        }
    }

    /**
     * Inverse-distance-squared weighting. Closer neighbours dominate the result —
     * important on terrain that climbs or descends steadily through the Dijkstra window,
     * where a plain 1/d would still let far samples drag the answer away from the
     * road that is immediately adjacent to the bridge end.
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

        // Mutate the fetched PointList in place (mirrors EdgeElevationInterpolator).
        // The distance is always recomputed (even on a 2-point edge with no pillars),
        // because a tower endpoint's elevation may have changed in the correction pass and needs an update.
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
