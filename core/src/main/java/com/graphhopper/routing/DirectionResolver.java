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

import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;

import java.util.*;

/**
 * This class is used to determine the pairs of edges that go into/out of a node of the routing graph. Two such pairs
 * are determined: One pair for the case a given coordinate should be right of a vehicle driving into/out of the node and
 * one pair for the case where the coordinate is on the left.
 * <p>
 * Example:
 * <p>
 * .a  x  b
 * --- o ---
 * <p>
 * If the location 'x' should be on the left side the incoming edge would be 'a' and the outgoing edge would be 'b'.
 * If the location 'x' should be on the right side the incoming edge would be 'b' and the outgoing edge would be 'a'.
 * <p>
 * The returned edge IDs can have some special values: we use {@link EdgeIterator#NO_EDGE} to indicate it is
 * not possible to arrive or leave a location in a certain direction and {@link EdgeIterator#ANY_EDGE} if
 * there was no clear way to determine an edge id.
 * <p>
 * There are a few special cases:
 * - if it is not possible to determine a clear result, such as for junctions with multiple adjacent edges
 * we return {@link DirectionResolverResult#unrestricted()}}
 * - if there is no way to reach or leave a location at all we return {@link DirectionResolverResult#impossible()}
 * - for locations where the location can only possibly be on the left or right side (such as one-ways we return
 * {@link DirectionResolverResult#onlyLeft(int, int)} or {@link DirectionResolverResult#onlyRight(int, int)}
 */
public class DirectionResolver {
    private final EdgeExplorer edgeExplorer;
    private final NodeAccess nodeAccess;
    private final FlagEncoder encoder;

    public DirectionResolver(Graph graph, FlagEncoder encoder) {
        edgeExplorer = graph.createEdgeExplorer();
        nodeAccess = graph.getNodeAccess();
        this.encoder = encoder;
    }

    /**
     * @param node     the node for which the incoming/outgoing edges should be determined
     * @param location the location next to the road relative to which the 'left' and 'right' side edges should be determined
     * @see DirectionResolver
     */
    public DirectionResolverResult resolveDirections(int node, GHPoint location) {
        AdjacentEdges adjacentEdges = calcAdjEdges(node);
        if (adjacentEdges.numNonLoops == 0) {
            return DirectionResolverResult.impossible();
        }
        if (!adjacentEdges.hasInEdges() || !adjacentEdges.hasOutEdges()) {
            return DirectionResolverResult.impossible();
        }
        if (adjacentEdges.nextPoints.isEmpty()) {
            return DirectionResolverResult.impossible();
        }
        if (adjacentEdges.numLoops > 0) {
            return DirectionResolverResult.unrestricted();
        }
        GHPoint snappedPoint = new GHPoint(nodeAccess.getLat(node), nodeAccess.getLon(node));
        if (adjacentEdges.nextPoints.contains(snappedPoint)) {
            // this might happen if a pillar node of an adjacent edge has the same coordinates as the snapped point,
            // but this should be prevented by the map import already
            throw new IllegalArgumentException("Pillar node of adjacent edge matches snapped point, this should not happen");
        }
        // we can classify the different cases by the number of different next points!
        if (adjacentEdges.nextPoints.size() == 1) {
            GHPoint neighbor = adjacentEdges.nextPoints.iterator().next();
            List<Edge> inEdges = adjacentEdges.getInEdges(neighbor);
            List<Edge> outEdges = adjacentEdges.getOutEdges(neighbor);
            assert inEdges.size() > 0 && outEdges.size() > 0 : "if there is only one next point there has to be an in edge and an out edge connected with it";
            // if there are multiple edges going to the (single) next point we cannot return a reasonable result and
            // leave this point unrestricted
            if (inEdges.size() > 1 || outEdges.size() > 1) {
                return DirectionResolverResult.unrestricted();
            }
            // since there is only one next point we know this is the end of a dead end street so the right and left
            // side are treated equally and for both cases we use the only possible edge ids.
            return DirectionResolverResult.restricted(inEdges.get(0).edgeId, outEdges.get(0).edgeId, inEdges.get(0).edgeId, outEdges.get(0).edgeId);
        } else if (adjacentEdges.nextPoints.size() == 2) {
            Iterator<GHPoint> iter = adjacentEdges.nextPoints.iterator();
            GHPoint p1 = iter.next();
            GHPoint p2 = iter.next();
            List<Edge> in1 = adjacentEdges.getInEdges(p1);
            List<Edge> in2 = adjacentEdges.getInEdges(p2);
            List<Edge> out1 = adjacentEdges.getOutEdges(p1);
            List<Edge> out2 = adjacentEdges.getOutEdges(p2);
            if (in1.size() > 1 || in2.size() > 1 || out1.size() > 1 || out2.size() > 1) {
                return DirectionResolverResult.unrestricted();
            }
            if (in1.size() + in2.size() == 0 || out1.size() + out2.size() == 0) {
                throw new IllegalStateException("there has to be at least one in and one out edge when there are two next points");
            }
            if (in1.size() + out1.size() == 0 || in2.size() + out2.size() == 0) {
                throw new IllegalStateException("there has to be at least one in or one out edge for each of the two next points");
            }
            if (in1.isEmpty() || out2.isEmpty()) {
                return resolveDirections(snappedPoint, location, in2.get(0), out1.get(0));
            } else if (in2.isEmpty() || out1.isEmpty()) {
                return resolveDirections(snappedPoint, location, in1.get(0), out2.get(0));
            } else {
                return resolveDirections(snappedPoint, location, in1.get(0), out2.get(0), in2.get(0).edgeId, out1.get(0).edgeId);
            }
        } else {
            // we snapped to a junction, in this case we do not apply restrictions
            // note: TOWER and PILLAR mostly occur when location is near the end of a dead end street or a sharp
            // curve, like switchbacks in the mountains of andorra
            return DirectionResolverResult.unrestricted();
        }
    }

    private DirectionResolverResult resolveDirections(GHPoint snappedPoint, GHPoint queryPoint, Edge inEdge, Edge outEdge) {
        boolean rightLane = isOnRightLane(queryPoint, snappedPoint, inEdge.nextPoint, outEdge.nextPoint);
        if (rightLane) {
            return DirectionResolverResult.onlyRight(inEdge.edgeId, outEdge.edgeId);
        } else {
            return DirectionResolverResult.onlyLeft(inEdge.edgeId, outEdge.edgeId);
        }
    }

    private DirectionResolverResult resolveDirections(GHPoint snappedPoint, GHPoint queryPoint, Edge inEdge, Edge outEdge, int altInEdge, int altOutEdge) {
        GHPoint inPoint = inEdge.nextPoint;
        GHPoint outPoint = outEdge.nextPoint;
        boolean rightLane = isOnRightLane(queryPoint, snappedPoint, inPoint, outPoint);
        if (rightLane) {
            return DirectionResolverResult.restricted(inEdge.edgeId, outEdge.edgeId, altInEdge, altOutEdge);
        } else {
            return DirectionResolverResult.restricted(altInEdge, altOutEdge, inEdge.edgeId, outEdge.edgeId);
        }
    }

    private boolean isOnRightLane(GHPoint queryPoint, GHPoint snappedPoint, GHPoint inPoint, GHPoint outPoint) {
        double qX = diffLon(snappedPoint, queryPoint);
        double qY = diffLat(snappedPoint, queryPoint);
        double iX = diffLon(snappedPoint, inPoint);
        double iY = diffLat(snappedPoint, inPoint);
        double oX = diffLon(snappedPoint, outPoint);
        double oY = diffLat(snappedPoint, outPoint);
        return !Helper.ANGLE_CALC.isClockwise(iX, iY, oX, oY, qX, qY);
    }

    private double diffLon(GHPoint p, GHPoint q) {
        return q.lon - p.lon;
    }

    private double diffLat(GHPoint p, GHPoint q) {
        return q.lat - p.lat;
    }

    private AdjacentEdges calcAdjEdges(int node) {
        AdjacentEdges adjacentEdges = new AdjacentEdges();
        EdgeIterator iter = edgeExplorer.setBaseNode(node);
        while (iter.next()) {
            // we are not interested in shortcuts here, even if there are shortcuts it is still sufficient to look
            // at the original edges they begin with
            if (iter instanceof CHEdgeIteratorState && ((CHEdgeIteratorState) iter).isShortcut()) {
                continue;
            }
            BooleanEncodedValue accessEnc = encoder.getAccessEnc();
            boolean isIn = iter.getReverse(accessEnc);
            boolean isOut = iter.get(accessEnc);
            if (!isIn && !isOut) {
                continue;
            }
            if (iter.getBaseNode() == iter.getAdjNode()) {
                adjacentEdges.numLoops++;
            } else {
                adjacentEdges.numNonLoops++;
            }
            // we are interested in the coordinates of the next point on this edge, it could be the adj tower node
            // but also a pillar node
            final PointList geometry = iter.fetchWayGeometry(3);
            double nextPointLat = geometry.getLat(1);
            double nextPointLon = geometry.getLon(1);

            // todo: special treatment in case the coordinates of the first pillar node equal those of the base tower
            // node, see #1694
            if (geometry.size() > 2 && PointList.equalsEps(nextPointLat, geometry.getLat(0)) &&
                    PointList.equalsEps(nextPointLon, geometry.getLon(0))) {
                nextPointLat = geometry.getLat(2);
                nextPointLon = geometry.getLon(2);
            }
            GHPoint nextPoint = new GHPoint(nextPointLat, nextPointLon);
            Edge edge = new Edge(iter.getEdge(), iter.getAdjNode(), nextPoint);
            adjacentEdges.addEdge(edge, isIn, isOut);
        }
        return adjacentEdges;
    }

    private static class AdjacentEdges {
        private final Map<GHPoint, List<Edge>> inEdgesByNextPoint = new HashMap<>(2);
        private final Map<GHPoint, List<Edge>> outEdgesByNextPoint = new HashMap<>(2);
        final Set<GHPoint> nextPoints = new HashSet<>(2);
        int numLoops;
        int numNonLoops;

        void addEdge(Edge edge, boolean isIn, boolean isOut) {
            if (isIn) {
                addInEdge(edge);
            }
            if (isOut) {
                addOutEdge(edge);
            }
            addNextPoint(edge);
        }

        List<Edge> getInEdges(GHPoint p) {
            List<Edge> result = inEdgesByNextPoint.get(p);
            return result == null ? Collections.<Edge>emptyList() : result;
        }

        List<Edge> getOutEdges(GHPoint p) {
            List<Edge> result = outEdgesByNextPoint.get(p);
            return result == null ? Collections.<Edge>emptyList() : result;
        }

        boolean hasInEdges() {
            return !inEdgesByNextPoint.isEmpty();
        }

        boolean hasOutEdges() {
            return !outEdgesByNextPoint.isEmpty();
        }

        private void addOutEdge(Edge edge) {
            addEdge(outEdgesByNextPoint, edge);
        }

        private void addInEdge(Edge edge) {
            addEdge(inEdgesByNextPoint, edge);
        }

        private void addNextPoint(Edge edge) {
            nextPoints.add(edge.nextPoint);
        }

        private void addEdge(Map<GHPoint, List<Edge>> edgesByNextPoint, Edge edge) {
            List<Edge> edges = edgesByNextPoint.get(edge.nextPoint);
            if (edges == null) {
                edges = new ArrayList<>(2);
                edges.add(edge);
                edgesByNextPoint.put(edge.nextPoint, edges);
            } else {
                edges.add(edge);
            }
        }
    }

    private static class Edge {
        final int edgeId;
        final int adjNode;
        /**
         * the next point of this edge, not necessarily the point corresponding to adjNode, but often this is the
         * next pillar (!) node.
         */
        final GHPoint nextPoint;

        Edge(int edgeId, int adjNode, GHPoint nextPoint) {
            this.edgeId = edgeId;
            this.adjNode = adjNode;
            this.nextPoint = nextPoint;
        }
    }

}
