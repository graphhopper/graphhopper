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
import com.carrotsearch.hppc.IntIndexedContainer;
import com.graphhopper.routing.ar.*;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.BeelineWeightApproximator;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.SPTEntry;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Parameters;

import java.util.*;

/**
 * This class implements the alternative paths search for the AStar algorithm using the "viaNode" method described in
 * the following papers.
 * <p>
 * <ul>
 * <li>Candidate Sets for Alternative Routes in Road Networks - Luxen and Schieferdecker 2012:
 * http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.665.4573&rep=rep1&type=pdf
 * <li>
 * https://algo2.iti.kit.edu/download/ls-csarr-13-poster.pdf
 * </li>
 * </ul>
 *
 * @author Maximilian Sturm
 */
public class AlternativeRoute extends AStarBidirection {
    private static final Comparator<AlternativeInfo> ALT_COMPARATOR
            = new Comparator<AlternativeInfo>() {
        @Override
        public int compare(AlternativeInfo o1,
                           AlternativeInfo o2) {
            return Double.compare(o1.getSortBy(), o2.getSortBy());
        }
    };
    private final double weightInfluence = 4;
    private final double shareInfluence = 1;

    private double maxWeightFactor = 1.4;
    private double maxShareFactor = 0.6;
    private int maxPaths = 3;
    private int additionalPaths = 3;

    private int maxAdditionalNodes = 100000;
    private double advAlgorithmFactor = 1.5;
    private int visitedNodesFinished = -1;

    private int from;
    private int to;
    private ViaNodeSet viaNodeSet;
    private ArrayList<ViaNode> viaNodes;
    private boolean[] contactFound;
    private ArrayList<ContactNode> contactNodes;
    private ArrayList<AlternativeInfo> alternatives;

    public AlternativeRoute(Graph graph, Weighting weighting, TraversalMode traversalMode) {
        super(graph, weighting, traversalMode);
        contactFound = new boolean[graph.getNodes()];
        contactNodes = new ArrayList<>();
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

    /**
     * @param viaNodeSet contains information about all viaNodes. These are possible waypoints for alternative routes
     */
    public void setViaNodeSet(ViaNodeSet viaNodeSet) {
        this.viaNodeSet = viaNodeSet;
    }

    /**
     * @param from the origin node of the base graph (not the query graph)
     * @param to the destination node of the base graph (not the query graph)
     */
    public void setViaNodes(int from, int to) {
        if (viaNodeSet == null)
            return;
        setViaNodes(viaNodeSet.get(from, to));
    }

    /**
     * @param viaNodes the list of actual viaNodes used for this request
     */
    public void setViaNodes(IntArrayList viaNodes) {
        if (viaNodes == null)
            return;
        if (viaNodes.size() == 0)
            return;
        this.viaNodes = new ArrayList<>(viaNodes.size());
        for (int i = 0; i < viaNodes.size(); i++)
            this.viaNodes.add(new ViaNode(viaNodes.get(i)));
    }

    /**
     * @return whether the advanced algorithm or the base algorithm is being used for this request.
     * advanced algo: get alternatives by computing shortest paths between origin, destination and a couple of
     *                precomputed viaNodes
     * base algo: get alternatives by computing contact nodes between both search spaces
     */
    public boolean isAdvancedAlgo() {
        return viaNodes != null;
    }

    /**
     * @return all computed contact nodes during this request. This is null if the advanced algorithm is being used.
     */
    public IntArrayList getContactNodes() {
        IntArrayList nodes = new IntArrayList(contactNodes.size());
        for (int i = 0; i < contactNodes.size(); i++)
            nodes.add(contactNodes.get(i).getNode());
        return nodes;
    }

    /**
     * @return the contact points of the final good alternatives
     */
    public IntArrayList getUsedContactNodes() {
        IntArrayList nodes = new IntArrayList(alternatives.size() - 1);
        for (int i = 1; i < alternatives.size(); i++)
            nodes.add(alternatives.get(i).getViaNode());
        return nodes;
    }

    @Override
    public Path calcPath(int from, int to) {
        return calcPaths(from, to).get(0);
    }

    @Override
    public List<Path> calcPaths(int from, int to) {
        this.from = from;
        this.to = to;
        if (graph instanceof QueryGraph) {
            EdgeIterator iterator = graph.createEdgeExplorer().setBaseNode(from);
            if (iterator.next())
                from = iterator.getAdjNode();
            iterator = graph.createEdgeExplorer().setBaseNode(to);
            if (iterator.next())
                to = iterator.getAdjNode();
        }
        setViaNodes(from, to);
        return calcAlternatives(super.calcPath(this.from, this.to));
    }

    /**
     * @param mainRoute the path returned by the basic AStarBidirection
     * @return a list of paths containing the main route and all good alternatives
     */
    private List<Path> calcAlternatives(Path mainRoute) {
        alternatives = new ArrayList<>();
        alternatives.add(new AlternativeInfo(mainRoute, 0, -1));
        if (isAdvancedAlgo()) {
            for (ViaNode node : viaNodes) {
                int visitedNodesLeft = visitedNodesFinished + maxAdditionalNodes - getVisitedNodes();
                if (visitedNodesLeft <= 3)
                    break;
                AStarBidirection aStar1 = new AStarBidirection(graph, weighting, traversalMode);
                aStar1.setApproximation(getApproximation());
                aStar1.setMaxVisitedNodes(visitedNodesLeft);
                Path path1;
                try {
                    path1 = aStar1.calcPath(from, node.getNode());
                    visitedCountTo += aStar1.getVisitedNodes();
                } catch (Exception e) {
                    visitedCountTo += aStar1.getVisitedNodes();
                    continue;
                }
                visitedNodesLeft = visitedNodesFinished + maxAdditionalNodes - getVisitedNodes();
                if (visitedNodesLeft <= 3)
                    break;
                AStarBidirection aStar2 = new AStarBidirection(graph, weighting, traversalMode);
                aStar2.setApproximation(getApproximation());
                aStar2.setMaxVisitedNodes(visitedNodesLeft);
                Path path2;
                try {
                    path2 = aStar2.calcPath(node.getNode(), to);
                    visitedCountFrom += aStar2.getVisitedNodes();
                } catch (Exception e) {
                    visitedCountFrom += aStar2.getVisitedNodes();
                    continue;
                }
                Path altRoute = createPath(path1, path2);
                double sortBy = calcSortBy(mainRoute, altRoute, true);
                if (sortBy < Double.MAX_VALUE)
                    alternatives.add(new AlternativeInfo(altRoute, sortBy, node.getNode()));
                if (alternatives.size() == maxPaths + additionalPaths)
                    break;
            }
        } else {
            for (ContactNode node : contactNodes) {
                Path altRoute = createPath(node.getEntryFrom(), node.getEntryTo());
                double sortBy = calcSortBy(mainRoute, altRoute, true);
                if (sortBy < Double.MAX_VALUE)
                    alternatives.add(new AlternativeInfo(altRoute, sortBy, node.getNode()));
                if (alternatives.size() == maxPaths + additionalPaths)
                    break;
            }
        }

        Collections.sort(alternatives, ALT_COMPARATOR);
        boolean remove = false;
        for (int i = 1; i < alternatives.size(); i++) {
            if (remove) {
                alternatives.remove(i--);
                continue;
            }
            Path path = alternatives.get(i).getPath();
            for (int j = i + 1; j < alternatives.size(); j++) {
                if (calcSortBy(path, alternatives.get(j).getPath(), false) == Double.MAX_VALUE) {
                    alternatives.remove(i--);
                    break;
                }
            }
            if (i == maxPaths - 1)
                remove = true;
        }

        ArrayList<Path> paths = new ArrayList<>(alternatives.size());
        for (AlternativeInfo alternative : alternatives)
            paths.add(alternative.getPath());
        return paths;
    }

    /**
     * @param from
     * @param to
     * @return the list of contact nodes between the from and to node. This method is only being used for preparation
     * purposes
     */
    public IntArrayList calcContactNodes(int from, int to) {
        super.calcPath(from, to);
        return getContactNodes();
    }

    @Override
    public boolean finished() {
        if (!super.finished())
            return false;

        // if we're using the advanced algorithm we won't have to continue searching for contact nodes but we have to
        // define the maxVisitedNodes for the search spaces for viaNodes. This should be below 100000 to 150000 nodes
        // to get consistent query times below 0.5 seconds
        if (isAdvancedAlgo()) {
            maxAdditionalNodes = Math.min((int) (advAlgorithmFactor * maxAdditionalNodes), super.maxVisitedNodes - 3);
            visitedNodesFinished = getVisitedNodes();
            return true;
        }

        // after the basic AStarBidirection is finished we safe the amount of visited nodes and define for how long
        // we continue searching for contact nodes
        if (visitedNodesFinished == -1) {
            maxAdditionalNodes = Math.min(maxAdditionalNodes, super.maxVisitedNodes - 3);
            visitedNodesFinished = getVisitedNodes();
        }

        // this is needed to prevent the search space for this algorithm from growing to big compared to the normal
        // AStarBidirection
        if (getVisitedNodes() > visitedNodesFinished + maxAdditionalNodes)
            return true;

        if (finishedFrom && finishedTo)
            return true;

        if (!bestPath.isFound() && (finishedFrom || finishedTo))
            return true;

        return currFrom.weight >= bestPath.getWeight() && currTo.weight >= bestPath.getWeight();
    }

    @Override
    protected void updateBestPath(EdgeIteratorState edgeState, SPTEntry entryCurrent, int traversalId, boolean reverse) {
        if (entryCurrent.parent == null) {
            super.updateBestPath(edgeState, entryCurrent, traversalId, reverse);
            return;
        }
        if (visitedNodesFinished >= 0) {
            if (contactFound[entryCurrent.parent.adjNode]) {
                contactFound[entryCurrent.adjNode] = true;
            } else {
                if (!reverse) {
                    Iterator<SPTEntry> iterator = pqOpenSetTo.iterator();
                    while (iterator.hasNext()) {
                        SPTEntry entryOther = iterator.next();
                        if (entryCurrent.adjNode == entryOther.adjNode) {
                            contactFound[entryCurrent.adjNode] = true;
                            contactNodes.add(new ContactNode(entryCurrent, entryOther));
                        }
                    }
                } else {
                    Iterator<SPTEntry> iterator = pqOpenSetFrom.iterator();
                    while (iterator.hasNext()) {
                        SPTEntry entryOther = iterator.next();
                        if (entryCurrent.adjNode == entryOther.adjNode) {
                            contactFound[entryCurrent.adjNode] = true;
                            contactNodes.add(new ContactNode(entryOther, entryCurrent));
                        }
                    }
                }
            }
        }
        super.updateBestPath(edgeState, entryCurrent, traversalId, reverse);
    }

    @Override
    public String getName() {
        return Parameters.Algorithms.ALT_ROUTE + (isAdvancedAlgo() ? "|advanced" : "") + "|" + super.getName();
    }

    /**
     * @param mainRoute
     * @param altRoute
     * @return how good an alternative is, with lower values being better. If an alternative is considered bad,
     * Double.MAX_VALUE will be returned.
     */
    private double calcSortBy(Path mainRoute, Path altRoute, boolean check) {
        if (mainRoute.calcNodes().size() == 0 || altRoute.calcNodes().size() == 0) return Double.MAX_VALUE;
        double share = calcShare(mainRoute, altRoute, check);
        if (share > maxShareFactor) return Double.MAX_VALUE;
        double weight = calcWeight(mainRoute, altRoute, share);
        if (weight > maxWeightFactor) return Double.MAX_VALUE;
        return weightInfluence * (weight - 1) / (maxWeightFactor - 1) + shareInfluence * share / maxShareFactor;
    }

    /**
     * @param mainRoute
     * @param altRoute
     * @param check
     * @return the shared weight between both routes based on the main route. This will return 1 if both paths are
     * identical and 0 if they are completely different. If there are more than one sharing part at the start and one
     * sharing part at the end of both routes Double.MAX_VALUE will be returned. If check is true and checkAlternative
     * for this altRoute is false, also Double.MAX_VALUE will be returned.
     */
    private double calcShare(Path mainRoute, Path altRoute, boolean check) {
        List<EdgeIteratorState> mainEdges = mainRoute.calcEdges();
        List<EdgeIteratorState> altEdges = altRoute.calcEdges();
        IntIndexedContainer mainNodes = mainRoute.calcNodes();
        IntIndexedContainer altNodes = altRoute.calcNodes();

        int start = 0;
        int end = mainRoute.getEdgeCount();
        int delta = altEdges.size() - mainEdges.size();
        double sharedWeight = 0;

        for (int i = 0; i < mainEdges.size(); i++) {
            if (mainEdges.get(i).getEdge() == altEdges.get(i).getEdge()) {
                sharedWeight += weighting.calcWeight(mainEdges.get(i), false, -1);
            } else {
                start = i + 1;
                break;
            }
        }
        for (int i = mainEdges.size() - 1; i >= 0; i--) {
            if (mainEdges.get(i).getEdge() == altEdges.get(i + delta).getEdge()) {
                sharedWeight += weighting.calcWeight(mainEdges.get(i), false, -1);
            } else {
                end = i + 1;
                break;
            }
        }

        if (sharedWeight / mainRoute.getWeight() >= 1)
            return 1;

        if (check)
            if (!checkAlternative(altNodes, start - 10, end + delta + 10))
                return Double.MAX_VALUE;

        for (int i = start; i < end; i++)
            for (int j = start; j < end + delta; j++)
                if (mainNodes.get(i) == altNodes.get(j))
                    return Double.MAX_VALUE;

        return sharedWeight / mainRoute.getWeight();
    }

    /**
     * @param mainRoute
     * @param altRoute
     * @param share
     * @return how much longer not shared part of the alternative is compared to the main route.
     */
    private double calcWeight(Path mainRoute, Path altRoute, double share) {
        double mainWeight = mainRoute.getWeight() * (1 - share);
        double altWeight = altRoute.getWeight() - mainRoute.getWeight() * share;
        return altWeight / mainWeight;
    }

    /**
     * @param altNodes the nodes of the alternative
     * @param start the point at which the first shared part ends
     * @param end the point at which the last shared part starts
     * @return false if altNodes contains at least one node more than once
     */
    private boolean checkAlternative(IntIndexedContainer altNodes, int start, int end) {
        BeelineWeightApproximator approximator =
                new BeelineWeightApproximator(graph.getNodeAccess(), new ShortestWeighting(weighting.getFlagEncoder()));
        if (start < 0)
            start = 0;
        if (end > altNodes.size())
            end = altNodes.size();
        for (int i = start + 10; i < end - 10; i++){
            if (i != altNodes.lastIndexOf(altNodes.get(i)))
                return false;
            approximator.setTo(altNodes.get(i + 10));
            if (approximator.approximate(altNodes.get(i - 10)) <= 100)
                return false;
            approximator.setTo(altNodes.get(i + 5));
            if (approximator.approximate(altNodes.get(i - 5)) <= 50)
                return false;
        }
        return true;
    }

    /**
     * @param entryFrom the contactNode's from entry
     * @param entryTo the contactNode's to entry
     * @return a path between the origin and destination which runs via the node shared by both entries. This method is
     * used to create an alternative route with the base algorithm.
     */
    private Path createPath(SPTEntry entryFrom, SPTEntry entryTo) {
        PathBidirRef path = new PathBidirRef(graph, weighting);
        path.setSPTEntry(entryFrom);
        path.setSPTEntryTo(entryTo);
        path.setWeight(entryFrom.weight + entryTo.weight);
        return path.extract();
    }

    /**
     * @param path1 a path from the origin to as specific node
     * @param path2 a path from the specific node to the destination
     * @return a path between the origin and destination which runs via the specific node. This method is used to create
     * an alternative route with the advanced algorithm.
     */
    private Path createPath(Path path1, Path path2) {
        List<EdgeIteratorState> edges = path1.calcEdges();
        if (edges.size() == 0)
            return new Path(graph, weighting);
        SPTEntry currentEntry = new SPTEntry(-1, edges.get(0).getBaseNode(), 0);
        double currentWeight = 0;
        for (EdgeIteratorState edge : edges) {
            currentWeight += weighting.calcWeight(edge, false, -1);
            SPTEntry newEntry = new SPTEntry(edge.getEdge(), edge.getAdjNode(), currentWeight);
            newEntry.parent = currentEntry;
            currentEntry = newEntry;
        }
        edges = path2.calcEdges();
        if (edges.size() == 0)
            return new Path(graph, weighting);
        for (EdgeIteratorState edge : edges) {
            currentWeight += weighting.calcWeight(edge, false, -1);
            SPTEntry newEntry = new SPTEntry(edge.getEdge(), edge.getAdjNode(), currentWeight);
            newEntry.parent = currentEntry;
            currentEntry = newEntry;
        }
        return new Path(graph, weighting).setWeight(currentWeight).setSPTEntry(currentEntry).extract();
    }
}
