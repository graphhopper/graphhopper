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
import com.graphhopper.routing.ch.CHPreparationHandler;
import com.graphhopper.routing.ch.CHProfileSelectionException;
import com.graphhopper.routing.ch.CHProfileSelector;
import com.graphhopper.routing.ch.CHRoutingAlgorithmFactory;
import com.graphhopper.routing.lm.LMPreparationHandler;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.template.AlternativeRoutingTemplate;
import com.graphhopper.routing.template.RoundTripRoutingTemplate;
import com.graphhopper.routing.template.RoutingTemplate;
import com.graphhopper.routing.template.ViaRoutingTemplate;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.*;
import com.graphhopper.storage.CHProfile;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphEdgeIdFinder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.*;
import com.graphhopper.util.details.PathDetailsBuilderFactory;
import com.graphhopper.util.exceptions.PointDistanceExceededException;
import com.graphhopper.util.exceptions.PointOutOfBoundsException;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.graphhopper.routing.weighting.TurnCostProvider.NO_TURN_COST_PROVIDER;
import static com.graphhopper.routing.weighting.Weighting.INFINITE_U_TURN_COSTS;
import static com.graphhopper.util.Helper.DIST_3D;
import static com.graphhopper.util.Parameters.Algorithms.*;
import static com.graphhopper.util.Parameters.Routing.CURBSIDE;

public class GraphHopperRouter {
    private final EncodingManager encodingManager;
    private final GraphHopperStorage ghStorage;
    private final LocationIndex locationIndex;
    private final LMPreparationHandler lmPreparationHandler;
    private final List<CHProfile> chProfiles;
    // todonow: too costly to import all the time, do static init or pass via constructor?
    private TranslationMap trMap = new TranslationMap().doImport();
    private RoutingConfig routingConfig;
    private WeightingFactory weightingFactory = new DefaultWeightingFactory();
    private PathDetailsBuilderFactory pathBuilderFactory = new PathDetailsBuilderFactory();
    private boolean chDisablingAllowed;
    private boolean lmDisablingAllowed;
    private boolean chEnabled;
    private boolean lmEnabled;

    public GraphHopperRouter(GraphHopperStorage ghStorage, LocationIndex locationIndex,
                             // todonow: remove these dependencies
                             LMPreparationHandler lmPreparationHandler, CHPreparationHandler chPreparationHandler,
                             RoutingConfig routingConfig) {
        if (ghStorage.isClosed())
            throw new IllegalStateException("GH storage should not be closed");

        this.ghStorage = ghStorage;
        this.chProfiles = ghStorage.getCHProfiles();
        this.encodingManager = ghStorage.getEncodingManager();
        this.locationIndex = locationIndex;
        this.lmPreparationHandler = lmPreparationHandler;
        this.routingConfig = routingConfig;
        // todonow: maybe simply add setters for ch/lm enabled -> no need for lm/ch prep handlers here anymore
        chEnabled = chPreparationHandler.isEnabled();
        lmEnabled = lmPreparationHandler.isEnabled();
        // todonow: maybe these could go into routing config -> no need for lm/ch prep handlers here anymore
        chDisablingAllowed = chPreparationHandler.isDisablingAllowed();
        lmDisablingAllowed = lmPreparationHandler.isDisablingAllowed();
    }

    // todonow: these setters are not thread-safe, do we care? maybe use builder pattern instead? or just volatile?
    // at least document thread safety of this class, also there is lock mechanism in GraphHopper but what if this class
    // is used standalone?
    public void setWeightingFactory(WeightingFactory weightingFactory) {
        this.weightingFactory = weightingFactory;
    }

    public void setPathBuilderFactory(PathDetailsBuilderFactory pathBuilderFactory) {
        this.pathBuilderFactory = pathBuilderFactory;
    }

    public void setTranslationMap(TranslationMap trMap) {
        this.trMap = trMap;
    }

    public void setRoutingConfig(RoutingConfig routingConfig) {
        this.routingConfig = routingConfig;
    }

    public List<Path> route(GHRequest request, GHResponse ghRsp) {
        // todonow: what about read/write lock? As long as GHRouter is used from GraphHopper this is fine, but what if GHRouter is used standalone (which is desirable)
        // default handling
        String vehicle = request.getVehicle();
        if (vehicle.isEmpty()) {
            vehicle = getDefaultVehicle().toString();
            request.setVehicle(vehicle);
        }
        if (!encodingManager.hasEncoder(vehicle))
            throw new IllegalArgumentException("Vehicle not supported: " + vehicle + ". Supported are: " + encodingManager.toString());

        FlagEncoder encoder = encodingManager.getEncoder(vehicle);
        HintsMap hints = request.getHints();

        // we use edge-based routing if the encoder supports turn-costs *unless* the edge_based parameter is set
        // explicitly.
        TraversalMode tMode = encoder.supportsTurnCosts() ? TraversalMode.EDGE_BASED : TraversalMode.NODE_BASED;
        if (hints.has(Parameters.Routing.EDGE_BASED))
            tMode = hints.getBool(Parameters.Routing.EDGE_BASED, false) ? TraversalMode.EDGE_BASED : TraversalMode.NODE_BASED;

        if (tMode.isEdgeBased() && !encoder.supportsTurnCosts()) {
            throw new IllegalArgumentException("You need to set up a turn cost storage to make use of edge_based=true, e.g. use car|turn_costs=true");
        }

        if (!tMode.isEdgeBased() && !request.getCurbsides().isEmpty()) {
            throw new IllegalArgumentException("To make use of the " + CURBSIDE + " parameter you need to set " + Parameters.Routing.EDGE_BASED + " to true");
        }

        boolean disableCH = hints.getBool(Parameters.CH.DISABLE, false);
        if (!chDisablingAllowed && disableCH)
            throw new IllegalArgumentException("Disabling CH not allowed on the server-side");

        boolean disableLM = hints.getBool(Parameters.Landmark.DISABLE, false);
        if (!lmDisablingAllowed && disableLM)
            throw new IllegalArgumentException("Disabling LM not allowed on the server-side");

        if (chEnabled && !disableCH) {
            if (request.hasFavoredHeading(0))
                throw new IllegalArgumentException("The 'heading' parameter is currently not supported for speed mode, you need to disable speed mode with `ch.disable=true`. See issue #483");

            if (request.getHints().getBool(Parameters.Routing.PASS_THROUGH, false))
                throw new IllegalArgumentException("The '" + Parameters.Routing.PASS_THROUGH + "' parameter is currently not supported for speed mode, you need to disable speed mode with `ch.disable=true`. See issue #1765");
        }

        String algoStr = request.getAlgorithm();
        if (algoStr.isEmpty())
            algoStr = chEnabled && !disableCH ? DIJKSTRA_BI : ASTAR_BI;

        List<GHPoint> points = request.getPoints();
        // TODO Maybe we should think about a isRequestValid method that checks all that stuff that we could do to fail fast
        // For example see #734
        checkIfPointsAreInBounds(points);

        final int uTurnCostsInt = request.getHints().getInt(Parameters.Routing.U_TURN_COSTS, INFINITE_U_TURN_COSTS);
        if (uTurnCostsInt != INFINITE_U_TURN_COSTS && !tMode.isEdgeBased()) {
            throw new IllegalArgumentException("Finite u-turn costs can only be used for edge-based routing, use `" + Parameters.Routing.EDGE_BASED + "=true'");
        }
        TurnCostProvider turnCostProvider = (encoder.supportsTurnCosts() && tMode.isEdgeBased())
                ? new DefaultTurnCostProvider(encoder, ghStorage.getTurnCostStorage(), uTurnCostsInt)
                : NO_TURN_COST_PROVIDER;

        RoutingAlgorithmFactory algorithmFactory = getAlgorithmFactory(hints);
        Weighting weighting;
        Graph graph = ghStorage;
        if (chEnabled && !disableCH) {
            if (algorithmFactory instanceof CHRoutingAlgorithmFactory) {
                CHProfile chProfile = ((CHRoutingAlgorithmFactory) algorithmFactory).getCHProfile();
                weighting = chProfile.getWeighting();
                graph = ghStorage.getCHGraph(chProfile);
            } else {
                throw new IllegalStateException("Although CH was enabled a non-CH algorithm factory was returned " + algorithmFactory);
            }
        } else {
            checkNonChMaxWaypointDistance(points);
            weighting = weightingFactory.createWeighting(hints, encoder, turnCostProvider);
            if (hints.has(Parameters.Routing.BLOCK_AREA))
                weighting = new BlockAreaWeighting(weighting, createBlockArea(points, hints, DefaultEdgeFilter.allEdges(encoder)));
        }
        ghRsp.addDebugInfo("tmode:" + tMode.toString());

        RoutingTemplate routingTemplate;
        if (ROUND_TRIP.equalsIgnoreCase(algoStr))
            routingTemplate = new RoundTripRoutingTemplate(request, ghRsp, locationIndex, encodingManager, weighting, routingConfig.getMaxRoundTripRetries());
        else if (ALT_ROUTE.equalsIgnoreCase(algoStr))
            routingTemplate = new AlternativeRoutingTemplate(request, ghRsp, locationIndex, encodingManager, weighting);
        else
            routingTemplate = new ViaRoutingTemplate(request, ghRsp, locationIndex, encodingManager, weighting);

        StopWatch sw = new StopWatch().start();
        List<QueryResult> qResults = routingTemplate.lookup(points);
        ghRsp.addDebugInfo("idLookup:" + sw.stop().getSeconds() + "s");
        if (ghRsp.hasErrors())
            return Collections.emptyList();

        QueryGraph queryGraph = QueryGraph.lookup(graph, qResults);
        int maxVisitedNodesForRequest = hints.getInt(Parameters.Routing.MAX_VISITED_NODES, routingConfig.getMaxVisitedNodes());
        if (maxVisitedNodesForRequest > routingConfig.getMaxVisitedNodes())
            throw new IllegalArgumentException("The max_visited_nodes parameter has to be below or equal to:" + routingConfig.getMaxVisitedNodes());

        AlgorithmOptions algoOpts = AlgorithmOptions.start().
                algorithm(algoStr).traversalMode(tMode).weighting(weighting).
                maxVisitedNodes(maxVisitedNodesForRequest).
                hints(hints).
                build();

        // do the actual route calculation !
        List<Path> altPaths = routingTemplate.calcPaths(queryGraph, algorithmFactory, algoOpts);

        boolean tmpEnableInstructions = hints.getBool(Parameters.Routing.INSTRUCTIONS, encodingManager.isEnableInstructions());
        boolean tmpCalcPoints = hints.getBool(Parameters.Routing.CALC_POINTS, routingConfig.isCalcPoints());
        double wayPointMaxDistance = hints.getDouble(Parameters.Routing.WAY_POINT_MAX_DISTANCE, 1d);

        DouglasPeucker peucker = new DouglasPeucker().setMaxDistance(wayPointMaxDistance);
        PathMerger pathMerger = new PathMerger(queryGraph.getBaseGraph(), weighting).
                setCalcPoints(tmpCalcPoints).
                setDouglasPeucker(peucker).
                setEnableInstructions(tmpEnableInstructions).
                setPathDetailsBuilders(pathBuilderFactory, request.getPathDetails()).
                setSimplifyResponse(routingConfig.isSimplifyResponse() && wayPointMaxDistance > 0);

        if (request.hasFavoredHeading(0))
            pathMerger.setFavoredHeading(request.getFavoredHeading(0));

        routingTemplate.finish(pathMerger, trMap.getWithFallBack(request.getLocale()));
        return altPaths;
    }

    public RoutingAlgorithmFactory getAlgorithmFactory(HintsMap map) {
        boolean disableCH = map.getBool(Parameters.CH.DISABLE, false);
        boolean disableLM = map.getBool(Parameters.Landmark.DISABLE, false);
        if (disableCH && !chDisablingAllowed) {
            throw new IllegalArgumentException("Disabling CH is not allowed on the server side");
        }
        if (disableLM && !lmDisablingAllowed) {
            throw new IllegalArgumentException("Disabling LM is not allowed on the server side");
        }

        // for now do not allow mixing CH&LM #1082,#1889
        if (chEnabled && !disableCH) {
            CHProfile chProfile = selectProfile(map);
            return new CHRoutingAlgorithmFactory(ghStorage.getCHGraph(chProfile));
        } else if (lmEnabled && !disableLM) {
            // todonow: create routing algo factory without the need of lmPrepHandler, just as for CH above -> then remove lmPrepHandler dependency
            // see #1900
            return lmPreparationHandler.getAlgorithmFactory(map);
        } else {
            return new RoutingAlgorithmFactorySimple();
        }
    }

    private CHProfile selectProfile(HintsMap map) {
        try {
            return CHProfileSelector.select(chProfiles, map);
        } catch (CHProfileSelectionException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    // todonow: make private
    public FlagEncoder getDefaultVehicle() {
        if (encodingManager == null)
            throw new IllegalStateException("No encoding manager specified or loaded");

        return encodingManager.fetchEdgeEncoders().get(0);
    }

    private GraphEdgeIdFinder.BlockArea createBlockArea(List<GHPoint> points, HintsMap hints, EdgeFilter edgeFilter) {
        String blockAreaStr = hints.get(Parameters.Routing.BLOCK_AREA, "");
        GraphEdgeIdFinder.BlockArea blockArea = new GraphEdgeIdFinder(ghStorage, locationIndex).
                parseBlockArea(blockAreaStr, edgeFilter, hints.getDouble(Parameters.Routing.BLOCK_AREA + ".edge_id_max_area", 1000 * 1000));
        for (GHPoint p : points) {
            if (blockArea.contains(p))
                throw new IllegalArgumentException("Request with " + Parameters.Routing.BLOCK_AREA + " contained query point " + p + ". This is not allowed.");
        }
        return blockArea;
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
        if (routingConfig.getNonChMaxWaypointDistance() == Integer.MAX_VALUE) {
            return;
        }
        GHPoint lastPoint = points.get(0);
        GHPoint point;
        double dist;
        DistanceCalc calc = DIST_3D;
        for (int i = 1; i < points.size(); i++) {
            point = points.get(i);
            dist = calc.calcDist(lastPoint.getLat(), lastPoint.getLon(), point.getLat(), point.getLon());
            if (dist > routingConfig.getNonChMaxWaypointDistance()) {
                Map<String, Object> detailMap = new HashMap<>(2);
                detailMap.put("from", i - 1);
                detailMap.put("to", i);
                throw new PointDistanceExceededException("Point " + i + " is too far from Point " + (i - 1) + ": " + point, detailMap);
            }
            lastPoint = point;
        }
    }
}
