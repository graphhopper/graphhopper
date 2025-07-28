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
import com.graphhopper.*;
import com.graphhopper.config.Profile;
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
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class PtRouterTripBasedImpl implements PtRouter {

    private final GraphHopperConfig config;
    private final TranslationMap translationMap;
    private final BaseGraph baseGraph;
    private final EncodingManager encodingManager;
    private final LocationIndex locationIndex;
    private final GtfsStorage gtfsStorage;
    private final PtGraph ptGraph;
    private final PathDetailsBuilderFactory pathDetailsBuilderFactory;
    private final WeightingFactory weightingFactory;
    private final Map<String, ZoneId> feedZoneIds = new ConcurrentHashMap<>(); // ad-hoc cache for timezone field of gtfs feed
    private final GraphHopper graphHopper;

    @Inject
    public PtRouterTripBasedImpl(GraphHopper graphHopper, GraphHopperConfig config, TranslationMap translationMap, BaseGraph baseGraph, EncodingManager encodingManager, LocationIndex locationIndex, GtfsStorage gtfsStorage, PathDetailsBuilderFactory pathDetailsBuilderFactory) {
        this.graphHopper = graphHopper;
        this.config = config;
        this.weightingFactory = new DefaultWeightingFactory(baseGraph, encodingManager);
        this.translationMap = translationMap;
        this.baseGraph = baseGraph;
        this.encodingManager = encodingManager;
        this.locationIndex = locationIndex;
        this.gtfsStorage = gtfsStorage;
        this.ptGraph = gtfsStorage.getPtGraph();
        this.pathDetailsBuilderFactory = pathDetailsBuilderFactory;
    }

    @Override
    public GHResponse route(Request request) {
        return new RequestHandler(request).route();
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
        private final double betaAccessTime;
        private final double betaEgressTime;
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
        private Label walkDestLabel;
        private List<Label> egressStationLabels;
        private List<TripBasedRouter.StopWithTimeDelta> egressStations;
        private ResponsePath walkResponsePath;

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
            betaAccessTime = request.getBetaAccessTime();
            accessWeighting = weightingFactory.createWeighting(accessProfile, new PMap(), false);
            accessSnapFilter = new DefaultSnapFilter(accessWeighting, encodingManager.getBooleanEncodedValue(Subnetwork.key(accessProfile.getName())));
            egressProfile = config.getProfiles().stream().filter(p -> p.getName().equals(request.getEgressProfile())).findFirst().get();
            betaEgressTime = request.getBetaEgressTime();
            egressWeighting = weightingFactory.createWeighting(egressProfile, new PMap(), false);
            egressSnapFilter = new DefaultSnapFilter(egressWeighting, encodingManager.getBooleanEncodedValue(Subnetwork.key(egressProfile.getName())));
        }

        GHResponse route() {
            StopWatch stopWatch = new StopWatch().start();
            PtLocationSnapper.Result result = new PtLocationSnapper(baseGraph, locationIndex, gtfsStorage).snapAll(Arrays.asList(enter, exit), Arrays.asList(accessSnapFilter, egressSnapFilter));
            queryGraph = result.queryGraph;
            response.addDebugInfo("idLookup:" + stopWatch.stop().getSeconds() + "s");

            Label.NodeId startNode = result.nodes.get(0);
            Label.NodeId destNode = result.nodes.get(1);

            StopWatch stopWatch1 = new StopWatch().start();

            accessStationLabels = access(startNode, destNode);
            accessStations = accessStationLabels.stream()
                    .map(l -> stopWithTimeDelta(l.edge.getPlatformDescriptor(), l.currentTime - initialTime.toEpochMilli()))
                    .collect(Collectors.toList());
            egressStationLabels = egress(startNode, destNode);
            egressStations = egressStationLabels.stream()
                    .map(l -> stopWithTimeDelta(l.edge.getPlatformDescriptor(), initialTime.toEpochMilli() - l.currentTime))
                    .collect(Collectors.toList());
            response.addDebugInfo("access/egress routing:" + stopWatch1.stop().getSeconds() + "s");

            TripBasedRouter tripBasedRouter = new TripBasedRouter(gtfsStorage, gtfsStorage.tripTransfers);
            List<TripBasedRouter.ResultLabel> routes;
            routes = tripBasedRouter.routeNaiveProfileWithNaiveBetas(new TripBasedRouter.Parameters(accessStations, egressStations, initialTime, maxProfileDuration, trip -> (blockedRouteTypes & (1 << trip.routeType)) == 0, betaAccessTime, betaEgressTime, betaTransfers, transferPenaltiesByRouteType));

            tripFromLabel = new TripFromLabel(queryGraph, encodingManager, gtfsStorage, RealtimeFeed.empty(), pathDetailsBuilderFactory, walkSpeedKmH);
            if (walkDestLabel != null) {
                List<Label.Transition> walkTransitions = Label.getTransitions(walkDestLabel, false);
                List<List<Label.Transition>> walkPartitions = tripFromLabel.parsePathToPartitions(walkTransitions);
                List<Trip.Leg> walkPath = tripFromLabel.parsePartitionToLegs(walkPartitions.get(0), result.queryGraph, encodingManager, accessWeighting, translation, requestedPathDetails);
                walkResponsePath = TripFromLabel.createResponsePath(gtfsStorage, translation, result.points, walkPath);
                walkResponsePath.setRouteWeight(walkResponsePath.getTime() * betaAccessTime);
                response.add(walkResponsePath);
            }
            for (TripBasedRouter.ResultLabel route : routes) {
                ResponsePath responsePath = extractResponse(route, result);
                if (walkResponsePath != null) {
                    Duration waitTimeBeforeDeparture = Duration.between(initialTime, responsePath.getLegs().get(0).getDepartureTime().toInstant());
                    Duration timeBetweenArrivals = Duration.between(responsePath.getLegs().get(responsePath.getLegs().size()-1).getArrivalTime().toInstant(), walkResponsePath.getLegs().get(0).getArrivalTime().toInstant());
                    double gapBetweenArrivals = responsePath.getRouteWeight() - walkResponsePath.getRouteWeight();
                    Instant earliestDepartureTimeWhereResponseMightBeBetterThanWalking = responsePath.getLegs().get(0).getDepartureTime().toInstant().minus((long) gapBetweenArrivals, ChronoUnit.MILLIS);
                    Instant endOfProfile = initialTime.plus(maxProfileDuration);
                    if (earliestDepartureTimeWhereResponseMightBeBetterThanWalking.isAfter(endOfProfile)) {
                        continue;
                    }
                }
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

        private List<Label> access(Label.NodeId startNode, Label.NodeId destNode) {
            final GraphExplorer accessEgressGraphExplorer = new GraphExplorer(queryGraph, ptGraph, accessWeighting, gtfsStorage, RealtimeFeed.empty(), false, true, false, walkSpeedKmH, false, blockedRouteTypes);
            MultiCriteriaLabelSetting stationRouter = new MultiCriteriaLabelSetting(accessEgressGraphExplorer, false, false, false, 0, new ArrayList<>());
            stationRouter.setBetaStreetTime(betaStreetTime);
            stationRouter.setLimitStreetTime(limitStreetTime);
            List<Label> stationLabels = new ArrayList<>();
            for (Label label : stationRouter.calcLabels(startNode, initialTime)) {
                visitedNodes++;
                if (label.node.equals(destNode)) {
                    walkDestLabel = label;
                    break;
                } else if (label.edge != null && label.edge.getType() == GtfsStorage.EdgeType.ENTER_PT) {
                    stationLabels.add(label);
                }
            }
            return stationLabels;
        }

        private List<Label> egress(Label.NodeId startNode, Label.NodeId destNode) {
            final GraphExplorer accessEgressGraphExplorer = new GraphExplorer(queryGraph, ptGraph, egressWeighting, gtfsStorage, RealtimeFeed.empty(), true, true, false, walkSpeedKmH, false, blockedRouteTypes);
            MultiCriteriaLabelSetting stationRouter = new MultiCriteriaLabelSetting(accessEgressGraphExplorer, true, false, false, 0, new ArrayList<>());
            stationRouter.setBetaStreetTime(betaStreetTime);
            stationRouter.setLimitStreetTime(limitStreetTime);
            List<Label> stationLabels = new ArrayList<>();
            for (Label label : stationRouter.calcLabels(destNode, initialTime)) {
                visitedNodes++;
                if (label.node.equals(startNode)) {
                    break;
                } else if (label.edge != null && label.edge.getType() == GtfsStorage.EdgeType.EXIT_PT) {
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

            long routeWeight = 0;
            List<Trip.Leg> legs = new ArrayList<>();
            Optional<Trip.Leg> maybeAccessLeg = extractAccessLeg(route, snapResult);
            if (maybeAccessLeg.isPresent()) {
                Trip.Leg accessLeg = maybeAccessLeg.get();
                legs.add(accessLeg);
                routeWeight += (accessLeg.getArrivalTime().getTime() - accessLeg.getDepartureTime().getTime()) * betaAccessTime;
            }
            String previousBlockId = null;
            for (int i = 0; i < segments.size(); i++) {
                TripBasedRouter.EnqueuedTripSegment segment = segments.get(i);
                GTFSFeed feed = gtfsStorage.getGtfsFeeds().get(segment.tripPointer.feedId);
                ZoneId zoneId = ZoneId.of(feed.agency.values().stream().findFirst().get().agency_timezone);
                LocalDate day = segment.serviceDay;
                com.conveyal.gtfs.model.Trip trip = segment.tripPointer.trip;
                int untilStopSequence;
                if (i == segments.size() - 1)
                    untilStopSequence = route.stopTime;
                else
                    untilStopSequence = segments.get(i+1).transferOrigin.stop_sequence;
                List<Trip.Stop> stops = segment.tripPointer.stopTimes.stream().filter(st -> st != null && st.stop_sequence >= segment.tripAtStopTime.stop_sequence && st.stop_sequence <= untilStopSequence)
                        .map(st -> {
                            Instant departureTime = day.atStartOfDay().plusSeconds(st.departure_time).atZone(zoneId).toInstant();
                            Instant arrivalTime = day.atStartOfDay().plusSeconds(st.arrival_time).atZone(zoneId).toInstant();
                            Stop stop = feed.stops.get(st.stop_id);
                            return new Trip.Stop(st.stop_id, st.stop_sequence, stop.stop_name, geometryFactory.createPoint(new Coordinate(stop.stop_lon, stop.stop_lat)), Date.from(arrivalTime), Date.from(arrivalTime), Date.from(arrivalTime), false, Date.from(departureTime), Date.from(departureTime), Date.from(departureTime), false);
                        })
                        .collect(Collectors.toList());
                boolean isInSameVehicleAsPrevious = trip.block_id != null && trip.block_id.equals(previousBlockId);
                if (segment.transferOrigin != null) {
                    GtfsStorage.FeedIdWithStopId stopA = new GtfsStorage.FeedIdWithStopId(segment.parent.tripPointer.feedId, segment.parent.tripPointer.stopTimes.get(segment.transferOrigin.stop_sequence).stop_id);
                    GtfsStorage.FeedIdWithStopId stopB = new GtfsStorage.FeedIdWithStopId(segment.tripPointer.feedId, segment.tripPointer.stopTimes.get(segment.tripAtStopTime.stop_sequence).stop_id);
                    List<Trip.Stop> previousStops = ((Trip.PtLeg) legs.get(legs.size() - 1)).stops;
                    gtfsStorage.interpolatedTransfers.get(stopA).stream().filter(it -> it.toPlatformDescriptor.equals(stopB)).findAny().ifPresent(it -> {
                        List<Label.Transition> transferTransitions = tripFromLabel.transferPath(it.skippedEdgesForTransfer, egressWeighting, previousStops.get(previousStops.size() - 1).arrivalTime.toInstant().toEpochMilli());
                        List<Trip.Leg> transferLegs = tripFromLabel.parsePartitionToLegs(transferTransitions, queryGraph, encodingManager, egressWeighting, translation, requestedPathDetails);
                        legs.add(transferLegs.get(0));
                    });
                }
                long travelTime = stops.get(stops.size() - 1).arrivalTime.toInstant().toEpochMilli() - stops.get(0).departureTime.toInstant().toEpochMilli();
                legs.add(new Trip.PtLeg(segment.tripPointer.feedId, isInSameVehicleAsPrevious, segment.tripPointer.trip.trip_id,
                        trip.route_id, trip.trip_headsign, stops, 0, travelTime, geometryFactory.createLineString(stops.stream().map(s -> s.geometry.getCoordinate()).toArray(Coordinate[]::new))));
                routeWeight += travelTime;
                routeWeight += transferPenaltiesByRouteType.getOrDefault(segment.tripPointer.routeType, 0L);
                previousBlockId = trip.block_id;
            }
            Optional<Trip.Leg> maybeEgressLeg = extractEgressLeg(route, snapResult);
            if (maybeEgressLeg.isPresent()) {
                Trip.Leg egressLeg = maybeEgressLeg.get();
                legs.add(egressLeg);
                routeWeight += (egressLeg.getArrivalTime().getTime() - egressLeg.getDepartureTime().getTime()) * betaEgressTime;
            }

            ResponsePath responsePath = TripFromLabel.createResponsePath(gtfsStorage, translation, snapResult.points, legs);
            Duration duration = Duration.between(initialTime, responsePath.getLegs().get(responsePath.getLegs().size() - 1).getArrivalTime().toInstant());
            responsePath.setTime(duration.toMillis());
            Duration waitTimeBeforeDeparture = Duration.between(initialTime, responsePath.getLegs().get(0).getDepartureTime().toInstant());
            routeWeight += waitTimeBeforeDeparture.toMillis();
            for (int i = 1; i < responsePath.getLegs().size(); i++) {
                Duration waitTimeBeforeLeg = Duration.between(responsePath.getLegs().get(i - 1).getArrivalTime().toInstant(), responsePath.getLegs().get(i).getDepartureTime().toInstant());
                routeWeight += waitTimeBeforeLeg.toMillis();
            }
            responsePath.setRouteWeight(routeWeight);
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

    private TripBasedRouter.StopWithTimeDelta stopWithTimeDelta(GtfsStorage.PlatformDescriptor platformDescriptor, long timeDelta) {
        ZoneId zoneId = feedZoneIds.computeIfAbsent(platformDescriptor.feed_id, feedId -> ZoneId.of(gtfsStorage.getGtfsFeeds().get(feedId).agency.values().stream().findFirst().get().agency_timezone));
        return new TripBasedRouter.StopWithTimeDelta(new GtfsStorage.FeedIdWithStopId(platformDescriptor.feed_id, platformDescriptor.stop_id), zoneId, timeDelta);
    }

}
