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
package com.graphhopper.routing.template;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.PathWrapper;
import com.graphhopper.routing.*;
import com.graphhopper.routing.util.*;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Parameters.Routing;
import com.graphhopper.util.PathMerger;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.Translation;
import com.graphhopper.util.exceptions.PointNotFoundException;
import com.graphhopper.util.shapes.GHPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of calculating a route with multiple via points.
 *
 * @author Peter Karich
 */
public class ViaRoutingTemplate extends AbstractRoutingTemplate implements RoutingTemplate {
    protected final GHRequest ghRequest;
    protected final GHResponse ghResponse;
    protected final PathWrapper altResponse = new PathWrapper();
    private final LocationIndex locationIndex;
    protected final EncodingManager encodingManager;
    // result from route
    protected List<Path> pathList;

    public ViaRoutingTemplate(GHRequest ghRequest, GHResponse ghRsp, LocationIndex locationIndex, EncodingManager encodingManager) {
        this.locationIndex = locationIndex;
        this.ghRequest = ghRequest;
        this.ghResponse = ghRsp;
        this.encodingManager = encodingManager;
    }

    @Override
    public List<QueryResult> lookup(List<GHPoint> points, FlagEncoder encoder) {
        if (points.size() < 2)
            throw new IllegalArgumentException("At least 2 points have to be specified, but was:" + points.size());

        EdgeFilter edgeFilter = DefaultEdgeFilter.allEdges(encoder);
        queryResults = new ArrayList<>(points.size());
        for (int placeIndex = 0; placeIndex < points.size(); placeIndex++) {
            GHPoint point = points.get(placeIndex);
            QueryResult qr = null;
            if (ghRequest.hasPointHints())
                qr = locationIndex.findClosest(point.lat, point.lon, new NameSimilarityEdgeFilter(edgeFilter, ghRequest.getPointHints().get(placeIndex)));
            if (qr == null || !qr.isValid())
                qr = locationIndex.findClosest(point.lat, point.lon, edgeFilter);
            if (!qr.isValid())
                ghResponse.addError(new PointNotFoundException("Cannot find point " + placeIndex + ": " + point, placeIndex));

            queryResults.add(qr);
        }

        return queryResults;
    }

    @Override
    public List<Path> calcPaths(QueryGraph queryGraph, RoutingAlgorithmFactory algoFactory, AlgorithmOptions algoOpts) {
        long visitedNodesSum = 0L;
        boolean viaTurnPenalty = ghRequest.getHints().getBool(Routing.PASS_THROUGH, false);
        int pointCounts = ghRequest.getPoints().size();
        pathList = new ArrayList<>(pointCounts - 1);
        QueryResult fromQResult = queryResults.get(0);
        StopWatch sw;
        for (int placeIndex = 1; placeIndex < pointCounts; placeIndex++) {
            if (placeIndex == 1) {
                // enforce start direction
                queryGraph.enforceHeading(fromQResult.getClosestNode(), ghRequest.getFavoredHeading(0), false);
            } else if (viaTurnPenalty) {
                // enforce straight start after via stop
                Path prevRoute = pathList.get(placeIndex - 2);
                if (prevRoute.getEdgeCount() > 0) {
                    EdgeIteratorState incomingVirtualEdge = prevRoute.getFinalEdge();
                    queryGraph.unfavorVirtualEdgePair(fromQResult.getClosestNode(), incomingVirtualEdge.getEdge());
                }
            }

            QueryResult toQResult = queryResults.get(placeIndex);

            // enforce end direction
            queryGraph.enforceHeading(toQResult.getClosestNode(), ghRequest.getFavoredHeading(placeIndex), true);

            sw = new StopWatch().start();
            RoutingAlgorithm algo = algoFactory.createAlgo(queryGraph, algoOpts);
            String debug = ", algoInit:" + sw.stop().getSeconds() + "s";

            sw = new StopWatch().start();

            // calculate paths
            List<Path> tmpPathList = algo.calcPaths(fromQResult.getClosestNode(), toQResult.getClosestNode());
            debug += ", " + algo.getName() + "-routing:" + sw.stop().getSeconds() + "s";
            if (tmpPathList.isEmpty())
                throw new IllegalStateException("At least one path has to be returned for " + fromQResult + " -> " + toQResult);

            int idx = 0;
            for (Path path : tmpPathList) {
                if (path.getTime() < 0)
                    throw new RuntimeException("Time was negative " + path.getTime() + " for index " + idx + ". Please report as bug and include:" + ghRequest);

                pathList.add(path);
                debug += ", " + path.getDebugInfo();
                idx++;
            }

            altResponse.addDebugInfo(debug);

            // reset all direction enforcements in queryGraph to avoid influencing next path
            queryGraph.clearUnfavoredStatus();

            if (algo.getVisitedNodes() >= algoOpts.getMaxVisitedNodes())
                throw new IllegalArgumentException("No path found due to maximum nodes exceeded " + algoOpts.getMaxVisitedNodes());

            visitedNodesSum += algo.getVisitedNodes();
            altResponse.addDebugInfo("visited nodes sum: " + visitedNodesSum);
            fromQResult = toQResult;
        }

        ghResponse.getHints().put("visited_nodes.sum", visitedNodesSum);
        ghResponse.getHints().put("visited_nodes.average", (float) visitedNodesSum / (pointCounts - 1));

        return pathList;
    }

    @Override
    public boolean isReady(PathMerger pathMerger, Translation tr) {
        if (ghRequest.getPoints().size() - 1 != pathList.size())
            throw new RuntimeException("There should be exactly one more points than paths. points:" + ghRequest.getPoints().size() + ", paths:" + pathList.size());

        altResponse.setWaypoints(getWaypoints());
        ghResponse.add(altResponse);
        pathMerger.doWork(altResponse, pathList, encodingManager, tr);
        return true;
    }

    @Override
    public int getMaxRetries() {
        return 1;
    }
}
