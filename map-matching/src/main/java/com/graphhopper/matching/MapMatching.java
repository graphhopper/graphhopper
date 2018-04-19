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
import com.graphhopper.routing.ch.CHAlgoFactoryDecorator;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.Map.Entry;

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

    private final Graph graph;
    private final Graph routingGraph;
    private final LocationIndexMatch locationIndex;
    private double measurementErrorSigma = 50.0;
    private double transitionProbabilityBeta = 2.0;
    private final int nodeCount;
    private DistanceCalc distanceCalc = new DistancePlaneProjection();
    private final RoutingAlgorithmFactory algoFactory;
    private final AlgorithmOptions algoOptions;

    public MapMatching(GraphHopper hopper, AlgorithmOptions algoOptions) {
        // Convert heading penalty [s] into U-turn penalty [m]
        final double PENALTY_CONVERSION_VELOCITY = 5;  // [m/s]
        final double headingTimePenalty = algoOptions.getHints().getDouble(
                Parameters.Routing.HEADING_PENALTY, Parameters.Routing.DEFAULT_HEADING_PENALTY);
        uTurnDistancePenalty = headingTimePenalty * PENALTY_CONVERSION_VELOCITY;

        this.locationIndex = new LocationIndexMatch(hopper.getGraphHopperStorage(),
                (LocationIndexTree) hopper.getLocationIndex());

        // create hints from algoOptions, so we can create the algorithm factory        
        HintsMap hints = new HintsMap();
        for (Entry<String, String> entry : algoOptions.getHints().toMap().entrySet()) {
            hints.put(entry.getKey(), entry.getValue());
        }

        // default is non-CH
        if (!hints.has(Parameters.CH.DISABLE)) {
            hints.put(Parameters.CH.DISABLE, true);

            if (!hopper.getCHFactoryDecorator().isDisablingAllowed())
                throw new IllegalArgumentException("Cannot disable CH. Not allowed on server side");
        }

        // TODO ugly workaround, duplicate data: hints can have 'vehicle' but algoOptions.weighting too!?
        // Similar problem in GraphHopper class
        String vehicle = hints.getVehicle();
        if (vehicle.isEmpty()) {
            if (algoOptions.hasWeighting()) {
                vehicle = algoOptions.getWeighting().getFlagEncoder().toString();
            } else {
                vehicle = hopper.getEncodingManager().fetchEdgeEncoders().get(0).toString();
            }
            hints.setVehicle(vehicle);
        }

        if (!hopper.getEncodingManager().supports(vehicle)) {
            throw new IllegalArgumentException("Vehicle " + vehicle + " unsupported. "
                    + "Supported are: " + hopper.getEncodingManager());
        }

        algoFactory = hopper.getAlgorithmFactory(hints);

        Weighting weighting;
        CHAlgoFactoryDecorator chFactoryDecorator = hopper.getCHFactoryDecorator();
        boolean forceFlexibleMode = hints.getBool(Parameters.CH.DISABLE, false);
        if (chFactoryDecorator.isEnabled() && !forceFlexibleMode) {
            if (!(algoFactory instanceof PrepareContractionHierarchies)) {
                throw new IllegalStateException("Although CH was enabled a non-CH algorithm "
                        + "factory was returned " + algoFactory);
            }

            weighting = ((PrepareContractionHierarchies) algoFactory).getWeighting();
            this.routingGraph = hopper.getGraphHopperStorage().getGraph(CHGraph.class, weighting);
        } else {
            weighting = algoOptions.hasWeighting()
                    ? algoOptions.getWeighting()
                    : new FastestWeighting(hopper.getEncodingManager().getEncoder(vehicle),
                    algoOptions.getHints());
            this.routingGraph = hopper.getGraphHopperStorage();
        }

        this.graph = hopper.getGraphHopperStorage();
        this.algoOptions = AlgorithmOptions.start(algoOptions).weighting(weighting).build();
        this.nodeCount = routingGraph.getNodes();
    }

    public void setDistanceCalc(DistanceCalc distanceCalc) {
        this.distanceCalc = distanceCalc;
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
    public MatchResult doWork(List<GPXEntry> gpxList) {
        if (gpxList.size() < 2) {
            throw new IllegalArgumentException("Too few coordinates in input file ("
                    + gpxList.size() + "). Correct format?");
        }

        // filter the entries:
        List<GPXEntry> filteredGPXEntries = filterGPXEntries(gpxList);
        if (filteredGPXEntries.size() < 2) {
            throw new IllegalStateException("Only " + filteredGPXEntries.size()
                    + " filtered GPX entries (from " + gpxList.size()
                    + "), but two or more are needed");
        }

        // now find each of the entries in the graph:
        final EdgeFilter edgeFilter = new DefaultEdgeFilter(algoOptions.getWeighting().getFlagEncoder());

        List<Collection<QueryResult>> queriesPerEntry =
                lookupGPXEntries(filteredGPXEntries, edgeFilter);

        // Add virtual nodes and edges to the graph so that candidates on edges can be represented
        // by virtual nodes.
        final QueryGraph queryGraph = new QueryGraph(routingGraph).setUseEdgeExplorerCache(true);
        List<QueryResult> allQueryResults = new ArrayList<>();
        for (Collection<QueryResult> qrs : queriesPerEntry)
            allQueryResults.addAll(qrs);
        queryGraph.lookup(allQueryResults);

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
                        isVirtualNode(qr.getClosestNode()), qr.getSnappedPosition(),
                        qr.getSnappedPoint().getLat(), qr.getSnappedPoint().getLon(),
                        qr.getQueryDistance());
            }
        }

        // Creates candidates from the QueryResults of all GPX entries (a candidate is basically a
        // QueryResult + direction).
        List<TimeStep<GPXExtension, GPXEntry, Path>> timeSteps =
                createTimeSteps(filteredGPXEntries, queriesPerEntry, queryGraph);
        logger.debug("=============== Time steps ===============");
        i = 1;
        for (TimeStep<GPXExtension, GPXEntry, Path> ts : timeSteps) {
            logger.debug("Candidates for time step {}", i++);
            for (GPXExtension candidate : ts.candidates) {
                logger.debug(candidate.toString());
            }
        }

        // Compute the most likely sequence of map matching candidates:
        List<SequenceState<GPXExtension, GPXEntry, Path>> seq = computeViterbiSequence(timeSteps,
                gpxList.size(), queryGraph);

        logger.debug("=============== Viterbi results =============== ");
        i = 1;
        for (SequenceState<GPXExtension, GPXEntry, Path> ss : seq) {
            logger.debug("{}: {}, path: {}", i, ss.state,
                    ss.transitionDescriptor != null ? ss.transitionDescriptor.calcEdges() : null);
            i++;
        }

        // finally, extract the result:
        final EdgeExplorer explorer = queryGraph.createEdgeExplorer(edgeFilter);

        // Needs original gpxList to compute stats.
        MatchResult matchResult = computeMatchResult(seq, gpxList, queriesPerEntry, explorer);

        logger.debug("=============== Matched real edges =============== ");
        i = 1;
        for (EdgeMatch em : matchResult.getEdgeMatches()) {
            logger.debug("{}: {}", i, em.getEdgeState());
            i++;
        }

        return matchResult;
    }

    /**
     * Filters GPX entries to only those which will be used for map matching (i.e. those which
     * are separated by at least 2 * measurementErrorSigman
     */
    private List<GPXEntry> filterGPXEntries(List<GPXEntry> gpxList) {
        List<GPXEntry> filtered = new ArrayList<>();
        GPXEntry prevEntry = null;
        int last = gpxList.size() - 1;
        for (int i = 0; i <= last; i++) {
            GPXEntry gpxEntry = gpxList.get(i);
            if (i == 0 || i == last || distanceCalc.calcDist(
                    prevEntry.getLat(), prevEntry.getLon(),
                    gpxEntry.getLat(), gpxEntry.getLon()) > 2 * measurementErrorSigma) {
                filtered.add(gpxEntry);
                prevEntry = gpxEntry;
            } else {
                logger.debug("Filter out GPX entry: {}", i + 1);
            }
        }
        return filtered;
    }

    /**
     * Find the possible locations (edges) of each GPXEntry in the graph.
     */
    private List<Collection<QueryResult>> lookupGPXEntries(List<GPXEntry> gpxList,
                                                           EdgeFilter edgeFilter) {

        final List<Collection<QueryResult>> gpxEntryLocations = new ArrayList<>();
        for (GPXEntry gpxEntry : gpxList) {
            final List<QueryResult> queryResults = locationIndex.findNClosest(
                    gpxEntry.lat, gpxEntry.lon, edgeFilter, measurementErrorSigma);
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
    private List<TimeStep<GPXExtension, GPXEntry, Path>> createTimeSteps(
            List<GPXEntry> filteredGPXEntries, List<Collection<QueryResult>> queriesPerEntry,
            QueryGraph queryGraph) {
        final int n = filteredGPXEntries.size();
        if (queriesPerEntry.size() != n) {
            throw new IllegalArgumentException(
                    "filteredGPXEntries and queriesPerEntry must have same size.");
        }

        final List<TimeStep<GPXExtension, GPXEntry, Path>> timeSteps = new ArrayList<>();
        for (int i = 0; i < n; i++) {

            GPXEntry gpxEntry = filteredGPXEntries.get(i);
            final Collection<QueryResult> queryResults = queriesPerEntry.get(i);

            List<GPXExtension> candidates = new ArrayList<>();
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
                        GPXExtension candidate = new GPXExtension(gpxEntry, vqr, incomingVirtualEdge,
                                outgoingVirtualEdge);
                        candidates.add(candidate);
                    }
                } else {
                    // Create an undirected candidate for the real node.
                    GPXExtension candidate = new GPXExtension(gpxEntry, qr);
                    candidates.add(candidate);
                }
            }

            final TimeStep<GPXExtension, GPXEntry, Path> timeStep = new TimeStep<>(gpxEntry, candidates);
            timeSteps.add(timeStep);
        }
        return timeSteps;
    }

    /**
     * Computes the most likely candidate sequence for the GPX entries.
     */
    private List<SequenceState<GPXExtension, GPXEntry, Path>> computeViterbiSequence(
            List<TimeStep<GPXExtension, GPXEntry, Path>> timeSteps, int originalGpxEntriesCount,
            QueryGraph queryGraph) {
        final HmmProbabilities probabilities
                = new HmmProbabilities(measurementErrorSigma, transitionProbabilityBeta);
        final ViterbiAlgorithm<GPXExtension, GPXEntry, Path> viterbi = new ViterbiAlgorithm<>();

        logger.debug("\n=============== Paths ===============");
        int timeStepCounter = 0;
        TimeStep<GPXExtension, GPXEntry, Path> prevTimeStep = null;
        int i = 1;
        for (TimeStep<GPXExtension, GPXEntry, Path> timeStep : timeSteps) {
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
                    GPXEntry prevGPXE = prevTimeStep.observation;
                    GPXEntry gpxe = timeStep.observation;
                    double dist = distanceCalc.calcDist(prevGPXE.lat, prevGPXE.lon,
                            gpxe.lat, gpxe.lon);
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

    private void computeEmissionProbabilities(TimeStep<GPXExtension, GPXEntry, Path> timeStep,
                                              HmmProbabilities probabilities) {
        for (GPXExtension candidate : timeStep.candidates) {
            // road distance difference in meters
            final double distance = candidate.getQueryResult().getQueryDistance();
            timeStep.addEmissionLogProbability(candidate,
                    probabilities.emissionLogProbability(distance));
        }
    }

    private void computeTransitionProbabilities(TimeStep<GPXExtension, GPXEntry, Path> prevTimeStep,
                                                TimeStep<GPXExtension, GPXEntry, Path> timeStep,
                                                HmmProbabilities probabilities,
                                                QueryGraph queryGraph) {
        final double linearDistance = distanceCalc.calcDist(prevTimeStep.observation.lat,
                prevTimeStep.observation.lon, timeStep.observation.lat, timeStep.observation.lon);

        // time difference in seconds
        final double timeDiff
                = (timeStep.observation.getTime() - prevTimeStep.observation.getTime()) / 1000.0;
        logger.debug("Time difference: {} s", timeDiff);

        for (GPXExtension from : prevTimeStep.candidates) {
            for (GPXExtension to : timeStep.candidates) {
                // enforce heading if required:
                if (from.isDirected()) {
                    // Make sure that the path starting at the "from" candidate goes through
                    // the outgoing edge.
                    queryGraph.unfavorVirtualEdgePair(from.getQueryResult().getClosestNode(),
                            from.getIncomingVirtualEdge().getEdge());
                }
                if (to.isDirected()) {
                    // Make sure that the path ending at "to" candidate goes through
                    // the incoming edge.
                    queryGraph.unfavorVirtualEdgePair(to.getQueryResult().getClosestNode(),
                            to.getOutgoingVirtualEdge().getEdge());
                }

                // Need to create a new routing algorithm for every routing.
                RoutingAlgorithm algo = algoFactory.createAlgo(queryGraph, algoOptions);

                final Path path = algo.calcPath(from.getQueryResult().getClosestNode(),
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

    private MatchResult computeMatchResult(List<SequenceState<GPXExtension, GPXEntry, Path>> seq,
                                           List<GPXEntry> gpxList,
                                           List<Collection<QueryResult>> queriesPerEntry,
                                           EdgeExplorer explorer) {
        final Map<String, EdgeIteratorState> virtualEdgesMap = createVirtualEdgesMap(
                queriesPerEntry, explorer);
        MatchResult matchResult = computeMatchedEdges(seq, virtualEdgesMap);
        computeGpxStats(gpxList, matchResult);

        return matchResult;
    }

    private MatchResult computeMatchedEdges(List<SequenceState<GPXExtension, GPXEntry, Path>> seq,
                                            Map<String, EdgeIteratorState> virtualEdgesMap) {
        List<EdgeMatch> edgeMatches = new ArrayList<>();
        double distance = 0.0;
        long time = 0;
        EdgeIteratorState currentEdge = null;
        List<GPXExtension> gpxExtensions = new ArrayList<>();
        GPXExtension queryResult = seq.get(0).state;
        gpxExtensions.add(queryResult);
        for (int j = 1; j < seq.size(); j++) {
            queryResult = seq.get(j).state;
            Path path = seq.get(j).transitionDescriptor;
            distance += path.getDistance();
            time += path.getTime();
            for (EdgeIteratorState edgeIteratorState : path.calcEdges()) {
                EdgeIteratorState directedRealEdge = resolveToRealEdge(virtualEdgesMap,
                        edgeIteratorState);
                if (directedRealEdge == null) {
                    throw new RuntimeException("Did not find real edge for "
                            + edgeIteratorState.getEdge());
                }
                if (currentEdge == null || !equalEdges(directedRealEdge, currentEdge)) {
                    EdgeMatch edgeMatch = new EdgeMatch(directedRealEdge, gpxExtensions);
                    edgeMatches.add(edgeMatch);
                    gpxExtensions = new ArrayList<>();
                    
                    currentEdge = directedRealEdge;
                }
            }
            gpxExtensions.add(queryResult);
        }
        if (edgeMatches.isEmpty()) {
            throw new IllegalArgumentException("No edge matches found for submitted track. Too short? Sequence size " + seq.size());
        }
        EdgeMatch lastEdgeMatch = edgeMatches.get(edgeMatches.size() - 1);
        if (!gpxExtensions.isEmpty() && !equalEdges(currentEdge, lastEdgeMatch.getEdgeState())) {
            edgeMatches.add(new EdgeMatch(currentEdge, gpxExtensions));
        } else {
            lastEdgeMatch.getGpxExtensions().addAll(gpxExtensions);
        }
        MatchResult matchResult = new MatchResult(edgeMatches);
        matchResult.setMatchMillis(time);
        matchResult.setMatchLength(distance);
        return matchResult;
    }

    /**
     * Calculate GPX stats to determine quality of matching.
     */
    private void computeGpxStats(List<GPXEntry> gpxList, MatchResult matchResult) {
        double gpxLength = 0;
        GPXEntry prevEntry = gpxList.get(0);
        for (int i = 1; i < gpxList.size(); i++) {
            GPXEntry entry = gpxList.get(i);
            gpxLength += distanceCalc.calcDist(prevEntry.lat, prevEntry.lon, entry.lat, entry.lon);
            prevEntry = entry;
        }

        long gpxMillis = gpxList.get(gpxList.size() - 1).getTime() - gpxList.get(0).getTime();
        matchResult.setGPXEntriesMillis(gpxMillis);
        matchResult.setGPXEntriesLength(gpxLength);
    }

    private boolean equalEdges(EdgeIteratorState edge1, EdgeIteratorState edge2) {
        return edge1.getEdge() == edge2.getEdge()
                && edge1.getBaseNode() == edge2.getBaseNode()
                && edge1.getAdjNode() == edge2.getAdjNode();
    }

    private EdgeIteratorState resolveToRealEdge(Map<String, EdgeIteratorState> virtualEdgesMap,
                                                EdgeIteratorState edgeIteratorState) {
        if (isVirtualNode(edgeIteratorState.getBaseNode())
                || isVirtualNode(edgeIteratorState.getAdjNode())) {
            return virtualEdgesMap.get(virtualEdgesMapKey(edgeIteratorState));
        } else {
            return edgeIteratorState;
        }
    }

    private boolean isVirtualNode(int node) {
        return node >= nodeCount;
    }

    /**
     * Returns a map where every virtual edge maps to its real edge with correct orientation.
     */
    private Map<String, EdgeIteratorState> createVirtualEdgesMap(
            List<Collection<QueryResult>> queriesPerEntry, EdgeExplorer explorer) {
        // TODO For map key, use the traversal key instead of string!
        Map<String, EdgeIteratorState> virtualEdgesMap = new HashMap<>();
        for (Collection<QueryResult> queryResults : queriesPerEntry) {
            for (QueryResult qr : queryResults) {
                if (isVirtualNode(qr.getClosestNode())) {
                    EdgeIterator iter = explorer.setBaseNode(qr.getClosestNode());
                    while (iter.next()) {
                        int node = traverseToClosestRealAdj(explorer, iter);
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

    private int traverseToClosestRealAdj(EdgeExplorer explorer, EdgeIteratorState edge) {
        if (!isVirtualNode(edge.getAdjNode())) {
            return edge.getAdjNode();
        }

        EdgeIterator iter = explorer.setBaseNode(edge.getAdjNode());
        while (iter.next()) {
            if (iter.getAdjNode() != edge.getBaseNode()) {
                return traverseToClosestRealAdj(explorer, iter);
            }
        }
        throw new IllegalStateException("Cannot find adjacent edge " + edge);
    }

    private String getSnappedCandidates(Collection<GPXExtension> candidates) {
        String str = "";
        for (GPXExtension gpxe : candidates) {
            if (!str.isEmpty()) {
                str += ", ";
            }
            str += "distance: " + gpxe.getQueryResult().getQueryDistance() + " to "
                    + gpxe.getQueryResult().getSnappedPoint();
        }
        return "[" + str + "]";
    }

    private void printMinDistances(List<TimeStep<GPXExtension, GPXEntry, Path>> timeSteps) {
        TimeStep<GPXExtension, GPXEntry, Path> prevStep = null;
        int index = 0;
        for (TimeStep<GPXExtension, GPXEntry, Path> ts : timeSteps) {
            if (prevStep != null) {
                double dist = distanceCalc.calcDist(
                        prevStep.observation.lat, prevStep.observation.lon,
                        ts.observation.lat, ts.observation.lon);
                double minCand = Double.POSITIVE_INFINITY;
                for (GPXExtension prevGPXE : prevStep.candidates) {
                    for (GPXExtension gpxe : ts.candidates) {
                        GHPoint psp = prevGPXE.getQueryResult().getSnappedPoint();
                        GHPoint sp = gpxe.getQueryResult().getSnappedPoint();
                        double tmpDist = distanceCalc.calcDist(psp.lat, psp.lon, sp.lat, sp.lon);
                        if (tmpDist < minCand) {
                            minCand = tmpDist;
                        }
                    }
                }
                logger.debug(index + ": " + Math.round(dist) + "m, minimum candidate: "
                        + Math.round(minCand) + "m");
                index++;
            }

            prevStep = ts;
        }
    }

    private static class MapMatchedPath extends Path {

        public MapMatchedPath(Graph graph, Weighting weighting) {
            super(graph, weighting);
        }

        @Override
        public Path setFromNode(int from) {
            return super.setFromNode(from);
        }

        @Override
        public void processEdge(int edgeId, int adjNode, int prevEdgeId) {
            super.processEdge(edgeId, adjNode, prevEdgeId);
        }
    }

    public Path calcPath(MatchResult mr) {
        MapMatchedPath p = new MapMatchedPath(graph, algoOptions.getWeighting());
        if (!mr.getEdgeMatches().isEmpty()) {
            int prevEdge = EdgeIterator.NO_EDGE;
            p.setFromNode(mr.getEdgeMatches().get(0).getEdgeState().getBaseNode());
            for (EdgeMatch em : mr.getEdgeMatches()) {
                p.processEdge(em.getEdgeState().getEdge(), em.getEdgeState().getAdjNode(), prevEdge);
                prevEdge = em.getEdgeState().getEdge();
            }

            p.setFound(true);

            return p;
        } else {
            return p;
        }
    }
}