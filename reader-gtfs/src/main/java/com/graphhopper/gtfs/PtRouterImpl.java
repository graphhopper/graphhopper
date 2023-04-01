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
import com.google.common.cache.LoadingCache;
import com.google.transit.realtime.GtfsRealtime;
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
import com.graphhopper.util.*;
import com.graphhopper.util.details.PathDetailsBuilderFactory;
import com.graphhopper.util.exceptions.ConnectionNotFoundException;
import com.graphhopper.util.exceptions.MaximumNodesExceededException;

import javax.inject.Inject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

public final class PtRouterImpl implements PtRouter {

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

    @Inject
    public PtRouterImpl(GraphHopperConfig config, TranslationMap translationMap, BaseGraph baseGraph, EncodingManager encodingManager, LocationIndex locationIndex, GtfsStorage gtfsStorage, RealtimeFeed realtimeFeed, PathDetailsBuilderFactory pathDetailsBuilderFactory) {
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

//        for (GTFSFeed gtfsFeed : gtfsStorage.getGtfsFeeds().values()) {
//            patterns.addAll(gtfsFeed.findPatterns().values());
//        }
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

        public PtRouter createWith(GtfsRealtime.FeedMessage realtimeFeed) {
            Map<String, GtfsRealtime.FeedMessage> realtimeFeeds = new HashMap<>();
            realtimeFeeds.put("gtfs_0", realtimeFeed);
            Map<String, Transfers> transfers = new HashMap<>();
            for (Map.Entry<String, GTFSFeed> entry : this.gtfsStorage.getGtfsFeeds().entrySet()) {
                transfers.put(entry.getKey(), new Transfers(entry.getValue()));
            }
            return new PtRouterImpl(config, translationMap, baseGraph, encodingManager, locationIndex, gtfsStorage, RealtimeFeed.fromProtobuf(gtfsStorage, transfers, realtimeFeeds), new PathDetailsBuilderFactory());
        }

        public PtRouter createWithoutRealtimeFeed() {
            return new PtRouterImpl(config, translationMap, baseGraph, encodingManager, locationIndex, gtfsStorage, RealtimeFeed.empty(), new PathDetailsBuilderFactory());
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
        private boolean filter = false;

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
            accessProfile = config.getProfiles().stream().filter(p -> p.getName().equals(request.getAccessProfile())).findFirst().get();
            accessWeighting = weightingFactory.createWeighting(accessProfile, new PMap(), false);
            accessSnapFilter = new DefaultSnapFilter(accessWeighting, encodingManager.getBooleanEncodedValue(Subnetwork.key(accessProfile.getVehicle())));
            egressProfile = config.getProfiles().stream().filter(p -> p.getName().equals(request.getEgressProfile())).findFirst().get();
            egressWeighting = weightingFactory.createWeighting(egressProfile, new PMap(), false);
            egressSnapFilter = new DefaultSnapFilter(egressWeighting, encodingManager.getBooleanEncodedValue(Subnetwork.key(egressProfile.getVehicle())));
            filter = request.isFilter();
        }

        GHResponse route() {
            StopWatch stopWatch = new StopWatch().start();
            PtLocationSnapper.Result result = new PtLocationSnapper(baseGraph, locationIndex, gtfsStorage).snapAll(Arrays.asList(enter, exit), Arrays.asList(accessSnapFilter, egressSnapFilter));
            queryGraph = result.queryGraph;
            response.addDebugInfo("idLookup:" + stopWatch.stop().getSeconds() + "s");

            Label.NodeId startNode = result.nodes.get(0);
            Label.NodeId destNode = result.nodes.get(1);

            StopWatch stopWatch1 = new StopWatch().start();

            List<TripBasedRouter.StopWithTimeDelta> accessStations = accessEgress(startNode, destNode, false).stream()
                    .map(l -> new TripBasedRouter.StopWithTimeDelta(new GtfsStorage.FeedIdWithStopId("gtfs_0", l.edge.getPlatformDescriptor().stop_id), l.currentTime - initialTime.toEpochMilli()))
                    .collect(Collectors.toList());
            List<TripBasedRouter.StopWithTimeDelta> egressStations = accessEgress(startNode, destNode, true).stream()
                    .map(l -> new TripBasedRouter.StopWithTimeDelta(new GtfsStorage.FeedIdWithStopId("gtfs_0", l.edge.getPlatformDescriptor().stop_id), initialTime.toEpochMilli() - l.currentTime))
                    .collect(Collectors.toList());
            response.addDebugInfo("access/egress routing:" + stopWatch1.stop().getSeconds() + "s");


            TripBasedRouter tripBasedRouter = new TripBasedRouter(gtfsStorage);
            List<TripBasedRouter.ResultLabel> routes = tripBasedRouter.route(accessStations, egressStations, initialTime);
            for (TripBasedRouter.ResultLabel route : routes) {

                List<TripBasedRouter.EnqueuedTripSegment> segments = new ArrayList<>();
                System.out.println("===");
                TripBasedRouter.EnqueuedTripSegment enqueuedTripSegment = route.enqueuedTripSegment;
                while (enqueuedTripSegment != null) {
                    segments.add(enqueuedTripSegment);
                    enqueuedTripSegment = enqueuedTripSegment.parent;
                }
                Collections.reverse(segments);

                ResponsePath responsePath = new ResponsePath();
                for (int i = 0; i < segments.size(); i++) {
                    TripBasedRouter.EnqueuedTripSegment segment = segments.get(i);

                    GTFSFeed feed = gtfsStorage.getGtfsFeeds().get(segment.tripAtStopTime.feedId);
                    com.conveyal.gtfs.model.Trip trip = feed.trips.get(segment.tripAtStopTime.tripDescriptor.getTripId());
                    int untilStopSequence;
                    if (i == segments.size() - 1)
                        untilStopSequence = route.t.stop_sequence;
                    else
                        untilStopSequence = Integer.MAX_VALUE;
                    List<Trip.Stop> stops = feed.stopTimes.getUnchecked(segment.tripAtStopTime.tripDescriptor).stopTimes.stream().filter(st -> st.stop_sequence >= segment.tripAtStopTime.stop_sequence && st.stop_sequence <= untilStopSequence)
                            .map(st -> {
                                LocalDate day = initialTime.atZone(ZoneId.of("America/Los_Angeles")).toLocalDate().plusDays(segment.plusDays);
                                LocalTime departureLocalTime = LocalTime.ofSecondOfDay(st.departure_time);
                                Instant departureTime = departureLocalTime.atDate(day).atZone(ZoneId.of("America/Los_Angeles")).toInstant();
                                LocalTime arrivalLocalTime = LocalTime.ofSecondOfDay(st.arrival_time);
                                Instant arrivalTime = arrivalLocalTime.atDate(day).atZone(ZoneId.of("America/Los_Angeles")).toInstant();
                                System.out.println(st.stop_id + " " + departureLocalTime);
                                Stop stop = feed.stops.get(st.stop_id);
                                return new Trip.Stop(st.stop_id, st.stop_sequence, stop.stop_name, null, Date.from(arrivalTime), Date.from(arrivalTime), Date.from(arrivalTime), false, Date.from(departureTime), Date.from(departureTime), Date.from(departureTime), false);
                            })
                            .collect(Collectors.toList());
                    responsePath.getLegs().add(new Trip.PtLeg(segment.tripAtStopTime.feedId, false, segment.tripAtStopTime.tripDescriptor.getTripId(),
                            trip.route_id, trip.trip_headsign, stops, 0, 0, null));
                }
                List<Trip.Stop> stops = ((Trip.PtLeg) responsePath.getLegs().get(responsePath.getLegs().size() - 1)).stops;
                responsePath.setTime(stops.get(stops.size()-1).arrivalTime.toInstant().toEpochMilli() - initialTime.toEpochMilli());
                response.add(responsePath);
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
            MultiCriteriaLabelSetting stationRouter = new MultiCriteriaLabelSetting(accessEgressGraphExplorer, isEgress, false, false, maxProfileDuration, new ArrayList<>());
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

    }

}
