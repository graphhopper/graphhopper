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

import com.carrotsearch.hppc.cursors.IntObjectCursor;
import com.graphhopper.routing.util.AbstractAlgoPreparation;
import com.graphhopper.routing.util.LevelEdgeFilter;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.SPTEntry;
import com.graphhopper.util.EdgeIteratorState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

import static com.graphhopper.util.Parameters.Algorithms.ALT_ROUTE;

/**
 * This class computes viaNodes for all possible pairs of areas which will help finding alternative routes
 *
 * @author Maximilian Sturm
 */
public class PrepareAlternativeRoute extends AbstractAlgoPreparation implements RoutingAlgorithmFactory {
    private final Graph graph;
    private final Weighting weighting;
    private final TraversalMode traversalMode;
    private final boolean CH;

    private int areas;
    private int precision;
    private int maxVisitedNodes = Integer.MAX_VALUE;
    private double explorationFactor = 1.0;
    private double maxWeightFactor = 1.4;
    private double maxShareFactor = 0.6;
    private int maxPaths = 3;
    private int additionalPaths = 3;

    private GraphPartition partition;
    private ArrayList<Integer> viaNodeList[][];
    private ViaNodeSet viaNodes;

    public PrepareAlternativeRoute(Graph graph, Weighting weighting, TraversalMode traversalMode) {
        this.graph = graph;
        this.weighting = weighting;
        this.traversalMode = traversalMode;
        if (graph.getClass().getName().contains("CHGraph") && weighting.getName().contains("prepare"))
            CH = true;
        else
            CH = false;
        areas = (int) Math.pow(2, Math.log10(this.graph.getNodes()));
        if (areas < 8)
            areas = 8;
        setPrecision(5);
    }

    /**
     * @return whether the algorithm uses contraction hierarchies. This is true if the graph is a CHGraph and the
     * weighing is a PreparationWeighting
     */
    public boolean isCH() {
        return CH;
    }

    /**
     * @param areas specifies in how many areas the graph will be split into
     */
    public void setAreas(int areas) {
        int currentPrecision = graph.getNodes() / this.areas / precision;
        this.areas = areas;
        if (this.areas < 8)
            throw new IllegalStateException("With less than 8 areas it could be possible that all areas are directly connected to each other");
        if (currentPrecision <= 0)
            precision = Integer.MAX_VALUE;
        else
            precision = graph.getNodes() / this.areas / currentPrecision;
    }

    /**
     * @param precision specifies how many nodes per area will be used on average to compute its viaNodes. Higher
     *                  values mean higher computation time but may result in finding more viaNodes. The default value
     *                  is 5 which seems to be good enough to find most viaNodes
     */
    public void setPrecision(int precision) {
        if (precision < 1)
            throw new IllegalStateException("Precision must be bigger than 0");
        else if (precision == 1)
            this.precision = Integer.MAX_VALUE;
        else
            this.precision = graph.getNodes() / this.areas / (precision - 1);
        if (this.precision < 1)
            this.precision = 1;
    }

    public void setMaxVisitedNodes(int numberOfNodes) {
        maxVisitedNodes = numberOfNodes;
    }

    /**
     * @param explorationFactor specifies how much is explored compared to normal bidirectional dijkstra. Higher values
     *                          mean more alternatives may be discovered but this also results in much higher
     *                          computation times
     */
    public void setExplorationFactor(double explorationFactor) {
        this.explorationFactor = explorationFactor;
        if (this.explorationFactor < 1)
            throw new IllegalStateException("The explorationFactor must be bigger or equal to 1");
    }

    /**
     * @param maxWeightFactor defines how much higher the alternative's not-shared weight can be compared to the main
     *                        route's not-shared weight in order to be called good alternative
     */
    public void setMaxWeightFactor(double maxWeightFactor) {
        this.maxWeightFactor = maxWeightFactor;
        if (this.maxWeightFactor <= 1)
            throw new IllegalStateException("The maxWeightFactor must be bigger than 1");
    }

    /**
     * @param maxShareFactor defines the maximum weight an alternative is allowed to share with the main route in order
     *                       to be called good alternative
     */
    public void setMaxShareFactor(double maxShareFactor) {
        this.maxShareFactor = maxShareFactor;
        if (this.maxShareFactor <= 0 || this.maxShareFactor > 1)
            throw new IllegalStateException("The maxShareFactor must be between 0 and 1");
    }

    /**
     * @param maxPaths specifies the maximum amount of paths that will be returned, including the main route
     */
    public void setMaxPaths(int maxPaths) {
        this.maxPaths = maxPaths;
        if (this.maxPaths < 2)
            throw new IllegalStateException("Use normal algorithm with less overhead instead if no alternatives are required");
    }

    /**
     * @param additionalPaths specifies out of how many paths the alternatives will be calculated. Having more
     *                        additional paths will allow the algorithm to choose between more alternatives and
     *                        therefore return better alternatives. It's recommended to set this equal to maxPaths
     */
    public void setAdditionalPaths(int additionalPaths) {
        this.additionalPaths = additionalPaths;
        if (this.additionalPaths < 0)
            throw new IllegalStateException("The amount of additional calculated Paths can't be negative");
    }

    @Override
    public void doWork() {
        super.doWork();
        partition = new GraphPartition(graph.getBaseGraph(), areas);
        partition.doWork();
        viaNodeList = new ArrayList[areas][areas];
        for (int area1 = 0; area1 < areas; area1++)
            for (int area2 = 0; area2 < areas; area2++)
                if (!partition.isDirectlyConnected(area1, area2))
                    viaNodeList[area1][area2] = searchViaNodes(area1, area2);
        viaNodes = createViaNodes();
    }

    @Override
    public RoutingAlgorithm createAlgo(Graph g, AlgorithmOptions opts) {
        if (!ALT_ROUTE.equals(opts.getAlgorithm()))
            throw new IllegalStateException("This class can only create algorithms for alternative routes. Use another RoutingAlgorithmFactory for " + opts.getAlgorithm() + ".");
        AlternativeRoute algo = new AlternativeRoute(graph, weighting, traversalMode);
        algo.setViaNodes(viaNodes);
        algo.setMaxVisitedNodes(maxVisitedNodes);
        if (isCH())
            algo.setEdgeFilter(new LevelEdgeFilter((CHGraph) graph));
        algo.setExplorationFactor(explorationFactor);
        algo.setMaxWeightFactor(maxWeightFactor);
        algo.setMaxShareFactor(maxShareFactor);
        algo.setMaxPaths(maxPaths);
        algo.setAdditionalPaths(additionalPaths);
        return algo;
    }

    /**
     * @return all found viaNodes as viaNodeSet
     */
    public ViaNodeSet getViaNodes() {
        return viaNodes;
    }

    private ArrayList<Integer> searchViaNodes(int area1, int area2) {
        ArrayList<Integer> nodes1 = partition.getNodes(area1);
        ArrayList<Integer> nodes2 = partition.getNodes(area2);
        ArrayList<Integer> viaNodes = new ArrayList<>();
        ArrayList<Integer> candidateNodes = new ArrayList<>();
        Loop:
        for (int i = 0; i < nodes1.size(); i += precision) {
            for (int j = 0; j < nodes2.size(); j += precision) {
                ContactSearch contactSearch = new ContactSearch(graph, weighting, traversalMode);
                contactSearch.setMaxVisitedNodes(maxVisitedNodes);
                contactSearch.setExplorationFactor(explorationFactor);
                if (isCH())
                    contactSearch.setEdgeFilter(new LevelEdgeFilter((CHGraph) graph));
                candidateNodes = contactSearch.getContactNodes(nodes1.get(i), nodes2.get(j));
                if (candidateNodes.size() > 0)
                    break Loop;
            }
        }
        for (int i = 0; i < nodes1.size(); i += precision) {
            int from = nodes1.get(i);
            for (int j = 0; j < nodes2.size(); j += precision) {
                int to = nodes2.get(j);
                ViaNodeSearch viaNodeSearch = new ViaNodeSearch(graph, weighting, traversalMode);
                viaNodeSearch.setMaxVisitedNodes(maxVisitedNodes);
                viaNodeSearch.setExplorationFactor(explorationFactor);
                viaNodeSearch.setMaxWeightFactor(maxWeightFactor);
                viaNodeSearch.setMaxShareFactor(maxShareFactor);
                viaNodeSearch.setMaxPaths(maxPaths);
                viaNodeSearch.setAdditionalPaths(additionalPaths);
                if (isCH())
                    viaNodeSearch.setEdgeFilter(new LevelEdgeFilter((CHGraph) graph));
                viaNodeSearch.doWork(from, to, viaNodes, candidateNodes);
            }
        }
        if (viaNodes.size() == 0)
            return null;
        return viaNodes;
    }

    private ViaNodeSet createViaNodes() {
        int nodes = graph.getBaseGraph().getNodes();
        int[] area = new int[nodes];
        for (int i = 0; i < nodes; i++)
            area[i] = partition.getArea(i);
        boolean[][] directlyConnected = new boolean[areas][areas];
        for (int i = 0; i < areas; i++)
            for (int j = 0; j < areas; j++)
                directlyConnected[i][j] = partition.isDirectlyConnected(i, j);
        return new ViaNodeSet(area, directlyConnected, viaNodeList);
    }

    /**
     * This class finds the contact nodes between two search spaces. This is needed to get the contact nodes of two areas
     * which will all be possible viaNodes for this pair of areas
     */
    private static class ContactSearch extends AlternativeRouteAlgorithm {
        private boolean[] contactFound;
        private ArrayList<Integer> contactNodes = new ArrayList<>();

        private ContactSearch(Graph graph, Weighting weighting, TraversalMode traversalMode) {
            super(graph, weighting, traversalMode);
            contactFound = new boolean[graph.getNodes()];
        }

        @Override
        protected void updateBestPath(EdgeIteratorState edgeState, SPTEntry entryCurrent, int traversalId) {
            if (entryCurrent.parent != null) {
                if (contactFound[entryCurrent.parent.adjNode]) {
                    contactFound[entryCurrent.adjNode] = true;
                } else {
                    Iterator<IntObjectCursor<SPTEntry>> iterator = bestWeightMapOther.iterator();
                    while (iterator.hasNext()) {
                        int node = iterator.next().value.adjNode;
                        if (entryCurrent.adjNode == node) {
                            contactFound[node] = true;
                            contactNodes.add(node);
                        }
                    }
                }
            }
            super.updateBestPath(edgeState, entryCurrent, traversalId);
        }

        private ArrayList<Integer> getContactNodes(int from, int to) {
            checkAlreadyRun();
            createAndInitPath();
            initFrom(from, 0);
            initTo(to, 0);
            runAlgo();
            return contactNodes;
        }
    }

    /**
     * This class takes a list of contact nodes and already found viaNodes to compute new viaNodes. It does this by
     * checking if sufficient alternative routes are found between two nodes using only already computed viaNodes. If this
     * isn't the case it searches the contact node list for other good alternatives and adds every good contact node to
     * the viaNodes
     */
    private static class ViaNodeSearch extends AlternativeRouteAlgorithm {
        ArrayList<Integer> viaNodes;
        ArrayList<Integer> candidateNodes;
        ArrayList<ViaPoint> viaPoints;
        ArrayList<ViaPoint> candidatePoints;

        private ViaNodeSearch(Graph graph, Weighting weighting, TraversalMode traversalMode) {
            super(graph, weighting, traversalMode);
        }

        @Override
        protected void updateBestPath(EdgeIteratorState edgeState, SPTEntry entryCurrent, int traversalId) {
            if (entryCurrent.parent != null) {
                int node = entryCurrent.adjNode;
                boolean found = false;
                for (ViaPoint point : viaPoints) {
                    if (node == point.getNode()) {
                        if (bestWeightMapTo == bestWeightMapOther) {
                            point.addEntryFrom(entryCurrent);
                        } else {
                            point.addEntryTo(entryCurrent);
                        }
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    for (ViaPoint point : candidatePoints) {
                        if (node == point.getNode()) {
                            if (bestWeightMapTo == bestWeightMapOther) {
                                point.addEntryFrom(entryCurrent);
                            } else {
                                point.addEntryTo(entryCurrent);
                            }
                            break;
                        }
                    }
                }
            }
            super.updateBestPath(edgeState, entryCurrent, traversalId);
        }

        private void doWork(int from, int to, ArrayList<Integer> viaNodes, ArrayList<Integer> candidateNodes) {
            this.viaNodes = viaNodes;
            this.candidateNodes = candidateNodes;
            viaPoints = new ArrayList<>();
            candidatePoints = new ArrayList<>();
            for (int node : this.viaNodes)
                viaPoints.add(new ViaPoint(node));
            for (int node : this.candidateNodes)
                candidatePoints.add(new ViaPoint(node));
            checkAlreadyRun();
            createAndInitPath();
            initFrom(from, 0);
            initTo(to, 0);
            runAlgo();
            Path mainRoute = extractPath();
            ArrayList<AlternativeInfo> alternatives = new ArrayList<>();
            alternatives.add(new AlternativeInfo(mainRoute, 0, -1));
            ArrayList<ContactPoint> points = new ArrayList<>();
            for (ViaPoint point : viaPoints)
                points.addAll(point.createContactPoints());
            for (ContactPoint point : points) {
                Path altRoute = createPath(point.getEntryFrom(), point.getEntryTo());
                double sortBy = calcSortBy(mainRoute, altRoute);
                if (sortBy < Double.MAX_VALUE)
                    alternatives.add(new AlternativeInfo(altRoute, sortBy, point.getNode()));
                if (alternatives.size() == maxPaths + additionalPaths)
                    break;
            }
            if (alternatives.size() <= maxPaths + additionalPaths) {
                points.clear();
                for (ViaPoint point : candidatePoints)
                    points.addAll(point.createContactPoints());
                for (ContactPoint point : points) {
                    Path altRoute = createPath(point.getEntryFrom(), point.getEntryTo());
                    double sortBy = calcSortBy(mainRoute, altRoute);
                    if (sortBy < Double.MAX_VALUE)
                        alternatives.add(new AlternativeInfo(altRoute, sortBy, point.getNode()));
                    if (alternatives.size() == maxPaths + additionalPaths)
                        break;
                }
            }
            Collections.sort(alternatives, ALT_COMPARATOR);
            ArrayList<Path> paths = new ArrayList<>();
            Iterator<AlternativeInfo> alternativeIterator = alternatives.iterator();
            paths.add(alternativeIterator.next().getPath());
            Loop:
            while (alternativeIterator.hasNext()) {
                AlternativeInfo alternative = alternativeIterator.next();
                Path path = alternative.getPath();
                int node = alternative.getViaNode();
                Iterator<Path> pathIterator = paths.iterator();
                pathIterator.next();
                while (pathIterator.hasNext())
                    if (calcSortBy(path, pathIterator.next()) == Double.MAX_VALUE)
                        continue Loop;
                paths.add(path);
                if (!viaNodes.contains(node)) {
                    viaNodes.add(node);
                    candidateNodes.remove(Integer.valueOf(node));
                }
                if (paths.size() == maxPaths)
                    break;
            }
        }
    }
}
