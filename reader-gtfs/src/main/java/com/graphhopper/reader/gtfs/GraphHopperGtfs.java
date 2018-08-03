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
        private final boolean separateWalkQuery;
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
        private final Map<Integer, PathWrapper> walkPaths = new HashMap<>();

        private final GHResponse response = new GHResponse();
        private final Graph graphWithExtraEdges = new WrapperGraph(graphHopperStorage, extraEdges);
        private QueryGraph queryGraph = new QueryGraph(graphWithExtraEdges);
        private GraphExplorer graphExplorer;
        private int realDestinationNode;
        private int visitedNodes;

        RequestHandler(GHRequest request) {
            maxVisitedNodesForRequest = request.getHints().getInt(Parameters.Routing.MAX_VISITED_NODES, 1_000_000);
            profileQuery = request.getHints().getBool(PROFILE_QUERY, false);
            ignoreTransfers = request.getHints().getBool(Parameters.PT.IGNORE_TRANSFERS, profileQuery);
            betaTransfers = request.getHints().getDouble("beta_transfers", 0.0);
            betaWalkTime = request.getHints().getDouble("beta_walk_time", 2.0);
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
            separateWalkQuery = true;
            // Maximum walking distance is unlimited by default, _except_ when we search for access/egress from origin
            // and destination separately (currently in profile queries). Because there we need to know when to stop.
            maxWalkDistancePerLeg = request.getHints().getDouble(Parameters.PT.MAX_WALK_DISTANCE_PER_LEG, separateWalkQuery ? 1000.0 : Double.MAX_VALUE);
        }

        GHResponse route() {
            StopWatch stopWatch = new StopWatch().start();

            ArrayList<QueryResult> allQueryResults = new ArrayList<>();

            QueryResult source = findClosest(enter, 0);
            QueryResult dest = findClosest(exit, 1);
            allQueryResults.add(source);
            allQueryResults.add(dest);
            queryGraph.lookup(Arrays.asList(source, dest)); // modifies queryGraph, source and dest!

            realDestinationNode = allQueryResults.get(1).getClosestNode();
            PointList startAndEndpoint = pointListFrom(Arrays.asList(source, dest));
            response.addDebugInfo("idLookup:" + stopWatch.stop().getSeconds() + "s");

            if (separateWalkQuery) {
                substitutePointWithVirtualNode(0, false, enter, allQueryResults);
                substitutePointWithVirtualNode(1, true, exit, allQueryResults);
                final int originNode = allQueryResults.get(0).getClosestNode();
                final int destinationNode = allQueryResults.get(1).getClosestNode();
                PathWrapper directWalkPath = walkPaths.get(realDestinationNode);
                if (directWalkPath != null) {
                    int nextEdgeId = graphWithExtraEdges.getAllEdges().length() + 200; // FIXME: A number bigger than the number of edges QueryGraph adds
                    final VirtualEdgeIteratorState newEdge = new VirtualEdgeIteratorState(-1,
                            nextEdgeId++, originNode, destinationNode, directWalkPath.getDistance(), 0, "", directWalkPath.getPoints());
                    final VirtualEdgeIteratorState reverseNewEdge = new VirtualEdgeIteratorState(-1,
                            nextEdgeId++, destinationNode, originNode, directWalkPath.getDistance(), 0, "", directWalkPath.getPoints());
                    newEdge.setFlags(flagEncoder.setEdgeType(newEdge.getFlags(), GtfsStorage.EdgeType.HIGHWAY));
                    final long time = directWalkPath.getTime() / 1000;
                    newEdge.setFlags(flagEncoder.setTime(newEdge.getFlags(), time));
                    newEdge.setFlags(flagEncoder.setAccess(newEdge.getFlags(), true, false));
                    newEdge.setFlags(accessEgressWeighting.getFlagEncoder().setAccess(newEdge.getFlags(), true, false));
                    newEdge.setFlags(accessEgressWeighting.getFlagEncoder().setSpeed(newEdge.getFlags(), 5.0));

                    reverseNewEdge.setFlags(newEdge.getFlags());
                    newEdge.setReverseEdge(reverseNewEdge);
                    reverseNewEdge.setReverseEdge(newEdge);
                    newEdge.setDistance(directWalkPath.getDistance());
                    extraEdges.add(newEdge);
                    extraEdges.add(reverseNewEdge);
                }
            }

            int startNode;
            int destNode;
            if (arriveBy) {
                startNode = allQueryResults.get(1).getClosestNode();
                destNode = allQueryResults.get(0).getClosestNode();
            } else {
                startNode = allQueryResults.get(0).getClosestNode();
                destNode = allQueryResults.get(1).getClosestNode();
            }
            List<Label> solutions = findPaths(startNode, destNode);
            parseSolutionsAndAddToResponse(solutions, startAndEndpoint);
            return response;
        }

        private void substitutePointWithVirtualNode(int index, boolean reverse, GHPoint ghPoint, ArrayList<QueryResult> allQueryResults) {
            final GraphExplorer graphExplorer = new GraphExplorer(queryGraph, accessEgressWeighting, flagEncoder, gtfsStorage, realtimeFeed, reverse, extraEdges, true, walkSpeedKmH);
            int nextNodeId = graphWithExtraEdges.getNodes() + 2 + index; // FIXME: A number bigger than the number of nodes QueryGraph adds
            int nextEdgeId = graphWithExtraEdges.getAllEdges().length() + 100; // FIXME: A number bigger than the number of edges QueryGraph adds

            final List<Label> stationNodes = findStationNodes(graphExplorer, allQueryResults.get(index).getClosestNode(), reverse, allQueryResults.get(1-index).getClosestNode());
            for (Label stationNode : stationNodes) {
                final PathWrapper pathWrapper = stationNode.parent.parent != null ?
                        tripFromLabel.parseSolutionIntoPath(reverse, flagEncoder, translation, graphExplorer, accessEgressWeighting, stationNode.parent, new PointList()) :
                        new PathWrapper();
                final VirtualEdgeIteratorState newEdge = new VirtualEdgeIteratorState(stationNode.edge,
                        nextEdgeId++, reverse ? stationNode.adjNode : nextNodeId, reverse ? nextNodeId : stationNode.adjNode, pathWrapper.getDistance(), 0, "", pathWrapper.getPoints());
                final VirtualEdgeIteratorState reverseNewEdge = new VirtualEdgeIteratorState(stationNode.edge,
                        nextEdgeId++, reverse ? nextNodeId : stationNode.adjNode, reverse ? stationNode.adjNode : nextNodeId, pathWrapper.getDistance(), 0, "", pathWrapper.getPoints());
                newEdge.setFlags(flagEncoder.setEdgeType(newEdge.getFlags(), reverse ? GtfsStorage.EdgeType.EXIT_PT : GtfsStorage.EdgeType.ENTER_PT));
                final long time = pathWrapper.getTime() / 1000;
                newEdge.setFlags(flagEncoder.setTime(newEdge.getFlags(), time));
                newEdge.setFlags(flagEncoder.setValidityId(newEdge.getFlags(), flagEncoder.getValidityId(graphExplorer.getEdgeIteratorState(stationNode.edge, Integer.MIN_VALUE).getFlags())));
                reverseNewEdge.setFlags(newEdge.getFlags());
                newEdge.setReverseEdge(reverseNewEdge);
                reverseNewEdge.setReverseEdge(newEdge);
                newEdge.setDistance(pathWrapper.getDistance());
                extraEdges.add(newEdge);
                extraEdges.add(reverseNewEdge);
                walkPaths.put(stationNode.adjNode, pathWrapper);
            }

            final QueryResult virtualNode = new QueryResult(ghPoint.getLat(), ghPoint.getLon());
            virtualNode.setClosestNode(nextNodeId);
            allQueryResults.set(index, virtualNode);
        }

        private List<Label> findStationNodes(GraphExplorer graphExplorer, int node, boolean reverse, int otherNode) {
            GtfsStorage.EdgeType edgeType = reverse ? GtfsStorage.EdgeType.EXIT_PT : GtfsStorage.EdgeType.ENTER_PT;
            MultiCriteriaLabelSetting router = new MultiCriteriaLabelSetting(graphExplorer, flagEncoder, reverse, maxWalkDistancePerLeg, false, false, false, maxVisitedNodesForRequest);
            final Stream<Label> labels = router.calcLabels(node, -1, initialTime, blockedRouteTypes);
            List<Label> stationNodes = labels
                    .peek(current -> {
                        if (!reverse && current.adjNode == otherNode) {
                            final PathWrapper pathWrapper = current.parent != null ?
                                    tripFromLabel.parseSolutionIntoPath(reverse, flagEncoder, translation, graphExplorer, accessEgressWeighting, current, new PointList()) :
                                    new PathWrapper();
                            walkPaths.put(current.adjNode, pathWrapper);
                        }
                    })
                    .filter(current -> current.edge != -1 && flagEncoder.getEdgeType(graphExplorer.getEdgeIteratorState(current.edge, current.adjNode).getFlags()) == edgeType)
                    .collect(Collectors.toList());
            visitedNodes += router.getVisitedNodes();
            return stationNodes;
        }

        private QueryResult findClosest(GHPoint point, int indexForErrorMessage) {
            final EdgeFilter filter = DefaultEdgeFilter.allEdges(graphHopperStorage.getEncodingManager().getEncoder("foot"));
            QueryResult source = locationIndex.findClosest(point.lat, point.lon, filter);
            if (!source.isValid()) {
                throw new PointNotFoundException("Cannot find point: " + point, indexForErrorMessage);
            }
            if (flagEncoder.getEdgeType(source.getClosestEdge().getFlags()) != GtfsStorage.EdgeType.HIGHWAY) {
                throw new RuntimeException(flagEncoder.getEdgeType(source.getClosestEdge().getFlags()).name());
            }
            return source;
        }

        private void parseSolutionsAndAddToResponse(List<Label> solutions, PointList waypoints) {
            for (Label solution : solutions) {
                final List<Trip.Leg> legs = new ArrayList<>();
                if (separateWalkQuery && solution.parent.parent == null) {
                    legs.addAll(0, walkPaths.get(realDestinationNode).getLegs());
                } else {
                    legs.addAll(tripFromLabel.getTrip(arriveBy, flagEncoder, translation, graphExplorer, accessEgressWeighting, solution));
                    if (separateWalkQuery) {
                        legs.addAll(0, walkPaths.get(accessNode(solution)).getLegs());
                        legs.addAll(walkPaths.get(egressNode(solution)).getLegs());
                    }
                }
                final PathWrapper pathWrapper = tripFromLabel.createPathWrapper(translation, waypoints, legs);
                pathWrapper.setImpossible(isImpossible(solution));
                pathWrapper.setTime((solution.currentTime - initialTime.toEpochMilli()) * (arriveBy ? -1 : 1));
                response.add(pathWrapper);
            }
            Comparator<PathWrapper> c = Comparator.comparingInt(p -> (p.isImpossible() ? 1 : 0));
            Comparator<PathWrapper> d = Comparator.comparingDouble(PathWrapper::getTime);
            response.getAll().sort(c.thenComparing(d));
        }

        private boolean isImpossible(Label solution) {
            for (Label i = solution; i != null; i = i.parent) {
                if (i.impossible) {
                    return true;
                }
            }
            return false;
        }

        private int accessNode(Label solution) {
            if (!arriveBy) {
                while (solution.parent.parent != null) {
                    solution = solution.parent;
                }
                return solution.adjNode;
            } else {
                return solution.parent.adjNode;
            }
        }

        private int egressNode(Label solution) {
            if (!arriveBy) {
                return solution.parent.adjNode;
            } else {
                while(solution.parent.parent != null) {
                    solution = solution.parent;
                }
                return solution.adjNode;
            }
        }

        private List<Label> findPaths(int startNode, int destNode) {
            StopWatch stopWatch = new StopWatch().start();
            graphExplorer = new GraphExplorer(queryGraph, accessEgressWeighting, flagEncoder, gtfsStorage, realtimeFeed, arriveBy, extraEdges, false, walkSpeedKmH);
            MultiCriteriaLabelSetting router = new MultiCriteriaLabelSetting(graphExplorer, flagEncoder, arriveBy, maxWalkDistancePerLeg, separateWalkQuery, !ignoreTransfers, profileQuery, maxVisitedNodesForRequest);
            router.setBetaTransfers(betaTransfers);
            router.setBetaWalkTime(betaWalkTime);
            final Stream<Label> labels = router.calcLabels(startNode, destNode, initialTime, blockedRouteTypes);
            List<Label> solutions = labels
                    .filter(current -> destNode == current.adjNode)
                    .limit(limitSolutions)
                    .collect(Collectors.toList());
            visitedNodes += router.getVisitedNodes();
            response.addDebugInfo("routing:" + stopWatch.stop().getSeconds() + "s");
            if (solutions.isEmpty() && router.getVisitedNodes() >= maxVisitedNodesForRequest) {
                throw new IllegalArgumentException("No path found - maximum number of nodes exceeded: " + maxVisitedNodesForRequest);
            }
            response.getHints().put("visited_nodes.sum", visitedNodes);
            response.getHints().put("visited_nodes.average", visitedNodes);
            if (solutions.isEmpty()) {
                response.addError(new RuntimeException("No route found"));
            }
            return solutions;
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
            new PrepareRoutingSubnetworks(graphHopperStorage, Collections.singletonList(ptFlagEncoder)).doWork();

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
                gtfsReader.readGraph();
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

                    MultiCriteriaLabelSetting router = new MultiCriteriaLabelSetting(graphExplorer, flagEncoder, false, Double.MAX_VALUE, false, false, false, Integer.MAX_VALUE);
                    final Stream<Label> labels = router.calcLabels(fromnode, tonode, Instant.ofEpochMilli(0), 0);
                    List<Label> solutions = labels
                            .filter(current -> tonode == current.adjNode)
                            .collect(Collectors.toList());
                    TransferWithTime transferWithTime = new TransferWithTime();
                    transferWithTime.id = e.getKey();
                    transferWithTime.transfer = e.getValue();
                    transferWithTime.time = solutions.get(0).currentTime;
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
