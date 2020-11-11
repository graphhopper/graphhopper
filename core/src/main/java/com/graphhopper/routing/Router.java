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
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.*;
import com.graphhopper.util.details.PathDetailsBuilderFactory;
import com.graphhopper.util.exceptions.PointDistanceExceededException;
import com.graphhopper.util.exceptions.PointNotFoundException;
import com.graphhopper.util.exceptions.PointOutOfBoundsException;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;

import java.util.*;

import static com.graphhopper.routing.weighting.Weighting.INFINITE_U_TURN_COSTS;
import static com.graphhopper.util.DistanceCalcEarth.DIST_EARTH;
import static com.graphhopper.util.Parameters.Algorithms.ALT_ROUTE;
import static com.graphhopper.util.Parameters.Algorithms.ROUND_TRIP;
import static com.graphhopper.util.Parameters.Routing.*;

public class Router {
    private final GraphHopperStorage ghStorage;
    private final EncodingManager encodingManager;
    private final LocationIndex locationIndex;
    private final Map<String, Profile> profilesByName;
    private final PathDetailsBuilderFactory pathDetailsBuilderFactory;
    private final TranslationMap translationMap;
    private final RouterConfig routerConfig;
    private final WeightingFactory weightingFactory;
    // todo: these should not be necessary anymore as soon as GraphHopperStorage (or something that replaces) it acts
    // like a 'graph database'
    private final Map<String, RoutingCHGraph> chGraphs;
    private final Map<String, LandmarkStorage> landmarks;
    private final boolean chEnabled;
    private final boolean lmEnabled;

    public Router(GraphHopperStorage ghStorage, LocationIndex locationIndex,
                  Map<String, Profile> profilesByName, PathDetailsBuilderFactory pathDetailsBuilderFactory,
                  TranslationMap translationMap, RouterConfig routerConfig, WeightingFactory weightingFactory,
                  Map<String, CHGraph> chGraphs, Map<String, LandmarkStorage> landmarks) {
        this.ghStorage = ghStorage;
        this.encodingManager = ghStorage.getEncodingManager();
        this.locationIndex = locationIndex;
        this.profilesByName = profilesByName;
        this.pathDetailsBuilderFactory = pathDetailsBuilderFactory;
        this.translationMap = translationMap;
        this.routerConfig = routerConfig;
        this.weightingFactory = weightingFactory;
        this.chGraphs = new LinkedHashMap<>(chGraphs.size());
        for (Map.Entry<String, CHGraph> e : chGraphs.entrySet()) {
            this.chGraphs.put(e.getKey(), new RoutingCHGraphImpl(e.getValue()));
        }
        this.landmarks = landmarks;
        // note that his is not the same as !ghStorage.getCHConfigs().isEmpty(), because the GHStorage might have some
        // CHGraphs that were not built yet (and possibly no CH profiles were configured).
        this.chEnabled = !chGraphs.isEmpty();
        this.lmEnabled = !landmarks.isEmpty();
    }

    public GHResponse route(GHRequest request) {
        try {
            validateRequest(request);
            final boolean disableCH = getDisableCH(request.getHints());
            final boolean disableLM = getDisableLM(request.getHints());
            Profile profile = profilesByName.get(request.getProfile());
            if (profile == null)
                throw new IllegalArgumentException("The requested profile '" + request.getProfile() + "' does not exist.\nAvailable profiles: " + profilesByName.keySet());
            if (!profile.isTurnCosts() && !request.getCurbsides().isEmpty())
                throw new IllegalArgumentException("To make use of the " + CURBSIDE + " parameter you need to use a profile that supports turn costs" +
                        "\nThe following profiles do support turn costs: " + getTurnCostProfiles());
            // todo later: should we be able to control this using the edge_based parameter?
            TraversalMode traversalMode = profile.isTurnCosts() ? TraversalMode.EDGE_BASED : TraversalMode.NODE_BASED;
            final int uTurnCostsInt = request.getHints().getInt(Parameters.Routing.U_TURN_COSTS, INFINITE_U_TURN_COSTS);
            if (uTurnCostsInt != INFINITE_U_TURN_COSTS && !traversalMode.isEdgeBased()) {
                throw new IllegalArgumentException("Finite u-turn costs can only be used for edge-based routing, you need to use a profile that" +
                        " supports turn costs. Currently the following profiles that support turn costs are available: " + getTurnCostProfiles());
            }
            final boolean passThrough = getPassThrough(request.getHints());
            final boolean forceCurbsides = request.getHints().getBool(FORCE_CURBSIDE, true);
            int maxVisitedNodesForRequest = request.getHints().getInt(Parameters.Routing.MAX_VISITED_NODES, routerConfig.getMaxVisitedNodes());
            if (maxVisitedNodesForRequest > routerConfig.getMaxVisitedNodes())
                throw new IllegalArgumentException("The max_visited_nodes parameter has to be below or equal to:" + routerConfig.getMaxVisitedNodes());

            // determine weighting
            final boolean useCH = chEnabled && !disableCH;
            Weighting weighting = createWeighting(profile, request.getHints(), request.getPoints(), useCH);

            AlgorithmOptions algoOpts = AlgorithmOptions.start().
                    algorithm(request.getAlgorithm()).
                    traversalMode(traversalMode).
                    weighting(weighting).
                    maxVisitedNodes(maxVisitedNodesForRequest).
                    hints(request.getHints()).
                    build();

            if (ROUND_TRIP.equalsIgnoreCase(request.getAlgorithm())) {
                return routeRoundTrip(request, algoOpts, weighting, profile, disableLM);
            } else if (ALT_ROUTE.equalsIgnoreCase(request.getAlgorithm())) {
                return routeAlt(request, algoOpts, weighting, profile, passThrough, forceCurbsides, disableCH, disableLM);
            } else {
                return routeVia(request, algoOpts, weighting, profile, passThrough, forceCurbsides, disableCH, disableLM);
            }
        } catch (MultiplePointsNotFoundException ex) {
            GHResponse ghRsp = new GHResponse();
            for (IntCursor p : ex.getPointsNotFound()) {
                ghRsp.addError(new PointNotFoundException("Cannot find point " + p.value + ": " + request.getPoints().get(p.value), p.value));
            }
            return ghRsp;
        } catch (IllegalArgumentException ex) {
            GHResponse ghRsp = new GHResponse();
            ghRsp.addError(ex);
            return ghRsp;
        }
    }

    protected GHResponse routeRoundTrip(GHRequest request, AlgorithmOptions algoOpts, Weighting weighting, Profile profile, boolean disableLM) {
        GHResponse ghRsp = new GHResponse();
        StopWatch sw = new StopWatch().start();
        double startHeading = request.getHeadings().isEmpty() ? Double.NaN : request.getHeadings().get(0);
        RoundTripRouting.Params params = new RoundTripRouting.Params(request.getHints(), startHeading, routerConfig.getMaxRoundTripRetries());
        List<Snap> qResults = RoundTripRouting.lookup(request.getPoints(), weighting, locationIndex, params);
        ghRsp.addDebugInfo("idLookup:" + sw.stop().getSeconds() + "s");

        // use A* for round trips
        AlgorithmOptions roundTripAlgoOpts = AlgorithmOptions
                .start(algoOpts)
                .algorithm(Parameters.Algorithms.ASTAR_BI)
                .build();
        roundTripAlgoOpts.getHints().putObject(Parameters.Algorithms.AStarBi.EPSILON, 2);
        QueryGraph queryGraph = QueryGraph.create(ghStorage, qResults);
        FlexiblePathCalculator pathCalculator = createFlexiblePathCalculator(queryGraph, profile, roundTripAlgoOpts, disableLM);

        RoundTripRouting.Result result = RoundTripRouting.calcPaths(qResults, pathCalculator);
        // we merge the different legs of the roundtrip into one response path
        ResponsePath responsePath = concatenatePaths(request, weighting, queryGraph, result.paths, getWaypoints(qResults));
        ghRsp.add(responsePath);
        ghRsp.getHints().putObject("visited_nodes.sum", result.visitedNodes);
        ghRsp.getHints().putObject("visited_nodes.average", (float) result.visitedNodes / (qResults.size() - 1));
        return ghRsp;
    }

    protected GHResponse routeAlt(GHRequest request, AlgorithmOptions algoOpts, Weighting weighting, Profile profile, boolean passThrough, boolean forceCurbsides, boolean disableCH, boolean disableLM) {
        if (request.getPoints().size() > 2)
            throw new IllegalArgumentException("Currently alternative routes work only with start and end point. You tried to use: " + request.getPoints().size() + " points");
        GHResponse ghRsp = new GHResponse();
        StopWatch sw = new StopWatch().start();
        List<Snap> qResults = ViaRouting.lookup(encodingManager, request.getPoints(), weighting, locationIndex, request.getSnapPreventions(), request.getPointHints());
        ghRsp.addDebugInfo("idLookup:" + sw.stop().getSeconds() + "s");
        QueryGraph queryGraph = QueryGraph.create(ghStorage, qResults);
        PathCalculator pathCalculator = createPathCalculator(queryGraph, profile, algoOpts, disableCH, disableLM);

        if (passThrough)
            throw new IllegalArgumentException("Alternative paths and " + PASS_THROUGH + " at the same time is currently not supported");
        if (!request.getCurbsides().isEmpty())
            throw new IllegalArgumentException("Alternative paths do not support the " + CURBSIDE + " parameter yet");

        ViaRouting.Result result = ViaRouting.calcPaths(request.getPoints(), queryGraph, qResults, weighting.getFlagEncoder().getAccessEnc(), pathCalculator, request.getCurbsides(), forceCurbsides, request.getHeadings(), passThrough);
        if (result.paths.isEmpty())
            throw new RuntimeException("Empty paths for alternative route calculation not expected");

        // each path represents a different alternative and we do the path merging for each of them
        PathMerger pathMerger = createPathMerger(request, weighting, queryGraph);
        for (Path path : result.paths) {
            PointList waypoints = getWaypoints(qResults);
            ResponsePath responsePath = pathMerger.doWork(waypoints, Collections.singletonList(path), encodingManager, translationMap.getWithFallBack(request.getLocale()));
            ghRsp.add(responsePath);
        }
        ghRsp.getHints().putObject("visited_nodes.sum", result.visitedNodes);
        ghRsp.getHints().putObject("visited_nodes.average", (float) result.visitedNodes / (qResults.size() - 1));
        return ghRsp;
    }

    protected GHResponse routeVia(GHRequest request, AlgorithmOptions algoOpts, Weighting weighting, Profile profile, boolean passThrough, boolean forceCurbsides, boolean disableCH, boolean disableLM) {
        GHResponse ghRsp = new GHResponse();
        StopWatch sw = new StopWatch().start();
        List<Snap> qResults = ViaRouting.lookup(encodingManager, request.getPoints(), weighting, locationIndex, request.getSnapPreventions(), request.getPointHints());
        ghRsp.addDebugInfo("idLookup:" + sw.stop().getSeconds() + "s");
        // (base) query graph used to resolve headings, curbsides etc. this is not necessarily the same thing as
        // the (possibly implementation specific) query graph used by PathCalculator
        QueryGraph queryGraph = QueryGraph.create(ghStorage, qResults);
        PathCalculator pathCalculator = createPathCalculator(queryGraph, profile, algoOpts, disableCH, disableLM);
        ViaRouting.Result result = ViaRouting.calcPaths(request.getPoints(), queryGraph, qResults, weighting.getFlagEncoder().getAccessEnc(), pathCalculator, request.getCurbsides(), forceCurbsides, request.getHeadings(), passThrough);

        if (request.getPoints().size() != result.paths.size() + 1)
            throw new RuntimeException("There should be exactly one more point than paths. points:" + request.getPoints().size() + ", paths:" + result.paths.size());

        // here each path represents one leg of the via-route and we merge them all together into one response path
        ResponsePath responsePath = concatenatePaths(request, weighting, queryGraph, result.paths, getWaypoints(qResults));
        responsePath.addDebugInfo(result.debug);
        ghRsp.add(responsePath);
        ghRsp.getHints().putObject("visited_nodes.sum", result.visitedNodes);
        ghRsp.getHints().putObject("visited_nodes.average", (float) result.visitedNodes / (qResults.size() - 1));
        return ghRsp;
    }

    private Weighting createWeighting(Profile profile, PMap requestHints, List<GHPoint> points, boolean forCH) {
        if (forCH) {
            // todo: do not allow things like short_fastest.distance_factor or u_turn_costs unless CH is disabled
            // and only under certain conditions for LM

            // the request hints are ignored for CH as we cannot change the profile after the preparation like this.
            // the weighting here has to be created the same way as we did when we created the weighting for the preparation
            return weightingFactory.createWeighting(profile, new PMap(), false);
        } else {
            Weighting weighting = weightingFactory.createWeighting(profile, requestHints, false);
            if (requestHints.has(Parameters.Routing.BLOCK_AREA)) {
                FlagEncoder encoder = encodingManager.getEncoder(profile.getVehicle());
                GraphEdgeIdFinder.BlockArea blockArea = GraphEdgeIdFinder.createBlockArea(ghStorage, locationIndex,
                        points, requestHints, DefaultEdgeFilter.allEdges(encoder));
                weighting = new BlockAreaWeighting(weighting, blockArea);
            }
            return weighting;
        }
    }

    private PathCalculator createPathCalculator(QueryGraph queryGraph, Profile profile, AlgorithmOptions algoOpts, boolean disableCH, boolean disableLM) {
        if (chEnabled && !disableCH) {
            PMap opts = new PMap(algoOpts.getHints());
            opts.putObject(ALGORITHM, algoOpts.getAlgorithm());
            opts.putObject(MAX_VISITED_NODES, algoOpts.getMaxVisitedNodes());
            return createCHPathCalculator(queryGraph, profile, opts);
        } else {
            return createFlexiblePathCalculator(queryGraph, profile, algoOpts, disableLM);
        }
    }

    private PathCalculator createCHPathCalculator(QueryGraph queryGraph, Profile profile, PMap opts) {
        RoutingCHGraph chGraph = chGraphs.get(profile.getName());
        if (chGraph == null)
            throw new IllegalArgumentException("Cannot find CH preparation for the requested profile: '" + profile.getName() + "'" +
                    "\nYou can try disabling CH using " + Parameters.CH.DISABLE + "=true" +
                    "\navailable CH profiles: " + chGraphs.keySet());
        return new CHPathCalculator(new CHRoutingAlgorithmFactory(chGraph, queryGraph), opts);
    }

    private FlexiblePathCalculator createFlexiblePathCalculator(QueryGraph queryGraph, Profile profile, AlgorithmOptions algoOpts, boolean disableLM) {
        RoutingAlgorithmFactory algorithmFactory;
        // for now do not allow mixing CH&LM #1082,#1889
        if (lmEnabled && !disableLM) {
            LandmarkStorage landmarkStorage = landmarks.get(profile.getName());
            if (landmarkStorage == null)
                throw new IllegalArgumentException("Cannot find LM preparation for the requested profile: '" + profile.getName() + "'" +
                        "\nYou can try disabling LM using " + Parameters.Landmark.DISABLE + "=true" +
                        "\navailable LM profiles: " + landmarks.keySet());
            algorithmFactory = new LMRoutingAlgorithmFactory(landmarkStorage).setDefaultActiveLandmarks(routerConfig.getActiveLandmarkCount());
        } else {
            algorithmFactory = new RoutingAlgorithmFactorySimple();
        }
        return new FlexiblePathCalculator(queryGraph, algorithmFactory, algoOpts);
    }

    private PathMerger createPathMerger(GHRequest request, Weighting weighting, Graph graph) {
        boolean enableInstructions = request.getHints().getBool(Parameters.Routing.INSTRUCTIONS, encodingManager.isEnableInstructions());
        boolean calcPoints = request.getHints().getBool(Parameters.Routing.CALC_POINTS, routerConfig.isCalcPoints());
        double wayPointMaxDistance = request.getHints().getDouble(Parameters.Routing.WAY_POINT_MAX_DISTANCE, 1d);
        double elevationWayPointMaxDistance = request.getHints().getDouble(ELEVATION_WAY_POINT_MAX_DISTANCE, routerConfig.getElevationWayPointMaxDistance());

        DouglasPeucker peucker = new DouglasPeucker().
                setMaxDistance(wayPointMaxDistance).
                setElevationMaxDistance(elevationWayPointMaxDistance);
        PathMerger pathMerger = new PathMerger(graph, weighting).
                setCalcPoints(calcPoints).
                setDouglasPeucker(peucker).
                setEnableInstructions(enableInstructions).
                setPathDetailsBuilders(pathDetailsBuilderFactory, request.getPathDetails()).
                setSimplifyResponse(routerConfig.isSimplifyResponse() && wayPointMaxDistance > 0);

        if (!request.getHeadings().isEmpty())
            pathMerger.setFavoredHeading(request.getHeadings().get(0));
        return pathMerger;
    }

    private ResponsePath concatenatePaths(GHRequest request, Weighting weighting, QueryGraph queryGraph, List<Path> paths, PointList waypoints) {
        PathMerger pathMerger = createPathMerger(request, weighting, queryGraph);
        return pathMerger.doWork(waypoints, paths, encodingManager, translationMap.getWithFallBack(request.getLocale()));
    }

    private PointList getWaypoints(List<Snap> snaps) {
        PointList pointList = new PointList(snaps.size(), true);
        for (Snap snap : snaps) {
            pointList.add(snap.getSnappedPoint());
        }
        return pointList;
    }

    protected void validateRequest(GHRequest request) {
        if (Helper.isEmpty(request.getProfile()))
            throw new IllegalArgumentException("You need to specify a profile to perform a routing request, see docs/core/profiles.md");

        if (request.getHints().has("vehicle"))
            throw new IllegalArgumentException("GHRequest may no longer contain a vehicle, use the profile parameter instead, see docs/core/profiles.md");
        if (request.getHints().has("weighting"))
            throw new IllegalArgumentException("GHRequest may no longer contain a weighting, use the profile parameter instead, see docs/core/profiles.md");
        if (request.getHints().has(Parameters.Routing.TURN_COSTS))
            throw new IllegalArgumentException("GHRequest may no longer contain the turn_costs=true/false parameter, use the profile parameter instead, see docs/core/profiles.md");
        if (request.getHints().has(Parameters.Routing.EDGE_BASED))
            throw new IllegalArgumentException("GHRequest may no longer contain the edge_based=true/false parameter, use the profile parameter instead, see docs/core/profiles.md");

        if (request.getPoints().isEmpty())
            throw new IllegalArgumentException("You have to pass at least one point");
        checkIfPointsAreInBounds(request.getPoints());

        if (request.getHeadings().size() > 1 && request.getHeadings().size() != request.getPoints().size())
            throw new IllegalArgumentException("The number of 'heading' parameters must be zero, one "
                    + "or equal to the number of points (" + request.getPoints().size() + ")");
        for (int i = 0; i < request.getHeadings().size(); i++)
            if (!GHRequest.isAzimuthValue(request.getHeadings().get(i)))
                throw new IllegalArgumentException("Heading for point " + i + " must be in range [0,360) or NaN, but was: " + request.getHeadings().get(i));

        if (request.getPointHints().size() > 0 && request.getPointHints().size() != request.getPoints().size())
            throw new IllegalArgumentException("If you pass " + POINT_HINT + ", you need to pass exactly one hint for every point, empty hints will be ignored");
        if (request.getCurbsides().size() > 0 && request.getCurbsides().size() != request.getPoints().size())
            throw new IllegalArgumentException("If you pass " + CURBSIDE + ", you need to pass exactly one curbside for every point, empty curbsides will be ignored");

        boolean disableCH = getDisableCH(request.getHints());
        if (chEnabled && !routerConfig.isCHDisablingAllowed() && disableCH)
            throw new IllegalArgumentException("Disabling CH not allowed on the server-side");

        boolean disableLM = getDisableLM(request.getHints());
        if (lmEnabled && !routerConfig.isLMDisablingAllowed() && disableLM)
            throw new IllegalArgumentException("Disabling LM not allowed on the server-side");

        if (chEnabled && !disableCH) {
            if (!request.getHeadings().isEmpty())
                throw new IllegalArgumentException("The 'heading' parameter is currently not supported for speed mode, you need to disable speed mode with `ch.disable=true`. See issue #483");

            if (getPassThrough(request.getHints()))
                throw new IllegalArgumentException("The '" + Parameters.Routing.PASS_THROUGH + "' parameter is currently not supported for speed mode, you need to disable speed mode with `ch.disable=true`. See issue #1765");

            if (request.getHints().has(Parameters.Routing.BLOCK_AREA))
                throw new IllegalArgumentException("When CH is enabled the " + Parameters.Routing.BLOCK_AREA + " cannot be specified");
        } else {
            checkNonChMaxWaypointDistance(request.getPoints());
        }
    }

    private List<String> getTurnCostProfiles() {
        List<String> turnCostProfiles = new ArrayList<>();
        for (Profile p : profilesByName.values()) {
            if (p.isTurnCosts()) {
                turnCostProfiles.add(p.getName());
            }
        }
        return turnCostProfiles;
    }

    private static boolean getDisableLM(PMap hints) {
        return hints.getBool(Parameters.Landmark.DISABLE, false);
    }

    private static boolean getDisableCH(PMap hints) {
        return hints.getBool(Parameters.CH.DISABLE, false);
    }

    private static boolean getPassThrough(PMap hints) {
        return hints.getBool(PASS_THROUGH, false);
    }

    private void checkIfPointsAreInBounds(List<GHPoint> points) {
        BBox bounds = ghStorage.getBounds();
        for (int i = 0; i < points.size(); i++) {
            GHPoint point = points.get(i);
            if (!bounds.contains(point.getLat(), point.getLon())) {
                throw new PointOutOfBoundsException("Point " + i + " is out of bounds: " + point + ", the bounds are: " + bounds, i);
            }
        }
    }

    private void checkNonChMaxWaypointDistance(List<GHPoint> points) {
        if (routerConfig.getNonChMaxWaypointDistance() == Integer.MAX_VALUE) {
            return;
        }
        GHPoint lastPoint = points.get(0);
        GHPoint point;
        double dist;
        for (int i = 1; i < points.size(); i++) {
            point = points.get(i);
            dist = DIST_EARTH.calcDist(lastPoint.getLat(), lastPoint.getLon(), point.getLat(), point.getLon());
            if (dist > routerConfig.getNonChMaxWaypointDistance()) {
                Map<String, Object> detailMap = new HashMap<>(2);
                detailMap.put("from", i - 1);
                detailMap.put("to", i);
                throw new PointDistanceExceededException("Point " + i + " is too far from Point " + (i - 1) + ": " + point, detailMap);
            }
            lastPoint = point;
        }
    }
}
