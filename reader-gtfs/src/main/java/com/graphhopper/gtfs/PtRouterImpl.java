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

package com.graphhopper.gtfs;

import com.conveyal.gtfs.GTFSFeed;
import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.querygraph.VirtualEdgeIteratorState;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.PointList;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.Translation;
import com.graphhopper.util.TranslationMap;
import com.graphhopper.util.details.PathDetailsBuilderFactory;
import com.graphhopper.util.exceptions.PointNotFoundException;
import com.graphhopper.util.shapes.GHPoint;

import javax.inject.Inject;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Comparator.comparingLong;

public final class PtRouterImpl implements PtRouter {

    private final TranslationMap translationMap;
    private final PtEncodedValues ptEncodedValues;
    private final Weighting accessEgressWeighting;
    private final GraphHopperStorage graphHopperStorage;
    private final LocationIndex locationIndex;
    private final GtfsStorage gtfsStorage;
    private final RealtimeFeed realtimeFeed;
    private final TripFromLabel tripFromLabel;

    @Inject
    public PtRouterImpl(TranslationMap translationMap, GraphHopperStorage graphHopperStorage, LocationIndex locationIndex, GtfsStorage gtfsStorage, RealtimeFeed realtimeFeed, PathDetailsBuilderFactory pathDetailsBuilderFactory) {
        this.ptEncodedValues = PtEncodedValues.fromEncodingManager(graphHopperStorage.getEncodingManager());
        this.accessEgressWeighting = new FastestWeighting(graphHopperStorage.getEncodingManager().getEncoder("foot"));
        this.translationMap = translationMap;
        this.graphHopperStorage = graphHopperStorage;
        this.locationIndex = locationIndex;
        this.gtfsStorage = gtfsStorage;
        this.realtimeFeed = realtimeFeed;
        this.tripFromLabel = new TripFromLabel(this.graphHopperStorage, this.gtfsStorage, this.realtimeFeed, pathDetailsBuilderFactory);
    }

    public static Factory createFactory(TranslationMap translationMap, GraphHopper graphHopperStorage, LocationIndex locationIndex, GtfsStorage gtfsStorage) {
        return new Factory(translationMap, graphHopperStorage.getGraphHopperStorage(), locationIndex, gtfsStorage);
    }

    @Override
    public GHResponse route(Request request) {
        return new RequestHandler(request).route();
    }

    public static class Factory {
        private final TranslationMap translationMap;
        private final GraphHopperStorage graphHopperStorage;
        private final LocationIndex locationIndex;
        private final GtfsStorage gtfsStorage;
        private final Map<String, Transfers> transfers;

        private Factory(TranslationMap translationMap, GraphHopperStorage graphHopperStorage, LocationIndex locationIndex, GtfsStorage gtfsStorage) {
            this.translationMap = translationMap;
            this.graphHopperStorage = graphHopperStorage;
            this.locationIndex = locationIndex;
            this.gtfsStorage = gtfsStorage;
            this.transfers = new HashMap<>();
            for (Map.Entry<String, GTFSFeed> entry : this.gtfsStorage.getGtfsFeeds().entrySet()) {
                this.transfers.put(entry.getKey(), new Transfers(entry.getValue()));
            }
        }

        public PtRouter createWith(GtfsRealtime.FeedMessage realtimeFeed) {
            Map<String, GtfsRealtime.FeedMessage> realtimeFeeds = new HashMap<>();
            realtimeFeeds.put("gtfs_0", realtimeFeed);
            return new PtRouterImpl(translationMap, graphHopperStorage, locationIndex, gtfsStorage, RealtimeFeed.fromProtobuf(graphHopperStorage, gtfsStorage, this.transfers, realtimeFeeds), new PathDetailsBuilderFactory());
        }

        public PtRouter createWithoutRealtimeFeed() {
            return new PtRouterImpl(translationMap, graphHopperStorage, locationIndex, gtfsStorage, RealtimeFeed.empty(gtfsStorage), new PathDetailsBuilderFactory());
        }
    }

    private class RequestHandler {
        private final int maxVisitedNodesForRequest;
        private final int limitSolutions;
        private final long maxProfileDuration;
        private final Instant initialTime;
        private final boolean profileQuery;
        private final boolean arriveBy;
        private final boolean ignoreTransfers;
        private final double betaTransfers;
        private final double betaWalkTime;
        private final double walkSpeedKmH;
        private final int blockedRouteTypes;
        private final GHLocation enter;
        private final GHLocation exit;
        private final Translation translation;
        private final List<String> requestedPathDetails;
        private final List<VirtualEdgeIteratorState> extraEdges = new ArrayList<>(realtimeFeed.getAdditionalEdges());

        private final GHResponse response = new GHResponse();
        private final Graph graphWithExtraEdges = new WrapperGraph(graphHopperStorage, extraEdges);
        private final long limitStreetTime;
        private QueryGraph queryGraph;
        private int visitedNodes;

        RequestHandler(Request request) {
            maxVisitedNodesForRequest = request.getMaxVisitedNodes();
            profileQuery = request.isProfileQuery();
            ignoreTransfers = Optional.ofNullable(request.getIgnoreTransfers()).orElse(request.isProfileQuery());
            betaTransfers = request.getBetaTransfers();
            betaWalkTime = request.getBetaWalkTime();
            limitSolutions = Optional.ofNullable(request.getLimitSolutions()).orElse(profileQuery ? 50 : ignoreTransfers ? 1 : Integer.MAX_VALUE);
            initialTime = request.getEarliestDepartureTime();
            maxProfileDuration = request.getMaxProfileDuration().toMillis();
            arriveBy = request.isArriveBy();
            walkSpeedKmH = request.getWalkSpeedKmH();
            blockedRouteTypes = request.getBlockedRouteTypes();
            translation = translationMap.getWithFallBack(request.getLocale());
            enter = request.getPoints().get(0);
            exit = request.getPoints().get(1);
            limitStreetTime = request.getLimitStreetTime() != null ? request.getLimitStreetTime().toMillis() : Long.MAX_VALUE;
            requestedPathDetails = request.getPathDetails();
        }

        GHResponse route() {
            StopWatch stopWatch = new StopWatch().start();
            ArrayList<Snap> pointSnaps = new ArrayList<>();
            ArrayList<Snap> allSnaps = new ArrayList<>();
            PointList points = new PointList(2, false);
            List<GHLocation> locations = Arrays.asList(enter, exit);
            for (int i = 0; i < locations.size(); i++) {
                if (enter instanceof GHPointLocation) {
                    final Snap closest = findByPoint(((GHPointLocation) locations.get(i)).ghPoint, i);
                    pointSnaps.add(closest);
                    allSnaps.add(closest);
                    points.add(closest.getSnappedPoint());
                } else if (enter instanceof GHStationLocation) {
                    final Snap station = findByStationId((GHStationLocation) locations.get(i), i);
                    allSnaps.add(station);
                    points.add(graphHopperStorage.getNodeAccess().getLat(station.getClosestNode()), graphHopperStorage.getNodeAccess().getLon(station.getClosestNode()));
                }
            }
            queryGraph = QueryGraph.create(graphWithExtraEdges, pointSnaps); // modifies pointSnaps!
            response.addDebugInfo("idLookup:" + stopWatch.stop().getSeconds() + "s");

            int startNode;
            int destNode;
            if (arriveBy) {
                startNode = allSnaps.get(1).getClosestNode();
                destNode = allSnaps.get(0).getClosestNode();
            } else {
                startNode = allSnaps.get(0).getClosestNode();
                destNode = allSnaps.get(1).getClosestNode();
            }
            List<List<Label.Transition>> solutions = findPaths(startNode, destNode);
            parseSolutionsAndAddToResponse(solutions, points);
            return response;
        }

        private Snap findByPoint(GHPoint point, int indexForErrorMessage) {
            final EdgeFilter filter = DefaultEdgeFilter.allEdges(graphHopperStorage.getEncodingManager().getEncoder("foot"));
            Snap source = locationIndex.findClosest(point.lat, point.lon, filter);
            if (!source.isValid()) {
                throw new PointNotFoundException("Cannot find point: " + point, indexForErrorMessage);
            }
            if (source.getClosestEdge().get(ptEncodedValues.getTypeEnc()) != GtfsStorage.EdgeType.HIGHWAY) {
                throw new RuntimeException(source.getClosestEdge().get(ptEncodedValues.getTypeEnc()).name());
            }
            return source;
        }

        private Snap findByStationId(GHStationLocation exit, int indexForErrorMessage) {
            for (Map.Entry<String, GTFSFeed> entry : gtfsStorage.getGtfsFeeds().entrySet()) {
                final Integer node = gtfsStorage.getStationNodes().get(new GtfsStorage.FeedIdWithStopId(entry.getKey(), exit.stop_id));
                if (node != null) {
                    final Snap station = new Snap(graphHopperStorage.getNodeAccess().getLat(node), graphHopperStorage.getNodeAccess().getLon(node));
                    station.setClosestNode(node);
                    return station;
                }
            }
            throw new PointNotFoundException("Cannot find station: " + exit.stop_id, indexForErrorMessage);
        }

        private void parseSolutionsAndAddToResponse(List<List<Label.Transition>> solutions, PointList waypoints) {
            for (List<Label.Transition> solution : solutions) {
                final ResponsePath responsePath = tripFromLabel.createResponsePath(translation, waypoints, queryGraph, accessEgressWeighting, solution, requestedPathDetails);
                responsePath.setImpossible(solution.stream().anyMatch(t -> t.label.impossible));
                responsePath.setTime((solution.get(solution.size() - 1).label.currentTime - solution.get(0).label.currentTime));
                response.add(responsePath);
            }
            Comparator<ResponsePath> c = Comparator.comparingInt(p -> (p.isImpossible() ? 1 : 0));
            Comparator<ResponsePath> d = Comparator.comparingDouble(ResponsePath::getTime);
            response.getAll().sort(c.thenComparing(d));
        }

        private List<List<Label.Transition>> findPaths(int startNode, int destNode) {
            StopWatch stopWatch = new StopWatch().start();
            final GraphExplorer accessEgressGraphExplorer = new GraphExplorer(queryGraph, accessEgressWeighting, ptEncodedValues, gtfsStorage, realtimeFeed, !arriveBy, true, walkSpeedKmH, false);
            boolean reverse = !arriveBy;
            GtfsStorage.EdgeType edgeType = reverse ? GtfsStorage.EdgeType.EXIT_PT : GtfsStorage.EdgeType.ENTER_PT;
            MultiCriteriaLabelSetting stationRouter = new MultiCriteriaLabelSetting(accessEgressGraphExplorer, ptEncodedValues, reverse, false, false, false, maxVisitedNodesForRequest, new ArrayList<>());
            stationRouter.setBetaWalkTime(betaWalkTime);
            stationRouter.setLimitStreetTime(limitStreetTime);
            Iterator<Label> stationIterator = stationRouter.calcLabels(destNode, initialTime, blockedRouteTypes).iterator();
            List<Label> stationLabels = new ArrayList<>();
            while (stationIterator.hasNext()) {
                Label label = stationIterator.next();
                if (label.adjNode == startNode) {
                    stationLabels.add(label);
                    break;
                } else if (label.edge != -1 && queryGraph.getEdgeIteratorState(label.edge, label.parent.adjNode).get(ptEncodedValues.getTypeEnc()) == edgeType) {
                    stationLabels.add(label);
                }
            }
            visitedNodes += stationRouter.getVisitedNodes();

            Map<Integer, Label> reverseSettledSet = new HashMap<>();
            for (Label stationLabel : stationLabels) {
                reverseSettledSet.put(stationLabel.adjNode, stationLabel);
            }

            GraphExplorer graphExplorer = new GraphExplorer(queryGraph, accessEgressWeighting, ptEncodedValues, gtfsStorage, realtimeFeed, arriveBy, false, walkSpeedKmH, false);
            List<Label> discoveredSolutions = new ArrayList<>();
            MultiCriteriaLabelSetting router = new MultiCriteriaLabelSetting(graphExplorer, ptEncodedValues, arriveBy, true, !ignoreTransfers, profileQuery, maxVisitedNodesForRequest, discoveredSolutions);
            router.setBetaTransfers(betaTransfers);
            router.setBetaWalkTime(betaWalkTime);
            final long smallestStationLabelWalkTime = stationLabels.stream()
                    .mapToLong(l -> l.walkTime).min()
                    .orElse(Long.MAX_VALUE);
            router.setLimitStreetTime(Math.max(0, limitStreetTime - smallestStationLabelWalkTime));
            final long smallestStationLabelWeight;
            if (!stationLabels.isEmpty()) {
                smallestStationLabelWeight = stationRouter.weight(stationLabels.get(0));
            } else {
                smallestStationLabelWeight = Long.MAX_VALUE;
            }
            Iterator<Label> iterator = router.calcLabels(startNode, initialTime, blockedRouteTypes).iterator();
            Map<Label, Label> originalSolutions = new HashMap<>();

            Label walkSolution = null;
            long highestWeightForDominationTest = Long.MAX_VALUE;
            while (iterator.hasNext()) {
                Label label = iterator.next();
                // For single-criterion or pareto queries, we run to the end.
                //
                // For profile queries, we need a limited time window. Limiting the number of solutions is not
                // enough, as there may not be that many solutions - perhaps only walking - and we would run until the end of the calendar
                // because the router can't know that a super-fast PT departure isn't going to happen some day.
                //
                // Arguably, the number of solutions doesn't even make sense as a parameter, since they are not really
                // alternatives to choose from, but points in time where the optimal solution changes, which isn't really
                // a criterion for a PT user to limit their search. Some O/D relations just have more complicated profiles than others.
                // On the other hand, we may simply want to limit the amount of output that an arbitrarily complex profile
                // can produce, so maybe we should keep both.
                //
                // But no matter what, we always have to run past the highest weight in the open set. If we don't,
                // the last couple of routes in a profile will be suboptimal while the rest is good.
                if ((!profileQuery || profileFinished(router, discoveredSolutions, walkSolution)) && router.weight(label) + smallestStationLabelWeight > highestWeightForDominationTest) {
                    break;
                }
                Label reverseLabel = reverseSettledSet.get(label.adjNode);
                if (reverseLabel != null) {
                    Label combinedSolution = new Label(label.currentTime - reverseLabel.currentTime + initialTime.toEpochMilli(), -1, label.adjNode, label.nTransfers + reverseLabel.nTransfers, label.walkDistanceOnCurrentLeg + reverseLabel.walkDistanceOnCurrentLeg, label.departureTime, label.walkTime + reverseLabel.walkTime, 0, label.impossible, null);
                    if (router.isNotDominatedByAnyOf(combinedSolution, discoveredSolutions)) {
                        router.removeDominated(combinedSolution, discoveredSolutions);
                        List<Label> closedSolutions = discoveredSolutions.stream().filter(s -> router.weight(s) < router.weight(label) + smallestStationLabelWeight).collect(Collectors.toList());
                        if (closedSolutions.size() >= limitSolutions) continue;
                        if (profileQuery && combinedSolution.departureTime != null && (combinedSolution.departureTime - initialTime.toEpochMilli()) * (arriveBy ? -1L : 1L) > maxProfileDuration && closedSolutions.size() > 0 && closedSolutions.get(closedSolutions.size() - 1).departureTime != null && (closedSolutions.get(closedSolutions.size() - 1).departureTime - initialTime.toEpochMilli()) * (arriveBy ? -1L : 1L) > maxProfileDuration)
                            continue;
                        discoveredSolutions.add(combinedSolution);
                        discoveredSolutions.sort(comparingLong(s -> Optional.ofNullable(s.departureTime).orElse(0L)));
                        originalSolutions.put(combinedSolution, label);
                        if (label.nTransfers == 0 && reverseLabel.nTransfers == 0) {
                            walkSolution = combinedSolution;
                        }
                        if (profileQuery) {
                            highestWeightForDominationTest = discoveredSolutions.stream().mapToLong(router::weight).max().orElse(Long.MAX_VALUE);
                            if (walkSolution != null && discoveredSolutions.size() < limitSolutions) {
                                // If we have a walk solution, we have it at every point in time in the profile.
                                // (I can start walking any time I want, unlike with bus departures.)
                                // Here we virtually add it to the end of the profile, so it acts as a sentinel
                                // to remind us that we still have to search that far to close the set.
                                highestWeightForDominationTest = Math.max(highestWeightForDominationTest, router.weight(walkSolution) + maxProfileDuration);
                            }
                        } else {
                            highestWeightForDominationTest = discoveredSolutions.stream().filter(s -> !s.impossible && (ignoreTransfers || s.nTransfers <= 1)).mapToLong(router::weight).min().orElse(Long.MAX_VALUE);
                        }
                    }
                }
            }

            List<List<Label.Transition>> paths = new ArrayList<>();
            for (Label discoveredSolution : discoveredSolutions) {
                Label originalSolution = originalSolutions.get(discoveredSolution);
                List<Label.Transition> pathToDestinationStop = Label.getTransitions(originalSolution, arriveBy, ptEncodedValues, queryGraph, realtimeFeed);
                if (arriveBy) {
                    List<Label.Transition> pathFromStation = Label.getTransitions(reverseSettledSet.get(pathToDestinationStop.get(0).label.adjNode), false, ptEncodedValues, queryGraph, realtimeFeed);
                    long diff = pathToDestinationStop.get(0).label.currentTime - pathFromStation.get(pathFromStation.size() - 1).label.currentTime;
                    List<Label.Transition> patchedPathFromStation = pathFromStation.stream().map(t -> {
                        return new Label.Transition(new Label(t.label.currentTime + diff, t.label.edge, t.label.adjNode, t.label.nTransfers, t.label.walkDistanceOnCurrentLeg, t.label.departureTime, t.label.walkTime, t.label.residualDelay, t.label.impossible, null), t.edge);
                    }).collect(Collectors.toList());
                    List<Label.Transition> pp = new ArrayList<>(pathToDestinationStop.subList(1, pathToDestinationStop.size()));
                    pp.addAll(0, patchedPathFromStation);
                    paths.add(pp);
                } else {
                    List<Label.Transition> pathFromStation = Label.getTransitions(reverseSettledSet.get(pathToDestinationStop.get(pathToDestinationStop.size() - 1).label.adjNode), true, ptEncodedValues, queryGraph, realtimeFeed);
                    long diff = pathToDestinationStop.get(pathToDestinationStop.size() - 1).label.currentTime - pathFromStation.get(0).label.currentTime;
                    List<Label.Transition> patchedPathFromStation = pathFromStation.stream().map(t -> {
                        return new Label.Transition(new Label(t.label.currentTime + diff, t.label.edge, t.label.adjNode, t.label.nTransfers, t.label.walkDistanceOnCurrentLeg, t.label.departureTime, t.label.walkTime, t.label.residualDelay, t.label.impossible, null), t.edge);
                    }).collect(Collectors.toList());
                    List<Label.Transition> pp = new ArrayList<>(pathToDestinationStop);
                    pp.addAll(patchedPathFromStation.subList(1, pathFromStation.size()));
                    paths.add(pp);
                }
            }

            visitedNodes += router.getVisitedNodes();
            response.addDebugInfo("routing:" + stopWatch.stop().getSeconds() + "s");
            if (discoveredSolutions.isEmpty() && router.getVisitedNodes() >= maxVisitedNodesForRequest) {
                response.addError(new IllegalArgumentException("No path found - maximum number of nodes exceeded: " + maxVisitedNodesForRequest));
            }
            response.getHints().putObject("visited_nodes.sum", visitedNodes);
            response.getHints().putObject("visited_nodes.average", visitedNodes);
            if (discoveredSolutions.isEmpty()) {
                response.addError(new RuntimeException("No route found"));
            }
            return paths;
        }

        private boolean profileFinished(MultiCriteriaLabelSetting router, List<Label> discoveredSolutions, Label walkSolution) {
            return discoveredSolutions.size() >= limitSolutions ||
                    (!discoveredSolutions.isEmpty() && router.timeSinceStartTime(discoveredSolutions.get(discoveredSolutions.size() - 1)) > maxProfileDuration) ||
                    walkSolution != null;
            // Imagine we can always add the walk solution again to the end of the list (it can start any time).
            // In turn, we must also think of this virtual walk solution in the other test (where we check if all labels are closed).
        }

    }

}
