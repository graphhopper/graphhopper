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
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.lm.LMRoutingAlgorithmFactory;
import com.graphhopper.routing.lm.LandmarkStorage;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.routing.weighting.custom.CustomWeighting;
import com.graphhopper.routing.weighting.custom.FindMinMax;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.RoutingCHGraph;
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

import static com.graphhopper.config.TurnCostsConfig.INFINITE_U_TURN_COSTS;
import static com.graphhopper.util.DistanceCalcEarth.DIST_EARTH;
import static com.graphhopper.util.Parameters.Algorithms.ALT_ROUTE;
import static com.graphhopper.util.Parameters.Algorithms.ROUND_TRIP;
import static com.graphhopper.util.Parameters.Routing.*;

public class Router {
    protected final BaseGraph graph;
    protected final EncodingManager encodingManager;
    protected final LocationIndex locationIndex;
    protected final Map<String, Profile> profilesByName;
    protected final PathDetailsBuilderFactory pathDetailsBuilderFactory;
    protected final TranslationMap translationMap;
    protected final RouterConfig routerConfig;
    protected final WeightingFactory weightingFactory;
    protected final Map<String, RoutingCHGraph> chGraphs;
    protected final Map<String, LandmarkStorage> landmarks;
    protected final boolean chEnabled;
    protected final boolean lmEnabled;

    public Router(BaseGraph graph, EncodingManager encodingManager, LocationIndex locationIndex,
                  Map<String, Profile> profilesByName, PathDetailsBuilderFactory pathDetailsBuilderFactory,
                  TranslationMap translationMap, RouterConfig routerConfig, WeightingFactory weightingFactory,
                  Map<String, RoutingCHGraph> chGraphs, Map<String, LandmarkStorage> landmarks) {
        this.graph = graph;
        this.encodingManager = encodingManager;
        this.locationIndex = locationIndex;
        this.profilesByName = profilesByName;
        this.pathDetailsBuilderFactory = pathDetailsBuilderFactory;
        this.translationMap = translationMap;
        this.routerConfig = routerConfig;
        this.weightingFactory = weightingFactory;
        this.chGraphs = chGraphs;
        this.landmarks = landmarks;
        // note that his is not the same as !ghStorage.getCHConfigs().isEmpty(), because the GHStorage might have some
        // CHGraphs that were not built yet (and possibly no CH profiles were configured).
        this.chEnabled = !chGraphs.isEmpty();
        this.lmEnabled = !landmarks.isEmpty();

        for (String profile : profilesByName.keySet()) {
            if (!encodingManager.hasEncodedValue(Subnetwork.key(profile)))
                throw new IllegalStateException("The profile '" + profile + "' needs an EncodedValue '" + Subnetwork.key(profile) + "'");
        }
    }

    public GHResponse route(GHRequest request) {
        try {
            checkNoLegacyParameters(request);
            checkAtLeastOnePoint(request);
            checkIfPointsAreInBounds(request.getPoints());
            checkHeadings(request);
            checkPointHints(request);
            checkCurbsides(request);
            checkNoBlockArea(request);

            Solver solver = createSolver(request);
            solver.checkRequest();
            solver.init();

            if (ROUND_TRIP.equalsIgnoreCase(request.getAlgorithm())) {
                if (!(solver instanceof FlexSolver))
                    throw new IllegalArgumentException("algorithm=round_trip only works with a flexible algorithm");
                return routeRoundTrip(request, (FlexSolver) solver);
            } else if (ALT_ROUTE.equalsIgnoreCase(request.getAlgorithm())) {
                return routeAlt(request, solver);
            } else {
                return routeVia(request, solver);
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

    private void checkNoLegacyParameters(GHRequest request) {
        if (request.getHints().has("vehicle"))
            throw new IllegalArgumentException("GHRequest may no longer contain a vehicle, use the profile parameter instead, see docs/core/profiles.md");
        if (request.getHints().has("weighting"))
            throw new IllegalArgumentException("GHRequest may no longer contain a weighting, use the profile parameter instead, see docs/core/profiles.md");
        if (request.getHints().has(Parameters.Routing.TURN_COSTS))
            throw new IllegalArgumentException("GHRequest may no longer contain the turn_costs=true/false parameter, use the profile parameter instead, see docs/core/profiles.md");
        if (request.getHints().has(Parameters.Routing.EDGE_BASED))
            throw new IllegalArgumentException("GHRequest may no longer contain the edge_based=true/false parameter, use the profile parameter instead, see docs/core/profiles.md");
    }

    private void checkAtLeastOnePoint(GHRequest request) {
        if (request.getPoints().isEmpty())
            throw new IllegalArgumentException("You have to pass at least one point");
    }

    private void checkIfPointsAreInBounds(List<GHPoint> points) {
        BBox bounds = graph.getBounds();
        for (int i = 0; i < points.size(); i++) {
            GHPoint point = points.get(i);
            if (!bounds.contains(point.getLat(), point.getLon())) {
                throw new PointOutOfBoundsException("Point " + i + " is out of bounds: " + point + ", the bounds are: " + bounds, i);
            }
        }
    }

    private void checkHeadings(GHRequest request) {
        if (request.getHeadings().size() > 1 && request.getHeadings().size() != request.getPoints().size())
            throw new IllegalArgumentException("The number of 'heading' parameters must be zero, one "
                    + "or equal to the number of points (" + request.getPoints().size() + ")");
        for (int i = 0; i < request.getHeadings().size(); i++)
            if (!GHRequest.isAzimuthValue(request.getHeadings().get(i)))
                throw new IllegalArgumentException("Heading for point " + i + " must be in range [0,360) or NaN, but was: " + request.getHeadings().get(i));
    }

    private void checkPointHints(GHRequest request) {
        if (!request.getPointHints().isEmpty() && request.getPointHints().size() != request.getPoints().size())
            throw new IllegalArgumentException("If you pass " + POINT_HINT + ", you need to pass exactly one hint for every point, empty hints will be ignored");
    }

    private void checkCurbsides(GHRequest request) {
        if (!request.getCurbsides().isEmpty() && request.getCurbsides().size() != request.getPoints().size())
            throw new IllegalArgumentException("If you pass " + CURBSIDE + ", you need to pass exactly one curbside for every point, empty curbsides will be ignored");
    }

    private void checkNoBlockArea(GHRequest request) {
        if (request.getHints().has("block_area"))
            throw new IllegalArgumentException("The `block_area` parameter is no longer supported. Use a custom model with `areas` instead.");
    }

    protected Solver createSolver(GHRequest request) {
        final boolean disableCH = getDisableCH(request.getHints());
        final boolean disableLM = getDisableLM(request.getHints());
        if (chEnabled && !disableCH) {
            return createCHSolver(request, profilesByName, routerConfig, encodingManager, chGraphs);
        } else if (lmEnabled && !disableLM) {
            return createLMSolver(request, profilesByName, routerConfig, encodingManager, weightingFactory, graph, locationIndex, landmarks);
        } else {
            return createFlexSolver(request, profilesByName, routerConfig, encodingManager, weightingFactory, graph, locationIndex);
        }
    }

    protected Solver createCHSolver(GHRequest request, Map<String, Profile> profilesByName, RouterConfig routerConfig,
                                    EncodingManager encodingManager, Map<String, RoutingCHGraph> chGraphs) {
        return new CHSolver(request, profilesByName, routerConfig, encodingManager, chGraphs);
    }

    protected Solver createLMSolver(GHRequest request, Map<String, Profile> profilesByName, RouterConfig routerConfig,
                                    EncodingManager encodingManager, WeightingFactory weightingFactory, BaseGraph baseGraph,
                                    LocationIndex locationIndex, Map<String, LandmarkStorage> landmarks) {
        return new LMSolver(request, profilesByName, routerConfig, encodingManager, weightingFactory, baseGraph, locationIndex, landmarks);
    }

    protected Solver createFlexSolver(GHRequest request, Map<String, Profile> profilesByName, RouterConfig routerConfig,
                                      EncodingManager encodingManager, WeightingFactory weightingFactory, BaseGraph baseGraph,
                                      LocationIndex locationIndex) {
        return new FlexSolver(request, profilesByName, routerConfig, encodingManager, weightingFactory, baseGraph, locationIndex);
    }

    protected GHResponse routeRoundTrip(GHRequest request, FlexSolver solver) {
        GHResponse ghRsp = new GHResponse();
        StopWatch sw = new StopWatch().start();
        double startHeading = request.getHeadings().isEmpty() ? Double.NaN : request.getHeadings().get(0);
        RoundTripRouting.Params params = new RoundTripRouting.Params(request.getHints(), startHeading, routerConfig.getMaxRoundTripRetries());
        List<Snap> snaps = RoundTripRouting.lookup(request.getPoints(), solver.createSnapFilter(), locationIndex, params);
        ghRsp.addDebugInfo("idLookup:" + sw.stop().getSeconds() + "s");

        QueryGraph queryGraph = QueryGraph.create(graph, snaps);
        FlexiblePathCalculator pathCalculator = solver.createPathCalculator(queryGraph);

        RoundTripRouting.Result result = RoundTripRouting.calcPaths(snaps, pathCalculator);
        // we merge the different legs of the roundtrip into one response path
        // note that the waypoints are not just the snapped points of the snaps, as usual, because we do some kind of tweak
        // to avoid 'unnecessary tails' in the roundtrip algo
        ResponsePath responsePath = concatenatePaths(request, solver.weighting, queryGraph, result.paths, result.wayPoints);
        ghRsp.add(responsePath);
        ghRsp.getHints().putObject("visited_nodes.sum", result.visitedNodes);
        ghRsp.getHints().putObject("visited_nodes.average", (float) result.visitedNodes / (snaps.size() - 1));
        return ghRsp;
    }

    protected GHResponse routeAlt(GHRequest request, Solver solver) {
        if (request.getPoints().size() > 2)
            throw new IllegalArgumentException("Currently alternative routes work only with start and end point. You tried to use: " + request.getPoints().size() + " points");
        GHResponse ghRsp = new GHResponse();
        StopWatch sw = new StopWatch().start();
        DirectedEdgeFilter directedEdgeFilter = solver.createDirectedEdgeFilter();
        List<Snap> snaps = ViaRouting.lookup(encodingManager, request.getPoints(), solver.createSnapFilter(), locationIndex,
                request.getSnapPreventions(), request.getPointHints(), directedEdgeFilter, request.getHeadings());
        ghRsp.addDebugInfo("idLookup:" + sw.stop().getSeconds() + "s");
        QueryGraph queryGraph = QueryGraph.create(graph, snaps);
        PathCalculator pathCalculator = solver.createPathCalculator(queryGraph);
        boolean passThrough = getPassThrough(request.getHints());
        String curbsideStrictness = getCurbsideStrictness(request.getHints());
        if (passThrough)
            throw new IllegalArgumentException("Alternative paths and " + PASS_THROUGH + " at the same time is currently not supported");
        if (!request.getCurbsides().isEmpty())
            throw new IllegalArgumentException("Alternative paths do not support the " + CURBSIDE + " parameter yet");

        ViaRouting.Result result = ViaRouting.calcPaths(request.getPoints(), queryGraph, snaps, directedEdgeFilter,
                pathCalculator, request.getCurbsides(), curbsideStrictness, request.getHeadings(), passThrough);
        if (result.paths.isEmpty())
            throw new RuntimeException("Empty paths for alternative route calculation not expected");

        // each path represents a different alternative and we do the path merging for each of them
        PathMerger pathMerger = createPathMerger(request, solver.weighting, queryGraph);
        for (Path path : result.paths) {
            PointList waypoints = getWaypoints(snaps);
            ResponsePath responsePath = pathMerger.doWork(waypoints, Collections.singletonList(path), encodingManager, translationMap.getWithFallBack(request.getLocale()));
            ghRsp.add(responsePath);
        }
        ghRsp.getHints().putObject("visited_nodes.sum", result.visitedNodes);
        ghRsp.getHints().putObject("visited_nodes.average", (float) result.visitedNodes / (snaps.size() - 1));
        return ghRsp;
    }

    protected GHResponse routeVia(GHRequest request, Solver solver) {
        GHResponse ghRsp = new GHResponse();
        StopWatch sw = new StopWatch().start();
        DirectedEdgeFilter directedEdgeFilter = solver.createDirectedEdgeFilter();
        List<Snap> snaps = ViaRouting.lookup(encodingManager, request.getPoints(), solver.createSnapFilter(), locationIndex,
                request.getSnapPreventions(), request.getPointHints(), directedEdgeFilter, request.getHeadings());
        ghRsp.addDebugInfo("idLookup:" + sw.stop().getSeconds() + "s");
        // (base) query graph used to resolve headings, curbsides etc. this is not necessarily the same thing as
        // the (possibly implementation specific) query graph used by PathCalculator
        QueryGraph queryGraph = QueryGraph.create(graph, snaps);
        PathCalculator pathCalculator = solver.createPathCalculator(queryGraph);
        boolean passThrough = getPassThrough(request.getHints());
        String curbsideStrictness = getCurbsideStrictness(request.getHints());
        ViaRouting.Result result = ViaRouting.calcPaths(request.getPoints(), queryGraph, snaps, directedEdgeFilter,
                pathCalculator, request.getCurbsides(), curbsideStrictness, request.getHeadings(), passThrough);

        if (request.getPoints().size() != result.paths.size() + 1)
            throw new RuntimeException("There should be exactly one more point than paths. points:" + request.getPoints().size() + ", paths:" + result.paths.size());

        // here each path represents one leg of the via-route and we merge them all together into one response path
        ResponsePath responsePath = concatenatePaths(request, solver.weighting, queryGraph, result.paths, getWaypoints(snaps));
        responsePath.addDebugInfo(result.debug);
        ghRsp.add(responsePath);
        ghRsp.getHints().putObject("visited_nodes.sum", result.visitedNodes);
        ghRsp.getHints().putObject("visited_nodes.average", (float) result.visitedNodes / (snaps.size() - 1));
        return ghRsp;
    }

    private PathMerger createPathMerger(GHRequest request, Weighting weighting, Graph graph) {
        boolean enableInstructions = request.getHints().getBool(Parameters.Routing.INSTRUCTIONS, routerConfig.isInstructionsEnabled());
        boolean calcPoints = request.getHints().getBool(Parameters.Routing.CALC_POINTS, routerConfig.isCalcPoints());
        double wayPointMaxDistance = request.getHints().getDouble(Parameters.Routing.WAY_POINT_MAX_DISTANCE, 0.5);
        double elevationWayPointMaxDistance = request.getHints().getDouble(ELEVATION_WAY_POINT_MAX_DISTANCE, routerConfig.getElevationWayPointMaxDistance());

        RamerDouglasPeucker peucker = new RamerDouglasPeucker().
                setMaxDistance(wayPointMaxDistance).
                setElevationMaxDistance(elevationWayPointMaxDistance);
        PathMerger pathMerger = new PathMerger(graph, weighting).
                setCalcPoints(calcPoints).
                setRamerDouglasPeucker(peucker).
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
        PointList pointList = new PointList(snaps.size(), graph.getNodeAccess().is3D());
        for (Snap snap : snaps) {
            pointList.add(snap.getSnappedPoint());
        }
        return pointList;
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

    private static String getCurbsideStrictness(PMap hints) {
        if (hints.has(CURBSIDE_STRICTNESS)) return hints.getString(CURBSIDE_STRICTNESS, "strict");

        // legacy
        return hints.getBool("force_curbside", true) ? "strict" : "soft";
    }

    public static abstract class Solver {
        protected final GHRequest request;
        private final Map<String, Profile> profilesByName;
        private final RouterConfig routerConfig;
        protected Profile profile;
        protected Weighting weighting;
        protected final EncodedValueLookup lookup;

        public Solver(GHRequest request, Map<String, Profile> profilesByName, RouterConfig routerConfig, EncodedValueLookup lookup) {
            this.request = request;
            this.profilesByName = profilesByName;
            this.routerConfig = routerConfig;
            this.lookup = lookup;
        }

        protected void checkRequest() {
            checkProfileSpecified();
            checkMaxVisitedNodes();
        }

        private void checkProfileSpecified() {
            if (Helper.isEmpty(request.getProfile()))
                throw new IllegalArgumentException("You need to specify a profile to perform a routing request, see docs/core/profiles.md");
        }

        private void checkMaxVisitedNodes() {
            if (getMaxVisitedNodes(request.getHints()) > routerConfig.getMaxVisitedNodes())
                throw new IllegalArgumentException("The max_visited_nodes parameter has to be below or equal to:" + routerConfig.getMaxVisitedNodes());
        }

        private void init() {
            profile = getProfile();
            checkProfileCompatibility();
            weighting = createWeighting();
        }

        protected Profile getProfile() {
            Profile profile = profilesByName.get(request.getProfile());
            if (profile == null)
                throw new IllegalArgumentException("The requested profile '" + request.getProfile() + "' does not exist.\nAvailable profiles: " + profilesByName.keySet());
            return profile;
        }

        protected void checkProfileCompatibility() {
            if (!profile.hasTurnCosts() && !request.getCurbsides().isEmpty())
                throw new IllegalArgumentException("To make use of the " + CURBSIDE + " parameter you need to use a profile that supports turn costs" +
                        "\nThe following profiles do support turn costs: " + getTurnCostProfiles());
            if (request.getCustomModel() != null && !CustomWeighting.NAME.equals(profile.getWeighting()))
                throw new IllegalArgumentException("The requested profile '" + request.getProfile() + "' cannot be used with `custom_model`, because it has weighting=" + profile.getWeighting());

            final int uTurnCostsInt = request.getHints().getInt(Parameters.Routing.U_TURN_COSTS, INFINITE_U_TURN_COSTS);
            if (uTurnCostsInt != INFINITE_U_TURN_COSTS && !profile.hasTurnCosts()) {
                throw new IllegalArgumentException("Finite u-turn costs can only be used for edge-based routing, you need to use a profile that" +
                        " supports turn costs. Currently the following profiles that support turn costs are available: " + getTurnCostProfiles());
            }
        }

        protected abstract Weighting createWeighting();

        protected EdgeFilter createSnapFilter() {
            return new DefaultSnapFilter(weighting, lookup.getBooleanEncodedValue(Subnetwork.key(profile.getName())));
        }

        protected DirectedEdgeFilter createDirectedEdgeFilter() {
            BooleanEncodedValue inSubnetworkEnc = lookup.getBooleanEncodedValue(Subnetwork.key(profile.getName()));
            return (edgeState, reverse) -> !edgeState.get(inSubnetworkEnc) && Double.isFinite(weighting.calcEdgeWeight(edgeState, reverse));
        }

        protected abstract PathCalculator createPathCalculator(QueryGraph queryGraph);

        private List<String> getTurnCostProfiles() {
            List<String> turnCostProfiles = new ArrayList<>();
            for (Profile p : profilesByName.values()) {
                if (p.hasTurnCosts()) {
                    turnCostProfiles.add(p.getName());
                }
            }
            return turnCostProfiles;
        }

        int getMaxVisitedNodes(PMap hints) {
            return hints.getInt(Parameters.Routing.MAX_VISITED_NODES, routerConfig.getMaxVisitedNodes());
        }

        long getTimeoutMillis(PMap hints) {
            // we silently use the minimum between the requested timeout and the server-side limit
            // see: https://github.com/graphhopper/graphhopper/pull/2795#discussion_r1168371343
            return Math.min(routerConfig.getTimeoutMillis(), hints.getLong(TIMEOUT_MS, routerConfig.getTimeoutMillis()));
        }
    }

    private static class CHSolver extends Solver {
        private final Map<String, RoutingCHGraph> chGraphs;

        CHSolver(GHRequest request, Map<String, Profile> profilesByName, RouterConfig routerConfig, EncodedValueLookup lookup, Map<String, RoutingCHGraph> chGraphs) {
            super(request, profilesByName, routerConfig, lookup);
            this.chGraphs = chGraphs;
        }

        @Override
        protected void checkRequest() {
            super.checkRequest();
            if (!request.getHeadings().isEmpty())
                throw new IllegalArgumentException("The 'heading' parameter is currently not supported for speed mode, you need to disable speed mode with `ch.disable=true`. See issue #483");

            if (getPassThrough(request.getHints()))
                throw new IllegalArgumentException("The '" + Parameters.Routing.PASS_THROUGH + "' parameter is currently not supported for speed mode, you need to disable speed mode with `ch.disable=true`. See issue #1765");

            if (request.getCustomModel() != null)
                throw new IllegalArgumentException("The 'custom_model' parameter is currently not supported for speed mode, you need to disable speed mode with `ch.disable=true`.");

            if (ROUND_TRIP.equalsIgnoreCase(request.getAlgorithm()))
                throw new IllegalArgumentException("algorithm=round_trip cannot be used with CH");
        }

        @Override
        protected Weighting createWeighting() {
            // todo: do not allow things like short_fastest.distance_factor or u_turn_costs unless CH is disabled
            // and only under certain conditions for LM

            // the request hints are ignored for CH as we cannot change the profile after the preparation like this.
            // the weighting here needs to be the same as the one we later use for CHPathCalculator and as it was
            // used for the preparation
            return getRoutingCHGraph(profile.getName()).getWeighting();
        }

        @Override
        protected PathCalculator createPathCalculator(QueryGraph queryGraph) {
            PMap opts = new PMap(request.getHints());
            opts.putObject(ALGORITHM, request.getAlgorithm());
            opts.putObject(MAX_VISITED_NODES, getMaxVisitedNodes(request.getHints()));
            opts.putObject(TIMEOUT_MS, getTimeoutMillis(request.getHints()));
            return new CHPathCalculator(new CHRoutingAlgorithmFactory(getRoutingCHGraph(profile.getName()), queryGraph), opts);
        }

        private RoutingCHGraph getRoutingCHGraph(String profileName) {
            RoutingCHGraph chGraph = chGraphs.get(profileName);
            if (chGraph == null)
                throw new IllegalArgumentException("Cannot find CH preparation for the requested profile: '" + profileName + "'" +
                        "\nYou can try disabling CH using " + Parameters.CH.DISABLE + "=true" +
                        "\navailable CH profiles: " + chGraphs.keySet());
            return chGraph;
        }
    }

    public static class FlexSolver extends Solver {
        protected final RouterConfig routerConfig;
        private final WeightingFactory weightingFactory;
        private final BaseGraph baseGraph;
        private final LocationIndex locationIndex;

        protected FlexSolver(GHRequest request, Map<String, Profile> profilesByName, RouterConfig routerConfig,
                             EncodedValueLookup lookup, WeightingFactory weightingFactory, BaseGraph graph, LocationIndex locationIndex) {
            super(request, profilesByName, routerConfig, lookup);
            this.routerConfig = routerConfig;
            this.weightingFactory = weightingFactory;
            this.baseGraph = graph;
            this.locationIndex = locationIndex;
        }

        @Override
        protected void checkRequest() {
            super.checkRequest();
            checkNonChMaxWaypointDistance(request.getPoints());
        }

        @Override
        protected Weighting createWeighting() {
            PMap requestHints = new PMap(request.getHints());
            requestHints.putObject(CustomModel.KEY, request.getCustomModel());
            return weightingFactory.createWeighting(profile, requestHints, false);
        }

        @Override
        protected FlexiblePathCalculator createPathCalculator(QueryGraph queryGraph) {
            RoutingAlgorithmFactory algorithmFactory = new RoutingAlgorithmFactorySimple();
            return new FlexiblePathCalculator(queryGraph, algorithmFactory, weighting, getAlgoOpts());
        }

        protected AlgorithmOptions getAlgoOpts() {
            AlgorithmOptions algoOpts = new AlgorithmOptions().
                    setAlgorithm(request.getAlgorithm()).
                    setTraversalMode(profile.hasTurnCosts() ? TraversalMode.EDGE_BASED : TraversalMode.NODE_BASED).
                    setMaxVisitedNodes(getMaxVisitedNodes(request.getHints())).
                    setTimeoutMillis(getTimeoutMillis(request.getHints())).
                    setHints(request.getHints());

            // use A* for round trips
            if (ROUND_TRIP.equalsIgnoreCase(request.getAlgorithm())) {
                algoOpts.setAlgorithm(Parameters.Algorithms.ASTAR_BI);
                algoOpts.getHints().putObject(Parameters.Algorithms.AStarBi.EPSILON, 2);
            }
            return algoOpts;
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

    private static class LMSolver extends FlexSolver {
        private final Map<String, LandmarkStorage> landmarks;

        LMSolver(GHRequest request, Map<String, Profile> profilesByName, RouterConfig routerConfig, EncodedValueLookup lookup,
                 WeightingFactory weightingFactory, BaseGraph graph, LocationIndex locationIndex, Map<String, LandmarkStorage> landmarks) {
            super(request, profilesByName, routerConfig, lookup, weightingFactory, graph, locationIndex);
            this.landmarks = landmarks;
        }

        @Override
        protected FlexiblePathCalculator createPathCalculator(QueryGraph queryGraph) {
            // for now do not allow mixing CH&LM #1082,#1889
            LandmarkStorage landmarkStorage = landmarks.get(profile.getName());
            if (landmarkStorage == null)
                throw new IllegalArgumentException("Cannot find LM preparation for the requested profile: '" + profile.getName() + "'" +
                        "\nYou can try disabling LM using " + Parameters.Landmark.DISABLE + "=true" +
                        "\navailable LM profiles: " + landmarks.keySet());
            if (request.getCustomModel() != null)
                FindMinMax.checkLMConstraints(profile.getCustomModel(), request.getCustomModel(), lookup);
            RoutingAlgorithmFactory routingAlgorithmFactory = new LMRoutingAlgorithmFactory(landmarkStorage).setDefaultActiveLandmarks(routerConfig.getActiveLandmarkCount());
            return new FlexiblePathCalculator(queryGraph, routingAlgorithmFactory, weighting, getAlgoOpts());
        }
    }
}
