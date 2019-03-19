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
import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.*;
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

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import static com.graphhopper.util.Parameters.PT.PROFILE_QUERY;

public final class GraphHopperGtfs implements GraphHopperAPI {

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

        public GraphHopperGtfs createWith(GtfsRealtime.FeedMessage realtimeFeed, String agencyId) {
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
        private final GHPoint enter;
        private final GHPoint exit;
        private final Translation translation;
        private final List<VirtualEdgeIteratorState> extraEdges = new ArrayList<>(realtimeFeed.getAdditionalEdges());

        private final GHResponse response = new GHResponse();
        private final Graph graphWithExtraEdges = new WrapperGraph(graphHopperStorage, extraEdges);
        private QueryGraph queryGraph = new QueryGraph(graphWithExtraEdges);
        private GraphExplorer graphExplorer;
        private int visitedNodes;

        RequestHandler(GHRequest request) {
            maxVisitedNodesForRequest = request.getHints().getInt(Parameters.Routing.MAX_VISITED_NODES, 1_000_000);
            profileQuery = request.getHints().getBool(PROFILE_QUERY, false);
            ignoreTransfers = request.getHints().getBool(Parameters.PT.IGNORE_TRANSFERS, profileQuery);
            betaTransfers = request.getHints().getDouble("beta_transfers", 0.0);
            betaWalkTime = request.getHints().getDouble("beta_walk_time", 1.0);
            limitSolutions = request.getHints().getInt(Parameters.PT.LIMIT_SOLUTIONS, profileQuery ? 5 : ignoreTransfers ? 1 : Integer.MAX_VALUE);
            final String departureTimeString = request.getHints().get(Parameters.PT.EARLIEST_DEPARTURE_TIME, "");
            try {
                initialTime = Instant.parse(departureTimeString);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException(String.format(Locale.ROOT, "Illegal value for required parameter %s: [%s]", Parameters.PT.EARLIEST_DEPARTURE_TIME, departureTimeString));
            }
            arriveBy = request.getHints().getBool(Parameters.PT.ARRIVE_BY, false);
            walkSpeedKmH = request.getHints().getDouble(Parameters.PT.WALK_SPEED, 5.0);
            blockedRouteTypes = request.getHints().getInt(Parameters.PT.BLOCKED_ROUTE_TYPES, 0);
            translation = translationMap.getWithFallBack(request.getLocale());
            if (request.getPoints().size() != 2) {
                throw new IllegalArgumentException("Exactly 2 points have to be specified, but was:" + request.getPoints().size());
            }
            enter = request.getPoints().get(0);
            exit = request.getPoints().get(1);
            maxWalkDistancePerLeg = request.getHints().getDouble(Parameters.PT.MAX_WALK_DISTANCE_PER_LEG, Integer.MAX_VALUE);
        }

        GHResponse route() {
            StopWatch stopWatch = new StopWatch().start();

            ArrayList<QueryResult> allQueryResults = new ArrayList<>();

            QueryResult source = findClosest(enter, 0);
            QueryResult dest = findClosest(exit, 1);
            allQueryResults.add(source);
            allQueryResults.add(dest);
            queryGraph.lookup(Arrays.asList(source, dest)); // modifies queryGraph, source and dest!

            PointList startAndEndpoint = pointListFrom(Arrays.asList(source, dest));
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
            parseSolutionsAndAddToResponse(solutions, startAndEndpoint);
            return response;
        }

        private QueryResult findClosest(GHPoint point, int indexForErrorMessage) {
            final EdgeFilter filter = DefaultEdgeFilter.allEdges(graphHopperStorage.getEncodingManager().getEncoder("foot"));
            QueryResult source = locationIndex.findClosest(point.lat, point.lon, filter);
            if (!source.isValid()) {
                throw new PointNotFoundException("Cannot find point: " + point, indexForErrorMessage);
            }
            if (flagEncoder.getEdgeType(source.getClosestEdge()) != GtfsStorage.EdgeType.HIGHWAY) {
                throw new RuntimeException(flagEncoder.getEdgeType(source.getClosestEdge()).name());
            }
            return source;
        }

        private void parseSolutionsAndAddToResponse(List<List<Label.Transition>> solutions, PointList waypoints) {
            for (List<Label.Transition> solution : solutions) {
                final List<Trip.Leg> legs = tripFromLabel.getTrip(translation, graphExplorer, accessEgressWeighting, solution);
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
            final GraphExplorer accessEgressGraphExplorer = new GraphExplorer(queryGraph, accessEgressWeighting, flagEncoder, gtfsStorage, realtimeFeed, !arriveBy, extraEdges, true, walkSpeedKmH);
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
                } else if (label.edge != -1 && flagEncoder.getEdgeType(accessEgressGraphExplorer.getEdgeIteratorState(label.edge, label.parent.adjNode)) == edgeType) {
                    stationLabels.add(label);
                }
            }
            visitedNodes += stationRouter.getVisitedNodes();

            Map<Integer, Label> reverseSettledSet = new HashMap<>();
            for (Label stationLabel : stationLabels) {
                reverseSettledSet.put(stationLabel.adjNode, stationLabel);
            }

            graphExplorer = new GraphExplorer(queryGraph, accessEgressWeighting, flagEncoder, gtfsStorage, realtimeFeed, arriveBy, extraEdges, false, walkSpeedKmH);
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
                    .map(l -> new TripFromLabel(gtfsStorage, realtimeFeed).getTransitions(arriveBy, flagEncoder, graphExplorer, l)).collect(Collectors.toList());

            List<List<Label.Transition>> paths = pathsToStations.stream().map(p -> {
                if (arriveBy) {
                    List<Label.Transition> pp = new ArrayList<>(p.subList(1, p.size()));
                    List<Label.Transition> pathFromStation = pathFromStation(accessEgressGraphExplorer, reverseSettledSet.get(p.get(0).label.adjNode));
                    long diff = p.get(0).label.currentTime - pathFromStation.get(pathFromStation.size() - 1).label.currentTime;
                    List<Label.Transition> patchedPathFromStation = pathFromStation.stream().map(t -> {
                        return new Label.Transition(new Label(t.label.currentTime + diff, t.label.edge, t.label.adjNode, t.label.nTransfers, t.label.nWalkDistanceConstraintViolations, t.label.walkDistanceOnCurrentLeg, t.label.departureTime, t.label.walkTime, t.label.residualDelay, t.label.impossible, null), t.edge);
                    }).collect(Collectors.toList());
                    pp.addAll(0, patchedPathFromStation);
                    return pp;
                } else {
                    List<Label.Transition> pp = new ArrayList<>(p);
                    List<Label.Transition> pathFromStation = pathFromStation(accessEgressGraphExplorer, reverseSettledSet.get(p.get(p.size() - 1).label.adjNode));
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

        private List<Label.Transition> pathFromStation(GraphExplorer accessEgressGraphExplorer, Label l) {
            return new TripFromLabel(gtfsStorage, realtimeFeed).getTransitions(!arriveBy, flagEncoder, accessEgressGraphExplorer, l);
        }
    }

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

    public boolean load(String graphHopperFolder) {
        throw new IllegalStateException("We are always loaded, or we wouldn't exist.");
    }

    @Override
    public GHResponse route(GHRequest request) {
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
                    final GraphExplorer graphExplorer = new GraphExplorer(queryGraph, accessEgressWeighting, flagEncoder, gtfsStorage, realtimeFeed, false, Collections.emptyList(), true, 5.0);

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

    private PointList pointListFrom(List<QueryResult> queryResults) {
        PointList waypoints = new PointList(queryResults.size(), true);
        for (QueryResult qr : queryResults) {
            waypoints.add(qr.getSnappedPoint());
        }
        return waypoints;
    }

}
