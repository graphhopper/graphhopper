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
import com.graphhopper.routing.util.SlopeCalculator;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;

/**
 * The elevation of a tower node is a single sample of the elevation data. Its noise (and small real features next
 * to the road like a sunken courtyard) directly creates a slope on the adjacent edges, which is large for short
 * edges, e.g. 0.9 m on a 12 m edge are 7.5 %. The edge smoothing (moving average or ramer) only touches pillar
 * nodes, so this class smooths the tower nodes after the import:
 * <ul>
 * <li>The road through a node is the pair of edges with the smallest heading change (below {@link #MAX_TURN}), so
 * side roads, ramps and driveways on a different level do not influence the node. Edges where the road ends before
 * the first sample are ignored for this.</li>
 * <li>The wanted elevation is the mean of the own elevation and the elevation sampled every {@link #STEP} meters
 * along the road within the radius in both directions. At junctions the road continues with the straightest edge.
 * Calculating the mean and moving the nodes is repeated {@link #ROUNDS} times on the result.</li>
 * <li>The window is symmetric: if the road ends on one side the other side is cut to the same length, so a dead end
 * keeps its elevation.</li>
 * <li>The change is limited to {@link #MAX_CHANGE}: noise is smaller, while the mean would flatten real summits,
 * valley floors and kinks of steep paths.</li>
 * <li>The pillar nodes are shifted linearly between both tower nodes, i.e. an edge is only tilted. Then all slopes
 * of the edge change by the same value and the allowed tilt of every edge is known before:
 * max_slope and average_slope must not get steeper than {@link #MAX_STEEPER}. The tower nodes start without a
 * change and move towards the wanted elevation only as far as all their edges allow. Without this limit a mean
 * makes many edges steeper: it moves the slope of a steep edge into its flat neighbors, and short edges get steep
 * if their tower nodes are smoothed along different roads (e.g. at the crossing of a steep path).</li>
 * <li>The only exception is a side edge that is already steeper than {@link #STEEP_SIDE_EDGE}, e.g. the ramp down to
 * a courtyard: it does not hold back the node of a road of a higher class, see {@link #rank}.</li>
 * <li>Steps are a real change of the elevation, so the road ends there. Bridges, tunnels and ferries are
 * interpolated before: the road ends there too and their tower nodes keep their elevation.</li>
 * <li>A barrier is a zero-length edge between two tower nodes at the same location. It gets the headings of the
 * edges behind it, so that the road continues through it and both nodes get the same elevation.</li>
 * </ul>
 * All loops over the edges and tower nodes run on several threads.
 */
public class TowerNodeElevationSmoothing {
    private static final Logger LOGGER = LoggerFactory.getLogger(TowerNodeElevationSmoothing.class);
    static final double STEP = 10;
    // max heading change for an edge to count as the continuation of the road at a junction
    static final double MAX_TURN = Math.toRadians(45);
    // max elevation change of a tower node in meters
    static final double MAX_CHANGE = 0.75;
    // how much steeper max_slope and average_slope of an edge can get, as slope. This is the strength of the
    // smoothing: with 0.003 fewer edges get one step of the stored slope steeper, but a step in the elevation data
    // on a short edge cannot be spread over its neighbors anymore. Keep it below 0.01 (one step of max_slope).
    static final double MAX_STEEPER = 0.005;
    // a side edge with this slope or more does not limit the node of a road of a higher rank
    static final double STEEP_SIDE_EDGE = 0.2;
    // how often the mean is calculated and the tower nodes are moved towards it: 4 rounds remove 2.5 % of the ascend
    // and descend of bike routes in Germany, 3 rounds 2.4 %, 5 rounds 2.7 %
    static final int ROUNDS = 4;
    // pillar elevations are stored in steps of 5 cm. After a tilt they are rounded again, which changes max_slope
    // of edges with short segments, so the allowed tilt is reduced by this (in meters) divided by the segment length
    static final double PILLAR_ROUNDING = 0.03;
    // headings are stored in steps of 1.5 degrees as byte to save memory (the result is the same as with 1e-4 rad),
    // plus three markers below the range for special edges
    private static final double HEADING_SCALE = (Byte.MAX_VALUE - 3) / Math.PI;
    // steps: not part of the road
    private static final byte SKIP = Byte.MIN_VALUE;
    // bridge, tunnel or ferry: not part of the road and additionally their tower nodes keep their elevation
    private static final byte INTERPOLATED = Byte.MIN_VALUE + 1;
    // zero length (barrier), only while calculating the headings
    private static final byte ZERO = Byte.MIN_VALUE + 2;

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
    private byte[] headings;
    // per edge key: how far the adjacent node can rise relative to the base node. Like all changes in millimeters
    // as short to save memory.
    private short[] limits;
    // the current change per tower node
    private short[] delta;

    public TowerNodeElevationSmoothing(BaseGraph graph, EnumEncodedValue<RoadClass> roadClass,
                                       EnumEncodedValue<RoadEnvironment> roadEnvironment, double radius, int threads) {
        if (radius < STEP)
            throw new IllegalArgumentException("The radius for the tower node elevation smoothing must be at least " + STEP + "m but was " + radius);
        this.graph = graph;
        this.nodeAccess = graph.getNodeAccess();
        this.roadClass = roadClass;
        this.roadEnvironment = roadEnvironment;
        this.radius = radius;
        this.threads = threads;
    }

    public void execute() {
        StopWatch sw = new StopWatch().start();
        calcHeadingsAndLimits();
        delta = new short[graph.getNodes()];
        short[] target = new short[graph.getNodes()], prev = new short[graph.getNodes()];
        for (int round = 0; round < ROUNDS; round++) {
            calcTargets(target);
            // staged: the nodes are moved in parallel, so they read the changes of the previous round
            System.arraycopy(delta, 0, prev, 0, delta.length);
            moveTowardsTargets(target, prev);
        }
        headings = null;
        limits = null;
        float smoothTime = sw.stop().getSeconds();

        sw = new StopWatch().start();
        int changed = 0;
        for (int node = 0; node < graph.getNodes(); node++) {
            if (delta[node] == 0) continue;
            nodeAccess.setNode(node, nodeAccess.getLat(node), nodeAccess.getLon(node), nodeAccess.getEle(node) + delta[node] / 1000.0);
            changed++;
        }
        int shifted = updateEdges();
        delta = null;
        LOGGER.info("TowerNodeElevationSmoothing: changed {} of {} tower nodes within {} m, took {}s, shifted pillars of {} edges, took {}s, threads: {}",
                changed, graph.getNodes(), radius, (int) smoothTime, shifted, (int) sw.stop().getSeconds(), threads);
    }

    /**
     * Both in one loop over all edges, as decoding the geometry is expensive.
     */
    private void calcHeadingsAndLimits() {
        headings = new byte[graph.getEdgeKeys()];
        limits = new short[graph.getEdgeKeys()];
        forEachChunk(graph.getEdges(), chunkSize, (from, to) -> {
            for (int edge = from; edge < to; edge++) {
                EdgeIteratorState state = graph.getEdgeIteratorState(edge, Integer.MIN_VALUE);
                PointList pl = state.fetchWayGeometry(FetchMode.ALL);
                RoadEnvironment env = state.get(roadEnvironment);
                if (env == RoadEnvironment.BRIDGE || env == RoadEnvironment.TUNNEL || env == RoadEnvironment.FERRY) {
                    headings[2 * edge] = headings[2 * edge + 1] = INTERPOLATED;
                } else if (state.get(roadClass) == RoadClass.STEPS) {
                    headings[2 * edge] = headings[2 * edge + 1] = SKIP;
                } else {
                    headings[2 * edge] = toByte(headingTowards(pl, false));
                    headings[2 * edge + 1] = toByte(headingTowards(pl, true));
                }
                calcLimits(edge, pl);
            }
        });
        // a barrier is a zero-length edge between two tower nodes at the same location: it gets the heading of the
        // edge behind it, so that the road continues through it. On one thread, as it reads the headings of others
        EdgeExplorer explorer = graph.createEdgeExplorer();
        for (int edge = 0; edge < graph.getEdges(); edge++) {
            if (headings[2 * edge] != ZERO) continue;
            EdgeIteratorState state = graph.getEdgeIteratorState(edge, Integer.MIN_VALUE);
            headings[2 * edge] = headingBehind(explorer, state.getAdjNode(), edge);
            headings[2 * edge + 1] = headingBehind(explorer, state.getBaseNode(), edge);
        }
    }

    /**
     * @return the heading of the only other edge at the node, SKIP if there is none, more than one or a zero-length one
     */
    private byte headingBehind(EdgeExplorer explorer, int node, int edge) {
        byte result = SKIP;
        int count = 0;
        EdgeIterator iter = explorer.setBaseNode(node);
        while (iter.next()) {
            if (iter.getEdge() == edge) continue;
            if (isBarrier(iter)) return SKIP;
            result = headings[iter.getEdgeKey()];
            count++;
        }
        return count == 1 ? result : SKIP;
    }

    /**
     * SlopeCalculator uses the slope between the tower nodes (average_slope) and additionally the slope of every
     * segment against the mean of the two points before (max_slope). A tilt of the edge changes all of them by the
     * tilt, so this calculates how far the edge can be tilted into both directions until one of them is steeper than
     * before by more than MAX_STEEPER.
     */
    private void calcLimits(int edge, PointList pl) {
        double length = DistanceCalcEarth.calcDistance(pl, false);
        if (length <= 0) return;
        double avg = (pl.getEle(pl.size() - 1) - pl.getEle(0)) / length, min = avg, max = avg;
        double prevDist = 0, shortest = Double.MAX_VALUE;
        for (int i = 1; i < pl.size(); i++) {
            double dist = DistanceCalcEarth.DIST_EARTH.calcDist(pl.getLat(i - 1), pl.getLon(i - 1), pl.getLat(i), pl.getLon(i));
            if (i > 1 && prevDist > SlopeCalculator.MIN_LENGTH) {
                double slope = (pl.getEle(i) - (pl.getEle(i - 1) + pl.getEle(i - 2)) / 2) / (dist + prevDist / 2);
                min = Math.min(min, slope);
                max = Math.max(max, slope);
                shortest = Math.min(shortest, dist + prevDist / 2);
            }
            prevDist = dist;
        }
        double steepest = Math.max(Math.abs(min), Math.abs(max));
        double steeperMax = Math.max(0, MAX_STEEPER - (shortest == Double.MAX_VALUE ? 0 : PILLAR_ROUNDING / shortest));
        double up = Math.min(steepest + steeperMax - max, Math.abs(avg) + MAX_STEEPER - avg);
        double down = Math.min(steepest + steeperMax + min, Math.abs(avg) + MAX_STEEPER + avg);
        // truncated and not rounded, so that the edge stays within its range
        limits[2 * edge] = (short) Math.min(Short.MAX_VALUE, 1000 * up * length);
        limits[2 * edge + 1] = (short) Math.min(Short.MAX_VALUE, 1000 * down * length);
    }

    /**
     * Sets the wanted change of every tower node. For a node that is not smoothed this is its current change.
     */
    private void calcTargets(short[] target) {
        forEachChunk(graph.getNodes(), chunkSize, (from, to) -> {
            EdgeExplorer explorer = graph.createEdgeExplorer();
            double[] fwdEle = new double[(int) (radius / STEP)], bwdEle = new double[fwdEle.length];
            IntArrayList keys = new IntArrayList();
            for (int node = from; node < to; node++) {
                double original = nodeAccess.getEle(node), own = original + delta[node] / 1000.0, newEle;
                int twin = findTwin(explorer, node);
                if (twin == -1) {
                    newEle = smooth(explorer, node, own, keys, fwdEle, bwdEle);
                } else if (twin == -2 || findTwin(explorer, twin) != node) {
                    // rare chain of barriers
                    newEle = Double.NaN;
                } else {
                    // both nodes of a barrier are at the same location and must get the same target: the one of the
                    // node with the smaller id, and both keep their elevation if one of them does
                    newEle = smooth(explorer, Math.min(node, twin), own, keys, fwdEle, bwdEle);
                    if (Double.isNaN(smooth(explorer, Math.max(node, twin), own, keys, fwdEle, bwdEle)))
                        newEle = Double.NaN;
                }
                target[node] = Double.isNaN(newEle) ? delta[node] : (short) Math.round(1000 * Math.max(-MAX_CHANGE, Math.min(MAX_CHANGE, newEle - original)));
            }
        });
    }

    /**
     * @return the node behind the barrier at this node, -1 if there is none, -2 for more than one barrier
     */
    private int findTwin(EdgeExplorer explorer, int node) {
        int twin = -1;
        EdgeIterator iter = explorer.setBaseNode(node);
        while (iter.next()) {
            if (!isBarrier(iter)) continue;
            if (twin != -1 || iter.getAdjNode() == node) return -2;
            twin = iter.getAdjNode();
        }
        return twin;
    }

    /**
     * Moves every tower node towards its target as far as the limits of its edges allow. All nodes start without a
     * change, which is within the limits, and a node only uses the room that the change of the other node in the
     * previous round left (or half of it, see limitRoom), so every round stays within the limits and the nodes can
     * be moved in parallel.
     */
    private void moveTowardsTargets(short[] target, short[] prev) {
        forEachChunk(graph.getNodes(), chunkSize, (from, to) -> {
            EdgeExplorer explorer = graph.createEdgeExplorer();
            // how far the node can move down and up
            int[] room = new int[2];
            for (int node = from; node < to; node++) {
                if (target[node] == prev[node]) continue;
                room[0] = room[1] = Integer.MAX_VALUE;
                // both nodes of a barrier use the edges of both, so they stay equal
                int twin = limitRoom(explorer, node, target, prev, room);
                if (twin >= 0) limitRoom(explorer, twin, target, prev, room);
                delta[node] = (short) (prev[node] + Math.max(-room[0], Math.min(room[1], target[node] - prev[node])));
            }
        });
    }

    /**
     * Limits the room of a node for every edge of 'node' (the node itself or the other node of its barrier).
     * If the other node of an edge wants to move into the opposite direction both can use only half of the room.
     *
     * @return the node behind a barrier or -1
     */
    private int limitRoom(EdgeExplorer explorer, int node, short[] target, short[] prev, int[] room) {
        int twin = -1, top = 0;
        EdgeIterator iter = explorer.setBaseNode(node);
        while (iter.next())
            if (!isBarrier(iter)) top = Math.max(top, rank(iter));
        double ele = nodeAccess.getEle(node);
        iter = explorer.setBaseNode(node);
        while (iter.next()) {
            int other = iter.getAdjNode();
            if (other == node) continue;
            if (isBarrier(iter)) {
                twin = other;
                continue;
            }
            // a steep side edge: the node follows its road
            if (rank(iter) < top && Math.abs(nodeAccess.getEle(other) - ele) >= STEEP_SIDE_EDGE * iter.getDistance())
                continue;
            // down: the other node can rise relative to this node by limits[key]. Up: the reverse edge key
            int diff = prev[node] - prev[other], otherWant = target[other] - prev[other];
            room[0] = Math.min(room[0], Math.max(0, limits[iter.getEdgeKey()] + diff) / (otherWant > 0 ? 2 : 1));
            room[1] = Math.min(room[1], Math.max(0, limits[GHUtility.reverseEdgeKey(iter.getEdgeKey())] - diff) / (otherWant < 0 ? 2 : 1));
        }
        return twin;
    }

    /**
     * The elevation at a junction belongs to its most important road: roads for cars, then tracks, paths and service
     * roads (driveways, parking aisles), then steps.
     */
    private int rank(EdgeIteratorState edge) {
        return switch (edge.get(roadClass)) {
            case MOTORWAY, TRUNK, PRIMARY, SECONDARY, TERTIARY, UNCLASSIFIED, RESIDENTIAL, LIVING_STREET, ROAD -> 2;
            case STEPS -> 0;
            default -> 1;
        };
    }

    /**
     * Updates the edges at changed tower nodes (which must be already set): tilts the pillar nodes and calculates
     * the distance again, as it includes the elevation. The edges are processed in batches: the new geometries are
     * calculated in parallel, the distances are set there too, and then the geometries are written on one thread,
     * because writing a longer geometry (which is very rare) allocates storage and storage is not write thread safe.
     * The copies of an edge for turn restrictions share its geometry, so only the edge with the smallest id updates it.
     *
     * @return the number of edges with shifted pillar nodes
     */
    private int updateEdges() {
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
                    double deltaBase = delta[state.getBaseNode()] / 1000.0, deltaAdj = delta[state.getAdjNode()] / 1000.0;
                    // skip edge copies but the original should loop over them at the end to update the distance
                    if (deltaBase == 0 && deltaAdj == 0 || findCopies(explorer, state, copies) != state.getEdge())
                        continue;
                    PointList pl = state.fetchWayGeometry(FetchMode.ALL);
                    if (pl.size() > 2) {
                        tilt(pl, deltaBase, deltaAdj);
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
     * Shifts the pillar nodes by the linear interpolation of the changes of both tower nodes along the 2D length
     * (like SlopeCalculator measures it).
     */
    private static void tilt(PointList pl, double deltaBase, double deltaAdj) {
        double length = DistanceCalcEarth.calcDistance(pl, false), walked = 0;
        for (int i = 1; i < pl.size() - 1; i++) {
            walked += DistanceCalcEarth.DIST_EARTH.calcDist(pl.getLat(i - 1), pl.getLon(i - 1), pl.getLat(i), pl.getLon(i));
            double shift = length <= 0 ? deltaBase : deltaBase + (deltaAdj - deltaBase) * walked / length;
            pl.set(i, pl.getLat(i), pl.getLon(i), pl.getEle(i) + shift);
        }
    }

    private static boolean isBarrier(EdgeIteratorState edge) {
        // OSMReader stores at least 1 mm
        return edge.getDistance() <= 0.001;
    }

    /**
     * @param own the current elevation of the node
     * @return the smoothed elevation of the node or NaN if it keeps its elevation
     */
    private double smooth(EdgeExplorer explorer, int node, double own, IntArrayList keys, double[] fwdEle, double[] bwdEle) {
        keys.clear();
        EdgeIterator iter = explorer.setBaseNode(node);
        while (iter.next()) {
            byte h = headings[iter.getEdgeKey()];
            if (h == INTERPOLATED) return Double.NaN;
            if (h != SKIP) keys.add(iter.getEdgeKey());
        }
        while (true) {
            // the pair of edges with the smallest heading change, for equal changes the first one
            int fwd = -1, bwd = -1;
            double bestTurn = MAX_TURN;
            for (int i = 0; i < keys.size(); i++) {
                for (int j = i + 1; j < keys.size(); j++) {
                    double turn = turn(heading(keys.get(i)), heading(keys.get(j)));
                    if (turn < bestTurn) {
                        bestTurn = turn;
                        fwd = i;
                        bwd = j;
                    }
                }
            }
            if (fwd < 0) return Double.NaN;
            int nFwd = walk(explorer, keys.get(fwd), fwdEle), nBwd = walk(explorer, keys.get(bwd), bwdEle);
            if (nFwd > 0 && nBwd > 0) {
                // the same number of samples from both sides
                int n = Math.min(nFwd, nBwd);
                double sum = own;
                for (int i = 0; i < n; i++)
                    sum += fwdEle[i] + bwdEle[i];
                return sum / (1 + 2 * n);
            }
            // the road ends before the first sample: try the next pair without this edge (bwd first, it is behind fwd)
            if (nBwd == 0) keys.remove(bwd);
            if (nFwd == 0) keys.remove(fwd);
        }
    }

    /**
     * Samples the current elevation (i.e. tilted by the changes of the tower nodes) along the edge every STEP meters
     * and continues at the adjacent node with the straightest edge, until the road ends or the array is full.
     *
     * @return the number of samples in the array
     */
    private int walk(EdgeExplorer explorer, int edgeKey, double[] samples) {
        int count = 0;
        double walked = 0, nextSample = STEP;
        while (edgeKey >= 0) {
            EdgeIteratorState edge = graph.getEdgeIteratorStateForKey(edgeKey);
            // cheap for an edge without pillar nodes: then only the tower nodes are read
            PointList pl = edge.fetchWayGeometry(FetchMode.ALL);
            double deltaBase = delta[edge.getBaseNode()] / 1000.0, deltaAdj = delta[edge.getAdjNode()] / 1000.0, length = 0;
            if (deltaBase != deltaAdj)
                for (int i = 1; i < pl.size(); i++)
                    length += dist(pl, i);
            double start = walked, prevEle = pl.getEle(0) + deltaBase;
            for (int i = 1; i < pl.size(); i++) {
                double seg = dist(pl, i);
                double ele = pl.getEle(i) + deltaBase + (length <= 0 ? 0 : (deltaAdj - deltaBase) * (walked + seg - start) / length);
                while (nextSample <= walked + seg) {
                    if (count == samples.length) return count;
                    double f = seg == 0 ? 0 : (nextSample - walked) / seg;
                    samples[count++] = prevEle + f * (ele - prevEle);
                    nextSample += STEP;
                }
                walked += seg;
                prevEle = ele;
            }
            if (count == samples.length) return count;
            // the road continues beyond the adjacent node: the edge with the smallest heading change to the reverse edge
            double back = heading(GHUtility.reverseEdgeKey(edgeKey));
            double bestTurn = MAX_TURN;
            int prevEdge = edge.getEdge();
            edgeKey = -1;
            EdgeIterator iter = explorer.setBaseNode(edge.getAdjNode());
            while (iter.next()) {
                if (iter.getEdge() == prevEdge || headings[iter.getEdgeKey()] <= INTERPOLATED) continue;
                double turn = turn(back, heading(iter.getEdgeKey()));
                if (turn < bestTurn) {
                    bestTurn = turn;
                    edgeKey = iter.getEdgeKey();
                }
            }
        }
        return count;
    }

    private void forEachChunk(int size, int chunk, BiConsumer<Integer, Integer> range) {
        GHUtility.runConcurrently(IntStream.range(0, (size + chunk - 1) / chunk).mapToObj(c ->
                () -> range.accept(c * chunk, Math.min(size, (c + 1) * chunk))), threads);
    }

    private double heading(int edgeKey) {
        byte h = headings[edgeKey];
        return h <= INTERPOLATED ? Double.NaN : h / HEADING_SCALE;
    }

    private static byte toByte(double heading) {
        return Double.isNaN(heading) ? ZERO : (byte) Math.round(heading * HEADING_SCALE);
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
        // zero-length segments: barrier edges (all points identical) or duplicate OSM points (without simplification)
        for (int k = 1; k < n; k++) {
            int j = fromEnd ? n - 1 - k : k;
            if (pl.getLat(j) != pl.getLat(i) || pl.getLon(j) != pl.getLon(i))
                return AngleCalc.ANGLE_CALC.calcOrientation(pl.getLat(i), pl.getLon(i), pl.getLat(j), pl.getLon(j), false);
        }
        return Double.NaN;
    }
}
