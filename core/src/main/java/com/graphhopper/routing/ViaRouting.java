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
import com.graphhopper.routing.querygraph.VirtualEdgeIteratorState;
import com.graphhopper.routing.util.*;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.GHPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static com.graphhopper.util.EdgeIterator.ANY_EDGE;
import static com.graphhopper.util.EdgeIterator.NO_EDGE;
import static com.graphhopper.util.Parameters.Curbsides.*;
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
    public static List<Snap> lookup(EncodedValueLookup lookup, List<GHPoint> points, EdgeFilter snapFilter,
                                    LocationIndex locationIndex, List<String> snapPreventions, List<String> pointHints,
                                    DirectedEdgeFilter directedSnapFilter, List<Double> headings) {
        if (points.size() < 2)
            throw new IllegalArgumentException("At least 2 points have to be specified, but was:" + points.size());

        final EnumEncodedValue<RoadClass> roadClassEnc = lookup.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        final EnumEncodedValue<RoadEnvironment> roadEnvEnc = lookup.getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment.class);
        EdgeFilter strictEdgeFilter = snapPreventions.isEmpty()
                ? snapFilter
                : new SnapPreventionEdgeFilter(snapFilter, roadClassEnc, roadEnvEnc, snapPreventions);
        List<Snap> snaps = new ArrayList<>(points.size());
        IntArrayList pointsNotFound = new IntArrayList();
        for (int placeIndex = 0; placeIndex < points.size(); placeIndex++) {
            GHPoint point = points.get(placeIndex);
            Snap snap = null;
            if (placeIndex < headings.size() && !Double.isNaN(headings.get(placeIndex))) {
                if (!pointHints.isEmpty() && !Helper.isEmpty(pointHints.get(placeIndex)))
                    throw new IllegalArgumentException("Cannot specify heading and point_hint at the same time. " +
                            "Make sure you specify either an empty point_hint (String) or a NaN heading (double) for point " + placeIndex);
                snap = locationIndex.findClosest(point.lat, point.lon, new HeadingEdgeFilter(directedSnapFilter, headings.get(placeIndex), point));
            } else if (!pointHints.isEmpty()) {
                snap = locationIndex.findClosest(point.lat, point.lon, new NameSimilarityEdgeFilter(strictEdgeFilter,
                        pointHints.get(placeIndex), point, 170));
            } else if (!snapPreventions.isEmpty()) {
                snap = locationIndex.findClosest(point.lat, point.lon, strictEdgeFilter);
            }

            if (snap == null || !snap.isValid())
                snap = locationIndex.findClosest(point.lat, point.lon, snapFilter);
            if (!snap.isValid())
                pointsNotFound.add(placeIndex);

            snaps.add(snap);
        }

        if (!pointsNotFound.isEmpty())
            throw new MultiplePointsNotFoundException(pointsNotFound);

        return snaps;
    }

    public static Result calcPaths(List<GHPoint> points, QueryGraph queryGraph, List<Snap> snaps,
                                   DirectedEdgeFilter directedEdgeFilter, PathCalculator pathCalculator,
                                   List<String> curbsides, String curbsideStrictness, List<Double> headings, boolean passThrough, EncodingManager em) {
        if (!curbsides.isEmpty() && curbsides.size() != points.size())
            throw new IllegalArgumentException("If you pass " + CURBSIDE + ", you need to pass exactly one curbside for every point, empty curbsides will be ignored");
        if (!curbsides.isEmpty() && !headings.isEmpty())
            throw new IllegalArgumentException("You cannot use curbsides and headings or pass_through at the same time");

        // Resolve AUTO curbsides based on road class, country and if one-way (later maybe lanes and max_speed)
        Function<Snap, String> curbsideAutoFunction = createCurbsideAutoFunction(directedEdgeFilter, em);

        final int legs = snaps.size() - 1;
        Result result = new Result(legs);
        for (int leg = 0; leg < legs; ++leg) {
            Snap fromSnap = snaps.get(leg);
            Snap toSnap = snaps.get(leg + 1);

            // enforce headings
            // at via-nodes and the target node the heading parameter is interpreted as the direction we want
            // to enforce for arriving (not starting) at this node. the starting direction is not enforced at
            // all for these points (unless using pass through). see this forum discussion:
            // https://discuss.graphhopper.com/t/meaning-of-heading-parameter-for-via-routing/5643/6
            double fromHeading = (leg == 0 && !headings.isEmpty()) ? headings.get(0) : Double.NaN;
            double toHeading = (snaps.size() == headings.size() && !Double.isNaN(headings.get(leg + 1))) ? headings.get(leg + 1) : Double.NaN;

            // enforce pass-through
            int incomingEdge = NO_EDGE;
            if (leg != 0) {
                // enforce straight start after via stop
                Path prevRoute = result.paths.get(leg - 1);
                if (prevRoute.getEdgeCount() > 0)
                    incomingEdge = prevRoute.getFinalEdge().getEdge();
            }

            // enforce curbsides
            String fromCurbside = curbsides.isEmpty() ? CURBSIDE_ANY : curbsides.get(leg);
            String toCurbside = curbsides.isEmpty() ? CURBSIDE_ANY : curbsides.get(leg + 1);

            if (CURBSIDE_AUTO.equals(fromCurbside))
                fromCurbside = curbsideAutoFunction.apply(fromSnap);
            if (CURBSIDE_AUTO.equals(toCurbside))
                toCurbside = curbsideAutoFunction.apply(toSnap);

            EdgeRestrictions edgeRestrictions = buildEdgeRestrictions(queryGraph, fromSnap, toSnap,
                    fromHeading, toHeading, incomingEdge, passThrough,
                    fromCurbside, toCurbside, directedEdgeFilter);

            edgeRestrictions.setSourceOutEdge(ignoreThrowOrAcceptImpossibleCurbsides(curbsides, edgeRestrictions.getSourceOutEdge(), leg, curbsideStrictness));
            edgeRestrictions.setTargetInEdge(ignoreThrowOrAcceptImpossibleCurbsides(curbsides, edgeRestrictions.getTargetInEdge(), leg + 1, curbsideStrictness));

            // calculate paths
            List<Path> paths = pathCalculator.calcPaths(fromSnap.getClosestNode(), toSnap.getClosestNode(), edgeRestrictions);
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
            result.debug += ", visited nodes sum: " + result.visitedNodes;
        }

        return result;
    }

    private static Function<Snap, String> createCurbsideAutoFunction(final DirectedEdgeFilter edgeFilter, final EncodingManager em) {
        EnumEncodedValue<RoadClass> roadClassEnc = em.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
//        DecimalEncodedValue maxSpeedEnc = em.getDecimalEncodedValue(MaxSpeed.KEY);
//        IntEncodedValue lanesEnc = em.hasEncodedValue(Lanes.KEY) ? em.getIntEncodedValue(Lanes.KEY) : null;
        EnumEncodedValue<Country> countryEnc = em.hasEncodedValue(Country.KEY) ? em.getEnumEncodedValue(Country.KEY, Country.class) : null;

        return snap -> {
            EdgeIteratorState edge = snap.getClosestEdge();

            // do not force curbside for 'smaller roads' (for now not configurable)
            RoadClass roadClass = edge.get(roadClassEnc);
            if (roadClass != RoadClass.PRIMARY && roadClass != RoadClass.SECONDARY && roadClass != RoadClass.TRUNK && roadClass != RoadClass.MOTORWAY)
                return CURBSIDE_ANY;

            // do not force curbside for one-ways
            if (!edgeFilter.accept(edge, false) || !edgeFilter.accept(edge, true))
                return CURBSIDE_ANY;

            // do not force curbside for 'smaller roads' regarding lanes and max_speed
            // note: lane count in OSM is for the entire road - not just for one direction
            // TODO: 'lanes' is 1 if OSM tag is missing, which might be rather misleading in this case
//            if (lanesEnc != null && edge.get(lanesEnc) < 2 && maxSpeedEnc != null && edge.get(maxSpeedEnc) <= 50)
//                return CURBSIDE_ANY;

            // could be different per point
            return countryEnc == null || edge.get(countryEnc).isRightHandTraffic() ? CURBSIDE_RIGHT : CURBSIDE_LEFT;
        };
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
            QueryGraph queryGraph, Snap fromSnap, Snap toSnap,
            double fromHeading, double toHeading, int incomingEdge, boolean passThrough,
            String fromCurbside, String toCurbside, DirectedEdgeFilter edgeFilter) {
        EdgeRestrictions edgeRestrictions = new EdgeRestrictions();

        // curbsides
        if (!fromCurbside.equals(CURBSIDE_ANY) || !toCurbside.equals(CURBSIDE_ANY)) {
            DirectedEdgeFilter directedEdgeFilter = (edge, reverse) -> {
                // todo: maybe find a cleaner way to obtain the original edge given a VirtualEdgeIterator (not VirtualEdgeIteratorState)
                if (queryGraph.isVirtualEdge(edge.getEdge())) {
                    EdgeIteratorState virtualEdge = queryGraph.getEdgeIteratorStateForKey(edge.getEdgeKey());
                    EdgeIteratorState origEdge = queryGraph.getEdgeIteratorStateForKey(((VirtualEdgeIteratorState) virtualEdge).getOriginalEdgeKey());
                    return edgeFilter.accept(origEdge, reverse);
                } else
                    return edgeFilter.accept(edge, reverse);
            };

            DirectionResolver directionResolver = new DirectionResolver(queryGraph, directedEdgeFilter);
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
                edgeRestrictions.getUnfavoredEdges().addAll(headingResolver.getEdgesWithDifferentHeading(fromSnap.getClosestNode(), fromHeading));

            if (!Double.isNaN(toHeading)) {
                toHeading += 180;
                if (toHeading > 360)
                    toHeading -= 360;
                edgeRestrictions.getUnfavoredEdges().addAll(headingResolver.getEdgesWithDifferentHeading(toSnap.getClosestNode(), toHeading));
            }
        }

        // pass through
        if (incomingEdge != NO_EDGE && passThrough)
            edgeRestrictions.getUnfavoredEdges().add(incomingEdge);
        return edgeRestrictions;
    }

    private static int ignoreThrowOrAcceptImpossibleCurbsides(List<String> curbsides, int edge, int placeIndex, String curbsideStrictness) {
        if (edge != NO_EDGE) {
            return edge;
        }
        if ("strict".equals(curbsideStrictness)) {
            return throwImpossibleCurbsideConstraint(curbsides, placeIndex);
        } else if ("soft".equals(curbsideStrictness)) {
            return ANY_EDGE;
        } else {
            throw new IllegalArgumentException("Unknown curbside_strictness " + curbsideStrictness);
        }
    }

    private static int throwImpossibleCurbsideConstraint(List<String> curbsides, int placeIndex) {
        throw new IllegalArgumentException("Impossible curbside constraint: 'curbside=" + curbsides.get(placeIndex) + "' at point " + placeIndex);
    }
}
