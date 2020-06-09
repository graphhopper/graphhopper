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
import com.graphhopper.ResponsePath;
import com.graphhopper.routing.*;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.ev.RoadEnvironment;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.NameSimilarityEdgeFilter;
import com.graphhopper.routing.util.SnapPreventionEdgeFilter;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Parameters.Routing;
import com.graphhopper.util.PathMerger;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.Translation;
import com.graphhopper.util.exceptions.PointNotFoundException;
import com.graphhopper.util.shapes.GHPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.graphhopper.util.EdgeIterator.ANY_EDGE;
import static com.graphhopper.util.EdgeIterator.NO_EDGE;
import static com.graphhopper.util.Parameters.Curbsides.CURBSIDE_ANY;
import static com.graphhopper.util.Parameters.Routing.CURBSIDE;

/**
 * Implementation of calculating a route with multiple via points.
 *
 * @author Peter Karich
 */
public class ViaRoutingTemplate extends AbstractRoutingTemplate implements RoutingTemplate {
    protected final GHRequest ghRequest;
    protected final GHResponse ghResponse;
    // result from route
    protected List<Path> pathList;
    protected final ResponsePath responsePath = new ResponsePath();
    private final EnumEncodedValue<RoadClass> roadClassEnc;
    private final EnumEncodedValue<RoadEnvironment> roadEnvEnc;

    public ViaRoutingTemplate(GHRequest ghRequest, GHResponse ghRsp, LocationIndex locationIndex,
                              EncodedValueLookup lookup, final Weighting weighting) {
        super(locationIndex, lookup, weighting);
        this.ghRequest = ghRequest;
        this.ghResponse = ghRsp;
        this.roadClassEnc = lookup.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        this.roadEnvEnc = lookup.getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment.class);
    }

    @Override
    public List<QueryResult> lookup(List<GHPoint> points) {
        if (points.size() < 2)
            throw new IllegalArgumentException("At least 2 points have to be specified, but was:" + points.size());

        EdgeFilter strictEdgeFilter = ghRequest.getSnapPreventions().isEmpty()
                ? edgeFilter
                : new SnapPreventionEdgeFilter(edgeFilter, roadClassEnc, roadEnvEnc, ghRequest.getSnapPreventions());
        queryResults = new ArrayList<>(points.size());
        for (int placeIndex = 0; placeIndex < points.size(); placeIndex++) {
            GHPoint point = points.get(placeIndex);
            QueryResult qr = null;
            if (!ghRequest.getPointHints().isEmpty())
                qr = locationIndex.findClosest(point.lat, point.lon, new NameSimilarityEdgeFilter(strictEdgeFilter,
                        ghRequest.getPointHints().get(placeIndex), point, 100));
            else if (!ghRequest.getSnapPreventions().isEmpty())
                qr = locationIndex.findClosest(point.lat, point.lon, strictEdgeFilter);
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
        final boolean passThrough = ghRequest.getHints().getBool(Routing.PASS_THROUGH, false);
        final int pointsCount = ghRequest.getPoints().size();
        pathList = new ArrayList<>(pointsCount - 1);

        List<DirectionResolverResult> directions = buildDirections(queryGraph);
        HeadingResolver headingResolver = new HeadingResolver(queryGraph);

        for (int p = 1; p < pointsCount; p++) {
            QueryResult fromQResult = queryResults.get(p - 1);
            QueryResult toQResult = queryResults.get(p);

            if (p == 1) {
                // enforce start direction
                if (!ghRequest.getHeadings().isEmpty()) {
                    double heading = ghRequest.getHeadings().get(0);
                    if (!Double.isNaN(heading))
                        queryGraph.unfavorVirtualEdges(headingResolver.getEdgesWithDifferentHeading(fromQResult.getClosestNode(), heading));
                }
            } else if (passThrough) {
                // enforce straight start after via stop
                Path prevRoute = pathList.get(p - 2);
                if (prevRoute.getEdgeCount() > 0) {
                    queryGraph.unfavorVirtualEdge(prevRoute.getFinalEdge().getEdge());
                }
            }

            // enforce end direction
            if (ghRequest.getPoints().size() == ghRequest.getHeadings().size()) {
                double heading = ghRequest.getHeadings().get(p);
                if (!Double.isNaN(heading)) {
                    // at via-nodes and the target node the heading parameter is interpreted as the direction we want
                    // to enforce for arriving (not starting) at this node. the starting direction is not enforced at
                    // all for these points (unless using pass through). see this forum discussion:
                    // https://discuss.graphhopper.com/t/meaning-of-heading-parameter-for-via-routing/5643/6
                    heading += 180;
                    if (heading > 360)
                        heading -= 360;
                    queryGraph.unfavorVirtualEdges(headingResolver.getEdgesWithDifferentHeading(toQResult.getClosestNode(), heading));
                }
            }

            StopWatch sw = new StopWatch().start();
            // todo: so far 'heading' is implemented like this: we mark the unfavored edges on the query graph and then
            // our weighting applies a penalty to these edges. however, this only works for virtual edges and to make
            // this compatible with edge-based routing we would have to use edge keys instead of edge ids. either way a
            // better approach seems to be making the weighting (or the algorithm for that matter) aware of the unfavored
            // edges directly without changing the graph
            RoutingAlgorithm algo = algoFactory.createAlgo(queryGraph, algoOpts);
            String debug = ", algoInit:" + sw.stop().getSeconds() + "s";

            sw = new StopWatch().start();

            // calculate paths
            List<Path> tmpPathList;
            if (!directions.isEmpty()) {
                if (ghRequest.getCurbsides().size() != ghRequest.getPoints().size())
                    throw new IllegalArgumentException("If you pass " + CURBSIDE + ", you need to pass exactly one curbside for every point, empty curbsides will be ignored");

                if (!(algo instanceof BidirRoutingAlgorithm))
                    throw new IllegalArgumentException("To make use of the " + Routing.CURBSIDE + " parameter you need a bidirectional algorithm, got: " + algo.getName());

                final String fromCurbside = ghRequest.getCurbsides().get(p - 1);
                final String toCurbside = ghRequest.getCurbsides().get(p);
                int sourceOutEdge = DirectionResolverResult.getOutEdge(directions.get(p - 1), fromCurbside);
                int targetInEdge = DirectionResolverResult.getInEdge(directions.get(p), toCurbside);
                final boolean forceCurbsides = ghRequest.getHints().getBool(Routing.FORCE_CURBSIDE, true);
                sourceOutEdge = ignoreThrowOrAcceptImpossibleCurbsides(sourceOutEdge, p - 1, forceCurbsides);
                targetInEdge = ignoreThrowOrAcceptImpossibleCurbsides(targetInEdge, p, forceCurbsides);

                if (fromQResult.getClosestNode() == toQResult.getClosestNode()) {
                    // special case where we go from one point back to itself. for example going from a point A
                    // with curbside right to the same point with curbside right is interpreted as 'being there
                    // already' -> empty path. Similarly if the curbside for the start/target is not even specified
                    // there is no need to drive a loop. However, going from point A/right to point A/left (or the
                    // other way around) means we need to drive some kind of loop to get back to the same location
                    // (arriving on the other side of the road).
                    if (Helper.isEmpty(fromCurbside) || Helper.isEmpty(toCurbside) || fromCurbside.equals(CURBSIDE_ANY) ||
                            toCurbside.equals(CURBSIDE_ANY) || fromCurbside.equals(toCurbside)) {
                        // we just disable start/target edge constraints to get an empty path
                        sourceOutEdge = ANY_EDGE;
                        targetInEdge = ANY_EDGE;
                    }
                }
                // todo: enable curbside feature for alternative routes as well ?
                tmpPathList = Collections.singletonList(((BidirRoutingAlgorithm) algo)
                        .calcPath(fromQResult.getClosestNode(), toQResult.getClosestNode(), sourceOutEdge, targetInEdge));

            } else {
                tmpPathList = algo.calcPaths(fromQResult.getClosestNode(), toQResult.getClosestNode());
            }
            debug += ", " + algo.getName() + "-routing:" + sw.stop().getSeconds() + "s";
            if (tmpPathList.isEmpty())
                throw new IllegalStateException("At least one path has to be returned for " + fromQResult + " -> " + toQResult);

            // todo: can tmpPathList ever have more than one path here? and would it even be correct to add them all
            // to pathList then?
            for (int i = 0; i < tmpPathList.size(); i++) {
                Path path = tmpPathList.get(i);
                if (path.getTime() < 0)
                    throw new RuntimeException("Time was negative " + path.getTime() + " for index " + i + ". Please report as bug and include:" + ghRequest);

                pathList.add(path);
                debug += ", " + path.getDebugInfo();
            }

            responsePath.addDebugInfo(debug);

            // reset all direction enforcements in queryGraph to avoid influencing next path
            queryGraph.clearUnfavoredStatus();

            if (algo.getVisitedNodes() >= algoOpts.getMaxVisitedNodes())
                throw new IllegalArgumentException("No path found due to maximum nodes exceeded " + algoOpts.getMaxVisitedNodes());

            visitedNodesSum += algo.getVisitedNodes();
            responsePath.addDebugInfo("visited nodes sum: " + visitedNodesSum);
        }

        ghResponse.getHints().putObject("visited_nodes.sum", visitedNodesSum);
        ghResponse.getHints().putObject("visited_nodes.average", (float) visitedNodesSum / (pointsCount - 1));

        return pathList;
    }

    private List<DirectionResolverResult> buildDirections(QueryGraph queryGraph) {
        if (ghRequest.getCurbsides().isEmpty())
            return Collections.emptyList();
        DirectionResolver directionResolver = new DirectionResolver(queryGraph, accessEnc);
        List<DirectionResolverResult> directions = new ArrayList<>(queryResults.size());
        for (QueryResult qr : queryResults) {
            directions.add(directionResolver.resolveDirections(qr.getClosestNode(), qr.getQueryPoint()));
        }
        return directions;
    }

    private int ignoreThrowOrAcceptImpossibleCurbsides(int edge, int placeIndex, boolean forceCurbsides) {
        if (edge != NO_EDGE) {
            return edge;
        }
        if (forceCurbsides) {
            return throwImpossibleCurbsideConstraint(placeIndex);
        } else {
            return ANY_EDGE;
        }
    }

    private int throwImpossibleCurbsideConstraint(int placeIndex) {
        throw new IllegalArgumentException("Impossible curbside constraint: 'curbside=" + ghRequest.getCurbsides().get(placeIndex) + "' at point " + placeIndex);
    }

    @Override
    public void finish(PathMerger pathMerger, Translation tr) {
        if (ghRequest.getPoints().size() - 1 != pathList.size())
            throw new RuntimeException("There should be exactly one more points than paths. points:" + ghRequest.getPoints().size() + ", paths:" + pathList.size());

        responsePath.setWaypoints(getWaypoints());
        ghResponse.add(responsePath);
        pathMerger.doWork(responsePath, pathList, lookup, tr);
    }

}
