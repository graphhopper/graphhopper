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

import com.carrotsearch.hppc.IntHashSet;
import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.AStarBidirection;
import com.graphhopper.routing.DijkstraBidirectionRef;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.lm.LMApproximator;
import com.graphhopper.routing.lm.LandmarkStorage;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.querygraph.VirtualEdgeIteratorState;
import com.graphhopper.routing.util.DefaultSnapFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;
import org.locationtech.jts.geom.Envelope;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.graphhopper.util.DistancePlaneProjection.DIST_PLANE;

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
    private final BaseGraph graph;
    private final Router router;
    private final LocationIndexTree locationIndex;
    private double measurementErrorSigma = 50.0;
    private double transitionProbabilityBeta = 2.0;
    private final DistanceCalc distanceCalc = new DistancePlaneProjection();
    private QueryGraph queryGraph;

    private Map<String, Object> statistics = new HashMap<>();

    public static MapMatching fromGraphHopper(GraphHopper graphHopper, PMap hints) {
        Router router = routerFromGraphHopper(graphHopper, hints);
        return new MapMatching(graphHopper.getBaseGraph(), (LocationIndexTree) graphHopper.getLocationIndex(), router);
    }

    public static Router routerFromGraphHopper(GraphHopper graphHopper, PMap hints) {
        if (hints.has("vehicle"))
            throw new IllegalArgumentException("MapMatching hints may no longer contain a vehicle, use the profile parameter instead, see core/#1958");
        if (hints.has("weighting"))
            throw new IllegalArgumentException("MapMatching hints may no longer contain a weighting, use the profile parameter instead, see core/#1958");

        if (graphHopper.getProfiles().isEmpty()) {
            throw new IllegalArgumentException("No profiles found, you need to configure at least one profile to use map matching");
        }
        if (!hints.has("profile")) {
            throw new IllegalArgumentException("You need to specify a profile to perform map matching");
        }
        String profileStr = hints.getString("profile", "");
        Profile profile = graphHopper.getProfile(profileStr);
        if (profile == null) {
            List<Profile> profiles = graphHopper.getProfiles();
            List<String> profileNames = new ArrayList<>(profiles.size());
            for (Profile p : profiles) {
                profileNames.add(p.getName());
            }
            throw new IllegalArgumentException("Could not find profile '" + profileStr + "', choose one of: " + profileNames);
        }

        boolean disableLM = hints.getBool(Parameters.Landmark.DISABLE, false);
        boolean disableCH = hints.getBool(Parameters.CH.DISABLE, false);

        // see map-matching/#177: both ch.disable and lm.disable can be used to force Dijkstra which is the better
        // (=faster) choice when the observations are close to each other
        boolean useDijkstra = disableLM || disableCH;

        LandmarkStorage landmarks;
        if (!useDijkstra && graphHopper.getLandmarks().get(profile.getName()) != null) {
            // using LM because u-turn prevention does not work properly with (node-based) CH
            landmarks = graphHopper.getLandmarks().get(profile.getName());
        } else {
            landmarks = null;
        }
        Weighting weighting = graphHopper.createWeighting(profile, hints);
        BooleanEncodedValue inSubnetworkEnc = graphHopper.getEncodingManager().getBooleanEncodedValue(Subnetwork.key(profileStr));
        DefaultSnapFilter snapFilter = new DefaultSnapFilter(weighting, inSubnetworkEnc);
        int maxVisitedNodes = hints.getInt(Parameters.Routing.MAX_VISITED_NODES, Integer.MAX_VALUE);

        Router router = new Router() {
            @Override
            public EdgeFilter getSnapFilter() {
                return snapFilter;
            }

            @Override
            public List<Path> calcPaths(QueryGraph queryGraph, int fromNode, int fromOutEdge, int[] toNodes, int[] toInEdges, int observationIndex) {
                assert(toNodes.length == toInEdges.length);
                List<Path> result = new ArrayList<>();
                for (int i = 0; i < toNodes.length; i++) {
                    result.add(calcOnePath(queryGraph, fromNode, toNodes[i], fromOutEdge, toInEdges[i]));
                }
                return result;
            }

            private Path calcOnePath(QueryGraph queryGraph, int fromNode, int toNode, int fromOutEdge, int toInEdge) {
                Weighting queryGraphWeighting = queryGraph.wrapWeighting(weighting);
                if (landmarks != null) {
                    AStarBidirection aStarBidirection = new AStarBidirection(queryGraph, queryGraphWeighting, TraversalMode.EDGE_BASED) {
                        @Override
                        protected void initCollections(int size) {
                            super.initCollections(50);
                        }
                    };
                    int activeLM = Math.min(8, landmarks.getLandmarkCount());
                    LMApproximator lmApproximator = LMApproximator.forLandmarks(queryGraph, queryGraphWeighting, landmarks, activeLM);
                    aStarBidirection.setApproximation(lmApproximator);
                    aStarBidirection.setMaxVisitedNodes(maxVisitedNodes);
                    return aStarBidirection.calcPath(fromNode, toNode, fromOutEdge, toInEdge);
                } else {
                    DijkstraBidirectionRef dijkstraBidirectionRef = new DijkstraBidirectionRef(queryGraph, queryGraphWeighting, TraversalMode.EDGE_BASED) {
                        @Override
                        protected void initCollections(int size) {
                            super.initCollections(50);
                        }
                    };
                    dijkstraBidirectionRef.setMaxVisitedNodes(maxVisitedNodes);
                    return dijkstraBidirectionRef.calcPath(fromNode, toNode, fromOutEdge, toInEdge);
                }
            }

            @Override
            public Weighting getWeighting() {
                return weighting;
            }
        };
        return router;
    }

    public MapMatching(BaseGraph graph, LocationIndexTree locationIndex, Router router) {
        this.graph = graph;
        this.locationIndex = locationIndex;
        this.router = router;
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

    public MatchResult match(List<Observation> observations) {
        List<Observation> filteredObservations = filterObservations(observations);
        statistics.put("filteredObservations", filteredObservations.size());

        // Snap observations to links. Generates multiple candidate snaps per observation.
        List<List<Snap>> snapsPerObservation = filteredObservations.stream()
                .map(o -> findCandidateSnaps(o.getPoint().lat, o.getPoint().lon))
                .collect(Collectors.toList());
        statistics.put("snapsPerObservation", snapsPerObservation.stream().mapToInt(Collection::size).toArray());

        // Create the query graph, containing split edges so that all the places where an observation might have happened
        // are a node. This modifies the Snap objects and puts the new node numbers into them.
        queryGraph = QueryGraph.create(graph, snapsPerObservation.stream().flatMap(Collection::stream).collect(Collectors.toList()));

        // Creates candidates from the Snaps of all observations (a candidate is basically a
        // Snap + direction).
        List<ObservationWithCandidateStates> timeSteps = createTimeSteps(filteredObservations, snapsPerObservation);

        // Compute the most likely sequence of map matching candidates:
        List<SequenceState<State, Observation, Path>> seq = computeViterbiSequence(timeSteps);
        statistics.put("transitionDistances", seq.stream().filter(s -> s.transitionDescriptor != null).mapToLong(s -> Math.round(s.transitionDescriptor.getDistance())).toArray());
        statistics.put("visitedNodes", router.getVisitedNodes());
        statistics.put("snapDistanceRanks", IntStream.range(0, seq.size()).map(i -> snapsPerObservation.get(i).indexOf(seq.get(i).state.getSnap())).toArray());
        statistics.put("snapDistances", seq.stream().mapToDouble(s -> s.state.getSnap().getQueryDistance()).toArray());
        statistics.put("maxSnapDistances", IntStream.range(0, seq.size()).mapToDouble(i -> snapsPerObservation.get(i).stream().mapToDouble(Snap::getQueryDistance).max().orElse(-1.0)).toArray());

        List<EdgeIteratorState> path = seq.stream().filter(s1 -> s1.transitionDescriptor != null).flatMap(s1 -> s1.transitionDescriptor.calcEdges().stream()).collect(Collectors.toList());

        MatchResult result = new MatchResult(prepareEdgeMatches(seq));
        Weighting queryGraphWeighting = queryGraph.wrapWeighting(router.getWeighting());
        result.setMergedPath(new MapMatchedPath(queryGraph, queryGraphWeighting, path));
        result.setMatchMillis(seq.stream().filter(s -> s.transitionDescriptor != null).mapToLong(s -> s.transitionDescriptor.getTime()).sum());
        result.setMatchLength(seq.stream().filter(s -> s.transitionDescriptor != null).mapToDouble(s -> s.transitionDescriptor.getDistance()).sum());
        result.setGPXEntriesLength(gpxLength(observations));
        result.setGraph(queryGraph);
        result.setWeighting(queryGraphWeighting);
        return result;
    }

    /**
     * Filters observations to only those which will be used for map matching (i.e. those which
     * are separated by at least 2 * measurementErrorSigman
     */
    public List<Observation> filterObservations(List<Observation> observations) {
        List<Observation> filtered = new ArrayList<>();
        Observation prevEntry = null;
        double acc = 0.0;
        int last = observations.size() - 1;
        for (int i = 0; i <= last; i++) {
            Observation observation = observations.get(i);
            if (i == 0 || i == last || distanceCalc.calcDist(
                    prevEntry.getPoint().getLat(), prevEntry.getPoint().getLon(),
                    observation.getPoint().getLat(), observation.getPoint().getLon()) > 2 * measurementErrorSigma) {
                if (i > 0) {
                    Observation prevObservation = observations.get(i - 1);
                    acc += distanceCalc.calcDist(
                            prevObservation.getPoint().getLat(), prevObservation.getPoint().getLon(),
                            observation.getPoint().getLat(), observation.getPoint().getLon());
                    acc -= distanceCalc.calcDist(
                            prevEntry.getPoint().getLat(), prevEntry.getPoint().getLon(),
                            observation.getPoint().getLat(), observation.getPoint().getLon());
                }
                // Here we store the meters of distance that we are missing because of the filtering,
                // so that when we add these terms to the distances between the filtered points,
                // the original total distance between the unfiltered points is conserved.
                // (See test for kind of a specification.)
                observation.setAccumulatedLinearDistanceToPrevious(acc);
                filtered.add(observation);
                prevEntry = observation;
                acc = 0.0;
            } else {
                Observation prevObservation = observations.get(i - 1);
                acc += distanceCalc.calcDist(
                        prevObservation.getPoint().getLat(), prevObservation.getPoint().getLon(),
                        observation.getPoint().getLat(), observation.getPoint().getLon());
            }
        }
        return filtered;
    }

    public List<Snap> findCandidateSnaps(final double queryLat, final double queryLon) {
        double rLon = (measurementErrorSigma * 360.0 / DistanceCalcEarth.DIST_EARTH.calcCircumference(queryLat));
        double rLat = measurementErrorSigma / DistanceCalcEarth.METERS_PER_DEGREE;
        Envelope envelope = new Envelope(queryLon, queryLon, queryLat, queryLat);
        for (int i = 0; i < 50; i++) {
            envelope.expandBy(rLon, rLat);
            List<Snap> snaps = findCandidateSnapsInBBox(queryLat, queryLon, BBox.fromEnvelope(envelope));
            if (!snaps.isEmpty()) {
                return snaps;
            }
        }
        return Collections.emptyList();
    }

    private List<Snap> findCandidateSnapsInBBox(double queryLat, double queryLon, BBox queryShape) {
        EdgeFilter edgeFilter = router.getSnapFilter();
        List<Snap> snaps = new ArrayList<>();
        IntHashSet seenEdges = new IntHashSet();
        IntHashSet seenNodes = new IntHashSet();
        locationIndex.query(queryShape, edgeId -> {
            EdgeIteratorState edge = graph.getEdgeIteratorStateForKey(edgeId * 2);
            if (seenEdges.add(edgeId) && edgeFilter.accept(edge)) {
                Snap snap = new Snap(queryLat, queryLon);
                locationIndex.traverseEdge(queryLat, queryLon, edge, (node, normedDist, wayIndex, pos) -> {
                    if (normedDist < snap.getQueryDistance()) {
                        snap.setQueryDistance(normedDist);
                        snap.setClosestNode(node);
                        snap.setWayIndex(wayIndex);
                        snap.setSnappedPosition(pos);
                    }
                });
                double dist = DIST_PLANE.calcDenormalizedDist(snap.getQueryDistance());
                snap.setClosestEdge(edge);
                snap.setQueryDistance(dist);
                if (snap.isValid() && (snap.getSnappedPosition() != Snap.Position.TOWER || seenNodes.add(snap.getClosestNode()))) {
                    snap.calcSnappedPoint(DistanceCalcEarth.DIST_EARTH);
                    if (queryShape.contains(snap.getSnappedPoint().lat, snap.getSnappedPoint().lon)) {
                        snaps.add(snap);
                    }
                }
            }
        });
        snaps.sort(Comparator.comparingDouble(Snap::getQueryDistance));
        return snaps;
    }

    /**
     * Creates TimeSteps with candidates for the GPX entries but does not create emission or
     * transition probabilities. Creates directed candidates for virtual nodes and undirected
     * candidates for real nodes.
     */
    private List<ObservationWithCandidateStates> createTimeSteps(List<Observation> filteredObservations, List<List<Snap>> splitsPerObservation) {
        if (splitsPerObservation.size() != filteredObservations.size()) {
            throw new IllegalArgumentException(
                    "filteredGPXEntries and queriesPerEntry must have same size.");
        }

        final List<ObservationWithCandidateStates> timeSteps = new ArrayList<>();
        for (int i = 0; i < filteredObservations.size(); i++) {
            Observation observation = filteredObservations.get(i);
            Collection<Snap> splits = splitsPerObservation.get(i);
            List<State> candidates = new ArrayList<>();
            for (Snap split : splits) {
                if (queryGraph.isVirtualNode(split.getClosestNode())) {
                    List<VirtualEdgeIteratorState> virtualEdges = new ArrayList<>();
                    EdgeIterator iter = queryGraph.createEdgeExplorer().setBaseNode(split.getClosestNode());
                    while (iter.next()) {
                        if (!queryGraph.isVirtualEdge(iter.getEdge())) {
                            throw new RuntimeException("Virtual nodes must only have virtual edges "
                                    + "to adjacent nodes.");
                        }
                        virtualEdges.add((VirtualEdgeIteratorState) queryGraph.getEdgeIteratorState(iter.getEdge(), iter.getAdjNode()));
                    }
                    if (virtualEdges.size() != 2) {
                        throw new RuntimeException("Each virtual node must have exactly 2 "
                                + "virtual edges (reverse virtual edges are not returned by the "
                                + "EdgeIterator");
                    }

                    // Create a directed candidate for each of the two possible directions through
                    // the virtual node. We need to add candidates for both directions because
                    // we don't know yet which is the correct one. This will be figured
                    // out by the Viterbi algorithm.
                    candidates.add(new State(observation, split, virtualEdges.get(0), virtualEdges.get(1)));
                    candidates.add(new State(observation, split, virtualEdges.get(1), virtualEdges.get(0)));
                } else {
                    // Create an undirected candidate for the real node.
                    candidates.add(new State(observation, split));
                }
            }

            timeSteps.add(new ObservationWithCandidateStates(observation, candidates));
        }
        return timeSteps;
    }

    static class Label {
        int timeStep;
        State state;
        Label back;
        boolean isDeleted;
        double minusLogProbability;
    }

    private List<SequenceState<State, Observation, Path>> computeViterbiSequence(List<ObservationWithCandidateStates> timeSteps) {
        final HmmProbabilities probabilities = new HmmProbabilities(measurementErrorSigma, transitionProbabilityBeta);
        final Map<State, Label> labels = new HashMap<>();
        Map<Transition<State>, Path> roadPaths = new HashMap<>();

        PriorityQueue<Label> q = new PriorityQueue<>(Comparator.comparing(qe -> qe.minusLogProbability));
        for (State candidate : timeSteps.get(0).candidates) {
            // distance from observation to road in meters
            final double distance = candidate.getSnap().getQueryDistance();
            Label label = new Label();
            label.state = candidate;
            label.minusLogProbability = probabilities.emissionLogProbability(distance) * -1.0;
            q.add(label);
            labels.put(candidate, label);
        }
        Label qe = null;
        while (!q.isEmpty()) {
            qe = q.poll();
            if (qe.isDeleted)
                continue;
            if (qe.timeStep == timeSteps.size() - 1)
                break;
            State from = qe.state;
            ObservationWithCandidateStates timeStep = timeSteps.get(qe.timeStep);
            ObservationWithCandidateStates nextTimeStep = timeSteps.get(qe.timeStep + 1);
            final double linearDistance = distanceCalc.calcDist(timeStep.observation.getPoint().lat, timeStep.observation.getPoint().lon,
                    nextTimeStep.observation.getPoint().lat, nextTimeStep.observation.getPoint().lon)
                    + nextTimeStep.observation.getAccumulatedLinearDistanceToPrevious();
            int fromNode = from.getSnap().getClosestNode();
            int fromOutEdge = from.isOnDirectedEdge() ? from.getOutgoingVirtualEdge().getEdge() : EdgeIterator.ANY_EDGE;
            int[] toNodes = nextTimeStep.candidates.stream().mapToInt(c -> c.getSnap().getClosestNode()).toArray();
            int[] toInEdges = nextTimeStep.candidates.stream().mapToInt(to -> to.isOnDirectedEdge() ? to.getIncomingVirtualEdge().getEdge() : EdgeIterator.ANY_EDGE).toArray();
            List<Path> paths = router.calcPaths(queryGraph, fromNode, fromOutEdge, toNodes, toInEdges, from.getEntry().getIndex());
            for (int i = 0; i < nextTimeStep.candidates.size(); i++) {
                State to = nextTimeStep.candidates.get(i);
                Path path = paths.get(i);
                if (path.isFound()) {
                    double transitionLogProbability = probabilities.transitionLogProbability(path.getDistance(), linearDistance);
                    Transition<State> transition = new Transition<>(from, to);
                    roadPaths.put(transition, path);
                    double minusLogProbability = qe.minusLogProbability - probabilities.emissionLogProbability(to.getSnap().getQueryDistance()) - transitionLogProbability;
                    Label label1 = labels.get(to);
                    if (label1 == null || minusLogProbability < label1.minusLogProbability) {
                        q.stream().filter(oldQe -> !oldQe.isDeleted && oldQe.state == to).findFirst().ifPresent(oldQe -> oldQe.isDeleted = true);
                        Label label = new Label();
                        label.state = to;
                        label.timeStep = qe.timeStep + 1;
                        label.back = qe;
                        label.minusLogProbability = minusLogProbability;
                        q.add(label);
                        labels.put(to, label);
                    }
                }
            }
        }
        if (qe == null) {
            throw new IllegalArgumentException("Sequence is broken for submitted track at initial time step.");
        }
//        if (qe.timeStep != timeSteps.size() - 1) {
//            throw new IllegalArgumentException("Sequence is broken for submitted track at time step "
//                    + qe.timeStep + ". observation:" + qe.state.getEntry());
//        }
        ArrayList<SequenceState<State, Observation, Path>> result = new ArrayList<>();
        while (qe != null) {
            final SequenceState<State, Observation, Path> ss = new SequenceState<>(qe.state, qe.state.getEntry(), qe.back == null ? null : roadPaths.get(new Transition<>(qe.back.state, qe.state)));
            result.add(ss);
            qe = qe.back;
        }
        Collections.reverse(result);
        return result;
    }

    private List<EdgeMatch> prepareEdgeMatches(List<SequenceState<State, Observation, Path>> seq) {
        // This creates a list of directed edges (EdgeIteratorState instances turned the right way),
        // each associated with 0 or more of the observations.
        // These directed edges are edges of the real street graph, where nodes are intersections.
        // So in _this_ representation, the path that you get when you just look at the edges goes from
        // an intersection to an intersection.

        // Implementation note: We have to look at both states _and_ transitions, since we can have e.g. just one state,
        // or two states with a transition that is an empty path (observations snapped to the same node in the query graph),
        // but these states still happen on an edge, and for this representation, we want to have that edge.
        // (Whereas in the ResponsePath representation, we would just see an empty path.)

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
                    EdgeIteratorState newDirectedRealEdge = resolveToRealEdge(edge);
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
                EdgeIteratorState newDirectedRealEdge = resolveToRealEdge(transitionAndState.state.getOutgoingVirtualEdge());
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

    private EdgeIteratorState resolveToRealEdge(EdgeIteratorState edgeIteratorState) {
        if (queryGraph.isVirtualNode(edgeIteratorState.getBaseNode()) || queryGraph.isVirtualNode(edgeIteratorState.getAdjNode())) {
            return graph.getEdgeIteratorStateForKey(((VirtualEdgeIteratorState) edgeIteratorState).getOriginalEdgeKey());
        } else {
            return edgeIteratorState;
        }
    }

    public Map<String, Object> getStatistics() {
        return statistics;
    }

    private static class MapMatchedPath extends Path {
        MapMatchedPath(Graph graph, Weighting weighting, List<EdgeIteratorState> edges) {
            super(graph);
            int prevEdge = EdgeIterator.NO_EDGE;
            for (EdgeIteratorState edge : edges) {
                addDistance(edge.getDistance());
                addTime(GHUtility.calcMillisWithTurnMillis(weighting, edge, false, prevEdge));
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

    public interface Router {
        EdgeFilter getSnapFilter();

        List<Path> calcPaths(QueryGraph queryGraph, int fromNode, int fromOutEdge, int[] toNodes, int[] toInEdges, int observationIndex);

        Weighting getWeighting();

        default long getVisitedNodes() {
            return 0L;
        }
    }

}