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
package com.graphhopper.routing

import com.carrotsearch.hppc.IntSet
import com.carrotsearch.hppc.predicates.IntObjectPredicate
import com.graphhopper.coll.GHIntHashSet
import com.graphhopper.routing.util.TraversalMode
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.storage.Graph
import com.graphhopper.util.EdgeIterator
import com.graphhopper.util.FetchMode
import com.graphhopper.util.PMap
import com.graphhopper.util.Parameters
import com.graphhopper.util.Parameters.Algorithms.AltRoute
import java.util.Collections
import java.util.Comparator
import java.util.concurrent.atomic.AtomicReference

/**
 * This class implements the alternative paths search using the "plateau" and partially the
 * "penalty" method described in the following papers.
 *
 *  * Choice Routing Explanation - Camvit 2009:
 * http://www.camvit.com/camvit-technical-english/Camvit-Choice-Routing-Explanation-english.pdf
 *  * and refined in: Alternative Routes in Road Networks 2010:
 * http://www.cs.princeton.edu/~rwerneck/papers/ADGW10-alternatives-sea.pdf
 *  * other ideas 'Improved Alternative Route Planning', 2013:
 * https://hal.inria.fr/hal-00871739/document
 *  * via point 'storage' idea 'Candidate Sets for Alternative Routes in Road Networks', 2013:
 * https://algo2.iti.kit.edu/download/s-csarrn-12.pdf
 *  * Alternative route graph construction 2011:
 * http://algo2.iti.kit.edu/download/altgraph_tapas_extended.pdf
 *
 * Note: This algorithm can be slow for longer routes and alternatives are only really practical in combination with CH, see #2566
 *
 * @author Peter Karich
 */
class AlternativeRoute(graph: Graph, weighting: Weighting, traversalMode: TraversalMode, hints: PMap) :
    AStarBidirection(graph, weighting, traversalMode), RoutingAlgorithm {

    private val maxPaths: Int

    /**
     * This variable influences the graph exploration for alternative paths. Specify a higher value than the default to
     * potentially get more alternatives and a lower value to improve query time but reduces chance to find alternatives.
     */
    private val explorationFactor: Double

    /**
     * Decreasing this factor filters found alternatives and increases quality. E.g. if the factor is 2 than
     * all alternatives with a weight 2 times longer than the optimal weight are return.
     */
    private val maxWeightFactor: Double

    /**
     * Decreasing this factor filters found alternatives and might increase quality. This parameter is used to avoid
     * alternatives too similar to the best path. Specify 0.2 to ensure maximum 20% of the best path are on the same roads.
     * The unit is also the 'weight'.
     */
    private val maxShareFactor: Double

    /**
     * Increasing this factor filters found alternatives and might increase quality. This specifies the minimum plateau
     * portion of every alternative path that is required. Keep in mind that a plateau is often not complete especially
     * when the explorationFactor is low (and for performance reasons the explorationFactor should be as low as possible).
     * This is the reason we cannot require a too big plateau portion here as default.
     */
    private val minPlateauFactor: Double

    init {
        check(!(weighting.hasTurnCosts() && !traversalMode.isEdgeBased)) {
            "Weightings supporting turn costs cannot be used with node-based traversal mode"
        }

        this.maxPaths = hints.getInt(AltRoute.MAX_PATHS, 2)
        require(this.maxPaths >= 2) { "Use normal algorithm with less overhead instead if no alternatives are required" }

        this.explorationFactor = hints.getDouble("alternative_route.max_exploration_factor", 1.12)
        this.maxWeightFactor = hints.getDouble(AltRoute.MAX_WEIGHT, 1.25)
        this.maxShareFactor = hints.getDouble(AltRoute.MAX_SHARE, 0.6)
        this.minPlateauFactor = hints.getDouble("alternative_route.min_plateau_factor", 0.1)
    }

    companion object {
        private val ALT_COMPARATOR: Comparator<AlternativeInfo> = Comparator.comparingDouble { o: AlternativeInfo -> o.sortBy }

        internal fun getAltNames(graph: Graph, ee: SPTEntry?): List<String> {
            if (ee == null || !EdgeIterator.Edge.isValid(ee.edge))
                return emptyList()

            val iter = graph.getEdgeIteratorState(ee.edge, Integer.MIN_VALUE) ?: return emptyList()

            val str = iter.name
            if (str.isEmpty())
                return emptyList()

            return Collections.singletonList(str)
        }

        internal fun calcSortBy(
            weightInfluence: Double, weight: Double,
            shareInfluence: Double, shareWeight: Double,
            plateauInfluence: Double, plateauWeight: Double
        ): Double {
            return weightInfluence * weight + shareInfluence * shareWeight + plateauInfluence * plateauWeight
        }
    }

    fun calcAlternatives(from: Int, to: Int): List<AlternativeInfo> {
        val bestPath = searchBest(from, to)
        return calcAlternatives(
            bestPath, maxPaths,
            maxWeightFactor, 7.0,
            maxShareFactor, 0.8,
            minPlateauFactor, -0.2
        )
    }

    override fun calcPaths(from: Int, to: Int): List<Path> {
        checkAlreadyRun()
        setupFinishTime()
        val alternatives = calcAlternatives(from, to)
        val paths = ArrayList<Path>(alternatives.size)
        for (a in alternatives) {
            paths.add(a.getPath())
        }
        return paths
    }

    override fun getName(): String = Parameters.Algorithms.ALT_ROUTE

    class AlternativeInfo(
        @JvmField internal val sortBy: Double,
        @JvmField internal val path: Path,
        private val shareWeight: Double,
        altNames: List<String>
    ) {
        private val names: List<String> = altNames

        init {
            this.path.setDescription(names)
        }

        fun getPath(): Path = path

        fun getShareWeight(): Double = shareWeight

        fun getSortBy(): Double = sortBy

        override fun toString(): String = "$names, sortBy:$sortBy, shareWeight:$shareWeight, $path"
    }

    public override fun finished(): Boolean {
        // we need to finish BOTH searches identical to CH
        if (finishedFrom && finishedTo)
            return true

        if (isMaxVisitedNodesExceeded() || isTimeoutExceeded())
            return true

        // The following condition is necessary to avoid traversing the full graph if areas are disconnected
        // but it is only valid for non-CH e.g. for CH it can happen that finishedTo is true but the from-SPT could still reach 'to'
        if (finishedFrom || finishedTo)
            return true

        // increase overlap of both searches:
        return currFrom!!.weight + currTo!!.weight > explorationFactor * (bestWeight + stoppingCriterionOffset)
        // This is more precise but takes roughly 20% longer: return currFrom.weight > bestWeight && currTo.weight > bestWeight;
        // For bidir A* and AStarEdge.getWeightOfVisitedPath see comment in AStarBidirection.finished
    }

    fun searchBest(from: Int, to: Int): Path {
        init(from, 0.0, to, 0.0)
        // init collections and bestPath.getWeight properly
        runAlgo()
        return extractPath()
    }

    /**
     * @return the information necessary to handle alternative paths. Note that the paths are
     * not yet extracted.
     */
    fun calcAlternatives(
        bestPath: Path, maxPaths: Int,
        maxWeightFactor: Double, weightInfluence: Double,
        maxShareFactor: Double, shareInfluence: Double,
        minPlateauFactor: Double, plateauInfluence: Double
    ): List<AlternativeInfo> {
        if (!bestPath.isFound()) {
            val notFound = ArrayList<AlternativeInfo>(1)
            notFound.add(AlternativeInfo(0.0, bestPath, 0.0, emptyList()))
            return notFound
        }
        val maxWeight = maxWeightFactor * bestWeight
        // Edge IDs of the best path - used to compute share by counting actual shared
        // edges on each candidate (analogous to AlternativeRouteCH.sharedDistance).
        val bestPathEdges: IntSet = GHIntHashSet(bestPath.getEdges())

        // For edge-based this is the first edge id of the best path, for node-based the from node.
        val startEdgeOrNode = if (traversalMode.isEdgeBased)
            (if (bestPath.getEdges().isEmpty) -1 else bestPath.getEdges().get(0))
        else
            bestPath.getFromNode()

        // find all 'good' alternatives from forward-SPT matching the backward-SPT and optimize by
        // small total weight (1), small share and big plateau (3a+b) and do these expensive calculations
        // only for plateau start candidates (2)
        val alternatives = ArrayList<AlternativeInfo>(maxPaths)

        val bestPlateau = bestWeight
        val bestShare = 0.0
        val sortBy = calcSortBy(
            weightInfluence, bestWeight,
            shareInfluence, bestShare,
            plateauInfluence, bestPlateau
        )

        val bestAlt = AlternativeInfo(sortBy, bestPath, bestShare, getAltNames(graph, bestFwdEntry))
        alternatives.add(bestAlt)
        val bestEntry = AtomicReference<SPTEntry>()

        bestWeightMapFrom.forEach(object : IntObjectPredicate<SPTEntry> {
            override fun apply(traversalId: Int, fromSPTEntry: SPTEntry): Boolean {
                var toSPTEntry: SPTEntry = bestWeightMapTo.get(traversalId) ?: return true

                // Using the parent is required to avoid duplicate edge in Path.
                // TODO we miss the turn cost weight (but at least we not duplicate the current edge weight)
                if (traversalMode.isEdgeBased && toSPTEntry.parent != null)
                    toSPTEntry = toSPTEntry.parent!!

                // The alternative path is suboptimal if U-turn (after fromSPTEntry)
                if (fromSPTEntry.edge == toSPTEntry.edge)
                    return true

                // (1) skip too long paths
                val weight = (fromSPTEntry.getWeightOfVisitedPath() + toSPTEntry.getWeightOfVisitedPath()
                        + weighting.calcTurnWeight(fromSPTEntry.edge, fromSPTEntry.adjNode, toSPTEntry.edge))
                if (weight > maxWeight)
                    return true

                // (2a)
                if (isStartOfFwdSPT(fromSPTEntry))
                    return true

                // (2b) For edge based traversal we need the next entry to find out the plateau start
                val tmpFromEntry = if (traversalMode.isEdgeBased) fromSPTEntry.parent else fromSPTEntry
                if (tmpFromEntry == null || tmpFromEntry.parent == null) {
                    // we can be here only if edge based and only if entry is not part of the best path
                    // e.g. when starting point has two edges and one is part of the best path the other edge is path of an alternative
                    assert(traversalMode.isEdgeBased)
                } else {
                    val nextToTraversalId = traversalMode.createTraversalId(
                        graph.getEdgeIteratorState(tmpFromEntry.edge, tmpFromEntry.parent!!.adjNode)!!, true
                    )
                    var correspondingToEntry = bestWeightMapTo.get(nextToTraversalId)
                    if (correspondingToEntry != null) {
                        if (traversalMode.isEdgeBased)
                            correspondingToEntry = correspondingToEntry.parent
                        if (correspondingToEntry!!.edge == fromSPTEntry.edge)
                            return true
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
                var plateauWeight = 0.0
                var prevToSPTEntry = toSPTEntry
                var prevFrom = fromSPTEntry
                while (prevToSPTEntry.parent != null) {
                    val nextFromTraversalId = traversalMode.createTraversalId(
                        graph.getEdgeIteratorState(prevToSPTEntry.edge, prevToSPTEntry.parent!!.adjNode)!!, false
                    )
                    val otherFromEntry = bestWeightMapFrom.get(nextFromTraversalId)
                    // end of a plateau
                    if (otherFromEntry == null ||
                        otherFromEntry.parent !== prevFrom ||
                        otherFromEntry.edge != prevToSPTEntry.edge
                    )
                        break

                    prevFrom = otherFromEntry
                    plateauWeight += (prevToSPTEntry.getWeightOfVisitedPath() - prevToSPTEntry.parent!!.getWeightOfVisitedPath())
                    prevToSPTEntry = prevToSPTEntry.parent!!
                }

                if (plateauWeight <= 0 || plateauWeight / weight < minPlateauFactor)
                    return true

                checkNotNull(fromSPTEntry.parent) {
                    "not implemented yet. in case of an edge based traversal the parent of fromSPTEntry could be null"
                }

                // (3b) Calculate share by walking the candidate's parent chains and summing the
                // weight of every edge that is also on the best path. This catches duplicates of
                // the best path naturally (share == bestWeight) and gives the true share for
                // partial overlaps - unlike the prior heuristic which only considered the first
                // shared edge on each side and could miss heavy-weight shared edge in the middle.
                var shareWeight = 0.0
                run {
                    var e: SPTEntry = fromSPTEntry
                    while (e.parent != null) {
                        if (bestPathEdges.contains(e.edge))
                            shareWeight += e.getWeightOfVisitedPath() - e.parent!!.getWeightOfVisitedPath()
                        e = e.parent!!
                    }
                }
                run {
                    var e: SPTEntry = toSPTEntry
                    while (e.parent != null) {
                        if (bestPathEdges.contains(e.edge))
                            shareWeight += e.getWeightOfVisitedPath() - e.parent!!.getWeightOfVisitedPath()
                        e = e.parent!!
                    }
                }
                val smallShare = shareWeight / bestWeight < maxShareFactor
                if (smallShare) {
                    val altNames = getAltNames(graph, fromSPTEntry)

                    val sortBy = calcSortBy(weightInfluence, weight, shareInfluence, shareWeight, plateauInfluence, plateauWeight)
                    val worstSortBy = getWorstSortBy()

                    if (sortBy < worstSortBy || alternatives.size < maxPaths) {
                        val path = DefaultBidirPathExtractor.extractPath(graph, weighting, fromSPTEntry, toSPTEntry, weight)
                        alternatives.add(AlternativeInfo(sortBy, path, shareWeight, altNames))

                        Collections.sort(alternatives, ALT_COMPARATOR)
                        check(alternatives.get(0) === bestAlt) {
                            "best path should be always first entry " + bestAlt.path.getWeight() + " vs " + alternatives.get(0).path.getWeight()
                        }

                        if (alternatives.size > maxPaths)
                            alternatives.subList(maxPaths, alternatives.size).clear()
                    }
                }

                return true
            }

            /**
             * Return the current worst weight for all alternatives
             */
            fun getWorstSortBy(): Double {
                check(!alternatives.isEmpty()) { "Empty alternative list cannot happen" }
                return alternatives.get(alternatives.size - 1).sortBy
            }

            // returns true if fromSPTEntry is the root of the forward SPT (the start of the best path)
            fun isStartOfFwdSPT(fromSPTEntry: SPTEntry): Boolean {
                if (traversalMode.isEdgeBased) {
                    if (startEdgeOrNode == fromSPTEntry.edge) {
                        checkNotNull(fromSPTEntry.parent) { "best path entry must have a parent but was null: $fromSPTEntry" }
                        check(!(bestEntry.get() != null && bestEntry.get().edge != fromSPTEntry.edge)) {
                            "there can be only one best entry but was " + fromSPTEntry + " vs old: " + bestEntry.get() +
                                    " " + graph.getEdgeIteratorState(fromSPTEntry.edge, fromSPTEntry.adjNode)!!.fetchWayGeometry(FetchMode.ALL)
                        }
                        bestEntry.set(fromSPTEntry)
                        return true
                    }
                } else if (fromSPTEntry.parent == null) {
                    check(startEdgeOrNode == fromSPTEntry.adjNode) {
                        "Start node has to be identical to root edge entry " +
                                "which is the plateau start of the best path but was: " + startEdgeOrNode + " vs. adjNode: " + fromSPTEntry.adjNode
                    }
                    check(bestEntry.get() == null) {
                        "there can be only one best entry but was " + fromSPTEntry + " vs old: " + bestEntry.get() +
                                " " + graph.getEdgeIteratorState(fromSPTEntry.edge, fromSPTEntry.adjNode)!!.fetchWayGeometry(FetchMode.ALL)
                    }
                    bestEntry.set(fromSPTEntry)
                    return true
                }

                return false
            }
        })

        return alternatives
    }
}
