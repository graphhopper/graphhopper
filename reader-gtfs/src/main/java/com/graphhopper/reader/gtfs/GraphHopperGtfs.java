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

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Transfer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.GHResponse;
import com.graphhopper.PathWrapper;
import com.graphhopper.Trip;
import com.graphhopper.http.WebHelper;
import com.graphhopper.reader.osm.OSMReader;
import com.graphhopper.routing.QueryGraph;
import com.graphhopper.routing.VirtualEdgeIteratorState;
import com.graphhopper.routing.subnetwork.PrepareRoutingSubnetworks;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.*;
import com.graphhopper.util.exceptions.PointNotFoundException;
import com.graphhopper.util.shapes.GHPoint;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

@Path("route")
public final class GraphHopperGtfs {

    public static class Factory {
        private final TranslationMap translationMap;
        private final PtFlagEncoder flagEncoder;
        private final GraphHopperStorage graphHopperStorage;
        private final LocationIndex locationIndex;
        private final GtfsStorage gtfsStorage;

        private Factory(PtFlagEncoder flagEncoder, TranslationMap translationMap, GraphHopperStorage graphHopperStorage, LocationIndex locationIndex, GtfsStorage gtfsStorage) {
            this.flagEncoder = flagEncoder;
            this.translationMap = translationMap;
            this.graphHopperStorage = graphHopperStorage;
            this.locationIndex = locationIndex;
            this.gtfsStorage = gtfsStorage;
        }

        public GraphHopperGtfs createWith(GtfsRealtime.FeedMessage realtimeFeed) {
            Map<String, GtfsRealtime.FeedMessage> realtimeFeeds = new HashMap<>();
            realtimeFeeds.put("gtfs_0", realtimeFeed);
            return new GraphHopperGtfs(flagEncoder, translationMap, graphHopperStorage, locationIndex, gtfsStorage, RealtimeFeed.fromProtobuf(graphHopperStorage, gtfsStorage, flagEncoder, realtimeFeeds));
        }

        public GraphHopperGtfs createWithoutRealtimeFeed() {
            return new GraphHopperGtfs(flagEncoder, translationMap, graphHopperStorage, locationIndex, gtfsStorage, RealtimeFeed.empty(gtfsStorage));
        }
    }

    public static Factory createFactory(PtFlagEncoder flagEncoder, TranslationMap translationMap, GraphHopperStorage graphHopperStorage, LocationIndex locationIndex, GtfsStorage gtfsStorage) {
        return new Factory(flagEncoder, translationMap, graphHopperStorage, locationIndex, gtfsStorage);
    }

    private final TranslationMap translationMap;
    private final PtFlagEncoder flagEncoder;
    private final Weighting accessEgressWeighting;
    private final GraphHopperStorage graphHopperStorage;
    private final LocationIndex locationIndex;
    private final GtfsStorage gtfsStorage;
    private final RealtimeFeed realtimeFeed;
    private final TripFromLabel tripFromLabel;

    private class RequestHandler {
        private final int maxVisitedNodesForRequest;
        private final int limitSolutions;
        private final Instant initialTime;
        private final boolean profileQuery;
        private final boolean arriveBy;
        private final boolean ignoreTransfers;
        private final double betaTransfers;
        private final double betaWalkTime;
        private final double walkSpeedKmH;
        private final double maxWalkDistancePerLeg;
        private final int blockedRouteTypes;
        private final GHLocation enter;
        private final GHLocation exit;
        private final Translation translation;
        private final List<VirtualEdgeIteratorState> extraEdges = new ArrayList<>(realtimeFeed.getAdditionalEdges());

        private final GHResponse response = new GHResponse();
        private final Graph graphWithExtraEdges = new WrapperGraph(graphHopperStorage, extraEdges);
        private QueryGraph queryGraph = new QueryGraph(graphWithExtraEdges);
        private GraphExplorer graphExplorer;
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
            maxWalkDistancePerLeg = request.getMaxWalkDistancePerLeg();
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
            queryGraph.lookup(pointQueryResults); // modifies queryGraph and queryResults!
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
            if (source.getClosestEdge().get(flagEncoder.getTypeEnc()) != GtfsStorage.EdgeType.HIGHWAY) {
                throw new RuntimeException(source.getClosestEdge().get(flagEncoder.getTypeEnc()).name());
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
            final GraphExplorer accessEgressGraphExplorer = new GraphExplorer(queryGraph, accessEgressWeighting, flagEncoder, gtfsStorage, realtimeFeed, !arriveBy, true, walkSpeedKmH);
            boolean reverse = !arriveBy;
            GtfsStorage.EdgeType edgeType = reverse ? GtfsStorage.EdgeType.EXIT_PT : GtfsStorage.EdgeType.ENTER_PT;
            MultiCriteriaLabelSetting stationRouter = new MultiCriteriaLabelSetting(accessEgressGraphExplorer, flagEncoder, reverse, maxWalkDistancePerLeg, false, false, false, maxVisitedNodesForRequest, new ArrayList<>());
            stationRouter.setBetaWalkTime(betaWalkTime);
            Iterator<Label> stationIterator = stationRouter.calcLabels(destNode, startNode, initialTime, blockedRouteTypes).iterator();
            List<Label> stationLabels = new ArrayList<>();
            while (stationIterator.hasNext()) {
                Label label = stationIterator.next();
                if (label.adjNode == startNode) {
                    stationLabels.add(label);
                    break;
                } else if (label.edge != -1 && queryGraph.getEdgeIteratorState(label.edge, label.parent.adjNode).get(flagEncoder.getTypeEnc()) == edgeType) {
                    stationLabels.add(label);
                }
            }
            visitedNodes += stationRouter.getVisitedNodes();

            Map<Integer, Label> reverseSettledSet = new HashMap<>();
            for (Label stationLabel : stationLabels) {
                reverseSettledSet.put(stationLabel.adjNode, stationLabel);
            }

            graphExplorer = new GraphExplorer(queryGraph, accessEgressWeighting, flagEncoder, gtfsStorage, realtimeFeed, arriveBy, false, walkSpeedKmH);
            List<Label> discoveredSolutions = new ArrayList<>();
            final long smallestStationLabelWeight;
            MultiCriteriaLabelSetting router = new MultiCriteriaLabelSetting(graphExplorer, flagEncoder, arriveBy, maxWalkDistancePerLeg, true, !ignoreTransfers, profileQuery, maxVisitedNodesForRequest, discoveredSolutions);
            router.setBetaTransfers(betaTransfers);
            router.setBetaWalkTime(betaWalkTime);
            if (!stationLabels.isEmpty()) {
                smallestStationLabelWeight = stationRouter.weight(stationLabels.get(0));
            } else {
                smallestStationLabelWeight = Long.MAX_VALUE;
            }
            Iterator<Label> iterator = router.calcLabels(startNode, destNode, initialTime, blockedRouteTypes).iterator();
            Map<Label, Label> originalSolutions = new HashMap<>();

            long highestWeightForDominationTest = Long.MAX_VALUE;
            while (iterator.hasNext()) {
                Label label = iterator.next();
                final long weight = router.weight(label);
                if ((!profileQuery || discoveredSolutions.size() >= limitSolutions) && weight + smallestStationLabelWeight > highestWeightForDominationTest) {
                    break;
                }
                Label reverseLabel = reverseSettledSet.get(label.adjNode);
                if (reverseLabel != null) {
                    Label combinedSolution = new Label(label.currentTime - reverseLabel.currentTime + initialTime.toEpochMilli(), -1, label.adjNode, label.nTransfers + reverseLabel.nTransfers, label.nWalkDistanceConstraintViolations + reverseLabel.nWalkDistanceConstraintViolations, label.walkDistanceOnCurrentLeg + reverseLabel.walkDistanceOnCurrentLeg, label.departureTime, label.walkTime + reverseLabel.walkTime, 0, label.impossible, null);
                    if (router.isNotDominatedByAnyOf(combinedSolution, discoveredSolutions)) {
                        router.removeDominated(combinedSolution, discoveredSolutions);
                        if (discoveredSolutions.size() < limitSolutions) {
                            discoveredSolutions.add(combinedSolution);
                            originalSolutions.put(combinedSolution, label);
                            if (profileQuery) {
                                highestWeightForDominationTest = router.weight(discoveredSolutions.get(discoveredSolutions.size() - 1));
                            } else {
                                highestWeightForDominationTest = discoveredSolutions.stream().filter(s -> !s.impossible && (ignoreTransfers || s.nTransfers <= 1)).mapToLong(router::weight).min().orElse(Long.MAX_VALUE);
                            }
                        }
                    }
                }
            }

            List<List<Label.Transition>> pathsToStations = discoveredSolutions.stream()
                    .map(originalSolutions::get)
                    .map(l -> tripFromLabel.getTransitions(arriveBy, flagEncoder, queryGraph, l)).collect(Collectors.toList());

            List<List<Label.Transition>> paths = pathsToStations.stream().map(p -> {
                if (arriveBy) {
                    List<Label.Transition> pp = new ArrayList<>(p.subList(1, p.size()));
                    List<Label.Transition> pathFromStation = pathFromStation(reverseSettledSet.get(p.get(0).label.adjNode));
                    long diff = p.get(0).label.currentTime - pathFromStation.get(pathFromStation.size() - 1).label.currentTime;
                    List<Label.Transition> patchedPathFromStation = pathFromStation.stream().map(t -> {
                        return new Label.Transition(new Label(t.label.currentTime + diff, t.label.edge, t.label.adjNode, t.label.nTransfers, t.label.nWalkDistanceConstraintViolations, t.label.walkDistanceOnCurrentLeg, t.label.departureTime, t.label.walkTime, t.label.residualDelay, t.label.impossible, null), t.edge);
                    }).collect(Collectors.toList());
                    pp.addAll(0, patchedPathFromStation);
                    return pp;
                } else {
                    List<Label.Transition> pp = new ArrayList<>(p);
                    List<Label.Transition> pathFromStation = pathFromStation(reverseSettledSet.get(p.get(p.size() - 1).label.adjNode));
                    long diff = p.get(p.size() - 1).label.currentTime - pathFromStation.get(0).label.currentTime;
                    List<Label.Transition> patchedPathFromStation = pathFromStation.subList(1, pathFromStation.size()).stream().map(t -> {
                        return new Label.Transition(new Label(t.label.currentTime + diff, t.label.edge, t.label.adjNode, t.label.nTransfers, t.label.nWalkDistanceConstraintViolations, t.label.walkDistanceOnCurrentLeg, t.label.departureTime, t.label.walkTime, t.label.residualDelay, t.label.impossible, null), t.edge);
                    }).collect(Collectors.toList());
                    pp.addAll(patchedPathFromStation);
                    return pp;
                }
            }).collect(Collectors.toList());

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

        private List<Label.Transition> pathFromStation(Label l) {
            return tripFromLabel.getTransitions(!arriveBy, flagEncoder, queryGraph, l);
        }
    }

    @Inject
    public GraphHopperGtfs(PtFlagEncoder flagEncoder, TranslationMap translationMap, GraphHopperStorage graphHopperStorage, LocationIndex locationIndex, GtfsStorage gtfsStorage, RealtimeFeed realtimeFeed) {
        this.flagEncoder = flagEncoder;
        this.accessEgressWeighting = new FastestWeighting(graphHopperStorage.getEncodingManager().getEncoder("foot"));
        this.translationMap = translationMap;
        this.graphHopperStorage = graphHopperStorage;
        this.locationIndex = locationIndex;
        this.gtfsStorage = gtfsStorage;
        this.realtimeFeed = realtimeFeed;
        this.tripFromLabel = new TripFromLabel(this.gtfsStorage, this.realtimeFeed);
    }

    public static GtfsStorage createGtfsStorage() {
        return new GtfsStorage();
    }

    public static GHDirectory createGHDirectory(String graphHopperFolder) {
        return new GHDirectory(graphHopperFolder, DAType.RAM_STORE);
    }

    public static TranslationMap createTranslationMap() {
        return new TranslationMap().doImport();
    }

    public static GraphHopperStorage createOrLoad(GHDirectory directory, EncodingManager encodingManager, PtFlagEncoder ptFlagEncoder, GtfsStorage gtfsStorage, Collection<String> gtfsFiles, Collection<String> osmFiles) {
        GraphHopperStorage graphHopperStorage = new GraphHopperStorage(directory, encodingManager, false, gtfsStorage);
        if (graphHopperStorage.loadExisting()) {
            return graphHopperStorage;
        } else {
            graphHopperStorage.create(1000);
            for (String osmFile : osmFiles) {
                OSMReader osmReader = new OSMReader(graphHopperStorage);
                osmReader.setFile(new File(osmFile));
                osmReader.setCreateStorage(false);
                try {
                    osmReader.readGraph();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            new PrepareRoutingSubnetworks(graphHopperStorage, Collections.singletonList(encodingManager.getEncoder("foot"))).doWork();

            int id = 0;
            for (String gtfsFile : gtfsFiles) {
                try {
                    ((GtfsStorage) graphHopperStorage.getExtension()).loadGtfsFromFile("gtfs_" + id++, new ZipFile(gtfsFile));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            LocationIndex walkNetworkIndex;
            if (graphHopperStorage.getNodes() > 0) {
                walkNetworkIndex = new LocationIndexTree(graphHopperStorage, new RAMDirectory()).prepareIndex();
            } else {
                walkNetworkIndex = new EmptyLocationIndex();
            }
            GraphHopperGtfs graphHopperGtfs = new GraphHopperGtfs(ptFlagEncoder, createTranslationMap(), graphHopperStorage, walkNetworkIndex, gtfsStorage, RealtimeFeed.empty(gtfsStorage));
            for (int i = 0; i < id; i++) {
                GTFSFeed gtfsFeed = gtfsStorage.getGtfsFeeds().get("gtfs_" + i);
                GtfsReader gtfsReader = new GtfsReader("gtfs_" + i, graphHopperStorage, gtfsStorage, ptFlagEncoder, walkNetworkIndex);
                gtfsReader.connectStopsToStreetNetwork();
                graphHopperGtfs.getType0TransferWithTimes(gtfsFeed)
                        .forEach(t -> {
                            t.transfer.transfer_type = 2;
                            t.transfer.min_transfer_time = (int) (t.time / 1000L);
                            gtfsFeed.transfers.put(t.id, t.transfer);
                        });
                try {
                    gtfsReader.buildPtNetwork();
                } catch (Exception e) {
                    throw new RuntimeException("Error while constructing transit network. Is your GTFS file valid? Please check log for possible causes.", e);
                }
            }
            graphHopperStorage.flush();
            return graphHopperStorage;
        }
    }


    public static LocationIndex createOrLoadIndex(GHDirectory directory, GraphHopperStorage graphHopperStorage) {
        final EdgeFilter filter = DefaultEdgeFilter.allEdges(graphHopperStorage.getEncodingManager().getEncoder("foot"));
        Graph walkNetwork = GraphSupport.filteredView(graphHopperStorage, filter);
        LocationIndex locationIndex = new LocationIndexTree(walkNetwork, directory);
        if (!locationIndex.loadExisting()) {
            locationIndex.prepareIndex();
        }
        return locationIndex;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public ObjectNode route(@QueryParam("point") List<GHLocation> requestPoints,
                            @QueryParam(Parameters.PT.EARLIEST_DEPARTURE_TIME) String departureTimeString,
                            @QueryParam("locale") String localeStr,
                            @QueryParam(Parameters.PT.IGNORE_TRANSFERS) Boolean ignoreTransfers,
                            @QueryParam(Parameters.PT.PROFILE_QUERY) Boolean profileQuery,
                            @QueryParam(Parameters.PT.LIMIT_SOLUTIONS) Integer limitSolutions) {

        if (departureTimeString == null) {
            throw new BadRequestException(String.format(Locale.ROOT, "Illegal value for required parameter %s: [%s]", Parameters.PT.EARLIEST_DEPARTURE_TIME, departureTimeString));
        }
        Instant departureTime;
        try {
            departureTime = Instant.parse(departureTimeString);
        } catch (DateTimeParseException e) {
            throw new BadRequestException(String.format(Locale.ROOT, "Illegal value for required parameter %s: [%s]", Parameters.PT.EARLIEST_DEPARTURE_TIME, departureTimeString));
        }

        Request request = new Request(requestPoints, departureTime);
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

    private class TransferWithTime {
        public String id;
        Transfer transfer;
        long time;
    }

    private Stream<TransferWithTime> getType0TransferWithTimes(GTFSFeed gtfsFeed) {
        return gtfsFeed.transfers.entrySet()
                .parallelStream()
                .filter(e -> e.getValue().transfer_type == 0)
                .map(e -> {
                    PointList points = new PointList(2, false);
                    final int fromnode = gtfsStorage.getStationNodes().get(e.getValue().from_stop_id);
                    final QueryResult fromstation = new QueryResult(graphHopperStorage.getNodeAccess().getLat(fromnode), graphHopperStorage.getNodeAccess().getLon(fromnode));
                    fromstation.setClosestNode(fromnode);
                    points.add(graphHopperStorage.getNodeAccess().getLat(fromnode), graphHopperStorage.getNodeAccess().getLon(fromnode));

                    final int tonode = gtfsStorage.getStationNodes().get(e.getValue().to_stop_id);
                    final QueryResult tostation = new QueryResult(graphHopperStorage.getNodeAccess().getLat(tonode), graphHopperStorage.getNodeAccess().getLon(tonode));
                    tostation.setClosestNode(tonode);
                    points.add(graphHopperStorage.getNodeAccess().getLat(tonode), graphHopperStorage.getNodeAccess().getLon(tonode));

                    QueryGraph queryGraph = new QueryGraph(graphHopperStorage);
                    queryGraph.lookup(Collections.emptyList());
                    final GraphExplorer graphExplorer = new GraphExplorer(queryGraph, accessEgressWeighting, flagEncoder, gtfsStorage, realtimeFeed, false, true, 5.0);

                    MultiCriteriaLabelSetting router = new MultiCriteriaLabelSetting(graphExplorer, flagEncoder, false, Double.MAX_VALUE, false, false, false, Integer.MAX_VALUE, new ArrayList<>());
                    Iterator<Label> iterator = router.calcLabels(fromnode, tonode, Instant.ofEpochMilli(0), 0).iterator();
                    Label solution = null;
                    while (iterator.hasNext()) {
                        Label label = iterator.next();
                        if (tonode == label.adjNode) {
                            solution = label;
                            break;
                        }
                    }
                    if (solution == null) {
                        throw new RuntimeException("Can't find a transfer walk route.");
                    }
                    TransferWithTime transferWithTime = new TransferWithTime();
                    transferWithTime.id = e.getKey();
                    transferWithTime.transfer = e.getValue();
                    transferWithTime.time = solution.currentTime;
                    return transferWithTime;
                });
    }

}
