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
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.DefaultWeightingFactory;
import com.graphhopper.routing.WeightingFactory;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.querygraph.VirtualEdgeIteratorState;
import com.graphhopper.routing.util.DefaultSnapFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.*;
import com.graphhopper.util.details.PathDetailsBuilderFactory;
import com.graphhopper.util.exceptions.PointNotFoundException;
import com.graphhopper.util.shapes.GHPoint;

import javax.inject.Inject;
import java.time.Instant;
import java.util.*;

import static java.util.Comparator.comparingLong;

public final class PtRouterFreeWalkImpl implements PtRouter {

    private final GraphHopperConfig config;
    private final TranslationMap translationMap;
    private final PtEncodedValues ptEncodedValues;
    private final Weighting accessEgressWeighting;
    private final GraphHopperStorage graphHopperStorage;
    private final LocationIndex locationIndex;
    private final GtfsStorage gtfsStorage;
    private final RealtimeFeed realtimeFeed;
    private final TripFromLabel tripFromLabel;
    private final WeightingFactory weightingFactory;

    @Inject
    public PtRouterFreeWalkImpl(GraphHopperConfig config, TranslationMap translationMap, GraphHopperStorage graphHopperStorage, LocationIndex locationIndex, GtfsStorage gtfsStorage, RealtimeFeed realtimeFeed, PathDetailsBuilderFactory pathDetailsBuilderFactory) {
        this.config = config;
        this.ptEncodedValues = PtEncodedValues.fromEncodingManager(graphHopperStorage.getEncodingManager());
        this.weightingFactory = new DefaultWeightingFactory(graphHopperStorage, graphHopperStorage.getEncodingManager());
        this.accessEgressWeighting = new FastestWeighting(graphHopperStorage.getEncodingManager().getEncoder("foot"));
        this.translationMap = translationMap;
        this.graphHopperStorage = graphHopperStorage;
        this.locationIndex = locationIndex;
        this.gtfsStorage = gtfsStorage;
        this.realtimeFeed = realtimeFeed;
        this.tripFromLabel = new TripFromLabel(this.graphHopperStorage, this.gtfsStorage, this.realtimeFeed, pathDetailsBuilderFactory);
    }

    public static Factory createFactory(GraphHopperConfig config, TranslationMap translationMap, GraphHopper graphHopperStorage, LocationIndex locationIndex, GtfsStorage gtfsStorage) {
        return new Factory(config, translationMap, graphHopperStorage.getGraphHopperStorage(), locationIndex, gtfsStorage);
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

        private Factory(GraphHopperConfig config, TranslationMap translationMap, GraphHopperStorage graphHopperStorage, LocationIndex locationIndex, GtfsStorage gtfsStorage) {
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
            return new PtRouterFreeWalkImpl(config, translationMap, graphHopperStorage, locationIndex, gtfsStorage, RealtimeFeed.fromProtobuf(graphHopperStorage, gtfsStorage, this.transfers, realtimeFeeds), new PathDetailsBuilderFactory());
        }

        public PtRouter createWithoutRealtimeFeed() {
            return new PtRouterFreeWalkImpl(config, translationMap, graphHopperStorage, locationIndex, gtfsStorage, RealtimeFeed.empty(gtfsStorage), new PathDetailsBuilderFactory());
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
        private final Map<Integer, Long> boardingPenaltiesByRouteType;
        private final GHLocation enter;
        private final GHLocation exit;
        private final Translation translation;
        private final List<String> requestedPathDetails;
        private final List<VirtualEdgeIteratorState> extraEdges = new ArrayList<>(realtimeFeed.getAdditionalEdges());

        private final GHResponse response = new GHResponse();
        private final Graph graphWithExtraEdges = new WrapperGraph(graphHopperStorage, extraEdges);
        private final long limitTripTime;
        private final long limitStreetTime;
        private QueryGraph queryGraph;
        private int visitedNodes;
        private MultiCriteriaLabelSetting router;

        private final Profile accessProfile;
        private final EdgeFilter accessSnapFilter;
        private final Weighting accessWeighting;
        private final Profile egressProfile;
        private final EdgeFilter egressSnapFilter;
        private final Weighting egressWeighting;

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
            boardingPenaltiesByRouteType = request.getBoardingPenaltiesByRouteType();
            translation = translationMap.getWithFallBack(request.getLocale());
            enter = request.getPoints().get(0);
            exit = request.getPoints().get(1);
            limitTripTime = request.getLimitTripTime() != null ? request.getLimitTripTime().toMillis() : Long.MAX_VALUE;
            limitStreetTime = request.getLimitStreetTime() != null ? request.getLimitStreetTime().toMillis() : Long.MAX_VALUE;
            requestedPathDetails = request.getPathDetails();
            accessProfile = config.getProfiles().stream().filter(p -> p.getName().equals(request.getAccessProfile())).findFirst().get();
            accessWeighting = weightingFactory.createWeighting(accessProfile, new PMap(), false);
            accessSnapFilter = new DefaultSnapFilter(new FastestWeighting(graphHopperStorage.getEncodingManager().getEncoder(accessProfile.getVehicle())), graphHopperStorage.getEncodingManager().getBooleanEncodedValue(Subnetwork.key(accessProfile.getVehicle())));
            egressProfile = config.getProfiles().stream().filter(p -> p.getName().equals(request.getEgressProfile())).findFirst().get();
            egressWeighting = weightingFactory.createWeighting(egressProfile, new PMap(), false);
            egressSnapFilter = new DefaultSnapFilter(new FastestWeighting(graphHopperStorage.getEncodingManager().getEncoder(egressProfile.getVehicle())), graphHopperStorage.getEncodingManager().getBooleanEncodedValue(Subnetwork.key(egressProfile.getVehicle())));
        }

        GHResponse route() {
            StopWatch stopWatch = new StopWatch().start();
            ArrayList<Snap> pointSnaps = new ArrayList<>();
            ArrayList<Snap> allSnaps = new ArrayList<>();
            PointList points = new PointList(2, false);
            List<GHLocation> locations = Arrays.asList(enter, exit);
            for (int i = 0; i < locations.size(); i++) {
                GHLocation location = locations.get(i);
                if (location instanceof GHPointLocation) {
                    final Snap closest = findByPoint(((GHPointLocation) location).ghPoint, i, i == 0 ? this.accessSnapFilter : this.egressSnapFilter);
                    pointSnaps.add(closest);
                    allSnaps.add(closest);
                    points.add(closest.getSnappedPoint());
                } else if (location instanceof GHStationLocation) {
                    final Snap station = findByStationId((GHStationLocation) location, i);
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

        private Snap findByPoint(GHPoint point, int indexForErrorMessage, EdgeFilter snapFilter) {
            Snap source = locationIndex.findClosest(point.lat, point.lon, snapFilter);
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
                final ResponsePath responsePath = tripFromLabel.createResponsePath(translation, waypoints, queryGraph, accessWeighting, egressWeighting, solution, requestedPathDetails);
                responsePath.setImpossible(solution.stream().anyMatch(t -> t.label.impossible));
                responsePath.setTime((solution.get(solution.size() - 1).label.currentTime - solution.get(0).label.currentTime));
                responsePath.setRouteWeight(router.weight(solution.get(solution.size() - 1).label));
                response.add(responsePath);
            }
            Comparator<ResponsePath> c = Comparator.comparingInt(p -> (p.isImpossible() ? 1 : 0));
            Comparator<ResponsePath> d = Comparator.comparingDouble(ResponsePath::getTime);
            response.getAll().sort(c.thenComparing(d));
        }

        private List<List<Label.Transition>> findPaths(int startNode, int destNode) {
            StopWatch stopWatch = new StopWatch().start();

            GraphExplorer graphExplorer = new GraphExplorer(queryGraph, accessEgressWeighting, ptEncodedValues, gtfsStorage, realtimeFeed, arriveBy, false, false, walkSpeedKmH, false, blockedRouteTypes);
            List<Label> discoveredSolutions = new ArrayList<>();
            router = new MultiCriteriaLabelSetting(graphExplorer, ptEncodedValues, arriveBy, !ignoreTransfers, profileQuery, maxProfileDuration, discoveredSolutions);
            router.setBetaTransfers(betaTransfers);
            router.setBetaStreetTime(betaStreetTime);
            router.setLimitStreetTime(limitStreetTime);
            router.setBoardingPenaltyByRouteType(routeType -> boardingPenaltiesByRouteType.getOrDefault(routeType, 0L));
            Iterator<Label> iterator = router.calcLabels(startNode, initialTime).iterator();
            while (iterator.hasNext()) {
                Label label = iterator.next();
                visitedNodes++;
                if (visitedNodes >= maxVisitedNodesForRequest) {
                    break;
                }
                if (label.adjNode == destNode) {
                        discoveredSolutions.add(label);
                        if (discoveredSolutions.size() >= limitSolutions) {
                            break;
                        }
                }
            }
            discoveredSolutions.sort(comparingLong(s -> Optional.ofNullable(s.departureTime).orElse(0L)));

            List<List<Label.Transition>> paths = new ArrayList<>();
            for (Label discoveredSolution : discoveredSolutions) {
                List<Label.Transition> path = Label.getTransitions(discoveredSolution, arriveBy, ptEncodedValues, queryGraph, realtimeFeed);
                paths.add(path);
            }

            response.addDebugInfo("routing:" + stopWatch.stop().getSeconds() + "s");
            if (discoveredSolutions.isEmpty() && visitedNodes >= maxVisitedNodesForRequest) {
                response.addError(new IllegalArgumentException("No path found - maximum number of nodes exceeded: " + maxVisitedNodesForRequest));
            }
            response.getHints().putObject("visited_nodes.sum", visitedNodes);
            response.getHints().putObject("visited_nodes.average", visitedNodes);
            if (discoveredSolutions.isEmpty()) {
                response.addError(new RuntimeException("No route found"));
            }
            return paths;
        }

    }

}
