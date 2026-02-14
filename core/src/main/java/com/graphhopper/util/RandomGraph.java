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

package com.graphhopper.util;

import java.util.*;

import com.carrotsearch.hppc.*;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;

/**
 * Creates random graphs for testing purposes. Nodes are aligned on a grid (+jitter), and connected
 * to their KNN
 */
public class RandomGraph {

    private RandomGraph() {
    }

    public static Builder start() {
        return new Builder();
    }

    public static class Builder {

        private long seed = 42;
        private int nodes = 10;
        private boolean tree = false;
        private Double speed = null;
        private double duplicateEdges = 0.05;
        private double curviness = 0.0;
        private double speedMean = 16;
        private double speedStdDev = 8;
        private double pSpeedZero = 0;

        private final double centerLat = 50.0;
        private final double centerLon = 10.0;
        private final double step = 0.001;
        private final double rowFactor = 0.9;
        private final double jitter = 0.8;
        private final int kMin = 2;
        private final int kMax = 3;

        private record TmpGraph(double[] lats, double[] lons, LongArrayList edges) {
        }

        public void fill(BaseGraph graph, DecimalEncodedValue speedEnc) {
            if (graph.getNodes() > 0 || graph.getEdges() > 0)
                throw new IllegalStateException("BaseGraph should be empty");
            if (!tree)
                buildGraph(graph, speedEnc, seed);
            else
                buildTree(graph, speedEnc);
        }

        private void buildGraph(BaseGraph graph, DecimalEncodedValue speedEnc, long useSeed) {
            var rnd = new Random(useSeed);
            TmpGraph g = generateTmpGraph(nodes, rnd);
            fillBaseGraph(graph, speedEnc, rnd, g);
        }

        private TmpGraph generateTmpGraph(int nodes, Random rnd) {
            double[] lats = new double[nodes];
            double[] lons = new double[nodes];
            generateNodePositions(rnd, nodes, lats, lons);
            var edges = generateKnnEdges(rnd, nodes, lats, lons);
            int duplicates = (int) Math.ceil(edges.size() * duplicateEdges);
            for (int i = 0; i < duplicates; i++)
                edges.add(edges.get(rnd.nextInt(edges.size())));
            return new TmpGraph(lats, lons, edges);
        }

        private void generateNodePositions(Random rnd, int n, double[] lats, double[] lons) {
            int cols = Math.max(1, (int) Math.round(Math.sqrt(n / rowFactor)));
            int rows = (int) Math.ceil((double) n / cols);
            double offsetLat = ((rows - 1) * step) / 2.0;
            double offsetLon = ((cols - 1) * step) / 2.0;
            for (int i = 0; i < n; i++) {
                int r = i / cols, c = i % cols;
                lats[i] = centerLat - offsetLat + r * step + (rnd.nextDouble() - 0.5) * jitter * step;
                lons[i] = centerLon - offsetLon + c * step + (rnd.nextDouble() - 0.5) * jitter * step;
            }
        }

        private LongArrayList generateKnnEdges(Random rnd, int n, double[] lats, double[] lons) {
            record Pair(int j, double d) {
            }
            var edges = new LongHashSet();
            for (int i = 0; i < n; i++) {
                int ki = kMin + rnd.nextInt(kMax - kMin + 1);
                var list = new ArrayList<Pair>();
                for (int j = 0; j < n; j++) {
                    if (j == i) continue;
                    double dLat = lats[i] - lats[j], dLon = lons[i] - lons[j];
                    list.add(new Pair(j, dLat * dLat + dLon * dLon));
                }
                list.sort(Comparator.comparingDouble(Pair::d));
                int limit = Math.min(ki, list.size());
                for (int k = 0; k < limit; k++) {
                    int j = list.get(k).j;
                    int a = Math.min(i, j), b = Math.max(i, j);
                    edges.add(BitUtil.LITTLE.toLong(a, b));
                }
            }
            return new LongArrayList(edges);
        }

        private void fillBaseGraph(BaseGraph graph, DecimalEncodedValue speedEnc, Random rnd, TmpGraph g) {
            NodeAccess na = graph.getNodeAccess();
            for (int i = 0; i < g.lats.length; i++) {
                na.setNode(i, g.lats[i], g.lons[i]);
            }
            for (var e : g.edges) {
                int from = BitUtil.LITTLE.getIntHigh(e.value);
                int to = BitUtil.LITTLE.getIntLow(e.value);
                EdgeIteratorState edge = graph.edge(from, to);

                double beeline = GHUtility.getDistance(from, to, na);
                double distance = Math.max(beeline, beeline * (1 + curviness * rnd.nextDouble()));
                if (distance < 0.001) distance = 0.001;
                edge.setDistance(distance);

                double fwdSpeed = Math.max(1, Math.min(50, speedMean + speedStdDev * rnd.nextGaussian()));
                double bwdSpeed = Math.max(1, Math.min(50, speedMean + speedStdDev * rnd.nextGaussian()));
                // if an explicit speed is given we discard the random speeds and use the given one instead
                if (speed != null)
                    fwdSpeed = bwdSpeed = speed;
                // zero speeds are possible even if an explicit speed is given
                if (rnd.nextDouble() < pSpeedZero)
                    fwdSpeed = 0;
                if (rnd.nextDouble() < pSpeedZero)
                    bwdSpeed = 0;
                if (speedEnc != null) {
                    edge.set(speedEnc, fwdSpeed);
                    if (speedEnc.isStoreTwoDirections())
                        edge.setReverse(speedEnc, bwdSpeed);
                }
            }
        }

        private void buildTree(BaseGraph graph, DecimalEncodedValue speedEnc) {
            for (int attempt = 0; attempt < 1000; attempt++) {
                long trySeed = seed + attempt;
                Random rnd = new Random(trySeed);
                TmpGraph g = generateTmpGraph(nodes, rnd);
                LongArrayList treeEdges = findBFSTreeEdgesFromCenter(g);
                IntSet nodesInTree = new IntHashSet();
                for (var e : treeEdges) {
                    nodesInTree.add(BitUtil.LITTLE.getIntHigh(e.value));
                    nodesInTree.add(BitUtil.LITTLE.getIntLow(e.value));
                }
                // we wait until we find a graph that is fully connected to make sure our tree has the
                // desired number of nodes
                if (nodesInTree.size() == nodes) {
                    var tree = new TmpGraph(g.lats, g.lons, treeEdges);
                    fillBaseGraph(graph, speedEnc, rnd, tree);
                    return;
                }
            }
            throw new IllegalStateException("Could not generate a spanning tree after 1000 attempts");
        }

        private LongArrayList findBFSTreeEdgesFromCenter(TmpGraph g) {
            var adjNodes = new HashMap<Integer, List<Integer>>();
            for (var e : g.edges) {
                int a = BitUtil.LITTLE.getIntHigh(e.value), b = BitUtil.LITTLE.getIntLow(e.value);
                adjNodes.computeIfAbsent(a, x -> new ArrayList<>()).add(b);
                adjNodes.computeIfAbsent(b, x -> new ArrayList<>()).add(a);
            }
            int center = 0;
            double best = Double.MAX_VALUE;
            for (int i = 0; i < g.lats.length; i++) {
                double d = (g.lats[i] - centerLat) * (g.lats[i] - centerLat) + (g.lons[i] - centerLon) * (g.lons[i] - centerLon);
                if (d < best) {
                    best = d;
                    center = i;
                }
            }
            var visited = new boolean[g.lats.length];
            visited[center] = true;
            var queue = new ArrayDeque<Integer>();
            queue.add(center);
            LongSet treeEdges = new LongHashSet();
            while (!queue.isEmpty()) {
                int cur = queue.poll();
                for (int nb : adjNodes.getOrDefault(cur, List.of())) {
                    if (!visited[nb]) {
                        visited[nb] = true;
                        treeEdges.add(BitUtil.LITTLE.toLong(Math.min(cur, nb), Math.max(cur, nb)));
                        queue.add(nb);
                    }
                }
            }
            return new LongArrayList(treeEdges);
        }

        public Builder seed(long v) {
            seed = v;
            return this;
        }

        public Builder nodes(int v) {
            nodes = v;
            return this;
        }

        public Builder tree(boolean v) {
            tree = v;
            return this;
        }

        public Builder speed(Double v) {
            speed = v;
            return this;
        }

        public Builder duplicateEdges(double v) {
            duplicateEdges = v;
            return this;
        }

        public Builder curviness(double v) {
            curviness = v;
            return this;
        }

        public Builder speedMean(double v) {
            speedMean = v;
            return this;
        }

        public Builder speedStdDev(double v) {
            speedStdDev = v;
            return this;
        }

        public Builder speedZero(double v) {
            pSpeedZero = v;
            return this;
        }

    }

}
