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

import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.NameSimilarityEdgeFilter;
import com.graphhopper.routing.util.SnapPreventionEdgeFilter;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.GHPoint;

import java.util.ArrayList;
import java.util.List;

import static com.graphhopper.util.EdgeIterator.ANY_EDGE;
import static com.graphhopper.util.EdgeIterator.NO_EDGE;
import static com.graphhopper.util.Parameters.Curbsides.CURBSIDE_ANY;
import static com.graphhopper.util.Parameters.Routing.CURBSIDE;

/**
 * The methods here can be used to calculate routes with or without via points and implement possible restrictions
 * like snap preventions, headings and curbsides.
 *
 * @author Peter Karich
 * @author easbar
 */
public class ViaRouting {

    /**
     * @throws MultiplePointsNotFoundException in case one or more points could not be resolved
     */
    public static List<QueryResult> lookup(EncodedValueLookup lookup, List<GHPoint> points, Weighting weighting, LocationIndex locationIndex, List<String> snapPreventions, List<String> pointHints) {
        if (points.size() < 2)
            throw new IllegalArgumentException("At least 2 points have to be specified, but was:" + points.size());

        final EnumEncodedValue<RoadClass> roadClassEnc = lookup.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        final EnumEncodedValue<RoadEnvironment> roadEnvEnc = lookup.getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment.class);
        EdgeFilter edgeFilter = createEdgeFilter(weighting);
        EdgeFilter strictEdgeFilter = snapPreventions.isEmpty()
                ? edgeFilter
                : new SnapPreventionEdgeFilter(edgeFilter, roadClassEnc, roadEnvEnc, snapPreventions);
        List<QueryResult> queryResults = new ArrayList<>(points.size());
        IntArrayList pointsNotFound = new IntArrayList();
        for (int placeIndex = 0; placeIndex < points.size(); placeIndex++) {
            GHPoint point = points.get(placeIndex);
            QueryResult qr = null;
            if (!pointHints.isEmpty())
                qr = locationIndex.findClosest(point.lat, point.lon, new NameSimilarityEdgeFilter(strictEdgeFilter,
                        pointHints.get(placeIndex), point, 100));
            else if (!snapPreventions.isEmpty())
                qr = locationIndex.findClosest(point.lat, point.lon, strictEdgeFilter);
            if (qr == null || !qr.isValid())
                qr = locationIndex.findClosest(point.lat, point.lon, edgeFilter);
            if (!qr.isValid())
                pointsNotFound.add(placeIndex);

            queryResults.add(qr);
        }

        if (!pointsNotFound.isEmpty())
            throw new MultiplePointsNotFoundException(pointsNotFound);

        return queryResults;
    }

    static EdgeFilter createEdgeFilter(final Weighting weighting) {
        final BooleanEncodedValue accessEnc = weighting.getFlagEncoder().getAccessEnc();
        return edgeState -> edgeState.get(accessEnc) && !Double.isInfinite(weighting.calcEdgeWeight(edgeState, false))
                || edgeState.getReverse(accessEnc) && !Double.isInfinite(weighting.calcEdgeWeight(edgeState, true));
    }

    public static Result calcPaths(List<GHPoint> points, QueryGraph queryGraph, List<QueryResult> queryResults, BooleanEncodedValue accessEnc, PathCalculator pathCalculator, List<String> curbsides, boolean forceCurbsides, List<Double> headings, boolean passThrough) {
        if (!curbsides.isEmpty() && curbsides.size() != points.size())
            throw new IllegalArgumentException("If you pass " + CURBSIDE + ", you need to pass exactly one curbside for every point, empty curbsides will be ignored");
        if (!curbsides.isEmpty() && !headings.isEmpty())
            throw new IllegalArgumentException("You cannot use curbsides and headings or pass_through at the same time");

        final int legs = queryResults.size() - 1;
        Result result = new Result(legs);
        for (int leg = 0; leg < legs; ++leg) {
            QueryResult fromQResult = queryResults.get(leg);
            QueryResult toQResult = queryResults.get(leg + 1);

            // enforce headings
            // at via-nodes and the target node the heading parameter is interpreted as the direction we want
            // to enforce for arriving (not starting) at this node. the starting direction is not enforced at
            // all for these points (unless using pass through). see this forum discussion:
            // https://discuss.graphhopper.com/t/meaning-of-heading-parameter-for-via-routing/5643/6
            double fromHeading = (leg == 0 && !headings.isEmpty()) ? headings.get(0) : Double.NaN;
            double toHeading = (queryResults.size() == headings.size() && !Double.isNaN(headings.get(leg + 1))) ? headings.get(leg + 1) : Double.NaN;

            // enforce pass-through
            int incomingEdge = NO_EDGE;
            if (leg != 0) {
                // enforce straight start after via stop
                Path prevRoute = result.paths.get(leg - 1);
                if (prevRoute.getEdgeCount() > 0)
                    incomingEdge = prevRoute.getFinalEdge().getEdge();
            }

            // enforce curbsides
            final String fromCurbside = curbsides.isEmpty() ? CURBSIDE_ANY : curbsides.get(leg);
            final String toCurbside = curbsides.isEmpty() ? CURBSIDE_ANY : curbsides.get(leg + 1);

            EdgeRestrictions edgeRestrictions = buildEdgeRestrictions(queryGraph, fromQResult, toQResult,
                    fromHeading, toHeading, incomingEdge, passThrough,
                    fromCurbside, toCurbside, accessEnc);

            edgeRestrictions.setSourceOutEdge(ignoreThrowOrAcceptImpossibleCurbsides(curbsides, edgeRestrictions.getSourceOutEdge(), leg, forceCurbsides));
            edgeRestrictions.setTargetInEdge(ignoreThrowOrAcceptImpossibleCurbsides(curbsides, edgeRestrictions.getTargetInEdge(), leg + 1, forceCurbsides));

            // calculate paths
            List<Path> paths = pathCalculator.calcPaths(fromQResult.getClosestNode(), toQResult.getClosestNode(), edgeRestrictions);
            result.debug += pathCalculator.getDebugString();

            // for alternative routing we get multiple paths and add all of them (which is ok, because we do not allow
            // via-points for alternatives at the moment). otherwise we would have to return a list<list<path>> and find
            // a good method to decide how to combine the different legs
            for (int i = 0; i < paths.size(); i++) {
                Path path = paths.get(i);
                if (path.getTime() < 0)
                    throw new RuntimeException("Time was negative " + path.getTime() + " for index " + i);

                result.paths.add(path);
                result.debug += ", " + path.getDebugInfo();
            }

            result.visitedNodes += pathCalculator.getVisitedNodes();
            result.debug += "visited nodes sum: " + result.visitedNodes;
        }

        return result;
    }

    public static class Result {
        public List<Path> paths;
        public long visitedNodes;
        public String debug = "";

        Result(int legs) {
            paths = new ArrayList<>(legs);
        }
    }

    /**
     * Determines restrictions for the start/target edges to account for the heading, pass_through and curbside parameters
     * for a single via-route leg.
     *
     * @param fromHeading  the heading at the start node of this leg, or NaN if no restriction should be applied
     * @param toHeading    the heading at the target node (the vehicle's heading when arriving at the target), or NaN if
     *                     no restriction should be applied
     * @param incomingEdge the last edge of the previous leg (or {@link EdgeIterator#NO_EDGE} if not available
     */
    private static EdgeRestrictions buildEdgeRestrictions(
            QueryGraph queryGraph, QueryResult fromQResult, QueryResult toQResult,
            double fromHeading, double toHeading, int incomingEdge, boolean passThrough,
            String fromCurbside, String toCurbside, BooleanEncodedValue accessEnc) {
        EdgeRestrictions edgeRestrictions = new EdgeRestrictions();

        // curbsides
        if (!fromCurbside.equals(CURBSIDE_ANY) || !toCurbside.equals(CURBSIDE_ANY)) {
            DirectionResolver directionResolver = new DirectionResolver(queryGraph, accessEnc);
            DirectionResolverResult fromDirection = directionResolver.resolveDirections(fromQResult.getClosestNode(), fromQResult.getQueryPoint());
            DirectionResolverResult toDirection = directionResolver.resolveDirections(toQResult.getClosestNode(), toQResult.getQueryPoint());
            int sourceOutEdge = DirectionResolverResult.getOutEdge(fromDirection, fromCurbside);
            int targetInEdge = DirectionResolverResult.getInEdge(toDirection, toCurbside);
            if (fromQResult.getClosestNode() == toQResult.getClosestNode()) {
                // special case where we go from one point back to itself. for example going from a point A
                // with curbside right to the same point with curbside right is interpreted as 'being there
                // already' -> empty path. Similarly if the curbside for the start/target is not even specified
                // there is no need to drive a loop. However, going from point A/right to point A/left (or the
                // other way around) means we need to drive some kind of loop to get back to the same location
                // (arriving on the other side of the road).
                if (Helper.isEmpty(fromCurbside) || Helper.isEmpty(toCurbside) ||
                        fromCurbside.equals(CURBSIDE_ANY) || toCurbside.equals(CURBSIDE_ANY) ||
                        fromCurbside.equals(toCurbside)) {
                    // we just disable start/target edge constraints to get an empty path
                    sourceOutEdge = ANY_EDGE;
                    targetInEdge = ANY_EDGE;
                }
            }
            edgeRestrictions.setSourceOutEdge(sourceOutEdge);
            edgeRestrictions.setTargetInEdge(targetInEdge);
        }

        // heading
        if (!Double.isNaN(fromHeading) || !Double.isNaN(toHeading)) {
            // todo: for heading/pass_through with edge-based routing (especially CH) we have to find the edge closest
            // to the heading and use it as sourceOutEdge/targetInEdge here. the heading penalty will not be applied
            // this way (unless we implement this), but this is more or less ok as we can use finite u-turn costs
            // instead. maybe the hardest part is dealing with headings that cannot be fulfilled, like in one-way
            // streets. see also #1765
            HeadingResolver headingResolver = new HeadingResolver(queryGraph);
            if (!Double.isNaN(fromHeading))
                edgeRestrictions.getUnfavoredEdges().addAll(headingResolver.getEdgesWithDifferentHeading(fromQResult.getClosestNode(), fromHeading));

            if (!Double.isNaN(toHeading)) {
                toHeading += 180;
                if (toHeading > 360)
                    toHeading -= 360;
                edgeRestrictions.getUnfavoredEdges().addAll(headingResolver.getEdgesWithDifferentHeading(toQResult.getClosestNode(), toHeading));
            }
        }

        // pass through
        if (incomingEdge != NO_EDGE && passThrough)
            edgeRestrictions.getUnfavoredEdges().add(incomingEdge);
        return edgeRestrictions;
    }

    private static int ignoreThrowOrAcceptImpossibleCurbsides(List<String> curbsides, int edge, int placeIndex, boolean forceCurbsides) {
        if (edge != NO_EDGE) {
            return edge;
        }
        if (forceCurbsides) {
            return throwImpossibleCurbsideConstraint(curbsides, placeIndex);
        } else {
            return ANY_EDGE;
        }
    }

    private static int throwImpossibleCurbsideConstraint(List<String> curbsides, int placeIndex) {
        throw new IllegalArgumentException("Impossible curbside constraint: 'curbside=" + curbsides.get(placeIndex) + "' at point " + placeIndex);
    }

}
