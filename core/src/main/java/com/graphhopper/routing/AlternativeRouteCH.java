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

import com.carrotsearch.hppc.IntIndexedContainer;
import com.carrotsearch.hppc.predicates.IntObjectPredicate;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.RoutingCHGraph;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.core.util.PMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Minimum number-of-moving-parts implementation of alternative route search with
 * contraction hierarchies.
 * <p>
 * "Alternative Routes in Road Networks" (Abraham et al.)
 *
 * @author michaz
 */
public class AlternativeRouteCH extends DijkstraBidirectionCHNoSOD {
    private final double maxWeightFactor;
    private final double maxShareFactor;
    private final double localOptimalityFactor;
    private final int maxPaths;
    private final List<AlternativeInfo> alternatives = new ArrayList<>();
    private int extraVisitedNodes = 0;

    public AlternativeRouteCH(RoutingCHGraph graph, PMap hints) {
        super(graph);
        maxWeightFactor = hints.getDouble("alternative_route.max_weight_factor", 1.25);
        maxShareFactor = hints.getDouble("alternative_route.max_share_factor", 0.8);
        localOptimalityFactor = hints.getDouble("alternative_route.local_optimality_factor", 0.25);
        maxPaths = hints.getInt("alternative_route.max_paths", 3);
    }

    @Override
    public boolean finished() {
        if (finishedFrom && finishedTo)
            return true;

        // Continue search longer than for point to point search -- not sure if makes a difference at all
        return currFrom.weight >= bestWeight * maxWeightFactor && currTo.weight >= bestWeight * maxWeightFactor;
    }

    @Override
    public int getVisitedNodes() {
        return visitedCountFrom + visitedCountTo + extraVisitedNodes;
    }

    List<AlternativeInfo> calcAlternatives(final int s, final int t) {
        // First, do a regular bidirectional route search
        checkAlreadyRun();
        init(s, 0, t, 0);
        runAlgo();
        final Path bestPath = extractPath();
        if (!bestPath.isFound()) {
            return Collections.emptyList();
        }

        alternatives.add(new AlternativeInfo(bestPath, 0));

        final ArrayList<PotentialAlternativeInfo> potentialAlternativeInfos = new ArrayList<>();

        bestWeightMapFrom.forEach((IntObjectPredicate<SPTEntry>) (v, fromSPTEntry) -> {
            SPTEntry toSPTEntry = bestWeightMapTo.get(v);
            if (toSPTEntry == null)
                return true;

            if (fromSPTEntry.getWeightOfVisitedPath() + toSPTEntry.getWeightOfVisitedPath() > bestPath.getWeight() * maxWeightFactor)
                return true;

            // This gives us a path s -> v -> t, but since we are using contraction hierarchies,
            // s -> v and v -> t need not be shortest paths. In fact, they can sometimes be pretty strange.
            // We still use this preliminary path to filter for shared path length with other alternatives,
            // so we don't have to work so much.
            Path preliminaryRoute = createPathExtractor().extract(fromSPTEntry, toSPTEntry, fromSPTEntry.getWeightOfVisitedPath() + toSPTEntry.getWeightOfVisitedPath());
            double preliminaryShare = calculateShare(preliminaryRoute);
            if (preliminaryShare > maxShareFactor) {
                return true;
            }
            PotentialAlternativeInfo potentialAlternativeInfo = new PotentialAlternativeInfo();
            potentialAlternativeInfo.v = v;
            potentialAlternativeInfo.weight = 2 * (fromSPTEntry.getWeightOfVisitedPath() + toSPTEntry.getWeightOfVisitedPath()) + preliminaryShare;
            potentialAlternativeInfos.add(potentialAlternativeInfo);
            return true;
        });

        potentialAlternativeInfos.sort(Comparator.comparingDouble(o -> o.weight));

        for (PotentialAlternativeInfo potentialAlternativeInfo : potentialAlternativeInfos) {
            int v = potentialAlternativeInfo.v;

            // Okay, now we want the s -> v -> t shortest via-path, so we route s -> v and v -> t
            // and glue them together.
            DijkstraBidirectionCH svRouter = new DijkstraBidirectionCH(graph);
            final Path svPath = svRouter.calcPath(s, v);
            extraVisitedNodes += svRouter.getVisitedNodes();

            DijkstraBidirectionCH vtRouter = new DijkstraBidirectionCH(graph);
            final Path vtPath = vtRouter.calcPath(v, t);
            Path path = concat(graph.getBaseGraph(), svPath, vtPath);
            extraVisitedNodes += vtRouter.getVisitedNodes();

            double sharedDistanceWithShortest = sharedDistanceWithShortest(path);
            double detourLength = path.getDistance() - sharedDistanceWithShortest;
            double directLength = bestPath.getDistance() - sharedDistanceWithShortest;
            if (detourLength > directLength * maxWeightFactor) {
                continue;
            }

            double share = calculateShare(path);
            if (share > maxShareFactor) {
                continue;
            }

            // This is the final test we need: Discard paths that are not "locally shortest" around v.
            // So move a couple of nodes to the left and right from v on our path,
            // route, and check if v is on the shortest path.
            final IntIndexedContainer svNodes = svPath.calcNodes();
            int vIndex = svNodes.size() - 1;
            if (!tTest(path, vIndex))
                continue;

            alternatives.add(new AlternativeInfo(path, share));
            if (alternatives.size() >= maxPaths)
                break;
        }
        return alternatives;
    }

    private double calculateShare(final Path path) {
        double sharedDistance = sharedDistance(path);
        return sharedDistance / path.getDistance();
    }

    private double sharedDistance(Path path) {
        double sharedDistance = 0.0;
        List<EdgeIteratorState> edges = path.calcEdges();
        for (EdgeIteratorState edge : edges) {
            if (nodesInCurrentAlternativeSetContains(edge.getBaseNode()) && nodesInCurrentAlternativeSetContains(edge.getAdjNode())) {
                sharedDistance += edge.getDistance();
            }
        }
        return sharedDistance;
    }

    private double sharedDistanceWithShortest(Path path) {
        double sharedDistance = 0.0;
        List<EdgeIteratorState> edges = path.calcEdges();
        for (EdgeIteratorState edge : edges) {
            if (alternatives.get(0).nodes.contains(edge.getBaseNode()) && alternatives.get(0).nodes.contains(edge.getAdjNode())) {
                sharedDistance += edge.getDistance();
            }
        }
        return sharedDistance;
    }

    private boolean nodesInCurrentAlternativeSetContains(int v) {
        for (AlternativeInfo alternative : alternatives) {
            if (alternative.nodes.contains(v)) {
                return true;
            }
        }
        return false;
    }

    private boolean tTest(Path path, int vIndex) {
        if (path.getEdgeCount() == 0) return true;
        double detourDistance = detourDistance(path);
        double T = 0.5 * localOptimalityFactor * detourDistance;
        int fromNode = getPreviousNodeTMetersAway(path, vIndex, T);
        int toNode = getNextNodeTMetersAway(path, vIndex, T);
        DijkstraBidirectionCH tRouter = new DijkstraBidirectionCH(graph);
        Path tPath = tRouter.calcPath(fromNode, toNode);
        extraVisitedNodes += tRouter.getVisitedNodes();
        IntIndexedContainer tNodes = tPath.calcNodes();
        int v = path.calcNodes().get(vIndex);
        return tNodes.contains(v);
    }

    private double detourDistance(Path path) {
        return path.getDistance() - sharedDistanceWithShortest(path);
    }

    private int getPreviousNodeTMetersAway(Path path, int vIndex, double T) {
        List<EdgeIteratorState> edges = path.calcEdges();
        double distance = 0.0;
        int i = vIndex;
        while (i > 0 && distance < T) {
            distance += edges.get(i - 1).getDistance();
            i--;
        }
        return edges.get(i).getBaseNode();
    }

    private int getNextNodeTMetersAway(Path path, int vIndex, double T) {
        List<EdgeIteratorState> edges = path.calcEdges();
        double distance = 0.0;
        int i = vIndex;
        while (i < edges.size() - 1 && distance < T) {
            distance += edges.get(i).getDistance();
            i++;
        }
        return edges.get(i - 1).getAdjNode();
    }

    private static Path concat(Graph graph, Path svPath, Path vtPath) {
        Path path = new Path(graph);
        path.getEdges().addAll(svPath.getEdges());
        path.getEdges().addAll(vtPath.getEdges());
        path.setFromNode(svPath.calcNodes().get(0));
        path.setEndNode(vtPath.getEndNode());
        path.setWeight(svPath.getWeight() + vtPath.getWeight());
        path.setDistance(svPath.getDistance() + vtPath.getDistance());
        path.addTime(svPath.getTime() + vtPath.getTime());
        path.setFound(true);
        return path;
    }

    @Override
    public List<Path> calcPaths(int from, int to) {
        List<AlternativeInfo> alts = calcAlternatives(from, to);
        if (alts.isEmpty()) {
            return Collections.singletonList(createEmptyPath());
        }
        List<Path> paths = new ArrayList<>(alts.size());
        for (AlternativeInfo a : alts) {
            paths.add(a.path);
        }
        return paths;
    }

    public static class PotentialAlternativeInfo {
        int v;
        double weight;
    }

    public static class AlternativeInfo {
        final double shareWeight;
        final Path path;
        final IntIndexedContainer nodes;

        AlternativeInfo(Path path, double shareWeight) {
            this.path = path;
            this.shareWeight = shareWeight;
            this.nodes = path.calcNodes();
        }

        @Override
        public String toString() {
            return "AlternativeInfo{" +
                    "shareWeight=" + shareWeight +
                    ", path=" + path.calcNodes() +
                    '}';
        }

        public Path getPath() {
            return path;
        }

    }

}
