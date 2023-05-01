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
import com.conveyal.gtfs.model.Stop;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.ResponsePath;
import com.graphhopper.Trip;
import com.graphhopper.config.Profile;
import com.graphhopper.gtfs.analysis.Trips;
import com.graphhopper.routing.DefaultWeightingFactory;
import com.graphhopper.routing.WeightingFactory;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.DefaultSnapFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.PMap;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.Translation;
import com.graphhopper.util.TranslationMap;
import com.graphhopper.util.details.PathDetailsBuilderFactory;
import com.graphhopper.util.exceptions.ConnectionNotFoundException;
import com.graphhopper.util.exceptions.MaximumNodesExceededException;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

import javax.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

public final class PtRouterTripBasedImpl implements PtRouter {

    private final GraphHopperConfig config;
    private final TranslationMap translationMap;
    private final BaseGraph baseGraph;
    private final EncodingManager encodingManager;
    private final LocationIndex locationIndex;
    private final GtfsStorage gtfsStorage;
    private final PtGraph ptGraph;
    private final RealtimeFeed realtimeFeed;
    private final PathDetailsBuilderFactory pathDetailsBuilderFactory;
    private final WeightingFactory weightingFactory;
    private final Trips trips;

    @Inject
    public PtRouterTripBasedImpl(GraphHopperConfig config, TranslationMap translationMap, BaseGraph baseGraph, EncodingManager encodingManager, LocationIndex locationIndex, GtfsStorage gtfsStorage, RealtimeFeed realtimeFeed, PathDetailsBuilderFactory pathDetailsBuilderFactory) {
        this.config = config;
        this.weightingFactory = new DefaultWeightingFactory(baseGraph, encodingManager);
        this.translationMap = translationMap;
        this.baseGraph = baseGraph;
        this.encodingManager = encodingManager;
        this.locationIndex = locationIndex;
        this.gtfsStorage = gtfsStorage;
        this.ptGraph = gtfsStorage.getPtGraph();
        this.realtimeFeed = realtimeFeed;
        this.pathDetailsBuilderFactory = pathDetailsBuilderFactory;
        trips = new Trips(gtfsStorage);
    }

    @Override
    public GHResponse route(Request request) {
        return new RequestHandler(request).route();
    }

    public static class Factory {
        private final GraphHopperConfig config;
        private final TranslationMap translationMap;
        private final BaseGraph baseGraph;
        private final EncodingManager encodingManager;
        private final LocationIndex locationIndex;
        private final GtfsStorage gtfsStorage;

        public Factory(GraphHopperConfig config, TranslationMap translationMap, BaseGraph baseGraph, EncodingManager encodingManager, LocationIndex locationIndex, GtfsStorage gtfsStorage) {
            this.config = config;
            this.translationMap = translationMap;
            this.baseGraph = baseGraph;
            this.encodingManager = encodingManager;
            this.locationIndex = locationIndex;
            this.gtfsStorage = gtfsStorage;
        }

        public PtRouter createWithoutRealtimeFeed() {
            return new PtRouterTripBasedImpl(config, translationMap, baseGraph, encodingManager, locationIndex, gtfsStorage, RealtimeFeed.empty(), new PathDetailsBuilderFactory());
        }
    }

    private class RequestHandler {
        private final int maxVisitedNodesForRequest;
        private final int limitSolutions;
        private final Duration maxProfileDuration;
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

        private final GHResponse response = new GHResponse();
        private final long limitTripTime;
        private final long limitStreetTime;
        private QueryGraph queryGraph;
        private int visitedNodes;
        private final Profile accessProfile;
        private final EdgeFilter accessSnapFilter;
        private final Weighting accessWeighting;
        private final Profile egressProfile;
        private final EdgeFilter egressSnapFilter;
        private final Weighting egressWeighting;
        private TripFromLabel tripFromLabel;
        private List<Label> accessStationLabels;
        private List<TripBasedRouter.StopWithTimeDelta> accessStations;
        private List<Label> egressStationLabels;
        private List<TripBasedRouter.StopWithTimeDelta> egressStations;

        RequestHandler(Request request) {
            maxVisitedNodesForRequest = request.getMaxVisitedNodes();
            profileQuery = request.isProfileQuery();
            ignoreTransfers = Optional.ofNullable(request.getIgnoreTransfers()).orElse(false);
            betaTransfers = request.getBetaTransfers();
            betaStreetTime = request.getBetaStreetTime();
            limitSolutions = Optional.ofNullable(request.getLimitSolutions()).orElse(profileQuery ? 50 : ignoreTransfers ? 1 : Integer.MAX_VALUE);
            initialTime = request.getEarliestDepartureTime();
            maxProfileDuration = request.getMaxProfileDuration();
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
            accessProfile = config.getProfiles().stream().filter(p -> p.getName().equals(request.getAccessProfile())).findFirst().get();
            accessWeighting = weightingFactory.createWeighting(accessProfile, new PMap(), false);
            accessSnapFilter = new DefaultSnapFilter(accessWeighting, encodingManager.getBooleanEncodedValue(Subnetwork.key(accessProfile.getVehicle())));
            egressProfile = config.getProfiles().stream().filter(p -> p.getName().equals(request.getEgressProfile())).findFirst().get();
            egressWeighting = weightingFactory.createWeighting(egressProfile, new PMap(), false);
            egressSnapFilter = new DefaultSnapFilter(egressWeighting, encodingManager.getBooleanEncodedValue(Subnetwork.key(egressProfile.getVehicle())));
        }

        GHResponse route() {
            StopWatch stopWatch = new StopWatch().start();
            PtLocationSnapper.Result result = new PtLocationSnapper(baseGraph, locationIndex, gtfsStorage).snapAll(Arrays.asList(enter, exit), Arrays.asList(accessSnapFilter, egressSnapFilter));
            queryGraph = result.queryGraph;
            response.addDebugInfo("idLookup:" + stopWatch.stop().getSeconds() + "s");

            Label.NodeId startNode = result.nodes.get(0);
            Label.NodeId destNode = result.nodes.get(1);

            StopWatch stopWatch1 = new StopWatch().start();

            accessStationLabels = accessEgress(startNode, destNode, false);
            accessStations = accessStationLabels.stream()
                    .map(l -> new TripBasedRouter.StopWithTimeDelta(new GtfsStorage.FeedIdWithStopId("gtfs_0", l.edge.getPlatformDescriptor().stop_id), l.currentTime - initialTime.toEpochMilli()))
                    .collect(Collectors.toList());
            egressStationLabels = accessEgress(startNode, destNode, true);
            egressStations = egressStationLabels.stream()
                    .map(l -> new TripBasedRouter.StopWithTimeDelta(new GtfsStorage.FeedIdWithStopId("gtfs_0", l.edge.getPlatformDescriptor().stop_id), initialTime.toEpochMilli() - l.currentTime))
                    .collect(Collectors.toList());
            response.addDebugInfo("access/egress routing:" + stopWatch1.stop().getSeconds() + "s");

            TripBasedRouter tripBasedRouter = new TripBasedRouter(gtfsStorage, trips);
            List<TripBasedRouter.ResultLabel> routes;
            if (profileQuery) {
                routes = tripBasedRouter.routeNaiveProfile(accessStations, egressStations, initialTime, maxProfileDuration);
            } else {
                routes = tripBasedRouter.route(accessStations, egressStations, initialTime);
            }

            tripFromLabel = new TripFromLabel(queryGraph, encodingManager, gtfsStorage, RealtimeFeed.empty(), pathDetailsBuilderFactory, walkSpeedKmH);
            for (TripBasedRouter.ResultLabel route : routes) {
                ResponsePath responsePath = extractResponse(route, result);
                response.add(responsePath);
            }
            response.getAll().sort(Comparator.comparingLong(ResponsePath::getTime));
            if (ignoreTransfers) {
                Instant bestDepartureTime = Instant.MIN;
                Iterator<ResponsePath> i = response.getAll().iterator();
                while (i.hasNext()) {
                    ResponsePath path = i.next();
                    Instant departureTime = path.getLegs().get(0).getDepartureTime().toInstant();
                    if (!departureTime.isAfter(bestDepartureTime)) {
                        i.remove();
                    } else {
                        bestDepartureTime = departureTime;
                    }
                }
            }
            response.getHints().putObject("visited_nodes.sum", visitedNodes);
            response.getHints().putObject("visited_nodes.average", visitedNodes);
            if (response.getAll().isEmpty()) {
                if (visitedNodes >= maxVisitedNodesForRequest) {
                    response.addError(new MaximumNodesExceededException("No path found - maximum number of nodes exceeded: " + maxVisitedNodesForRequest, maxVisitedNodesForRequest));
                } else {
                    response.addError(new ConnectionNotFoundException("No route found", Collections.emptyMap()));
                }
            }
            return response;
        }

        private List<Label> accessEgress(Label.NodeId startNode, Label.NodeId destNode, boolean isEgress) {
            final GraphExplorer accessEgressGraphExplorer = new GraphExplorer(queryGraph, ptGraph, isEgress ? egressWeighting : accessWeighting, gtfsStorage, realtimeFeed, isEgress, true, false, walkSpeedKmH, false, blockedRouteTypes);
            GtfsStorage.EdgeType edgeType = isEgress ? GtfsStorage.EdgeType.EXIT_PT : GtfsStorage.EdgeType.ENTER_PT;
            MultiCriteriaLabelSetting stationRouter = new MultiCriteriaLabelSetting(accessEgressGraphExplorer, isEgress, false, false, 0, new ArrayList<>());
            stationRouter.setBetaStreetTime(betaStreetTime);
            stationRouter.setLimitStreetTime(limitStreetTime);
            List<Label> stationLabels = new ArrayList<>();
            for (Label label : stationRouter.calcLabels(isEgress ? destNode : startNode, initialTime)) {
                visitedNodes++;
                if (isEgress && label.node.equals(startNode) || !isEgress && label.node.equals(destNode)) {
                    break;
                } else if (label.edge != null && label.edge.getType() == edgeType) {
                    stationLabels.add(label);
                }
            }
            return stationLabels;
        }

        private ResponsePath extractResponse(TripBasedRouter.ResultLabel route, PtLocationSnapper.Result snapResult) {
            GeometryFactory geometryFactory = new GeometryFactory();

            List<TripBasedRouter.EnqueuedTripSegment> segments = new ArrayList<>();
            TripBasedRouter.EnqueuedTripSegment enqueuedTripSegment = route.enqueuedTripSegment;
            while (enqueuedTripSegment != null) {
                segments.add(enqueuedTripSegment);
                enqueuedTripSegment = enqueuedTripSegment.parent;
            }
            Collections.reverse(segments);

            List<Trip.Leg> legs = new ArrayList<>();
            extractAccessLeg(route, snapResult).ifPresent(legs::add);
            String previousBlockId = null;
            for (int i = 0; i < segments.size(); i++) {
                TripBasedRouter.EnqueuedTripSegment segment = segments.get(i);

                GTFSFeed feed = gtfsStorage.getGtfsFeeds().get(segment.tripAtStopTime.feedId);
                com.conveyal.gtfs.model.Trip trip = feed.trips.get(segment.tripAtStopTime.tripDescriptor.getTripId());
                int untilStopSequence;
                if (i == segments.size() - 1)
                    untilStopSequence = route.t.stop_sequence;
                else
                    untilStopSequence = segments.get(i+1).transferOrigin.stop_sequence;
                List<Trip.Stop> stops = trips.trips.get(segment.tripAtStopTime.feedId).get(segment.tripAtStopTime.tripDescriptor).stopTimes.stream().filter(st -> st != null && st.stop_sequence >= segment.tripAtStopTime.stop_sequence && st.stop_sequence <= untilStopSequence)
                        .map(st -> {
                            LocalDate day = initialTime.atZone(ZoneId.of("America/Los_Angeles")).toLocalDate().plusDays(segment.plusDays);
                            Instant departureTime = day.atStartOfDay().plusSeconds(st.departure_time).atZone(ZoneId.of("America/Los_Angeles")).toInstant();
                            Instant arrivalTime = day.atStartOfDay().plusSeconds(st.arrival_time).atZone(ZoneId.of("America/Los_Angeles")).toInstant();;
                            Stop stop = feed.stops.get(st.stop_id);
                            return new Trip.Stop(st.stop_id, st.stop_sequence, stop.stop_name, geometryFactory.createPoint(new Coordinate(stop.stop_lon, stop.stop_lat)), Date.from(arrivalTime), Date.from(arrivalTime), Date.from(arrivalTime), false, Date.from(departureTime), Date.from(departureTime), Date.from(departureTime), false);
                        })
                        .collect(Collectors.toList());
                boolean isInSameVehicleAsPrevious = trip.block_id != null && trip.block_id.equals(previousBlockId);
                legs.add(new Trip.PtLeg(segment.tripAtStopTime.feedId, isInSameVehicleAsPrevious, segment.tripAtStopTime.tripDescriptor.getTripId(),
                        trip.route_id, trip.trip_headsign, stops, 0, stops.get(stops.size() - 1).arrivalTime.toInstant().toEpochMilli() - stops.get(0).departureTime.toInstant().toEpochMilli(), geometryFactory.createLineString(stops.stream().map(s -> s.geometry.getCoordinate()).toArray(Coordinate[]::new))));
                previousBlockId = trip.block_id;
            }
            extractEgressLeg(route, snapResult).ifPresent(legs::add);

            ResponsePath responsePath = TripFromLabel.createResponsePath(gtfsStorage, translation, snapResult.points, legs);
            responsePath.setTime(Duration.between(initialTime,
                    responsePath.getLegs().get(responsePath.getLegs().size() - 1).getArrivalTime().toInstant()).toMillis());
            return responsePath;
        }

        private Optional<Trip.Leg> extractAccessLeg(TripBasedRouter.ResultLabel route, PtLocationSnapper.Result snapResult) {
            Label accessLabel = accessStationLabels.get(accessStations.indexOf(route.getAccessStop()));
            List<Label.Transition> accessTransitions = Label.getTransitions(accessLabel, false);
            List<List<Label.Transition>> accessPartitions = tripFromLabel.parsePathToPartitions(accessTransitions);
            List<Trip.Leg> accessPath = tripFromLabel.parsePartitionToLegs(accessPartitions.get(0), snapResult.queryGraph, encodingManager, accessWeighting, translation, requestedPathDetails);
            if (accessPath.isEmpty()) {
                return Optional.empty();
            } else {
                return Optional.of(accessPath.get(0));
            }
        }

        private Optional<Trip.Leg> extractEgressLeg(TripBasedRouter.ResultLabel route, PtLocationSnapper.Result snapResult) {
            Label egressLabel = egressStationLabels.get(egressStations.indexOf(route.destination));
            List<Label.Transition> egressTransitions = Label.getTransitions(egressLabel, true);
            List<List<Label.Transition>> egressPartitions = tripFromLabel.parsePathToPartitions(egressTransitions);
            if (egressPartitions.size() < 2) {
                return Optional.empty();
            } else {
                List<Trip.Leg> egressPath = tripFromLabel.parsePartitionToLegs(egressPartitions.get(1), snapResult.queryGraph, encodingManager, egressWeighting, translation, requestedPathDetails);
                return Optional.of(egressPath.get(0));
            }
        }
    }

}
