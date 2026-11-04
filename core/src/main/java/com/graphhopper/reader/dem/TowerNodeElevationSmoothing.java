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

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.ev.RoadEnvironment;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;

/**
 * The elevation of a tower node is a single sample of the elevation data. Its noise (and small real features next
 * to the road like a sunken courtyard) directly creates a slope on the adjacent edges, which is large for short
 * edges, e.g. 0.9 m on a 12 m edge are 7.5 %. The edge smoothing (moving average or ramer) only touches pillar
 * nodes, so this class smooths the tower nodes after the import:
 * <ul>
 * <li>The road through a node is the pair of edges with the smallest heading change (below {@link #MAX_TURN}), so
 * side roads, ramps and driveways on a different level do not influence the node. If this pair has no window on
 * both sides, the next straightest pair is used.</li>
 * <li>The new elevation is the mean of the own elevation and the elevation sampled every {@link #STEP} meters along
 * the road within the radius in both directions. At junctions the road continues with the straightest edge.</li>
 * <li>The window is symmetric: if the road ends on one side the other side is cut to the same length, so a dead end
 * keeps its elevation.</li>
 * <li>Steps are a real change of the elevation, so the road ends there. Bridges, tunnels and ferries are
 * interpolated before: the road ends there too and their tower nodes keep their elevation.</li>
 * <li>The pillar nodes within the radius are shifted along with their tower nodes so that the elevation profile
 * stays continuous.</li>
 * </ul>
 * All passes run on several threads.
 */
public class TowerNodeElevationSmoothing {
    private static final Logger LOGGER = LoggerFactory.getLogger(TowerNodeElevationSmoothing.class);
    static final double STEP = 10;
    // max heading change for an edge to count as the continuation of the road at a junction
    static final double MAX_TURN = Math.toRadians(45);
    // headings are stored quantized to 1e-4 rad, plus two markers for edges that are not part of a road
    private static final double HEADING_SCALE = Short.MAX_VALUE / Math.PI;
    // no length or steps
    private static final short SKIP = Short.MIN_VALUE;
    // bridge, tunnel or ferry: additionally their tower nodes keep their elevation
    private static final short INTERPOLATED = Short.MIN_VALUE + 1;

    private final BaseGraph graph;
    private final NodeAccess nodeAccess;
    private final EnumEncodedValue<RoadClass> roadClass;
    private final EnumEncodedValue<RoadEnvironment> roadEnvironment;
    private final double radius;
    private final int threads;
    // number of nodes or edges per task, package-private for tests
    int chunkSize = 100_000;
    // heading of the first segment per edge key, i.e. pointing away from the base node of the edge state, as
    // decoding the geometry is expensive
    private short[] headings;

    /**
     * @param roadClass       to end the road at steps, can be null
     * @param roadEnvironment to end the road at bridges, tunnels and ferries, can be null
     */
    public TowerNodeElevationSmoothing(BaseGraph graph, EnumEncodedValue<RoadClass> roadClass,
                                       EnumEncodedValue<RoadEnvironment> roadEnvironment, double radius, int threads) {
        this.graph = graph;
        this.nodeAccess = graph.getNodeAccess();
        this.roadClass = roadClass;
        this.roadEnvironment = roadEnvironment;
        this.radius = radius;
        this.threads = threads;
    }

    public void execute() {
        StopWatch sw = new StopWatch().start();
        headings = calcHeadings();
        // staged: the smoothing must read the original elevations of the neighbors
        float[] delta = calcDeltas();
        headings = null;
        float smoothTime = sw.stop().getSeconds();

        sw = new StopWatch().start();
        AtomicInteger changed = new AtomicInteger();
        forEachChunk(graph.getNodes(), (from, to) -> {
            int count = 0;
            for (int node = from; node < to; node++) {
                if (delta[node] == 0) continue;
                nodeAccess.setNode(node, nodeAccess.getLat(node), nodeAccess.getLon(node), nodeAccess.getEle(node) + delta[node]);
                count++;
            }
            changed.addAndGet(count);
        });
        int shifted = updateEdges(delta);
        LOGGER.info("TowerNodeElevationSmoothing: changed {} of {} tower nodes within {} m, took {}s, shifted pillars of {} edges, took {}s, threads: {}",
                changed.get(), graph.getNodes(), radius, (int) smoothTime, shifted, (int) sw.stop().getSeconds(), threads);
    }

    private short[] calcHeadings() {
        short[] result = new short[graph.getEdgeKeys()];
        forEachChunk(graph.getEdges(), (from, to) -> {
            for (int edge = from; edge < to; edge++) {
                EdgeIteratorState state = graph.getEdgeIteratorState(edge, Integer.MIN_VALUE);
                RoadEnvironment env = roadEnvironment == null ? null : state.get(roadEnvironment);
                if (env == RoadEnvironment.BRIDGE || env == RoadEnvironment.TUNNEL || env == RoadEnvironment.FERRY) {
                    result[2 * edge] = result[2 * edge + 1] = INTERPOLATED;
                } else if (roadClass != null && state.get(roadClass) == RoadClass.STEPS) {
                    result[2 * edge] = result[2 * edge + 1] = SKIP;
                } else {
                    PointList pl = state.fetchWayGeometry(FetchMode.ALL);
                    result[2 * edge] = toShort(headingTowards(pl, false));
                    result[2 * edge + 1] = toShort(headingTowards(pl, true));
                }
            }
        });
        return result;
    }

    /**
     * @return the elevation change of every tower node, 0 if it is not smoothed
     */
    private float[] calcDeltas() {
        float[] delta = new float[graph.getNodes()];
        forEachChunk(graph.getNodes(), (from, to) -> {
            EdgeExplorer explorer = graph.createEdgeExplorer();
            double[] fwd = new double[(int) (radius / STEP)], bwd = new double[fwd.length];
            IntArrayList keys = new IntArrayList();
            for (int node = from; node < to; node++) {
                double own = nodeAccess.getEle(node);
                double newEle = smooth(explorer, node, own, keys, fwd, bwd);
                if (!Double.isNaN(newEle))
                    delta[node] = (float) (newEle - own);
            }
        });
        return delta;
    }

    /**
     * Updates the edges at changed tower nodes (which must be already set): shifts the pillar nodes and calculates
     * the distance again, as it includes the elevation. The edges are processed in batches: the new geometries are
     * calculated in parallel, the distances are set there too, and then the geometries are written on one thread,
     * because writing a longer geometry allocates storage. The copies of an edge for turn restrictions share its
     * geometry, so only the edge with the smallest id updates it.
     *
     * @return the number of edges with shifted pillar nodes
     */
    private int updateEdges(float[] delta) {
        int batchSize = 2 * chunkSize, taskSize = Math.max(1, chunkSize / 50);
        PointList[] pillars = new PointList[batchSize];
        int shifted = 0;
        for (int batchStart = 0; batchStart < graph.getEdges(); batchStart += batchSize) {
            int start = batchStart, size = Math.min(batchSize, graph.getEdges() - batchStart);
            Arrays.fill(pillars, null);
            forEachChunk(size, taskSize, (from, to) -> {
                EdgeExplorer explorer = graph.createEdgeExplorer();
                IntArrayList copies = new IntArrayList();
                for (int i = from; i < to; i++) {
                    EdgeIteratorState state = graph.getEdgeIteratorState(start + i, Integer.MIN_VALUE);
                    float deltaBase = delta[state.getBaseNode()], deltaAdj = delta[state.getAdjNode()];
                    if (deltaBase == 0 && deltaAdj == 0 || findCopies(explorer, state, copies) != state.getEdge())
                        continue;
                    PointList pl = state.fetchWayGeometry(FetchMode.ALL);
                    if (pl.size() > 2) {
                        shiftPillars(pl, deltaBase, deltaAdj);
                        pillars[i] = pl.shallowCopy(1, pl.size() - 1, false);
                    }
                    // like OSMReader, for the edge and its copies
                    double distance = Math.min(BaseGraph.MAX_DIST_METERS, Math.max(0.001, DistanceCalcEarth.DIST_EARTH.calcDistance(pl)));
                    for (IntCursor c : copies)
                        graph.getEdgeIteratorState(c.value, Integer.MIN_VALUE).setDistance(distance);
                }
            });
            for (int i = 0; i < size; i++) {
                if (pillars[i] == null) continue;
                graph.getEdgeIteratorState(start + i, Integer.MIN_VALUE).setWayGeometry(pillars[i]);
                shifted++;
            }
        }
        return shifted;
    }

    /**
     * Fills copies with the edge and its copies that share the geometry.
     *
     * @return the smallest edge id of them
     */
    private int findCopies(EdgeExplorer explorer, EdgeIteratorState edge, IntArrayList copies) {
        copies.clear();
        graph.forEdgeAndCopiesOfEdge(explorer, edge.getBaseNode(), edge.getEdge(), copies::add);
        int min = Integer.MAX_VALUE;
        for (IntCursor c : copies)
            min = Math.min(min, c.value);
        return min;
    }

    /**
     * The shift of a pillar node is the sum of the shifts of both tower nodes, each decreasing linearly to 0 at the
     * radius. The pillar elevations do not depend on the tower elevations.
     */
    private void shiftPillars(PointList pl, double deltaBase, double deltaAdj) {
        double total = 0;
        for (int i = 1; i < pl.size(); i++)
            total += dist(pl, i);
        double walked = 0;
        for (int i = 1; i < pl.size() - 1; i++) {
            walked += dist(pl, i);
            double shift = deltaBase * weight(walked) + deltaAdj * weight(total - walked);
            pl.set(i, pl.getLat(i), pl.getLon(i), pl.getEle(i) + shift);
        }
    }

    private double weight(double distanceToTower) {
        return Math.max(0, 1 - distanceToTower / radius);
    }

    /**
     * @return the smoothed elevation of the node or NaN if it keeps its elevation
     */
    private double smooth(EdgeExplorer explorer, int node, double own, IntArrayList keys, double[] fwd, double[] bwd) {
        keys.clear();
        EdgeIterator iter = explorer.setBaseNode(node);
        while (iter.next()) {
            short h = headings[iter.getEdgeKey()];
            if (h == INTERPOLATED) return Double.NaN;
            if (h != SKIP) keys.add(iter.getEdgeKey());
        }
        // try the pairs of edges from the smallest heading change on (for equal changes in the order of their index)
        // until one has a window on both sides
        int size = keys.size(), lastPair = -1;
        double lastTurn = -1;
        while (true) {
            int bestPair = -1;
            double bestTurn = MAX_TURN;
            for (int i = 0; i < size; i++) {
                for (int j = i + 1; j < size; j++) {
                    double turn = turn(heading(keys.get(i)), heading(keys.get(j)));
                    int pair = i * size + j;
                    if (turn < bestTurn && (turn > lastTurn || turn == lastTurn && pair > lastPair)) {
                        bestTurn = turn;
                        bestPair = pair;
                    }
                }
            }
            if (bestPair < 0) return Double.NaN;
            int n = Math.min(walk(explorer, keys.get(bestPair / size), fwd, 0, 0, STEP),
                    walk(explorer, keys.get(bestPair % size), bwd, 0, 0, STEP));
            if (n > 0) {
                double sum = own;
                for (int i = 0; i < n; i++)
                    sum += fwd[i] + bwd[i];
                return sum / (1 + 2 * n);
            }
            lastTurn = bestTurn;
            lastPair = bestPair;
        }
    }

    /**
     * Samples the elevation along the edge every STEP meters until the radius and continues at the adjacent node with
     * the straightest edge.
     *
     * @return the number of samples in the array
     */
    private int walk(EdgeExplorer explorer, int edgeKey, double[] samples, int count, double walked, double nextSample) {
        EdgeIteratorState edge = graph.getEdgeIteratorStateForKey(edgeKey);
        // cheap for an edge without pillar nodes: then only the tower nodes are read
        PointList pl = edge.fetchWayGeometry(FetchMode.ALL);
        for (int i = 1; i < pl.size(); i++) {
            double seg = dist(pl, i);
            while (nextSample <= walked + seg) {
                if (count == samples.length) return count;
                double f = seg == 0 ? 0 : (nextSample - walked) / seg;
                samples[count++] = pl.getEle(i - 1) + f * (pl.getEle(i) - pl.getEle(i - 1));
                nextSample += STEP;
            }
            walked += seg;
        }
        if (count == samples.length) return count;
        // the road continues beyond the adjacent node: the edge with the smallest heading change to the reverse edge
        double back = heading(GHUtility.reverseEdgeKey(edgeKey));
        int best = -1;
        double bestTurn = MAX_TURN;
        EdgeIterator iter = explorer.setBaseNode(edge.getAdjNode());
        while (iter.next()) {
            if (iter.getEdge() == edge.getEdge() || headings[iter.getEdgeKey()] <= INTERPOLATED) continue;
            double turn = turn(back, heading(iter.getEdgeKey()));
            if (turn < bestTurn) {
                bestTurn = turn;
                best = iter.getEdgeKey();
            }
        }
        return best < 0 ? count : walk(explorer, best, samples, count, walked, nextSample);
    }

    private void forEachChunk(int size, BiConsumer<Integer, Integer> range) {
        forEachChunk(size, chunkSize, range);
    }

    private void forEachChunk(int size, int chunk, BiConsumer<Integer, Integer> range) {
        GHUtility.runConcurrently(IntStream.range(0, (size + chunk - 1) / chunk).mapToObj(c ->
                () -> range.accept(c * chunk, Math.min(size, (c + 1) * chunk))), threads);
    }

    private double heading(int edgeKey) {
        short h = headings[edgeKey];
        return h <= INTERPOLATED ? Double.NaN : h / HEADING_SCALE;
    }

    private static short toShort(double heading) {
        if (Double.isNaN(heading)) return SKIP;
        short h = (short) Math.round(heading * HEADING_SCALE);
        // -pi and pi are the same direction, -pi would collide with INTERPOLATED
        return h == INTERPOLATED ? Short.MAX_VALUE : h;
    }

    private static double dist(PointList pl, int i) {
        return DistancePlaneProjection.DIST_PLANE.calcDist(pl.getLat(i - 1), pl.getLon(i - 1), pl.getLat(i), pl.getLon(i));
    }

    /**
     * @param in  orientation of one edge pointing away from the node
     * @param out orientation of another edge pointing away from the node
     * @return the heading change when driving from one edge into the other, 0 for a straight road
     */
    static double turn(double in, double out) {
        // normalized to [-pi, pi]
        double back = in > 0 ? in - Math.PI : in + Math.PI;
        return Math.abs(AngleCalc.ANGLE_CALC.alignOrientation(back, out) - back);
    }

    /**
     * @param fromEnd false for the first segment, true for the last segment pointing back into the point list
     * @return orientation of the first segment with a length, NaN if all points are at the same location
     */
    static double headingTowards(PointList pl, boolean fromEnd) {
        int n = pl.size(), i = fromEnd ? n - 1 : 0;
        for (int k = 1; k < n; k++) {
            int j = fromEnd ? n - 1 - k : k;
            if (pl.getLat(j) != pl.getLat(i) || pl.getLon(j) != pl.getLon(i))
                return AngleCalc.ANGLE_CALC.calcOrientation(pl.getLat(i), pl.getLon(i), pl.getLat(j), pl.getLon(j), false);
        }
        return Double.NaN;
    }
}
