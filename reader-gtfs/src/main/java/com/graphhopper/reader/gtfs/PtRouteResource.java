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

package com.graphhopper.reader.gtfs;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.PathWrapper;
import com.graphhopper.Trip;
import com.graphhopper.http.WebHelper;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.querygraph.VirtualEdgeIteratorState;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.*;
import com.graphhopper.util.exceptions.PointNotFoundException;
import com.graphhopper.util.shapes.GHPoint;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Comparator.comparingLong;

@Path("route-pt")
public final class PtRouteResource {

    private final TranslationMap translationMap;
    private final PtEncodedValues ptEncodedValues;
    private final Weighting accessEgressWeighting;
    private final GraphHopperStorage graphHopperStorage;
    private final LocationIndex locationIndex;
    private final GtfsStorage gtfsStorage;
    private final RealtimeFeed realtimeFeed;
    private final TripFromLabel tripFromLabel;

    @Inject
    public PtRouteResource(TranslationMap translationMap, GraphHopperStorage graphHopperStorage, LocationIndex locationIndex, GtfsStorage gtfsStorage, RealtimeFeed realtimeFeed) {
        this.ptEncodedValues = PtEncodedValues.fromEncodingManager(graphHopperStorage.getEncodingManager());
        this.accessEgressWeighting = new FastestWeighting(graphHopperStorage.getEncodingManager().getEncoder("foot"));
        this.translationMap = translationMap;
        this.graphHopperStorage = graphHopperStorage;
        this.locationIndex = locationIndex;
        this.gtfsStorage = gtfsStorage;
        this.realtimeFeed = realtimeFeed;
        this.tripFromLabel = new TripFromLabel(this.gtfsStorage, this.realtimeFeed);
    }

    public static Factory createFactory(TranslationMap translationMap, GraphHopper graphHopperStorage, LocationIndex locationIndex, GtfsStorage gtfsStorage) {
        return new Factory(translationMap, graphHopperStorage.getGraphHopperStorage(), locationIndex, gtfsStorage);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public ObjectNode route(@QueryParam("point") List<GHLocation> requestPoints,
                            @QueryParam("pt.earliest_departure_time") String departureTimeString,
                            @QueryParam("pt.arrive_by") @DefaultValue("false") boolean arriveBy,
                            @QueryParam("locale") String localeStr,
                            @QueryParam("pt.ignore_transfers") Boolean ignoreTransfers,
                            @QueryParam("pt.profile") Boolean profileQuery,
                            @QueryParam("pt.limit_solutions") Integer limitSolutions) {

        if (departureTimeString == null) {
            throw new BadRequestException(String.format(Locale.ROOT, "Illegal value for required parameter %s: [%s]", "pt.earliest_departure_time", departureTimeString));
        }
        Instant departureTime;
        try {
            departureTime = Instant.parse(departureTimeString);
        } catch (DateTimeParseException e) {
            throw new BadRequestException(String.format(Locale.ROOT, "Illegal value for required parameter %s: [%s]", "pt.earliest_departure_time", departureTimeString));
        }

        Request request = new Request(requestPoints, departureTime);
        request.setArriveBy(arriveBy);
        Optional.ofNullable(profileQuery).ifPresent(request::setProfileQuery);
        Optional.ofNullable(ignoreTransfers).ifPresent(request::setIgnoreTransfers);
        Optional.ofNullable(localeStr).ifPresent(s -> request.setLocale(Helper.getLocale(s)));
        Optional.ofNullable(limitSolutions).ifPresent(request::setLimitSolutions);

        GHResponse route = new RequestHandler(request).route();
        return WebHelper.jsonObject(route, true, true, false, false, 0.0f);
    }

    public GHResponse route(Request request) {
        return new RequestHandler(request).route();
    }

    public static class Factory {
        private final TranslationMap translationMap;
        private final PtEncodedValues ptEncodedValues;
        private final GraphHopperStorage graphHopperStorage;
        private final LocationIndex locationIndex;
        private final GtfsStorage gtfsStorage;

        private Factory(TranslationMap translationMap, GraphHopperStorage graphHopperStorage, LocationIndex locationIndex, GtfsStorage gtfsStorage) {
            this.ptEncodedValues = PtEncodedValues.fromEncodingManager(graphHopperStorage.getEncodingManager());
            this.translationMap = translationMap;
            this.graphHopperStorage = graphHopperStorage;
            this.locationIndex = locationIndex;
            this.gtfsStorage = gtfsStorage;
        }

        public PtRouteResource createWith(GtfsRealtime.FeedMessage realtimeFeed) {
            Map<String, GtfsRealtime.FeedMessage> realtimeFeeds = new HashMap<>();
            realtimeFeeds.put("gtfs_0", realtimeFeed);
            return new PtRouteResource(translationMap, graphHopperStorage, locationIndex, gtfsStorage, RealtimeFeed.fromProtobuf(graphHopperStorage, gtfsStorage, ptEncodedValues, realtimeFeeds));
        }

        public PtRouteResource createWithoutRealtimeFeed() {
            return new PtRouteResource(translationMap, graphHopperStorage, locationIndex, gtfsStorage, RealtimeFeed.empty(gtfsStorage));
        }
    }

    private class RequestHandler {
        private final int maxVisitedNodesForRequest;
        private final int limitSolutions;
        private final long maxProfileDuration = Duration.ofHours(4).toMillis();
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
        private final List<VirtualEdgeIteratorState> extraEdges = new ArrayList<>(realtimeFeed.getAdditionalEdges());

        private final GHResponse response = new GHResponse();
        private final Graph graphWithExtraEdges = new WrapperGraph(graphHopperStorage, extraEdges);
        private QueryGraph queryGraph;
        private int visitedNodes;

        RequestHandler(Request request) {
            maxVisitedNodesForRequest = request.getMaxVisitedNodes();
            profileQuery = request.isProfileQuery();
            ignoreTransfers = Optional.ofNullable(request.getIgnoreTransfers()).orElse(request.isProfileQuery());
            betaTransfers = request.getBetaTransfers();
            betaWalkTime = request.getBetaWalkTime();
            limitSolutions = Optional.ofNullable(request.getLimitSolutions()).orElse(profileQuery ? 5 : ignoreTransfers ? 1 : Integer.MAX_VALUE);
            initialTime = request.getEarliestDepartureTime();
            arriveBy = request.isArriveBy();
            walkSpeedKmH = request.getWalkSpeedKmH();
            blockedRouteTypes = request.getBlockedRouteTypes();
            translation = translationMap.getWithFallBack(request.getLocale());
            if (request.getPoints().size() != 2) {
                throw new IllegalArgumentException("Exactly 2 points have to be specified, but was:" + request.getPoints().size());
            }
            enter = request.getPoints().get(0);
            exit = request.getPoints().get(1);
        }

        GHResponse route() {
            StopWatch stopWatch = new StopWatch().start();
            ArrayList<QueryResult> pointQueryResults = new ArrayList<>();
            ArrayList<QueryResult> allQueryResults = new ArrayList<>();
            PointList points = new PointList(2, false);
            if (enter instanceof GHPointLocation) {
                final QueryResult closest = findClosest(((GHPointLocation) enter).ghPoint, 0);
                pointQueryResults.add(closest);
                allQueryResults.add(closest);
                points.add(closest.getSnappedPoint());
            } else if (enter instanceof GHStationLocation) {
                final String stop_id = ((GHStationLocation) enter).stop_id;
                final int node = gtfsStorage.getStationNodes().get(stop_id);
                final QueryResult station = new QueryResult(graphHopperStorage.getNodeAccess().getLat(node), graphHopperStorage.getNodeAccess().getLon(node));
                station.setClosestNode(node);
                allQueryResults.add(station);
                points.add(graphHopperStorage.getNodeAccess().getLat(node), graphHopperStorage.getNodeAccess().getLon(node));
            }
            if (exit instanceof GHPointLocation) {
                final QueryResult closest = findClosest(((GHPointLocation) exit).ghPoint, 1);
                pointQueryResults.add(closest);
                allQueryResults.add(closest);
                points.add(closest.getSnappedPoint());
            } else if (exit instanceof GHStationLocation) {
                final String stop_id = ((GHStationLocation) exit).stop_id;
                final int node = gtfsStorage.getStationNodes().get(stop_id);
                final QueryResult station = new QueryResult(graphHopperStorage.getNodeAccess().getLat(node), graphHopperStorage.getNodeAccess().getLon(node));
                station.setClosestNode(node);
                allQueryResults.add(station);
                points.add(graphHopperStorage.getNodeAccess().getLat(node), graphHopperStorage.getNodeAccess().getLon(node));
            }
            queryGraph = QueryGraph.lookup(graphWithExtraEdges, pointQueryResults); // modifies queryResults
            response.addDebugInfo("idLookup:" + stopWatch.stop().getSeconds() + "s");

            int startNode;
            int destNode;
            if (arriveBy) {
                startNode = allQueryResults.get(1).getClosestNode();
                destNode = allQueryResults.get(0).getClosestNode();
            } else {
                startNode = allQueryResults.get(0).getClosestNode();
                destNode = allQueryResults.get(1).getClosestNode();
            }
            List<List<Label.Transition>> solutions = findPaths(startNode, destNode);
            parseSolutionsAndAddToResponse(solutions, points);
            return response;
        }

        private QueryResult findClosest(GHPoint point, int indexForErrorMessage) {
            final EdgeFilter filter = DefaultEdgeFilter.allEdges(graphHopperStorage.getEncodingManager().getEncoder("foot"));
            QueryResult source = locationIndex.findClosest(point.lat, point.lon, filter);
            if (!source.isValid()) {
                throw new PointNotFoundException("Cannot find point: " + point, indexForErrorMessage);
            }
            if (source.getClosestEdge().get(ptEncodedValues.getTypeEnc()) != GtfsStorage.EdgeType.HIGHWAY) {
                throw new RuntimeException(source.getClosestEdge().get(ptEncodedValues.getTypeEnc()).name());
            }
            return source;
        }

        private void parseSolutionsAndAddToResponse(List<List<Label.Transition>> solutions, PointList waypoints) {
            for (List<Label.Transition> solution : solutions) {
                final List<Trip.Leg> legs = tripFromLabel.getTrip(translation, queryGraph, accessEgressWeighting, solution);
                final PathWrapper pathWrapper = tripFromLabel.createPathWrapper(translation, waypoints, legs);
                pathWrapper.setImpossible(solution.stream().anyMatch(t -> t.label.impossible));
                pathWrapper.setTime((solution.get(solution.size() - 1).label.currentTime - solution.get(0).label.currentTime));
                response.add(pathWrapper);
            }
            Comparator<PathWrapper> c = Comparator.comparingInt(p -> (p.isImpossible() ? 1 : 0));
            Comparator<PathWrapper> d = Comparator.comparingDouble(PathWrapper::getTime);
            response.getAll().sort(c.thenComparing(d));
        }

        private List<List<Label.Transition>> findPaths(int startNode, int destNode) {
            StopWatch stopWatch = new StopWatch().start();
            final GraphExplorer accessEgressGraphExplorer = new GraphExplorer(queryGraph, accessEgressWeighting, ptEncodedValues, gtfsStorage, realtimeFeed, !arriveBy, true, walkSpeedKmH, false);
            boolean reverse = !arriveBy;
            GtfsStorage.EdgeType edgeType = reverse ? GtfsStorage.EdgeType.EXIT_PT : GtfsStorage.EdgeType.ENTER_PT;
            MultiCriteriaLabelSetting stationRouter = new MultiCriteriaLabelSetting(accessEgressGraphExplorer, ptEncodedValues, reverse, false, false, false, maxVisitedNodesForRequest, new ArrayList<>());
            stationRouter.setBetaWalkTime(betaWalkTime);
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
            final long smallestStationLabelWeight;
            MultiCriteriaLabelSetting router = new MultiCriteriaLabelSetting(graphExplorer, ptEncodedValues, arriveBy, true, !ignoreTransfers, profileQuery, maxVisitedNodesForRequest, discoveredSolutions);
            router.setBetaTransfers(betaTransfers);
            router.setBetaWalkTime(betaWalkTime);
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
                List<Label.Transition> pathToDestinationStop = Label.getTransitions(originalSolution, arriveBy, ptEncodedValues, queryGraph);
                if (arriveBy) {
                    List<Label.Transition> pathFromStation = Label.getTransitions(reverseSettledSet.get(pathToDestinationStop.get(0).label.adjNode), false, ptEncodedValues, queryGraph);
                    long diff = pathToDestinationStop.get(0).label.currentTime - pathFromStation.get(pathFromStation.size() - 1).label.currentTime;
                    List<Label.Transition> patchedPathFromStation = pathFromStation.stream().map(t -> {
                        return new Label.Transition(new Label(t.label.currentTime + diff, t.label.edge, t.label.adjNode, t.label.nTransfers, t.label.walkDistanceOnCurrentLeg, t.label.departureTime, t.label.walkTime, t.label.residualDelay, t.label.impossible, null), t.edge);
                    }).collect(Collectors.toList());
                    List<Label.Transition> pp = new ArrayList<>(pathToDestinationStop.subList(1, pathToDestinationStop.size()));
                    pp.addAll(0, patchedPathFromStation);
                    paths.add(pp);
                } else {
                    List<Label.Transition> pathFromStation = Label.getTransitions(reverseSettledSet.get(pathToDestinationStop.get(pathToDestinationStop.size() - 1).label.adjNode), true, ptEncodedValues, queryGraph);
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
            response.getHints().put("visited_nodes.sum", visitedNodes);
            response.getHints().put("visited_nodes.average", visitedNodes);
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
