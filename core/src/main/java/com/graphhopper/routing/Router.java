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

package com.graphhopper.routing;

import com.carrotsearch.hppc.cursors.IntCursor;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.ch.CHRoutingAlgorithmFactory;
import com.graphhopper.routing.lm.LMRoutingAlgorithmFactory;
import com.graphhopper.routing.lm.LandmarkStorage;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.BlockAreaWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.*;
import com.graphhopper.util.details.PathDetailsBuilderFactory;
import com.graphhopper.util.exceptions.PointDistanceExceededException;
import com.graphhopper.util.exceptions.PointNotFoundException;
import com.graphhopper.util.exceptions.PointOutOfBoundsException;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;

import java.util.*;

public class Router {
    private final GraphHopperStorage ghStorage;
    private final EncodingManager encodingManager;
    private final LocationIndex locationIndex;
    private final Map<String, Profile> profilesByName;
    private final PathDetailsBuilderFactory pathDetailsBuilderFactory;
    private final TranslationMap translationMap;
    private final RouterConfig routerConfig;
    private final WeightingFactory weightingFactory;
    private final Map<String, RoutingCHGraph> chGraphs;
    private final Map<String, LandmarkStorage> landmarks;
    private final boolean chEnabled;
    private final boolean lmEnabled;

    public Router(GraphHopperStorage ghStorage, LocationIndex locationIndex, Map<String, Profile> profilesByName, PathDetailsBuilderFactory pathDetailsBuilderFactory, TranslationMap translationMap, RouterConfig routerConfig, WeightingFactory weightingFactory, Map<String, CHGraph> chGraphs, Map<String, LandmarkStorage> landmarks) {
        this.ghStorage = ghStorage;
        this.encodingManager = ghStorage.getEncodingManager();
        this.locationIndex = locationIndex;
        this.profilesByName = profilesByName;
        this.pathDetailsBuilderFactory = pathDetailsBuilderFactory;
        this.translationMap = translationMap;
        this.routerConfig = routerConfig;
        this.weightingFactory = weightingFactory;
        this.chGraphs = new LinkedHashMap(chGraphs.size());
        Iterator var10 = chGraphs.entrySet().iterator();

        while(var10.hasNext()) {
            Map.Entry<String, CHGraph> e = (Map.Entry)var10.next();
            this.chGraphs.put(e.getKey(), new RoutingCHGraphImpl((CHGraph)e.getValue()));
        }

        this.landmarks = landmarks;
        this.chEnabled = !chGraphs.isEmpty();
        this.lmEnabled = !landmarks.isEmpty();
    }

    public GHResponse route(GHRequest request) {
        GHResponse ghRsp;
        try {
            this.validateRequest(request);
            boolean disableCH = getDisableCH(request.getHints());
            boolean disableLM = getDisableLM(request.getHints());
            Profile profile = (Profile)this.profilesByName.get(request.getProfile());
            if (profile == null) {
                throw new IllegalArgumentException("The requested profile '" + request.getProfile() + "' does not exist.\nAvailable profiles: " + this.profilesByName.keySet());
            } else if (!profile.isTurnCosts() && !request.getCurbsides().isEmpty()) {
                throw new IllegalArgumentException("To make use of the curbside parameter you need to use a profile that supports turn costs\nThe following profiles do support turn costs: " + this.getTurnCostProfiles());
            } else {
                TraversalMode traversalMode = profile.isTurnCosts() ? TraversalMode.EDGE_BASED : TraversalMode.NODE_BASED;
                int uTurnCostsInt = request.getHints().getInt("u_turn_costs", -1);
                if (uTurnCostsInt != -1 && !traversalMode.isEdgeBased()) {
                    throw new IllegalArgumentException("Finite u-turn costs can only be used for edge-based routing, you need to use a profile that supports turn costs. Currently the following profiles that support turn costs are available: " + this.getTurnCostProfiles());
                } else {
                    boolean passThrough = getPassThrough(request.getHints());
                    boolean forceCurbsides = request.getHints().getBool("force_curbside", true);
                    int maxVisitedNodesForRequest = request.getHints().getInt("max_visited_nodes", this.routerConfig.getMaxVisitedNodes());
                    if (maxVisitedNodesForRequest > this.routerConfig.getMaxVisitedNodes()) {
                        throw new IllegalArgumentException("The max_visited_nodes parameter has to be below or equal to:" + this.routerConfig.getMaxVisitedNodes());
                    } else {
                        boolean useCH = this.chEnabled && !disableCH;
                        Weighting weighting = this.createWeighting(profile, request.getHints(), request.getPoints(), useCH);
                        AlgorithmOptions algoOpts = AlgorithmOptions.start().algorithm(request.getAlgorithm()).traversalMode(traversalMode).weighting(weighting).maxVisitedNodes(maxVisitedNodesForRequest).hints(request.getHints()).build();
                        if ("round_trip".equalsIgnoreCase(request.getAlgorithm())) {
                            return this.routeRoundTrip(request, algoOpts, weighting, profile, disableLM);
                        } else {
                            return "alternative_route".equalsIgnoreCase(request.getAlgorithm()) ? this.routeAlt(request, algoOpts, weighting, profile, passThrough, forceCurbsides, disableCH, disableLM) : this.routeVia(request, algoOpts, weighting, profile, passThrough, forceCurbsides, disableCH, disableLM);
                        }
                    }
                }
            }
        } catch (MultiplePointsNotFoundException var13) {
            ghRsp = new GHResponse();
            Iterator var4 = var13.getPointsNotFound().iterator();

            while(var4.hasNext()) {
                IntCursor p = (IntCursor)var4.next();
                ghRsp.addError(new PointNotFoundException("Cannot find point " + p.value + ": " + request.getPoints().get(p.index), p.value));
            }

            return ghRsp;
        } catch (IllegalArgumentException var14) {
            ghRsp = new GHResponse();
            ghRsp.addError(var14);
            return ghRsp;
        }
    }

    protected GHResponse routeRoundTrip(GHRequest request, AlgorithmOptions algoOpts, Weighting weighting, Profile profile, boolean disableLM) {
        GHResponse ghRsp = new GHResponse();
        StopWatch sw = (new StopWatch()).start();
        double startHeading = request.getHeadings().isEmpty() ? 0.0D / 0.0 : (Double)request.getHeadings().get(0);
        RoundTripRouting.Params params = new RoundTripRouting.Params(request.getHints(), startHeading, this.routerConfig.getMaxRoundTripRetries());
        List<QueryResult> qResults = RoundTripRouting.lookup(request.getPoints(), weighting, this.locationIndex, params);
        ghRsp.addDebugInfo("idLookup:" + sw.stop().getSeconds() + "s");
        AlgorithmOptions roundTripAlgoOpts = AlgorithmOptions.start(algoOpts).algorithm("astarbi").build();
        roundTripAlgoOpts.getHints().putObject("astarbi.epsilon", 2);
        QueryGraph queryGraph = QueryGraph.create(this.ghStorage, qResults);
        FlexiblePathCalculator pathCalculator = this.createFlexiblePathCalculator(queryGraph, profile, roundTripAlgoOpts, disableLM);
        RoundTripRouting.Result result = RoundTripRouting.calcPaths(qResults, pathCalculator);
        ResponsePath responsePath = this.concatenatePaths(request, weighting, queryGraph, result.paths, qResults);
        ghRsp.add(responsePath);
        ghRsp.getHints().putObject("visited_nodes.sum", result.visitedNodes);
        ghRsp.getHints().putObject("visited_nodes.average", (float)result.visitedNodes / (float)(qResults.size() - 1));
        return ghRsp;
    }

    protected GHResponse routeAlt(GHRequest request, AlgorithmOptions algoOpts, Weighting weighting, Profile profile, boolean passThrough, boolean forceCurbsides, boolean disableCH, boolean disableLM) {
        if (request.getPoints().size() > 2) {
            throw new IllegalArgumentException("Currently alternative routes work only with start and end point. You tried to use: " + request.getPoints().size() + " points");
        } else {
            GHResponse ghRsp = new GHResponse();
            StopWatch sw = (new StopWatch()).start();
            List<QueryResult> qResults = ViaRouting.lookup(this.encodingManager, request.getPoints(), weighting, this.locationIndex, request.getSnapPreventions(), request.getPointHints());
            ghRsp.addDebugInfo("idLookup:" + sw.stop().getSeconds() + "s");
            QueryGraph queryGraph = QueryGraph.create(this.ghStorage, qResults);
            PathCalculator pathCalculator = this.createPathCalculator(queryGraph, profile, algoOpts, disableCH, disableLM);
            if (passThrough) {
                throw new IllegalArgumentException("Alternative paths and pass_through at the same time is currently not supported");
            } else if (!request.getCurbsides().isEmpty()) {
                throw new IllegalArgumentException("Alternative paths do not support the curbside parameter yet");
            } else {
                com.graphhopper.routing.ViaRouting.Result result = ViaRouting.calcPaths(request.getPoints(), queryGraph, qResults, weighting.getFlagEncoder().getAccessEnc(), pathCalculator, request.getCurbsides(), forceCurbsides, request.getHeadings(), passThrough);
                if (result.paths.isEmpty()) {
                    throw new RuntimeException("Empty paths for alternative route calculation not expected");
                } else {
                    PathMerger pathMerger = this.createPathMerger(request, weighting, queryGraph);
                    Iterator var16 = result.paths.iterator();

                    while(var16.hasNext()) {
                        Path path = (Path)var16.next();
                        PointList waypoints = this.getWaypoints(qResults);
                        ResponsePath responsePath = pathMerger.doWork(waypoints, Collections.singletonList(path), this.encodingManager, this.translationMap.getWithFallBack(request.getLocale()));
                        ghRsp.add(responsePath);
                    }

                    ghRsp.getHints().putObject("visited_nodes.sum", result.visitedNodes);
                    ghRsp.getHints().putObject("visited_nodes.average", (float)result.visitedNodes / (float)(qResults.size() - 1));
                    return ghRsp;
                }
            }
        }
    }

    protected GHResponse routeVia(GHRequest request, AlgorithmOptions algoOpts, Weighting weighting, Profile profile, boolean passThrough, boolean forceCurbsides, boolean disableCH, boolean disableLM) {
        GHResponse ghRsp = new GHResponse();
        StopWatch sw = (new StopWatch()).start();
        List<QueryResult> qResults = ViaRouting.lookup(this.encodingManager, request.getPoints(), weighting, this.locationIndex, request.getSnapPreventions(), request.getPointHints());
        ghRsp.addDebugInfo("idLookup:" + sw.stop().getSeconds() + "s");
        QueryGraph queryGraph = QueryGraph.create(this.ghStorage, qResults);
        PathCalculator pathCalculator = this.createPathCalculator(queryGraph, profile, algoOpts, disableCH, disableLM);
        com.graphhopper.routing.ViaRouting.Result result = ViaRouting.calcPaths(request.getPoints(), queryGraph, qResults, weighting.getFlagEncoder().getAccessEnc(), pathCalculator, request.getCurbsides(), forceCurbsides, request.getHeadings(), passThrough);
        if (request.getPoints().size() != result.paths.size() + 1) {
            throw new RuntimeException("There should be exactly one more point than paths. points:" + request.getPoints().size() + ", paths:" + result.paths.size());
        } else {
            ResponsePath responsePath = this.concatenatePaths(request, weighting, queryGraph, result.paths, qResults);
            responsePath.addDebugInfo(result.debug);
            ghRsp.add(responsePath);
            ghRsp.getHints().putObject("visited_nodes.sum", result.visitedNodes);
            ghRsp.getHints().putObject("visited_nodes.average", (float)result.visitedNodes / (float)(qResults.size() - 1));
            return ghRsp;
        }
    }

    private Weighting createWeighting(Profile profile, PMap requestHints, List<GHPoint> points, boolean forCH) {
        if (forCH) {
            return this.weightingFactory.createWeighting(profile, new PMap(), false);
        } else {
            Weighting weighting = this.weightingFactory.createWeighting(profile, requestHints, false);
            if (requestHints.has("block_area")) {
                FlagEncoder encoder = this.encodingManager.getEncoder(profile.getVehicle());
                GraphEdgeIdFinder.BlockArea blockArea = GraphEdgeIdFinder.createBlockArea(this.ghStorage, this.locationIndex, points, requestHints, DefaultEdgeFilter.allEdges(encoder));
                weighting = new BlockAreaWeighting((Weighting)weighting, blockArea);
            }

            return (Weighting)weighting;
        }
    }

    private PathCalculator createPathCalculator(QueryGraph queryGraph, Profile profile, AlgorithmOptions algoOpts, boolean disableCH, boolean disableLM) {
        if (this.chEnabled && !disableCH) {
            PMap opts = new PMap(algoOpts.getHints());
            opts.putObject("algorithm", algoOpts.getAlgorithm());
            opts.putObject("max_visited_nodes", algoOpts.getMaxVisitedNodes());
            return this.createCHPathCalculator(queryGraph, profile, opts);
        } else {
            return this.createFlexiblePathCalculator(queryGraph, profile, algoOpts, disableLM);
        }
    }

    private PathCalculator createCHPathCalculator(QueryGraph queryGraph, Profile profile, PMap opts) {
        RoutingCHGraph chGraph = (RoutingCHGraph)this.chGraphs.get(profile.getName());
        if (chGraph == null) {
            throw new IllegalArgumentException("Cannot find CH preparation for the requested profile: '" + profile.getName() + "'\nYou can try disabling CH using " + "ch.disable" + "=true\navailable CH profiles: " + this.chGraphs.keySet());
        } else {
            return new CHPathCalculator(new CHRoutingAlgorithmFactory(chGraph, queryGraph), opts);
        }
    }

    protected FlexiblePathCalculator createFlexiblePathCalculator(QueryGraph queryGraph, Profile profile, AlgorithmOptions algoOpts, boolean disableLM) {
        Object algorithmFactory;
        if (this.lmEnabled && !disableLM) {
            LandmarkStorage landmarkStorage = (LandmarkStorage)this.landmarks.get(profile.getName());
            if (landmarkStorage == null) {
                throw new IllegalArgumentException("Cannot find LM preparation for the requested profile: '" + profile.getName() + "'\nYou can try disabling LM using " + "lm.disable" + "=true\navailable LM profiles: " + this.landmarks.keySet());
            }

            algorithmFactory = (new LMRoutingAlgorithmFactory(landmarkStorage)).setDefaultActiveLandmarks(this.routerConfig.getActiveLandmarkCount());
        } else {
            algorithmFactory = new RoutingAlgorithmFactorySimple();
        }

        return new FlexiblePathCalculator(queryGraph, (RoutingAlgorithmFactory)algorithmFactory, algoOpts);
    }

    private PathMerger createPathMerger(GHRequest request, Weighting weighting, Graph graph) {
        boolean enableInstructions = request.getHints().getBool("instructions", this.encodingManager.isEnableInstructions());
        boolean calcPoints = request.getHints().getBool("calc_points", this.routerConfig.isCalcPoints());
        double wayPointMaxDistance = request.getHints().getDouble("way_point_max_distance", 1.0D);
        double elevationWayPointMaxDistance = request.getHints().getDouble("elevation_way_point_max_distance", this.routerConfig.getElevationWayPointMaxDistance());
        DouglasPeucker peucker = (new DouglasPeucker()).setMaxDistance(wayPointMaxDistance).setElevationMaxDistance(elevationWayPointMaxDistance);
        PathMerger pathMerger = (new PathMerger(graph, weighting)).setCalcPoints(calcPoints).setDouglasPeucker(peucker).setEnableInstructions(enableInstructions).setPathDetailsBuilders(this.pathDetailsBuilderFactory, request.getPathDetails()).setSimplifyResponse(this.routerConfig.isSimplifyResponse() && wayPointMaxDistance > 0.0D);
        if (!request.getHeadings().isEmpty()) {
            pathMerger.setFavoredHeading((Double)request.getHeadings().get(0));
        }

        return pathMerger;
    }

    protected ResponsePath concatenatePaths(GHRequest request, Weighting weighting, QueryGraph queryGraph, List<Path> paths, List<QueryResult> qResult) {
        PathMerger pathMerger = this.createPathMerger(request, weighting, queryGraph);
        return pathMerger.doWork(this.getWaypoints(qResult), paths, this.encodingManager, this.translationMap.getWithFallBack(request.getLocale()));
    }

    private PointList getWaypoints(List<QueryResult> queryResults) {
        PointList pointList = new PointList(queryResults.size(), true);
        Iterator var3 = queryResults.iterator();

        while(var3.hasNext()) {
            QueryResult qr = (QueryResult)var3.next();
            pointList.add(qr.getSnappedPoint());
        }

        return pointList;
    }

    protected void validateRequest(GHRequest request) {
        if (Helper.isEmpty(request.getProfile())) {
            throw new IllegalArgumentException("You need to specify a profile to perform a routing request, see docs/core/profiles.md");
        } else if (request.getHints().has("vehicle")) {
            throw new IllegalArgumentException("GHRequest may no longer contain a vehicle, use the profile parameter instead, see docs/core/profiles.md");
        } else if (request.getHints().has("weighting")) {
            throw new IllegalArgumentException("GHRequest may no longer contain a weighting, use the profile parameter instead, see docs/core/profiles.md");
        } else if (request.getHints().has("turn_costs")) {
            throw new IllegalArgumentException("GHRequest may no longer contain the turn_costs=true/false parameter, use the profile parameter instead, see docs/core/profiles.md");
        } else if (request.getHints().has("edge_based")) {
            throw new IllegalArgumentException("GHRequest may no longer contain the edge_based=true/false parameter, use the profile parameter instead, see docs/core/profiles.md");
        } else if (request.getPoints().isEmpty()) {
            throw new IllegalArgumentException("You have to pass at least one point");
        } else {
            this.checkIfPointsAreInBounds(request.getPoints());
            if (request.getHeadings().size() > 1 && request.getHeadings().size() != request.getPoints().size()) {
                throw new IllegalArgumentException("The number of 'heading' parameters must be zero, one or equal to the number of points (" + request.getPoints().size() + ")");
            } else {
                for(int i = 0; i < request.getHeadings().size(); ++i) {
                    if (!GHRequest.isAzimuthValue((Double)request.getHeadings().get(i))) {
                        throw new IllegalArgumentException("Heading for point " + i + " must be in range [0,360) or NaN, but was: " + request.getHeadings().get(i));
                    }
                }

                if (request.getPointHints().size() > 0 && request.getPointHints().size() != request.getPoints().size()) {
                    throw new IllegalArgumentException("If you pass point_hint, you need to pass exactly one hint for every point, empty hints will be ignored");
                } else if (request.getCurbsides().size() > 0 && request.getCurbsides().size() != request.getPoints().size()) {
                    throw new IllegalArgumentException("If you pass curbside, you need to pass exactly one curbside for every point, empty curbsides will be ignored");
                } else {
                    boolean disableCH = getDisableCH(request.getHints());
                    if (this.chEnabled && !this.routerConfig.isCHDisablingAllowed() && disableCH) {
                        throw new IllegalArgumentException("Disabling CH not allowed on the server-side");
                    } else {
                        boolean disableLM = getDisableLM(request.getHints());
                        if (this.lmEnabled && !this.routerConfig.isLMDisablingAllowed() && disableLM) {
                            throw new IllegalArgumentException("Disabling LM not allowed on the server-side");
                        } else {
                            if (this.chEnabled && !disableCH) {
                                if (!request.getHeadings().isEmpty()) {
                                    throw new IllegalArgumentException("The 'heading' parameter is currently not supported for speed mode, you need to disable speed mode with `ch.disable=true`. See issue #483");
                                }

                                if (getPassThrough(request.getHints())) {
                                    throw new IllegalArgumentException("The 'pass_through' parameter is currently not supported for speed mode, you need to disable speed mode with `ch.disable=true`. See issue #1765");
                                }

                                if (request.getHints().has("block_area")) {
                                    throw new IllegalArgumentException("When CH is enabled the block_area cannot be specified");
                                }
                            } else {
                                this.checkNonChMaxWaypointDistance(request.getPoints());
                            }

                        }
                    }
                }
            }
        }
    }

    private List<String> getTurnCostProfiles() {
        List<String> turnCostProfiles = new ArrayList();
        Iterator var2 = this.profilesByName.values().iterator();

        while(var2.hasNext()) {
            Profile p = (Profile)var2.next();
            if (p.isTurnCosts()) {
                turnCostProfiles.add(p.getName());
            }
        }

        return turnCostProfiles;
    }

    private static boolean getDisableLM(PMap hints) {
        return hints.getBool("lm.disable", false);
    }

    private static boolean getDisableCH(PMap hints) {
        return hints.getBool("ch.disable", false);
    }

    private static boolean getPassThrough(PMap hints) {
        return hints.getBool("pass_through", false);
    }

    private void checkIfPointsAreInBounds(List<GHPoint> points) {
        BBox bounds = this.ghStorage.getBounds();

        for(int i = 0; i < points.size(); ++i) {
            GHPoint point = (GHPoint)points.get(i);
            if (!bounds.contains(point.getLat(), point.getLon())) {
                throw new PointOutOfBoundsException("Point " + i + " is out of bounds: " + point + ", the bounds are: " + bounds, i);
            }
        }

    }

    private void checkNonChMaxWaypointDistance(List<GHPoint> points) {
        if (this.routerConfig.getNonChMaxWaypointDistance() != 2147483647) {
            GHPoint lastPoint = (GHPoint)points.get(0);

            for(int i = 1; i < points.size(); ++i) {
                GHPoint point = (GHPoint)points.get(i);
                double dist = DistanceCalcEarth.DIST_EARTH.calcDist(lastPoint.getLat(), lastPoint.getLon(), point.getLat(), point.getLon());
                if (dist > (double)this.routerConfig.getNonChMaxWaypointDistance()) {
                    Map<String, Object> detailMap = new HashMap(2);
                    detailMap.put("from", i - 1);
                    detailMap.put("to", i);
                    throw new PointDistanceExceededException("Point " + i + " is too far from Point " + (i - 1) + ": " + point, detailMap);
                }

                lastPoint = point;
            }

        }
    }
}
