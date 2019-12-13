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
package com.graphhopper.matching;

import com.bmw.hmm.SequenceState;
import com.bmw.hmm.ViterbiAlgorithm;
import com.graphhopper.GraphHopper;
import com.graphhopper.matching.util.HmmProbabilities;
import com.graphhopper.matching.util.TimeStep;
import com.graphhopper.routing.*;
import com.graphhopper.routing.ch.PreparationWeighting;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.querygraph.VirtualEdgeIteratorState;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * This class matches real world GPX entries to the digital road network stored
 * in GraphHopper. The Viterbi algorithm is used to compute the most likely
 * sequence of map matching candidates. The Viterbi algorithm takes into account
 * the distance between GPX entries and map matching candidates as well as the
 * routing distances between consecutive map matching candidates.
 * <p>
 * <p>
 * See http://en.wikipedia.org/wiki/Map_matching and Newson, Paul, and John
 * Krumm. "Hidden Markov map matching through noise and sparseness." Proceedings
 * of the 17th ACM SIGSPATIAL International Conference on Advances in Geographic
 * Information Systems. ACM, 2009.
 *
 * @author Peter Karich
 * @author Michael Zilske
 * @author Stefan Holder
 * @author kodonnell
 */
public class MapMatching {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    // Penalty in m for each U-turn performed at the beginning or end of a path between two
    // subsequent candidates.
    private double uTurnDistancePenalty;

    private final Graph routingGraph;
    private final LocationIndexTree locationIndex;
    private double measurementErrorSigma = 50.0;
    private double transitionProbabilityBeta = 2.0;
    private final int maxVisitedNodes;
    private DistanceCalc distanceCalc = new DistancePlaneProjection();
    private final Weighting weighting;
    private final boolean ch;
    private QueryGraph queryGraph;

    public MapMatching(GraphHopper graphHopper, HintsMap hints) {
        this.locationIndex = (LocationIndexTree) graphHopper.getLocationIndex();

        if (!hints.has("vehicle")) hints.put("vehicle", "car");

        // Convert heading penalty [s] into U-turn penalty [m]
        // The heading penalty is automatically taken into account by GraphHopper routing,
        // for all links that we set to "unfavored" on the QueryGraph.
        // We use that mechanism to softly enforce a heading for each map-matching state.
        // We want to consistently use the same parameter for our own objective function (independent of the routing),
        // which has meters as unit, not seconds.

        final double PENALTY_CONVERSION_VELOCITY = 5;  // [m/s]
        final double headingTimePenalty = hints.getDouble(Parameters.Routing.HEADING_PENALTY, Parameters.Routing.DEFAULT_HEADING_PENALTY);
        uTurnDistancePenalty = headingTimePenalty * PENALTY_CONVERSION_VELOCITY;

        RoutingAlgorithmFactory routingAlgorithmFactory = graphHopper.getAlgorithmFactory(hints);
        if (routingAlgorithmFactory instanceof PrepareContractionHierarchies) {
            ch = true;
            routingGraph = graphHopper.getGraphHopperStorage().getCHGraph(((PrepareContractionHierarchies) routingAlgorithmFactory).getCHProfile());
        } else {
            ch = false;
            routingGraph = graphHopper.getGraphHopperStorage();
        }
        weighting = graphHopper.createWeighting(hints, graphHopper.getEncodingManager().getEncoder(hints.getVehicle()), null);
        this.maxVisitedNodes = hints.getInt(Parameters.Routing.MAX_VISITED_NODES, Integer.MAX_VALUE);
    }

    /**
     * Beta parameter of the exponential distribution for modeling transition
     * probabilities.
     */
    public void setTransitionProbabilityBeta(double transitionProbabilityBeta) {
        this.transitionProbabilityBeta = transitionProbabilityBeta;
    }

    /**
     * Standard deviation of the normal distribution [m] used for modeling the
     * GPS error.
     */
    public void setMeasurementErrorSigma(double measurementErrorSigma) {
        this.measurementErrorSigma = measurementErrorSigma;
    }

    /**
     * This method does the actual map matching.
     * <p>
     *
     * @param gpxList the input list with GPX points which should match to edges
     *                of the graph specified in the constructor
     */
    public MatchResult doWork(List<Observation> gpxList) {
        // filter the entries:
        List<Observation> filteredGPXEntries = filterGPXEntries(gpxList);

        // now find each of the entries in the graph:
        List<Collection<QueryResult>> queriesPerEntry = lookupGPXEntries(filteredGPXEntries, DefaultEdgeFilter.allEdges(weighting.getFlagEncoder()));

        // Add virtual nodes and edges to the graph so that candidates on edges can be represented
        // by virtual nodes.
        List<QueryResult> allQueryResults = new ArrayList<>();
        for (Collection<QueryResult> qrs : queriesPerEntry) {
            allQueryResults.addAll(qrs);
        }
        queryGraph = QueryGraph.lookup(routingGraph, allQueryResults);

        // Different QueryResults can have the same tower node as their closest node.
        // Hence, we now dedupe the query results of each GPX entry by their closest node (#91).
        // This must be done after calling queryGraph.lookup() since this replaces some of the
        // QueryResult nodes with virtual nodes. Virtual nodes are not deduped since there is at
        // most one QueryResult per edge and virtual nodes are inserted into the middle of an edge.
        // Reducing the number of QueryResults improves performance since less shortest/fastest
        // routes need to be computed.
        queriesPerEntry = deduplicateQueryResultsByClosestNode(queriesPerEntry);

        logger.debug("================= Query results =================");
        int i = 1;
        for (Collection<QueryResult> entries : queriesPerEntry) {
            logger.debug("Query results for GPX entry {}", i++);
            for (QueryResult qr : entries) {
                logger.debug("Node id: {}, virtual: {}, snapped on: {}, pos: {},{}, "
                                + "query distance: {}", qr.getClosestNode(),
                        queryGraph.isVirtualNode(qr.getClosestNode()), qr.getSnappedPosition(),
                        qr.getSnappedPoint().getLat(), qr.getSnappedPoint().getLon(),
                        qr.getQueryDistance());
            }
        }

        // Creates candidates from the QueryResults of all GPX entries (a candidate is basically a
        // QueryResult + direction).
        List<TimeStep<State, Observation, Path>> timeSteps =
                createTimeSteps(filteredGPXEntries, queriesPerEntry, queryGraph);
        logger.debug("=============== Time steps ===============");
        i = 1;
        for (TimeStep<State, Observation, Path> ts : timeSteps) {
            logger.debug("Candidates for time step {}", i++);
            for (State candidate : ts.candidates) {
                logger.debug(candidate.toString());
            }
        }

        // Compute the most likely sequence of map matching candidates:
        List<SequenceState<State, Observation, Path>> seq = computeViterbiSequence(timeSteps, gpxList.size(), queryGraph);

        logger.debug("=============== Viterbi results =============== ");
        i = 1;
        for (SequenceState<State, Observation, Path> ss : seq) {
            logger.debug("{}: {}, path: {}", i, ss.state,
                    ss.transitionDescriptor != null ? ss.transitionDescriptor.calcEdges() : null);
            i++;
        }

        final Map<String, EdgeIteratorState> virtualEdgesMap = createVirtualEdgesMap(queriesPerEntry);
        MatchResult matchResult = computeMatchResult(seq, virtualEdgesMap, gpxList, queryGraph);
        logger.debug("=============== Matched real edges =============== ");
        i = 1;
        for (EdgeMatch em : matchResult.getEdgeMatches()) {
            logger.debug("{}: {}", i, em.getEdgeState());
            i++;
        }
        return matchResult;
    }

    private EdgeExplorer createAllEdgeExplorer() {
        return queryGraph.createEdgeExplorer(DefaultEdgeFilter.allEdges(weighting.getFlagEncoder()));
    }

    /**
     * Filters GPX entries to only those which will be used for map matching (i.e. those which
     * are separated by at least 2 * measurementErrorSigman
     */
    private List<Observation> filterGPXEntries(List<Observation> gpxList) {
        List<Observation> filtered = new ArrayList<>();
        Observation prevEntry = null;
        int last = gpxList.size() - 1;
        for (int i = 0; i <= last; i++) {
            Observation gpxEntry = gpxList.get(i);
            if (i == 0 || i == last || distanceCalc.calcDist(
                    prevEntry.getPoint().getLat(), prevEntry.getPoint().getLon(),
                    gpxEntry.getPoint().getLat(), gpxEntry.getPoint().getLon()) > 2 * measurementErrorSigma) {
                filtered.add(gpxEntry);
                prevEntry = gpxEntry;
            } else {
                logger.debug("Filter out GPX entry: {}", i + 1);
            }
        }
        return filtered;
    }

    /**
     * Find the possible locations (edges) of each Observation in the graph.
     */
    private List<Collection<QueryResult>> lookupGPXEntries(List<Observation> gpxList,
                                                           EdgeFilter edgeFilter) {

        final List<Collection<QueryResult>> gpxEntryLocations = new ArrayList<>();
        for (Observation gpxEntry : gpxList) {
            final List<QueryResult> queryResults = locationIndex.findNClosest(
                    gpxEntry.getPoint().lat, gpxEntry.getPoint().lon, edgeFilter, measurementErrorSigma);
            gpxEntryLocations.add(queryResults);
        }
        return gpxEntryLocations;
    }

    private List<Collection<QueryResult>> deduplicateQueryResultsByClosestNode(
            List<Collection<QueryResult>> queriesPerEntry) {
        final List<Collection<QueryResult>> result = new ArrayList<>(queriesPerEntry.size());

        for (Collection<QueryResult> queryResults : queriesPerEntry) {
            final Map<Integer, QueryResult> dedupedQueryResults = new HashMap<>();
            for (QueryResult qr : queryResults) {
                dedupedQueryResults.put(qr.getClosestNode(), qr);
            }
            result.add(dedupedQueryResults.values());
        }
        return result;
    }

    /**
     * Creates TimeSteps with candidates for the GPX entries but does not create emission or
     * transition probabilities. Creates directed candidates for virtual nodes and undirected
     * candidates for real nodes.
     */
    private List<TimeStep<State, Observation, Path>> createTimeSteps(
            List<Observation> filteredGPXEntries, List<Collection<QueryResult>> queriesPerEntry,
            QueryGraph queryGraph) {
        final int n = filteredGPXEntries.size();
        if (queriesPerEntry.size() != n) {
            throw new IllegalArgumentException(
                    "filteredGPXEntries and queriesPerEntry must have same size.");
        }

        final List<TimeStep<State, Observation, Path>> timeSteps = new ArrayList<>();
        for (int i = 0; i < n; i++) {

            Observation gpxEntry = filteredGPXEntries.get(i);
            final Collection<QueryResult> queryResults = queriesPerEntry.get(i);

            List<State> candidates = new ArrayList<>();
            for (QueryResult qr : queryResults) {
                int closestNode = qr.getClosestNode();
                if (queryGraph.isVirtualNode(closestNode)) {
                    // get virtual edges:
                    List<VirtualEdgeIteratorState> virtualEdges = new ArrayList<>();
                    EdgeIterator iter = queryGraph.createEdgeExplorer().setBaseNode(closestNode);
                    while (iter.next()) {
                        if (!queryGraph.isVirtualEdge(iter.getEdge())) {
                            throw new RuntimeException("Virtual nodes must only have virtual edges "
                                    + "to adjacent nodes.");
                        }
                        virtualEdges.add((VirtualEdgeIteratorState)
                                queryGraph.getEdgeIteratorState(iter.getEdge(), iter.getAdjNode()));
                    }
                    if (virtualEdges.size() != 2) {
                        throw new RuntimeException("Each virtual node must have exactly 2 "
                                + "virtual edges (reverse virtual edges are not returned by the "
                                + "EdgeIterator");
                    }

                    // Create a directed candidate for each of the two possible directions through
                    // the virtual node. This is needed to penalize U-turns at virtual nodes
                    // (see also #51). We need to add candidates for both directions because
                    // we don't know yet which is the correct one. This will be figured
                    // out by the Viterbi algorithm.
                    //
                    // Adding further candidates to explicitly allow U-turns through setting
                    // incomingVirtualEdge==outgoingVirtualEdge doesn't make sense because this
                    // would actually allow to perform a U-turn without a penalty by going to and
                    // from the virtual node through the other virtual edge or its reverse edge.
                    VirtualEdgeIteratorState e1 = virtualEdges.get(0);
                    VirtualEdgeIteratorState e2 = virtualEdges.get(1);
                    for (int j = 0; j < 2; j++) {
                        // get favored/unfavored edges:
                        VirtualEdgeIteratorState incomingVirtualEdge = j == 0 ? e1 : e2;
                        VirtualEdgeIteratorState outgoingVirtualEdge = j == 0 ? e2 : e1;
                        // create candidate
                        QueryResult vqr = new QueryResult(qr.getQueryPoint().lat, qr.getQueryPoint().lon);
                        vqr.setQueryDistance(qr.getQueryDistance());
                        vqr.setClosestNode(qr.getClosestNode());
                        vqr.setWayIndex(qr.getWayIndex());
                        vqr.setSnappedPosition(qr.getSnappedPosition());
                        vqr.setClosestEdge(qr.getClosestEdge());
                        vqr.calcSnappedPoint(distanceCalc);
                        State candidate = new State(gpxEntry, vqr, incomingVirtualEdge,
                                outgoingVirtualEdge);
                        candidates.add(candidate);
                    }
                } else {
                    // Create an undirected candidate for the real node.
                    State candidate = new State(gpxEntry, qr);
                    candidates.add(candidate);
                }
            }

            final TimeStep<State, Observation, Path> timeStep = new TimeStep<>(gpxEntry, candidates);
            timeSteps.add(timeStep);
        }
        return timeSteps;
    }

    /**
     * Computes the most likely candidate sequence for the GPX entries.
     */
    private List<SequenceState<State, Observation, Path>> computeViterbiSequence(
            List<TimeStep<State, Observation, Path>> timeSteps, int originalGpxEntriesCount,
            QueryGraph queryGraph) {
        final HmmProbabilities probabilities
                = new HmmProbabilities(measurementErrorSigma, transitionProbabilityBeta);
        final ViterbiAlgorithm<State, Observation, Path> viterbi = new ViterbiAlgorithm<>();

        logger.debug("\n=============== Paths ===============");
        int timeStepCounter = 0;
        TimeStep<State, Observation, Path> prevTimeStep = null;
        int i = 1;
        for (TimeStep<State, Observation, Path> timeStep : timeSteps) {
            logger.debug("\nPaths to time step {}", i++);
            computeEmissionProbabilities(timeStep, probabilities);

            if (prevTimeStep == null) {
                viterbi.startWithInitialObservation(timeStep.observation, timeStep.candidates,
                        timeStep.emissionLogProbabilities);
            } else {
                computeTransitionProbabilities(prevTimeStep, timeStep, probabilities, queryGraph);
                viterbi.nextStep(timeStep.observation, timeStep.candidates,
                        timeStep.emissionLogProbabilities, timeStep.transitionLogProbabilities,
                        timeStep.roadPaths);
            }
            if (viterbi.isBroken()) {
                String likelyReasonStr = "";
                if (prevTimeStep != null) {
                    Observation prevGPXE = prevTimeStep.observation;
                    Observation gpxe = timeStep.observation;
                    double dist = distanceCalc.calcDist(prevGPXE.getPoint().lat, prevGPXE.getPoint().lon,
                            gpxe.getPoint().lat, gpxe.getPoint().lon);
                    if (dist > 2000) {
                        likelyReasonStr = "Too long distance to previous measurement? "
                                + Math.round(dist) + "m, ";
                    }
                }

                throw new IllegalArgumentException("Sequence is broken for submitted track at time step "
                        + timeStepCounter + " (" + originalGpxEntriesCount + " points). "
                        + likelyReasonStr + "observation:" + timeStep.observation + ", "
                        + timeStep.candidates.size() + " candidates: "
                        + getSnappedCandidates(timeStep.candidates)
                        + ". If a match is expected consider increasing max_visited_nodes.");
            }

            timeStepCounter++;
            prevTimeStep = timeStep;
        }

        return viterbi.computeMostLikelySequence();
    }

    private void computeEmissionProbabilities(TimeStep<State, Observation, Path> timeStep,
                                              HmmProbabilities probabilities) {
        for (State candidate : timeStep.candidates) {
            // road distance difference in meters
            final double distance = candidate.getQueryResult().getQueryDistance();
            timeStep.addEmissionLogProbability(candidate,
                    probabilities.emissionLogProbability(distance));
        }
    }

    private void computeTransitionProbabilities(TimeStep<State, Observation, Path> prevTimeStep,
                                                TimeStep<State, Observation, Path> timeStep,
                                                HmmProbabilities probabilities,
                                                QueryGraph queryGraph) {
        final double linearDistance = distanceCalc.calcDist(prevTimeStep.observation.getPoint().lat,
                prevTimeStep.observation.getPoint().lon, timeStep.observation.getPoint().lat, timeStep.observation.getPoint().lon);

        for (State from : prevTimeStep.candidates) {
            for (State to : timeStep.candidates) {
                // enforce heading if required:
                if (from.isOnDirectedEdge()) {
                    // Make sure that the path starting at the "from" candidate goes through
                    // the outgoing edge.
                    queryGraph.unfavorVirtualEdgePair(from.getQueryResult().getClosestNode(),
                            from.getIncomingVirtualEdge().getEdge());
                }
                if (to.isOnDirectedEdge()) {
                    // Make sure that the path ending at "to" candidate goes through
                    // the incoming edge.
                    queryGraph.unfavorVirtualEdgePair(to.getQueryResult().getClosestNode(),
                            to.getOutgoingVirtualEdge().getEdge());
                }

                RoutingAlgorithm router;
                if (ch) {
                    router = new DijkstraBidirectionCH(queryGraph, new PreparationWeighting(weighting)) {
                        @Override
                        protected void initCollections(int size) {
                            super.initCollections(50);
                        }
                    };
                    ((DijkstraBidirectionCH) router).setEdgeFilter(new LevelEdgeFilter((CHGraph) routingGraph));
                    router.setMaxVisitedNodes(maxVisitedNodes);
                } else {
                    router = new DijkstraBidirectionRef(queryGraph, weighting, TraversalMode.NODE_BASED) {
                        @Override
                        protected void initCollections(int size) {
                            super.initCollections(50);
                        }
                    };
                    router.setMaxVisitedNodes(maxVisitedNodes);
                }

                final Path path = router.calcPath(from.getQueryResult().getClosestNode(),
                        to.getQueryResult().getClosestNode());

                if (path.isFound()) {
                    timeStep.addRoadPath(from, to, path);

                    // The router considers unfavored virtual edges using edge penalties
                    // but this is not reflected in the path distance. Hence, we need to adjust the
                    // path distance accordingly.
                    final double penalizedPathDistance = penalizedPathDistance(path,
                            queryGraph.getUnfavoredVirtualEdges());

                    logger.debug("Path from: {}, to: {}, penalized path length: {}",
                            from, to, penalizedPathDistance);

                    final double transitionLogProbability = probabilities
                            .transitionLogProbability(penalizedPathDistance, linearDistance);
                    timeStep.addTransitionLogProbability(from, to, transitionLogProbability);
                } else {
                    logger.debug("No path found for from: {}, to: {}", from, to);
                }
                queryGraph.clearUnfavoredStatus();

            }
        }
    }

    /**
     * Returns the path length plus a penalty if the starting/ending edge is unfavored.
     */
    private double penalizedPathDistance(Path path,
                                         Set<EdgeIteratorState> penalizedVirtualEdges) {
        double totalPenalty = 0;

        // Unfavored edges in the middle of the path should not be penalized because we are
        // only concerned about the direction at the start/end.
        final List<EdgeIteratorState> edges = path.calcEdges();
        if (!edges.isEmpty()) {
            if (penalizedVirtualEdges.contains(edges.get(0))) {
                totalPenalty += uTurnDistancePenalty;
            }
        }
        if (edges.size() > 1) {
            if (penalizedVirtualEdges.contains(edges.get(edges.size() - 1))) {
                totalPenalty += uTurnDistancePenalty;
            }
        }
        return path.getDistance() + totalPenalty;
    }

    private MatchResult computeMatchResult(List<SequenceState<State, Observation, Path>> seq,
                                           Map<String, EdgeIteratorState> virtualEdgesMap, List<Observation> gpxList, QueryGraph queryGraph) {
        double distance = 0.0;
        long time = 0;
        for (SequenceState<State, Observation, Path> transitionAndState : seq) {
            if (transitionAndState.transitionDescriptor != null) {
                distance += transitionAndState.transitionDescriptor.getDistance();
                time += transitionAndState.transitionDescriptor.getTime();
            }
        }

        List<EdgeIteratorState> edges = new ArrayList<>();
        for (SequenceState<State, Observation, Path> state : seq) {
            if (state.transitionDescriptor != null) {
                edges.addAll(state.transitionDescriptor.calcEdges());
            }
        }
        Path mergedPath = new MapMatchedPath(queryGraph.getBaseGraph(), weighting, edges);

        List<EdgeMatch> edgeMatches = computeEdgeMatches(seq, virtualEdgesMap);
        MatchResult matchResult = new MatchResult(edgeMatches);
        matchResult.setMergedPath(mergedPath);
        matchResult.setMatchMillis(time);
        matchResult.setMatchLength(distance);
        matchResult.setGPXEntriesLength(gpxLength(gpxList));
        matchResult.setGraph(queryGraph.getBaseGraph());
        matchResult.setWeighting(weighting);
        return matchResult;
    }

    private List<EdgeMatch> computeEdgeMatches(List<SequenceState<State, Observation, Path>> seq, Map<String, EdgeIteratorState> virtualEdgesMap) {
        // This creates a list of directed edges (EdgeIteratorState instances turned the right way),
        // each associated with 0 or more of the observations.
        // These directed edges are edges of the real street graph, where nodes are intersections.
        // So in _this_ representation, the path that you get when you just look at the edges goes from
        // an intersection to an intersection.

        // Implementation note: We have to look at both states _and_ transitions, since we can have e.g. just one state,
        // or two states with a transition that is an empty path (observations snapped to the same node in the query graph),
        // but these states still happen on an edge, and for this representation, we want to have that edge.
        // (Whereas in the PathWrapper representation, we would just see an empty path.)

        // Note that the result can be empty, even when the input is not. Observations can be on nodes as well as on
        // edges, and when all observations are on the same node, we get no edge at all.
        // But apart from that corner case, all observations that go in here are also in the result.

        // (Consider totally forbidding candidate states to be snapped to a point, and make them all be on directed
        // edges, then that corner case goes away.)
        List<EdgeMatch> edgeMatches = new ArrayList<>();
        List<State> states = new ArrayList<>();
        EdgeIteratorState currentDirectedRealEdge = null;
        for (SequenceState<State, Observation, Path> transitionAndState : seq) {
            // transition (except before the first state)
            if (transitionAndState.transitionDescriptor != null) {
                for (EdgeIteratorState edge : transitionAndState.transitionDescriptor.calcEdges()) {
                    EdgeIteratorState newDirectedRealEdge = resolveToRealEdge(virtualEdgesMap, edge);
                    if (currentDirectedRealEdge != null) {
                        if (!equalEdges(currentDirectedRealEdge, newDirectedRealEdge)) {
                            EdgeMatch edgeMatch = new EdgeMatch(currentDirectedRealEdge, states);
                            edgeMatches.add(edgeMatch);
                            states = new ArrayList<>();
                        }
                    }
                    currentDirectedRealEdge = newDirectedRealEdge;
                }
            }
            // state
            if (transitionAndState.state.isOnDirectedEdge()) { // as opposed to on a node
                EdgeIteratorState newDirectedRealEdge = resolveToRealEdge(virtualEdgesMap, transitionAndState.state.getOutgoingVirtualEdge());
                if (currentDirectedRealEdge != null) {
                    if (!equalEdges(currentDirectedRealEdge, newDirectedRealEdge)) {
                        EdgeMatch edgeMatch = new EdgeMatch(currentDirectedRealEdge, states);
                        edgeMatches.add(edgeMatch);
                        states = new ArrayList<>();
                    }
                }
                currentDirectedRealEdge = newDirectedRealEdge;
            }
            states.add(transitionAndState.state);
        }
        if (currentDirectedRealEdge != null) {
            EdgeMatch edgeMatch = new EdgeMatch(currentDirectedRealEdge, states);
            edgeMatches.add(edgeMatch);
        }
        return edgeMatches;
    }

    private double gpxLength(List<Observation> gpxList) {
        if (gpxList.isEmpty()) {
            return 0;
        } else {
            double gpxLength = 0;
            Observation prevEntry = gpxList.get(0);
            for (int i = 1; i < gpxList.size(); i++) {
                Observation entry = gpxList.get(i);
                gpxLength += distanceCalc.calcDist(prevEntry.getPoint().lat, prevEntry.getPoint().lon, entry.getPoint().lat, entry.getPoint().lon);
                prevEntry = entry;
            }
            return gpxLength;
        }
    }

    private boolean equalEdges(EdgeIteratorState edge1, EdgeIteratorState edge2) {
        return edge1.getEdge() == edge2.getEdge()
                && edge1.getBaseNode() == edge2.getBaseNode()
                && edge1.getAdjNode() == edge2.getAdjNode();
    }

    private EdgeIteratorState resolveToRealEdge(Map<String, EdgeIteratorState> virtualEdgesMap,
                                                EdgeIteratorState edgeIteratorState) {
        if (queryGraph.isVirtualNode(edgeIteratorState.getBaseNode())
                || queryGraph.isVirtualNode(edgeIteratorState.getAdjNode())) {
            return virtualEdgesMap.get(virtualEdgesMapKey(edgeIteratorState));
        } else {
            return edgeIteratorState;
        }
    }

    /**
     * Returns a map where every virtual edge maps to its real edge with correct orientation.
     */
    private Map<String, EdgeIteratorState> createVirtualEdgesMap(List<Collection<QueryResult>> queriesPerEntry) {
        EdgeExplorer explorer = createAllEdgeExplorer();
        // TODO For map key, use the traversal key instead of string!
        Map<String, EdgeIteratorState> virtualEdgesMap = new HashMap<>();
        for (Collection<QueryResult> queryResults : queriesPerEntry) {
            for (QueryResult qr : queryResults) {
                if (queryGraph.isVirtualNode(qr.getClosestNode())) {
                    EdgeIterator iter = explorer.setBaseNode(qr.getClosestNode());
                    while (iter.next()) {
                        int node = traverseToClosestRealAdj(iter);
                        if (node == qr.getClosestEdge().getAdjNode()) {
                            virtualEdgesMap.put(virtualEdgesMapKey(iter),
                                    qr.getClosestEdge().detach(false));
                            virtualEdgesMap.put(reverseVirtualEdgesMapKey(iter),
                                    qr.getClosestEdge().detach(true));
                        } else if (node == qr.getClosestEdge().getBaseNode()) {
                            virtualEdgesMap.put(virtualEdgesMapKey(iter),
                                    qr.getClosestEdge().detach(true));
                            virtualEdgesMap.put(reverseVirtualEdgesMapKey(iter),
                                    qr.getClosestEdge().detach(false));
                        } else {
                            throw new RuntimeException();
                        }
                    }
                }
            }
        }
        return virtualEdgesMap;
    }

    private String virtualEdgesMapKey(EdgeIteratorState iter) {
        return iter.getBaseNode() + "-" + iter.getEdge() + "-" + iter.getAdjNode();
    }

    private String reverseVirtualEdgesMapKey(EdgeIteratorState iter) {
        return iter.getAdjNode() + "-" + iter.getEdge() + "-" + iter.getBaseNode();
    }

    private int traverseToClosestRealAdj(EdgeIteratorState edge) {
        if (!queryGraph.isVirtualNode(edge.getAdjNode())) {
            return edge.getAdjNode();
        }
        EdgeExplorer explorer = createAllEdgeExplorer();
        EdgeIterator iter = explorer.setBaseNode(edge.getAdjNode());
        while (iter.next()) {
            if (iter.getAdjNode() != edge.getBaseNode()) {
                return traverseToClosestRealAdj(iter);
            }
        }
        throw new IllegalStateException("Cannot find adjacent edge " + edge);
    }

    private String getSnappedCandidates(Collection<State> candidates) {
        String str = "";
        for (State gpxe : candidates) {
            if (!str.isEmpty()) {
                str += ", ";
            }
            str += "distance: " + gpxe.getQueryResult().getQueryDistance() + " to "
                    + gpxe.getQueryResult().getSnappedPoint();
        }
        return "[" + str + "]";
    }

    public Graph getQueryGraph() {
        return queryGraph;
    }

    private static class MapMatchedPath extends Path {
        MapMatchedPath(Graph graph, Weighting weighting, List<EdgeIteratorState> edges) {
            super(graph);
            int prevEdge = EdgeIterator.NO_EDGE;
            for (EdgeIteratorState edge : edges) {
                addDistance(edge.getDistance());
                addTime(weighting.calcMillis(edge, false, prevEdge));
                addEdge(edge.getEdge());
                prevEdge = edge.getEdge();
            }
            if (edges.isEmpty()) {
                setFound(false);
            } else {
                setFromNode(edges.get(0).getBaseNode());
                setFound(true);
            }
        }
    }

}