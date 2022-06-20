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

import com.carrotsearch.hppc.IntSet;
import com.carrotsearch.hppc.predicates.IntObjectPredicate;
import com.graphhopper.coll.GHIntHashSet;
import com.graphhopper.coll.GHIntObjectHashMap;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.graphhopper.util.Parameters.Algorithms.AltRoute.*;

/**
 * This class implements the alternative paths search using the "plateau" and partially the
 * "penalty" method described in the following papers.
 * <p>
 * <ul>
 * <li>Choice Routing Explanation - Camvit 2009:
 * http://www.camvit.com/camvit-technical-english/Camvit-Choice-Routing-Explanation-english.pdf</li>
 * <li>and refined in: Alternative Routes in Road Networks 2010:
 * http://www.cs.princeton.edu/~rwerneck/papers/ADGW10-alternatives-sea.pdf</li>
 * <li>other ideas 'Improved Alternative Route Planning', 2013:
 * https://hal.inria.fr/hal-00871739/document</li>
 * <li>via point 'storage' idea 'Candidate Sets for Alternative Routes in Road Networks', 2013:
 * https://algo2.iti.kit.edu/download/s-csarrn-12.pdf</li>
 * <li>Alternative route graph construction 2011:
 * http://algo2.iti.kit.edu/download/altgraph_tapas_extended.pdf
 * </li>
 * </ul>
 * <p>
 * Note: This algorithm can be slow for longer routes and alternatives are only really practical in combination with CH, see #2566
 *
 * @author Peter Karich
 */
public class AlternativeRoute extends AStarBidirection implements RoutingAlgorithm {
    private static final Comparator<AlternativeInfo> ALT_COMPARATOR = Comparator.comparingDouble(o -> o.sortBy);

    private final int maxPaths;
    /**
     * This variable influences the graph exploration for alternative paths. Specify a higher value than the default to
     * potentially get more alternatives and a lower value to improve query time but reduces chance to find alternatives.
     */
    private final double explorationFactor;
    /**
     * Decreasing this factor filters found alternatives and increases quality. E.g. if the factor is 2 than
     * all alternatives with a weight 2 times longer than the optimal weight are return.
     */
    private final double maxWeightFactor;
    /**
     * Decreasing this factor filters found alternatives and might increase quality. This parameter is used to avoid
     * alternatives too similar to the best path. Specify 0.2 to ensure maximum 20% of the best path are on the same roads.
     * The unit is also the 'weight'.
     */
    private final double maxShareFactor;
    /**
     * Increasing this factor filters found alternatives and might increase quality. This specifies the minimum plateau
     * portion of every alternative path that is required. Keep in mind that a plateau is often not complete especially
     * when the explorationFactor is low (and for performance reasons the explorationFactor should be as low as possible).
     * This is the reason we cannot require a too big plateau portion here as default.
     */
    private final double minPlateauFactor;

    public AlternativeRoute(Graph graph, Weighting weighting, TraversalMode traversalMode, PMap hints) {
        super(graph, weighting, traversalMode);
        if (weighting.hasTurnCosts() && !traversalMode.isEdgeBased())
            throw new IllegalStateException("Weightings supporting turn costs cannot be used with node-based traversal mode");

        this.maxPaths = hints.getInt(MAX_PATHS, 2);
        if (this.maxPaths < 2)
            throw new IllegalArgumentException("Use normal algorithm with less overhead instead if no alternatives are required");

        this.explorationFactor = hints.getDouble("alternative_route.max_exploration_factor", 1.12);
        this.maxWeightFactor = hints.getDouble(MAX_WEIGHT, 1.25);
        this.maxShareFactor = hints.getDouble(MAX_SHARE, 0.6);
        this.minPlateauFactor = hints.getDouble("alternative_route.min_plateau_factor", 0.1);
    }

    static List<String> getAltNames(Graph graph, SPTEntry ee) {
        if (ee == null || !EdgeIterator.Edge.isValid(ee.edge))
            return Collections.emptyList();

        EdgeIteratorState iter = graph.getEdgeIteratorState(ee.edge, Integer.MIN_VALUE);
        if (iter == null)
            return Collections.emptyList();

        String str = iter.getName();
        if (str.isEmpty())
            return Collections.emptyList();

        return Collections.singletonList(str);
    }

    static double calcSortBy(double weightInfluence, double weight,
                             double shareInfluence, double shareWeight,
                             double plateauInfluence, double plateauWeight) {
        return weightInfluence * weight + shareInfluence * shareWeight + plateauInfluence * plateauWeight;
    }

    @Override
    public void setMaxVisitedNodes(int numberOfNodes) {
        this.maxVisitedNodes = numberOfNodes;
    }

    public List<AlternativeInfo> calcAlternatives(int from, int to) {
        Path bestPath = searchBest(from, to);
        return calcAlternatives(bestPath, maxPaths,
                maxWeightFactor, 7,
                maxShareFactor, 0.8,
                minPlateauFactor, -0.2);
    }

    @Override
    public List<Path> calcPaths(int from, int to) {
        List<AlternativeInfo> alternatives = calcAlternatives(from, to);
        List<Path> paths = new ArrayList<>(alternatives.size());
        for (AlternativeInfo a : alternatives) {
            paths.add(a.getPath());
        }
        return paths;
    }

    @Override
    public String getName() {
        return Parameters.Algorithms.ALT_ROUTE;
    }

    public static class AlternativeInfo {
        private final double sortBy;
        private final Path path;
        private final SPTEntry shareStart;
        private final SPTEntry shareEnd;
        private final double shareWeight;
        private final List<String> names;

        public AlternativeInfo(double sortBy, Path path, SPTEntry shareStart, SPTEntry shareEnd,
                               double shareWeight, List<String> altNames) {
            this.names = altNames;
            this.sortBy = sortBy;
            this.path = path;
            this.path.setDescription(names);
            this.shareStart = shareStart;
            this.shareEnd = shareEnd;
            this.shareWeight = shareWeight;
        }

        public Path getPath() {
            return path;
        }

        public SPTEntry getShareStart() {
            return shareStart;
        }

        public SPTEntry getShareEnd() {
            return shareEnd;
        }

        public double getShareWeight() {
            return shareWeight;
        }

        public double getSortBy() {
            return sortBy;
        }

        @Override
        public String toString() {
            return names + ", sortBy:" + sortBy + ", shareWeight:" + shareWeight + ", " + path;
        }
    }

    @Override
    public boolean finished() {
        // we need to finish BOTH searches identical to CH
        if (finishedFrom && finishedTo)
            return true;

        if (isMaxVisitedNodesExceeded())
            return true;

        // The following condition is necessary to avoid traversing the full graph if areas are disconnected
        // but it is only valid for non-CH e.g. for CH it can happen that finishedTo is true but the from-SPT could still reach 'to'
        if (finishedFrom || finishedTo)
            return true;

        // increase overlap of both searches:
        return currFrom.weight + currTo.weight > explorationFactor * (bestWeight + stoppingCriterionOffset);
        // This is more precise but takes roughly 20% longer: return currFrom.weight > bestWeight && currTo.weight > bestWeight;
        // For bidir A* and AStarEdge.getWeightOfVisitedPath see comment in AStarBidirection.finished
    }

    public Path searchBest(int from, int to) {
        init(from, 0, to, 0);
        // init collections and bestPath.getWeight properly
        runAlgo();
        return extractPath();
    }

    /**
     * @return the information necessary to handle alternative paths. Note that the paths are
     * not yet extracted.
     */
    public List<AlternativeInfo> calcAlternatives(final Path bestPath, final int maxPaths,
                                                  double maxWeightFactor, final double weightInfluence,
                                                  final double maxShareFactor, final double shareInfluence,
                                                  final double minPlateauFactor, final double plateauInfluence) {
        final double maxWeight = maxWeightFactor * bestWeight;
        final GHIntObjectHashMap<IntSet> traversalIdMap = new GHIntObjectHashMap<>();
        final AtomicInteger startTID = addToMap(traversalIdMap, bestPath);

        // find all 'good' alternatives from forward-SPT matching the backward-SPT and optimize by
        // small total weight (1), small share and big plateau (3a+b) and do these expensive calculations
        // only for plateau start candidates (2)
        final List<AlternativeInfo> alternatives = new ArrayList<>(maxPaths);

        double bestPlateau = bestWeight;
        double bestShare = 0;
        double sortBy = calcSortBy(weightInfluence, bestWeight,
                shareInfluence, bestShare,
                plateauInfluence, bestPlateau);

        final AlternativeInfo bestAlt = new AlternativeInfo(sortBy, bestPath,
                bestFwdEntry, bestBwdEntry, bestShare, getAltNames(graph, bestFwdEntry));
        alternatives.add(bestAlt);
        AtomicReference<SPTEntry> bestEntry = new AtomicReference<>();

        bestWeightMapFrom.forEach(new IntObjectPredicate<SPTEntry>() {
            @Override
            public boolean apply(final int traversalId, final SPTEntry fromSPTEntry) {
                SPTEntry toSPTEntry = bestWeightMapTo.get(traversalId);
                if (toSPTEntry == null)
                    return true;

                // Using the parent is required to avoid duplicate edge in Path.
                // TODO we miss the turn cost weight (but at least we not duplicate the current edge weight)
                if (traversalMode.isEdgeBased() && toSPTEntry.parent != null)
                    toSPTEntry = toSPTEntry.parent;

                // The alternative path is suboptimal if U-turn (after fromSPTEntry)
                if (fromSPTEntry.edge == toSPTEntry.edge)
                    return true;

                // (1) skip too long paths
                final double weight = fromSPTEntry.getWeightOfVisitedPath() + toSPTEntry.getWeightOfVisitedPath();
                if (weight > maxWeight)
                    return true;

                if (isBestPath(fromSPTEntry))
                    return true;

                // For edge based traversal we need the next entry to find out the plateau start
                SPTEntry tmpFromEntry = traversalMode.isEdgeBased() ? fromSPTEntry.parent : fromSPTEntry;
                if (tmpFromEntry == null || tmpFromEntry.parent == null) {
                    // we can be here only if edge based and only if entry is not part of the best path
                    // e.g. when starting point has two edges and one is part of the best path the other edge is path of an alternative
                    assert traversalMode.isEdgeBased();
                } else {
                    int nextToTraversalId = traversalMode.createTraversalId(graph.getEdgeIteratorState(tmpFromEntry.edge, tmpFromEntry.parent.adjNode), true);
                    SPTEntry correspondingToEntry = bestWeightMapTo.get(nextToTraversalId);
                    if (correspondingToEntry != null) {
                        if (traversalMode.isEdgeBased())
                            correspondingToEntry = correspondingToEntry.parent;
                        if (correspondingToEntry.edge == fromSPTEntry.edge)
                            return true;
                    }
                }

                // (3a) calculate plateau, we know we are at the beginning of the 'from'-side of
                // the plateau A-B-C and go further to B
                // where B is the next-'from' of A and B is also the previous-'to' of A.
                //
                //      *<-A-B-C->*
                //        /    \
                //    start    end
                //
                // extend plateau in only one direction necessary (A to B to ...) as we know
                // that the from-SPTEntry is the start of the plateau or there is no plateau at all
                //
                double plateauWeight = 0;
                SPTEntry prevToSPTEntry = toSPTEntry, prevFrom = fromSPTEntry;
                while (prevToSPTEntry.parent != null) {
                    int nextFromTraversalId = traversalMode.createTraversalId(graph.getEdgeIteratorState(prevToSPTEntry.edge, prevToSPTEntry.parent.adjNode), false);
                    SPTEntry otherFromEntry = bestWeightMapFrom.get(nextFromTraversalId);
                    // end of a plateau
                    if (otherFromEntry == null ||
                            otherFromEntry.parent != prevFrom ||
                            otherFromEntry.edge != prevToSPTEntry.edge)
                        break;

                    prevFrom = otherFromEntry;
                    plateauWeight += (prevToSPTEntry.getWeightOfVisitedPath() - prevToSPTEntry.parent.getWeightOfVisitedPath());
                    prevToSPTEntry = prevToSPTEntry.parent;
                }

                if (plateauWeight <= 0 || plateauWeight / weight < minPlateauFactor)
                    return true;

                if (fromSPTEntry.parent == null)
                    throw new IllegalStateException("not implemented yet. in case of an edge based traversal the parent of fromSPTEntry could be null");

                // (3b) calculate share
                SPTEntry fromEE = getFirstShareEE(fromSPTEntry.parent, true);
                SPTEntry toEE = getFirstShareEE(toSPTEntry.parent, false);
                double shareWeight = fromEE.getWeightOfVisitedPath() + toEE.getWeightOfVisitedPath();
                boolean smallShare = shareWeight / bestWeight < maxShareFactor;
                if (smallShare) {
                    List<String> altNames = getAltNames(graph, fromSPTEntry);

                    double sortBy = calcSortBy(weightInfluence, weight, shareInfluence, shareWeight, plateauInfluence, plateauWeight);
                    double worstSortBy = getWorstSortBy();

                    // plateaus.add(new PlateauInfo(altName, plateauEdges));
                    if (sortBy < worstSortBy || alternatives.size() < maxPaths) {
                        Path path = DefaultBidirPathExtractor.extractPath(graph, weighting, fromSPTEntry, toSPTEntry, weight);

                        // for now do not add alternatives to set, if we do we need to remove then on alternatives.clear too (see below)
                        // AtomicInteger tid = addToMap(traversalIDMap, path);
                        // int tid = traversalMode.createTraversalId(path.calcEdges().get(0), false);
                        alternatives.add(new AlternativeInfo(sortBy, path, fromEE, toEE, shareWeight, altNames));

                        Collections.sort(alternatives, ALT_COMPARATOR);
                        if (alternatives.get(0) != bestAlt)
                            throw new IllegalStateException("best path should be always first entry");

                        if (alternatives.size() > maxPaths)
                            alternatives.subList(maxPaths, alternatives.size()).clear();
                    }
                }

                return true;
            }

            /**
             * Extract path until we stumble over an existing traversal id
             */
            SPTEntry getFirstShareEE(SPTEntry startEE, boolean reverse) {
                while (startEE.parent != null) {
                    // TODO we could make use of traversal ID directly if stored in SPTEntry
                    int tid = traversalMode.createTraversalId(graph.getEdgeIteratorState(startEE.edge, startEE.parent.adjNode), reverse);
                    if (isAlreadyExisting(tid))
                        return startEE;

                    startEE = startEE.parent;
                }

                return startEE;
            }

            /**
             * This method returns true if the specified tid is already existent in the
             * traversalIDMap
             */
            boolean isAlreadyExisting(final int tid) {
                final AtomicBoolean exists = new AtomicBoolean(false);
                traversalIdMap.forEach(new IntObjectPredicate<IntSet>() {
                    @Override
                    public boolean apply(int key, IntSet set) {
                        if (set.contains(tid)) {
                            exists.set(true);
                            return false;
                        }
                        return true;
                    }
                });

                return exists.get();
            }

            /**
             * Return the current worst weight for all alternatives
             */
            double getWorstSortBy() {
                if (alternatives.isEmpty())
                    throw new IllegalStateException("Empty alternative list cannot happen");
                return alternatives.get(alternatives.size() - 1).sortBy;
            }

            // returns true if fromSPTEntry is identical to the specified best path
            boolean isBestPath(SPTEntry fromSPTEntry) {
                if (traversalMode.isEdgeBased()) {
                    if (GHUtility.getEdgeFromEdgeKey(startTID.get()) == fromSPTEntry.edge) {
                        if (fromSPTEntry.parent == null)
                            throw new IllegalStateException("best path must have no parent but was non-null: " + fromSPTEntry);
                        if (bestEntry.get() != null && bestEntry.get().edge != fromSPTEntry.edge)
                            throw new IllegalStateException("there can be only one best entry but was " + fromSPTEntry + " vs old: " + bestEntry.get()
                                    + " " + graph.getEdgeIteratorState(fromSPTEntry.edge, fromSPTEntry.adjNode).fetchWayGeometry(FetchMode.ALL));
                        bestEntry.set(fromSPTEntry);
                        return true;
                    }

                } else if (fromSPTEntry.parent == null) {
                    if (startTID.get() != fromSPTEntry.adjNode)
                        throw new IllegalStateException("Start traversal ID has to be identical to root edge entry "
                                + "which is the plateau start of the best path but was: " + startTID + " vs. adjNode: " + fromSPTEntry.adjNode);
                    if (bestEntry.get() != null)
                        throw new IllegalStateException("there can be only one best entry but was " + fromSPTEntry + " vs old: " + bestEntry.get()
                                + " " + graph.getEdgeIteratorState(fromSPTEntry.edge, fromSPTEntry.adjNode).fetchWayGeometry(FetchMode.ALL));
                    bestEntry.set(fromSPTEntry);
                    return true;
                }

                return false;
            }
        });

        return alternatives;
    }

    /**
     * This method adds the traversal IDs of the specified path as set to the specified map.
     */
    AtomicInteger addToMap(GHIntObjectHashMap<IntSet> map, Path path) {
        IntSet set = new GHIntHashSet();
        final AtomicInteger startTID = new AtomicInteger(-1);
        for (EdgeIteratorState iterState : path.calcEdges()) {
            int tid = traversalMode.createTraversalId(iterState, false);
            set.add(tid);
            if (startTID.get() < 0) {
                // for node based traversal we need to explicitly add base node as starting node and to list
                if (!traversalMode.isEdgeBased()) {
                    tid = iterState.getBaseNode();
                    set.add(tid);
                }

                startTID.set(tid);
            }
        }
        map.put(startTID.get(), set);
        return startTID;
    }
}
