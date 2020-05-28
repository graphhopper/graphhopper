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

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.ch.CHRoutingAlgorithmFactory;
import com.graphhopper.routing.lm.LMRoutingAlgorithmFactory;
import com.graphhopper.routing.lm.LandmarkStorage;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.template.AlternativeRoutingTemplate;
import com.graphhopper.routing.template.RoundTripRoutingTemplate;
import com.graphhopper.routing.template.RoutingTemplate;
import com.graphhopper.routing.template.ViaRoutingTemplate;
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
import com.graphhopper.util.exceptions.PointOutOfBoundsException;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;

import java.util.*;

import static com.graphhopper.routing.weighting.Weighting.INFINITE_U_TURN_COSTS;
import static com.graphhopper.util.Helper.DIST_EARTH;
import static com.graphhopper.util.Parameters.Algorithms.*;
import static com.graphhopper.util.Parameters.Routing.CURBSIDE;
import static com.graphhopper.util.Parameters.Routing.POINT_HINT;

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
    private final Map<String, CHGraph> chGraphs;
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
        this.chGraphs = chGraphs;
        this.landmarks = landmarks;
        // note that his is not the same as !ghStorage.getCHConfigs().isEmpty(), because the GHStorage might have some
        // CHGraphs that were not built yet (and possibly no CH profiles were configured). If this wasn't so we would
        // not need the chGraphs map at all here, because we could get the CHGraphs from GHStorage
        this.chEnabled = !chGraphs.isEmpty();
        this.lmEnabled = !landmarks.isEmpty();
    }

    public List<Path> route(GHRequest request, GHResponse ghRsp) {
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
            TraversalMode tMode = profile.isTurnCosts() ? TraversalMode.EDGE_BASED : TraversalMode.NODE_BASED;
            RoutingAlgorithmFactory algorithmFactory = getAlgorithmFactory(profile.getName(), disableCH, disableLM);
            Weighting weighting;
            Graph graph = ghStorage;
            if (chEnabled && !disableCH) {
                if (!(algorithmFactory instanceof CHRoutingAlgorithmFactory))
                    throw new IllegalStateException("Although CH was enabled a non-CH algorithm factory was returned " + algorithmFactory);

                if (request.getHints().has(Parameters.Routing.BLOCK_AREA))
                    throw new IllegalArgumentException("When CH is enabled the " + Parameters.Routing.BLOCK_AREA + " cannot be specified");

                CHConfig chConfig = ((CHRoutingAlgorithmFactory) algorithmFactory).getCHConfig();
                weighting = chConfig.getWeighting();
                graph = chGraphs.get(chConfig.getName());
                // we know this exists because we already got the algorithm factory this way -> will be cleaned up soon
            } else {
                checkNonChMaxWaypointDistance(request.getPoints());
                final int uTurnCostsInt = request.getHints().getInt(Parameters.Routing.U_TURN_COSTS, INFINITE_U_TURN_COSTS);
                if (uTurnCostsInt != INFINITE_U_TURN_COSTS && !tMode.isEdgeBased()) {
                    throw new IllegalArgumentException("Finite u-turn costs can only be used for edge-based routing, you need to use a profile that" +
                            " supports turn costs. Currently the following profiles that support turn costs are available: " + getTurnCostProfiles());
                }
                FlagEncoder encoder = encodingManager.getEncoder(profile.getVehicle());
                weighting = weightingFactory.createWeighting(profile, request.getHints(), false);
                if (request.getHints().has(Parameters.Routing.BLOCK_AREA))
                    weighting = new BlockAreaWeighting(weighting, GraphEdgeIdFinder.createBlockArea(ghStorage, locationIndex,
                            request.getPoints(), request.getHints(), DefaultEdgeFilter.allEdges(encoder)));
            }
            ghRsp.addDebugInfo("tmode:" + tMode.toString());

            String algoStr = request.getAlgorithm();
            if (algoStr.isEmpty())
                algoStr = chEnabled && !disableCH ? DIJKSTRA_BI : ASTAR_BI;
            RoutingTemplate routingTemplate = createRoutingTemplate(request, ghRsp, algoStr, weighting);

            StopWatch sw = new StopWatch().start();
            List<QueryResult> qResults = routingTemplate.lookup(request.getPoints());
            ghRsp.addDebugInfo("idLookup:" + sw.stop().getSeconds() + "s");
            if (ghRsp.hasErrors())
                return Collections.emptyList();

            QueryGraph queryGraph = QueryGraph.create(graph, qResults);
            int maxVisitedNodesForRequest = request.getHints().getInt(Parameters.Routing.MAX_VISITED_NODES, routerConfig.getMaxVisitedNodes());
            if (maxVisitedNodesForRequest > routerConfig.getMaxVisitedNodes())
                throw new IllegalArgumentException("The max_visited_nodes parameter has to be below or equal to:" + routerConfig.getMaxVisitedNodes());

            AlgorithmOptions algoOpts = AlgorithmOptions.start().
                    algorithm(algoStr).
                    traversalMode(tMode).
                    weighting(weighting).
                    maxVisitedNodes(maxVisitedNodesForRequest).
                    hints(request.getHints()).
                    build();

            // do the actual route calculation !
            List<Path> altPaths = routingTemplate.calcPaths(queryGraph, algorithmFactory, algoOpts);

            boolean tmpEnableInstructions = request.getHints().getBool(Parameters.Routing.INSTRUCTIONS, encodingManager.isEnableInstructions());
            boolean tmpCalcPoints = request.getHints().getBool(Parameters.Routing.CALC_POINTS, routerConfig.isCalcPoints());
            double wayPointMaxDistance = request.getHints().getDouble(Parameters.Routing.WAY_POINT_MAX_DISTANCE, 1d);

            DouglasPeucker peucker = new DouglasPeucker().setMaxDistance(wayPointMaxDistance);
            PathMerger pathMerger = new PathMerger(queryGraph.getBaseGraph(), weighting).
                    setCalcPoints(tmpCalcPoints).
                    setDouglasPeucker(peucker).
                    setEnableInstructions(tmpEnableInstructions).
                    setPathDetailsBuilders(pathDetailsBuilderFactory, request.getPathDetails()).
                    setSimplifyResponse(routerConfig.isSimplifyResponse() && wayPointMaxDistance > 0);

            if (!request.getHeadings().isEmpty())
                pathMerger.setFavoredHeading(request.getHeadings().get(0));

            routingTemplate.finish(pathMerger, translationMap.getWithFallBack(request.getLocale()));
            return altPaths;
        } catch (IllegalArgumentException ex) {
            ghRsp.addError(ex);
            return Collections.emptyList();
        }
    }

    public RoutingAlgorithmFactory getAlgorithmFactory(String profile, boolean disableCH, boolean disableLM) {
        if (chEnabled && disableCH && !routerConfig.isCHDisablingAllowed()) {
            throw new IllegalArgumentException("Disabling CH is not allowed on the server side");
        }
        if (lmEnabled && disableLM && !routerConfig.isLMDisablingAllowed()) {
            throw new IllegalArgumentException("Disabling LM is not allowed on the server side");
        }

        // for now do not allow mixing CH&LM #1082,#1889
        if (chEnabled && !disableCH) {
            CHGraph chGraph = chGraphs.get(profile);
            if (chGraph == null)
                throw new IllegalArgumentException("Cannot find CH preparation for the requested profile: '" + profile + "'" +
                        "\nYou can try disabling CH using " + Parameters.CH.DISABLE + "=true" +
                        "\navailable CH profiles: " + chGraphs.keySet());
            return new CHRoutingAlgorithmFactory(chGraph);
        } else if (lmEnabled && !disableLM) {
            LandmarkStorage landmarkStorage = landmarks.get(profile);
            if (landmarkStorage == null)
                throw new IllegalArgumentException("Cannot find LM preparation for the requested profile: '" + profile + "'" +
                        "\nYou can try disabling LM using " + Parameters.Landmark.DISABLE + "=true" +
                        "\navailable LM profiles: " + landmarks.keySet());
            return new LMRoutingAlgorithmFactory(landmarkStorage).setDefaultActiveLandmarks(routerConfig.getActiveLandmarkCount());
        } else {
            return new RoutingAlgorithmFactorySimple();
        }
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

        // todonow: do not allow things like short_fastest.distance_factor or u_turn_costs unless CH is disabled and only under certain conditions for LM
        if (chEnabled && !disableCH) {
            if (!request.getHeadings().isEmpty())
                throw new IllegalArgumentException("The 'heading' parameter is currently not supported for speed mode, you need to disable speed mode with `ch.disable=true`. See issue #483");

            if (request.getHints().getBool(Parameters.Routing.PASS_THROUGH, false))
                throw new IllegalArgumentException("The '" + Parameters.Routing.PASS_THROUGH + "' parameter is currently not supported for speed mode, you need to disable speed mode with `ch.disable=true`. See issue #1765");
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

    private boolean getDisableLM(PMap hints) {
        return hints.getBool(Parameters.Landmark.DISABLE, false);
    }

    private boolean getDisableCH(PMap hints) {
        return hints.getBool(Parameters.CH.DISABLE, false);
    }

    protected RoutingTemplate createRoutingTemplate(GHRequest request, GHResponse ghRsp, String algoStr, Weighting weighting) {
        RoutingTemplate routingTemplate;
        if (ROUND_TRIP.equalsIgnoreCase(algoStr))
            routingTemplate = new RoundTripRoutingTemplate(request, ghRsp, locationIndex, encodingManager, weighting, routerConfig.getMaxRoundTripRetries());
        else if (ALT_ROUTE.equalsIgnoreCase(algoStr))
            routingTemplate = new AlternativeRoutingTemplate(request, ghRsp, locationIndex, encodingManager, weighting);
        else
            routingTemplate = new ViaRoutingTemplate(request, ghRsp, locationIndex, encodingManager, weighting);
        return routingTemplate;
    }

    private void checkIfPointsAreInBounds(List<GHPoint> points) {
        BBox bounds = ghStorage.getBounds();
        for (int i = 0; i < points.size(); i++) {
            GHPoint point = points.get(i);
            if (!bounds.contains(point.getLat(), point.getLon())) {
                throw new PointOutOfBoundsException("Point " + i + " is out of bounds: " + point, i);
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
        DistanceCalc calc = DIST_EARTH;
        for (int i = 1; i < points.size(); i++) {
            point = points.get(i);
            dist = calc.calcDist(lastPoint.getLat(), lastPoint.getLon(), point.getLat(), point.getLon());
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
