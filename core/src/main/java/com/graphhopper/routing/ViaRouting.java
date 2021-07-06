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
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.GHPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntToDoubleFunction;

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
    public static List<Snap> lookup(EncodedValueLookup lookup, List<GHPoint> points, EdgeFilter edgeFilter, LocationIndex locationIndex, List<String> snapPreventions, List<String> pointHints) {
        if (points.size() < 2)
            throw new IllegalArgumentException("At least 2 points have to be specified, but was:" + points.size());

        final EnumEncodedValue<RoadClass> roadClassEnc = lookup.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        final EnumEncodedValue<RoadEnvironment> roadEnvEnc = lookup.getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment.class);
        EdgeFilter strictEdgeFilter = snapPreventions.isEmpty()
                ? edgeFilter
                : new SnapPreventionEdgeFilter(edgeFilter, roadClassEnc, roadEnvEnc, snapPreventions);
        List<Snap> snaps = new ArrayList<>(points.size());
        IntArrayList pointsNotFound = new IntArrayList();
        for (int placeIndex = 0; placeIndex < points.size(); placeIndex++) {
            GHPoint point = points.get(placeIndex);
            Snap snap = null;
            if (!pointHints.isEmpty())
                snap = locationIndex.findClosest(point.lat, point.lon, new NameSimilarityEdgeFilter(strictEdgeFilter,
                        pointHints.get(placeIndex), point, 100));
            else if (!snapPreventions.isEmpty())
                snap = locationIndex.findClosest(point.lat, point.lon, strictEdgeFilter);
            if (snap == null || !snap.isValid())
                snap = locationIndex.findClosest(point.lat, point.lon, edgeFilter);
            if (!snap.isValid())
                pointsNotFound.add(placeIndex);

            snaps.add(snap);
        }

        if (!pointsNotFound.isEmpty())
            throw new MultiplePointsNotFoundException(pointsNotFound);

        return snaps;
    }

    public static Result calcPaths(List<GHPoint> points, QueryGraph queryGraph, List<Snap> snaps, Weighting weighting, PathCalculator pathCalculator, List<String> curbsides, boolean forceCurbsides, List<Double> headings, boolean passThrough) {
        if (!curbsides.isEmpty() && curbsides.size() != points.size())
            throw new IllegalArgumentException("If you pass " + CURBSIDE + ", you need to pass exactly one curbside for every point, empty curbsides will be ignored");
        if (!curbsides.isEmpty() && !headings.isEmpty())
            throw new IllegalArgumentException("You cannot use curbsides and headings or pass_through at the same time");

        DirectionResolver directionResolver = new DirectionResolver(queryGraph,
                (edge, reverse) -> Double.isFinite(weighting.calcEdgeWeightWithAccess(edge, reverse)));
        HeadingResolver headingResolver = new HeadingResolver(queryGraph);
        final int legs = snaps.size() - 1;
        Result result = new Result(legs);
        for (int leg = 0; leg < legs; ++leg) {
            // depending on the heading, pass_through and curbside parameters we might have to penalize some of the
            // possible start/target edges
            EdgeRestrictions edgeRestrictions;
            if (!curbsides.isEmpty())
                edgeRestrictions = buildEdgeRestrictionsForCurbsides(directionResolver, snaps, curbsides, leg, forceCurbsides);
            else if (!headings.isEmpty() || passThrough) {
                int incomingEdge = NO_EDGE;
                if (leg != 0) {
                    Path prevRoute = result.paths.get(leg - 1);
                    if (prevRoute.getEdgeCount() > 0)
                        incomingEdge = prevRoute.getFinalEdge().getEdge();
                }
                edgeRestrictions = buildEdgeRestrictionsForHeading(headingResolver, snaps, headings, leg, passThrough, incomingEdge);
            } else
                edgeRestrictions = EdgeRestrictions.none();

            // calculate paths
            List<Path> paths = pathCalculator.calcPaths(snaps.get(leg).getClosestNode(), snaps.get(leg + 1).getClosestNode(), edgeRestrictions);
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

    private static EdgeRestrictions buildEdgeRestrictionsForCurbsides(DirectionResolver directionResolver, List<Snap> snaps, List<String> curbsides, int leg, boolean forceCurbsides) {
        final Snap fromSnap = snaps.get(leg);
        final Snap toSnap = snaps.get(leg + 1);
        final String fromCurbside = curbsides.isEmpty() ? CURBSIDE_ANY : curbsides.get(leg);
        final String toCurbside = curbsides.isEmpty() ? CURBSIDE_ANY : curbsides.get(leg + 1);
        IntToDoubleFunction getEdgePenaltyFrom = null;
        IntToDoubleFunction getEdgePenaltyTo = null;
        if (!fromCurbside.equals(CURBSIDE_ANY) || !toCurbside.equals(CURBSIDE_ANY)) {
            DirectionResolverResult fromDirection = directionResolver.resolveDirections(fromSnap.getClosestNode(), fromSnap.getQueryPoint());
            DirectionResolverResult toDirection = directionResolver.resolveDirections(toSnap.getClosestNode(), toSnap.getQueryPoint());
            int sourceOutEdge = DirectionResolverResult.getOutEdge(fromDirection, fromCurbside);
            int targetInEdge = DirectionResolverResult.getInEdge(toDirection, toCurbside);
            if (fromSnap.getClosestNode() == toSnap.getClosestNode()) {
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
            final int tmpSourceOutEdge = ignoreThrowOrAcceptImpossibleCurbsides(curbsides, sourceOutEdge, leg, forceCurbsides);
            final int tmpTargetInEdge = ignoreThrowOrAcceptImpossibleCurbsides(curbsides, targetInEdge, leg + 1, forceCurbsides);
            getEdgePenaltyFrom = edge -> (tmpSourceOutEdge == ANY_EDGE || edge == tmpSourceOutEdge) ? 0 : Double.POSITIVE_INFINITY;
            getEdgePenaltyTo = edge -> (tmpTargetInEdge == ANY_EDGE || edge == tmpTargetInEdge) ? 0 : Double.POSITIVE_INFINITY;
        }
        return new EdgeRestrictions(getEdgePenaltyFrom, getEdgePenaltyTo);
    }

    private static EdgeRestrictions buildEdgeRestrictionsForHeading(HeadingResolver headingResolver, List<Snap> snaps, List<Double> headings, int leg, boolean passThrough, int incomingEdge) {
        IntArrayList unfavoredOutEdges = new IntArrayList();
        IntArrayList unfavoredInEdges = new IntArrayList();
        // heading
        // The heading at the start node is the direction we want to enforce at the beginning of the route.
        // At via-nodes and the target node the heading parameter is interpreted as the direction we want
        // to enforce for arriving (not starting) at this node. It's the vehicle's heading when arriving at the node.
        // The starting direction is not enforced at all for these points (unless using pass through).
        // See this forum discussion:
        // https://discuss.graphhopper.com/t/meaning-of-heading-parameter-for-via-routing/5643/6
        double fromHeading = (leg == 0 && !headings.isEmpty()) ? headings.get(0) : Double.NaN;
        double toHeading = (snaps.size() == headings.size() && !Double.isNaN(headings.get(leg + 1))) ? headings.get(leg + 1) : Double.NaN;
        if (!Double.isNaN(fromHeading))
            unfavoredOutEdges.addAll(headingResolver.getEdgesWithDifferentHeading(snaps.get(leg).getClosestNode(), fromHeading));

        if (!Double.isNaN(toHeading)) {
            toHeading += 180;
            if (toHeading > 360)
                toHeading -= 360;
            unfavoredInEdges.addAll(headingResolver.getEdgesWithDifferentHeading(snaps.get(leg + 1).getClosestNode(), toHeading));
        }

        // pass through
        if (passThrough && incomingEdge != NO_EDGE)
            unfavoredOutEdges.add(incomingEdge);

        // todonow: how should this be configurable? also why is heading penalty added to *time* in FastestWeighting?!
        //          maybe we could just set it to one here and then in the algorithms scale it with something like
        //          weighting.getHeadingPenalty()?
        final double headingPenalty = 300;
        IntToDoubleFunction getEdgePenaltyFrom = edge -> unfavoredOutEdges.contains(edge) ? headingPenalty : 0;
        IntToDoubleFunction getEdgePenaltyTo = edge -> unfavoredInEdges.contains(edge) ? headingPenalty : 0;
        return new EdgeRestrictions(getEdgePenaltyFrom, getEdgePenaltyTo);
    }

    public static class Result {
        public List<Path> paths;
        public long visitedNodes;
        public String debug = "";

        Result(int legs) {
            paths = new ArrayList<>(legs);
        }
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
