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
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.tour.MultiPointTour;
import com.graphhopper.routing.util.tour.TourStrategy;
import com.graphhopper.routing.weighting.AvoidEdgesWeighting;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.Parameters.Algorithms;
import com.graphhopper.util.Parameters.Algorithms.RoundTrip;
import com.graphhopper.util.PathMerger;
import com.graphhopper.util.Translation;
import com.graphhopper.util.exceptions.PointNotFoundException;
import com.graphhopper.util.shapes.GHPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Implementation of calculating a route with one or more round trip (route with identical start and
 * end).
 *
 * @author Peter Karich
 */
public class RoundTripRoutingTemplate extends AbstractRoutingTemplate implements RoutingTemplate {
    private final int maxRetries;
    private final GHRequest ghRequest;
    private final GHResponse ghResponse;
    private final LocationIndex locationIndex;
    private final EncodingManager encodingManager;
    private PathWrapper altResponse;
    // result from route
    private List<Path> pathList;

    public RoundTripRoutingTemplate(GHRequest request, GHResponse ghRsp, LocationIndex locationIndex, EncodingManager encodingManager, int maxRetries) {
        this.ghRequest = request;
        this.ghResponse = ghRsp;
        this.locationIndex = locationIndex;
        this.encodingManager = encodingManager;
        this.maxRetries = maxRetries;
    }

    @Override
    public List<QueryResult> lookup(List<GHPoint> points, FlagEncoder encoder) {
        if (points.size() != 1 || ghRequest.getPoints().size() != 1)
            throw new IllegalArgumentException("For round trip calculation exactly one point is required");
        final double distanceInMeter = ghRequest.getHints().getDouble(RoundTrip.DISTANCE, 10000);
        final long seed = ghRequest.getHints().getLong(RoundTrip.SEED, 0L);
        double initialHeading = ghRequest.getFavoredHeading(0);
        final int roundTripPointCount = Math.min(20, ghRequest.getHints().getInt(RoundTrip.POINTS, 2 + (int) (distanceInMeter / 50000)));
        final GHPoint start = points.get(0);

        TourStrategy strategy = new MultiPointTour(new Random(seed), distanceInMeter, roundTripPointCount, initialHeading);
        queryResults = new ArrayList<>(2 + strategy.getNumberOfGeneratedPoints());
        EdgeFilter edgeFilter = DefaultEdgeFilter.allEdges(encoder);
        QueryResult startQR = locationIndex.findClosest(start.lat, start.lon, edgeFilter);
        if (!startQR.isValid())
            throw new PointNotFoundException("Cannot find point 0: " + start, 0);

        queryResults.add(startQR);

        GHPoint last = start;
        for (int i = 0; i < strategy.getNumberOfGeneratedPoints(); i++) {
            double heading = strategy.getHeadingForIteration(i);
            QueryResult result = generateValidPoint(last, strategy.getDistanceForIteration(i), heading, edgeFilter);
            if (result == null) {
                ghResponse.addError(new IllegalStateException("Could not find a valid point after " + maxRetries + " tries, for the point:" + last));
                return Collections.emptyList();
            }
            last = result.getSnappedPoint();
            queryResults.add(result);
        }

        queryResults.add(startQR);
        return queryResults;
    }

    void setQueryResults(List<QueryResult> queryResults) {
        this.queryResults = queryResults;
    }

    @Override
    public List<Path> calcPaths(QueryGraph queryGraph, RoutingAlgorithmFactory algoFactory, AlgorithmOptions algoOpts) {
        pathList = new ArrayList<>(queryResults.size() - 1);

        AvoidEdgesWeighting avoidPathWeighting = new AvoidEdgesWeighting(algoOpts.getWeighting());
        avoidPathWeighting.setEdgePenaltyFactor(5);
        algoOpts = AlgorithmOptions.start(algoOpts).
                algorithm(Parameters.Algorithms.ASTAR_BI).
                weighting(avoidPathWeighting).build();
        algoOpts.getHints().put(Algorithms.AStarBi.EPSILON, 2);

        long visitedNodesSum = 0L;
        QueryResult start = queryResults.get(0);
        for (int qrIndex = 1; qrIndex < queryResults.size(); qrIndex++) {
            RoutingAlgorithm algo = algoFactory.createAlgo(queryGraph, algoOpts);
            // instead getClosestNode (which might be a virtual one and introducing unnecessary tails of the route)
            // use next tower node -> getBaseNode or getAdjNode
            // Later: remove potential route tail
            QueryResult startQR = queryResults.get(qrIndex - 1);
            int startNode = (startQR == start) ? startQR.getClosestNode() : startQR.getClosestEdge().getBaseNode();
            QueryResult endQR = queryResults.get(qrIndex);
            int endNode = (endQR == start) ? endQR.getClosestNode() : endQR.getClosestEdge().getBaseNode();

            Path path = algo.calcPath(startNode, endNode);
            visitedNodesSum += algo.getVisitedNodes();

            pathList.add(path);

            // it is important to avoid previously visited nodes for future paths
            avoidPathWeighting.addEdges(path.calcEdges());
        }

        ghResponse.getHints().put("visited_nodes.sum", visitedNodesSum);
        ghResponse.getHints().put("visited_nodes.average", (float) visitedNodesSum / (queryResults.size() - 1));

        return pathList;
    }

    public void setPaths(List<Path> pathList) {
        this.pathList = pathList;
    }

    @Override
    public boolean isReady(PathMerger pathMerger, Translation tr) {
        altResponse = new PathWrapper();
        altResponse.setWaypoints(getWaypoints());
        ghResponse.add(altResponse);
        pathMerger.doWork(altResponse, pathList, encodingManager, tr);
        // with potentially retrying, including generating new route points, for now disabled
        return true;
    }

    private QueryResult generateValidPoint(GHPoint from, double distanceInMeters, double heading,
                                           EdgeFilter edgeFilter) {
        int tryCount = 0;
        while (true) {
            GHPoint generatedPoint = Helper.DIST_EARTH.projectCoordinate(from.getLat(), from.getLon(), distanceInMeters, heading);
            QueryResult qr = locationIndex.findClosest(generatedPoint.getLat(), generatedPoint.getLon(), edgeFilter);
            if (qr.isValid())
                return qr;

            tryCount++;
            distanceInMeters *= 0.95;

            if (tryCount >= maxRetries)
                return null;
        }
    }

    @Override
    public int getMaxRetries() {
        // with potentially retrying, including generating new route points, for now disabled
        return 1;
    }
}
