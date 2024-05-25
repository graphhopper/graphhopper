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

import static java.util.Comparator.comparingLong;

import com.conveyal.gtfs.GTFSFeed;
import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.DefaultWeightingFactory;
import com.graphhopper.routing.WeightingFactory;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.ev.RoadEnvironment;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.DefaultSnapFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.SnapPreventionEdgeFilter;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.PMap;
import com.graphhopper.util.PointList;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.Translation;
import com.graphhopper.util.TranslationMap;
import com.graphhopper.util.details.PathDetailsBuilderFactory;
import com.graphhopper.util.exceptions.ConnectionNotFoundException;
import com.graphhopper.util.exceptions.MaximumNodesExceededException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.inject.Inject;

public final class PtRouterImpl implements PtRouter {

    private final GraphHopperConfig config;
    private final TranslationMap translationMap;
    private final GraphHopperStorage graphHopperStorage;
    private final LocationIndex locationIndex;
    private final GtfsStorage gtfsStorage;
    private final PtGraph ptGraph;
    private final RealtimeFeed realtimeFeed;
    private final PathDetailsBuilderFactory pathDetailsBuilderFactory;
    private final WeightingFactory weightingFactory;

    @Inject
    public PtRouterImpl(GraphHopperConfig config, TranslationMap translationMap, GraphHopperStorage graphHopperStorage, LocationIndex locationIndex, GtfsStorage gtfsStorage, RealtimeFeed realtimeFeed, PathDetailsBuilderFactory pathDetailsBuilderFactory) {
        this.config = config;
        this.weightingFactory = new DefaultWeightingFactory(graphHopperStorage.getBaseGraph(), graphHopperStorage.getEncodingManager());
        this.translationMap = translationMap;
        this.graphHopperStorage = graphHopperStorage;
        this.locationIndex = locationIndex;
        this.gtfsStorage = gtfsStorage;
        this.ptGraph = gtfsStorage.getPtGraph();
        this.realtimeFeed = realtimeFeed;
        this.pathDetailsBuilderFactory = pathDetailsBuilderFactory;
    }

    @Override
    public GHResponse route(Request request) {
        return new RequestHandler(request).route();
    }

    public static class Factory {
        private final GraphHopperConfig config;
        private final TranslationMap translationMap;
        private final GraphHopperStorage graphHopperStorage;
        private final LocationIndex locationIndex;
        private final GtfsStorage gtfsStorage;
        private final Map<String, Transfers> transfers;

        public Factory(GraphHopperConfig config, TranslationMap translationMap, GraphHopperStorage graphHopperStorage, LocationIndex locationIndex, GtfsStorage gtfsStorage) {
            this.config = config;
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
            return new PtRouterImpl(config, translationMap, graphHopperStorage, locationIndex, gtfsStorage, RealtimeFeed.fromProtobuf(graphHopperStorage, gtfsStorage, this.transfers, realtimeFeeds), new PathDetailsBuilderFactory());
        }

        public PtRouter createWithoutRealtimeFeed() {
            return new PtRouterImpl(config, translationMap, graphHopperStorage, locationIndex, gtfsStorage, RealtimeFeed.empty(), new PathDetailsBuilderFactory());
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
        private final double betaStreetTime;
        private final double walkSpeedKmH;
        private final int blockedRouteTypes;
        private final Map<Integer, Long> transferPenaltiesByRouteType;
        private final GHLocation enter;
        private final GHLocation exit;
        private final Translation translation;
        private final List<String> requestedPathDetails;
        private boolean includeElevation;
        private boolean includeEdges;

        private final GHResponse response = new GHResponse();
        private final long limitTripTime;
        private final long limitStreetTime;
        private QueryGraph queryGraph;
        private int visitedNodes;
        private MultiCriteriaLabelSetting router;

        private final Profile connectingProfile;
        private final EdgeFilter connectingSnapFilter;
        private final Weighting connectingWeighting;

        RequestHandler(Request request) {
            maxVisitedNodesForRequest = request.getMaxVisitedNodes();
            profileQuery = request.isProfileQuery();
            ignoreTransfers = Optional.ofNullable(request.getIgnoreTransfers()).orElse(request.isProfileQuery());
            betaTransfers = request.getBetaTransfers();
            betaStreetTime = request.getBetaStreetTime();
            limitSolutions = Optional.ofNullable(request.getLimitSolutions()).orElse(profileQuery ? 50 : ignoreTransfers ? 1 : Integer.MAX_VALUE);
            initialTime = request.getEarliestDepartureTime();
            maxProfileDuration = request.getMaxProfileDuration().toMillis();
            arriveBy = request.isArriveBy();
            walkSpeedKmH = request.getWalkSpeedKmH();
            blockedRouteTypes = request.getBlockedRouteTypes();
            transferPenaltiesByRouteType = request.getBoardingPenaltiesByRouteType();
            translation = translationMap.getWithFallBack(request.getLocale());
            enter = request.getPoints().get(0);
            exit = request.getPoints().get(1);
            limitTripTime = request.getLimitTripTime() != null ? request.getLimitTripTime().toMillis() : Long.MAX_VALUE;
            limitStreetTime = request.getLimitStreetTime() != null ? request.getLimitStreetTime().toMillis() : Long.MAX_VALUE;
            requestedPathDetails = request.getPathDetails();
            String connectingProfileName = request.getConnectingProfile() != null ? request.getConnectingProfile() : config.getString("pt.connecting_profile", "foot");
            connectingProfile = config.getProfileByName(connectingProfileName).get();
            connectingWeighting = weightingFactory.createWeighting(connectingProfile, new PMap(), false);
            connectingSnapFilter = makeConnectingSnapFilter(graphHopperStorage,
                    connectingProfile);
            includeElevation = request.getEnableElevation();
            includeEdges = request.getIncludeEdges();
        }

        GHResponse route() {
            StopWatch stopWatch = new StopWatch().start();
            PtLocationSnapper.Result result = new PtLocationSnapper(graphHopperStorage, locationIndex, gtfsStorage).snapAll(Arrays.asList(enter, exit), Arrays.asList(connectingSnapFilter, connectingSnapFilter));
            queryGraph = result.queryGraph;
            response.addDebugInfo("idLookup time", stopWatch.stop().getSeconds());

            Label.NodeId startNode;
            Label.NodeId destNode;
            if (arriveBy) {
                startNode = result.nodes.get(1);
                destNode = result.nodes.get(0);
            } else {
                startNode = result.nodes.get(0);
                destNode = result.nodes.get(1);
            }
            List<List<Label.Transition>> solutions = findPaths(startNode, destNode, response);
            parseSolutionsAndAddToResponse(solutions, result.points);
            return response;
        }

        private void parseSolutionsAndAddToResponse(List<List<Label.Transition>> solutions, PointList waypoints) {
            TripFromLabel tripFromLabel = new TripFromLabel(queryGraph, gtfsStorage, realtimeFeed, pathDetailsBuilderFactory, walkSpeedKmH);
            for (List<Label.Transition> solution : solutions) {
                final ResponsePath responsePath = tripFromLabel.createResponsePath(translation, waypoints, router, queryGraph, connectingWeighting, solution, requestedPathDetails, connectingProfile.getVehicle(), includeElevation, includeEdges);
                responsePath.setImpossible(solution.stream().anyMatch(t -> t.label.impossible));
                responsePath.setRouteWeight(router.weight(solution.get(solution.size() - 1).label));
                response.add(responsePath);
            }
            Comparator<ResponsePath> c = Comparator.comparingInt(p -> (p.isImpossible() ? 1 : 0));
            Comparator<ResponsePath> d = Comparator.comparingDouble(ResponsePath::getTime);
            response.getAll().sort(c.thenComparing(d));
        }

        private List<List<Label.Transition>> findPaths(Label.NodeId startNode, Label.NodeId destNode, GHResponse response) {
            StopWatch stopWatch = new StopWatch().start();
            boolean isEgress = !arriveBy;
            final GraphExplorer accessEgressGraphExplorer = new GraphExplorer(queryGraph, ptGraph, connectingWeighting, gtfsStorage, realtimeFeed, isEgress, true, false, connectingProfile.isBike(), false, blockedRouteTypes);
            GtfsStorage.EdgeType edgeType = isEgress ? GtfsStorage.EdgeType.EXIT_PT : GtfsStorage.EdgeType.ENTER_PT;
            MultiCriteriaLabelSetting stationRouter = new MultiCriteriaLabelSetting(accessEgressGraphExplorer, isEgress, false, false, maxProfileDuration, new ArrayList<>());
            stationRouter.setBetaStreetTime(betaStreetTime);
            stationRouter.setLimitStreetTime(limitStreetTime);
            List<Label> stationLabels = new ArrayList<>();
            for (Label label : stationRouter.calcLabels(destNode, initialTime)) {
                visitedNodes++;
                if (label.node.equals(startNode)) {
                    stationLabels.add(label);
                    break;
                } else if (label.edge != null && label.edge.getType() == edgeType) {
                    stationLabels.add(label);
                }
            }

            Map<Label.NodeId, Label> reverseSettledSet = new HashMap<>();
            for (Label stationLabel : stationLabels) {
                reverseSettledSet.put(stationLabel.node, stationLabel);
            }

            GraphExplorer graphExplorer = new GraphExplorer(queryGraph, ptGraph, connectingWeighting, gtfsStorage, realtimeFeed, arriveBy, false, true, connectingProfile.isBike(), false, blockedRouteTypes);
            List<Label> discoveredSolutions = new ArrayList<>();
            router = new MultiCriteriaLabelSetting(graphExplorer, arriveBy, !ignoreTransfers, profileQuery, maxProfileDuration, discoveredSolutions);
            router.setBetaTransfers(betaTransfers);
            router.setBetaStreetTime(betaStreetTime);
            router.setBoardingPenaltyByRouteType(routeType -> transferPenaltiesByRouteType.getOrDefault(routeType, 0L));
            final long smallestStationLabelWalkTime = stationLabels.stream()
                    .mapToLong(l -> l.streetTime).min()
                    .orElse(Long.MAX_VALUE);
            router.setLimitTripTime(Math.max(0, limitTripTime - smallestStationLabelWalkTime));
            router.setLimitStreetTime(Math.max(0, limitStreetTime - smallestStationLabelWalkTime));
            final double smallestStationLabelWeight;
            if (!stationLabels.isEmpty()) {
                smallestStationLabelWeight = stationRouter.weight(stationLabels.get(0));
            } else {
                smallestStationLabelWeight = Double.MAX_VALUE;
            }
            Map<Label, Label> forwardSolutions = new HashMap<>();
            Map<Label, Label> backwardSolutions = new HashMap<>();

            Label accessEgressModeOnlySolution = null;
            double highestWeightForDominationTest = Double.MAX_VALUE;
            for (Label label : router.calcLabels(startNode, initialTime)) {
                visitedNodes++;
                if (visitedNodes >= maxVisitedNodesForRequest) {
                    break;
                }
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
                if ((!profileQuery || profileFinished(router, discoveredSolutions, accessEgressModeOnlySolution)) && router.weight(label) + smallestStationLabelWeight > highestWeightForDominationTest) {
                    break;
                }
                Label reverseLabel = reverseSettledSet.get(label.node);
                if (reverseLabel != null) {
                    Label combinedSolution = new Label(label.edgeWeight + reverseLabel.edgeWeight, label.currentTime - reverseLabel.currentTime + initialTime.toEpochMilli(), null, label.node, label.nTransfers + reverseLabel.nTransfers, label.departureTime, label.streetTime + reverseLabel.streetTime, label.extraWeight + reverseLabel.extraWeight, 0, label.impossible, null);
                    Predicate<Label> filter;
                    if (profileQuery && combinedSolution.departureTime != null)
                        filter = targetLabel -> (!arriveBy ? router.prc(combinedSolution, targetLabel) : router.rprc(combinedSolution, targetLabel));
                    else
                        filter = tagetLabel -> true;
                    if (router.isNotDominatedByAnyOf(combinedSolution, discoveredSolutions, filter)) {
                        router.removeDominated(combinedSolution, discoveredSolutions, filter);
                        List<Label> closedSolutions = discoveredSolutions.stream().filter(s -> router.weight(s) < router.weight(label) + smallestStationLabelWeight).collect(Collectors.toList());
                        if (closedSolutions.size() >= limitSolutions) continue;
                        if (profileQuery && combinedSolution.departureTime != null && (combinedSolution.departureTime - initialTime.toEpochMilli()) * (arriveBy ? -1L : 1L) > maxProfileDuration && closedSolutions.size() > 0 && closedSolutions.get(closedSolutions.size() - 1).departureTime != null && (closedSolutions.get(closedSolutions.size() - 1).departureTime - initialTime.toEpochMilli()) * (arriveBy ? -1L : 1L) > maxProfileDuration) {
                            continue;
                        }
                        discoveredSolutions.add(combinedSolution);
                        discoveredSolutions.sort(comparingLong(s -> Optional.ofNullable(s.departureTime).orElse(0L)));
                        forwardSolutions.put(combinedSolution, label);
                        backwardSolutions.put(combinedSolution, reverseLabel);
                        if (label.nTransfers == 0 && reverseLabel.nTransfers == 0) {
                            accessEgressModeOnlySolution = combinedSolution;
                        }
                        if (profileQuery) {
                            highestWeightForDominationTest = discoveredSolutions.stream().mapToDouble(router::weight).max().orElse(Double.MAX_VALUE);
                            if (accessEgressModeOnlySolution != null && discoveredSolutions.size() < limitSolutions) {
                                // If we have a walk solution, we have it at every point in time in the profile.
                                // (I can start walking any time I want, unlike with bus departures.)
                                // Here we virtually add it to the end of the profile, so it acts as a sentinel
                                // to remind us that we still have to search that far to close the set.
                                highestWeightForDominationTest = Math.max(highestWeightForDominationTest, router.weight(accessEgressModeOnlySolution) + maxProfileDuration);
                            }
                        } else {
                            highestWeightForDominationTest = discoveredSolutions.stream().filter(s -> !s.impossible && (ignoreTransfers || s.nTransfers <= 1)).mapToDouble(router::weight).min().orElse(Double.MAX_VALUE);
                        }
                    }
                }
            }

            List<List<Label.Transition>> paths = new ArrayList<>();
            for (Label discoveredSolution : discoveredSolutions) {
                Label forwardSolution = forwardSolutions.get(discoveredSolution);
                Label backwardSolution = backwardSolutions.get(discoveredSolution);
                long diff = forwardSolution.currentTime - backwardSolution.currentTime;
                List<Label.Transition> pathToDestinationStop = Label.getTransitions(forwardSolution, arriveBy);
                List<Label.Transition> pathFromStation = Label.getTransitions(backwardSolution, !arriveBy);
                if (arriveBy) {
                    // TODO: check if weights here are calculated correctly (probably not)
                    List<Label.Transition> patchedPathFromStation = pathFromStation.stream().map(t -> {
                        return new Label.Transition(new Label(t.label.edgeWeight, t.label.currentTime + diff, t.label.edge, t.label.node, t.label.nTransfers, t.label.departureTime, t.label.streetTime, t.label.extraWeight, t.label.residualDelay, t.label.impossible, null), t.edge);
                    }).collect(Collectors.toList());
                    List<Label.Transition> pp = new ArrayList<>(pathToDestinationStop.subList(1, pathToDestinationStop.size()));
                    pp.addAll(0, patchedPathFromStation);
                    paths.add(pp);
                } else {
                    List<Label.Transition> patchedPathFromStation = pathFromStation.stream().map(t -> {
                        return new Label.Transition(new Label(forwardSolution.edgeWeight + backwardSolution.edgeWeight - t.label.edgeWeight, t.label.currentTime + diff, t.label.edge, t.label.node, forwardSolution.nTransfers + backwardSolution.nTransfers- t.label.nTransfers, t.label.departureTime, forwardSolution.streetTime + backwardSolution.streetTime - t.label.streetTime, forwardSolution.extraWeight + backwardSolution.extraWeight - t.label.extraWeight, t.label.residualDelay, t.label.impossible, null), t.edge);
                    }).collect(Collectors.toList());
                    List<Label.Transition> pp = new ArrayList<>(pathToDestinationStop);
                    pp.addAll(patchedPathFromStation.subList(1, pathFromStation.size()));
                    paths.add(pp);
                }
            }

            response.addDebugInfo("routing time",stopWatch.stop().getSeconds());
            if (visitedNodes >= maxVisitedNodesForRequest) {
                response.addError(new MaximumNodesExceededException("Maximum number of nodes exceeded: " + maxVisitedNodesForRequest, maxVisitedNodesForRequest));
            }
            response.getHints().putObject("visited_nodes.sum", visitedNodes);
            response.getHints().putObject("visited_nodes.average", visitedNodes);
            if (discoveredSolutions.isEmpty()) {
                response.addError(new ConnectionNotFoundException("No route found", Collections.emptyMap()));
            }
            return paths;
        }

        private boolean profileFinished(MultiCriteriaLabelSetting router, List<Label> discoveredSolutions, Label walkSolution) {
            return discoveredSolutions.size() >= limitSolutions ||
                    (!discoveredSolutions.isEmpty() && router.departureTimeSinceStartTime(discoveredSolutions.get(discoveredSolutions.size() - 1)) != null && router.departureTimeSinceStartTime(discoveredSolutions.get(discoveredSolutions.size() - 1)) > maxProfileDuration) ||
                    walkSolution != null;
            // Imagine we can always add the walk solution again to the end of the list (it can start any time).
            // In turn, we must also think of this virtual walk solution in the other test (where we check if all labels are closed).
        }

        private EdgeFilter makeConnectingSnapFilter(
                GraphHopperStorage ghStorage, Profile connectingProfile) {
            EdgeFilter snapFilter = new DefaultSnapFilter(new FastestWeighting(
                    ghStorage.getEncodingManager()
                            .getEncoder(connectingProfile.getVehicle())),
                    ghStorage.getEncodingManager()
                            .getBooleanEncodedValue(Subnetwork.key(
                                    connectingProfile.getVehicle())));
            if (connectingProfile.getHints().has("snap_preventions")) {
                List<String> snapPreventions = List.of(
                        connectingProfile.getHints()
                                .getString("snap_preventions", "").split(","));
                EncodingManager encodingManager =
                        graphHopperStorage.getEncodingManager();
                final EnumEncodedValue<RoadClass> roadClassEnc = encodingManager.getEnumEncodedValue(
                        RoadClass.KEY, RoadClass.class);
                final EnumEncodedValue<RoadEnvironment> roadEnvEnc = encodingManager.getEnumEncodedValue(
                        RoadEnvironment.KEY, RoadEnvironment.class);
                return new SnapPreventionEdgeFilter(snapFilter, roadClassEnc,
                        roadEnvEnc, snapPreventions);
            }
            return snapFilter;
        }
    }
}
